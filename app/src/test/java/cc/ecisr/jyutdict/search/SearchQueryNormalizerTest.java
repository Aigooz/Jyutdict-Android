package cc.ecisr.jyutdict.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SearchQueryNormalizerTest {
    @Test
    public void trimsAndCollapsesWhitespace() {
        assertEquals("廣州 話", SearchQueryNormalizer.normalize("  廣州\t\n 話  "));
        assertEquals("", SearchQueryNormalizer.normalize("  \t "));
        assertEquals("", SearchQueryNormalizer.normalize(null));
    }

    @Test
    public void convertsFullWidthCharactersConservatively() {
        assertEquals("aa123", SearchQueryNormalizer.normalize("ａａ１２３"));
        assertEquals("j 3", SearchQueryNormalizer.normalize("ｊ　３"));
    }

    @Test
    public void convertsSuperscriptAndSubscriptTonesOnly() {
        assertEquals("cung4", SearchQueryNormalizer.normalize("cung⁴"));
        assertEquals("cung4", SearchQueryNormalizer.normalize("cung₄"));
        assertEquals("ngoi21", SearchQueryNormalizer.normalize("ngoi²¹"));
    }

    @Test
    public void normalizesCurlyApostrophesAndKeepsJyutpingLowercase() {
        assertEquals("t'oi2", SearchQueryNormalizer.normalize(" t’oi² "));
        assertEquals("daai6", SearchQueryNormalizer.normalizeJyutping(" DAAI⁶ "));
        assertEquals("t'oi2", SearchQueryNormalizer.normalizeJyutping("T’OI²"));
    }

    @Test
    public void leavesNonAsciiDictionaryCharactersAlone() {
        assertEquals("𠮷", SearchQueryNormalizer.normalize(" 𠮷 "));
        assertEquals(". . .", SearchQueryNormalizer.normalize(". . ."));
    }

    @Test
    public void convertsTraditionalChineseToSimplifiedForSearch() {
        assertEquals("廣州話", SearchQueryNormalizer.normalize(" 廣州話 "));
        assertEquals("广州话", SearchQueryNormalizer.matchKey(" 廣州話 "));
        assertEquals("台湾", SearchQueryNormalizer.matchKey("臺灣"));
        assertEquals("里", SearchQueryNormalizer.matchKey("裏"));
    }

    @Test
    public void normalizesConnectivesAndPunctuationWithoutBreakingSynonyms() {
        assertEquals("aa1-bb2", SearchQueryNormalizer.normalize("aa1—bb2"));
        assertEquals("aa1 bb2", SearchQueryNormalizer.normalize("aa1·bb2"));
        assertEquals("你好,世界", SearchQueryNormalizer.normalize("你好，世界"));
    }
}
