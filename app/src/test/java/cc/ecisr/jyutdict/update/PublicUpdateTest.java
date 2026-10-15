package cc.ecisr.jyutdict.update;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
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
}
