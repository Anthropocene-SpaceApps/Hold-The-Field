package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.I18n;
import org.anthropocene.htf.game.ReportExporter;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.Event;
import org.anthropocene.htf.sim.GameState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.anthropocene.htf.ui.Theme.*;

/**
 * The debrief. Your result against the farmer who got no warning, how early NASA's data spoke, and what every
 * variety and planting date would have yielded on the same real weather.
 */
public final class EndScreen extends Screen {
    private final Session session;
    private final GameState other;
    private Chart chart;
    private List<Engine.Plan> noWarning, withScout;
    private final List<String> insights = new ArrayList<>();

    public EndScreen(Session session, GameState other) { this.session = session; this.other = other; }

    @Override
    protected void init() {
        layoutCard(Math.min(w - 40, 1160), Math.min(h - 40, 680));
        GameState st = session.state;
        Config cfg = session.cfg;
        int n = session.season.length();
        double[] level = new double[n], bund = new double[n];
        String[] dates = new String[n];
        for (int i = 0; i < n; i++) {
            dates[i] = Hud.date(session.season.day(i).date());
            int k = Math.max(0, Math.min(st.history.size() - 1, i - st.startIndex));
            level[i] = i < st.startIndex ? 0 : st.history.get(k).level();
            bund[i] = st.history.get(k).bund();
        }
        boolean dry = cfg.isDrought();
        chart = new Chart(dry ? "Soil wetness vs the crop's stress line" : "Floodwater vs your embankment (m)");
        chart.days = n; chart.visibleUntil = st.i; chart.cursorDay = st.i; chart.dates = dates; chart.decimals = 2; chart.firstDay = st.startIndex;
        if (dry) chart.yMax = 1;
        if (dry) chart.areaOf("Soil + irrigation", level, WATER, "").stepped("Stress line", bund, SOIL, "");
        else chart.areaOf("Water", level, WATER, "m").stepped("Embankment", bund, SOIL, "m");
        for (Event e : st.events) {
            switch (e.type()) {
                case "warning" -> chart.vline(e.i(), BAD, "warning");
                case "flood" -> chart.vline(e.i(), WATER, "flood");
                case "stress" -> chart.vline(e.i(), TEMP, "stress");
                case "harvest" -> chart.vline(e.i(), GOOD, "harvest");
                case "loss" -> chart.vline(e.i(), 0xFFCCCCCC, "loss");
                default -> { }
            }
        }
        noWarning = Engine.planningWindow(session.season, cfg, "rahim");
        withScout = Engine.planningWindow(session.season, cfg, "scout");
        buildInsights();

        int by = cardY + cardH - 62, bw = (cardW - 64 - 4 * 10) / 5;
        int x = cardX + 32;
        button("Play again", x, by, bw, () -> game.startSession(new Session(session.season, session.mode, session.variety, session.name + " (again)", st.transplant))).primary();
        button("Try another plan", x + (bw + 10), by, bw, () -> open(new PlanSeasonScreen()));
        button("Save report", x + (bw + 10) * 2, by, bw, this::exportReport);
        button("Look around", x + (bw + 10) * 3, by, bw, () -> game.setScreen(null));
        button("Title screen", x + (bw + 10) * 4, by, bw, game::quitToTitle);
    }

    private void buildInsights() {
        insights.clear();
        Config cfg = session.cfg;
        boolean dry = cfg.isDrought();
        Engine.Summary sum = Engine.summarize(session.state);
        Engine.Plan bestNo = noWarning.stream().max(java.util.Comparator.comparingDouble(Engine.Plan::yieldPct)).orElse(null);
        Engine.Plan bestScout = withScout.stream().max(java.util.Comparator.comparingDouble(Engine.Plan::yieldPct)).orElse(null);
        if (sum.leadDays() != null) insights.add(I18n.f(dry ? "NASA rain and soil data gave a warning {} day(s) before the crop felt the dry soil."
                : "NASA rain data in the hills gave a warning {} day(s) before the water reached the embankment.", sum.leadDays()));
        else if (sum.floodDate() != null) insights.add(dry ? "The dry spell hit without a scout warning ahead of it." : "The flood arrived without a scout warning ahead of it.");
        else insights.add(dry ? "The crop never suffered from dry soil on this plan." : "No flood reached the embankment on this plan.");
        if (bestNo != null) insights.add(I18n.f("With no warning at all, the best plan keeps {}%: {}.", Math.round(bestNo.yieldPct() * 100), planLabel(bestNo)));
        if (bestScout != null) insights.add(I18n.f("Acting on the scout, the best plan keeps {}%: {}.", Math.round(bestScout.yieldPct() * 100), planLabel(bestScout)));
        if (dry && bestNo != null && bestScout != null && bestNo.variety().equals("short") && bestScout.variety().equals("long"))
            insights.add("Without a warning the drought-tolerant variety is the safer choice; with the scout and well-timed irrigation the standard variety wins.");
        else if (!dry && bestNo != null && bestNo.variety().equals("short") && !bestNo.transplant().equals(cfg.transplantOptions[cfg.transplantOptions.length - 1][0]))
            insights.add("Ripening earlier let the rice come in before the spring water: timing is itself an adaptation.");
        else if (bestNo != null && bestNo.yieldPct() < 0.5)
            insights.add("Without a warning, every plan here lost most of the crop: early warning, not the calendar, is what protects the harvest.");
    }

    private String planLabel(Engine.Plan p) { return I18n.t(session.cfg.varieties.get(p.variety()).label()) + ", " + I18n.t(p.label()); }

    private void exportReport() {
        try {
            Path p = ReportExporter.export(session, other, noWarning, withScout);
            game.toastMsg(I18n.f("Report saved: {}", p));
            game.say("System", I18n.f("Report saved to {}", p), 0xFFB6D8FF);
        } catch (IOException e) {
            game.toastMsg(I18n.f("Could not save the report: {}", e.getMessage()));
        }
    }

    @Override protected void onEscape() { game.setScreen(null); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        GameState st = session.state;
        boolean rahim = session.mode.equals(Session.MODE_RAHIM);
        boolean dry = session.cfg.isDrought();
        r.text(st.yieldPct > 0 ? "The rice is home" : dry ? "The drought took it all" : "The water took it all", cardX + 32, cardY + 22, 30, TEXT, false, true);
        r.text(dry ? "Same field, same real weather. The difference is whether the warning reached the farmer, and how the season was planned."
                : "Same field, same real rain. The difference is whether the warning reached the farmer, and how the season was planned.", cardX + 32, cardY + 62, 13, MUTED);

        int x0 = cardX + 32, top = cardY + 100;
        int c1 = 300, c3 = 330, gap = 22, c2 = cardW - 64 - c1 - c3 - gap * 2;
        // --- results
        result(r, x0, top, c1, rahim ? "Rahim's way (no scout)" : "You, with the scout", st.yieldPct, CROP);
        result(r, x0, top + 118, c1, rahim ? "With the scout" : "Rahim's way (no scout)", other.yieldPct, 0xFF8AB4F0);
        float y = top + 246;
        Theme.label(r, "What the data showed", x0, y);
        y += 18;
        for (String line : insights) for (String l : r.wrap(line, c1, 12.5f)) { r.text(l, x0, y, 12.5f, 0xFFD3DEEC); y += 17; if (y > cardY + cardH - 80) break; }

        // --- chart + timeline
        int cx = x0 + c1 + gap;
        chart.draw(r, cx, top, c2, 230, mx, my);
        float ty = top + 246;
        Theme.label(r, "Timeline", cx, ty);
        ty += 18;
        List<Event> events = st.events.stream().filter(e -> !e.type().equals("watch")).toList();
        for (int i = Math.max(0, events.size() - 6); i < events.size() && ty < cardY + cardH - 80; i++) {
            Event e = events.get(i);
            int col = switch (e.type()) { case "warning", "loss" -> BAD; case "flood" -> WATER; case "stress" -> TEMP; case "harvest" -> GOOD; default -> TEXT; };
            r.circle(cx + 4, ty + 7, 3.5f, col);
            r.text(Hud.date(e.date()) + "   " + e.text(), cx + 16, ty, 12.5f, 0xFFD3DEEC);
            ty += 19;
        }

        // --- planting window grids
        int gx = cx + c2 + gap;
        Theme.label(r, "Every plan, same real weather", gx, top - 2);
        grid(r, gx, top + 18, c3, "No warning, no action", noWarning);
        grid(r, gx, top + 18 + 166, c3, "Acting on the scout", withScout);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }

    private void result(Renderer2D r, int x, int y, int w, String label, double yield, int color) {
        r.roundRect(x, y, w, 106, 13, 0x16FFFFFF);
        r.roundRing(x, y, w, 106, 13, 1f, BORDER);
        r.text(label, x + 16, y + 12, 12.5f, MUTED, false, true);
        r.text(I18n.f("{}%", Math.round(yield * 100)), x + 16, y + 30, 46, color, false, true);
        r.text("of the harvest saved", x + 16, y + 84, 12, FAINT);
        r.roundRect(x + w - 100, y + 76, 84, 8, 4, 0x22FFFFFF);
        r.roundRect(x + w - 100, y + 76, 84 * (float) Math.min(1, yield), 8, 4, color);
    }

    private void grid(Renderer2D r, int x, int y, int w, String title, List<Engine.Plan> plans) {
        r.text(title, x, y, 13, TEXT, false, true);
        Config cfg = session.cfg;
        int cols = cfg.transplantOptions.length, cw = (w - 78) / cols, ch = 44;
        for (int c = 0; c < cols; c++) r.textCentered(cfg.transplantOptions[c][1].replaceAll(" \\(.*", ""), x + 78 + c * cw + cw / 2f, y + 22, 11, MUTED, false, false);
        String[] vs = {"short", "long"};
        for (int ri = 0; ri < 2; ri++) {
            boolean dryCfg = cfg.isDrought();
            r.text(vs[ri].equals("short") ? (dryCfg ? "Tolerant rice" : "Short rice") : (dryCfg ? "Standard rice" : "Long rice"), x, y + 46 + ri * (ch + 6) + 14, 12, MUTED);
            for (int c = 0; c < cols; c++) {
                Engine.Plan p = find(plans, vs[ri], cfg.transplantOptions[c][0]);
                float cx = x + 78 + c * cw + 3, cy = y + 40 + ri * (ch + 6);
                if (p == null) { r.roundRect(cx, cy, cw - 6, ch, 9, 0x10FFFFFF); continue; }
                float f = (float) Math.min(1, p.yieldPct());
                int col = Renderer2D.lerp(0xFFB5453A, 0xFF4FBF7E, f);
                r.roundRect(cx, cy, cw - 6, ch, 9, Renderer2D.withAlpha(col, 0.78f));
                r.textCentered(I18n.f("{}%", Math.round(p.yieldPct() * 100)), cx + (cw - 6) / 2f, cy + 13, 17, 0xFFFFFFFF, false, true);
                boolean mine = p.variety().equals(session.variety) && p.transplant().equals(session.state.transplant);
                if (mine) r.roundRing(cx - 1, cy - 1, cw - 4, ch + 2, 10, 2.2f, 0xFFFFFFFF);
            }
        }
        r.text("White outline = your plan", x, y + 150, 10.5f, FAINT);
    }

    private Engine.Plan find(List<Engine.Plan> plans, String variety, String transplant) {
        for (Engine.Plan p : plans) if (p.variety().equals(variety) && p.transplant().equals(transplant)) return p;
        return null;
    }
}
