package cc.ecisr.jyutdict.utils;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

/**
 * Converts dictionary data for display only. Search terms, API keys and character
 * heads are deliberately left in their source form.
 */
public final class ChineseTextConverter {
    private ChineseTextConverter() {
    }

    public static String display(String text) {
        if (text == null || text.isEmpty() || !LocaleUtil.shouldSimplifyData()) {
            return text;
        }
        return ZhConverterUtil.toSimple(text);
    }
}
