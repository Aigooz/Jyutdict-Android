package cc.ecisr.jyutdict.pronunciation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Converts the pronunciation-search API payload into display-ready data.
 *
 * <p>The parser deliberately has no Android UI dependency. Callers provide the
 * label formatting and geographic ordering used by the current screen.</p>
 */
public final class PronunciationResultParser {
    private static final String DEFAULT_BOOK_NAME = "韻書";

    public interface NameTransformer {
        String transform(String name);
    }

    public interface LocationLabelProvider {
        String labelFor(int locationId);
    }

    public static final class Entry {
        public enum Kind { BOOK, LOCATION }

        public final Kind kind;
        public final String label;
        public final int locationId;
        public final String pronunciations;
        public final List<String> readings;

        private Entry(Kind kind, String label, int locationId, String pronunciations,
                      List<String> readings) {
            this.kind = kind;
            this.label = label;
            this.locationId = locationId;
            this.pronunciations = pronunciations;
            this.readings = Collections.unmodifiableList(new ArrayList<>(readings));
        }
    }

    private PronunciationResultParser() {
    }

    public static List<Entry> parse(
            String json,
            NameTransformer bookNameTransformer,
            LocationLabelProvider locationLabels,
            Comparator<Integer> locationOrder,
            Set<String> hiddenBooks,
            Set<String> hiddenLocations) throws JSONException {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(bookNameTransformer, "bookNameTransformer");
        Objects.requireNonNull(locationLabels, "locationLabels");
        Objects.requireNonNull(locationOrder, "locationOrder");
        Objects.requireNonNull(hiddenBooks, "hiddenBooks");
        Objects.requireNonNull(hiddenLocations, "hiddenLocations");

        JSONObject root = new JSONObject(json);
        List<Entry> entries = new ArrayList<>();
        appendBooks(root.optJSONArray("韻書"), bookNameTransformer, hiddenBooks, entries);
        appendLocations(root.optJSONArray("各地"), locationLabels, locationOrder,
                hiddenLocations, entries);
        return Collections.unmodifiableList(entries);
    }

    private static void appendBooks(
            JSONArray books,
            NameTransformer nameTransformer,
            Set<String> hiddenBooks,
            List<Entry> entries) {
        if (books == null) return;
        for (int index = 0; index < books.length(); index++) {
            JSONObject book = books.optJSONObject(index);
            if (book == null) continue;
            String name = nameTransformer.transform(
                    book.optString("__name", DEFAULT_BOOK_NAME));
            if (hiddenBooks.contains(name)) continue;
            Pronunciations pronunciations = parsePronunciations(book, "__name");
            if (!pronunciations.display.isEmpty()) {
                entries.add(new Entry(Entry.Kind.BOOK, name, -1,
                        pronunciations.display, pronunciations.readings));
            }
        }
    }

    private static void appendLocations(
            JSONArray areas,
            LocationLabelProvider locationLabels,
            Comparator<Integer> locationOrder,
            Set<String> hiddenLocations,
            List<Entry> entries) {
        if (areas == null) return;
        List<JSONObject> sortedAreas = new ArrayList<>(areas.length());
        for (int index = 0; index < areas.length(); index++) {
            JSONObject area = areas.optJSONObject(index);
            if (area != null) sortedAreas.add(area);
        }
        Collections.sort(sortedAreas, (left, right) -> {
            int leftId = left.optInt("__id", -1);
            int rightId = right.optInt("__id", -1);
            int comparison = locationOrder.compare(leftId, rightId);
            return comparison == 0 ? Integer.compare(leftId, rightId) : comparison;
        });
        for (JSONObject area : sortedAreas) {
            int locationId = area.optInt("__id", -1);
            if (locationId != -1 && hiddenLocations.contains(String.valueOf(locationId))) {
                continue;
            }
            Pronunciations pronunciations = parsePronunciations(area, "__id");
            if (!pronunciations.display.isEmpty()) {
                entries.add(new Entry(Entry.Kind.LOCATION,
                        locationLabels.labelFor(locationId), locationId,
                        pronunciations.display, pronunciations.readings));
            }
        }
    }

    private static final class Pronunciations {
        final String display;
        final List<String> readings;

        Pronunciations(String display, List<String> readings) {
            this.display = display;
            this.readings = readings;
        }
    }

    private static Pronunciations parsePronunciations(JSONObject source, String metadataKey) {
        StringBuilder display = new StringBuilder();
        List<String> readings = new ArrayList<>();
        Iterator<String> syllables = source.keys();
        while (syllables.hasNext()) {
            String syllable = syllables.next();
            if (metadataKey.equals(syllable)) continue;
            JSONObject tones = source.optJSONObject(syllable);
            if (tones == null) continue;
            Iterator<String> toneKeys = tones.keys();
            while (toneKeys.hasNext()) {
                String tone = toneKeys.next();
                String characters = tones.optString(tone, "");
                if (characters.isEmpty()) continue;
                if (display.length() > 0) display.append('\n');
                display.append(syllable).append(tone).append(": ").append(characters);
                readings.add(syllable + tone);
            }
        }
        return new Pronunciations(display.toString(), readings);
    }
}
