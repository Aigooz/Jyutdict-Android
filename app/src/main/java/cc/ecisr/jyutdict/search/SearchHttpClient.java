package cc.ecisr.jyutdict.search;

import androidx.annotation.NonNull;

import cc.ecisr.jyutdict.utils.HttpUtil;

/** Adapts the cancellable GET transport to the search state owner. */
final class SearchHttpClient implements SearchViewModel.Client {
    private final HttpUtil request;

    SearchHttpClient() {
        this(new HttpUtil());
    }

    SearchHttpClient(HttpUtil request) {
        this.request = request;
    }

    @Override
    public void search(@NonNull String url, @NonNull HttpUtil.Callback callback) {
        request.enqueue(url, callback);
    }

    @Override
    public void cancel() {
        request.cancel();
    }
}
