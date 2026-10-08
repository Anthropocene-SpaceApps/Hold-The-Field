package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.SaveManager;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Season;
import org.anthropocene.htf.sim.SeasonCatalog;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Saved worlds list: play, create, delete. */
public final class WorldSelectScreen extends Screen {
    private final List<SaveManager.SaveData> saves = new ArrayList<>();
    private Widget.ListBox<SaveManager.SaveData> list;
    private Widget.Button play, delete;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    @Override
    protected void init() {
        saves.clear();
        saves.addAll(SaveManager.list());
        int lw = Math.min(340, w - 20);
        list = add(new Widget.ListBox<>(saves, this::drawRow));
        list.bounds(w / 2 - lw / 2, 32, lw, h - 32 - 58);
        list.rowH = 38;
        list.onSelect = i -> refresh();
        list.onDouble = i -> playSelected();
        int bx = w / 2 - 154, by = h - 52;
        play = button("Play Selected World", bx, by, 150, this::playSelected);
        button("Create New World", bx + 158, by, 150, () -> { Screen s = new CreateWorldScreen(); s.parent = this; game.setScreen(s); });
        delete = button("Delete", bx, by + 24, 98, this::askDelete);
        button("Back", bx + 210, by + 24, 98, () -> game.setScreen(parent));
        refresh();
    }

    private void refresh() {
        boolean has = list.selected >= 0 && list.selected < saves.size();
        play.enabled = has;
        delete.enabled = has;
    }

    private void drawRow(Renderer2D r, SaveManager.SaveData d, int x, int y, int w, int h, boolean sel, boolean hover) {
        String season = SeasonCatalog.find(d.seasonId).name();
        String mode = d.mode.equals(Session.MODE_RAHIM) ? "Rahim's way" : "Scout mode";
        r.text(d.name, x, y + 3, 10, 0xFFFFFFFF, true, true);
        String state = d.ended ? "Finished: " + Math.round(d.yieldPct * 100) + "% harvest" : "Day " + (d.day + 1);
        r.text(season + "  |  " + mode, x, y + 15, 7.5f, 0xFFB0B0B0);
        r.text(state + "  |  last played " + WHEN.format(Instant.ofEpochMilli(d.updated)), x, y + 25, 7.5f, d.ended ? 0xFFE0C060 : 0xFF909090);
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Select World", 11);
        for (Widget wd : widgets) wd.render(r, mx, my);
        if (saves.isEmpty()) {
            r.textCentered("No worlds yet.", w / 2f, 62, 10, 0xFFE0E0E0, true, true);
            r.textCentered("Create one to replay a real NASA season.", w / 2f, 76, 8.5f, 0xFFB0B0B0, true, false);
        }
    }

    private void playSelected() {
        if (list.selected < 0 || list.selected >= saves.size()) return;
        SaveManager.SaveData d = saves.get(list.selected);
        try {
            Season season = SeasonCatalog.load(d.seasonId);
            game.startSession(Session.restore(season, d));
        } catch (IOException | RuntimeException e) {
            game.toastMsg("Could not load world: " + e.getMessage());
            System.err.println("Could not load world: " + e);
        }
    }

    private void askDelete() {
        if (list.selected < 0 || list.selected >= saves.size()) return;
        SaveManager.SaveData d = saves.get(list.selected);
        Screen c = new ConfirmScreen("Delete \"" + d.name + "\"? This cannot be undone.", "Delete", "Cancel", () -> { SaveManager.delete(d.id); game.setScreen(refreshed()); });
        c.parent = this;
        game.setScreen(c);
    }

    private Screen refreshed() { WorldSelectScreen s = new WorldSelectScreen(); s.parent = parent; return s; }
}
