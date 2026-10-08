package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

public final class ControlsScreen extends Screen {
    @Override
    protected void init() {
        Settings s = game.settings();
        int cx = w / 2 - 100;
        add(new Widget.Slider("Mouse Sensitivity", s.mouseSensitivity, v -> v < 0.01 ? "*yawn*" : v > 0.99 ? "HYPERSPEED!!!" : Math.round(v * 200) + "%", v -> s.mouseSensitivity = v)).bounds(cx, 40, 200, 20);
        add(Widget.toggle("Invert Mouse", s.invertY, v -> s.invertY = v)).bounds(cx, 64, 200, 20);
        button("Key Binds...", cx, 98, 200, () -> { Screen k = new KeyBindsScreen(); k.parent = this; game.setScreen(k); });
        button("Done", cx, h - 32, 200, this::onEscape);
    }

    @Override protected void onEscape() { game.settings().save(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Controls", 14);
        for (Widget wd : widgets) wd.render(r, mx, my);
        r.textCentered("Hotbar: 1-9 or mouse wheel   |   Pause: Esc   |   Double-tap Jump to fly", w / 2f, 130, 7.5f, 0xFFA0A0A0, true, false);
    }
}
