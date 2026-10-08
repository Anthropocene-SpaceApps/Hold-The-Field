package org.anthropocene.htf.ui;

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
 * Plan the season: how you farm is the first adaptation. Variety (duration vs yield) and transplanting date decide
 * whether the rice is ripe before the spring flash floods.
 */
public final class PlanSeasonScreen extends Screen {
    private Widget.TextField name;
    private final List<Widget.Choice> varietyCards = new ArrayList<>(), dateCards = new ArrayList<>(), modeCards = new ArrayList<>();
    private String variety = "short", mode = Session.MODE_SCOUT;
    private int dateIdx = 1;
    private final Config cfg = Config.DEFAULT;
    private static String keptName = "";

    @Override
    protected void init() {
        layoutCard(1000, 640);
        varietyCards.clear(); dateCards.clear(); modeCards.clear();
        int x0 = cardX + 32, col = (cardW - 64 - 32) / 3;

        String keep = name != null ? name.text.toString() : keptName;
        name = add(new Widget.TextField(keep.isEmpty() ? defaultName() : keep));
        name.bounds(x0, cardY + 108, col, 56);
        name.focused = false;
        name.placeholder = "Farm name";

        // mode
        String[][] modes = {{Session.MODE_SCOUT, "Scout mode", "You farm with the satellite scout"}, {Session.MODE_RAHIM, "Rahim's way", "Watch a farmer with no warning"}};
        for (int i = 0; i < 2; i++) {
            final String[] m = modes[i];
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(m[1], x + 16, y + 11, 15, TEXT, false, true);
                r.text(m[2], x + 16, y + 33, 12, MUTED);
            }, () -> { mode = m[0]; refresh(); }));
            c.bounds(x0 + (col + 16) * (i + 1), cardY + 108, col, 56);
            modeCards.add(c);
        }
        // variety cards
        String[] keys = {"short", "long"};
        for (int i = 0; i < 2; i++) {
            final String k = keys[i];
            Config.Variety v = cfg.varieties.get(k);
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(v.label(), x + 18, y + 16, 17, TEXT, false, true);
                r.text(v.note(), x + 18, y + 40, 12, MUTED);
                Theme.label(r, "Days in the field", x + 18, y + 72);
                r.text(String.valueOf(v.fieldDays()), x + 18, y + 88, 26, sel ? ACCENT : TEXT, false, true);
                Theme.label(r, "Yield potential", x + 18, y + 128);
                r.roundRect(x + 18, y + 148, w - 36, 8, 4, 0x22FFFFFF);
                r.roundRect(x + 18, y + 148, (w - 36) * (float) v.potential(), 8, 4, CROP);
                r.text(Math.round(v.potential() * 100) + "% of full yield", x + 18, y + 164, 12, MUTED);
            }, () -> { variety = k; refresh(); }));
            c.bounds(x0 + i * (col + 16), cardY + 204, col, 196);
            varietyCards.add(c);
        }
        // transplant dates
        for (int i = 0; i < cfg.transplantOptions.length; i++) {
            final int idx = i;
            final String[] o = cfg.transplantOptions[i];
            Widget.Choice c = add(new Widget.Choice((r, x, y, w, h, sel, hv) -> {
                r.text(o[1], x + 16, y + 11, 15, TEXT, false, true);
                String when = i18nRipe(idx);
                r.text(when, x + 16, y + 33, 12, sel ? 0xFFB6D8FF : MUTED);
            }, () -> { dateIdx = idx; refresh(); }));
            c.bounds(x0 + col * 2 + 32, cardY + 204 + i * 66, col, 58);
            dateCards.add(c);
        }
        button("Back", x0, cardY + cardH - 66, 150, () -> game.setScreen(parent));
        button("Start Season", cardX + cardW - 32 - 240, cardY + cardH - 66, 240, this::create).primary();
        refresh();
    }

    private String i18nRipe(int idx) {
        Config.Variety v = cfg.varieties.get(variety);
        LocalDate ripe = LocalDate.parse(cfg.transplantOptions[idx][0]).plusDays(v.fieldDays());
        return "ripe about " + Hud.date(ripe.toString());
    }

    private void refresh() {
        boolean scout = mode.equals(Session.MODE_SCOUT);
        for (int i = 0; i < varietyCards.size(); i++) { varietyCards.get(i).selected = (i == 0 ? "short" : "long").equals(variety) && scout; varietyCards.get(i).enabled = scout; }
        for (int i = 0; i < dateCards.size(); i++) { dateCards.get(i).selected = i == dateIdx && scout; dateCards.get(i).enabled = scout; }
        for (int i = 0; i < modeCards.size(); i++) modeCards.get(i).selected = modeCards.get(i) != null && (i == 0) == scout;
    }

    private String defaultName() { return "Rahim's Farm " + (SaveManager.list().size() + 1); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        Theme.heading(r, "Plan the Season", cardX + 32, cardY + 26);
        int x0 = cardX + 32, col = (cardW - 64 - 32) / 3;
        Theme.label(r, "Farm name", x0, cardY + 90);
        Theme.label(r, "How you play", x0 + col + 16, cardY + 90);
        Theme.label(r, "Rice variety", x0, cardY + 184);
        Theme.label(r, "Transplanting date", x0 + col * 2 + 32, cardY + 184);
        for (Widget wd : widgets) wd.render(r, mx, my);

        // advice panel
        int ay = cardY + 416;
        r.roundRect(x0, ay, cardW - 64, 128, 13, 0x16FFFFFF);
        boolean scout = mode.equals(Session.MODE_SCOUT);
        Config.Variety v = cfg.varieties.get(scout ? variety : "long");
        LocalDate ripe = LocalDate.parse(scout ? cfg.transplantOptions[dateIdx][0] : cfg.defaultTransplant.toString()).plusDays(v.fieldDays());
        r.text(scout ? "Your plan" : "Rahim farms the way his father did", x0 + 20, ay + 14, 15, TEXT, false, true);
        String plan = scout
                ? v.label() + ", transplanted " + Hud.date(cfg.transplantOptions[dateIdx][0]) + ", ripe about " + Hud.date(ripe.toString())
                + ". You can harvest from 80% maturity, so a warning can still save part of the crop."
                : "Long-duration rice transplanted on the usual date, no satellite warning, no actions. You watch the real rain decide.";
        float y = ay + 40;
        for (String line : r.wrap(plan, cardW - 104, 13)) { r.text(line, x0 + 20, y, 13, MUTED); y += 18; }
        y += 6;
        r.text("Why it matters", x0 + 20, y, 12, ACCENT, false, true);
        for (String line : r.wrap("In flood-prone haors the rain that arrives in spring comes from the hills upstream. Rice that ripens earlier can be harvested before the water arrives; rice that ripens later can only be saved by watching the data.", cardW - 104, 12))
            r.text(line, x0 + 20, y += 16, 12, FAINT);
    }

    private void create() {
        String n = name.text.toString().trim();
        if (n.isEmpty()) n = defaultName();
        try {
            Season s = SeasonCatalog.load(SeasonCatalog.all().get(0).id());
            boolean scout = mode.equals(Session.MODE_SCOUT);
            String plant = scout ? cfg.transplantOptions[dateIdx][0] : cfg.defaultTransplant.toString();
            game.startSession(new Session(s, mode, scout ? variety : "long", n, plant));
        } catch (IOException | RuntimeException e) {
            game.toastMsg("Could not create farm: " + e.getMessage());
            System.err.println("Could not create farm: " + e);
        }
    }

    @Override public void onClose() { keptName = ""; }
}
