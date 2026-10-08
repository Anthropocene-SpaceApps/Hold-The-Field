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

    private <T extends Screen> T child(T s, Screen parent) { s.parent = parent; return s; }

    private void script() {
        Screen[] title = new Screen[1];
        steps.add(wait(25));
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
        steps.add(run(() -> game.setScreen(child(new CreateWorldScreen(), title[0]))));
        steps.add(wait(3)); steps.add(shot("07-create"));
        steps.add(run(() -> game.setScreen(child(new WorldSelectScreen(), title[0]))));
        steps.add(wait(3)); steps.add(shot("08-worlds-empty"));

        steps.add(run(() -> {
            try {
                game.startSession(new Session(SeasonCatalog.load("haor-2017"), Session.MODE_SCOUT, "short", "Capture World"));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }));
        steps.add(wait(6)); steps.add(shot("10-ingame-start"));
        steps.add(run(() -> game.player().teleport(2f, 5f, -4f, 0.7f, -0.35f)));
        steps.add(wait(4)); steps.add(shot("11-ingame-lakeside"));
        steps.add(run(() -> { game.player().teleport(4f, 8f, 20f, 0f, -0.45f); game.player().flying = true; }));
        steps.add(wait(4));
        steps.add(run(() -> game.session().timePaused = false));
        steps.add(run(() -> game.session().speedIdx = 3));
        steps.add(until(() -> game.session().state.i >= 60, 4000));
        steps.add(shot("12-ingame-growing"));
        steps.add(until(() -> game.session().state.status.equals("warning"), 4000));
        steps.add(run(() -> game.session().timePaused = true));
        steps.add(wait(30)); steps.add(shot("13-ingame-warning"));
        steps.add(run(() -> game.setScreen(new DashboardScreen(game.session()))));
        steps.add(wait(3)); steps.add(shot("14-dashboard"));
        steps.add(run(() -> game.setScreen(null)));
        steps.add(run(() -> { game.session().timePaused = false; game.session().speedIdx = 1; }));
        steps.add(until(() -> game.session().state.flooded, 4000));
        steps.add(run(() -> game.session().timePaused = true));
        steps.add(wait(60)); steps.add(shot("15-ingame-flood"));
        steps.add(run(() -> { game.player().teleport(4f, 3f, 12f, 0f, -0.1f); }));
        steps.add(wait(8)); steps.add(shot("15b-flood-low"));
        steps.add(run(() -> game.session().timePaused = false));
        steps.add(run(() -> game.setScreen(new PauseScreen())));
        steps.add(wait(3)); steps.add(shot("16-pause"));
        steps.add(run(() -> game.setScreen(null)));
        steps.add(until(() -> game.screen() instanceof EndScreen, 6000));
        steps.add(wait(4)); steps.add(shot("17-end"));
        steps.add(run(() -> game.quitToTitle()));
        steps.add(wait(4));
        steps.add(run(() -> game.setScreen(child(new WorldSelectScreen(), game.screen()))));
        steps.add(wait(4)); steps.add(shot("18-worlds"));
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
        pendingShot = null;
    }
}
