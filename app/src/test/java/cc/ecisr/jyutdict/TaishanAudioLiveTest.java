package cc.ecisr.jyutdict;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/** Opt-in smoke checks against the public source; never contacts the site during normal builds. */
public class TaishanAudioLiveTest {
    @Before
    public void enableOnlyOnRequest() {
        Assume.assumeTrue("Set TAISHAN_LIVE_TESTS=1 for network checks",
                "1".equals(System.getenv("TAISHAN_LIVE_TESTS")));
    }

    @Test
    public void liveOnlineDictionarySearchParsesFullEntriesAndFieldSearch() throws Exception {
        TaishanAudioSource.DictionaryResult result = dictionarySearch(
                "\u53f0\u5c71", TaishanAudioSource.SearchField.TAISHANESE, true);
        assertTrue(result.matchCount >= 1);
        assertFalse(result.entries.isEmpty());
        assertTrue(result.entries.stream().anyMatch(entry -> entry.headword.contains("\u53f0\u5c71")));
        TaishanAudioSource.DictionaryResult english = dictionarySearch(
                "water", TaishanAudioSource.SearchField.ENGLISH, false);
        assertTrue(english.matchCount > 0);
        assertFalse(english.entries.isEmpty());
    }

    @Test
    public void livePronounsKeepDifferentTonesAndPlayableAudio() throws Exception {
        List<TaishanAudioSource.Recording> recordings = lookup("\u6211");
        assertTrue(recordings.size() >= 2);
        TaishanAudioSource.Recording singular = TaishanAudioSource.uniqueMatch(recordings, "\u014b\u0254i33");
        TaishanAudioSource.Recording plural = TaishanAudioSource.uniqueMatch(recordings, "\u014b\u0254i21");
        assertNotNull(singular);
        assertNotNull(plural);
        assertNotEquals(singular.url, plural.url);
        checkMp3(singular.url);
        checkMp3(plural.url);
    }

    @Test
    public void deadSkyAndMoonLinksAreReportedAsUnavailable() throws Exception {
        List<TaishanAudioSource.Recording> sky = lookup("\u5929");
        assertFalse(sky.isEmpty());
        assertNull(TaishanAudioSource.uniqueMatch(sky, "hen33"));
        List<TaishanAudioSource.Recording> moon = lookup("\u6708\u5149");
        assertFalse(moon.isEmpty());
        assertNull(TaishanAudioSource.uniqueMatch(moon, "\u014but31k\u0254\u014b"));
        assertEquals(404, status(sky.get(0).url));
        assertEquals(404, status(moon.get(0).url));
    }

    @Test
    public void liveGreetingHasWholeWordRecording() throws Exception {
        List<TaishanAudioSource.Recording> result = lookup("\u4f60\u597d");
        assertFalse(result.isEmpty());
        assertEquals("\u4f60\u597d", result.get(0).word);
        checkMp3(result.get(0).url);
    }

    @Test
    public void liveNoMatchIsAValidEmptyResult() throws Exception {
        assertTrue(lookup("\u98df").isEmpty());
    }

    private static List<TaishanAudioSource.Recording> lookup(String word) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(TaishanAudioSource.SEARCH).openConnection();
        try {
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] form = ("data=" + URLEncoder.encode(word, "UTF-8")
                    + "&Select1=Taishanese&submit=Search&Select2=Full").getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(form); }
            assertEquals(200, connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return TaishanAudioSource.parse(new String(input.readNBytes(256 * 1024), StandardCharsets.UTF_8), word);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static TaishanAudioSource.DictionaryResult dictionarySearch(
            String query, TaishanAudioSource.SearchField field, boolean partial) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(TaishanAudioSource.SEARCH).openConnection();
        try {
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] form = ("data=" + URLEncoder.encode(query, "UTF-8")
                    + "&Select1=" + field.formValue + "&submit=Search&Select2="
                    + (partial ? "Partial" : "Full")).getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(form); }
            assertEquals(200, connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return TaishanAudioSource.parseDictionary(
                        new String(input.readNBytes(512 * 1024), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void checkMp3(String url) throws Exception {
        checkMp3(url, false);
    }

    private static void checkMp3(String url, boolean allowMissing) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (allowMissing && (status == 404 || status == 410)) {
                System.out.println("Source recording unavailable (" + status + "): " + url);
                return;
            }
            assertEquals(200, status);
            assertTrue(connection.getContentType().toLowerCase(Locale.ROOT).startsWith("audio/"));
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(16);
                assertTrue(bytes.length >= 3);
                assertTrue("Must have ID3 header or MPEG audio frame",
                        (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3')
                                || ((bytes[0] & 255) == 255 && (bytes[1] & 224) == 224));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static int status(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }
}
