package cc.ecisr.jyutdict;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.ecisr.jyutdict.utils.DiskTextCache;

/** Translates public source text on demand; never changes the dictionary or IPA. */
final class TaishanChineseTranslation implements AutoCloseable {
    interface Callback { void complete(String chinese); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, String> cache = new LinkedHashMap<String, String>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 128;
        }
    };
    private final Map<String, List<Callback>> pending = new LinkedHashMap<>();
    private volatile HttpURLConnection connection;
    private volatile boolean closed;

    TaishanChineseTranslation(Context context) {
        this.context = context.getApplicationContext();
    }

    static String sourceText(String english, String description) {
        String gloss = english == null ? "" : english.trim();
        String detail = description == null ? "" : description.trim();
        return gloss.isEmpty() ? detail : detail.isEmpty() ? gloss : gloss + "\n" + detail;
    }

    static boolean needsTranslation(String text) {
        return text != null && text.matches("(?s).*[A-Za-z]{2,}.*");
    }

    static String chineseMeaning(String mandarin, String cantonese) {
        if (mandarin != null && mandarin.matches("(?s).*[\\u3400-\\u9fff].*")) return mandarin.trim();
        if (cantonese != null && cantonese.matches("(?s).*[\\u3400-\\u9fff].*")) return cantonese.trim();
        return "";
    }

    void translate(String text, Callback callback) {
        if (closed) return;
        if (!needsTranslation(text)) {
            callback.complete(text);
            return;
        }
        if (cache.containsKey(text)) {
            callback.complete(cache.get(text));
            return;
        }
        List<Callback> waiting = pending.get(text);
        if (waiting != null) {
            waiting.add(callback);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(callback);
        pending.put(text, waiting);
        executor.execute(() -> {
            String translated = DiskTextCache.readFresh(context, "taishan-zh-v1:" + text,
                    30L * 24 * 60 * 60 * 1000);
            if (translated != null) translated = translated.trim();
            if (translated == null || translated.isEmpty()) {
                try {
                    StringBuilder result = new StringBuilder();
                    for (String chunk : chunks(text)) {
                        if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                        if (result.length() > 0) result.append('\n');
                        result.append(request(chunk));
                    }
                    translated = result.toString();
                    if (!closed) DiskTextCache.write(context, "taishan-zh-v1:" + text, translated);
                } catch (Exception exception) {
                    translated = null;
                }
            }
            String result = translated;
            main.post(() -> {
                if (closed) return;
                if (result != null) cache.put(text, result);
                List<Callback> callbacks = pending.remove(text);
                if (callbacks != null) for (Callback item : callbacks) item.complete(result);
            });
        });
    }

    // MyMemory limits each query to 500 UTF-8 bytes, not 500 Java characters.
    static List<String> chunks(String text) throws IOException {
        if (text == null || text.length() > 12000) throw new IOException("Text too large");
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\n")) {
            int start = 0;
            while (start < line.length()) {
                int end = start;
                int bytes = 0;
                int boundary = -1;
                while (end < line.length()) {
                    int point = line.codePointAt(end);
                    int size = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
                    if (bytes + size > 450) break;
                    bytes += size;
                    end += Character.charCount(point);
                    if (Character.isWhitespace(point) || point == '.' || point == ';') boundary = end;
                }
                if (end < line.length() && boundary > start) end = boundary;
                String part = line.substring(start, end).trim();
                if (!part.isEmpty()) result.add(part);
                start = end;
                if (result.size() > 32) throw new IOException("Too many translation chunks");
            }
        }
        return result;
    }

    private String request(String text) throws Exception {
        HttpURLConnection request = (HttpURLConnection) new URL(
                "https://api.mymemory.translated.net/get?langpair=en%7Czh-CN&q="
                        + URLEncoder.encode(text, "UTF-8")).openConnection();
        connection = request;
        try {
            if (closed) throw new IOException("Cancelled");
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(5000);
            request.setReadTimeout(7000);
            request.setRequestProperty("User-Agent", "Yueci-Android (public dictionary translation)");
            if (request.getResponseCode() != 200) throw new IOException("Translation HTTP error");
            try (InputStream input = request.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (closed || output.size() + count > 65536) throw new IOException("Invalid response");
                    output.write(buffer, 0, count);
                }
                return parseResponse(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            request.disconnect();
            connection = null;
        }
    }

    static String parseResponse(String json) throws Exception {
        JSONObject response = new JSONObject(json);
        if (response.optInt("responseStatus") != 200 || response.optBoolean("quotaFinished")) {
            throw new IOException("Translation unavailable");
        }
        JSONObject data = response.optJSONObject("responseData");
        String text = data == null ? "" : data.optString("translatedText", "").trim();
        if (text.isEmpty() || !text.matches("(?s).*[\\u3400-\\u9fff].*")) {
            throw new IOException("No Chinese translation");
        }
        return org.jsoup.parser.Parser.unescapeEntities(text, false);
    }

    @Override public void close() {
        closed = true;
        pending.clear();
        executor.shutdownNow();
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
        main.removeCallbacksAndMessages(null);
    }
}
