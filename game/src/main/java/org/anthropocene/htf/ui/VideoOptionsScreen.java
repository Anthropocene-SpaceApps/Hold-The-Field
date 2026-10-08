package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.world.Atmosphere;

import java.util.List;

public final class VideoOptionsScreen extends Screen {
    @Override
    protected void init() {
        layoutCard(820, 640);
        Settings s = game.settings();
        int colW = (cardW - 64 - 16) / 2, l = cardX + 32, rg = l + colW + 16, y = cardY + 92, dy = 52;
        add(new Widget.Slider("Render distance", (s.renderDistance - 4) / 12.0, v -> (4 + (int) Math.round(v * 12)) + " chunks", v -> s.renderDistance = 4 + (int) Math.round(v * 12))).bounds(l, y, colW, 44);
        add(new Widget.Slider("Max framerate", (s.maxFps - 30) / 230.0, v -> { int f = 30 + (int) Math.round(v * 23) * 10; return f >= 260 ? "Unlimited" : f + " fps"; },
                v -> s.maxFps = Math.min(260, 30 + (int) Math.round(v * 23) * 10))).bounds(rg, y, colW, 44);
        add(Widget.toggle("VSync", s.vsync, v -> { s.vsync = v; game.applySettings(); })).bounds(l, y + dy, colW, 44);
        add(Widget.toggle("Fullscreen", game.window().isFullscreen(), v -> { s.fullscreen = v; game.applySettings(); })).bounds(rg, y + dy, colW, 44);
        add(new Widget.Cycle<>("Interface size", List.of(1, 2, 0, 3, 4, 5), 2, v -> new String[]{"Auto", "Compact", "Small", "Large", "Larger", "Largest"}[v], v -> { s.guiScale = v; game.applySettings(); })).bounds(l, y + dy * 2, colW, 44);
        add(new Widget.Slider("Brightness", s.brightness, v -> Math.round(v * 100) + "%", v -> s.brightness = v)).bounds(rg, y + dy * 2, colW, 44);
        add(new Widget.Cycle<>("Time of day", List.of(0, 1, 2, 3, 4), s.timeMode, v -> Atmosphere.TIME_MODES[v], v -> s.timeMode = v)).bounds(l, y + dy * 3, colW, 44);
        add(new Widget.Cycle<>("Anti-aliasing", List.of(0, 2, 4, 8), java.util.List.of(0, 2, 4, 8).indexOf(s.msaa), v -> v == 0 ? "Off" : v + "x MSAA", v -> s.msaa = v)).bounds(rg, y + dy * 3, colW, 44);
        add(Widget.toggle("Shadows", s.shadows, v -> s.shadows = v)).bounds(l, y + dy * 4, colW, 44);
        add(Widget.toggle("Bloom", s.bloom, v -> s.bloom = v)).bounds(rg, y + dy * 4, colW, 44);
        add(new Widget.Cycle<>("Vegetation", List.of(2, 1, 0), 2 - s.grass, v -> new String[]{"Sparse", "Medium", "Dense"}[v], v -> s.grass = v)).bounds(l, y + dy * 5, colW, 44);
        add(Widget.toggle("Particles", s.particles, v -> s.particles = v)).bounds(rg, y + dy * 5, colW, 44);
        add(Widget.toggle("Head bob", s.viewBobbing, v -> s.viewBobbing = v)).bounds(l, y + dy * 6, colW, 44);
        button("Done", cardX + 32, cardY + cardH - 66, cardW - 64, this::onEscape).primary();
    }

    @Override protected void onEscape() { game.applySettings(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        heading(r, "Video settings");
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
