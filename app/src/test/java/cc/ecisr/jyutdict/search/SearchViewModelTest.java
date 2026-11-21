package cc.ecisr.jyutdict.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import cc.ecisr.jyutdict.utils.HttpUtil;

public class SearchViewModelTest {
    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @Test
    public void searchPublishesLoadingThenSuccess() {
        FakeClient dataSource = new FakeClient();
        SearchViewModel viewModel = new SearchViewModel(dataSource);
        SearchViewModel.Request request =
                new SearchViewModel.Request("https://example.test/search", 17);

        viewModel.search(request);

        SearchViewModel.State loading = viewModel.getUiState().getValue();
        assertNotNull(loading);
        assertEquals(SearchViewModel.Status.LOADING, loading.status);
        assertSame(request, loading.request);

        dataSource.succeed("[]");

        SearchViewModel.State success = viewModel.getUiState().getValue();
        assertNotNull(success);
        assertEquals(SearchViewModel.Status.SUCCESS, success.status);
        assertSame(request, success.request);
        assertEquals("[]", success.responseBody);
    }

    @Test
    public void failureIsRepresentedInUiState() {
        FakeClient dataSource = new FakeClient();
        SearchViewModel viewModel = new SearchViewModel(dataSource);
        SearchViewModel.Request request =
                new SearchViewModel.Request("https://example.test/search", 23);

        viewModel.search(request);
        HttpUtil.RequestError error = new HttpUtil.RequestError(
                HttpUtil.ErrorKind.TIMEOUT, 0, "timeout");
        dataSource.fail(error);

        SearchViewModel.State failure = viewModel.getUiState().getValue();
        assertNotNull(failure);
        assertEquals(SearchViewModel.Status.ERROR, failure.status);
        assertSame(request, failure.request);
        assertSame(error, failure.error);
    }

    @Test
    public void cancelIsForwardedToTheTransport() {
        FakeClient dataSource = new FakeClient();
        SearchViewModel viewModel = new SearchViewModel(dataSource);

        viewModel.cancel();

        assertTrue(dataSource.cancelled);
        SearchViewModel.State idle = viewModel.getUiState().getValue();
        assertNotNull(idle);
        assertEquals(SearchViewModel.Status.IDLE, idle.status);
    }

    @Test
    public void cancelDropsLateTransportCallbacks() {
        FakeClient dataSource = new FakeClient();
        SearchViewModel viewModel = new SearchViewModel(dataSource);
        SearchViewModel.Request request =
                new SearchViewModel.Request("https://example.test/search", 17);

        viewModel.search(request);
        viewModel.cancel();
        dataSource.succeed("stale result");

        SearchViewModel.State afterSuccess = viewModel.getUiState().getValue();
        assertNotNull(afterSuccess);
        assertEquals(SearchViewModel.Status.IDLE, afterSuccess.status);

        dataSource.fail(new HttpUtil.RequestError(
                HttpUtil.ErrorKind.NETWORK, 0, "stale failure"));

        SearchViewModel.State afterFailure = viewModel.getUiState().getValue();
        assertNotNull(afterFailure);
        assertEquals(SearchViewModel.Status.IDLE, afterFailure.status);
    }

    @Test
    public void newerRequestDropsCallbacksFromOlderRequest() {
        FakeClient dataSource = new FakeClient();
        SearchViewModel viewModel = new SearchViewModel(dataSource);
        SearchViewModel.Request first =
                new SearchViewModel.Request("https://example.test/first", 17);
        SearchViewModel.Request second =
                new SearchViewModel.Request("https://example.test/second", 23);

        viewModel.search(first);
        HttpUtil.Callback firstCallback = dataSource.callback;
        viewModel.search(second);

        firstCallback.onSuccess("stale result");

        SearchViewModel.State state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(SearchViewModel.Status.LOADING, state.status);
        assertSame(second, state.request);

        dataSource.succeed("current result");
        state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(SearchViewModel.Status.SUCCESS, state.status);
        assertSame(second, state.request);
        assertEquals("current result", state.responseBody);
    }

    private static final class FakeClient implements SearchViewModel.Client {
        private HttpUtil.Callback callback;
        private boolean cancelled;

        @Override
        public void search(String url, HttpUtil.Callback callback) {
            this.callback = callback;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        void succeed(String responseBody) {
            callback.onSuccess(responseBody);
        }

        void fail(HttpUtil.RequestError error) {
            callback.onFailure(error);
        }
    }
}
