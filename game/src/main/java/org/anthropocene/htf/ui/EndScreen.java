package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.Event;
import org.anthropocene.htf.sim.GameState;

import java.util.List;

/** Season report: your result against Rahim's way (or the scout), lead time, chart, timeline. */
public final class EndScreen extends Screen {
    private final Session session;
    private final GameState other;
    private Chart chart;

    public EndScreen(Session session, GameState other) { this.session = session; this.other = other; }

    @Override
    protected void init() {
        GameState st = session.state;
        int n = session.season.length();
        double[] level = new double[st.history.size()], bund = new double[st.history.size()];
        for (int i = 0; i < level.length; i++) { level[i] = st.history.get(i).level(); bund[i] = st.history.get(i).bund(); }
        String[] dates = new String[n];
        for (int i = 0; i < n; i++) dates[i] = Hud.date(session.season.day(i).date());
        chart = new Chart("Floodwater vs bund, whole run (m)");
        chart.days = n; chart.visibleUntil = st.i; chart.cursorDay = st.i; chart.dates = dates; chart.decimals = 2;
        chart.areaOf("Water", level, 0xFF4F9BE0, "m").stepped("Bund", bund, 0xFFC08040, "m");
        for (Event e : st.events) {
            switch (e.type()) {
                case "warning" -> chart.vline(e.i(), 0xFFFF6B6B, "warning");
                case "flood" -> chart.vline(e.i(), 0xFF6BB8FF, "flood");
                case "harvest" -> chart.vline(e.i(), 0xFF8CE06A, "harvest");
                case "loss" -> chart.vline(e.i(), 0xFFCCCCCC, "loss");
                default -> { }
            }
        }
        int bx = w / 2 - 154, by = h - 26;
        button("Play Again", bx, by, 100, () -> {
            Session fresh = new Session(session.season, session.mode, session.variety, session.name + " (again)");
            game.startSession(fresh);
        });
        button("Keep Looking Around", bx + 104, by, 100, () -> game.setScreen(null));
        button("Title Screen", bx + 208, by, 100, game::quitToTitle);
    }

    @Override protected void onEscape() { game.setScreen(null); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        GameState st = session.state;
        boolean rahim = session.mode.equals(Session.MODE_RAHIM);
        title(r, st.yieldPct > 0 ? "The rice is home" : "The water took it all", 8);

        int bw = Math.min(150, w / 2 - 16), y = 28;
        card(r, w / 2 - bw - 4, y, bw, rahim ? "Rahim's way (no scout)" : "You, with the scout", st.yieldPct, 0xFFE0C040);
        card(r, w / 2 + 4, y, bw, rahim ? "With the scout" : "Rahim's way (no scout)", other.yieldPct, 0xFF8AB4F0);

        Engine.Summary sum = Engine.summarize(st);
        String lead = sum.leadDays() == null
                ? (sum.floodDate() != null ? "The flood came, and no scout warning came before it." : "No flood reached the bund this season.")
                : "NASA data warned " + sum.leadDays() + " day" + (sum.leadDays() == 1 ? "" : "s") + " before the water arrived ("
                + Hud.date(sum.warningDate()) + " to " + Hud.date(sum.floodDate()) + ").";
        int cy = y + 52;
        for (String line : r.wrap(lead, Math.min(400, w - 20), 9)) { r.textCentered(line, w / 2f, cy, 9, 0xFFFFFFFF, true, true); cy += 11; }

        int chartH = Math.max(70, Math.min(110, h - cy - 96));
        int cw = Math.min(400, w - 20);
        chart.draw(r, w / 2 - cw / 2, cy + 4, cw, chartH, mx, my);

        // timeline
        int ty = cy + chartH + 10;
        List<Event> events = st.events.stream().filter(e -> !e.type().equals("watch")).toList();
        int shown = 0;
        for (int i = Math.max(0, events.size() - 5); i < events.size() && ty < h - 34; i++, shown++) {
            Event e = events.get(i);
            int col = switch (e.type()) { case "warning", "loss" -> 0xFFFF7777; case "flood" -> 0xFF77BBFF; case "harvest" -> 0xFF99E077; default -> 0xFFDDDDDD; };
            r.textCentered(Hud.date(e.date()) + ": " + e.text(), w / 2f, ty, 7.5f, col, true, false);
            ty += 9;
        }
        r.textCentered("Same field, same real rain. The only difference is whether the warning reached the farmer.", w / 2f, h - 38, 7.5f, 0xFFB0B0B0, true, false);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }

    private void card(Renderer2D r, int x, int y, int w, String label, double yield, int color) {
        r.rect(x, y, w, 46, 0xFF000000);
        r.rect(x + 1, y + 1, w - 2, 44, 0xE0202428);
        r.textCentered(label, x + w / 2f, y + 4, 7.5f, 0xFFB0B0B0, true, false);
        r.textCentered(Math.round(yield * 100) + "%", x + w / 2f, y + 14, 22, color, true, true);
        r.textCentered("of the harvest saved", x + w / 2f, y + 36, 7, 0xFFB0B0B0, true, false);
    }
}
