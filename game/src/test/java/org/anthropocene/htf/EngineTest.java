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
        assertEquals(CFG.varieties.get("short").potential(), f.yieldPct, 1e-9, "a full harvest yields the variety's potential");
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
        Map<Integer, Double> r = new java.util.HashMap<>(Map.of(10, 40.0, 11, 40.0, 12, 40.0));
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

    @Test void raisingTheBundChangesHeightAndRecordsAction() {
        Season data = makeData(Map.of());
        GameState before = Engine.createState(data, CFG, "long");

        Engine.ActionResult result = Engine.act(before, Engine.ActionType.RAISE_BUND, CFG);

        assertTrue(result.ok());
        assertEquals(before.bund + CFG.bundRaise, result.state().bund, 1e-9);
        assertEquals(before.coins - CFG.bundRaiseCost, result.state().coins);
        assertTrue(result.state().events.stream().anyMatch(e -> e.type().equals("action")));
    }

    @Test void simulationIsDeterministic() {
        Season data = makeData(storm(70, 75, 150));
        GameState a = Engine.autoplay(data, CFG, "scout"), b = Engine.autoplay(data, CFG, "scout");
        assertEquals(a.events, b.events);
        assertEquals(a.yieldPct, b.yieldPct);
        assertEquals(a.history, b.history);
    }

    @Test void maturityGrowsLinearlyFromTransplantDate() {
        assertEquals(0, Engine.maturityOn(CFG.defaultTransplant.toString(), "long", CFG));
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

    @Test void shortVarietyYieldsLessButRipensSooner() {
        Season data = makeData(Map.of());
        GameState shortRice = Engine.autoplay(data, CFG, "rahim", "short");
        GameState longRice = Engine.autoplay(data, CFG, "rahim", "long");
        assertEquals(CFG.varieties.get("short").potential(), shortRice.yieldPct, 1e-9);
        assertEquals(CFG.varieties.get("long").potential(), longRice.yieldPct, 1e-9);
        assertTrue(shortRice.yieldPct < longRice.yieldPct);
        int shortDay = shortRice.events.stream().filter(e -> e.type().equals("harvest")).findFirst().orElseThrow().i();
        int longDay = longRice.events.stream().filter(e -> e.type().equals("harvest")).findFirst().orElseThrow().i();
        assertTrue(shortDay < longDay);
    }

    @Test void transplantDateMovesTheWholeSchedule() {
        Season data = makeData(Map.of());
        GameState early = Engine.createState(data, CFG, "long", "2017-01-02");
        GameState late = Engine.createState(data, CFG, "long", "2017-01-20");
        assertEquals("2017-01-02", early.transplant);
        assertEquals(1, early.startIndex);
        assertEquals(19, late.startIndex);
        assertEquals(0.0, late.maturity, 1e-9, "nothing has grown on the transplant day");
        assertTrue(Engine.step(late, data, CFG).maturity > 0);
        GameState e = Engine.autoplay(data, CFG, "rahim", "short", "2017-01-02"), l = Engine.autoplay(data, CFG, "rahim", "short", "2017-01-20");
        assertTrue(e.events.stream().filter(x -> x.type().equals("harvest")).findFirst().orElseThrow().i()
                < l.events.stream().filter(x -> x.type().equals("harvest")).findFirst().orElseThrow().i());
    }

    @Test void planningWindowCoversEveryOptionAndRewardsEarlyShortRiceInALateFlood() {
        // flood centred on day ~100: short rice planted on the earliest option is cut long before it
        Season data = makeData(storm(95, 108, 230));
        List<Engine.Plan> noWarning = Engine.planningWindow(data, CFG, "rahim");
        assertTrue(noWarning.size() >= 2);
        Engine.Plan earlyShort = noWarning.stream().filter(p -> p.variety().equals("short")).min(java.util.Comparator.comparing(Engine.Plan::transplant)).orElseThrow();
        Engine.Plan lateLong = noWarning.stream().filter(p -> p.variety().equals("long")).max(java.util.Comparator.comparing(Engine.Plan::transplant)).orElseThrow();
        assertTrue(earlyShort.yieldPct() >= lateLong.yieldPct());
        List<Engine.Plan> scout = Engine.planningWindow(data, CFG, "scout");
        for (int i = 0; i < noWarning.size(); i++) assertTrue(scout.get(i).yieldPct() >= noWarning.get(i).yieldPct() - 1e-9, "acting on the scout never does worse");
    }

    @Test void historyStartsAtTheTransplantDay() {
        Season data = makeData(Map.of());
        GameState s = Engine.createState(data, CFG, "long", "2017-01-10");
        s = Engine.step(s, data, CFG);
        assertEquals(2, s.history.size());
        assertEquals(s.startIndex + 1, s.i);
    }
}
