package cc.ecisr.jyutdict;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** On-demand lookup through the source's public search form, not a bulk audio mirror. */
final class TaishanAudioRepository {
    private static final long CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long UNAVAILABLE_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private final SharedPreferences cache;
    private final SharedPreferences availability;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    TaishanAudioRepository(Context context) {
        Context appContext = context.getApplicationContext();
        cache = appContext.getSharedPreferences("taishan-audio-lookup", Context.MODE_PRIVATE);
        availability = appContext.getSharedPreferences("taishan-audio-availability", Context.MODE_PRIVATE);
    }

    static void markUnavailable(Context context, String url) {
        String safeUrl = cc.ecisr.jyutdict.utils.TaishanAudioUrls.resolve(url);
        if (safeUrl == null) return;
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences("taishan-audio-availability", Context.MODE_PRIVATE);
        preferences.edit().putLong(safeUrl, System.currentTimeMillis()).apply();
    }

    List<TaishanAudioSource.Recording> lookup(String word) throws IOException {
        String query = TaishanAudioSource.queryWord(word);
        if (query.isEmpty() || query.length() > 100) throw new IOException("Invalid word");
        String html = cache.getString(query, null);
        long age = System.currentTimeMillis() - cache.getLong(query + ":time", 0);
        if (html != null && age >= 0 && age < CACHE_AGE_MS) {
            try {
                checkCancelled();
                List<TaishanAudioSource.Recording> parsed = TaishanAudioSource.parse(html, query);
                List<TaishanAudioSource.Recording> usable = filterUnavailable(parsed);
                if (!parsed.isEmpty()) return usable;
            } catch (IOException exception) {
                checkCancelled();
            }
            cache.edit().remove(query).remove(query + ":time").apply();
        }
        byte[] form = ("data=" + URLEncoder.encode(query, "UTF-8")
                + "&Select1=Taishanese&submit=Search&Select2=Full").getBytes(StandardCharsets.UTF_8);
        HttpURLConnection request = (HttpURLConnection) new URL(TaishanAudioSource.SEARCH).openConnection();
        connection = request;
        try {
            checkCancelled();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(6000);
            request.setReadTimeout(10000);
            request.setRequestMethod("POST");
            request.setRequestProperty("User-Agent", "Yueci-Android (Taishanese reference lookup)");
            request.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            request.setDoOutput(true);
            request.setFixedLengthStreamingMode(form.length);
            try (OutputStream output = request.getOutputStream()) { output.write(form); }
            if (request.getResponseCode() != 200) throw new IOException("Dictionary HTTP error");
            String type = request.getContentType();
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                throw new IOException("Dictionary is not HTML");
            }
            try (InputStream input = request.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > 256 * 1024) throw new IOException("Response too large");
                    output.write(buffer, 0, count);
                }
                html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            checkCancelled();
            List<TaishanAudioSource.Recording> parsed = TaishanAudioSource.parse(html, query);
            List<TaishanAudioSource.Recording> result = filterUnavailable(parsed);
            // Keep known source rows even if their recordings are dead; do not persist true misses.
            if (!parsed.isEmpty()) {
                save(query, html);
            }
            return result;
        } finally {
            request.disconnect();
            connection = null;
        }
    }

    private List<TaishanAudioSource.Recording> filterUnavailable(
            List<TaishanAudioSource.Recording> recordings) {
        long now = System.currentTimeMillis();
        Set<String> unavailable = new HashSet<>();
        SharedPreferences.Editor expired = availability.edit();
        boolean changed = false;
        for (TaishanAudioSource.Recording recording : recordings) {
            long markedAt = availability.getLong(recording.url, 0);
            long age = now - markedAt;
            if (markedAt != 0 && age >= 0 && age < UNAVAILABLE_AGE_MS) {
                unavailable.add(recording.url);
                continue;
            }
            if (markedAt != 0) {
                expired.remove(recording.url);
                changed = true;
            }
        }
        if (changed) expired.apply();
        return TaishanAudioSource.excludingUnavailable(recordings, unavailable);
    }

    private void save(String query, String html) {
        Map<String, ?> existing = cache.getAll();
        List<String> words = new ArrayList<>();
        for (Map.Entry<String, ?> item : existing.entrySet()) {
            if (item.getValue() instanceof String && !item.getKey().equals(query)) words.add(item.getKey());
        }
        Collections.sort(words, (left, right) -> Long.compare(
                cache.getLong(right + ":time", 0), cache.getLong(left + ":time", 0)));
        SharedPreferences.Editor editor = cache.edit();
        for (int i = 19; i < words.size(); i++) {
            editor.remove(words.get(i)).remove(words.get(i) + ":time");
        }
        editor.putString(query, html).putLong(query + ":time", System.currentTimeMillis()).apply();
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
