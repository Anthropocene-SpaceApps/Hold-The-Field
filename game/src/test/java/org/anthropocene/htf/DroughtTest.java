package org.anthropocene.htf;

import org.anthropocene.htf.sim.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Barind drought scenario: the bundled sample season, the scout, irrigation and the planting-window analysis. */
class DroughtTest {
    static Season realSeason() throws IOException { return SeasonLoader.load("barind-2010"); }

    static Season droughtFixture() {
        Season s = new Season();
        s.region = "barind-fixture";
        s.hazard = "drought";
        s.sample = true;
        s.points = new Season.Points(new Season.LatLon(25.0, 91.0), new Season.LatLon(25.0, 91.0));
        s.days = new ArrayList<>();
        LocalDate start = LocalDate.of(2010, 6, 25);
        for (int i = 0; i < 174; i++) {
            double soil = i < 50 ? 0.82 : i < 65 ? 0.66 : i < 85 ? 0.52 : 0.70;
            double rain = i >= 35 && i < 45 ? 10 : 0;
            s.days.add(new Day(start.plusDays(i).toString(), 0, rain, 35, soil));
        }
        return s;
    }

    static Season wetFixture() {
        Season s = droughtFixture();
        s.days = new ArrayList<>();
        LocalDate start = LocalDate.of(2010, 6, 25);
        for (int i = 0; i < 174; i++) s.days.add(new Day(start.plusDays(i).toString(), 0, 10, 32, 0.85));
        return s;
    }

    @Test
    void bundledSeasonLoadsAsDrought() throws IOException {
        Season s = realSeason();
        assertEquals(174, s.length());
        assertFalse(s.sample, "the bundled Barind file is real NASA POWER data");
        assertTrue(Config.forSeason(s).isDrought());
        assertFalse(Config.forSeason(SeasonLoader.load("haor-2017")).isDrought());
        assertEquals(s.points.farm(), s.points.upstream(), "drought is driven locally: one point");
    }

    @Test
    void real2010DroughtRewardsTheScoutAndTolerantRice() throws IOException {
        Config c = Config.DROUGHT;
        GameState rahim = Engine.autoplay(realSeason(), c, "rahim", "long", "2010-07-25");
        GameState scout = Engine.autoplay(realSeason(), c, "scout", "long", "2010-07-25");
        GameState tolerant = Engine.autoplay(realSeason(), c, "rahim", "short", "2010-07-25");

        assertTrue(rahim.yieldPct < 0.6, "Rahim yield " + rahim.yieldPct);
        assertTrue(scout.yieldPct > rahim.yieldPct + 0.1, "Scout " + scout.yieldPct + " vs Rahim " + rahim.yieldPct);
        assertTrue(tolerant.yieldPct > 0.8, "tolerant yield " + tolerant.yieldPct);
    }

    @Test
    void wetFixtureDoesNotCreateDroughtLoss() {
        Config c = Config.DROUGHT;
        GameState rahim = Engine.autoplay(wetFixture(), c, "rahim", "long", "2010-07-25");
        GameState scout = Engine.autoplay(wetFixture(), c, "scout", "long", "2010-07-25");

        assertEquals(1.0, rahim.yieldPct, 1e-9);
        assertEquals(rahim.yieldPct, scout.yieldPct, 1e-9);
        assertTrue(rahim.events.stream().noneMatch(e -> e.type().equals("stress") || e.type().equals("loss")));
    }

    @Test
    void scoutWarnsBeforeTheCropFeelsTheDrySoil() throws IOException {
        Season s = droughtFixture();
        Config c = Config.DROUGHT;
        GameState rahim = Engine.autoplay(s, c, "rahim", "long", "2010-07-25");
        Engine.Summary sum = Engine.summarize(rahim);
        assertNotNull(sum.warningDate(), "a warning is issued");
        assertNotNull(sum.floodDate(), "the crop eventually feels the dry soil");
        assertNotNull(sum.leadDays());
        assertTrue(sum.leadDays() >= 3, "lead time was " + sum.leadDays());
    }

    @Test
    void actingOnTheScoutSavesMostOfTheCrop() throws IOException {
        Season s = droughtFixture();
        Config c = Config.DROUGHT;
        GameState rahim = Engine.autoplay(s, c, "rahim", "long", "2010-07-25");
        GameState scout = Engine.autoplay(s, c, "scout", "long", "2010-07-25");
        assertTrue(scout.yieldPct > rahim.yieldPct + 0.1, "scout " + scout.yieldPct + " vs rahim " + rahim.yieldPct);
        assertTrue(scout.irrigations > 0);
        assertTrue(scout.tank >= 0 && scout.coins >= 0);
    }

    @Test
    void droughtTolerantRiceIsSaferWithoutAWarning() throws IOException {
        Season s = droughtFixture();
        Config c = Config.DROUGHT;
        double tolerant = Engine.autoplay(s, c, "rahim", "short", "2010-07-25").yieldPct;
        double standard = Engine.autoplay(s, c, "rahim", "long", "2010-07-25").yieldPct;
        assertTrue(tolerant > standard, "tolerant " + tolerant + " vs standard " + standard);
    }

    @Test
    void planningWindowCoversEveryVarietyAndDate() throws IOException {
        List<Engine.Plan> plans = Engine.planningWindow(droughtFixture(), Config.DROUGHT, "scout");
        assertEquals(6, plans.size());
        for (Engine.Plan p : plans) assertTrue(p.yieldPct() >= 0 && p.yieldPct() <= 1, p.toString());
    }

    @Test
    void irrigationNeedsWaterAndMoney() throws IOException {
        Config c = Config.DROUGHT;
        GameState s = Engine.createState(droughtFixture(), c, "long", "2010-07-25");
        int done = 0;
        Engine.ActionResult r;
        while ((r = Engine.act(s, Engine.ActionType.IRRIGATE, c)).ok()) { s = r.state(); done++; assertTrue(done < 20); }
        assertEquals((int) c.tankStart, done, "the tank limits irrigation");
        assertEquals("The water tank is empty", r.reason());
        assertEquals(c.startCoins - done * c.irrigationCost, s.coins);
    }

    @Test
    void irrigationRaisesMoistureAndUsesOneTankLoad() throws IOException {
        Config c = Config.DROUGHT;
        GameState before = Engine.createState(droughtFixture(), c, "long", "2010-07-25");

        Engine.ActionResult result = Engine.act(before, Engine.ActionType.IRRIGATE, c);

        assertTrue(result.ok());
        assertEquals(before.tank - 1, result.state().tank);
        assertTrue(result.state().moisture > before.moisture);
        assertEquals(before.coins - c.irrigationCost, result.state().coins);
    }

    @Test
    void hazardsKeepTheirOwnActions() throws IOException {
        GameState dry = Engine.createState(droughtFixture(), Config.DROUGHT, "long", "2010-07-25");
        assertFalse(Engine.act(dry, Engine.ActionType.RAISE_BUND, Config.DROUGHT).ok());
        GameState wet = Engine.createState(SeasonLoader.load("haor-2017"), Config.DEFAULT, "long");
        assertFalse(Engine.act(wet, Engine.ActionType.IRRIGATE, Config.DEFAULT).ok());
    }

    @Test
    void sameDataAndActionsGiveTheSameSeason() throws IOException {
        GameState a = Engine.autoplay(droughtFixture(), Config.DROUGHT, "scout", "short", "2010-07-10");
        GameState b = Engine.autoplay(droughtFixture(), Config.DROUGHT, "scout", "short", "2010-07-10");
        assertEquals(a.yieldPct, b.yieldPct);
        assertEquals(a.events, b.events);
    }

    @Test
    void scoutDoesNotRepeatTheSameAlertEveryOtherDay() throws IOException {
        GameState s = Engine.autoplay(droughtFixture(), Config.DROUGHT, "rahim", "long", "2010-07-25");
        long warnings = s.events.stream().filter(e -> e.type().equals("warning")).count();
        assertTrue(warnings <= 6, "warnings: " + warnings);
        for (int i = 1; i < s.events.size(); i++) {
            Event p = s.events.get(i - 1), q = s.events.get(i);
            if (p.type().equals("warning") && q.type().equals("warning")) assertTrue(q.i() - p.i() > 12);
        }
    }
}
