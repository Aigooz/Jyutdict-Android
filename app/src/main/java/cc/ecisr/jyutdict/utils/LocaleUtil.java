package cc.ecisr.jyutdict.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/** Applies the user's Chinese-language override to every app activity. */
public final class LocaleUtil {
    public static final String SYSTEM = "system";
    public static final String SIMPLIFIED = "zh-CN";
    public static final String TRADITIONAL = "zh-TW";

    private static volatile boolean simplifyData;

    private LocaleUtil() {}

    public static Context wrap(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String setting = preferences.getString("app_locale", SYSTEM);
        if (SYSTEM.equals(setting)) {
            Locale systemLocale = context.getResources().getConfiguration().locale;
            simplifyData = isSimplified(systemLocale);
            return context;
        }

        String region = SIMPLIFIED.equals(setting) ? "CN" : "TW";
        Locale locale = new Locale("zh", region);
        simplifyData = SIMPLIFIED.equals(setting);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    /** Whether API-backed display text should follow the simplified UI language. */
    public static boolean shouldSimplifyData() {
        return simplifyData;
    }

    private static boolean isSimplified(Locale locale) {
        if (locale == null || !"zh".equals(locale.getLanguage())) return false;
        String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
        return tag.contains("hans") || tag.endsWith("-cn") || tag.endsWith("-sg");
    }
}
