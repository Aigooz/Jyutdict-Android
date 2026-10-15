package cc.ecisr.jyutdict.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Plays complete Jyutping readings, local audio or attributed Taishan reference recordings. */
public final class PronunciationPlayer {
    private static final String[] AUDIO_URLS = {
            "https://fastly.jsdelivr.net/gh/jyutnet/jyutnet-cantonese-syllables@master"
                    + "/sound/kt/%1$s/%2$s/%3$s.opus",
            "https://raw.githubusercontent.com/jyutnet/jyutnet-cantonese-syllables/master"
                    + "/sound/kt/%1$s/%2$s/%3$s.opus"
    };
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicInteger PLAY_TOKEN = new AtomicInteger();
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile JyutpingAudioCatalog audioCatalog;
    private static volatile HttpURLConnection activeConnection;
    private static MediaPlayer mediaPlayer;
    private static AudioManager audioManager;
    private static AudioFocusRequest audioFocusRequest;
    private static AudioManager.OnAudioFocusChangeListener audioFocusListener;
    private static String playbackKey = "";
    private static State state = State.IDLE;

    public enum State { IDLE, LOADING, PLAYING }

    public interface Listener {
        void onPlaybackChanged(String key, State state);

        default void onReferenceFailed(String key, String url, boolean unavailable) {}
    }

    private static final class MissingReferenceException extends IOException {}

    private PronunciationPlayer() {}

    public static String keyForReading(String reading) {
        return "jyutping:" + PronunciationSequence.join(PronunciationSequence.parse(reading));
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
        listener.onPlaybackChanged(playbackKey, state);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static boolean isActive(String key) {
        return state != State.IDLE && playbackKey.equals(key);
    }

    public static boolean isLoading() {
        return state == State.LOADING;
    }

    public static boolean isPlayable(Context context, String reading) {
        return !getAudioCatalog(context).resolveSequence(reading).isEmpty();
    }

    public static void play(Context context, String reading) {
        Context appContext = context.getApplicationContext();
        Context localized = LocaleUtil.wrap(appContext);
        List<String[]> sequence = getAudioCatalog(appContext).resolveSequence(reading);
        if (sequence.isEmpty()) {
            ToastUtil.msg(localized, localized.getString(cc.ecisr.jyutdict.R.string.pron_audio_unavailable));
            return;
        }
        stop();
        int token = PLAY_TOKEN.incrementAndGet();
        setState(keyForReading(reading), State.LOADING);
        EXECUTOR.execute(() -> {
            List<File> files = new ArrayList<>(sequence.size());
            try {
                for (String[] parts : sequence) {
                    if (PLAY_TOKEN.get() != token) return;
                    String syllable = parts[0] + parts[1] + parts[2];
                    File cached = cacheFile(appContext, syllable);
                    if (!cached.isFile() || cached.length() == 0) {
                        boolean downloaded = false;
                        for (String template : AUDIO_URLS) {
                            if (PLAY_TOKEN.get() != token) return;
                            try {
                                download(String.format(Locale.US, template,
                                        parts[1], parts[2], syllable), cached, token);
                                downloaded = true;
                                break;
                            } catch (IOException ignored) {}
                        }
                        if (!downloaded) throw new IOException("Audio download failed");
                    }
                    files.add(cached);
                }
                MAIN_HANDLER.post(() -> start(localized, files, 0, token));
            } catch (IOException | RuntimeException exception) {
                MAIN_HANDLER.post(() -> fail(localized, token));
            }
        });
    }

    public static void playLocal(Context context, String key, File file) {
        stop();
        int token = PLAY_TOKEN.incrementAndGet();
        setState(key, State.LOADING);
        start(LocaleUtil.wrap(context.getApplicationContext()),
                Collections.singletonList(file), 0, token);
    }

    public static void playTaishanReference(Context context, String key, String url) {
        Context appContext = context.getApplicationContext();
        Context localized = LocaleUtil.wrap(appContext);
        stop();
        int token = PLAY_TOKEN.incrementAndGet();
        setState(key, State.LOADING);
        EXECUTOR.execute(() -> {
            try {
                if (!url.equals(TaishanAudioUrls.resolve(url))) throw new IOException("Untrusted reference URL");
                if (PLAY_TOKEN.get() != token) return;
                File cached = taishanReferenceCacheFile(appContext, url);
                trimTaishanCache(cached.getParentFile(), cached);
                if (!cached.isFile() || cached.length() == 0) download(url, cached, token, true);
                cached.setLastModified(System.currentTimeMillis());
                MAIN_HANDLER.post(() -> start(localized, Collections.singletonList(cached), 0, token, key, url));
            } catch (Exception exception) {
                boolean unavailable = exception instanceof MissingReferenceException;
                MAIN_HANDLER.post(() -> failReference(localized, token, key, url, unavailable));
            }
        });
    }

    private static JyutpingAudioCatalog getAudioCatalog(Context context) {
        JyutpingAudioCatalog catalog = audioCatalog;
        if (catalog == null) {
            synchronized (PronunciationPlayer.class) {
                if (audioCatalog == null) audioCatalog = JyutpingAudioCatalog.read(context);
                catalog = audioCatalog;
            }
        }
        return catalog;
    }

    /** UI entry points and MediaPlayer callbacks are confined to the main thread. */
    public static void stop() {
        PLAY_TOKEN.incrementAndGet();
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        releasePlayer();
        abandonAudioFocus();
        setState("", State.IDLE);
    }

    private static AudioAttributes speechAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private static boolean requestAudioFocus(Context context, int token) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return false;
        audioFocusListener = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                MAIN_HANDLER.post(() -> {
                    if (PLAY_TOKEN.get() == token) stop();
                });
            }
        };
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(speechAttributes())
                    .setOnAudioFocusChangeListener(audioFocusListener, MAIN_HANDLER)
                    .build();
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(audioFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private static void abandonAudioFocus() {
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else if (audioFocusListener != null) {
                audioManager.abandonAudioFocus(audioFocusListener);
            }
        }
        audioManager = null;
        audioFocusRequest = null;
        audioFocusListener = null;
    }

    private static void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private static void setState(String key, State next) {
        playbackKey = key;
        state = next;
        for (Listener listener : LISTENERS) listener.onPlaybackChanged(key, next);
    }

    private static void start(Context context, List<File> files, int index, int token) {
        start(context, files, index, token, null, null);
    }

    private static void start(Context context, List<File> files, int index, int token,
                              String referenceKey, String referenceUrl) {
        if (PLAY_TOKEN.get() != token) return;
        releasePlayer();
        if (index >= files.size()) {
            stop();
            return;
        }
        File file = files.get(index);
        try {
            if (index == 0 && !requestAudioFocus(context, token)) {
                failPlayback(context, token, referenceKey, referenceUrl);
                return;
            }
            MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;
            player.setAudioAttributes(speechAttributes());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                if (PLAY_TOKEN.get() != token || mediaPlayer != mp) return;
                try {
                    mp.start();
                    setState(playbackKey, State.PLAYING);
                } catch (IllegalStateException exception) {
                    failPlayback(context, token, referenceKey, referenceUrl);
                }
            });
            player.setOnCompletionListener(mp -> {
                if (PLAY_TOKEN.get() == token && mediaPlayer == mp) {
                    start(context, files, index + 1, token, referenceKey, referenceUrl);
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (PLAY_TOKEN.get() == token && mediaPlayer == mp) {
                    // A damaged network cache must not keep failing on every retry.
                    if (referenceUrl != null) {
                        file.delete();
                    } else if (new File(context.getCacheDir(), "pronunciations").equals(file.getParentFile())) {
                        file.delete();
                    }
                    failPlayback(context, token, referenceKey, referenceUrl);
                }
                return true;
            });
            player.prepareAsync();
        } catch (Exception exception) {
            if (referenceUrl != null) file.delete();
            failPlayback(context, token, referenceKey, referenceUrl);
        }
    }

    private static void failPlayback(Context context, int token, String referenceKey, String referenceUrl) {
        if (referenceUrl == null) fail(context, token);
        else failReference(context, token, referenceKey, referenceUrl, false);
    }

    private static void fail(Context context, int token) {
        if (PLAY_TOKEN.get() != token) return;
        stop();
        ToastUtil.msg(context, context.getString(cc.ecisr.jyutdict.R.string.pron_audio_failed));
    }

    private static void failReference(Context context, int token, String key, String url, boolean unavailable) {
        if (PLAY_TOKEN.get() != token) return;
        stop();
        for (Listener listener : LISTENERS) listener.onReferenceFailed(key, url, unavailable);
        ToastUtil.msg(context, context.getString(unavailable
                ? cc.ecisr.jyutdict.R.string.taishan_audio_source_unavailable
                : cc.ecisr.jyutdict.R.string.taishan_audio_playback_failed));
    }

    private static File cacheFile(Context context, String syllable) throws IOException {
        return cacheFile(context, syllable, ".opus");
    }

    private static File cacheFile(Context context, String syllable, String extension) throws IOException {
        File directory = new File(context.getCacheDir(), "pronunciations");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create audio cache");
        String safeName = syllable.replaceAll("[^a-z0-9]", "");
        return new File(directory, safeName + extension);
    }

    private static File taishanReferenceCacheFile(Context context, String url) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder("taishan");
        for (byte value : digest) name.append(String.format(Locale.ROOT, "%02x", value & 255));
        return cacheFile(context, name.toString(), ".mp3");
    }

    private static void trimTaishanCache(File directory, File current) {
        File[] files = directory.listFiles((parent, name) -> name.startsWith("taishan") && name.endsWith(".mp3"));
        if (files == null) return;
        java.util.Arrays.sort(files, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        long bytes = current.length();
        for (File file : files) {
            if (file.equals(current)) continue;
            bytes += file.length();
            if (bytes > 30L * 1024 * 1024) file.delete();
        }
    }

    private static void download(String spec, File destination, int token) throws IOException {
        download(spec, destination, token, false);
    }

    private static void download(String spec, File destination, int token, boolean reference) throws IOException {
        File partial = new File(destination.getParentFile(), destination.getName() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(spec).openConnection();
        try {
            if (PLAY_TOKEN.get() != token) throw new IOException("Cancelled");
            activeConnection = connection;
            if (reference) connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Yueci-Android");
            int response = connection.getResponseCode();
            if (reference && (response == 404 || response == 410)) throw new MissingReferenceException();
            if (response != 200) throw new IOException("Audio HTTP error");
            if (reference) {
                String type = connection.getContentType();
                if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("audio/")) {
                    throw new IOException("Reference is not audio");
                }
            }
            long total = 0;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (PLAY_TOKEN.get() != token) throw new IOException("Cancelled");
                    total += length;
                    if (total > 2 * 1024 * 1024) throw new IOException("Audio too large");
                    output.write(buffer, 0, length);
                }
            }
            if (total == 0 || PLAY_TOKEN.get() != token) throw new IOException("Empty or cancelled audio");
            if (reference) {
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(partial.getAbsolutePath());
                    long duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    if (duration <= 0 || duration > 120_000
                            || !"yes".equals(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))) {
                        throw new IOException("Invalid reference recording");
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("Unsupported reference recording", exception);
                } finally {
                    metadata.release();
                }
                if (PLAY_TOKEN.get() != token) throw new IOException("Cancelled");
            }
            if (!partial.renameTo(destination)) throw new IOException("Cannot cache audio");
        } finally {
            if (partial.exists()) partial.delete();
            connection.disconnect();
            if (activeConnection == connection) activeConnection = null;
        }
    }
}
