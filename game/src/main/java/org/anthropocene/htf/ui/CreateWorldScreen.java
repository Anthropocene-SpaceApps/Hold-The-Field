package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.SaveManager;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Season;
import org.anthropocene.htf.sim.SeasonCatalog;

import java.io.IOException;
import java.util.List;

/** New world: name, season, mode and rice variety. */
public final class CreateWorldScreen extends Screen {
    private Widget.TextField name;
    private Widget.Cycle<SeasonCatalog.Entry> seasonCycle;
    private Widget.Cycle<String> modeCycle, riceCycle;
    private static String lastName = "";

    @Override
    protected void init() {
        String keep = name != null ? name.text.toString() : lastName;
        int cx = w / 2 - 100;
        name = add(new Widget.TextField(keep.isEmpty() ? defaultName() : keep));
        name.bounds(cx, 50, 200, 20);
        name.focused = true;
        name.placeholder = "World Name";
        Config cfg = Config.DEFAULT;
        seasonCycle = add(new Widget.Cycle<>("Season", SeasonCatalog.all(), 0, SeasonCatalog.Entry::name, e -> {}));
        seasonCycle.bounds(cx, 84, 200, 20);
        modeCycle = add(new Widget.Cycle<>("Mode", List.of(Session.MODE_SCOUT, Session.MODE_RAHIM), 0,
                m -> m.equals(Session.MODE_SCOUT) ? "Scout (you play)" : "Rahim's way (watch)", m -> updateRice()));
        modeCycle.bounds(cx, 108, 200, 20);
        riceCycle = add(new Widget.Cycle<>("Rice", List.of("long", "short"), 0,
                v -> cfg.varieties.get(v).label(), v -> {}));
        riceCycle.bounds(cx, 132, 200, 20);
        updateRice();
        button("Create New World", cx, h - 52, 200, this::create);
        button("Cancel", cx, h - 28, 200, () -> game.setScreen(parent));
    }

    private void updateRice() { riceCycle.enabled = modeCycle.value().equals(Session.MODE_SCOUT); }

    private String defaultName() {
        int n = SaveManager.list().size() + 1;
        return "Rahim's Farm " + n;
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Create New World", 11);
        r.text("World Name", w / 2f - 100, 39, 8, 0xFFA0A0A0);
        for (Widget wd : widgets) wd.render(r, mx, my);
        String info = modeCycle.value().equals(Session.MODE_SCOUT)
                ? "You play the season day by day. Read the satellite data, raise the bund or harvest early. Short-duration rice ripens sooner but yields what it has grown."
                : "Rahim farms the way his father did: no satellite warning, no actions. You watch the real rain take his harvest, then compare.";
        int y = 160;
        for (String line : r.wrap(info, Math.min(320, w - 30), 8)) { r.textCentered(line, w / 2f, y, 8, 0xFFC8C8C8, true, false); y += 10; }
        y += 6;
        for (String line : r.wrap(seasonCycle.value().blurb(), Math.min(320, w - 30), 7.5f)) { r.textCentered(line, w / 2f, y, 7.5f, 0xFF909090, true, false); y += 9; }
    }

    private void create() {
        String n = name.text.toString().trim();
        if (n.isEmpty()) n = defaultName();
        lastName = "";
        try {
            Season s = SeasonCatalog.load(seasonCycle.value().id());
            game.startSession(new Session(s, modeCycle.value(), riceCycle.value(), n));
        } catch (IOException | RuntimeException e) {
            game.toastMsg("Could not create world: " + e.getMessage());
            System.err.println("Could not create world: " + e);
        }
    }

    @Override public void onClose() { lastName = ""; }
}
