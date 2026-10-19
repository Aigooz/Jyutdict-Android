package cc.ecisr.jyutdict.search;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent local search history and bookmarked dictionary snapshots. */
public final class DictionaryRecordStore {
    private static final String PREFS = "dictionary_records";
    private static final String KEY_HISTORY = "history_v1";
    private static final String KEY_FAVORITES = "favorites_v1";
    public static final int MAX_HISTORY = 200;
    public static final int MAX_FAVORITES = 500;

    private final SharedPreferences preferences;
    private String cachedHistoryRaw;
    private List<HistoryEntry> cachedHistory = Collections.emptyList();
    private String cachedFavoritesRaw;
    private List<FavoriteEntry> cachedFavorites = Collections.emptyList();
    private Set<String> cachedFavoriteKeys = Collections.emptySet();

    public DictionaryRecordStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<HistoryEntry> history() {
        String raw = storedArray(KEY_HISTORY);
        if (!raw.equals(cachedHistoryRaw)) {
            cachedHistoryRaw = raw;
            JSONArray array = readArray(raw);
            ArrayList<HistoryEntry> result = new ArrayList<>(array.length());
            for (int index = 0; index < array.length(); index++) {
                JSONObject json = array.optJSONObject(index);
                HistoryEntry entry = HistoryEntry.fromJson(json);
                if (entry != null) result.add(entry);
            }
            cachedHistory = Collections.unmodifiableList(result);
        }
        return cachedHistory;
    }

    public void recordSearch(String query, int mode, int resultCount, long timestamp) {
        String displayQuery = query == null ? "" : query.trim();
        String normalized = SearchQueryNormalizer.normalize(displayQuery);
        if (normalized.isEmpty()) return;
        ArrayList<HistoryEntry> history = new ArrayList<>(history());
        for (int index = history.size() - 1; index >= 0; index--) {
            HistoryEntry entry = history.get(index);
            if (SearchQueryNormalizer.matchKey(entry.query)
                    .equals(SearchQueryNormalizer.matchKey(displayQuery)) && entry.mode == mode) {
                history.remove(index);
            }
        }
        history.add(0, new HistoryEntry(displayQuery, mode, resultCount, timestamp));
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        writeHistory(history);
    }

    public synchronized void clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply();
        cachedHistoryRaw = null;
        cachedHistory = Collections.emptyList();
    }

    public void removeHistory(HistoryEntry target) {
        ArrayList<HistoryEntry> history = new ArrayList<>(history());
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).id().equals(target.id())) history.remove(index);
        }
        writeHistory(history);
    }

    public synchronized List<FavoriteEntry> favorites() {
        String raw = storedArray(KEY_FAVORITES);
        if (!raw.equals(cachedFavoritesRaw)) {
            cachedFavoritesRaw = raw;
            JSONArray array = readArray(raw);
            ArrayList<FavoriteEntry> result = new ArrayList<>(array.length());
            HashSet<String> keys = new HashSet<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject json = array.optJSONObject(index);
                FavoriteEntry entry = FavoriteEntry.fromJson(json);
                if (entry != null) {
                    result.add(entry);
                    keys.add(entry.key);
                }
            }
            Collections.sort(result, new Comparator<FavoriteEntry>() {
                @Override
                public int compare(FavoriteEntry left, FavoriteEntry right) {
                    return Long.compare(right.savedAt, left.savedAt);
                }
            });
            cachedFavorites = Collections.unmodifiableList(result);
            cachedFavoriteKeys = Collections.unmodifiableSet(keys);
        }
        return cachedFavorites;
    }

    public synchronized boolean isFavorite(String key) {
        if (key == null || key.isEmpty()) return false;
        favorites();
        return cachedFavoriteKeys.contains(key);
    }

    public boolean toggleFavorite(FavoriteEntry entry) {
        if (entry == null || entry.key == null || entry.key.isEmpty()) return false;
        ArrayList<FavoriteEntry> favorites = new ArrayList<>(favorites());
        for (int index = 0; index < favorites.size(); index++) {
            if (favorites.get(index).key.equals(entry.key)) {
                favorites.remove(index);
                writeFavorites(favorites);
                return false;
            }
        }
        favorites.add(0, entry);
        while (favorites.size() > MAX_FAVORITES) favorites.remove(favorites.size() - 1);
        writeFavorites(favorites);
        return true;
    }

    public void removeFavorite(FavoriteEntry target) {
        ArrayList<FavoriteEntry> favorites = new ArrayList<>(favorites());
        for (int index = favorites.size() - 1; index >= 0; index--) {
            if (favorites.get(index).key.equals(target.key)) favorites.remove(index);
        }
        writeFavorites(favorites);
    }

    private String storedArray(String key) {
        String raw = preferences.getString(key, "[]");
        return raw == null ? "[]" : raw;
    }

    private static JSONArray readArray(String raw) {
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void writeHistory(List<HistoryEntry> entries) {
        String stored = toHistoryJson(entries).toString();
        synchronized (this) {
            cachedHistoryRaw = stored;
            cachedHistory = Collections.unmodifiableList(new ArrayList<>(entries));
            preferences.edit().putString(KEY_HISTORY, stored).apply();
        }
    }

    private void writeFavorites(List<FavoriteEntry> entries) {
        String stored = toFavoriteJson(entries).toString();
        synchronized (this) {
            HashSet<String> keys = new HashSet<>();
            for (FavoriteEntry entry : entries) keys.add(entry.key);
            cachedFavoritesRaw = stored;
            cachedFavorites = Collections.unmodifiableList(new ArrayList<>(entries));
            cachedFavoriteKeys = Collections.unmodifiableSet(keys);
            preferences.edit().putString(KEY_FAVORITES, stored).apply();
        }
    }

    private static JSONArray toHistoryJson(List<HistoryEntry> entries) {
        JSONArray array = new JSONArray();
        for (HistoryEntry entry : entries) array.put(entry.toJson());
        return array;
    }

    private static JSONArray toFavoriteJson(List<FavoriteEntry> entries) {
        JSONArray array = new JSONArray();
        for (FavoriteEntry entry : entries) array.put(entry.toJson());
        return array;
    }

    public static final class HistoryEntry {
        public final String query;
        public final int mode;
        public final int resultCount;
        public final long searchedAt;

        public HistoryEntry(String query, int mode, int resultCount, long searchedAt) {
            this.query = query;
            this.mode = mode;
            this.resultCount = resultCount;
            this.searchedAt = searchedAt;
        }

        public String id() {
            return mode + "\u0000" + query;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("query", query);
                json.put("mode", mode);
                json.put("result_count", resultCount);
                json.put("searched_at", searchedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static HistoryEntry fromJson(JSONObject json) {
            if (json == null) return null;
            String query = json.optString("query", "").trim();
            if (query.isEmpty()) return null;
            return new HistoryEntry(query, json.optInt("mode", 0),
                    json.optInt("result_count", -1), json.optLong("searched_at", 0L));
        }
    }

    public static final class FavoriteEntry {
        public final String id;
        public final String key;
        public final String headword;
        public final String readings;
        public final String definitions;
        public final String source;
        public final int mode;
        public final long savedAt;

        public FavoriteEntry(String id, String key, String headword, String readings,
                             String definitions, String source, int mode, long savedAt) {
            this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
            this.key = key == null ? "" : key;
            this.headword = headword == null ? "" : headword;
            this.readings = readings == null ? "" : readings;
            this.definitions = definitions == null ? "" : definitions;
            this.source = source == null ? "" : source;
            this.mode = mode;
            this.savedAt = savedAt;
        }

        public static FavoriteEntry create(String headword, String readings,
                                           String definitions, String source, int mode,
                                           long savedAt) {
            String key = mode + "\u0000" + headword + "\u0000" + readings;
            return new FavoriteEntry(UUID.randomUUID().toString(), key, headword, readings,
                    definitions, source, mode, savedAt);
        }

        public static FavoriteEntry createWithKey(String key, String headword, String readings,
                                                  String definitions, String source, int mode,
                                                  long savedAt) {
            return new FavoriteEntry(UUID.randomUUID().toString(), key, headword, readings,
                    definitions, source, mode, savedAt);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("key", key);
                json.put("headword", headword);
                json.put("readings", readings);
                json.put("definitions", definitions);
                json.put("source", source);
                json.put("mode", mode);
                json.put("saved_at", savedAt);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static FavoriteEntry fromJson(JSONObject json) {
            if (json == null) return null;
            String key = json.optString("key", "").trim();
            String headword = json.optString("headword", "").trim();
            if (key.isEmpty() && headword.isEmpty()) return null;
            return new FavoriteEntry(json.optString("id", ""), key, headword,
                    json.optString("readings", ""), json.optString("definitions", ""),
                    json.optString("source", ""), json.optInt("mode", 0),
                    json.optLong("saved_at", 0L));
        }
    }
}
