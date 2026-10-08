package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

public final class SoundOptionsScreen extends Screen {
    @Override
    protected void init() {
        layoutCard(560, 430);
        Settings s = game.settings();
        int x = cardX + 32, rw = cardW - 64, y = cardY + 92;
        add(new Widget.Slider("Master volume", s.masterVolume, v -> Math.round(v * 100) + "%", v -> { s.masterVolume = v; game.audio().applyVolumes(); })).bounds(x, y, rw, 44);
        add(new Widget.Slider("Sound effects", s.effectsVolume, v -> Math.round(v * 100) + "%", v -> s.effectsVolume = v)).bounds(x, y + 52, rw, 44);
        add(new Widget.Slider("Weather and music", s.ambientVolume, v -> Math.round(v * 100) + "%", v -> { s.ambientVolume = v; game.audio().applyVolumes(); })).bounds(x, y + 104, rw, 44);
        button("Play test sound", x, y + 160, rw, () -> game.audio().warning());
        button("Done", x, cardY + cardH - 66, rw, this::onEscape).primary();
    }

    @Override protected void onEscape() { game.applySettings(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        heading(r, "Music and sounds");
        for (Widget wd : widgets) wd.render(r, mx, my);
        if (!game.audio().isEnabled()) r.text("No audio device found: sound is off.", cardX + 32, cardY + 304, 13, Theme.WARN);
    }
}
