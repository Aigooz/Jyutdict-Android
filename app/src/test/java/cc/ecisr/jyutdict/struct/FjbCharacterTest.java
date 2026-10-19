package cc.ecisr.jyutdict.struct;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class FjbCharacterTest {
    private static final String CITY_COLUMN = "X1";

    @Before
    public void loadHeader() throws Exception {
        JSONArray header = new JSONArray();
        JSONObject city = new JSONObject();
        city.put("index", 0);
        city.put("col", CITY_COLUMN);
        city.put("fullname", "台城");
        city.put("kind", 1);
        header.put(city);
        FjbHeaderInfo.load(header);
    }

    @After
    public void resetHeader() {
        FjbHeaderInfo.reset();
    }

    @Test
    public void rendersMeaningsWhenGrammarMarkersRunOut() throws Exception {
        Spanned rendered = character("A<B。{1}", "名;動").printMeanings();
        assertNotNull(rendered);
        assertTrue(rendered.toString().contains("A"));
    }

    @Test
    public void keepsMarkerRenderingForAlignedColumns() throws Exception {
        Spanned rendered = character("A<B", "名;動").printMeanings();
        assertNotNull(rendered);
        assertTrue(rendered.toString().contains("A"));
    }

    @Test
    public void rendersCityReadingThatStartsWithCorrectionMarker() throws Exception {
        JSONObject entry = new JSONObject();
        entry.put(FjbHeaderInfo.COLUMN_NAME_CHARACTER, "試");
        entry.put(CITY_COLUMN, "^p");
        Spanned rendered = new FjbCharacter(entry, new EntrySetting()).printLocations();
        assertTrue(rendered.toString().contains("p"));
    }

    @Test
    public void rendersCityReadingWithEmptyMarkerSegment() throws Exception {
        JSONObject entry = new JSONObject();
        entry.put(FjbHeaderInfo.COLUMN_NAME_CHARACTER, "試");
        entry.put(CITY_COLUMN, "k^^p");
        assertNotNull(new FjbCharacter(entry, new EntrySetting()).printLocations());
    }

    private static FjbCharacter character(String meaning, String markers) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put(FjbHeaderInfo.COLUMN_NAME_CHARACTER, "試");
        entry.put(FjbHeaderInfo.COLUMN_NAME_MEANING, meaning);
        entry.put(FjbHeaderInfo.COLUMN_NAME_GRAMMAR_MARKER, markers);
        return new FjbCharacter(entry, new EntrySetting());
    }
}
