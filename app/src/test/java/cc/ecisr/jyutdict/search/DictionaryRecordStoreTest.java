package cc.ecisr.jyutdict.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DictionaryRecordStoreTest {
    private Context context;
    private DictionaryRecordStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("dictionary_records", Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new DictionaryRecordStore(context);
    }

    @Test
    public void historyDeduplicatesAndKeepsNewestFirst() {
        store.recordSearch(" 廣州 ", 1, 10, 100L);
        store.recordSearch("香港", 1, 2, 200L);
        store.recordSearch("廣州", 1, 3, 300L);

        assertEquals(2, store.history().size());
        assertEquals("廣州", store.history().get(0).query);
        assertEquals(3, store.history().get(0).resultCount);
        assertEquals(300L, store.history().get(0).searchedAt);
        assertEquals("香港", store.history().get(1).query);
    }

    @Test
    public void historyIsModeScopedAndRemovable() {
        store.recordSearch("aa1", 2, 4, 100L);
        store.recordSearch("aa1", 3, 5, 200L);
        assertEquals(2, store.history().size());

        store.removeHistory(store.history().get(0));
        assertEquals(1, store.history().size());
        assertEquals(2, store.history().get(0).mode);
    }

    @Test
    public void historyDeduplicatesScriptVariantsAndKeepsLatestDisplayText() {
        store.recordSearch("廣州話", 1, 4, 100L);
        store.recordSearch("广州话", 1, 6, 200L);

        assertEquals(1, store.history().size());
        assertEquals("广州话", store.history().get(0).query);
        assertEquals(6, store.history().get(0).resultCount);
    }

    @Test
    public void favoritesToggleAndKeepOfflineSnapshot() {
        DictionaryRecordStore.FavoriteEntry entry =
                DictionaryRecordStore.FavoriteEntry.create(
                        "日头", "ŋit³¹ heu²¹", "太陽", "通用字表", 1, 100L);
        assertTrue(store.toggleFavorite(entry));
        assertTrue(store.isFavorite(entry.key));
        assertEquals(1, store.favorites().size());
        assertEquals("日头", store.favorites().get(0).headword);
        assertEquals("ŋit³¹ heu²¹", store.favorites().get(0).readings);

        assertFalse(store.toggleFavorite(entry));
        assertTrue(store.favorites().isEmpty());
    }

    @Test
    public void malformedStoredJsonDoesNotBreakReads() {
        SharedPreferences preferences =
                context.getSharedPreferences("dictionary_records", Context.MODE_PRIVATE);
        preferences.edit().putString("history_v1", "not-json").commit();
        assertTrue(store.history().isEmpty());
    }
}
