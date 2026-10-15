package cc.ecisr.jyutdict;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** On-demand searches against the public Taishanese dictionary form. */
final class TaishanDictionaryRepository {
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    TaishanAudioSource.DictionaryResult search(String query, TaishanAudioSource.SearchField field,
                                                boolean partial) throws IOException {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 100) throw new IOException("Invalid query");
        byte[] form = ("data=" + URLEncoder.encode(normalized, "UTF-8")
                + "&Select1=" + field.formValue + "&submit=Search&Select2="
                + (partial ? "Partial" : "Full")).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection request = (HttpURLConnection) new URL(TaishanAudioSource.SEARCH).openConnection();
        connection = request;
        try {
            checkCancelled();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(7000);
            request.setReadTimeout(12000);
            request.setRequestMethod("POST");
            request.setRequestProperty("User-Agent", "Yueci-Android (Taishanese dictionary search)");
            request.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            request.setDoOutput(true);
            request.setFixedLengthStreamingMode(form.length);
            try (OutputStream output = request.getOutputStream()) { output.write(form); }
            if (request.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Dictionary HTTP error");
            }
            String contentType = request.getContentType();
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/html")) {
                throw new IOException("Dictionary response is not HTML");
            }
            try (InputStream input = request.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > 512 * 1024) throw new IOException("Response too large");
                    output.write(buffer, 0, count);
                }
                checkCancelled();
                return TaishanAudioSource.parseDictionary(
                        new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            request.disconnect();
            connection = null;
        }
    }

    void cancel() {
        cancelled = true;
        HttpURLConnection request = connection;
        if (request != null) request.disconnect();
    }

    private void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
    }
}
