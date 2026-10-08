package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

public final class SoundOptionsScreen extends Screen {
    @Override
    protected void init() {
        Settings s = game.settings();
        int cx = w / 2 - 100;
        add(new Widget.Slider("Master Volume", s.masterVolume, v -> Math.round(v * 100) + "%", v -> { s.masterVolume = v; game.audio().applyVolumes(); })).bounds(cx, 40, 200, 20);
        add(new Widget.Slider("Sound Effects", s.effectsVolume, v -> Math.round(v * 100) + "%", v -> s.effectsVolume = v)).bounds(cx, 64, 200, 20);
        add(new Widget.Slider("Weather & Music", s.ambientVolume, v -> Math.round(v * 100) + "%", v -> { s.ambientVolume = v; game.audio().applyVolumes(); })).bounds(cx, 88, 200, 20);
        button("Test Sound", cx, 118, 200, () -> game.audio().warning());
        button("Done", cx, h - 32, 200, this::onEscape);
    }

    @Override protected void onEscape() { game.applySettings(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Music & Sounds", 14);
        for (Widget wd : widgets) wd.render(r, mx, my);
        if (!game.audio().isEnabled()) r.textCentered("No audio device found: sound is off.", w / 2f, 150, 8.5f, 0xFFFF8866, true, false);
    }
}
