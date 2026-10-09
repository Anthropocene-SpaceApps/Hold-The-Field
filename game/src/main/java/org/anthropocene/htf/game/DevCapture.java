package org.anthropocene.htf.game;

import org.anthropocene.htf.sim.SeasonCatalog;
import org.anthropocene.htf.ui.*;
import org.anthropocene.htf.world.WorldScene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * Developer tool (enabled with -Dhtf.capture=DIR): drives the game through every screen and key moment and writes
 * PNG screenshots. Used to check the UI under a virtual display and to make README screenshots.
 */
final class DevCapture {
    private interface Step { boolean tick(); }

    private final Game game;
    private final Path dir;
    private final Deque<Step> steps = new ArrayDeque<>();
    private String pendingShot;

    DevCapture(Game game, String dir) throws IOException {
        this.game = game;
        this.dir = Path.of(dir);
        Files.createDirectories(this.dir);
        script();
    }

    private Step wait(int frames) { int[] n = {frames}; return () -> --n[0] <= 0; }
    private Step run(Runnable r) { return () -> { r.run(); return true; }; }
    private Step shot(String name) { boolean[] asked = {false}; return () -> { if (!asked[0]) { pendingShot = name; asked[0] = true; } return pendingShot == null; }; }
    private Step until(BooleanSupplier cond, int maxFrames) { int[] n = {maxFrames}; return () -> cond.getAsBoolean() || --n[0] <= 0; }

    /** Place the free camera: x, z and height above ground. */
    private void fly(float x, float z, float above, float yaw, float pitch) {
        game.player().teleport(x, game.scene().landscape().height(x, z) + above, z, yaw, pitch);
        game.player().flying = true;
    }

    private <T extends Screen> T child(T s, Screen parent) { s.parent = parent; return s; }

    private void script() {
        String set = System.getProperty("htf.capture.set", "all");
        boolean menus = set.equals("all") || set.equals("menus"), play = set.equals("all") || set.equals("game");
        if (set.equals("quick")) { quick(); return; }
        Screen[] title = new Screen[1];
        steps.add(wait(25));
        if (menus) {
            steps.add(shot("01-title"));
            steps.add(run(() -> title[0] = game.screen()));
            steps.add(run(() -> game.setScreen(child(new OptionsScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("02-options"));
            steps.add(run(() -> game.setScreen(child(new VideoOptionsScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("03-video"));
            steps.add(run(() -> game.setScreen(child(new KeyBindsScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("04-keybinds"));
            steps.add(run(() -> game.setScreen(child(new AboutScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("05-about"));
            steps.add(run(() -> game.setScreen(child(new AdvancementsScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("06-advancements"));
            steps.add(run(() -> game.setScreen(child(new PlanSeasonScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("07-plan"));
            steps.add(run(() -> game.setScreen(child(new WorldSelectScreen(), title[0]))));
            steps.add(wait(3)); steps.add(shot("08-worlds-empty"));
        }
        if (!play) { steps.add(run(() -> game.window().requestClose())); return; }

        steps.add(run(() -> {
            try {
                game.startSession(new Session(SeasonCatalog.load("haor-2017"), Session.MODE_SCOUT, "short", "Capture Farm", "2017-01-05"));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }));
        steps.add(wait(6)); steps.add(shot("10-ingame-start"));
        steps.add(run(() -> fly(-12f, -38.5f, 1.2f, 0.05f, -0.06f)));
        steps.add(wait(4)); steps.add(shot("11-ingame-dike"));
        steps.add(run(() -> fly(40f, 90f, 60f, 0.207f, -0.30f)));
        steps.add(wait(4)); steps.add(shot("11b-aerial"));
        steps.add(run(() -> fly(-36f, 30f, 1.7f, -1.01f, -0.06f)));
        steps.add(wait(4)); steps.add(shot("11c-plot"));
        steps.add(run(() -> fly(40f, 22f, 1.7f, -1.01f, -0.04f)));
        steps.add(wait(4)); steps.add(shot("11d-home"));
        steps.add(run(() -> fly(30f, -2f, 1.65f, -1.45f, 0.0f)));
        steps.add(wait(4)); steps.add(shot("11e-rahim"));
        steps.add(run(() -> fly(-58f, -50f, 1.7f, 0.9f, -0.12f)));
        steps.add(wait(4)); steps.add(shot("11f-boat"));
        steps.add(run(() -> game.toggleSatellite()));
        steps.add(wait(4)); steps.add(shot("11g-satellite"));
        steps.add(run(() -> game.toggleSatellite()));
        steps.add(run(() -> fly(14f, 48f, 5f, 0.12f, -0.12f)));
        steps.add(run(() -> game.session().timePaused = false));
        steps.add(run(() -> game.session().speedIdx = 3));
        steps.add(until(() -> game.session().state.i >= game.session().state.startIndex + 60, 4000));
        steps.add(run(() -> game.session().timePaused = true));
        steps.add(wait(10)); steps.add(shot("12-ingame-growing"));
        steps.add(run(() -> game.session().timePaused = false));
        steps.add(until(() -> game.session().state.status.equals("warning"), 4000));
        steps.add(run(() -> game.session().timePaused = true));
        steps.add(wait(30)); steps.add(shot("13-ingame-warning"));
        steps.add(run(() -> game.toggleSatellite()));
        steps.add(wait(6)); steps.add(shot("13b-satellite-warning"));
        steps.add(run(() -> game.toggleSatellite()));
        steps.add(run(() -> game.setScreen(new DashboardScreen(game.session()))));
        steps.add(wait(3)); steps.add(shot("14-dashboard"));
        steps.add(run(() -> game.setScreen(null)));
        steps.add(run(() -> { game.session().timePaused = false; game.session().speedIdx = 1; }));
        steps.add(until(() -> game.session().state.flooded, 4000));
        steps.add(run(() -> game.session().timePaused = true));
        steps.add(wait(60)); steps.add(shot("15-ingame-flood"));
        steps.add(run(() -> game.setScreen(new PauseScreen())));
        steps.add(wait(3)); steps.add(shot("16-pause"));
        steps.add(run(() -> game.setScreen(null)));
        steps.add(run(() -> game.session().timePaused = false));
        steps.add(until(() -> game.screen() instanceof EndScreen, 6000));
        steps.add(wait(4)); steps.add(shot("17-end"));
        steps.add(run(() -> game.quitToTitle()));
        steps.add(wait(4));
        steps.add(run(() -> game.setScreen(child(new WorldSelectScreen(), game.screen()))));
        steps.add(wait(4)); steps.add(shot("18-worlds"));
        steps.add(run(() -> game.window().requestClose()));
    }

    /** Fast-forward helper: advance the sim without rendering the days in between. */
    private void ff(java.util.function.Predicate<org.anthropocene.htf.sim.GameState> stop) {
        Session s = game.session();
        while (!stop.test(s.state) && !s.state.finished) s.state = org.anthropocene.htf.sim.Engine.step(s.state, s.season, s.cfg);
        game.scene().snap();
    }

    private void quick() {
        steps.add(run(() -> {
            try { game.startSession(new Session(SeasonCatalog.load("haor-2017"), Session.MODE_SCOUT, "short", "Quick Farm", "2017-01-05")); }
            catch (IOException e) { throw new IllegalStateException(e); }
        }));
        steps.add(wait(4));
        steps.add(run(() -> { ff(st -> st.i >= 60 + st.startIndex); game.session().timePaused = true; fly(14f, 48f, 1.7f, 0.12f, -0.05f); }));
        steps.add(wait(12)); steps.add(shot("q0-growing"));
        steps.add(run(() -> { ff(st -> st.status.equals("warning")); game.session().timePaused = true; }));
        steps.add(wait(40)); steps.add(shot("q1-warning"));
        steps.add(run(() -> game.setScreen(new DashboardScreen(game.session()))));
        steps.add(wait(3)); steps.add(shot("q2-dashboard"));
        steps.add(run(() -> { game.setScreen(null); game.toggleSatellite(); }));
        steps.add(wait(8)); steps.add(shot("q3-satellite"));
        steps.add(run(() -> game.toggleSatellite()));
        steps.add(run(() -> { ff(st -> st.flooded); game.session().timePaused = true; fly(10f, 42f, 3f, 0.1f, -0.1f); }));
        steps.add(wait(50)); steps.add(shot("q4-flood"));
        steps.add(run(() -> game.setScreen(new PauseScreen())));
        steps.add(wait(3)); steps.add(shot("q5-pause"));
        steps.add(run(() -> game.setScreen(null)));
        steps.add(run(() -> { ff(st -> st.over()); }));
        steps.add(until(() -> game.screen() instanceof EndScreen, 3000));
        steps.add(wait(6)); steps.add(shot("q6-end"));
        steps.add(run(() -> game.window().requestClose()));
    }

    void update() {
        Step s = steps.peek();
        if (s != null && s.tick()) steps.poll();
    }

    void afterRender() {
        if (pendingShot == null) return;
        game.screenshotTo(dir.resolve(pendingShot + ".png"));
        System.out.println("captured " + pendingShot);
        if (pendingShot.equals(System.getProperty("htf.capture.stop"))) game.window().requestClose();
        pendingShot = null;
    }
}
