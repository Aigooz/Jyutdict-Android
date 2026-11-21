package cc.ecisr.jyutdict.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cc.ecisr.jyutdict.utils.HttpUtil;

public class ResultViewStateTest {
    @Test
    public void initialStateHasNoRetryAndNoReveal() {
        ResultViewState state = ResultViewState.initial();

        assertEquals(ResultViewState.Page.INITIAL, state.page);
        assertFalse(state.retryVisible);
        assertFalse(state.listRevealAllowed);
        assertFalse(state.listDimmed);
    }

    @Test
    public void loadingDimsTheExistingList() {
        ResultViewState state = ResultViewState.loading();

        assertEquals(ResultViewState.Page.LOADING, state.page);
        assertFalse(state.retryVisible);
        assertTrue(state.listDimmed);
    }

    @Test
    public void emptyAndErrorStatesExposeRetry() {
        ResultViewState empty = ResultViewState.fromResultCount(0);
        ResultViewState parseError = ResultViewState.parseError();
        ResultViewState networkError = ResultViewState.networkError();

        assertEquals(ResultViewState.Page.EMPTY, empty.page);
        assertEquals(ResultViewState.Page.PARSE_ERROR, parseError.page);
        assertEquals(ResultViewState.Page.NETWORK_ERROR, networkError.page);
        assertTrue(empty.retryVisible);
        assertTrue(parseError.retryVisible);
        assertTrue(networkError.retryVisible);
        assertTrue(networkError.clearResults);
    }

    @Test
    public void nonEmptyResultsRevealTheList() {
        ResultViewState state = ResultViewState.fromResultCount(3);

        assertEquals(ResultViewState.Page.CONTENT, state.page);
        assertFalse(state.retryVisible);
        assertTrue(state.listRevealAllowed);
        assertFalse(state.listDimmed);
    }

    @Test
    public void requestErrorsKeepServerAndTransportFailuresDistinct() {
        ResultViewState timeout = ResultViewState.requestError(new HttpUtil.RequestError(
                HttpUtil.ErrorKind.TIMEOUT, 0, "timeout"));
        ResultViewState network = ResultViewState.requestError(new HttpUtil.RequestError(
                HttpUtil.ErrorKind.NETWORK, 0, "network"));
        ResultViewState server = ResultViewState.requestError(new HttpUtil.RequestError(
                HttpUtil.ErrorKind.HTTP, 500, "HTTP 500"));
        ResultViewState invalid = ResultViewState.requestError(new HttpUtil.RequestError(
                HttpUtil.ErrorKind.INVALID_REQUEST, 0, "invalid"));
        ResultViewState oversized = ResultViewState.requestError(new HttpUtil.RequestError(
                HttpUtil.ErrorKind.RESPONSE_TOO_LARGE, 0, "too large"));

        assertEquals(ResultViewState.Page.NETWORK_ERROR, timeout.page);
        assertEquals(ResultViewState.Page.NETWORK_ERROR, network.page);
        assertEquals(ResultViewState.Page.SERVER_ERROR, server.page);
        assertEquals(ResultViewState.Page.REQUEST_ERROR, invalid.page);
        assertEquals(ResultViewState.Page.PARSE_ERROR, oversized.page);
        assertTrue(server.clearResults);
        assertTrue(invalid.clearResults);
        assertTrue(oversized.clearResults);
    }

    @Test
    public void localValidationErrorClearsResultsAndKeepsThemCleared() {
        ResultViewState state = ResultViewState.invalidRequestError();

        assertEquals(ResultViewState.Page.VALIDATION_ERROR, state.page);
        assertTrue(state.retryVisible);
        assertTrue(state.clearResults);
        assertFalse(state.listRevealAllowed);
    }
}
