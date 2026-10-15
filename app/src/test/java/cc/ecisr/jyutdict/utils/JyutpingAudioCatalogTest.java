package cc.ecisr.jyutdict.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class JyutpingAudioCatalogTest {
    private final JyutpingAudioCatalog catalog = JyutpingAudioCatalog.fromLines(Arrays.asList(
            "aa1", "dak3", "dak6", "dau6", "duk1", "duk3", "duk6", "je1",
            "je2", "je4", "je5", "ne2", "ne5", "ng", "yu1", "zi1", "zyu1"));

    @Test
    public void resolvesExactSyllables() {
        assertArrayEquals(new String[]{"j", "e", "1"}, catalog.resolve("j", "e", "1"));
    }

    @Test
    public void resolvesFieldworkSpellingVariants() {
        assertArrayEquals(new String[]{"j", "e", "1"}, catalog.resolve("j", "ie", "1"));
        assertArrayEquals(new String[]{"j", "e", "4"}, catalog.resolve("j", "ee", "4"));
        assertArrayEquals(new String[]{"n", "e", "5"}, catalog.resolve("nj", "e", "5"));
        assertArrayEquals(new String[]{"n", "e", "2"}, catalog.resolve("nj", "e", "2"));
        assertArrayEquals(new String[]{"z", "yu", "1"}, catalog.resolve("z", "y", "1"));
        assertArrayEquals(new String[]{"z", "i", "1"}, catalog.resolve("z", "ir", "1"));
    }

    @Test
    public void resolvesCheckedToneCategories() {
        assertArrayEquals(new String[]{"d", "uk", "6"}, catalog.resolve("d", "uk", "9"));
        assertArrayEquals(new String[]{"d", "uk", "1"}, catalog.resolve("d", "uk", "7"));
        assertArrayEquals(new String[]{"d", "uk", "3"}, catalog.resolve("d", "uk", "8"));
        assertArrayEquals(new String[]{"d", "au", "6"}, catalog.resolve("d", "au", "9*"));
        assertArrayEquals(new String[]{"d", "ak", "6"}, catalog.resolve("d", "eak", "9"));
        assertArrayEquals(new String[]{"d", "ak", "6"}, catalog.resolve("d", "eak", "6"));
    }

    @Test
    public void doesNotInventMissingReadings() {
        assertNull(catalog.resolve("j", "e", "3"));
        assertNull(catalog.resolve("ngg", "e", "1"));
        assertNull(catalog.resolve("d", "eoh", "6"));
    }

    @Test
    public void resolvesTheWholePhraseInOrderIncludingRepeatedSyllables() {
        java.util.List<String[]> parts = catalog.resolveSequence("je1 duk8 je1");
        assertEquals(3, parts.size());
        assertArrayEquals(new String[]{"j", "e", "1"}, parts.get(0));
        assertArrayEquals(new String[]{"d", "uk", "3"}, parts.get(1));
        assertArrayEquals(parts.get(0), parts.get(2));
    }

    @Test
    public void doesNotPlayAnIncompletePhraseOrTreatTaishanToneValuesAsCantoneseTones() {
        assertTrue(catalog.resolveSequence("je1 je3").isEmpty());
        assertTrue(catalog.resolveSequence("je1 unknown3").isEmpty());
        assertTrue(catalog.resolveSequence("hen33").isEmpty());
        assertTrue(catalog.resolveSequence("je1 (duk3)").isEmpty());
    }
}
