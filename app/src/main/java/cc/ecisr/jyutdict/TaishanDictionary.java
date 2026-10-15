package cc.ecisr.jyutdict;

import android.content.Context;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import android.util.AtomicFile;
import android.net.Uri;
import java.util.Collections;
import java.util.List;

/** Loads user-provided dictionary data; book contents are not redistributed. */
final class TaishanDictionary {
    private static final String FILE_NAME = "taishan-dictionary.v1.json";
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static volatile List<TaishanEntry> cache;

    private TaishanDictionary() {}

    static synchronized List<TaishanEntry> load(Context context)
            throws IOException, JSONException {
        List<TaishanEntry> loaded = cache;
        if (loaded != null) return loaded;

        try (InputStream input = storedFile(context).openRead()) {
            List<TaishanEntry> entries = parse(read(input));
            cache = entries;
            return entries;
        }
    }

    static boolean isImported(Context context) {
        return storedFile(context).getBaseFile().exists();
    }

    static synchronized void importDictionary(Context context, Uri uri)
            throws IOException, JSONException {
        byte[] data;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Cannot open dictionary");
            data = read(input);
        }
        List<TaishanEntry> entries = parse(data);
        AtomicFile file = storedFile(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(data);
            file.finishWrite(output);
        } catch (IOException exception) {
            file.failWrite(output);
            throw exception;
        }
        cache = entries;
    }

    private static AtomicFile storedFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    private static List<TaishanEntry> parse(byte[] data) throws JSONException {
        List<TaishanEntry> entries = TaishanEntry.fromJson(
                new String(data, StandardCharsets.UTF_8));
        if (entries.isEmpty()) throw new JSONException("Taishan dictionary is empty");
        return Collections.unmodifiableList(entries);
    }

    private static byte[] read(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (output.size() + length > MAX_BYTES) {
                    throw new IOException("Dictionary exceeds 16 MiB");
                }
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }
}
