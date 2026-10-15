package cc.ecisr.jyutdict;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** User-supplied recordings stay separate from both the book and Cantonese reference audio. */
final class TaishanRecordingStore {
    private static final Object FILE_LOCK = new Object();
    private final Context context;
    private final File directory;
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final long MAX_DURATION_MS = 120_000;

    TaishanRecordingStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "taishan-recordings");
    }

    static String key(TaishanEntry entry) {
        return "taishan:" + entry.id + ":" + entry.headword + ":" + entry.rawIpa;
    }

    File file(TaishanEntry entry) {
        File file = fileForKey(key(entry));
        synchronized (FILE_LOCK) {
            // Android 6-10 leave a backup if an atomic replacement is interrupted.
            if (new File(file.getPath() + ".bak").isFile()) {
                try (FileInputStream ignored = new AtomicFile(file).openRead()) {
                    // Opening the file restores the previous complete recording.
                } catch (IOException ignored) {}
            }
        }
        return file;
    }

    private File fileForKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : digest) {
                name.append(Character.forDigit((value >> 4) & 15, 16));
                name.append(Character.forDigit(value & 15, 16));
            }
            return new File(directory, name + ".audio");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    boolean contains(TaishanEntry entry) {
        File file = file(entry);
        return file.isFile() && file.length() > 0;
    }

    void delete(TaishanEntry entry) {
        synchronized (FILE_LOCK) {
            new AtomicFile(fileForKey(key(entry))).delete();
        }
    }

    /** Copy and validate first, then replace atomically so a bad import cannot erase a good take. */
    void importRecording(String key, Uri uri) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create audio directory");
        File pending = File.createTempFile("import-", ".audio", directory);
        try {
            long size = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(pending)) {
                if (input == null) throw new IOException("Missing audio");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                    size += count;
                    if (size > MAX_BYTES) throw new IOException("Audio too large");
                    output.write(buffer, 0, count);
                }
            }
            if (size == 0) throw new IOException("Empty audio");
            validate(pending);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            synchronized (FILE_LOCK) {
                AtomicFile destination = new AtomicFile(fileForKey(key));
                FileOutputStream output = null;
                try (FileInputStream input = new FileInputStream(pending)) {
                    output = destination.startWrite();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                        output.write(buffer, 0, count);
                    }
                    destination.finishWrite(output);
                    output = null;
                } catch (IOException | RuntimeException exception) {
                    if (output != null) destination.failWrite(output);
                    throw exception;
                }
            }
        } finally {
            pending.delete();
        }
    }

    private static void validate(File file) throws IOException {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(file.getAbsolutePath());
            long duration = Long.parseLong(metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (duration <= 0 || duration > MAX_DURATION_MS
                    || !"yes".equals(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))) {
                throw new IOException("Invalid audio duration or stream");
            }
        } catch (RuntimeException exception) {
            throw new IOException("Unsupported audio", exception);
        } finally {
            metadata.release();
        }
    }
}
