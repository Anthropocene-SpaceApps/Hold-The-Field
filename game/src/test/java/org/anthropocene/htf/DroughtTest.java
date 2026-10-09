package org.anthropocene.htf;

import org.anthropocene.htf.sim.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Barind drought scenario: the bundled sample season, the scout, irrigation and the planting-window analysis. */
class DroughtTest {
    static Season season() throws IOException { return SeasonLoader.load("barind-2022"); }

    @Test
    void bundledSeasonLoadsAsDrought() throws IOException {
        Season s = season();
        assertEquals(174, s.length());
        assertTrue(s.sample, "the bundled Barind season is a labelled sample until real NASA data replaces it");
        assertTrue(Config.forSeason(s).isDrought());
        assertFalse(Config.forSeason(SeasonLoader.load("haor-2017")).isDrought());
        assertEquals(s.points.farm(), s.points.upstream(), "drought is driven locally: one point");
    }

    @Test
    void scoutWarnsBeforeTheCropFeelsTheDrySoil() throws IOException {
        Season s = season();
        Config c = Config.DROUGHT;
        GameState rahim = Engine.autoplay(s, c, "rahim", "long", "2022-07-25");
        Engine.Summary sum = Engine.summarize(rahim);
        assertNotNull(sum.warningDate(), "a warning is issued");
        assertNotNull(sum.floodDate(), "the crop eventually feels the dry soil");
        assertNotNull(sum.leadDays());
        assertTrue(sum.leadDays() >= 3, "lead time was " + sum.leadDays());
    }

    @Test
    void actingOnTheScoutSavesMostOfTheCrop() throws IOException {
        Season s = season();
        Config c = Config.DROUGHT;
        GameState rahim = Engine.autoplay(s, c, "rahim", "long", "2022-07-25");
        GameState scout = Engine.autoplay(s, c, "scout", "long", "2022-07-25");
        assertTrue(scout.yieldPct > rahim.yieldPct + 0.2, "scout " + scout.yieldPct + " vs rahim " + rahim.yieldPct);
        assertTrue(scout.irrigations > 0);
        assertTrue(scout.tank >= 0 && scout.coins >= 0);
    }

    @Test
    void droughtTolerantRiceIsSaferWithoutAWarning() throws IOException {
        Season s = season();
        Config c = Config.DROUGHT;
        double tolerant = Engine.autoplay(s, c, "rahim", "short", "2022-07-25").yieldPct;
        double standard = Engine.autoplay(s, c, "rahim", "long", "2022-07-25").yieldPct;
        assertTrue(tolerant > standard, "tolerant " + tolerant + " vs standard " + standard);
    }

    @Test
    void planningWindowCoversEveryVarietyAndDate() throws IOException {
        List<Engine.Plan> plans = Engine.planningWindow(season(), Config.DROUGHT, "scout");
        assertEquals(6, plans.size());
        for (Engine.Plan p : plans) assertTrue(p.yieldPct() >= 0 && p.yieldPct() <= 1, p.toString());
    }

    @Test
    void irrigationNeedsWaterAndMoney() throws IOException {
        Config c = Config.DROUGHT;
        GameState s = Engine.createState(season(), c, "long", "2022-07-25");
        int done = 0;
        Engine.ActionResult r;
        while ((r = Engine.act(s, Engine.ActionType.IRRIGATE, c)).ok()) { s = r.state(); done++; assertTrue(done < 20); }
        assertEquals((int) c.tankStart, done, "the tank limits irrigation");
        assertEquals("The water tank is empty", r.reason());
        assertEquals(c.startCoins - done * c.irrigationCost, s.coins);
    }

    @Test
    void hazardsKeepTheirOwnActions() throws IOException {
        GameState dry = Engine.createState(season(), Config.DROUGHT, "long", "2022-07-25");
        assertFalse(Engine.act(dry, Engine.ActionType.RAISE_BUND, Config.DROUGHT).ok());
        GameState wet = Engine.createState(SeasonLoader.load("haor-2017"), Config.DEFAULT, "long");
        assertFalse(Engine.act(wet, Engine.ActionType.IRRIGATE, Config.DEFAULT).ok());
    }

    @Test
    void sameDataAndActionsGiveTheSameSeason() throws IOException {
        GameState a = Engine.autoplay(season(), Config.DROUGHT, "scout", "short", "2022-07-10");
        GameState b = Engine.autoplay(season(), Config.DROUGHT, "scout", "short", "2022-07-10");
        assertEquals(a.yieldPct, b.yieldPct);
        assertEquals(a.events, b.events);
    }

    @Test
    void scoutDoesNotRepeatTheSameAlertEveryOtherDay() throws IOException {
        GameState s = Engine.autoplay(season(), Config.DROUGHT, "rahim", "long", "2022-07-25");
        long warnings = s.events.stream().filter(e -> e.type().equals("warning")).count();
        assertTrue(warnings <= 6, "warnings: " + warnings);
        for (int i = 1; i < s.events.size(); i++) {
            Event p = s.events.get(i - 1), q = s.events.get(i);
            if (p.type().equals("warning") && q.type().equals("warning")) assertTrue(q.i() - p.i() > 12);
        }
    }
}
