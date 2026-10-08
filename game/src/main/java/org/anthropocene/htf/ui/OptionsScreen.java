package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

/** Top-level options (Minecraft-style two column layout). */
public final class OptionsScreen extends Screen {
    @Override
    protected void init() {
        Settings s = game.settings();
        int l = w / 2 - 155, rgt = w / 2 + 5, y = 50;
        add(new Widget.Slider("FOV", (s.fov - 40) / 70.0, v -> String.valueOf(40 + (int) Math.round(v * 70)), v -> { s.fov = 40 + (int) Math.round(v * 70); })).bounds(l, y, 150, 20);
        button("Video Settings...", rgt, y, 150, () -> open(new VideoOptionsScreen()));
        button("Music & Sounds...", l, y + 24, 150, () -> open(new SoundOptionsScreen()));
        button("Controls...", rgt, y + 24, 150, () -> open(new ControlsScreen()));
        add(Widget.toggle("Show Hints", s.showHints, v -> s.showHints = v)).bounds(l, y + 48, 150, 20);
        add(Widget.toggle("Sample Data Warning", s.sampleDataWarning, v -> s.sampleDataWarning = v)).bounds(rgt, y + 48, 150, 20);
        button("Done", w / 2 - 100, h - 32, 200, this::onEscape);
    }

    private void open(Screen s) { s.parent = this; game.setScreen(s); }

    @Override
    protected void onEscape() { game.applySettings(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Options", 14);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
