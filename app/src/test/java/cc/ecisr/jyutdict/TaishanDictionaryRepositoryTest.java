package cc.ecisr.jyutdict;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;

public class TaishanDictionaryRepositoryTest {
    @Test
    public void cancellationFlagIsPreservedWhenConnectionCloses() {
        TaishanDictionaryRepository repository = new TaishanDictionaryRepository();
        repository.cancel();

        TaishanDictionaryRepository.SearchException translated = invokeTranslate(
                repository, new IOException("Socket closed"));

        assertTrue(translated.cancelled);
    }

    @Test
    public void ordinaryNetworkFailureIsNotReportedAsCancellation() throws Exception {
        TaishanDictionaryRepository repository = new TaishanDictionaryRepository();

        TaishanDictionaryRepository.SearchException translated = invokeTranslate(
                repository, new IOException("Connection refused"));

        assertFalse(translated.cancelled);
    }

    private static TaishanDictionaryRepository.SearchException invokeTranslate(
            TaishanDictionaryRepository repository, IOException exception) {
        try {
            Method method = TaishanDictionaryRepository.class.getDeclaredMethod(
                    "translateCancellation", IOException.class, String.class);
            method.setAccessible(true);
            return (TaishanDictionaryRepository.SearchException) method.invoke(
                    repository, exception, "Network error");
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new AssertionError(reflectionFailure);
        }
    }
}
