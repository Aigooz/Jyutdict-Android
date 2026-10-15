package cc.ecisr.jyutdict.struct;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
