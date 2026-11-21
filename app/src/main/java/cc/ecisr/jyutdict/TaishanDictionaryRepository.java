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
    static final class SearchException extends IOException {
        final boolean cancelled;

        SearchException(String message, boolean cancelled) {
            super(message);
            this.cancelled = cancelled;
        }

        SearchException(String message, boolean cancelled, IOException cause) {
            super(message, cause);
            this.cancelled = cancelled;
        }
    }

    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    TaishanAudioSource.DictionaryResult search(String query, TaishanAudioSource.SearchField field,
                                                boolean partial) throws SearchException {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new SearchException("Invalid query", false);
        }
        byte[] form;
        HttpURLConnection request;
        try {
            form = ("data=" + URLEncoder.encode(normalized, "UTF-8")
                    + "&Select1=" + field.formValue + "&submit=Search&Select2="
                    + (partial ? "Partial" : "Full")).getBytes(StandardCharsets.UTF_8);
            request = (HttpURLConnection) new URL(TaishanAudioSource.SEARCH).openConnection();
        } catch (IOException exception) {
            throw new SearchException("Unable to create dictionary request", false, exception);
        }
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
            try (OutputStream output = request.getOutputStream()) {
                output.write(form);
            } catch (IOException exception) {
                throw translateCancellation(exception, "Unable to send dictionary request");
            }
            if (request.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new SearchException("Dictionary HTTP error", false);
            }
            String contentType = request.getContentType();
            if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/html")) {
                throw new SearchException("Dictionary response is not HTML", false);
            }
            try (InputStream input = request.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > 512 * 1024) {
                        throw new SearchException("Response too large", false);
                    }
                    output.write(buffer, 0, count);
                }
                checkCancelled();
                try {
                    return TaishanAudioSource.parseDictionary(
                            new String(output.toByteArray(), StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    throw new SearchException("Unable to parse dictionary response", false);
                }
            }
        } catch (SearchException exception) {
            throw exception;
        } catch (IOException exception) {
            throw translateCancellation(exception, "Dictionary network error");
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

    private void checkCancelled() throws SearchException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new SearchException("Cancelled", true);
        }
    }

    private SearchException translateCancellation(IOException exception, String message) {
        boolean isCancellation = cancelled || Thread.currentThread().isInterrupted()
                || exception.getMessage() != null
                && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("closed");
        return new SearchException(message, isCancellation, exception);
    }
}
