package cc.ecisr.jyutdict.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PronunciationSequenceTest {
    @Test
    public void acceptsSpacedHyphenatedAndConcatenatedReadings() {
        assertEquals(Arrays.asList("nei5", "hou2"), PronunciationSequence.parse(" NEI5 hou2 "));
        assertEquals(Arrays.asList("nei5", "hou2"), PronunciationSequence.parse("nei5hou2"));
        assertEquals(Arrays.asList("nei5", "hou2"), PronunciationSequence.parse("nei5-hou2"));
        assertEquals(Arrays.asList("m4", "ng5"), PronunciationSequence.parse("m4\u00a0ng5"));
    }

    @Test
    public void keepsAlternativeReadingsSeparate() {
        assertEquals(Arrays.asList("nei5 hou2", "lei5 hou2"),
                PronunciationSequence.alternatives("nei5hou2!/lei5 hou2/nei5 hou2"));
        assertTrue(PronunciationSequence.parse("nei5/lei5 hou2").isEmpty());
    }

    @Test
    public void doesNotSkipUnknownCharactersUncertaintyOrMissingTones() {
        for (String invalid : Arrays.asList("nei5 ? hou2", "nei5 hou", "nei5 (hou2)",
                "nei5字hou2", "nei5+hou2", "_", "hen33/hen55", "", "hello")) {
            assertTrue(invalid, PronunciationSequence.parse(invalid).isEmpty());
        }
        assertTrue(PronunciationSequence.parse(null).isEmpty());
    }

    @Test
    public void retainsExtendedJyutpingAndCheckedToneNotation() {
        assertEquals(Arrays.asList("dak7", "dau9*", "ne5'"),
                PronunciationSequence.parse("dak7 dau9* ne5'"));
        assertEquals(Collections.singletonList("hen33"), PronunciationSequence.parse("hen33"));
    }

    @Test
    public void limitsUnexpectedlyLongSequences() {
        assertEquals(48, PronunciationSequence.parse("nei5 ".repeat(48)).size());
        assertTrue(PronunciationSequence.parse("nei5 ".repeat(49)).isEmpty());
    }
}
