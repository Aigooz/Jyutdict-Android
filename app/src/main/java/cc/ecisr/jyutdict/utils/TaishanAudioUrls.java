package cc.ecisr.jyutdict.utils;

import java.net.URI;
import java.util.Locale;

/** Public reference audio is restricted to the current dictionary host. */
public final class TaishanAudioUrls {
    public static final String HOME = "https://www.taishandict.com/";

    private TaishanAudioUrls() {}

    public static String resolve(String href) {
        if (href == null || href.isEmpty()) return null;
        try {
            URI input = URI.create(href);
            String inputPath = input.getPath();
            if (inputPath != null && (inputPath.contains("..") || inputPath.contains("\\")
                    || inputPath.contains("%") || inputPath.contains("\u0000"))) return null;
            URI uri = URI.create(HOME).resolve(input);
            String path = uri.getPath();
            if (!"https".equals(uri.getScheme()) || !"www.taishandict.com".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || path == null || path.contains("..")
                    || path.contains("\\") || !path.toLowerCase(Locale.ROOT).endsWith(".mp3")
                    || !path.toLowerCase(Locale.ROOT).contains("/audio/")) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
