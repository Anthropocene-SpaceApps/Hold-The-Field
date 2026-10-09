package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import static org.anthropocene.htf.ui.Theme.*;

/** Home menu over the cinematic flyover of the flooded haor. */
public final class TitleScreen extends Screen {
    @Override public boolean transparentBackground() { return true; }

    @Override
    protected void init() {
        int lx = Math.max(56, (int) (w * 0.065)), bw = 320, y = (int) (h * 0.52f);
        button("Play", lx, y, bw, () -> open(new WorldSelectScreen())).primary().h = 48;
        button("Data & Model", lx, y + 60, bw, () -> open(new AboutScreen()));
        button("Advancements", lx, y + 108, bw, () -> open(new AdvancementsScreen()));
        button("Options", lx, y + 156, 154, () -> open(new OptionsScreen()));
        button("Quit", lx + 166, y + 156, 154, game::quit);
    }

    @Override
    protected void background(Renderer2D r) {
        r.gradientH(0, 0, w * 0.62f, h, 0xDD04090F, 0x0004090F);
        r.gradientV(0, h * 0.72f, w, h * 0.28f, 0x0004090F, 0xAA04090F);
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        int lx = Math.max(56, (int) (w * 0.065));
        float y = h * 0.16f;
        r.text("NASA SPACE APPS CHALLENGE 2026  /  FIELD SHIFT", lx, y, 12, ACCENT, false, true);
        r.text("Agro", lx, y + 24, 54, TEXT, true, true);
        r.text("cene", lx + r.textWidth("Agro", 54, true), y + 24, 54, CROP, true, true);
        r.roundRect(lx, y + 92, 56, 4, 2, ACCENT);
        r.text("The climate raids your farm. NASA is your scout.", lx, y + 110, 17, TEXT, false, true);
        float ty = y + 140;
        for (String line : r.wrap("A farming game where the climate raids your fields and NASA satellite data is your scout. Replay a real flash flood or drought, day by day.", 400, 15)) {
            r.text(line, lx, ty, 15, MUTED);
            ty += 22;
        }
        for (Widget wd : widgets) wd.render(r, mx, my);

        r.text("Agrocene 1.0.0   |   Team Anthropocene", lx, h - 34, 12, FAINT);
        boolean sample = game.titleSeason() != null && game.titleSeason().sample;
        String badge = sample ? "SAMPLE DATA  -  open the launcher to download real NASA data" : "NASA POWER daily data";
        float bw = r.textWidth(badge, 12, true) + 28;
        Theme.chip(r, w - bw - 32, h - 46, bw, 26, sample ? 0x55FF5D5D : 0x554DA3FF);
        r.text(badge, w - bw - 18, h - 40, 12, sample ? 0xFFFF9C9C : 0xFFB6D8FF, false, true);
    }

    @Override protected void onEscape() {}
}
