package cc.ecisr.jyutdict;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class TaishanDictionaryImportTest {
    @Test
    public void validImportPersistsAndInvalidReplacementPreservesData() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File source = new File(context.getCacheDir(), "user-dictionary.json");
        write(source, "{\"entries\":[{\"id\":\"user-1\",\"headword\":\"sample\",\"search\":\"sample\"}]}");
        TaishanDictionary.importDictionary(context, Uri.fromFile(source));
        assertTrue(TaishanDictionary.isImported(context));
        assertEquals("sample", TaishanDictionary.load(context).get(0).headword);
        write(source, "{\"entries\":[]}");
        try {
            TaishanDictionary.importDictionary(context, Uri.fromFile(source));
            fail("Empty dictionary must be rejected");
        } catch (JSONException expected) {
            assertEquals("sample", TaishanDictionary.load(context).get(0).headword);
        }
        File stored = new File(context.getFilesDir(), "taishan-dictionary.v1.json");
        assertTrue(stored.length() > 20);
    }

    @Test
    public void duplicateIdsAreRejected() throws Exception {
        try {
            TaishanEntry.fromJson("{\"entries\":[{\"id\":\"same\",\"search\":\"a\"},{\"id\":\"same\",\"search\":\"b\"}]}");
            fail("Duplicate IDs must be rejected");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("Duplicate"));
        }
    }

    private static void write(File file, String json) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
