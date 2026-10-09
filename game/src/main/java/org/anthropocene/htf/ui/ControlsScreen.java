package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

public final class ControlsScreen extends Screen {
    @Override
    protected void init() {
        layoutCard(560, 430);
        Settings s = game.settings();
        int x = cardX + 32, rw = cardW - 64, y = cardY + 92;
        add(new Widget.Slider("Mouse sensitivity", s.mouseSensitivity, v -> Math.round(v * 200) + "%", v -> s.mouseSensitivity = v)).bounds(x, y, rw, 44);
        add(Widget.toggle("Invert vertical look", s.invertY, v -> s.invertY = v)).bounds(x, y + 52, rw, 44);
        button("Key binds", x, y + 104, rw, () -> open(new KeyBindsScreen()));
        button("Done", x, cardY + cardH - 66, rw, this::onEscape).primary();
    }

    @Override protected void onEscape() { game.settings().save(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        heading(r, "Controls");
        for (Widget wd : widgets) wd.render(r, mx, my);
        r.text("Hotbar: 1-9 or mouse wheel   |   Esc: menu   |   Double-tap Jump: fly", cardX + 32, cardY + 304, 12, Theme.FAINT);
    }
}
