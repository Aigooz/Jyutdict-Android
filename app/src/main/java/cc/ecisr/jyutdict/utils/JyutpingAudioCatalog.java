package cc.ecisr.jyutdict.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline index of the syllables available in Jyutnet's kt audio set.
 * Keeping the index in the app avoids a network round-trip for known misses.
 */
public final class JyutpingAudioCatalog {
    private final Set<String> syllables = new HashSet<>();

    private JyutpingAudioCatalog() {}

    public static JyutpingAudioCatalog read(Context context) {
        try (InputStream input = context.getResources().openRawResource(
                cc.ecisr.jyutdict.R.raw.jyutnet_audio_syllables);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) lines.add(value);
            }
            return fromLines(lines);
        } catch (Exception exception) {
            return new JyutpingAudioCatalog();
        }
    }

    static JyutpingAudioCatalog fromLines(List<String> lines) {
        JyutpingAudioCatalog catalog = new JyutpingAudioCatalog();
        catalog.syllables.addAll(lines);
        return catalog;
    }

    public boolean contains(String initial, String fin, String tone) {
        return syllables.contains(initial + fin + tone);
    }

    /** A phrase is playable only when every syllable has a recording. */
    public List<String[]> resolveSequence(String reading) {
        List<String> sequence = PronunciationSequence.parse(reading);
        if (sequence.isEmpty()) return java.util.Collections.emptyList();
        List<String[]> result = new ArrayList<>(sequence.size());
        for (String syllable : sequence) {
            String[] parts = JyutpingUtil.splitJyutping(syllable);
            String[] resolved = resolve(parts[0], parts[1], parts[2]);
            if (resolved == null) return java.util.Collections.emptyList();
            result.add(resolved);
        }
        return result;
    }

    /** Returns [initial, final, tone] for the first available spelling, or null. */
    public String[] resolve(String initial, String fin, String tone) {
        if (contains(initial, fin, tone)) return new String[]{initial, fin, tone};

        String normalizedTone = normalizeTone(tone);
        if (!normalizedTone.equals(tone) && contains(initial, fin, normalizedTone)) {
            return new String[]{initial, fin, normalizedTone};
        }

        String normalizedInitial = normalizeInitial(initial);
        if (!normalizedInitial.equals(initial)) {
            if (contains(normalizedInitial, fin, tone)) {
                return new String[]{normalizedInitial, fin, tone};
            }
            if (!normalizedTone.equals(tone)
                    && contains(normalizedInitial, fin, normalizedTone)) {
                return new String[]{normalizedInitial, fin, normalizedTone};
            }
        }

        String normalizedFin = normalizeFinal(initial, fin);
        if (!normalizedFin.equals(fin)) {
            if (contains(initial, normalizedFin, tone)) {
                return new String[]{initial, normalizedFin, tone};
            }
            if (contains(normalizedInitial, normalizedFin, tone)) {
                return new String[]{normalizedInitial, normalizedFin, tone};
            }
            if (!normalizedTone.equals(tone) && contains(initial, normalizedFin, normalizedTone)) {
                return new String[]{initial, normalizedFin, normalizedTone};
            }
            if (!normalizedTone.equals(tone)
                    && contains(normalizedInitial, normalizedFin, normalizedTone)) {
                return new String[]{normalizedInitial, normalizedFin, normalizedTone};
            }
        }
        return null;
    }

    private static String normalizeInitial(String initial) {
        return "nj".equals(initial) ? "n" : initial;
    }

    private static String normalizeFinal(String initial, String fin) {
        switch (fin) {
            case "ie":
            case "ee":
                return "e";
            case "eak":
                return "ak";
            case "oo":
                return "o";
            case "oa":
                return "aa";
            case "ae":
                return "a";
            case "y":
                return "yu";
            case "ir":
                return isSibilantInitial(initial) ? "i" : fin;
            default:
                return fin;
        }
    }

    private static boolean isSibilantInitial(String initial) {
        return "z".equals(initial) || "c".equals(initial) || "s".equals(initial);
    }

    private static String normalizeTone(String tone) {
        if (tone.length() == 1 && Character.isDigit(tone.charAt(0))) {
            return mapCheckedTone(tone);
        }
        if (tone.matches("[1-9][*'*]?")) return mapCheckedTone(tone.substring(0, 1));
        return tone;
    }

    /** 泛粤字表以 7/8/9 標陰入/中入/陽入，音庫按標準粵拼 1/3/6 收錄。 */
    private static String mapCheckedTone(String tone) {
        switch (tone) {
            case "7": return "1";
            case "8": return "3";
            case "9": return "6";
            default: return tone;
        }
    }
}
