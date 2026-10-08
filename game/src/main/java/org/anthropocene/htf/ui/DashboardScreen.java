package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.GameState;
import org.anthropocene.htf.sim.Season;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/** Satellite Scout dashboard: interactive charts of everything NASA measured so far. */
public final class DashboardScreen extends Screen {
    private final Session session;
    private final List<Chart> charts = new ArrayList<>();

    public DashboardScreen(Session session) { this.session = session; }

    @Override
    protected void init() {
        charts.clear();
        Season s = session.season;
        GameState st = session.state;
        Config cfg = session.cfg;
        int n = s.length();
        double[] up = new double[n], farm = new double[n], sum3 = new double[n], tmax = new double[n], soil = new double[n];
        double[] matLong = new double[n], matShort = new double[n];
        String[] dates = new String[n];
        for (int i = 0; i < n; i++) {
            up[i] = s.day(i).rainUp(); farm[i] = s.day(i).rainFarm();
            sum3[i] = Engine.threeDayUpstream(s.days, i);
            tmax[i] = s.day(i).tmax(); soil[i] = s.day(i).soil();
            matLong[i] = Engine.maturityOn(s.day(i).date(), "long", cfg) * 100;
            matShort[i] = Engine.maturityOn(s.day(i).date(), "short", cfg) * 100;
            dates[i] = Hud.date(s.day(i).date());
        }
        double[] level = new double[st.history.size()], bund = new double[st.history.size()];
        for (int i = 0; i < level.length; i++) { level[i] = st.history.get(i).level(); bund[i] = st.history.get(i).bund(); }

        charts.add(base(new Chart("Rain: hills upstream vs farm (mm/day)"), n, st, dates)
                .areaOf("Upstream", up, 0xFF4F9BE0, "mm").line("Farm", farm, 0xFF7CE07C, "mm"));
        charts.add(base(new Chart("Scout index: 3-day upstream rain (mm)"), n, st, dates)
                .line("3-day sum", sum3, 0xFFE0E0E0, "mm").hline(cfg.watchMm, 0xFFE0A030, "WATCH").hline(cfg.warningMm, 0xFFE04040, "WARNING"));
        Chart water = base(new Chart("Floodwater vs your bund (m)"), n, st, dates);
        water.decimals = 2;
        water.areaOf("Water", level, 0xFF4F9BE0, "m").stepped("Bund", bund, 0xFFC08040, "m");
        charts.add(water);
        charts.add(base(new Chart("Maximum temperature (C)"), n, st, dates).line("Tmax", tmax, 0xFFE08040, "C"));
        Chart sc = base(new Chart("Root-zone soil wetness (0-1)"), n, st, dates);
        sc.decimals = 2; sc.yMax = 1;
        sc.areaOf("Soil wetness", soil, 0xFF8A6A3A, "");
        charts.add(sc);
        Chart mat = base(new Chart("Rice maturity (%), harvest from 80%"), n, st, dates);
        mat.yMax = 100;
        mat.line("Long-duration", matLong, session.variety.equals("long") ? 0xFFE0C040 : 0xFF807040, "%")
                .line("Short-duration", matShort, session.variety.equals("short") ? 0xFF7CE07C : 0xFF487048, "%")
                .hline(80, 0xFFFFFFFF, "80%");
        charts.add(mat);

        button("Done", w / 2 - 50, h - 24, 100, this::onEscape);
    }

    private Chart base(Chart c, int n, GameState st, String[] dates) {
        c.days = n; c.visibleUntil = st.i; c.cursorDay = st.i; c.dates = dates;
        return c;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (key == game.settings().key(org.anthropocene.htf.core.KeyAction.DASHBOARD)) { onEscape(); return true; }
        return super.keyDown(key, mods);
    }

    @Override protected void onEscape() { game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        int pw = Math.min(w - 16, 700), ph = h - 56;
        int px = w / 2 - pw / 2, py = 28;
        r.rect(px - 3, py - 3, pw + 6, ph + 6, 0xFF000000);
        r.rect(px - 2, py - 2, pw + 4, ph + 4, 0xFF505050);
        r.rect(px, py, pw, ph, 0xF0202428);
        title(r, "Satellite Scout Dashboard", 8);
        String sub = Hud.date(session.state.date) + "  |  day " + (session.state.i + 1) + " of " + session.season.length()
                + "  |  hover a chart to read values";
        r.textCentered(sub, w / 2f, 22, 7.5f, 0xFFB0B0B0, true, false);

        boolean wide = pw >= 560;
        int cols = wide ? 3 : 2, rows = (charts.size() + cols - 1) / cols;
        int gap = 6, cw = (pw - gap * (cols + 1)) / cols, ch = (ph - gap * (rows + 1)) / rows;
        for (int i = 0; i < charts.size(); i++) {
            int cx = px + gap + (i % cols) * (cw + gap), cy = py + gap + (i / cols) * (ch + gap);
            charts.get(i).draw(r, cx, cy, cw, ch, mx, my);
        }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
