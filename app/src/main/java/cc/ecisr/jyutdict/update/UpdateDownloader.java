package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import cc.ecisr.jyutdict.BuildConfig;
import cc.ecisr.jyutdict.utils.LocaleUtil;

public final class UpdateDownloader {
    private static final String TAG = "UpdateDownloader";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int DOWNLOAD_ATTEMPTS = 3;
    private static final int BUFFER_SIZE = 65536;
    private static final int PROGRESS_STEP = 65536;
    private static final int MAX_CHECKSUM_BYTES = 65536;
    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile(
            "bytes\\s+(\\d+)-(\\d+)/(\\d+)");

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public interface ProgressCallback extends Callback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
    }

    public interface DownloadHandle {
        void cancel();
    }

    private UpdateDownloader() {}

    /** Returns true when the APK for this release is already fully downloaded. */
    public static boolean isDownloaded(Context context,
                                       AppUpdateClient.ReleaseInfo release) {
        if (!release.hasAsset() || release.assetSize() <= 0) return false;
        File directory = updateDirectory(context);
        File destination = new File(directory, release.assetName());
        if (!destination.exists() || destination.length() != release.assetSize()) {
            return false;
        }
        try {
            validateDownloadedApk(context, release, destination);
            return true;
        } catch (Exception exception) {
            destination.delete();
            return false;
        }
    }

    /** Downloads the APK without launching the installer; for background use. */
    public static DownloadHandle downloadOnly(Context context,
                                              AppUpdateClient.ReleaseInfo release,
                                              ProgressCallback callback) {
        Context appContext = context.getApplicationContext();
        Context localizedContext = LocaleUtil.wrap(appContext);
        Future<?> future = EXECUTOR.submit(() -> {
            try {
                download(localizedContext, release, callback);
                MAIN_HANDLER.post(() -> callback.onComplete(true, null));
            } catch (Exception exception) {
                Log.e(TAG, "Background download failed", exception);
                String message = exception.getMessage();
                boolean cancelled = exception instanceof InterruptedIOException
                        || exception instanceof java.nio.channels.ClosedByInterruptException;
                MAIN_HANDLER.post(() -> callback.onComplete(false, cancelled
                        ? localizedContext.getString(cc.ecisr.jyutdict.R.string.version_download_cancelled)
                        : message));
            }
        });
        return () -> future.cancel(true);
    }

    public static DownloadHandle downloadAndInstall(Context context,
                                                    AppUpdateClient.ReleaseInfo release,
                                                    ProgressCallback callback) {
        Context appContext = context.getApplicationContext();
        Context localizedContext = LocaleUtil.wrap(appContext);
        Future<?> future = EXECUTOR.submit(() -> {
            try {
                File file = download(localizedContext, release, callback);
                MAIN_HANDLER.post(() -> install(localizedContext, file, callback));
            } catch (Exception exception) {
                Log.e(TAG, "Download failed", exception);
                String message = exception.getMessage();
                boolean cancelled = exception instanceof InterruptedIOException
                        || exception instanceof java.nio.channels.ClosedByInterruptException;
                MAIN_HANDLER.post(() -> callback.onComplete(false, cancelled
                        ? localizedContext.getString(cc.ecisr.jyutdict.R.string.version_download_cancelled)
                        : message));
            }
        });
        return () -> future.cancel(true);
    }

    private static File updateDirectory(Context context) {
        File directory = context.getExternalFilesDir("updates");
        if (directory == null) directory = new File(context.getFilesDir(), "updates");
        return directory;
    }

    private static File download(Context context, AppUpdateClient.ReleaseInfo release,
                                 ProgressCallback callback)
            throws Exception {
        File directory = updateDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create update directory");
        }
        File destination = new File(directory, release.assetName());
        if (release.assetSize() > 0 && destination.exists()
                && destination.length() == release.assetSize()) {
            try {
                validateDownloadedApk(context, release, destination);
                return destination;
            } catch (Exception exception) {
                destination.delete();
            }
        }
        File partial = new File(directory, partialName(release));
        clearOldFiles(directory, partial.getName());
        Set<String> urls = new LinkedHashSet<>();
        if (release.assetDownloadUrl() != null && !release.assetDownloadUrl().isEmpty()) {
            urls.add(release.assetDownloadUrl());
        }
        if (release.assetApiUrl() != null && !release.assetApiUrl().isEmpty()) {
            urls.add(release.assetApiUrl());
        }

        Exception lastException = null;
        for (String url : urls) {
            try {
                return downloadWithRetry(context, directory, url, release, callback);
            } catch (Exception exception) {
                if (Thread.currentThread().isInterrupted()
                        || exception instanceof InterruptedIOException) {
                    throw exception;
                }
                Log.w(TAG, "Update download failed: " + url, exception);
                lastException = exception;
            }
        }
        throw lastException == null
                ? new IllegalStateException("No update download URL")
        : lastException;
    }

    private static File downloadWithRetry(Context context, File directory, String url,
                                          AppUpdateClient.ReleaseInfo release,
                                          ProgressCallback callback)
            throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Download cancelled");
            }
            try {
                return downloadFrom(context, directory, url, release, callback);
            } catch (Exception exception) {
                if (Thread.currentThread().isInterrupted()
                        || exception instanceof InterruptedIOException) {
                    throw exception;
                }
                Log.w(TAG, "Update download attempt " + attempt + " failed: " + url, exception);
                lastException = exception;
                if (attempt < DOWNLOAD_ATTEMPTS) {
                    try {
                        Thread.sleep(attempt * 1200L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Download cancelled");
                    }
                }
            }
        }
        throw lastException == null
                ? new IllegalStateException("Download failed")
                : lastException;
    }

    private static File downloadFrom(Context context, File directory, String url,
                                     AppUpdateClient.ReleaseInfo release,
                                     ProgressCallback callback)
            throws Exception {
        File destination = new File(directory, release.assetName());
        File partial = new File(directory, partialName(release));
        long existingBytes = partial.exists() ? partial.length() : 0;
        if (release.assetSize() > 0 && existingBytes > release.assetSize()) {
            partial.delete();
            existingBytes = 0;
        }
        if (release.assetSize() > 0 && existingBytes == release.assetSize()) {
            return finalizeDownload(context, partial, destination, release);
        }
        boolean resuming = existingBytes > 0;

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "Jyutdict-Android-Update");
            if (resuming) {
                connection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
            }

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK && resuming) {
                partial.delete();
                return downloadFrom(context, directory, url, release, callback);
            }
            if (status == HttpURLConnection.HTTP_PARTIAL && resuming) {
                Matcher range = contentRange(connection.getHeaderField("Content-Range"));
                if (range == null || Long.parseLong(range.group(1)) != existingBytes) {
                    partial.delete();
                    return downloadFrom(context, directory, url, release, callback);
                }
            } else if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + status);
            }

            long totalBytes = contentLength(connection.getHeaderField("Content-Length"));
            long downloadedBytes = existingBytes;
            if (status == HttpURLConnection.HTTP_PARTIAL) {
                Matcher range = contentRange(connection.getHeaderField("Content-Range"));
                if (range != null && Long.parseLong(range.group(3)) > 0) {
                    totalBytes = Long.parseLong(range.group(3));
                } else if (totalBytes > 0) {
                    totalBytes += downloadedBytes;
                }
            }
            if (totalBytes <= 0 && release.assetSize() > 0) totalBytes = release.assetSize();
            postProgress(callback, downloadedBytes, totalBytes);
            try (InputStream input = connection.getInputStream();
                 OutputStream fileOutput = resuming
                         ? new FileOutputStream(partial, true)
                         : new FileOutputStream(partial);
                 BufferedOutputStream output = new BufferedOutputStream(
                         fileOutput, BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long lastProgressAt = downloadedBytes;
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Download cancelled");
                    }
                    output.write(buffer, 0, length);
                    downloadedBytes += length;
                    if (downloadedBytes - lastProgressAt >= PROGRESS_STEP) {
                        lastProgressAt = downloadedBytes;
                        postProgress(callback, downloadedBytes, totalBytes);
                    }
                }
                postProgress(callback, downloadedBytes, totalBytes);
                if (totalBytes > 0 && downloadedBytes != totalBytes) {
                    throw new IllegalStateException("Incomplete download: " + downloadedBytes
                            + "/" + totalBytes);
                }
            }
            return finalizeDownload(context, partial, destination, release);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static File finalizeDownload(Context context, File partial, File destination,
                                         AppUpdateClient.ReleaseInfo release)
            throws Exception {
        validateDownloadedApk(context, release, partial);
        if (destination.exists()) destination.delete();
        if (!partial.renameTo(destination)) {
            throw new IllegalStateException("Cannot finalize update file");
        }
        return destination;
    }

    private static void validateDownloadedApk(
            Context context, AppUpdateClient.ReleaseInfo release, File apk) throws Exception {
        long releaseSize = release.assetSize();
        if (releaseSize <= 0 || apk.length() != releaseSize) {
            throw new IllegalStateException("APK length mismatch");
        }
        String expectedDigest = fetchChecksumDigest(context, release);
        String actualDigest = UpdateIntegrity.sha256(apk);
        if (!MessageDigest.isEqual(
                expectedDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalStateException(
                    context.getString(cc.ecisr.jyutdict.R.string.version_checksum_failed));
        }

        PackageManager manager = context.getPackageManager();
        int flags = packageSignatureFlags();
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        if (archive == null || !context.getPackageName().equals(archive.packageName)
                || !isNotDowngrade(archive.versionCode, installed.versionCode)) {
            throw new IllegalStateException(
                    context.getString(cc.ecisr.jyutdict.R.string.version_package_invalid));
        }
        if (!sameSigner(archive, installed)) {
            throw new IllegalStateException(
                    context.getString(cc.ecisr.jyutdict.R.string.version_package_invalid));
        }
    }

    private static String fetchChecksumDigest(
            Context context, AppUpdateClient.ReleaseInfo release)
            throws Exception {
        if (release.checksumSize() > MAX_CHECKSUM_BYTES) {
            throw new IllegalStateException("Checksum manifest is too large");
        }
        Set<String> urls = new LinkedHashSet<>();
        if (release.checksumDownloadUrl() != null && !release.checksumDownloadUrl().isEmpty()) {
            urls.add(release.checksumDownloadUrl());
        }
        if (release.checksumApiUrl() != null && !release.checksumApiUrl().isEmpty()) {
            urls.add(release.checksumApiUrl());
        }
        if (urls.isEmpty()) {
            throw new IllegalStateException(
                    context.getString(cc.ecisr.jyutdict.R.string.version_checksum_missing));
        }
        Exception last = null;
        for (String url : urls) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "Jyutdict-Android-Update");
                connection.setRequestProperty("Accept", "text/plain");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Checksum HTTP " + status);
                }
                String manifest = UpdateIntegrity.readLimitedUtf8(
                        connection.getInputStream(), MAX_CHECKSUM_BYTES);
                String digest = UpdateIntegrity.digestForAsset(manifest, release.assetName());
                if (digest == null) {
                    throw new IllegalStateException(
                            "Checksum entry is missing for " + release.assetName());
                }
                return digest;
            } catch (Exception exception) {
                last = exception;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last == null
                ? new IllegalStateException("Checksum manifest is unavailable")
                : last;
    }

    private static boolean sameSigner(PackageInfo archive, PackageInfo installed)
            throws Exception {
        byte[] archiveDigest = signerDigest(archive);
        byte[] installedDigest = signerDigest(installed);
        return archiveDigest != null && installedDigest != null
                && MessageDigest.isEqual(archiveDigest, installedDigest);
    }

    private static byte[] signerDigest(PackageInfo info) throws Exception {
        Signature[] signatures = packageSignatures(info);
        if (signatures.length == 0) return null;
        return MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
    }

    private static int packageSignatureFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    static Signature[] packageSignatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return new Signature[0];
            Signature[] signatures = info.signingInfo.getApkContentsSigners();
            return signatures == null ? new Signature[0] : signatures;
        }
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    static long contentLength(String header) {
        try {
            long length = Long.parseLong(header);
            return length >= 0 ? length : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static boolean isNotDowngrade(int candidateVersionCode, int installedVersionCode) {
        return candidateVersionCode >= installedVersionCode;
    }

    private static String partialName(AppUpdateClient.ReleaseInfo release) {
        String name = release.tagName() + "-" + release.assetName() + ".part";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Matcher contentRange(String value) {
        if (value == null) return null;
        Matcher matcher = CONTENT_RANGE_PATTERN.matcher(value.trim());
        return matcher.matches() ? matcher : null;
    }

    private static void postProgress(ProgressCallback callback, long downloadedBytes,
                                     long totalBytes) {
        int percent = totalBytes > 0
                ? (int) Math.min(100, downloadedBytes * 100 / totalBytes)
                : -1;
        MAIN_HANDLER.post(() -> callback.onProgress(percent, downloadedBytes, totalBytes));
    }

    private static void install(Context context, File file, Callback callback) {
        Uri uri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            callback.onComplete(true, null);
        } catch (Exception exception) {
            callback.onComplete(false, exception.getMessage());
        }
    }

    private static void clearOldFiles(File directory, String keepName) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && !keepName.equals(file.getName())) file.delete();
        }
    }
}
