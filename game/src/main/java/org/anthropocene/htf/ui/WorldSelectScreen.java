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

import static org.anthropocene.htf.ui.Theme.*;

/** Saved farms: continue, start a new season, delete. */
public final class WorldSelectScreen extends Screen {
    private final List<SaveManager.SaveData> saves = new ArrayList<>();
    private Widget.ListBox<SaveManager.SaveData> list;
    private Widget.Button play, delete;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    @Override
    protected void init() {
        layoutCard(760, 580);
        saves.clear();
        saves.addAll(SaveManager.list());
        list = add(new Widget.ListBox<>(saves, this::drawRow));
        list.bounds(cardX + 24, cardY + 96, cardW - 48, cardH - 96 - 84);
        list.rowH = 68;
        list.onSelect = i -> refresh();
        list.onDouble = i -> playSelected();
        int by = cardY + cardH - 62;
        button("New Season", cardX + 24, by, 200, () -> open(new PlanSeasonScreen())).primary();
        play = button("Continue", cardX + 236, by, 160, this::playSelected);
        delete = button("Delete", cardX + 408, by, 120, this::askDelete);
        delete.danger = true;
        button("Back", cardX + cardW - 144, by, 120, () -> game.setScreen(parent));
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
        r.text(d.name, x, y + 12, 17, TEXT, false, true);
        r.text(season + "   |   " + mode + (d.plantDate != null ? "   |   planted " + d.plantDate : ""), x, y + 36, 12, MUTED);
        String chipText = d.ended ? Math.round(d.yieldPct * 100) + "% harvest" : "Day " + (d.day + 1);
        float cw = r.textWidth(chipText, 12, true) + 24;
        Theme.chip(r, x + w - cw, y + 12, cw, 24, d.ended ? (d.yieldPct > 0 ? 0x445BD18A : 0x44FF5D5D) : 0x444DA3FF);
        r.text(chipText, x + w - cw + 12, y + 17, 12, d.ended ? (d.yieldPct > 0 ? GOOD : BAD) : 0xFFB6D8FF, false, true);
        r.textRight(WHEN.format(Instant.ofEpochMilli(d.updated)), x + w, y + 40, 11, FAINT, false);
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        heading(r, "Your Farms");
        for (Widget wd : widgets) wd.render(r, mx, my);
        if (saves.isEmpty()) {
            r.textCentered("No farms yet", w / 2f, cardY + 220, 22, TEXT, false, true);
            r.textCentered("Start a new season to replay a real NASA-measured flood or drought.", w / 2f, cardY + 252, 14, MUTED, false, false);
        }
    }

    private void playSelected() {
        if (list.selected < 0 || list.selected >= saves.size()) return;
        SaveManager.SaveData d = saves.get(list.selected);
        try {
            Season season = SeasonCatalog.load(d.seasonId);
            game.startSession(Session.restore(season, d));
        } catch (IOException | RuntimeException e) {
            game.toastMsg("Could not load farm: " + e.getMessage());
            System.err.println("Could not load farm: " + e);
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
