package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.ecisr.jyutdict.BuildConfig;
import cc.ecisr.jyutdict.utils.LocaleUtil;

/** Queries the update repository's latest release. */
public final class AppUpdateClient {
    private static final String TAG = "AppUpdateClient";
    private static final Pattern BUILD_TAG_PATTERN = Pattern.compile("(?:^|-)build-(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEMANTIC_TAG_PATTERN = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile Handler mainHandler;

    public record ReleaseInfo(
            String tagName,
            String versionName,
            int buildNumber,
            String assetName,
            String assetApiUrl,
            String assetDownloadUrl,
            long assetSize,
            String releaseBody
    ) {
        public boolean hasAsset() {
            return (assetDownloadUrl != null && !assetDownloadUrl.isEmpty())
                    || (assetApiUrl != null && !assetApiUrl.isEmpty());
        }
    }

    public record UpdateResult(ReleaseInfo release, boolean hasUpdate, String message) {
    }

    public interface Callback {
        void onComplete(UpdateResult result);
    }

    private AppUpdateClient() {}

    public static void fetchLatestRelease(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        Context localizedContext = LocaleUtil.wrap(appContext);
        EXECUTOR.execute(() -> {
            UpdateResult result;
            try {
                result = fetchLatestReleaseSync(localizedContext);
            } catch (UpdateException exception) {
                result = new UpdateResult(null, false, exception.getMessage());
            } catch (Exception exception) {
                Log.w(TAG, "Update check failed", exception);
                result = new UpdateResult(null, false,
                        localizedContext.getString(cc.ecisr.jyutdict.R.string.version_status_failed));
            }
            UpdateResult finalResult = result;
            mainHandler().post(() -> callback.onComplete(finalResult));
        });
    }

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (AppUpdateClient.class) {
                handler = mainHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                    mainHandler = handler;
                }
            }
        }
        return handler;
    }

    public static UpdateResult fetchLatestReleaseSync(Context context) throws Exception {
        UpdateSettings.clearLegacyToken(context);
        HttpURLConnection connection = null;
        try {
            connection = open(
                    "https://api.github.com/repos/" + BuildConfig.UPDATE_REPO
                            + "/releases?per_page=100",
                    "application/vnd.github+json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new UpdateException(context.getString(
                        cc.ecisr.jyutdict.R.string.version_status_http_error, status));
            }
            ReleaseInfo info = selectBestRelease(new JSONArray(readBody(connection.getInputStream())));
            if (info == null) {
                throw new UpdateException(
                        context.getString(cc.ecisr.jyutdict.R.string.version_status_failed));
            }
            boolean hasUpdate = info.hasAsset() && info.buildNumber > BuildConfig.UPDATE_BUILD;
            return new UpdateResult(info, hasUpdate, null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static int parseBuildNumber(String tagName) {
        String tag = tagName == null ? "" : tagName.trim();
        Matcher buildMatcher = BUILD_TAG_PATTERN.matcher(tag);
        if (buildMatcher.find()) return parsePositiveInt(buildMatcher.group(1));

        // Keep compatibility with the original public repository, whose releases
        // use semantic tags such as v0.8.4 instead of build-N tags.
        Matcher semanticMatcher = SEMANTIC_TAG_PATTERN.matcher(tag);
        if (!semanticMatcher.matches()) return 0;
        try {
            int major = Integer.parseInt(semanticMatcher.group(1));
            int minor = Integer.parseInt(semanticMatcher.group(2));
            int patch = Integer.parseInt(semanticMatcher.group(3));
            if (minor > 99 || patch > 99) return 0;
            return major * 10000 + minor * 100 + patch;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int parsePositiveInt(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && parsed <= Integer.MAX_VALUE ? (int) parsed : 0;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    static ReleaseInfo selectBestRelease(JSONArray releases) {
        ReleaseInfo best = null;
        for (int index = 0; index < releases.length(); index++) {
            JSONObject release = releases.optJSONObject(index);
            if (release == null || release.optBoolean("draft", false)
                    || release.optBoolean("prerelease", false)) continue;

            String tag = release.optString("tag_name", "");
            int build = parseBuildNumber(tag);
            if (build <= 0) continue;

            ReleaseInfo candidate = parseReleaseAssets(release, tag, build);
            // Ignore historical releases that do not contain the APK format
            // produced by this app. They must not block a newer build-N release.
            if (!candidate.hasAsset()) continue;
            if (best == null || build > best.buildNumber()) best = candidate;
        }
        return best;
    }

    private static ReleaseInfo parseReleaseAssets(
            JSONObject release, String tag, int build) {
        String version = release.optString("name", tag);
        ReleaseInfo info = new ReleaseInfo(
                    tag,
                    version,
                    build,
                    "",
                    "",
                    "",
                    0,
                    release.optString("body", "")
            );

        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.optJSONObject(index);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (name.endsWith(".apk") && name.contains("dual")) {
                    return new ReleaseInfo(
                            tag,
                            version,
                            build,
                            name,
                            asset.optString("url", ""),
                            asset.optString("browser_download_url", ""),
                            asset.optLong("size", 0),
                            release.optString("body", "")
                    );
                }
            }
        }
        return info;
    }

    private static HttpURLConnection open(String url, String accept)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "Jyutdict-Android-Update");
        return connection;
    }

    private static String readBody(InputStream stream) throws Exception {
        StringBuilder result = new StringBuilder();
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            while ((line = buffered.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static final class UpdateException extends Exception {
        private UpdateException(String message) {
            super(Objects.requireNonNull(message));
        }
    }
}
