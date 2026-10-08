package org.anthropocene.htf;

import org.anthropocene.htf.sim.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    static final Config CFG = Config.DEFAULT;

    /** Small hand-made dataset so tests never depend on the real JSON. */
    static Season makeData(Map<Integer, Double> rainUp) {
        Season s = new Season();
        s.days = new ArrayList<>();
        LocalDate start = LocalDate.of(2017, 1, 1);
        for (int d = 0; d < 120; d++) {
            s.days.add(new Day(start.plusDays(d).toString(), rainUp.getOrDefault(d, 0.0), 0, 30, 0.6));
        }
        s.points = new Season.Points(new Season.LatLon(0, 0), new Season.LatLon(0, 0));
        return s;
    }

    static Map<Integer, Double> storm(int from, int to, double mm) {
        Map<Integer, Double> m = new java.util.HashMap<>();
        for (int d = from; d <= to; d++) m.put(d, mm);
        return m;
    }

    @Test void dryDayCropReachesFullHarvestNoFlood() {
        GameState f = Engine.autoplay(makeData(Map.of()), CFG, "rahim", "short");
        assertTrue(f.alive);
        assertEquals(1.0, f.yieldPct);
        assertTrue(f.events.stream().noneMatch(e -> e.type().equals("flood")));
    }

    @Test void bigUpstreamStormKillsAnUnprotectedCrop() {
        GameState f = Engine.autoplay(makeData(storm(80, 88, 220)), CFG, "rahim", "long");
        assertTrue(f.events.stream().anyMatch(e -> e.type().equals("flood")));
        assertFalse(f.alive);
        assertEquals(0.0, f.yieldPct);
    }

    @Test void scoutWarningFiresBeforeTheFlood() {
        GameState f = Engine.autoplay(makeData(storm(80, 88, 220)), CFG, "rahim", "long");
        Event warn = f.events.stream().filter(e -> e.type().equals("warning")).findFirst().orElseThrow();
        Event flood = f.events.stream().filter(e -> e.type().equals("flood")).findFirst().orElseThrow();
        assertTrue(warn.i() < flood.i(), "warning day " + warn.i() + " should be before flood day " + flood.i());
    }

    @Test void scoutStatusThresholds() {
        Map<Integer, Double> r = new java.util.HashMap<>(Map.of(10, 50.0, 11, 50.0, 12, 50.0));
        r.putAll(Map.of(20, 100.0, 21, 100.0, 22, 100.0));
        List<Day> d = makeData(r).days;
        assertEquals("calm", Engine.scoutStatus(d, 5, CFG));
        assertEquals("watch", Engine.scoutStatus(d, 12, CFG));
        assertEquals("warning", Engine.scoutStatus(d, 22, CFG));
    }

    @Test void cannotHarvestBeforeMinimumMaturity() {
        Season data = makeData(Map.of());
        GameState s = Engine.step(Engine.createState(data, CFG, "long"), data, CFG);
        assertFalse(Engine.act(s, Engine.ActionType.HARVEST, CFG).ok());
    }

    @Test void raisingTheBundCostsCoinsAndIsCapped() {
        Season data = makeData(Map.of());
        GameState s = Engine.createState(data, CFG, "long");
        for (int k = 0; k < CFG.maxBundRaises; k++) s = Engine.act(s, Engine.ActionType.RAISE_BUND, CFG).state();
        assertEquals(CFG.startCoins - CFG.maxBundRaises * CFG.bundRaiseCost, s.coins);
        assertFalse(Engine.act(s, Engine.ActionType.RAISE_BUND, CFG).ok());
    }

    @Test void simulationIsDeterministic() {
        Season data = makeData(storm(70, 75, 150));
        GameState a = Engine.autoplay(data, CFG, "scout"), b = Engine.autoplay(data, CFG, "scout");
        assertEquals(a.events, b.events);
        assertEquals(a.yieldPct, b.yieldPct);
        assertEquals(a.history, b.history);
    }

    @Test void maturityGrowsLinearlyFromTransplantDate() {
        assertEquals(0, Engine.maturityOn(CFG.transplantDate.toString(), "long", CFG));
        assertEquals(1, Engine.maturityOn("2030-01-01", "long", CFG));
    }

    @Test void historyHasOnePointPerDayAndTracksBundRaises() {
        Season data = makeData(Map.of());
        GameState s = Engine.step(Engine.createState(data, CFG, "long"), data, CFG);
        s = Engine.act(s, Engine.ActionType.RAISE_BUND, CFG).state();
        assertEquals(2, s.history.size());
        assertEquals(CFG.bundStart + CFG.bundRaise, s.history.get(1).bund(), 1e-9);
    }

    @Test void summarizeReportsScoutLeadTime() {
        GameState f = Engine.autoplay(makeData(storm(80, 88, 220)), CFG, "rahim", "long");
        Engine.Summary sum = Engine.summarize(f);
        assertNotNull(sum.leadDays());
        assertTrue(sum.leadDays() > 0);
        assertTrue(sum.lost());
        assertNull(Engine.summarize(Engine.autoplay(makeData(Map.of()), CFG, "rahim", "short")).leadDays());
    }

    @Test void actingOnTheScoutBeatsIgnoringItInTheSameStorm() {
        Season data = makeData(storm(80, 88, 220));
        assertTrue(Engine.autoplay(data, CFG, "scout").yieldPct > Engine.autoplay(data, CFG, "rahim").yieldPct);
    }

    @Test void bundledSeasonFileLoads() throws IOException {
        Season s = SeasonLoader.load("haor-2017");
        assertTrue(s.length() >= 100);
        assertNotNull(s.points.farm());
        if (!s.sample) {
            assertFalse(Engine.autoplay(s, CFG, "rahim", "long").alive, "Rahim loses the crop in the real 2017 flood");
            assertTrue(Engine.autoplay(s, CFG, "scout").yieldPct > 0, "the scout-assisted player saves some harvest");
        }
    }

    @Test void malformedSeasonIsRejected() {
        assertThrows(IllegalStateException.class, () -> SeasonLoader.parse("{\"days\":[]}"));
    }
}
