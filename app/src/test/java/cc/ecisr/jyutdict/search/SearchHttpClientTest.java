package cc.ecisr.jyutdict.search;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import cc.ecisr.jyutdict.utils.HttpUtil;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class SearchHttpClientTest {
    @Test
    public void forwardsRequestsAndCancellationToHttpUtil() {
        RecordingHttpUtil http = new RecordingHttpUtil();
        SearchHttpClient client = new SearchHttpClient(http);
        HttpUtil.Callback callback = new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
            }

            @Override
            public void onFailure(HttpUtil.RequestError error) {
            }
        };

        client.search("https://example.test/search", callback);
        client.cancel();

        assertTrue(http.enqueued);
        assertSame(callback, http.callback);
        assertEqualsOne("https://example.test/search", http.url);
        assertTrue(http.cancelled);
    }

    private static final class RecordingHttpUtil extends HttpUtil {
        boolean enqueued;
        boolean cancelled;
        String url;
        Callback callback;

        @Override
        public synchronized void enqueue(String requestUrl, Callback callback) {
            enqueued = true;
            url = requestUrl;
            this.callback = callback;
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
        }
    }

    private static void assertEqualsOne(String expected, String actual) {
        if (!expected.equals(actual)) {
            fail("Expected " + expected + ", but was " + actual);
        }
    }
}
