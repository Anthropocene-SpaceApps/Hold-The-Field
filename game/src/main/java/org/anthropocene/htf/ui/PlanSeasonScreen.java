package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.I18n;
import org.anthropocene.htf.game.SaveManager;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Season;
import org.anthropocene.htf.sim.SeasonCatalog;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.anthropocene.htf.ui.Theme.*;

/**
 * Plan the season: how you farm is the first adaptation. The scenario, the variety (duration, yield, tolerance) and
 * the transplanting date decide whether the rice is ready before the hazard arrives, or tough enough to ride it out.
 */
public final class PlanSeasonScreen extends Screen {
    private Widget.TextField name;
    private final List<Widget.Choice> scenarioCards = new ArrayList<>(), varietyCards = new ArrayList<>(), dateCards = new ArrayList<>(), modeCards = new ArrayList<>();
    private String variety = "short", mode = Session.MODE_SCOUT;
    private int dateIdx = 1, scenario = 0;
    private Config cfg = Config.forHazard(SeasonCatalog.all().get(0).hazard());
    private static String keptName = "";
    private boolean rebuild;                 // the widgets change with the scenario; rebuilt between events, never inside one

    private boolean drought() { return cfg.isDrought(); }

    @Override
    protected void init() {
        layoutCard(1000, 700);
        scenarioCards.clear(); varietyCards.clear(); dateCards.clear(); modeCards.clear();
        int x0 = cardX + 32, col = (cardW - 64 - 32) / 3;
        int rowA = cardY + 98, rowB = cardY + 178, rowC = cardY + 262;

        // scenario
        List<SeasonCatalog.Entry> seasons = SeasonCatalog.all();
        for (int i = 0; i < seasons.size() && i < 2; i++) {
            final int idx = i;
            final SeasonCatalog.Entry e = seasons.get(i);
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(e.name(), x + 16, y + 9, 15, TEXT, false, true);
                r.text(e.tag(), x + 16, y + 32, 12, sel ? 0xFFB6D8FF : MUTED);
            }, () -> { scenario = idx; cfg = Config.forHazard(e.hazard()); dateIdx = 1; rebuild = true; }));
            c.bounds(x0 + (col + 16) * i, rowA, col, 54);
            c.selected = i == scenario;
            scenarioCards.add(c);
        }
        String keep = name != null ? name.text.toString() : keptName;
        name = add(new Widget.TextField(keep.isEmpty() ? defaultName() : keep));
        name.bounds(x0 + (col + 16) * 2, rowA, col, 54);
        name.focused = false;
        name.placeholder = "Farm name";

        // mode
        String[][] modes = {{Session.MODE_SCOUT, "Scout mode", "You farm with the satellite scout"}, {Session.MODE_RAHIM, "Rahim's way", "Watch a farmer with no warning"}};
        for (int i = 0; i < 2; i++) {
            final String[] m = modes[i];
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(m[1], x + 16, y + 9, 15, TEXT, false, true);
                r.text(m[2], x + 16, y + 31, 12, MUTED);
            }, () -> { mode = m[0]; refresh(); }));
            c.bounds(x0 + (col + 16) * i, rowB, col, 54);
            modeCards.add(c);
        }
        // variety cards
        String[] keys = {"short", "long"};
        for (int i = 0; i < 2; i++) {
            final String k = keys[i];
            Config.Variety v = cfg.varieties.get(k);
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(v.label(), x + 18, y + 14, 17, TEXT, false, true);
                r.text(v.note(), x + 18, y + 38, 12, MUTED);
                Theme.label(r, "Days in the field", x + 18, y + 66);
                r.text(String.valueOf(v.fieldDays()), x + 18, y + 80, 24, sel ? ACCENT : TEXT, false, true);
                Theme.label(r, "Yield potential", x + 18, y + 114);
                r.roundRect(x + 18, y + 132, w - 36, 8, 4, 0x22FFFFFF);
                r.roundRect(x + 18, y + 132, (w - 36) * (float) v.potential(), 8, 4, CROP);
                r.text(I18n.f("{}% of full yield", Math.round(v.potential() * 100)), x + 18, y + 146, 12, MUTED);
            }, () -> { variety = k; refresh(); }));
            c.bounds(x0 + i * (col + 16), rowC + 20, col, 168);
            varietyCards.add(c);
        }
        // transplant dates
        for (int i = 0; i < cfg.transplantOptions.length; i++) {
            final int idx = i;
            final String[] o = cfg.transplantOptions[i];
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(o[1], x + 16, y + 9, 15, TEXT, false, true);
                r.text(ripeText(idx), x + 16, y + 31, 12, sel ? 0xFFB6D8FF : MUTED);
            }, () -> { dateIdx = idx; refresh(); }));
            c.bounds(x0 + col * 2 + 32, rowB + i * 58, col, 54);
            dateCards.add(c);
        }
        button("Back", x0, cardY + cardH - 62, 150, () -> game.setScreen(parent));
        button("Start Season", cardX + cardW - 32 - 240, cardY + cardH - 62, 240, this::create).primary();
        refresh();
    }

    private String ripeText(int idx) {
        Config.Variety v = cfg.varieties.get(variety);
        LocalDate ripe = LocalDate.parse(cfg.transplantOptions[idx][0]).plusDays(v.fieldDays());
        return I18n.f("ripe about {}", Hud.date(ripe.toString()));
    }

    private void refresh() {
        boolean scout = mode.equals(Session.MODE_SCOUT);
        for (int i = 0; i < scenarioCards.size(); i++) scenarioCards.get(i).selected = i == scenario;
        for (int i = 0; i < varietyCards.size(); i++) { varietyCards.get(i).selected = (i == 0 ? "short" : "long").equals(variety) && scout; varietyCards.get(i).enabled = scout; }
        for (int i = 0; i < dateCards.size(); i++) { dateCards.get(i).selected = i == dateIdx && scout; dateCards.get(i).enabled = scout; }
        for (int i = 0; i < modeCards.size(); i++) modeCards.get(i).selected = (i == 0) == scout;
    }

    private String defaultName() { return I18n.f("Rahim's Farm {}", SaveManager.list().size() + 1); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        if (rebuild) { rebuild = false; widgets.clear(); init(); }
        background(r);
        drawCard(r);
        Theme.heading(r, "Plan the Season", cardX + 32, cardY + 26);
        int x0 = cardX + 32, col = (cardW - 64 - 32) / 3;
        int rowA = cardY + 98, rowB = cardY + 178, rowC = cardY + 262;
        Theme.label(r, "Scenario", x0, rowA - 16);
        Theme.label(r, "Farm name", x0 + (col + 16) * 2, rowA - 16);
        Theme.label(r, "How you play", x0, rowB - 16);
        Theme.label(r, "Transplanting date", x0 + col * 2 + 32, rowB - 16);
        Theme.label(r, "Rice variety", x0, rowC);
        for (Widget wd : widgets) wd.render(r, mx, my);

        // advice panel
        int ay = rowC + 20 + 168 + 14;
        r.roundRect(x0, ay, cardW - 64, 120, 13, 0x16FFFFFF);
        boolean scout = mode.equals(Session.MODE_SCOUT);
        Config.Variety v = cfg.varieties.get(scout ? variety : "long");
        String plant = scout ? cfg.transplantOptions[dateIdx][0] : cfg.defaultTransplant.toString();
        LocalDate ripe = LocalDate.parse(plant).plusDays(v.fieldDays());
        r.text(scout ? "Your plan" : "Rahim farms the way his father did", x0 + 20, ay + 12, 15, TEXT, false, true);
        String plan = scout
                ? I18n.f(drought()
                        ? "{}, transplanted {}, ripe about {}. Irrigate when the scout warns: the soil must stay wet while the rice flowers."
                        : "{}, transplanted {}, ripe about {}. You can harvest from 80% maturity, so a warning can still save part of the crop.",
                        v.label(), Hud.date(plant), Hud.date(ripe.toString()))
                : drought() ? "Standard rice transplanted on the usual date, no satellite warning, no irrigation. You watch the real weather decide."
                : "Long-duration rice transplanted on the usual date, no satellite warning, no actions. You watch the real rain decide.";
        float y = ay + 36;
        for (String line : r.wrap(plan, cardW - 104, 13)) { r.text(line, x0 + 20, y, 13, MUTED); y += 18; }
        y += 8;
        r.text("Why it matters", x0 + 20, y, 12, ACCENT, false, true);
        String why = drought()
                ? "In the Barind Tract the monsoon can fail in the weeks when rice flowers. A tolerant variety copes with dry soil but yields less; the NASA soil and rain data tell you when to irrigate."
                : "In flood-prone haors the rain that arrives in spring comes from the hills upstream. Rice that ripens earlier can be harvested before the water arrives; rice that ripens later can only be saved by watching the data.";
        for (String line : r.wrap(why, cardW - 104, 12)) r.text(line, x0 + 20, y += 16, 12, FAINT);
    }

    private void create() {
        String n = name.text.toString().trim();
        if (n.isEmpty()) n = defaultName();
        try {
            Season s = SeasonCatalog.load(SeasonCatalog.all().get(scenario).id());
            boolean scout = mode.equals(Session.MODE_SCOUT);
            String plant = scout ? cfg.transplantOptions[dateIdx][0] : cfg.defaultTransplant.toString();
            game.startSession(new Session(s, mode, scout ? variety : "long", n, plant));
        } catch (IOException | RuntimeException e) {
            game.toastMsg(I18n.f("Could not create farm: {}", e.getMessage()));
            System.err.println("Could not create farm: " + e);
        }
    }

    @Override public void onClose() { keptName = ""; }
}
