package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import java.util.List;
import java.util.Random;

/** Home menu, drawn over the slowly orbiting 3D haor. */
public final class TitleScreen extends Screen {
    private static final List<String> SPLASHES = List.of("Powered by NASA POWER!", "Real rain, real data!", "Hold the bund!",
            "Watch the Meghalaya hills!", "Rahim says hi!", "Check the scout!", "Made in Chattogram!", "Space Apps 2026!",
            "Short rice, fewer tears!", "Don't forget the sickle!", "50% more satellites!");
    private final String splash = SPLASHES.get(new Random().nextInt(SPLASHES.size()));
    private double t;

    @Override public boolean transparentBackground() { return true; }

    @Override
    protected void init() {
        int cx = w / 2 - 100, y = Math.max(h / 4 + 32, 96);
        button("Singleplayer", cx, y, 200, () -> game.setScreen(child(new WorldSelectScreen())));
        button("Data & Model", cx, y + 24, 200, () -> game.setScreen(child(new AboutScreen())));
        button("Advancements", cx, y + 48, 200, () -> game.setScreen(child(new AdvancementsScreen())));
        button("Options...", cx, y + 80, 98, () -> game.setScreen(child(new OptionsScreen())));
        button("Quit Game", cx + 102, y + 80, 98, game::quit);
    }

    private Screen child(Screen s) { s.parent = this; return s; }

    @Override
    protected void background(Renderer2D r) {
        r.gradientV(0, 0, w, h, 0x50000000, 0x90000000);
    }

    @Override
    public void tick(double dt) { t += dt; }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        float cx = w / 2f, ly = Math.max(h / 4 - 38, 14);
        // blocky shadowed logo
        String logo = "HOLD THE FIELD";
        float size = Math.min(34, w / 15f);
        for (int d = 4; d >= 1; d--) r.textCentered(logo, cx + d, ly + d, size, 0xFF3A2A12, false, true);
        r.textCentered(logo, cx, ly, size, 0xFFE6B73A, false, true);
        r.textCentered("NASA SPACE APPS 2026  |  FIELD SHIFT", cx, ly + size + 3, 8.5f, 0xFFFFFFFF, true, false);
        float pulse = 1 + (float) Math.sin(t * 5) * 0.06f;
        r.textCentered(splash, cx, ly + size + 15, 9.5f * pulse, 0xFFFFFF55, true, true);

        for (Widget wd : widgets) wd.render(r, mx, my);

        r.text("Hold the Field 1.0.0  |  Team Anthropocene", 4, h - 11, 7.5f, 0xFFFFFFFF, true, false);
        r.textRight("Real NASA data. Real floods. Real farmers.", w - 4, h - 11, 7.5f, 0xFFFFFFFF, true);
        if (game.titleSeason() != null && game.titleSeason().sample && game.settings().sampleDataWarning) {
            r.textCentered("Bundled data is SAMPLE data. Use the Launcher > Update NASA data to download the real season.", cx, h - 24, 7.5f, 0xFFFF7766, true, false);
        }
    }

    @Override protected void onEscape() {}
}
