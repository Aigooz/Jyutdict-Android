package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads an update after the user explicitly chooses background mode. */
public final class BackgroundUpdateWorker extends Worker {
    private static final String TAG = "BackgroundUpdateWorker";
    static final String UNIQUE_WORK_NAME = "jyutdict_background_update_download";
    private static final String KEY_TAG_NAME = "tag_name";
    private static final String KEY_VERSION_NAME = "version_name";
    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_ASSET_NAME = "asset_name";
    private static final String KEY_ASSET_API_URL = "asset_api_url";
    private static final String KEY_ASSET_DOWNLOAD_URL = "asset_download_url";
    private static final String KEY_ASSET_SIZE = "asset_size";

    public BackgroundUpdateWorker(@NonNull Context context,
                                  @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context, AppUpdateClient.ReleaseInfo release) {
        Data input = new Data.Builder()
                .putString(KEY_TAG_NAME, release.tagName())
                .putString(KEY_VERSION_NAME, release.versionName())
                .putInt(KEY_BUILD_NUMBER, release.buildNumber())
                .putString(KEY_ASSET_NAME, release.assetName())
                .putString(KEY_ASSET_API_URL, release.assetApiUrl())
                .putString(KEY_ASSET_DOWNLOAD_URL, release.assetDownloadUrl())
                .putLong(KEY_ASSET_SIZE, release.assetSize())
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundUpdateWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInputData(input)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppUpdateClient.ReleaseInfo release = releaseFromInput();
        if (release == null || !release.hasAsset()) return Result.failure();

        Context context = getApplicationContext();
        if (UpdateDownloader.isDownloaded(context, release)) {
            UpdateNotifier.notifyInstallReady(context, release);
            return Result.success();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        UpdateDownloader.downloadOnly(context, release, new UpdateDownloader.ProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                UpdateNotifier.notifyDownloadProgress(
                        context, release, percent, downloadedBytes, totalBytes);
            }

            @Override
            public void onComplete(boolean downloadSuccess, String message) {
                success.set(downloadSuccess);
                if (downloadSuccess) {
                    UpdateNotifier.notifyInstallReady(context, release);
                } else {
                    UpdateNotifier.notifyDownloadFailed(context, message);
                }
                latch.countDown();
            }
        });

        try {
            if (!latch.await(10, TimeUnit.MINUTES)) {
                Log.w(TAG, "Background update download timed out");
                return Result.retry();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
        return success.get() ? Result.success() : Result.retry();
    }

    private AppUpdateClient.ReleaseInfo releaseFromInput() {
        Data input = getInputData();
        String tagName = input.getString(KEY_TAG_NAME);
        String versionName = input.getString(KEY_VERSION_NAME);
        String assetName = input.getString(KEY_ASSET_NAME);
        if (tagName == null || versionName == null || assetName == null) return null;
        return new AppUpdateClient.ReleaseInfo(
                tagName,
                versionName,
                input.getInt(KEY_BUILD_NUMBER, 0),
                assetName,
                input.getString(KEY_ASSET_API_URL),
                input.getString(KEY_ASSET_DOWNLOAD_URL),
                input.getLong(KEY_ASSET_SIZE, 0),
                ""
        );
    }
}
