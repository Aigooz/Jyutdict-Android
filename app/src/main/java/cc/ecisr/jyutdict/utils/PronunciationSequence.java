package cc.ecisr.jyutdict.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses complete readings without silently dropping unknown text or missing syllables. */
public final class PronunciationSequence {
    private static final Pattern SYLLABLE = Pattern.compile("[a-z]+(?:[0-9]{1,3}[*']?|\\*{1,2})");
    private static final Pattern GAP = Pattern.compile("[\\s\\u00a0\\u2000-\\u200b-]*");
    private static final int MAX_SYLLABLES = 48;

    private PronunciationSequence() {}

    public static List<String> parse(String reading) {
        if (reading == null) return Collections.emptyList();
        String normalized = reading.toLowerCase(Locale.ROOT).trim();
        List<String> result = new ArrayList<>();
        Matcher matcher = SYLLABLE.matcher(normalized);
        int end = 0;
        while (matcher.find()) {
            if (!GAP.matcher(normalized.substring(end, matcher.start())).matches()) {
                return Collections.emptyList();
            }
            String syllable = matcher.group();
            if (!JyutpingUtil.isValidJpp(syllable) || result.size() >= MAX_SYLLABLES) {
                return Collections.emptyList();
            }
            result.add(syllable);
            end = matcher.end();
        }
        if (!GAP.matcher(normalized.substring(end)).matches()) return Collections.emptyList();
        return Collections.unmodifiableList(result);
    }

    public static List<String> alternatives(String readings) {
        if (readings == null) return Collections.emptyList();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String alternative : readings.replaceAll("[!！]", "").split("[/=;；,，\\n]+")) {
            List<String> syllables = parse(alternative);
            if (!syllables.isEmpty()) result.add(join(syllables));
        }
        return new ArrayList<>(result);
    }

    public static String join(List<String> syllables) {
        StringBuilder result = new StringBuilder();
        for (String syllable : syllables) {
            if (result.length() > 0) result.append(' ');
            result.append(syllable);
        }
        return result.toString();
    }
}
