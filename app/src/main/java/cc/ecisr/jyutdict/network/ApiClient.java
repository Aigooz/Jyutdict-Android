package cc.ecisr.jyutdict.network;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/** JSON API client for public Jyutdict requests. */
public final class ApiClient {
    public static final String BASE_URL = "https://jyutdict.org";
    private static volatile ApiClient instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ApiClient getInstance() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) instance = new ApiClient();
            }
        }
        return instance;
    }

    private ApiClient() {
    }

    public void get(String pathAndQuery, Callback callback) {
        request(pathAndQuery, callback);
    }

    private void request(
            String pathAndQuery,
            Callback callback
    ) {
        executor.execute(() -> execute(pathAndQuery, callback));
    }

    private void execute(
            String pathAndQuery,
            Callback callback
    ) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(pathAndQuery.startsWith("http")
                    ? pathAndQuery
                    : BASE_URL + pathAndQuery);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestProperty("User-Agent", "Jyutdict-Android");
            int status = connection.getResponseCode();
            InputStream rawStream = status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String rawBody = readBody(rawStream, connection.getContentEncoding());
            JSONObject json = rawBody.isEmpty() ? new JSONObject() : new JSONObject(rawBody);
            if (status >= 200 && status < 300) {
                deliver(callback, json, null);
            } else {
                deliver(callback, null, json.optString("error", "HTTP " + status));
            }
        } catch (IOException | JSONException exception) {
            deliver(callback, null, exception.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readBody(InputStream stream, String contentEncoding) throws IOException {
        if (stream == null) return "";
        InputStream decoded = "gzip".equalsIgnoreCase(contentEncoding)
                ? new GZIPInputStream(stream)
                : stream;
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private void deliver(Callback callback, JSONObject response, String error) {
        String message = response == null && (error == null || error.isEmpty())
                ? "Network request failed" : error;
        mainHandler.post(() -> callback.onComplete(response, message));
    }

    public interface Callback {
        void onComplete(JSONObject response, String errorMessage);
    }
}
