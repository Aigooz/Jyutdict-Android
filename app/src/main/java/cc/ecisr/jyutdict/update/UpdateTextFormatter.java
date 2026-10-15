package cc.ecisr.jyutdict.update;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

/** Turns GitHub release markdown into short, readable in-app text. */
final class UpdateTextFormatter {
    private UpdateTextFormatter() {}

    static CharSequence styledNotes(String markdown, Context context) {
        String text = markdown == null ? "" : markdown.trim();
        if (text.isEmpty()) return text;

        int accent = cc.ecisr.jyutdict.utils.ColorUtil.resolveThemeColor(
                context, android.R.attr.colorPrimary);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (String rawLine : cleanNotes(text).split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                continue;
            }

            if (line.matches("^#{1,6}\\s+.+$")) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                appendStyledText(builder, line.replaceFirst("^#{1,6}\\s+", ""), accent, true);
                builder.append('\n');
                continue;
            }

            if (line.matches("^\\s*[-*]\\s+.+$")) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                    builder.append('\n');
                }
                int bulletStart = builder.length();
                builder.append("•  ");
                builder.setSpan(new ForegroundColorSpan(accent), bulletStart,
                        bulletStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                appendStyledText(builder, line.replaceFirst("^\\s*[-*]\\s+", ""), 0, false);
                continue;
            }

            appendStyledText(builder, line, 0, false);
            builder.append('\n');
        }

        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\n') {
            builder.delete(builder.length() - 1, builder.length());
        }
        return builder;
    }

    static String plainNotes(String markdown) {
        String text = markdown == null ? "" : markdown.trim();
        if (text.isEmpty()) return text;
        return text.replaceAll("(?m)^```.*$", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("(?m)^\\s*[-*]\\s+", "- ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    static String versionLabel(String releaseTitle) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+\\.\\d+)")
                .matcher(releaseTitle == null ? "" : releaseTitle);
        return matcher.find() ? "v" + matcher.group(1) : releaseTitle;
    }

    private static String cleanNotes(String markdown) {
        return markdown
                .replaceAll("(?m)^```.*$", "")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("\n{3,}", "\n\n");
    }

    private static void appendStyledText(
            SpannableStringBuilder builder, String text, int color, boolean bold) {
        int start = builder.length();
        builder.append(text);
        int end = builder.length();
        if (color != 0) {
            builder.setSpan(new ForegroundColorSpan(color), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (bold) {
            builder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    static String formatBytes(long value) {
        if (value < 1024) return value + " B";
        double size = value / 1024.0;
        if (size < 1024) return String.format(java.util.Locale.US, "%.1f KB", size);
        size /= 1024.0;
        if (size < 1024) return String.format(java.util.Locale.US, "%.1f MB", size);
        return String.format(java.util.Locale.US, "%.2f GB", size / 1024.0);
    }
}
