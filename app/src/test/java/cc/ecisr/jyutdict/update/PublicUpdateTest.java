package cc.ecisr.jyutdict.update;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import cc.ecisr.jyutdict.BuildConfig;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class PublicUpdateTest {
    @Test
    public void readsDownloadLengthsOnAndroidSix() {
        assertEquals(4294967296L, UpdateDownloader.contentLength("4294967296"));
        assertEquals(-1, UpdateDownloader.contentLength(null));
        assertEquals(-1, UpdateDownloader.contentLength("unknown"));
        assertEquals(-1, UpdateDownloader.contentLength("-3"));
    }

    @Test
    public void selectsLegacySignaturesOnAndroidSix() {
        assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.P);
        PackageInfo info = new PackageInfo();
        Signature[] expected = new Signature[]{new Signature("0123456789abcdef")};
        info.signatures = expected;
        assertSame(expected, UpdateDownloader.packageSignatures(info));
    }

    @Test
    public void treatsMissingLegacySignaturesAsEmpty() {
        assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.P);
        PackageInfo info = new PackageInfo();
        info.signatures = null;
        assertEquals(0, UpdateDownloader.packageSignatures(info).length);
    }
    @Test
    public void removesLegacyTokenWithoutLosingNotificationState() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        settings.edit().putString("github_update_token", "obsolete-test-value").commit();
        UpdateSettings.setDismissedBuild(context, 35);
        UpdateSettings.clearLegacyToken(context);
        assertFalse(settings.contains("github_update_token"));
        assertEquals(35, UpdateSettings.getDismissedBuild(context));
        assertEquals("Aigooz/Jyutdict-Android", BuildConfig.UPDATE_REPO);
        assertTrue(BuildConfig.UPDATE_BUILD > 35);
    }

    @Test
    public void selectsPublicApkAndSkipsDraftsAndMissingAssets() throws Exception {
        JSONArray releases = new JSONArray("["
                + "{\"tag_name\":\"build-9999\",\"draft\":true},"
                + "{\"tag_name\":\"build-9998\",\"assets\":[]},"
                + "{\"tag_name\":\"build-1001\",\"assets\":[{"
                + "\"name\":\"Yueci-Android-dual-debug.apk\","
                + "\"browser_download_url\":\"https://github.com/Aigooz/Jyutdict-Android/releases/download/build-1001/app.apk\"}]}]");
        AppUpdateClient.ReleaseInfo release = AppUpdateClient.selectBestRelease(releases);
        assertNotNull(release);
        assertEquals(1001, release.buildNumber());
        assertTrue(release.assetDownloadUrl().startsWith("https://github.com/Aigooz/"));
    }

    @Test
    public void attachesChecksumAssetToApkRelease() throws Exception {
        JSONArray releases = new JSONArray("[{"
                + "\"tag_name\":\"build-1002\","
                + "\"assets\":["
                + "{\"name\":\"Yueci-Android-dual-debug.apk\","
                + "\"url\":\"https://api.github.com/assets/apk\","
                + "\"browser_download_url\":\"https://github.com/apk\","
                + "\"size\":123},"
                + "{\"name\":\"SHA256SUMS\","
                + "\"url\":\"https://api.github.com/assets/sums\","
                + "\"browser_download_url\":\"https://github.com/sums\","
                + "\"size\":88}]}]");
        AppUpdateClient.ReleaseInfo release = AppUpdateClient.selectBestRelease(releases);
        assertEquals("https://api.github.com/assets/sums", release.checksumApiUrl());
        assertEquals("https://github.com/sums", release.checksumDownloadUrl());
        assertEquals(88, release.checksumSize());
    }

    @Test
    public void doesNotOfferAnApkWithoutChecksumManifest() throws Exception {
        JSONArray releases = new JSONArray("[{"
                + "\"tag_name\":\"build-1003\","
                + "\"assets\":[{\"name\":\"Yueci-Android-dual-debug.apk\","
                + "\"browser_download_url\":\"https://github.com/apk\","
                + "\"size\":123}]}]");
        AppUpdateClient.ReleaseInfo release = AppUpdateClient.selectBestRelease(releases);
        assertNotNull(release);
        assertTrue(release.checksumDownloadUrl().isEmpty());
        assertTrue(release.checksumApiUrl().isEmpty());
    }

    @Test
    public void updateMayKeepVersionCodeButMustNotDowngrade() {
        assertTrue(UpdateDownloader.isNotDowngrade(830, 830));
        assertTrue(UpdateDownloader.isNotDowngrade(831, 830));
        assertFalse(UpdateDownloader.isNotDowngrade(829, 830));
    }
}
