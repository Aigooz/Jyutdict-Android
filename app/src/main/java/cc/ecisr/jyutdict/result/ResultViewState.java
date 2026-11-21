package cc.ecisr.jyutdict.result;

import java.util.Objects;

import cc.ecisr.jyutdict.utils.HttpUtil;

/** Pure page-level presentation state for the result screen. */
public final class ResultViewState {
    public enum Page {
        INITIAL,
        LOADING,
        EMPTY,
        PARSE_ERROR,
        REQUEST_ERROR,
        VALIDATION_ERROR,
        NETWORK_ERROR,
        SERVER_ERROR,
        CONTENT
    }

    public final Page page;
    public final boolean retryVisible;
    public final boolean listRevealAllowed;
    public final boolean listDimmed;
    public final boolean clearResults;

    private ResultViewState(
            Page page,
            boolean retryVisible,
            boolean listRevealAllowed,
            boolean listDimmed,
            boolean clearResults) {
        this.page = Objects.requireNonNull(page, "page");
        this.retryVisible = retryVisible;
        this.listRevealAllowed = listRevealAllowed;
        this.listDimmed = listDimmed;
        this.clearResults = clearResults;
    }

    public static ResultViewState initial() {
        return new ResultViewState(Page.INITIAL, false, false, false, false);
    }

    public static ResultViewState loading() {
        return new ResultViewState(Page.LOADING, false, false, true, false);
    }

    public static ResultViewState fromResultCount(int resultCount) {
        return resultCount > 0
                ? new ResultViewState(Page.CONTENT, false, true, false, false)
                : new ResultViewState(Page.EMPTY, true, false, false, false);
    }

    public static ResultViewState parseError() {
        return new ResultViewState(Page.PARSE_ERROR, true, false, false, true);
    }

    public static ResultViewState networkError() {
        return new ResultViewState(Page.NETWORK_ERROR, true, false, false, true);
    }

    public static ResultViewState requestError(HttpUtil.RequestError error) {
        if (error == null) return networkError();
        switch (error.kind()) {
            case HTTP:
                return new ResultViewState(Page.SERVER_ERROR, true, false, false, true);
            case INVALID_REQUEST:
                return new ResultViewState(Page.REQUEST_ERROR, true, false, false, true);
            case RESPONSE_TOO_LARGE:
                return parseError();
            case TIMEOUT:
            case NETWORK:
            default:
                return networkError();
        }
    }

    /** Error state used when the app rejects parameters before sending a request. */
    public static ResultViewState invalidRequestError() {
        return new ResultViewState(Page.VALIDATION_ERROR, true, false, false, true);
    }
}
