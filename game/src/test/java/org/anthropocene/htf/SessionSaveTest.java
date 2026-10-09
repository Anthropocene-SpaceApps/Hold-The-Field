package org.anthropocene.htf;

import org.anthropocene.htf.game.SaveManager;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.Season;
import org.anthropocene.htf.sim.SeasonCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SessionSaveTest {
    @BeforeAll static void home(@TempDir Path dir) { System.setProperty("htf.home", dir.toString()); }

    private static Season season() throws IOException { return SeasonCatalog.load("haor-2017"); }

    @Test void restoredSessionMatchesTheOriginalExactly() throws IOException {
        Session a = new Session(season(), Session.MODE_SCOUT, "short", "Test");
        a.timePaused = false;
        a.speedIdx = 0;
        for (int i = 0; i < 70; i++) a.tick(1.0);                       // one day per tick at 1x
        assertTrue(a.act(Engine.ActionType.RAISE_BUND).ok());
        for (int i = 0; i < 12; i++) a.tick(1.0);
        a.act(Engine.ActionType.RAISE_BUND);

        SaveManager.save(a.toSave());
        SaveManager.SaveData loaded = SaveManager.list().stream().filter(d -> d.id.equals(a.id)).findFirst().orElseThrow();
        Session b = Session.restore(season(), loaded);

        assertEquals(a.state.i, b.state.i);
        assertEquals(a.state.coins, b.state.coins);
        assertEquals(a.state.bund, b.state.bund, 1e-12);
        assertEquals(a.state.level, b.state.level, 1e-12);
        assertEquals(a.state.events, b.state.events);
        assertTrue(b.drainEvents().isEmpty(), "restored history is not news");
    }

    @Test void droughtSessionRestoresWithItsIrrigation() throws IOException {
        Season dry = SeasonCatalog.load("barind-2022");
        Session a = new Session(dry, Session.MODE_SCOUT, "long", "Dry test");
        assertTrue(a.cfg.isDrought());
        a.timePaused = false;
        for (int i = 0; i < 95; i++) a.tick(1.0);
        assertTrue(a.act(Engine.ActionType.IRRIGATE).ok());
        for (int i = 0; i < 6; i++) a.tick(1.0);
        assertTrue(a.act(Engine.ActionType.IRRIGATE).ok());
        assertFalse(a.act(Engine.ActionType.RAISE_BUND).ok(), "no bund in the drought scenario");

        SaveManager.save(a.toSave());
        SaveManager.SaveData loaded = SaveManager.list().stream().filter(d -> d.id.equals(a.id)).findFirst().orElseThrow();
        assertEquals("barind-2022", loaded.seasonId);
        Session b = Session.restore(dry, loaded);
        assertEquals(a.state.i, b.state.i);
        assertEquals(a.state.coins, b.state.coins);
        assertEquals(a.state.tank, b.state.tank, 1e-12);
        assertEquals(a.state.moisture, b.state.moisture, 1e-12);
        assertEquals(a.state.stressLoad, b.state.stressLoad, 1e-12);
        assertEquals(a.state.events, b.state.events);
    }

    @Test void rahimModeCannotAct() throws IOException {
        Session s = new Session(season(), Session.MODE_RAHIM, "short", "Watch");
        assertEquals("long", s.variety);
        assertFalse(s.act(Engine.ActionType.RAISE_BUND).ok());
    }

    @Test void deleteRemovesTheSave() throws IOException {
        Session s = new Session(season(), Session.MODE_SCOUT, "long", "Temp");
        SaveManager.save(s.toSave());
        assertTrue(SaveManager.list().stream().anyMatch(d -> d.id.equals(s.id)));
        SaveManager.delete(s.id);
        assertTrue(SaveManager.list().stream().noneMatch(d -> d.id.equals(s.id)));
    }

    @Test void speedControlsAreClamped() throws IOException {
        Session s = new Session(season(), Session.MODE_SCOUT, "long", "Speed");
        for (int i = 0; i < 10; i++) s.speedUp();
        assertEquals(Session.SPEEDS.length - 1, s.speedIdx);
        for (int i = 0; i < 10; i++) s.speedDown();
        assertEquals(0, s.speedIdx);
    }

    @Test void launcherPassesAnAbsoluteClasspathToTheGameProcess() {
        String cp = org.anthropocene.htf.launcher.GameProcess.absoluteClasspath("hold-the-field.jar" + java.io.File.pathSeparator + "lib/x.jar");
        for (String e : cp.split(java.io.File.pathSeparator)) assertTrue(new java.io.File(e).isAbsolute(), e);
    }
}
