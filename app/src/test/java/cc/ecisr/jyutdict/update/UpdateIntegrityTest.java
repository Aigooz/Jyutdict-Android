package cc.ecisr.jyutdict.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class UpdateIntegrityTest {
    @Test
    public void parsesGnuAndWindowsChecksumManifest() {
        String digest = "0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef";
        String manifest = digest.toUpperCase() + "  Yueci-Android-dual-debug.apk\n"
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa *other.apk\n";
        assertEquals(digest, UpdateIntegrity.digestForAsset(
                manifest, "Yueci-Android-dual-debug.apk"));
    }

    @Test
    public void rejectsMissingOrAmbiguousChecksum() {
        assertNull(UpdateIntegrity.digestForAsset("", "app.apk"));
        assertNull(UpdateIntegrity.digestForAsset("not a checksum  app.apk", "app.apk"));
        assertNull(UpdateIntegrity.digestForAsset(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  other.apk",
                "app.apk"));
    }

    @Test
    public void computesSha256FromFile() throws Exception {
        File file = File.createTempFile("update-integrity-", ".apk");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write("abc".getBytes(StandardCharsets.UTF_8));
            assertEquals(
                    "ba7816bf8f01cfea414140de5dae2223"
                            + "b00361a396177a9cb410ff61f20015ad",
                    UpdateIntegrity.sha256(file));
        } finally {
            file.delete();
        }
    }
}
