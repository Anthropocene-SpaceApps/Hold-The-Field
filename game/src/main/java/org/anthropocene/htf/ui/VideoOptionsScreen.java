package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

import java.util.List;

public final class VideoOptionsScreen extends Screen {
    @Override
    protected void init() {
        Settings s = game.settings();
        int l = w / 2 - 155, rgt = w / 2 + 5, y = 32, dy = 24;
        add(new Widget.Slider("Render Distance", (s.renderDistance - 4) / 12.0, v -> (4 + (int) Math.round(v * 12)) + " chunks", v -> s.renderDistance = 4 + (int) Math.round(v * 12))).bounds(l, y, 150, 20);
        add(new Widget.Slider("Max Framerate", (s.maxFps - 30) / 230.0, v -> { int f = 30 + (int) Math.round(v * 23) * 10; return f >= 260 ? "Unlimited" : f + " fps"; },
                v -> s.maxFps = Math.min(260, 30 + (int) Math.round(v * 23) * 10))).bounds(rgt, y, 150, 20);
        add(Widget.toggle("VSync", s.vsync, v -> { s.vsync = v; game.applySettings(); })).bounds(l, y + dy, 150, 20);
        add(Widget.toggle("Fullscreen", game.window().isFullscreen(), v -> { s.fullscreen = v; game.applySettings(); })).bounds(rgt, y + dy, 150, 20);
        add(new Widget.Cycle<>("GUI Scale", List.of(0, 1, 2, 3, 4, 5), s.guiScale, v -> v == 0 ? "Auto" : String.valueOf(v), v -> { s.guiScale = v; game.applySettings(); })).bounds(l, y + dy * 2, 150, 20);
        add(new Widget.Slider("Brightness", s.brightness, v -> v < 0.05 ? "Moody" : v > 0.95 ? "Bright" : Math.round(v * 100) + "%", v -> s.brightness = v)).bounds(rgt, y + dy * 2, 150, 20);
        add(Widget.toggle("Clouds", s.clouds, v -> s.clouds = v)).bounds(l, y + dy * 3, 150, 20);
        add(Widget.toggle("Particles", s.particles, v -> s.particles = v)).bounds(rgt, y + dy * 3, 150, 20);
        add(Widget.toggle("View Bobbing", s.viewBobbing, v -> s.viewBobbing = v)).bounds(l, y + dy * 4, 150, 20);
        add(new Widget.Slider("FOV", (s.fov - 40) / 70.0, v -> String.valueOf(40 + (int) Math.round(v * 70)), v -> s.fov = 40 + (int) Math.round(v * 70))).bounds(rgt, y + dy * 4, 150, 20);
        button("Done", w / 2 - 100, h - 32, 200, this::onEscape);
    }

    @Override protected void onEscape() { game.applySettings(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Video Settings", 11);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
