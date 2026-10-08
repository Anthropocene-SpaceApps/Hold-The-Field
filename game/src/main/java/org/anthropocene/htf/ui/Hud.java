package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Game;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.gfx.Tile;
import org.anthropocene.htf.sim.Day;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.GameState;
import org.anthropocene.htf.world.WorldScene;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** In-game overlay: hotbar, bars, boss bars, chat, crosshair, toasts. */
public final class Hud {
    public record Item(Tile icon, String name, String hint) {}

    public static final List<Item> ITEMS = List.of(
            new Item(Tile.ICON_BRICK, "Mud Bricks", "Raise the bund (aim at the wall)"),
            new Item(Tile.ICON_SICKLE, "Sickle", "Harvest the rice (aim at the field)"),
            new Item(Tile.ICON_SPYGLASS, "Scout Spyglass", "Open the satellite dashboard"),
            new Item(Tile.ICON_SATELLITE, "Field Notes", "Data & model"));

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static String date(String iso) { return LocalDate.parse(iso).format(DATE); }

    private Hud() {}

    public static void render(Game g, Renderer2D r) {
        Session s = g.session();
        GameState st = s.state;
        Day d = s.season.day(st.i);
        int w = r.guiW, h = r.guiH;

        // --- top-left info
        r.rect(6, 6, 152, 62, 0x70000000);
        r.text(date(st.date), 11, 9, 11, 0xFFFFFFFF, true, true);
        r.text("Day " + (st.i + 1) + " / " + s.season.length(), 11, 23, 8.5f, 0xFFDDDDDD, true, false);
        String sc = switch (st.status) { case "warning" -> "FLOOD WARNING"; case "watch" -> "Watch"; default -> "Calm"; };
        int scc = switch (st.status) { case "warning" -> 0xFFFF5555; case "watch" -> 0xFFFFAA33; default -> 0xFF8CE06A; };
        r.text("Scout: " + sc, 11, 34, 8.5f, scc, true, true);
        r.text(String.format("Rain up %3.0f mm  farm %2.0f mm", d.rainUp(), d.rainFarm()), 11, 45, 7.5f, 0xFFB8D8FF, true, false);
        r.text(String.format("Tmax %2.0f C   soil %.2f", d.tmax(), d.soil()), 11, 55, 7.5f, 0xFFE8D8B0, true, false);

        // --- boss bars (water vs bund, scout)
        float bw = 182, bx = Math.max(w / 2f - bw / 2, 166);
        double scale = Math.max(1.0, st.bund * 1.7);
        r.textCentered(String.format("Floodwater %.2f m  /  Bund %.2f m", st.level, st.bund), bx + bw / 2, 3, 8, 0xFFFFFFFF, true, false);
        r.rect(bx, 13, bw, 6, 0xFF000000);
        r.rect(bx + 1, 14, bw - 2, 4, 0xFF303050);
        int waterCol = st.level > st.bund ? 0xFFE04040 : st.level > st.bund * 0.85 ? 0xFFE0A030 : 0xFF4F9BE0;
        r.rect(bx + 1, 14, (float) Math.min(1, st.level / scale) * (bw - 2), 4, waterCol);
        r.rect(bx + 1 + (float) (st.bund / scale) * (bw - 2) - 1, 12, 2, 8, 0xFFFFFFFF);
        r.textCentered("Satellite Scout: " + sc, bx + bw / 2, 22, 8, scc, true, false);
        r.rect(bx, 32, bw, 6, 0xFF000000);
        r.rect(bx + 1, 33, bw - 2, 4, 0xFF303030);
        float scoutFill = switch (st.status) { case "warning" -> 1f; case "watch" -> 0.55f; default -> 0.2f; };
        r.rect(bx + 1, 33, scoutFill * (bw - 2), 4, scc);

        // --- top-right clock
        String speed = s.timePaused ? "PAUSED" : "Time x" + Session.SPEED_LABELS[s.speedIdx].replace("x", "");
        String mode = s.mode.equals(Session.MODE_RAHIM) ? "Rahim's way" : "Scout mode";
        r.rect(w - 130, 6, 124, 32, 0x70000000);
        r.textRight(speed, w - 11, 9, 9.5f, s.timePaused ? 0xFFFFDD55 : 0xFFFFFFFF, true);
        r.textRight(mode + " | " + (s.variety.equals("long") ? "long rice" : "short rice"), w - 11, 22, 7.5f, 0xFFDDDDDD, true);

        // --- start prompt
        if (s.timePaused && st.i == 0) {
            r.textCentered("Press " + g.keyName(org.anthropocene.htf.core.KeyAction.PAUSE_TIME) + " to begin the season", w / 2f, h * 0.30f, 12, 0xFFFFFFFF, true, true);
            r.textCentered("Look around first. Double-tap Space to fly up and see over the bund.", w / 2f, h * 0.30f + 15, 8.5f, 0xFFDDDDDD, true, false);
        }

        // --- hotbar
        int sel = g.hotbarSlot();
        int hx = w / 2 - 98, hy = h - 25;
        r.rect(hx - 1, hy - 1, 198, 24, 0xFF000000);
        for (int i = 0; i < 9; i++) {
            int sx = hx + i * 22;
            r.rect(sx, hy, 20, 22, i == sel ? 0xFF8A8A8A : 0xFF555555);
            r.rect(sx + 1, hy + 1, 18, 20, 0xFF2A2A2A);
            if (i < ITEMS.size()) {
                r.tile(ITEMS.get(i).icon(), sx + 2, hy + 3, 16, 16, 0xFFFFFFFF);
                if (i == 0) {
                    int left = s.cfg.maxBundRaises - st.bundRaises;
                    r.textRight(String.valueOf(left), sx + 19, hy + 11, 8, left > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);
                }
            }
            r.text(String.valueOf(i + 1), sx + 2, hy, 6, 0xFF909090);
        }
        r.border(hx + sel * 22 - 1, hy - 1, 22, 24, 2, 0xFFFFFFFF);
        if (sel < ITEMS.size() && g.hotbarNameTimer() > 0) {
            r.textCentered(ITEMS.get(sel).name(), w / 2f, hy - 34, 9, 0xFFFFFFFF, true, true);
        }

        // --- status above the hotbar
        float my = hy - 12;
        r.rect(w / 2f - 91, my, 182, 5, 0xFF000000);
        r.rect(w / 2f - 90, my + 1, 180, 3, 0xFF2A3A18);
        r.rect(w / 2f - 90, my + 1, (float) st.maturity * 180, 3, 0xFFE0B83A);
        r.rect(w / 2f - 90 + 0.8f * 180, my - 1, 1.5f, 7, 0xFFFFFFFF);
        r.textCentered(String.format("Rice %d%% mature (harvest from 80%%)", Math.round(st.maturity * 100)), w / 2f, my - 10, 7.5f, 0xFFDDF0A0, true, false);
        r.tile(Tile.ICON_COIN, w / 2f + 104, hy + 3, 14, 14, 0xFFFFFFFF);
        r.text(String.valueOf(st.coins), w / 2f + 121, hy + 5, 10, 0xFFFFE070, true, true);
        r.tile(Tile.ICON_DROP, w / 2f - 118, hy + 3, 14, 14, 0xFFFFFFFF);
        r.textRight(String.format("%.2f m", st.level), w / 2f - 122, hy + 5, 8.5f, 0xFFB8D8FF, true);

        crosshair(g, r, w, h);
        chat(g, r, h - 62);
    }

    private static void crosshair(Game g, Renderer2D r, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        WorldScene.Target t = g.target();
        int c = t.type() == WorldScene.TargetType.NONE ? 0xD0FFFFFF : 0xFF7CFF7C;
        r.rect(cx - 5, cy - 1, 11, 3, 0xA0000000);
        r.rect(cx - 1, cy - 5, 3, 11, 0xA0000000);
        r.rect(cx - 4, cy, 9, 1, c);
        r.rect(cx, cy - 4, 1, 9, c);
        String label = g.targetLabel();
        if (label != null) r.textCentered(label, cx, cy + 12, 8.5f, 0xFFFFFFFF, true, false);
    }

    private static void chat(Game g, Renderer2D r, float bottomY) {
        List<Game.ChatLine> lines = g.chatLines();
        double now = g.time();
        int shown = 0;
        for (int i = lines.size() - 1; i >= 0 && shown < 7; i--) {
            Game.ChatLine l = lines.get(i);
            double age = now - l.born();
            if (age > 12) break;
            float alpha = age < 9 ? 1f : (float) (1 - (age - 9) / 3);
            float y = bottomY - shown * 11;
            float tw = r.textWidth(l.text(), 8) + 6;
            r.rect(4, y - 1, Math.min(tw, r.guiW * 0.55f), 11, Renderer2D.withAlpha(0x80000000, alpha));
            r.text(l.text(), 7, y, 8, Renderer2D.withAlpha(l.color(), alpha), true, false);
            shown++;
        }
    }

    /** Minecraft-style advancement toast, top right. */
    public static void toast(Renderer2D r, Game.Toast t, double now) {
        double age = now - t.born();
        if (age > 5.5) return;
        float slide = (float) Math.min(1, Math.min(age * 4, (5.5 - age) * 4));
        float w = 168, x = r.guiW - w * slide - 4, y = 44;
        r.rect(x, y, w, 30, 0xFF000000);
        r.rect(x + 1, y + 1, w - 2, 28, 0xFF2B2B2B);
        r.border(x + 1, y + 1, w - 2, 28, 1, 0xFF6A6A6A);
        r.rect(x + 5, y + 5, 20, 20, 0xFF000000);
        r.rect(x + 6, y + 6, 18, 18, 0xFF6A5A1A);
        r.tile(t.icon(), x + 7, y + 7, 16, 16, 0xFFFFFFFF);
        r.text(t.header(), x + 30, y + 4, 7.5f, 0xFFFFFF55, true, false);
        r.text(t.title(), x + 30, y + 15, 8.5f, 0xFFFFFFFF, true, true);
    }

    public static void debug(Game g, Renderer2D r, int fps) {
        Session s = g.session();
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Hold the Field 1.0.0 (" + fps + " fps)");
        lines.add("OpenGL: " + g.glInfo());
        var p = g.player();
        lines.add(String.format("XYZ: %.2f / %.2f / %.2f", p.pos.x, p.pos.y, p.pos.z));
        lines.add(String.format("Facing: yaw %.0f pitch %.0f %s%s", Math.toDegrees(p.yaw) % 360, Math.toDegrees(p.pitch),
                p.flying ? "[flying] " : "", p.swimming ? "[swimming]" : p.onGround ? "[on ground]" : ""));
        if (s != null) {
            GameState st = s.state;
            Day d = s.season.day(st.i);
            lines.add("Season: " + s.season.id + (s.season.sample ? " (SAMPLE DATA)" : " (NASA POWER)") + "  day " + st.i);
            lines.add(String.format("rainUp %.1f  rainFarm %.1f  tmax %.1f  soil %.3f", d.rainUp(), d.rainFarm(), d.tmax(), d.soil()));
            lines.add(String.format("3-day upstream %.0f mm  status %s", Engine.threeDayUpstream(s.season.days, st.i), st.status));
            lines.add(String.format("level %.3f m  bund %.2f m  flooded %s  underwater %d d", st.level, st.bund, st.flooded, st.underwaterDays));
            lines.add(String.format("maturity %.3f  alive %s  harvested %s  yield %.2f", st.maturity, st.alive, st.harvested, st.yieldPct));
            lines.add("Target: " + g.target());
        }
        float y = 74;
        for (String l : lines) {
            r.rect(5, y - 1, r.textWidth(l, 8) + 4, 10, 0x90505050);
            r.text(l, 7, y, 8, 0xFFE0E0E0);
            y += 10;
        }
    }
}
