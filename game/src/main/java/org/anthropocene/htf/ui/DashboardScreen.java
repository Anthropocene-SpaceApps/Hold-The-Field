package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.GameState;
import org.anthropocene.htf.sim.Season;

import java.util.ArrayList;
import java.util.List;

import static org.anthropocene.htf.ui.Theme.*;

/** NASA scout dashboard: interactive charts of everything measured so far. */
public final class DashboardScreen extends Screen {
    private final Session session;
    private final List<Chart> charts = new ArrayList<>();

    public DashboardScreen(Session session) { this.session = session; }

    @Override
    protected void init() {
        layoutCard(Math.min(w - 48, 1240), Math.min(h - 48, 680));
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
            matLong[i] = Engine.maturityOn(s.day(i).date(), "long", st.transplant, cfg) * 100;
            matShort[i] = Engine.maturityOn(s.day(i).date(), "short", st.transplant, cfg) * 100;
            dates[i] = Hud.date(s.day(i).date());
        }
        double[] level = new double[n], bund = new double[n];
        for (int i = 0; i < n; i++) {
            int k = Math.max(0, Math.min(st.history.size() - 1, i - st.startIndex));
            level[i] = i < st.startIndex ? 0 : st.history.get(k).level();
            bund[i] = st.history.get(k).bund();
        }

        charts.add(base(new Chart("Rain: hills upstream vs farm (mm/day)"), n, st, dates)
                .areaOf("Upstream", up, RAIN, "mm").line("Farm", farm, GOOD, "mm"));
        charts.add(base(new Chart("Scout index: 3-day upstream rain (mm)"), n, st, dates)
                .line("3-day sum", sum3, TEXT, "mm").hline(cfg.watchMm, WARN, "WATCH").hline(cfg.warningMm, BAD, "WARNING"));
        Chart water = base(new Chart("Floodwater vs your embankment (m)"), n, st, dates);
        water.decimals = 2;
        water.areaOf("Water", level, WATER, "m").stepped("Embankment", bund, SOIL, "m");
        charts.add(water);
        charts.add(base(new Chart("Maximum temperature (C)"), n, st, dates).line("Tmax", tmax, TEMP, "C"));
        Chart sc = base(new Chart("Root-zone soil wetness (0-1)"), n, st, dates);
        sc.decimals = 2; sc.yMax = 1;
        sc.areaOf("Soil wetness", soil, SOIL, "");
        charts.add(sc);
        Chart mat = base(new Chart("Rice maturity (%), harvest from 80%"), n, st, dates);
        mat.yMax = 100;
        mat.line("Long-duration", matLong, session.variety.equals("long") ? CROP : 0xFF8A7A4A, "%")
                .line("Short-duration", matShort, session.variety.equals("short") ? GOOD : 0xFF4A7A5A, "%")
                .hline(80, TEXT, "80%");
        charts.add(mat);

        button("Close", cardX + cardW - 32 - 140, cardY + 22, 140, this::onEscape).bounds(cardX + cardW - 32 - 140, cardY + 22, 140, 38);
    }

    private Chart base(Chart c, int n, GameState st, String[] dates) {
        c.days = n; c.visibleUntil = st.i; c.cursorDay = st.i; c.dates = dates; c.firstDay = st.startIndex;
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
        drawCard(r);
        Theme.heading(r, "NASA Satellite Scout", cardX + 32, cardY + 22);
        String sub = Hud.date(session.state.date) + "   |   day " + (session.state.i + 1) + " of " + session.season.length()
                + "   |   " + (session.season.sample ? "SAMPLE DATA" : "NASA POWER") + "   |   hover a chart for values";
        r.text(sub, cardX + 300, cardY + 34, 12, MUTED);
        int top = cardY + 84, gap = 14;
        boolean wide = cardW >= 980;
        int cols = wide ? 3 : 2, rows = (charts.size() + cols - 1) / cols;
        int cw = (cardW - 64 - gap * (cols - 1)) / cols, ch = (cardH - 84 - 28 - gap * (rows - 1)) / rows;
        for (int i = 0; i < charts.size(); i++) {
            int cx = cardX + 32 + (i % cols) * (cw + gap), cy = top + (i / cols) * (ch + gap);
            charts.get(i).draw(r, cx, cy, cw, ch, mx, my);
        }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
