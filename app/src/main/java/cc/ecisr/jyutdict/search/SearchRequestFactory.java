package cc.ecisr.jyutdict.search;

import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_CHARA;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_PRON;
import static cc.ecisr.jyutdict.utils.EnumConst.QUERYING_SHEET;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import cc.ecisr.jyutdict.struct.FjbHeaderInfo;
import cc.ecisr.jyutdict.utils.ApiUrlBuilder;
import cc.ecisr.jyutdict.utils.JyutpingUtil;
import cc.ecisr.jyutdict.utils.StringUtil;

/** Builds API requests without depending on activity views or lifecycle state. */
public final class SearchRequestFactory {
    public static final String DEFAULT_API_ROOT = "https://jyutdict.org/api/v1.0/";
    public static final int SHEET_QUERY_MAX_BYTES = 256;
    public static final int SHEET_REGEX_MAX_BYTES = 128;
    public static final int SHEET_TRIM_QUERY_MAX_BYTES = 64;
    public static final int DETAIL_QUERY_MAX_BYTES = 256;

    public enum ValidationError {
        QUERY_TOO_LONG,
        INVALID_REGEX,
        INVALID_TRIM_QUERY
    }

    public static final class RequestOptions {
        public boolean sheetMode;
        public boolean reverseSearch;
        public boolean regexMode;
        public boolean conciseReverseSearch = true;
        public String selectedColumn = FjbHeaderInfo.COLUMN_NAME_RETRIEVAL;
        public BookFilter bookFilter = BookFilter.ALL;

        public RequestOptions setSheetMode(boolean value) {
            sheetMode = value;
            return this;
        }

        public RequestOptions setReverseSearch(boolean value) {
            reverseSearch = value;
            return this;
        }

        public RequestOptions setRegexMode(boolean value) {
            regexMode = value;
            return this;
        }

        public RequestOptions setConciseReverseSearch(boolean value) {
            conciseReverseSearch = value;
            return this;
        }

        public RequestOptions setSelectedColumn(String value) {
            selectedColumn = value == null ? "" : value;
            return this;
        }

        public RequestOptions setBookFilter(BookFilter value) {
            bookFilter = value == null ? BookFilter.ALL : value;
            return this;
        }
    }

    public enum BookFilter {
        ALL(null),
        FANWAN_ONLY("fanwan"),
        JINGWAA_ONLY("jingwaa"),
        NONE("none");

        private final String queryValue;

        BookFilter(String queryValue) {
            this.queryValue = queryValue;
        }
    }

    public static final class SearchRequest {
        public final String url;
        public final int responseMode;
        public final String historyQuery;

        private SearchRequest(String url, int responseMode, String historyQuery) {
            this.url = url;
            this.responseMode = responseMode;
            this.historyQuery = historyQuery;
        }
    }

    public static final class CreateResult {
        public final SearchRequest request;
        public final ValidationError error;

        private CreateResult(SearchRequest request, ValidationError error) {
            this.request = request;
            this.error = error;
        }
    }

    private SearchRequestFactory() {
    }

    /**
     * Builds a request and validates the input before it can reach the API.
     */
    public static CreateResult createChecked(String rawInput, RequestOptions options) {
        return createChecked(DEFAULT_API_ROOT, rawInput, options, 0);
    }

    /**
     * Builds a request and validates the input before it can reach the API.
     * Empty non-sheet queries remain valid inputs for {@code create}, which returns
     * {@code null}; checked callers can use {@link #createChecked} for validation details.
     */
    public static CreateResult createChecked(
            String apiRoot,
            String rawInput,
            RequestOptions options,
            int displayModeConfig) {
        Objects.requireNonNull(apiRoot, "apiRoot");
        Objects.requireNonNull(options, "options");
        String input = rawInput == null ? "" : rawInput;
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty() && !(options.sheetMode && !options.reverseSearch)) {
            return new CreateResult(null, null);
        }

        boolean regexMode = options.sheetMode && options.regexMode;
        boolean pronunciationInput = !options.reverseSearch
                && StringUtil.isSheetPronunciationInput(
                        regexMode ? trimmedInput : SearchQueryNormalizer.matchKey(trimmedInput));
        ValidationError validationError = validateInput(
                options, input, trimmedInput, regexMode, pronunciationInput);
        if (validationError != null) {
            return new CreateResult(null, validationError);
        }

        // The sheet endpoint matches the literal traditional glyphs stored in its
        // character column. The detail endpoint performs its own script folding.
        String searchInput = regexMode || options.sheetMode
                ? trimmedInput : SearchQueryNormalizer.matchKey(trimmedInput);
        String historyQuery = trimmedInput;
        int mode;
        ApiUrlBuilder url;

        if (options.sheetMode) {
            mode = QUERYING_SHEET;
            url = ApiUrlBuilder.from(apiRoot, "sheet");
            if (searchInput.isEmpty() && !options.reverseSearch) {
                url.add("random", 10);
            } else {
                url.add("q", searchInput);
                String sheetMode;
                if (pronunciationInput) {
                    sheetMode = "trim";
                } else if (options.reverseSearch) {
                    sheetMode = options.conciseReverseSearch ? "concise" : "meaning";
                } else {
                    sheetMode = "fuzzy";
                }
                if (regexMode) sheetMode = "regex";
                url.add("mode", sheetMode);

                String selectedColumn = options.selectedColumn.isEmpty()
                        ? FjbHeaderInfo.COLUMN_NAME_RETRIEVAL : options.selectedColumn;
                // Character fuzzy search is driven by the dedicated character column on the
                // server; selected location columns apply only to pronunciation searches.
                if (pronunciationInput) {
                    if (!FjbHeaderInfo.COLUMN_NAME_PRONUNCIATION.equals(selectedColumn)) {
                        url.add("col", selectedColumn);
                    }
                }
            }
        } else {
            url = ApiUrlBuilder.from(apiRoot, "detail");
            String jyutpingInput = SearchQueryNormalizer.normalizeJyutping(searchInput);
            if (StringUtil.isJyutpingInput(jyutpingInput)) {
                String[] parts = JyutpingUtil.parseJyutpingQuery(jyutpingInput);
                if (parts != null) {
                    mode = QUERYING_PRON;
                    url.add("in", parts[0])
                            .add("nu", parts[1])
                            .add("co", parts[2]);
                    if (parts[3] != null && !parts[3].isEmpty()) url.add("to", parts[3]);
                    addBookFilter(url, options.bookFilter);
                } else {
                    mode = QUERYING_CHARA;
                    url.add("chara", searchInput);
                }
            } else {
                mode = QUERYING_CHARA;
                url.add("chara", searchInput);
            }
        }
        return new CreateResult(
                new SearchRequest(url.build(), mode | displayModeConfig, historyQuery), null);
    }

    /**
     * Returns {@code null} when the input is empty or does not pass validation.
     * Use {@link #createChecked} when the UI needs to explain why validation failed.
     */
    public static SearchRequest create(String rawInput, RequestOptions options) {
        return createChecked(DEFAULT_API_ROOT, rawInput, options, 0).request;
    }

    public static SearchRequest create(
            String apiRoot,
            String rawInput,
            RequestOptions options,
            int displayModeConfig) {
        return createChecked(apiRoot, rawInput, options, displayModeConfig).request;
    }

    private static ValidationError validateInput(
            RequestOptions options,
            String input,
            String trimmedInput,
            boolean regexMode,
            boolean pronunciationInput) {
        if (regexMode) {
            if (utf8Length(trimmedInput) > SHEET_REGEX_MAX_BYTES) {
                return ValidationError.QUERY_TOO_LONG;
            }
            return isValidRegex(trimmedInput) ? null : ValidationError.INVALID_REGEX;
        }

        if (options.sheetMode) {
            if (utf8Length(trimmedInput) > SHEET_QUERY_MAX_BYTES) {
                return ValidationError.QUERY_TOO_LONG;
            }
            if (pronunciationInput) {
                if (utf8Length(trimmedInput) > SHEET_TRIM_QUERY_MAX_BYTES) {
                    return ValidationError.QUERY_TOO_LONG;
                }
                if (!trimmedInput.matches("[A-Za-z0-9*? _./+\\-]+")) {
                    return ValidationError.INVALID_TRIM_QUERY;
                }
            }
            return null;
        }

        return utf8Length(trimmedInput) > DETAIL_QUERY_MAX_BYTES
                ? ValidationError.QUERY_TOO_LONG : null;
    }

    /**
     * Mirrors the API's obvious PCRE safety rules and catches malformed syntax
     * before a request can produce a server-side database error.
     */
    static boolean isValidRegex(String pattern) {
        if (pattern.isEmpty()
                || utf8Length(pattern) > SHEET_REGEX_MAX_BYTES
                || pattern.matches("(?s).*[\\x00-\\x1F\\x7F].*")
                || pattern.matches("(?s).*\\\\[1-9].*")
                || pattern.contains("(?P<")
                || pattern.contains("(?P=")
                || pattern.contains("(?R")
                || pattern.contains("(?&")
                || pattern.matches("(?s).*\\(\\?[0-9]+\\)")) {
            return false;
        }
        if (!hasBalancedRegexStructure(pattern)) {
            return false;
        }
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private static boolean hasBalancedRegexStructure(String pattern) {
        Deque<Integer> opens = new ArrayDeque<>();
        boolean escaped = false;
        boolean inClass = false;
        boolean previousCanRepeat = false;
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (escaped) {
                escaped = false;
                previousCanRepeat = true;
                continue;
            }
            if (character == '\\') {
                if (index + 1 >= pattern.length()) return false;
                escaped = true;
                previousCanRepeat = false;
                continue;
            }
            if (inClass) {
                if (character == ']') {
                    inClass = false;
                    previousCanRepeat = true;
                }
                continue;
            }
            if (character == '[') {
                inClass = true;
                previousCanRepeat = false;
                continue;
            }
            if (character == '(') {
                opens.push(index);
                previousCanRepeat = false;
                continue;
            }
            if (character == ')') {
                if (opens.isEmpty()) return false;
                int open = opens.pop();
                if (hasQuantifierAfter(pattern, index)
                        && containsUnescapedQuantifier(pattern.substring(open + 1, index))) {
                    return false;
                }
                previousCanRepeat = true;
                continue;
            }
            if (character == '*' || character == '+' || character == '?') {
                if (!previousCanRepeat) return false;
                previousCanRepeat = true;
                continue;
            }
            if (character == '{' && !previousCanRepeat) return false;
            if (character == '|') {
                previousCanRepeat = false;
                continue;
            }
            previousCanRepeat = true;
        }
        return !escaped && !inClass && opens.isEmpty();
    }

    private static boolean hasQuantifierAfter(String pattern, int groupEnd) {
        if (groupEnd + 1 >= pattern.length()) return false;
        char next = pattern.charAt(groupEnd + 1);
        return next == '*' || next == '+' || next == '?' || next == '{';
    }

    private static boolean containsUnescapedQuantifier(String group) {
        boolean escaped = false;
        boolean inClass = false;
        for (int index = 0; index < group.length(); index++) {
            char character = group.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (inClass) {
                if (character == ']') inClass = false;
                continue;
            }
            if (character == '[') {
                inClass = true;
                continue;
            }
            if (character == '*' || character == '+' ||
                    character == '?' || character == '{') {
                return true;
            }
        }
        return false;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void addBookFilter(ApiUrlBuilder url, BookFilter filter) {
        if (filter.queryValue != null) url.add("wanshyu", filter.queryValue);
    }
}
