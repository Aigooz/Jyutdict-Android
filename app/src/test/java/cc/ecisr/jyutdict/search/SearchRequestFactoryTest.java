package cc.ecisr.jyutdict.search;

import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_CHARA;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_PRON;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_SHEET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class SearchRequestFactoryTest {
    private static final String ROOT = "https://api.test/v1.0/";

    @Test
    public void leavesOrdinaryQueriesEmptyWhenNoInputWasEntered() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "", new SearchRequestFactory.RequestOptions(), 0);

        assertNull(request);
    }

    @Test
    public void buildsCharacterRequestWithNormalizedInputAndDisplayFlags() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "  嗎  ", new SearchRequestFactory.RequestOptions(), 0b1100);

        assertNotNull(request);
        assertEquals(QUERYING_CHARA | 0b1100, request.responseMode);
        assertEquals("嗎", request.historyQuery);
        assertTrue(request.url.contains("/detail?"));
        assertEquals("吗", Uri.parse(request.url).getQueryParameter("chara"));
    }

    @Test
    public void buildsPronunciationRequestWithAllJyutpingPartsAndTone() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, " Jyut6 ", new SearchRequestFactory.RequestOptions()
                        .setBookFilter(SearchRequestFactory.BookFilter.FANWAN_ONLY), 0);

        assertNotNull(request);
        assertEquals(QUERYING_PRON, request.responseMode);
        assertTrue(request.url.contains("in=j"));
        assertTrue(request.url.contains("nu=yu"));
        assertTrue(request.url.contains("co=t"));
        assertTrue(request.url.contains("to=6"));
        assertTrue(request.url.contains("wanshyu=fanwan"));
    }

    @Test
    public void omitsToneParameterWhenTheToneIsNotSpecified() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "dak", new SearchRequestFactory.RequestOptions(), 0);

        assertNotNull(request);
        assertEquals(QUERYING_PRON, request.responseMode);
        assertTrue(request.url.contains("in=d"));
        assertTrue(request.url.contains("nu=a"));
        assertTrue(request.url.contains("co=k"));
        assertTrue(!request.url.contains("&to="));
    }

    @Test
    public void emptySheetInputBecomesRandomSheetRequest() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setReverseSearch(false), 0);

        assertNotNull(request);
        assertEquals(QUERYING_SHEET, request.responseMode);
        assertTrue(request.url.contains("/sheet?"));
        assertTrue(request.url.contains("random=10"));
    }

    @Test
    public void asciiSheetInputUsesTrimModeAndSelectedColumn() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "jyut6", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setSelectedColumn("台山"), 0);

        assertNotNull(request);
        assertEquals(QUERYING_SHEET, request.responseMode);
        assertTrue(request.url.contains("mode=trim"));
        assertTrue(request.url.contains("col="));
    }

    @Test
    public void retrievalColumnUsesPhysicalColumnNameForPronunciationSearch() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "jyut6", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setSelectedColumn("檢"), 0);

        assertNotNull(request);
        assertTrue(request.url.contains("mode=trim"));
        assertTrue(request.url.contains("col=%E6%AA%A2")
                || request.url.contains("col=檢"));
        assertTrue(!request.url.contains("%E6%AA%A2%E7%B4%A2%E9%9F%B3")
                && !request.url.contains("檢索音"));
    }

    @Test
    public void chineseSheetInputUsesFuzzyModeAndDoesNotSendColumn() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "粤", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setSelectedColumn("台山"), 0);

        assertNotNull(request);
        assertTrue(request.url.contains("mode=fuzzy"));
        assertTrue(!request.url.contains("&col="));
    }

    @Test
    public void chineseFuzzySearchDoesNotSendRetrievalColumnForCharacterInput() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "粵", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setSelectedColumn("檢"), 0);

        assertNotNull(request);
        assertTrue(request.url.contains("mode=fuzzy"));
        assertTrue(!request.url.contains("&col="));
        assertEquals("粵", Uri.parse(request.url).getQueryParameter("q"));
        assertEquals("粵", request.historyQuery);
    }

    @Test
    public void reverseSheetSearchUsesConciseOrMeaningMode() {
        SearchRequestFactory.SearchRequest concise = SearchRequestFactory.create(
                ROOT, "香", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setReverseSearch(true)
                        .setConciseReverseSearch(true), 0);
        SearchRequestFactory.SearchRequest meaning = SearchRequestFactory.create(
                ROOT, "香", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setReverseSearch(true)
                        .setConciseReverseSearch(false), 0);

        assertNotNull(concise);
        assertNotNull(meaning);
        assertTrue(concise.url.contains("mode=concise"));
        assertTrue(meaning.url.contains("mode=meaning"));
    }

    @Test
    public void regexOverridesSheetSearchModeAndKeepsRawInput() {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "a&b", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setRegexMode(true), 0);

        assertNotNull(request);
        assertTrue(request.url.contains("mode=regex"));
        assertTrue(request.url.contains("%26") || request.url.contains("&amp;"));
    }

    @Test
    public void checkedRequestAcceptsValidRegularExpressions() {
        SearchRequestFactory.CreateResult result = SearchRequestFactory.createChecked(
                ROOT, "^香.$", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setRegexMode(true), 0);

        assertNull(result.error);
        assertNotNull(result.request);
        assertTrue(result.request.url.contains("mode=regex"));
    }

    @Test
    public void checkedRequestRejectsMalformedOrUnsafeRegularExpressionsLocally() {
        String[] invalidPatterns = {"^[", "(", "[", "*", "\\", "(a+)+$"};

        for (String pattern : invalidPatterns) {
            SearchRequestFactory.CreateResult result = SearchRequestFactory.createChecked(
                    ROOT, pattern, new SearchRequestFactory.RequestOptions()
                            .setSheetMode(true)
                            .setRegexMode(true), 0);
            assertEquals(pattern, SearchRequestFactory.ValidationError.INVALID_REGEX,
                    result.error);
            assertNull(result.request);
        }
    }

    @Test
    public void checkedRequestRejectsOversizedInputLocally() {
        SearchRequestFactory.CreateResult regex = SearchRequestFactory.createChecked(
                ROOT, "a".repeat(SearchRequestFactory.SHEET_REGEX_MAX_BYTES + 1),
                new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true)
                        .setRegexMode(true), 0);
        SearchRequestFactory.CreateResult detail = SearchRequestFactory.createChecked(
                ROOT, "a".repeat(SearchRequestFactory.DETAIL_QUERY_MAX_BYTES + 1),
                new SearchRequestFactory.RequestOptions(), 0);

        assertEquals(SearchRequestFactory.ValidationError.QUERY_TOO_LONG, regex.error);
        assertNull(regex.request);
        assertEquals(SearchRequestFactory.ValidationError.QUERY_TOO_LONG, detail.error);
        assertNull(detail.request);
    }

    @Test
    public void checkedRequestRejectsUnsupportedTrimCharactersLocally() {
        SearchRequestFactory.CreateResult result = SearchRequestFactory.createChecked(
                ROOT, "mat1;.*", new SearchRequestFactory.RequestOptions()
                        .setSheetMode(true), 0);

        assertEquals(SearchRequestFactory.ValidationError.INVALID_TRIM_QUERY, result.error);
        assertNull(result.request);
    }

    @Test
    public void whitespaceOnlyNonSheetInputDoesNotCreateARequest() {
        SearchRequestFactory.CreateResult result = SearchRequestFactory.createChecked(
                ROOT, "   \t  ", new SearchRequestFactory.RequestOptions(), 0);

        assertNull(result.error);
        assertNull(result.request);
    }

    @Test
    public void mapsEveryBookFilterToTheExistingApiContract() {
        assertEquals(null, createBookFiltered(SearchRequestFactory.BookFilter.ALL));
        assertEquals("fanwan",
                createBookFiltered(SearchRequestFactory.BookFilter.FANWAN_ONLY));
        assertEquals("jingwaa",
                createBookFiltered(SearchRequestFactory.BookFilter.JINGWAA_ONLY));
        assertEquals("none", createBookFiltered(SearchRequestFactory.BookFilter.NONE));
    }

    private static String createBookFiltered(SearchRequestFactory.BookFilter filter) {
        SearchRequestFactory.SearchRequest request = SearchRequestFactory.create(
                ROOT, "ji3", new SearchRequestFactory.RequestOptions()
                        .setBookFilter(filter), 0);
        assertNotNull(request);
        String marker = "wanshyu=";
        int index = request.url.indexOf(marker);
        return index < 0 ? null : request.url.substring(index + marker.length());
    }
}
