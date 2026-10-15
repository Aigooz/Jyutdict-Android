package cc.ecisr.jyutdict;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** One normalized entry from a user-provided Taishan dictionary. */
public final class TaishanEntry {
    public final String id;
    public final String headword;
    public final String ipa;
    public final String rawIpa;
    public final String definition;
    public final String category;
    public final String subcategory;
    public final int bookPage;
    private final String filterText;

    private TaishanEntry(@NonNull JSONObject source) throws JSONException {
        id = source.getString("id");
        headword = source.optString("headword", "");
        ipa = source.optString("ipa_display", "");
        rawIpa = source.optString("ipa", ipa);
        definition = source.optString("definition", "");
        category = source.optString("category", "");
        subcategory = source.optString("subcategory", "");
        bookPage = source.optInt("book_page", 0);
        filterText = source.optString("search", "").toLowerCase(Locale.ROOT);
        if (id.trim().isEmpty() || filterText.trim().isEmpty()) {
            throw new JSONException("Invalid Taishan entry: " + id);
        }
    }

    public static List<TaishanEntry> fromJson(@NonNull String json) throws JSONException {
        JSONArray values = new JSONObject(json).optJSONArray("entries");
        if (values == null) throw new JSONException("Missing Taishan entries");

        List<TaishanEntry> entries = new ArrayList<>(values.length());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            TaishanEntry entry = new TaishanEntry(values.getJSONObject(i));
            if (!ids.add(entry.id)) throw new JSONException("Duplicate Taishan entry ID");
            entries.add(entry);
        }
        return entries;
    }

    public boolean matches(@NonNull String query, String categoryFilter) {
        if (categoryFilter != null && !categoryFilter.equals(category)) return false;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;

        for (String term : normalized.split("\\s+")) {
            if (!term.isEmpty() && !filterText.contains(term)) return false;
        }
        return true;
    }
}
