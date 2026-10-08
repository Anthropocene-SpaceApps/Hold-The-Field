package org.anthropocene.htf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.anthropocene.htf.launcher.DataUpdater;
import org.anthropocene.htf.sim.Season;
import org.anthropocene.htf.sim.SeasonLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataUpdaterTest {
    private static final String RESPONSE = """
            {"properties":{"parameter":{
              "PRECTOTCORR":{"20170101":0.0,"20170102":12.34,"20170103":-999.0},
              "T2M_MAX":{"20170101":23.2,"20170102":24.0,"20170103":24.5},
              "GWETROOT":{"20170101":0.55,"20170102":0.6,"20170103":0.61}}}}""";

    private static final JsonObject REGION = JsonParser.parseString(
            "{\"farm\":{\"lat\":25.07,\"lon\":91.40},\"upstream\":{\"lat\":25.27,\"lon\":91.73}}").getAsJsonObject();

    @Test void parsesPowerResponse() throws IOException {
        Map<String, Map<String, Double>> p = DataUpdater.parseResponse(RESPONSE);
        assertEquals(12.34, p.get("PRECTOTCORR").get("20170102"));
        assertEquals(3, p.get("T2M_MAX").size());
    }

    @Test void rejectsUnexpectedResponse() {
        assertThrows(IOException.class, () -> DataUpdater.parseResponse("{\"messages\":[\"error\"]}"));
        assertThrows(IOException.class, () -> DataUpdater.parseResponse("{\"properties\":{\"parameter\":{\"PRECTOTCORR\":{}}}}"));
    }

    @Test void builtSeasonFileLoadsInTheGameAndFillsGaps() throws IOException {
        var farm = DataUpdater.parseResponse(RESPONSE);
        var up = DataUpdater.parseResponse(RESPONSE.replace("12.34", "99.9"));
        var target = new DataUpdater.Target("test-2017", "haor", "20170101", "20170103", "Test");
        String json = DataUpdater.buildSeasonJson(target, REGION, farm, up);
        Season s = SeasonLoader.parse(json);
        assertFalse(s.sample);
        assertEquals(3, s.length());
        assertEquals("2017-01-02", s.day(1).date());
        assertEquals(99.9, s.day(1).rainUp());
        assertEquals(12.3, s.day(1).rainFarm());
        assertEquals(0.0, s.day(2).rainFarm(), "fill value -999 becomes 0 mm");
        assertEquals(25.27, s.points.upstream().lat());
    }

    @Test void targetsAndRegionsAreBundled() throws IOException {
        assertFalse(DataUpdater.targets().isEmpty());
        assertTrue(DataUpdater.regions().has(DataUpdater.targets().get(0).region()));
    }
}
