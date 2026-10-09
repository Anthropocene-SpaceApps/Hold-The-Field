package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.I18n;
import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

/** Top-level options. */
public final class OptionsScreen extends Screen {
    @Override
    protected void init() {
        layoutCard(560, 560);
        Settings s = game.settings();
        int x = cardX + 32, rw = cardW - 64, y = cardY + 92, dy = 52;
        add(new Widget.Slider("Field of view", (s.fov - 40) / 70.0, v -> String.valueOf(40 + (int) Math.round(v * 70)), v -> s.fov = 40 + (int) Math.round(v * 70))).bounds(x, y, rw, 44);
        button("Video settings", x, y + dy, rw, () -> open(new VideoOptionsScreen()));
        button("Music and sounds", x, y + dy * 2, rw, () -> open(new SoundOptionsScreen()));
        button("Controls", x, y + dy * 3, rw, () -> open(new ControlsScreen()));
        add(Widget.toggle("Show hints", s.showHints, v -> s.showHints = v)).bounds(x, y + dy * 4, rw, 44);
        add(Widget.toggle("Warn when using sample data", s.sampleDataWarning, v -> s.sampleDataWarning = v)).bounds(x, y + dy * 5, rw, 44);
        add(new Widget.Cycle<>("Language", java.util.List.of(I18n.EN, I18n.BN), I18n.BN.equals(s.language) ? 1 : 0, I18n::languageName,
                v -> { s.language = v; I18n.setLanguage(v); })).bounds(x, y + dy * 6, rw, 44);
        button("Done", x, cardY + cardH - 66, rw, this::onEscape).primary();
    }

    @Override
    protected void onEscape() { game.applySettings(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        heading(r, "Options");
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
