package cc.ecisr.jyutdict.search;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.Locale;

/** Conservative normalization for user-entered dictionary queries. */
public final class SearchQueryNormalizer {
    private SearchQueryNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String text = input.trim();
        if (text.isEmpty()) return "";
        StringBuilder output = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            output.appendCodePoint(normalizeCodePoint(codePoint));
        }
        return collapseWhitespace(output.toString());
    }

    /** Script-folded key used for equality and deduplication, not for display. */
    public static String matchKey(String input) {
        String normalized = normalize(input);
        return ZhConverterUtil.containsChinese(normalized)
                ? ZhConverterUtil.toSimple(normalized) : normalized;
    }

    private static int normalizeCodePoint(int codePoint) {
        if (codePoint == 0x3000) return ' ';
        if (codePoint >= 0xFF01 && codePoint <= 0xFF5E) return codePoint - 0xFEE0;
        switch (codePoint) {
            case 0x2070: return '0';
            case 0x00B9: return '1';
            case 0x00B2: return '2';
            case 0x00B3: return '3';
            case 0x2074: return '4';
            case 0x2075: return '5';
            case 0x2076: return '6';
            case 0x2077: return '7';
            case 0x2078: return '8';
            case 0x2079: return '9';
            case 0x2080: return '0';
            case 0x2081: return '1';
            case 0x2082: return '2';
            case 0x2083: return '3';
            case 0x2084: return '4';
            case 0x2085: return '5';
            case 0x2086: return '6';
            case 0x2087: return '7';
            case 0x2088: return '8';
            case 0x2089: return '9';
            case 0x2018:
            case 0x2019:
            case 0x201B:
            case 0xFF07: return '\'';
            case 0x201C:
            case 0x201D:
            case 0xFF02: return '"';
            case 0x2010:
            case 0x2011:
            case 0x2012:
            case 0x2013:
            case 0x2014:
            case 0x2015:
            case 0x2212:
            case 0xFE58:
            case 0xFE63:
            case 0xFF0D: return '-';
            case 0x00B7:
            case 0x2022:
            case 0x2027:
            case 0x30FB:
            case 0xFF65: return ' ';
            case 0x3001:
            case 0xFF0C: return ',';
            case 0x3002:
            case 0xFF0E: return '.';
            case 0xFF1A: return ':';
            case 0xFF1B: return ';';
            case 0xFF1F: return '?';
            case 0xFF01: return '!';
            default:
                return codePoint;
        }
    }

    private static String collapseWhitespace(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                if (result.length() > 0) pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    public static String normalizeJyutping(String input) {
        return normalize(input).toLowerCase(Locale.ROOT);
    }
}
