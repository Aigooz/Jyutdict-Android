package cc.ecisr.jyutdict;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cc.ecisr.jyutdict.utils.TaishanAudioUrls;

/** Source readings remain separate from the book's transcriptions and senses. */
final class TaishanAudioSource {
    static final String HOME = TaishanAudioUrls.HOME;
    static final String SEARCH = HOME + "MySQLSearch.php";
    private static final Pattern IPA = Pattern.compile("\\[([^\\[\\]]+)\\]");
    private static final Pattern MATCH_COUNT = Pattern.compile("Number of matches:\\s*(\\d+)");
    private static final Pattern SIMPLIFIED_ONSET = Pattern.compile("(?:^|[1-5]{2,3})(?:[bpdgtk]|ts|dz|ia|Ø)");

    static final class Recording {
        final String word;
        final String ipa;
        final String mandarin;
        final String english;
        final String url;

        Recording(String word, String ipa, String mandarin, String english, String url) {
            this.word = word;
            this.ipa = ipa;
            this.mandarin = mandarin;
            this.english = english;
            this.url = url;
        }

        boolean matchesReading(String bookIpa) {
            // Only formatting is ignored. Accent, aspiration and tone differences are not guesses.
            String reading = normalizeIpa(ipa);
            return reading.matches("(?:[a-z\\u00e6\\u00f8\\u014b\\u0153\\u0250-\\u02ff\\u1d00-\\u1d7f\\u0300-\\u036f\\u03b2\\u03b8\\u03c7']+[1-5]{2,3})+")
                    && !SIMPLIFIED_ONSET.matcher(reading).find()
                    && reading.equals(normalizeIpa(bookIpa));
        }
    }

    static final class DictionaryEntry {
        final String headword;
        final String ipa;
        final String cantonese;
        final String mandarin;
        final String english;
        final String definition;
        final String audioUrl;

        DictionaryEntry(String headword, String ipa, String cantonese, String mandarin,
                        String english, String definition, String audioUrl) {
            this.headword = headword;
            this.ipa = ipa;
            this.cantonese = cantonese;
            this.mandarin = mandarin;
            this.english = english;
            this.definition = definition;
            this.audioUrl = audioUrl;
        }
    }

    static final class DictionaryResult {
        final List<DictionaryEntry> entries;
        final int matchCount;
        final boolean truncated;

        DictionaryResult(List<DictionaryEntry> entries, int matchCount, boolean truncated) {
            this.entries = Collections.unmodifiableList(entries);
            this.matchCount = matchCount;
            this.truncated = truncated;
        }
    }

    enum SearchField {
        TAISHANESE("Taishanese"), IPA("IPA"), CANTONESE("Cantonese"),
        MANDARIN("Mandarin"), ENGLISH("English"), DESCRIPTION("Description");

        final String formValue;

        SearchField(String formValue) { this.formValue = formValue; }
    }

    static Recording uniqueMatch(List<Recording> recordings, String bookIpa) {
        Recording match = null;
        for (Recording recording : recordings) {
            if (!recording.matchesReading(bookIpa)) continue;
            if (match != null) return null;
            match = recording;
        }
        return match;
    }

    static List<Recording> excludingUnavailable(List<Recording> recordings, Set<String> unavailableUrls) {
        if (unavailableUrls.isEmpty()) return recordings;
        List<Recording> available = new ArrayList<>(recordings.size());
        for (Recording recording : recordings) {
            if (!unavailableUrls.contains(recording.url)) available.add(recording);
        }
        return Collections.unmodifiableList(available);
    }

    static String queryWord(String word) {
        return ZhConverterUtil.toSimple(word).trim();
    }

    static List<Recording> parse(String html, String query) throws IOException {
        Element body = Jsoup.parse(html).body();
        Matcher matchCount = MATCH_COUNT.matcher(body.text());
        if (!matchCount.find()) {
            throw new IOException("Unexpected dictionary response");
        }
        if (Integer.parseInt(matchCount.group(1)) == 0) return Collections.emptyList();
        List<Recording> result = new ArrayList<>();
        Element row = new Element("div");
        for (Node node : body.childNodes()) {
            if (node instanceof Element && ((Element) node).tagName().equals("hr")) {
                addRow(result, row, query);
                row = new Element("div");
            } else {
                row.appendChild(node.clone());
            }
        }
        addRow(result, row, query);
        return Collections.unmodifiableList(result);
    }

    static DictionaryResult parseDictionary(String html) throws IOException {
        Element body = Jsoup.parse(html).body();
        String bodyText = body.text();
        Matcher count = MATCH_COUNT.matcher(bodyText);
        boolean truncated = bodyText.contains("Number of matches exceeded limit");
        boolean hasCount = count.find();
        if (!hasCount && !truncated) throw new IOException("Unexpected dictionary response");
        int matchCount = 100;
        if (hasCount) {
            try {
                matchCount = Integer.parseInt(count.group(1));
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid dictionary result count", exception);
            }
        }
        if (truncated) matchCount = 100;
        if (matchCount == 0) return new DictionaryResult(Collections.emptyList(), 0, false);

        List<DictionaryEntry> entries = new ArrayList<>();
        Element row = new Element("div");
        boolean started = false;
        for (Node node : body.childNodes()) {
            if (node instanceof Element && ((Element) node).tagName().equals("b")) started = true;
            if (!started) continue;
            if (node instanceof Element && ((Element) node).tagName().equals("hr")) {
                addDictionaryRow(entries, row);
                row = new Element("div");
            } else {
                row.appendChild(node.clone());
            }
        }
        addDictionaryRow(entries, row);
        return new DictionaryResult(entries, matchCount, truncated);
    }

    private static void addDictionaryRow(List<DictionaryEntry> entries, Element row) {
        Element headword = row.selectFirst("b");
        if (headword == null) return;
        Element beforeBreak = new Element("div");
        Element afterBreak = new Element("div");
        boolean after = false;
        for (Node node : row.childNodes()) {
            if (node instanceof Element && ((Element) node).tagName().equals("br")) {
                after = true;
            } else if (after) {
                afterBreak.appendChild(node.clone());
            } else {
                beforeBreak.appendChild(node.clone());
            }
        }
        String fieldText = beforeBreak.text();
        int cantoneseOffset = fieldText.indexOf("cantonese:");
        int mandarinOffset = fieldText.indexOf("mandarin:");
        int englishOffset = fieldText.indexOf("english:");
        int firstField = minPositive(cantoneseOffset, mandarinOffset, englishOffset);
        Matcher ipa = IPA.matcher(firstField < 0 ? fieldText : fieldText.substring(0, firstField));
        if (!ipa.find()) return;
        StringBuilder readings = new StringBuilder(ipa.group(1).trim());
        while (ipa.find()) readings.append(" / ").append(ipa.group(1).trim());

        String cantonese = field(fieldText, "cantonese:", "mandarin:");
        String mandarin = field(fieldText, "mandarin:", "english:");
        String english = field(fieldText, "english:", null);
        String definition = afterBreak.text().trim();
        if (definition.startsWith("Example:")) definition = definition.substring("Example:".length()).trim();

        Element link = row.selectFirst("a[href]");
        String audio = link == null ? null : TaishanAudioUrls.resolve(link.attr("href"));
        entries.add(new DictionaryEntry(headword.text().trim(), readings.toString(), cantonese,
                mandarin, english, definition, audio));
    }

    private static int minPositive(int... values) {
        int result = Integer.MAX_VALUE;
        for (int value : values) if (value >= 0 && value < result) result = value;
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private static void addRow(List<Recording> result, Element row, String query) {
        Element word = row.selectFirst("b");
        Element link = row.selectFirst("a[href]");
        if (word == null || link == null || !queryWord(word.text()).equals(queryWord(query))) return;
        String text = row.text();
        int fieldStart = text.indexOf("cantonese:");
        if (fieldStart < 0) fieldStart = text.indexOf("mandarin:");
        if (fieldStart < 0) fieldStart = text.indexOf("english:");
        Matcher ipa = IPA.matcher(fieldStart < 0 ? text : text.substring(0, fieldStart));
        if (!ipa.find()) return;
        StringBuilder readings = new StringBuilder(ipa.group(1).trim());
        while (ipa.find()) readings.append(" / ").append(ipa.group(1).trim());
        String url = TaishanAudioUrls.resolve(link.attr("href"));
        if (url == null) return;
        String mandarin = field(text, "mandarin:", "english:");
        String english = field(text, "english:", null);
        Recording recording = new Recording(word.text(), readings.toString(), mandarin, english, url);
        for (Recording prior : result) {
            if (prior.url.equals(url) && prior.ipa.equals(recording.ipa)
                    && prior.mandarin.equals(mandarin) && prior.english.equals(english)) return;
        }
        if (result.size() < 30) result.add(recording);
    }

    private static String field(String text, String start, String end) {
        int offset = text.indexOf(start);
        if (offset < 0) return "";
        offset += start.length();
        int limit = end == null ? text.length() : text.indexOf(end, offset);
        return text.substring(offset, limit < 0 ? text.length() : limit).trim();
    }

    static String normalizeIpa(String ipa) {
        if (ipa == null) return "";
        return ipa.replace('\u00b9', '1').replace('\u00b2', '2').replace('\u00b3', '3')
                .replace('\u2074', '4').replace('\u2075', '5')
                .replaceAll("[\\s\\u00a0\\[\\]]", "");
    }
}
