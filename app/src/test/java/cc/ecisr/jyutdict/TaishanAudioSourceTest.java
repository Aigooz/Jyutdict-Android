package cc.ecisr.jyutdict;

import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class TaishanAudioSourceTest {
    private static final String HEAD = "<html><body>Number of matches: 2<br>";

    private static String row(String word, String ipa, String href, String mandarin, String english) {
        return "<b>" + word + "</b> <a href='" + href + "'>&#9835;</a> [" + ipa
                + "]&nbsp;<i></i><span>cantonese:</span>unused<span>mandarin:</span>"
                + mandarin + "&nbsp;<span>english:</span>" + english + "<br><hr>";
    }

    private static String document(String rows) {
        return HEAD + rows + "</body></html>";
    }

    @Test
    public void parsesActualSourceStructureAndSeparatesPronounSenses() throws Exception {
        List<TaishanAudioSource.Recording> result = TaishanAudioSource.parse(document(
                row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211", "I, me, my")
                        + row("\u6211", "\u014b\u0254i21", "Prowords/audio/2.mp3", "\u6211\u4eec(\u7684)", "We, us, our")), "\u6211");
        assertEquals(2, result.size());
        assertEquals("https://www.taishandict.com/Prowords/audio/1.mp3", result.get(0).url);
        assertEquals("I, me, my", result.get(0).english);
        assertEquals("\u6211\u4eec(\u7684)", result.get(1).mandarin);
        assertSame(result.get(0), TaishanAudioSource.uniqueMatch(result, "\u014b\u0254i33"));
        assertSame(result.get(1), TaishanAudioSource.uniqueMatch(result, "\u014b\u0254i21"));
    }

    @Test
    public void parsesOnlineDictionaryRowsAndExposesSourceLimit() throws Exception {
        String html = "<html><body>Number of matches: 2<br>"
                + "<b>台山</b> <a href='Places/Audio/1.mp3'>♪</a> [hɔi22 san33]"
                + " <span>cantonese:</span>台山 <span>mandarin:</span>台山"
                + " <span>english:</span>Taishan<br>Name of a county<hr>"
                + "<b>台山话</b> <a href='Words/Audio/2.mp3'>♪</a> [hɔi22 san33 va325]"
                + " <span>cantonese:</span>台山话 <span>mandarin:</span>台山话"
                + " <span>english:</span>Taishanese dialect<hr></body></html>";
        TaishanAudioSource.DictionaryResult result = TaishanAudioSource.parseDictionary(html);
        assertEquals(2, result.matchCount);
        assertFalse(result.truncated);
        assertEquals(2, result.entries.size());
        assertEquals("台山", result.entries.get(0).headword);
        assertEquals("hɔi22 san33", result.entries.get(0).ipa);
        assertEquals("台山", result.entries.get(0).cantonese);
        assertEquals("Taishan", result.entries.get(0).english);
        assertEquals("Name of a county", result.entries.get(0).definition);
        assertEquals("https://www.taishandict.com/Places/Audio/1.mp3",
                result.entries.get(0).audioUrl);
        assertEquals("Taishanese dialect", result.entries.get(1).english);
    }

    @Test
    public void parsesDictionaryZeroResultsAndHundredEntryLimit() throws Exception {
        TaishanAudioSource.DictionaryResult empty = TaishanAudioSource.parseDictionary(
                "<body>Sorry, no match. Number of matches: 0<br></body>");
        assertTrue(empty.entries.isEmpty());
        assertEquals(0, empty.matchCount);
        TaishanAudioSource.DictionaryResult truncated = TaishanAudioSource.parseDictionary(
                "<body>Number of matches exceeded limit. Only the first 100 entries displayed"
                        + "<hr><b>*</b> [lɔi33] <span>english:</span>cover</body>");
        assertTrue(truncated.truncated);
        assertEquals(100, truncated.matchCount);
        assertEquals(1, truncated.entries.size());
    }

    @Test
    public void excludesCompoundsAndMandarinSynonymsDespiteFullSearch() throws Exception {
        List<TaishanAudioSource.Recording> result = TaishanAudioSource.parse(document(
                row("\u83dc", "t\u0254i33", "Food/audio/1.mp3", "\u83dc", "Vegetables")
                        + row("\u901a\u83dc", "zu\u014b33t\u0254i21", "Food/audio/2.mp3", "\u7a7a\u5fc3\u83dc", "Water spinach")
                        + row("\u83dc(\u5fc3)", "t\u0254i33", "Food/audio/3.mp3", "\u83dc", "Greens")), "\u83dc");
        assertEquals(1, result.size());
        assertNull(TaishanAudioSource.uniqueMatch(result, "zo\u014b33t'\u0254i21"));
    }

    @Test
    public void traditionalQueryMatchesSimplifiedSourceHeadword() throws Exception {
        assertEquals("\u6708\u5149", TaishanAudioSource.queryWord(" \u6708\u5149 "));
        assertEquals(1, TaishanAudioSource.parse(document(row("\u8bdd", "va22", "Words/Audio/1.mp3",
                "\u8bdd", "Speech")), "\u8a71").size());
    }

    @Test
    public void onlyIgnoresFormattingNotTonesOrAccents() {
        TaishanAudioSource.Recording recording = recording("ni33 h\u0254u55");
        assertTrue(recording.matchesReading("[ni\u00b3\u00b3\u00a0h\u0254u\u2075\u2075]"));
        assertFalse(recording.matchesReading("ni33 h\u0254u53"));
        assertFalse(recording.matchesReading("ni33 hou55"));
        assertFalse(recording.matchesReading("ni33 h\u0254u"));
        assertFalse(recording.matchesReading("ni33/h\u0254u55"));
        assertFalse(recording.matchesReading(null));
    }

    @Test
    public void differentBookSenseOfOneCannotMatchOneRecording() {
        assertFalse(recording("yit55").matchesReading("it55s\u0251k55"));
    }

    @Test
    public void missingToneAlternativeAndEmptyReadingsNeverAutoplay() {
        for (String ipa : List.of("", " ", "\u014but32/\u014but215", "\u014but31k\u0254\u014b", "?", "[ ]")) {
            assertFalse(ipa, recording(ipa).matchesReading(ipa));
        }
        assertFalse(recording("\u014but32 g\u0254\u014b33").matchesReading("\u014but31k\u0254\u014b"));
        assertFalse(recording("\u014bit215").matchesReading("\u014bit31"));
        assertFalse(recording("h\u025b\u026an33").matchesReading("hen33"));
    }

    @Test
    public void simplifiedSourceStopConventionsCannotBeTreatedAsStandardIpa() {
        for (String ipa : List.of("t\u025bt33", "p\u0254i33", "ka55", "ba55", "da55", "ga55",
                "tsa55", "dza55", "ia55", "ni33 ta55")) {
            assertFalse(ipa, recording(ipa).matchesReading(ipa));
        }
        assertTrue(recording("\u014bit55").matchesReading("\u014bit55"));
    }

    @Test
    public void ambiguousExactReadingsRequireExplicitChoice() throws Exception {
        List<TaishanAudioSource.Recording> candidates = TaishanAudioSource.parse(document(
                row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211", "I")
                        + row("\u6211", "\u014b\u0254i33", "Prowords/audio/3.mp3", "\u6211", "Another sense")), "\u6211");
        assertNull(TaishanAudioSource.uniqueMatch(candidates, "\u014b\u0254i33"));
    }

    @Test
    public void unavailableRecordingIsRemovedWithoutHidingOtherReadings() throws Exception {
        List<TaishanAudioSource.Recording> candidates = TaishanAudioSource.parse(document(
                row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211", "I")
                        + row("\u6211", "\u014b\u0254i21", "Prowords/audio/2.mp3", "\u6211\u4eec", "We")), "\u6211");
        Set<String> unavailable = new HashSet<>();
        unavailable.add(candidates.get(0).url);

        List<TaishanAudioSource.Recording> available =
                TaishanAudioSource.excludingUnavailable(candidates, unavailable);

        assertEquals(1, available.size());
        assertSame(candidates.get(1), available.get(0));
        assertSame(candidates.get(1), TaishanAudioSource.uniqueMatch(available, "\u014b\u0254i21"));
    }

    @Test
    public void preservesSeparateBracketedAlternativesWithoutAutoplay() throws Exception {
        String html = row("\u6211", "\u014b\u0254i33] or [\u014b\u0254i21", "Prowords/audio/1.mp3", "\u6211", "I");
        List<TaishanAudioSource.Recording> candidates = TaishanAudioSource.parse(document(html), "\u6211");
        assertEquals(1, candidates.size());
        assertEquals("\u014b\u0254i33 / \u014b\u0254i21", candidates.get(0).ipa);
        assertNull(TaishanAudioSource.uniqueMatch(candidates, "\u014b\u0254i33"));
        assertNull(TaishanAudioSource.uniqueMatch(candidates, "\u014b\u0254i21"));
    }

    @Test
    public void punctuationAndNotesCannotMakeAnAutomaticMatch() {
        for (String ipa : List.of("ni33,ni21", "ni33;ni21", "ni33|ni21", "ni33?", "ni33(rare)")) {
            assertFalse(ipa, recording(ipa).matchesReading(ipa));
        }
    }

    @Test
    public void sameAudioAndMandarinDoNotDiscardDifferentEnglishSenses() throws Exception {
        List<TaishanAudioSource.Recording> candidates = TaishanAudioSource.parse(document(
                row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211", "I")
                        + row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211", "Different sense")), "\u6211");
        assertEquals(2, candidates.size());
        assertNull(TaishanAudioSource.uniqueMatch(candidates, "\u014b\u0254i33"));
    }

    @Test
    public void deduplicatesButKeepsDistinctMeaningsAndNotes() throws Exception {
        String first = row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211", "I &amp; me");
        List<TaishanAudioSource.Recording> candidates = TaishanAudioSource.parse(document(first + first
                + row("\u6211", "\u014b\u0254i33", "Prowords/audio/1.mp3", "\u6211\u4eec", "We<br>Literal: [\u014b\u025421]")), "\u6211");
        assertEquals(2, candidates.size());
        assertEquals("I & me", candidates.get(0).english);
        assertTrue(candidates.get(1).english.contains("Literal:"));
    }

    @Test
    public void emptyResultsAreValidButUnexpectedPagesFail() throws Exception {
        assertTrue(TaishanAudioSource.parse("<body>Number of matches: 0</body>", "\u6211").isEmpty());
        assertTrue(TaishanAudioSource.parse("<body>Sorry, but we can not find an entry to match your query ...... "
                + "Number of matches: 0<br></body>", "\u98df").isEmpty());
        for (String html : List.of("", "<body>Welcome to casino</body>", "<body>Server error</body>")) {
            try {
                TaishanAudioSource.parse(html, "\u6211");
                fail("Unexpected page must fail");
            } catch (IOException expected) {}
        }
    }

    @Test
    public void malformedRowsCannotCreateRecordings() throws Exception {
        assertTrue(TaishanAudioSource.parse(document("<b>\u6211</b><hr>"
                + row("\u6211", "", "Prowords/audio/1.mp3", "\u6211", "I")
                + row("\u6211", "", "Prowords/audio/1.mp3", "\u6211", "[Greeting] I")
                + row("\u6211", "ni33", "https://other.example/Audio/1.mp3", "\u6211", "I")), "\u6211").isEmpty());
    }

    @Test
    public void boundsCandidates() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 50; i++) rows.append(row("\u6211", "ni33", "Words/Audio/" + i + ".mp3", "\u6211", "I"));
        assertEquals(30, TaishanAudioSource.parse(document(rows.toString()), "\u6211").size());
    }

    private static TaishanAudioSource.Recording recording(String ipa) {
        return new TaishanAudioSource.Recording("word", ipa, "", "", "https://www.taishandict.com/Audio/1.mp3");
    }
}
