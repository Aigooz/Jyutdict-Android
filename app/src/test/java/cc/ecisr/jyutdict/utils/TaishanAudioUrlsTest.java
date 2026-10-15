package cc.ecisr.jyutdict.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class TaishanAudioUrlsTest {
    @Test
    public void resolvesOnlyCurrentHttpsHostMp3Paths() {
        assertEquals("https://www.taishandict.com/Misc19/Audio/misc19_35.mp3",
                TaishanAudioUrls.resolve("Misc19/Audio/misc19_35.mp3"));
        assertEquals("https://www.taishandict.com/Prowords/audio/1.mp3",
                TaishanAudioUrls.resolve("https://www.taishandict.com/Prowords/audio/1.mp3"));
        assertEquals("https://www.taishandict.com/Grooming/Audio/grooming%20Part%2001.mp3",
                TaishanAudioUrls.resolve("Grooming/Audio/grooming%20Part%2001.mp3"));
    }

    @Test
    public void rejectsUntrustedMalformedOrRedirectStyleUrls() {
        for (String href : new String[]{null, "", "http://www.taishandict.com/Audio/1.mp3",
                "https://www.stephen-li.com/Audio/1.mp3", "https://evil.example/Audio/1.mp3",
                "//evil.example/Audio/1.mp3", "https://www.taishandict.com.evil.example/Audio/1.mp3",
                "https://evil@www.taishandict.com/Audio/1.mp3", "https://www.taishandict.com:443/Audio/1.mp3",
                "Audio/1.mp3?q=1", "Audio/1.mp3#x", "Audio/1.html", "Other/1.mp3",
                "javascript:alert(1)", "../Audio/1.mp3", "Other/../Audio/1.mp3", "Audio/%2e%2e/1.mp3",
                "Audio/%252e%252e/1.mp3", "Audio/%5c1.mp3", "Audio/%001.mp3", "Audio/has space.mp3"}) {
            assertNull(String.valueOf(href), TaishanAudioUrls.resolve(href));
        }
    }
}
