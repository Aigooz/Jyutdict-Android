package cc.ecisr.jyutdict.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure update-manifest parsing and SHA-256 verification helpers. */
public final class UpdateIntegrity {
    private static final Pattern CHECKSUM_LINE = Pattern.compile(
            "^\\s*([0-9a-fA-F]{64})\\s+\\*?(.+?)\\s*$");

    private UpdateIntegrity() {}

    /** Extract the expected digest for one exact asset name from SHA256SUMS. */
    public static String digestForAsset(String manifest, String assetName) {
        if (manifest == null || assetName == null || assetName.isEmpty()) {
            return null;
        }
        for (String line : manifest.split("\\R")) {
            Matcher matcher = CHECKSUM_LINE.matcher(line);
            if (!matcher.matches()) continue;
            String listedName = matcher.group(2);
            if (listedName.startsWith("./")) {
                listedName = listedName.substring(2);
            }
            if (assetName.equals(listedName)) {
                return matcher.group(1).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
        }
        return toHex(digest.digest());
    }

    static String readLimitedUtf8(InputStream input, int limit) throws IOException {
        try (InputStream stream = input) {
            byte[] buffer = new byte[4096];
            byte[] result = new byte[Math.max(limit, 1)];
            int total = 0;
            int length;
            while ((length = stream.read(buffer)) != -1) {
                if (total + length > limit) {
                    throw new IOException("Checksum manifest is too large");
                }
                System.arraycopy(buffer, 0, result, total, length);
                total += length;
            }
            return new String(result, 0, total, StandardCharsets.UTF_8);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }
}
