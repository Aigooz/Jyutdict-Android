package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.content.SharedPreferences;

public final class UpdateSettings {
    private static final String PREFERENCES = "settings";
    private static final String TOKEN_KEY = "github_update_token";
    private static final String LAST_NOTIFIED_KEY = "last_notified_update_release";
    private static final String DISMISSED_BUILD_KEY = "dismissed_update_build";

    private UpdateSettings() {}

    public static void clearLegacyToken(Context context) {
        SharedPreferences settings = preferences(context);
        if (settings.contains(TOKEN_KEY)) settings.edit().remove(TOKEN_KEY).apply();
    }

    public static String getLastNotifiedReleaseKey(Context context) {
        return preferences(context).getString(LAST_NOTIFIED_KEY, "");
    }

    public static void setLastNotifiedReleaseKey(Context context, String releaseKey) {
        preferences(context).edit()
                .putString(LAST_NOTIFIED_KEY, releaseKey == null ? "" : releaseKey)
                .apply();
    }

    public static int getDismissedBuild(Context context) {
        return preferences(context).getInt(DISMISSED_BUILD_KEY, 0);
    }

    public static void setDismissedBuild(Context context, int buildNumber) {
        preferences(context).edit()
                .putInt(DISMISSED_BUILD_KEY, Math.max(0, buildNumber))
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
