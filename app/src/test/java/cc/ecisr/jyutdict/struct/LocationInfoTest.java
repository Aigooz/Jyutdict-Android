package cc.ecisr.jyutdict.struct;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class LocationInfoTest {
    @Test
    public void sortsLocationsByCityThenLocalityBeforeDialectRegion() {
        LocationInfo.Location cityA = location(1, "新会", "会城", "四邑片");
        LocationInfo.Location cityB = location(2, "台山", "斗山墟", "四邑片");
        LocationInfo.Location sameCityOtherRegion = location(3, "台山", "台城", "广府片");

        assertTrue(LocationInfo.compareGeographically(cityB, cityA) < 0);
        assertTrue(LocationInfo.compareGeographically(
                sameCityOtherRegion, cityB) < 0);
    }

    @Test
    public void keepsJiangmenTogetherAcrossCountiesAndDialectRegions() {
        ArrayList<LocationInfo.Location> locations = new ArrayList<>(Arrays.asList(
                location(1, "中山", "石岐", "粵海片"),
                location(2, "化州", "上江", "吳化片"),
                location(3, "台山", "台城", "四邑片"),
                location(4, "江門", "白沙", "四邑片"),
                location(5, "蓬江", "棠下", "四邑片"),
                location(6, "江門", "(白)", "粵海片"),
                location(7, "新會", "會城", "四邑片"),
                location(8, "開平", "蒼城", "四邑片"),
                location(9, "鶴山", "沙坪", "四邑片")));
        Collections.sort(locations, LocationInfo::compareGeographically);
        for (int i = 0; i < 7; i++) {
            assertEquals("江门", LocationOrder.group(locations.get(i)).name);
        }
        assertEquals("中山", locations.get(7).second);
        assertEquals("化州", locations.get(8).second);
        ArrayList<Integer> expectedIds = new ArrayList<>();
        for (LocationInfo.Location location : locations) expectedIds.add(location.id);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(locations, new java.util.Random(i));
            Collections.sort(locations, LocationInfo::compareGeographically);
            ArrayList<Integer> actualIds = new ArrayList<>();
            for (LocationInfo.Location location : locations) actualIds.add(location.id);
            assertEquals(expectedIds, actualIds);
        }
    }

    @Test
    public void usesFixedCountyOrderAndKeepsSuffixVariantsTogether() {
        ArrayList<LocationInfo.Location> locations = new ArrayList<>(Arrays.asList(
                location(1, "台山", "大江", "四邑片"),
                location(2, "台山市", "台城", "四邑片"),
                location(3, "蓬江區", "棠下", "四邑片"),
                location(4, "新會", "會城", "四邑片"),
                location(5, "江門", "白沙", "四邑片"),
                location(6, "開平", "蒼城", "四邑片"),
                location(7, "鶴山", "沙坪", "四邑片"),
                location(8, "恩平", "恩城", "四邑片"),
                location(9, "江海", "外海", "粵海片")));
        Collections.sort(locations, LocationInfo::compareGeographically);
        int[] expected = {5, 3, 9, 2, 1, 4, 7, 6, 8};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], locations.get(i).id);
        }
        assertEquals("容县", LocationOrder.administrativeLabel("容縣"));
        assertEquals("未来县", LocationOrder.administrativeLabel("未来县"));
    }

    @Test
    public void groupsCurrentApiCountiesUnderTheirActualPrefectures() {
        assertEquals("北海", LocationOrder.group(location(1, "廉州", "", "欽廉片")).name);
        assertEquals("肇庆", LocationOrder.group(location(2, "封川", "", "勾漏片")).name);
        assertEquals("肇庆", LocationOrder.group(location(3, "開建", "", "勾漏片")).name);
        assertEquals("梧州", LocationOrder.group(location(4, "蒙山", "", "勾漏片")).name);
        assertEquals("玉林", LocationOrder.group(location(5, "北流", "", "勾漏片")).name);
        assertEquals("江门", LocationOrder.group(location(6, "蓬江区", "", "四邑片")).name);
        assertEquals("茂名", LocationOrder.group(location(7, "化州市", "", "吳化片")).name);
    }

    @Test
    public void handlesHistoricalUnknownAndMissingLocationsDeterministically() {
        LocationInfo.Location historical = location(1, "1890新會", "會城", "歷史音");
        LocationInfo.Location modern = location(2, "江门", "白沙", "四邑片");
        LocationInfo.Location unknown = location(3, "未来地点", "新采音点", "四邑片");
        assertTrue(LocationInfo.compareGeographically(historical, modern) < 0);
        assertTrue(LocationInfo.compareGeographically(modern, unknown) < 0);
        assertTrue(LocationInfo.compareGeographically(unknown, null) < 0);
        assertEquals(0, LocationInfo.compareGeographically(null, null));
        assertEquals(0, LocationInfo.compareGeographically(modern, modern));
        assertTrue(LocationInfo.compareGeographically(
                location(10, "台山", "台城", "四邑片"),
                location(11, "台山", "台城", "四邑片")) < 0);
        assertTrue(LocationInfo.compareGeographically(
                location(12, "台山", "台城", "四邑片"),
                location(13, "台山", "大江", "四邑片")) < 0);
    }

    @Test
    public void sortsHeaderAndParsedCharacterAndRebuildsLookupIndexes() throws Exception {
        JSONArray header = new JSONArray()
                .put(new JSONObject().put("id", 1).put("second", "中山").put("third", "石岐"))
                .put(new JSONObject().put("id", 2).put("second", "台山").put("third", "台城"))
                .put(new JSONObject().put("id", 3).put("second", "蓬江").put("third", "棠下"))
                .put(new JSONObject().put("id", 4).put("second", "化州").put("third", "上江"));
        try {
            LocationInfo.load(header);
            assertEquals(3, LocationInfo.getAll().get(0).id);
            assertEquals(2, LocationInfo.getAll().get(1).id);
            JSONArray areas = new JSONArray();
            for (int id : new int[]{1, 4, 3, 2}) {
                areas.put(new JSONObject().put("id", id)
                        .put("粵拼", new JSONArray().put("maa1")));
            }
            GeneralCharacter character = new GeneralCharacter(
                    new JSONObject().put("字", "嗎").put("各地", areas));
            assertEquals(3, character.areas.get(0).id);
            assertEquals(2, character.areas.get(1).id);
            assertEquals(1, character.areas.get(2).id);
            assertEquals(4, character.areas.get(3).id);
            for (GeneralCharacter.SingleLoc area : character.areas) {
                assertEquals(area.id, character.area(area.city).id);
            }
        } finally {
            LocationInfo.load(new JSONArray());
        }
    }

    private static LocationInfo.Location location(
            int id, String city, String locality, String dialectRegion) {
        LocationInfo.Location location = new LocationInfo.Location();
        location.id = id;
        location.second = city;
        location.third = locality;
        location.first = dialectRegion;
        return location;
    }
}
