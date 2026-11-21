package cc.ecisr.jyutdict.pronunciation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class PronunciationResultParserTest {
    @Test
    public void keepsBookOrderAndFormatsPronunciationsInApiFieldOrder() throws Exception {
        String json = "{"
                + "\"韻書\":["
                + "{\"__name\":\"分韻\",\"a1\":{\"平\":\"甲\",\"上\":\"乙\"},"
                + "\"a2\":{\"去\":\"\"}},"
                + "{\"b1\":{\"入\":\"丙\"}}"
                + "]"
                + "}";

        List<PronunciationResultParser.Entry> entries = PronunciationResultParser.parse(
                json,
                name -> "分韻".equals(name) ? "分韵" : name,
                id -> "id=" + id,
                (left, right) -> 0,
                Collections.emptySet(),
                Collections.emptySet());

        assertEquals(2, entries.size());
        assertEquals(PronunciationResultParser.Entry.Kind.BOOK, entries.get(0).kind);
        assertEquals("分韵", entries.get(0).label);
        assertEquals("a1平: 甲\na1上: 乙", entries.get(0).pronunciations);
        assertEquals(new java.util.HashSet<>(Arrays.asList("a1平", "a1上")),
                new java.util.HashSet<>(entries.get(0).readings));
        assertEquals("韻書", entries.get(1).label);
        assertEquals("b1入: 丙", entries.get(1).pronunciations);
        assertEquals(Collections.singletonList("b1入"), entries.get(1).readings);
    }

    @Test
    public void filtersDisplayedBookNamesAndEmptyPronunciations() throws Exception {
        String json = "{"
                + "\"韻書\":["
                + "{\"__name\":\"英華\",\"a1\":{\"平\":\"甲\"}},"
                + "{\"__name\":\"已隱藏\",\"a1\":{\"平\":\"乙\"}},"
                + "{\"__name\":\"空項\",\"a1\":{\"平\":\"\"}}"
                + "]"
                + "}";

        List<PronunciationResultParser.Entry> entries = PronunciationResultParser.parse(
                json,
                name -> "已隱藏".equals(name) ? "hidden" : name,
                id -> "id=" + id,
                (left, right) -> 0,
                new HashSet<>(Collections.singletonList("hidden")),
                Collections.emptySet());

        assertEquals(1, entries.size());
        assertEquals("英華", entries.get(0).label);
    }

    @Test
    public void sortsLocationsGeographicallyThenByIdAndFiltersLocationIds() throws Exception {
        String json = "{"
                + "\"各地\":["
                + "{\"__id\":2,\"a1\":{\"平\":\"二\"}},"
                + "{\"__id\":3,\"a1\":{\"平\":\"三\"}},"
                + "{\"__id\":1,\"a1\":{\"平\":\"一\"}},"
                + "{\"__id\":4,\"a1\":{\"平\":\"四\"}}"
                + "]"
                + "}";
        Set<String> hiddenLocations = new HashSet<>(Arrays.asList("2"));

        List<PronunciationResultParser.Entry> entries = PronunciationResultParser.parse(
                json,
                name -> name,
                id -> "loc-" + id,
                (left, right) -> Integer.compare(rank(left), rank(right)),
                Collections.emptySet(),
                hiddenLocations);

        assertEquals(3, entries.size());
        assertEquals("loc-3", entries.get(0).label);
        assertEquals(3, entries.get(0).locationId);
        assertEquals("loc-1", entries.get(1).label);
        assertEquals("loc-4", entries.get(2).label);
        for (PronunciationResultParser.Entry entry : entries) {
            assertEquals(PronunciationResultParser.Entry.Kind.LOCATION, entry.kind);
        }
    }

    @Test
    public void treatsMissingArraysAsAnEmptyResult() throws Exception {
        List<PronunciationResultParser.Entry> entries = PronunciationResultParser.parse(
                "{}",
                name -> name,
                id -> "id=" + id,
                (left, right) -> 0,
                Collections.emptySet(),
                Collections.emptySet());

        assertTrue(entries.isEmpty());
    }

    private static int rank(int locationId) {
        switch (locationId) {
            case 3: return 0;
            case 1: return 1;
            case 2: return 2;
            case 4: return 3;
            default: return 4;
        }
    }
}
