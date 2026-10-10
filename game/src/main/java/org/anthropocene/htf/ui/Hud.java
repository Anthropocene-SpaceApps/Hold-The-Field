package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.I18n;
import org.anthropocene.htf.core.KeyAction;
import org.anthropocene.htf.game.Game;
import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.sim.Day;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.Event;
import org.anthropocene.htf.sim.GameState;
import org.anthropocene.htf.world.Landscape;
import org.anthropocene.htf.world.WorldScene;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.anthropocene.htf.ui.Theme.*;

/** In-game overlay: season timeline, NASA data card, water gauge, action bar, field notes, crosshair, toasts. */
public final class Hud {
    public record Item(Icons.Id icon, String name, String hint, String key) {}

    public static final List<Item> ITEMS_DRY = List.of(
            new Item(Icons.Id.DROP, "Irrigate", "Aim at the rice field and click", "1"),
            new Item(Icons.Id.SICKLE, "Harvest", "Aim at the rice and click", "2"),
            new Item(Icons.Id.SATELLITE, "Satellite view", "See what NASA sees", "3"),
            new Item(Icons.Id.CHART, "Dashboard", "Charts of the season so far", "4"));

    public static List<Item> items(Session s) { return s != null && s.cfg.isDrought() ? ITEMS_DRY : ITEMS; }

    public static final List<Item> ITEMS = List.of(
            new Item(Icons.Id.BUND, "Raise bund", "Aim at the embankment and click", "1"),
            new Item(Icons.Id.SICKLE, "Harvest", "Aim at the rice and click", "2"),
            new Item(Icons.Id.SATELLITE, "Satellite view", "See what NASA sees", "3"),
            new Item(Icons.Id.CHART, "Dashboard", "Charts of the season so far", "4"));

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static String date(String iso) { return LocalDate.parse(iso).format(DATE); }

    private Hud() {}

    private static int statusColor(String status) { return switch (status) { case "warning" -> BAD; case "watch" -> WARN; default -> GOOD; }; }
    private static String statusText(String status, boolean drought) { return switch (status) { case "warning" -> drought ? "DROUGHT WARNING" : "FLOOD WARNING"; case "watch" -> "WATCH"; default -> "CALM"; }; }

    // ------------------------------------------------------------------ main HUD

    public static void render(Game g, Renderer2D r) {
        Session s = g.session();
        GameState st = s.state;
        Day d = s.season.day(st.i);
        int w = r.guiW, h = r.guiH;
        boolean sat = g.satelliteView();

        seasonCard(r, s, st, 24, 24);
        dataCard(r, s, st, d, w - 24 - 320, 24);
        timeChip(r, s, w / 2f - 96, 24);
        gauge(r, st, s, 24, 152, Math.min(320, h - 152 - 170));
        if (!sat) {
            actionBar(g, r, s, st, w, h);
            crosshair(g, r, w, h);
        }
        feed(g, r, h - 24);
        speech(g, r, w, h);
        if (s.timePaused && st.i == st.startIndex && !sat) prompt(g, r, w, h);
        if (sat) satellite(g, r, s, st, d, w, h);
    }

    private static void seasonCard(Renderer2D r, Session s, GameState st, int x, int y) {
        Theme.card(r, x, y, 300, 112, 14, PANEL);
        int n = s.season.length(), from = st.startIndex;
        r.text(date(st.date), x + 18, y + 14, 22, TEXT, false, true);
        String day = I18n.f("Day {} of {}", st.i - from + 1, n - from);
        r.textRight(day, x + 282, y + 22, 12, MUTED, false);
        // timeline with the key moments
        float bx = x + 18, bw = 264, by = y + 54;
        r.roundRect(bx, by, bw, 6, 3, 0x26FFFFFF);
        float prog = (st.i - from) / (float) Math.max(1, n - 1 - from);
        r.roundRect(bx, by, Math.max(6, bw * prog), 6, 3, ACCENT);
        for (Event e : st.events) {
            int col = switch (e.type()) { case "warning" -> BAD; case "flood" -> WATER; case "stress" -> TEMP; case "harvest" -> GOOD; case "loss" -> 0xFFDDDDDD; case "action" -> SOIL; default -> -1; };
            if (col == -1) continue;
            float ex = bx + bw * (e.i() - from) / (float) Math.max(1, n - 1 - from);
            r.circle(ex, by + 3, 4.2f, col);
        }
        r.circle(bx + bw * prog, by + 3, 6.5f, 0xFFFFFFFF);
        int sc = statusColor(st.status);
        String t = I18n.f("SCOUT  {}", statusText(st.status, s.cfg.isDrought()));
        float tw = r.textWidth(t, 11.5f, true) + 26;
        Theme.chip(r, x + 18, y + 72, tw, 24, Renderer2D.withAlpha(sc, st.status.equals("warning") ? 0.30f + 0.12f * (float) Math.sin(Theme.clock * 7) : 0.24f));
        r.circle(x + 30, y + 84, 4.2f, sc);
        r.text(t, x + 40, y + 77, 11.5f, sc, false, true);
    }

    private static void dataCard(Renderer2D r, Session s, GameState st, Day d, int x, int y) {
        boolean dry = s.cfg.isDrought();
        Theme.card(r, x, y, 320, 132, 14, PANEL);
        Theme.label(r, I18n.f("{}  /  today", s.season.sample ? "SAMPLE DATA" : "NASA POWER"), x + 18, y + 12);
        if (dry) {
            metric(r, Icons.Id.RAIN, GOOD, I18n.f("{} mm", fmt0(d.rainFarm())), "rain on farm", x + 18, y + 32);
            metric(r, Icons.Id.RAIN, RAIN, I18n.f("{} mm", fmt0(Engine.recentRain(s.season.days, st.i, 7))), "rain, last 7 days", x + 168, y + 32);
        } else {
            metric(r, Icons.Id.RAIN, RAIN, I18n.f("{} mm", fmt0(d.rainUp())), "rain upstream", x + 18, y + 32);
            metric(r, Icons.Id.RAIN, GOOD, I18n.f("{} mm", fmt0(d.rainFarm())), "rain on farm", x + 168, y + 32);
        }
        metric(r, Icons.Id.THERMO, TEMP, I18n.f("{} C", fmt0(d.tmax())), "max temperature", x + 18, y + 62);
        metric(r, Icons.Id.SOIL, SOIL, String.format(Locale.ROOT, "%.2f", d.soil()), "soil wetness", x + 168, y + 62);
        // 14-day rain sparkline, coloured by the scout thresholds
        float sx = x + 18, sw = 284, sy = y + 100, sh = 24;
        int from = Math.max(0, st.i - 13);
        double max = dry ? 40 : 120;
        for (int i = from; i <= st.i; i++) max = Math.max(max, dry ? s.season.day(i).rainFarm() : s.season.day(i).rainUp());
        float bw = sw / 14f;
        for (int i = from; i <= st.i; i++) {
            int col;
            double v;
            if (dry) {
                String sc = Engine.scoutStatus(s.season.days, i, s.cfg);
                col = sc.equals("warning") ? BAD : sc.equals("watch") ? WARN : RAIN;
                v = s.season.day(i).rainFarm();
            } else {
                double sum3 = Engine.threeDayUpstream(s.season.days, i);
                col = sum3 >= s.cfg.warningMm ? BAD : sum3 >= s.cfg.watchMm ? WARN : RAIN;
                v = s.season.day(i).rainUp();
            }
            float bh = (float) (v / max) * sh;
            r.roundRect(sx + (i - from) * bw + 1, sy + sh - Math.max(2, bh), bw - 2, Math.max(2, bh), 1.5f, col);
        }
    }

    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static String fmt0(double v) { return String.format(Locale.ROOT, "%.0f", v); }

    private static void metric(Renderer2D r, Icons.Id icon, int color, String value, String label, float x, float y) {
        Icons.draw(r, icon, x + 10, y + 14, 18, color);
        r.text(value, x + 28, y + 1, 16, TEXT, false, true);
        r.text(label, x + 28, y + 19, 10.5f, MUTED);
    }

    private static void timeChip(Renderer2D r, Session s, float x, float y) {
        Theme.card(r, x, y, 192, 38, 19, PANEL);
        boolean paused = s.timePaused;
        Icons.draw(r, paused ? Icons.Id.PAUSE : Icons.Id.PLAY, x + 24, y + 19, 16, paused ? WARN : GOOD);
        r.text(paused ? "Paused" : "Running  " + Session.SPEED_LABELS[s.speedIdx], x + 44, y + 11, 14, TEXT, false, true);
        r.textRight("P", x + 176, y + 13, 11, FAINT, false);
    }

    private static void gauge(Renderer2D r, GameState st, Session s, int x, int y, int h) {
        if (h < 120) return;
        boolean dry = s.cfg.isDrought();
        Theme.card(r, x, y, 96, h, 14, PANEL);
        Theme.label(r, dry ? "Soil" : "Water", x + 18, y + 12);
        float top = y + 38, bottom = y + h - 30, max = dry ? 1.0f : 1.6f, gx = x + 20, gw = 22;
        r.roundRect(gx, top, gw, bottom - top, 6, 0x22FFFFFF);
        double value = dry ? st.moisture : st.level, line = dry ? Engine.stressThreshold(s.cfg, st.variety) : st.bund;
        float fill = (float) Math.min(1, value / max) * (bottom - top);
        int wc = dry ? (value < line ? BAD : value < line + 0.08 ? WARN : WATER) : (st.level > st.bund ? BAD : st.level > st.bund * 0.85 ? WARN : WATER);
        if (fill > 1) r.roundRect(gx, bottom - fill, gw, fill, 6, wc);
        if (dry && st.boost > 0.01) {      // irrigation still working in the soil
            float bf = (float) Math.min(st.boost / max, value / max) * (bottom - top);
            r.roundRect(gx + 4, bottom - fill, gw - 8, bf, 4, 0x66FFFFFF);
        }
        for (int i = 0; i <= (dry ? 4 : 6); i++) {
            float v = i * (dry ? 0.25f : 0.25f), ty = bottom - v / max * (bottom - top);
            r.rect(gx + gw + 4, ty - 0.5f, i % 2 == 0 ? 8 : 5, 1, 0x66FFFFFF);
            if (i % 2 == 0) r.text(String.format(Locale.ROOT, "%.1f", v), gx + gw + 15, ty - 6, 10.5f, MUTED);
        }
        float by = bottom - (float) line / max * (bottom - top);
        r.rect(gx - 5, by - 1, gw + 10, 2.4f, WARN);
        r.text(dry ? "stress" : "bund", x + 52, by - 14, 10, WARN, false, true);
        r.text(dry ? String.format(Locale.ROOT, "%.2f", value) : I18n.f("{} m", String.format(Locale.ROOT, "%.2f", st.level)), x + 18, y + h - 22, 13, wc, false, true);
    }

    private static void actionBar(Game g, Renderer2D r, Session s, GameState st, int w, int h) {
        List<Item> items = items(s);
        int sz = 92, gap = 14, total = items.size() * sz + (items.size() - 1) * gap;
        int x0 = (w - total) / 2, y0 = h - 24 - sz;
        int sel = g.hotbarSlot();
        // maturity strip across the top of the action bar
        float mx = x0, mw = total, my = y0 - 42;
        r.roundRect(mx, my, mw, 30, 15, PANEL);
        r.roundRing(mx, my, mw, 30, 15, 1f, BORDER);
        float bx = mx + 16, bw = mw - 190;
        r.roundRect(bx, my + 12, bw, 7, 3.5f, 0x26FFFFFF);
        r.roundRect(bx, my + 12, Math.max(7, bw * (float) st.maturity), 7, 3.5f, st.maturity >= 0.8 ? GOOD : CROP);
        r.rect(bx + bw * 0.8f - 0.8f, my + 8, 1.6f, 15, 0xCCFFFFFF);
        String rice = I18n.f("Rice {}%", Math.round(st.maturity * 100));
        r.text(rice, bx + bw + 14, my + 7, 13, TEXT, false, true);
        r.text(st.maturity >= 0.8 ? "ready to cut" : "ready at 80%", bx + bw + 14 + r.textWidth(rice, 13, true) + 8, my + 9, 10.5f, st.maturity >= 0.8 ? GOOD : MUTED);
        // budget chip to the right of the bar
        float cx = x0 - 118, cy = y0 + sz / 2f - 15;
        r.roundRect(cx, cy, 104, 30, 15, PANEL);
        r.roundRing(cx, cy, 104, 30, 15, 1f, BORDER);
        Icons.draw(r, Icons.Id.COIN, cx + 18, cy + 15, 17, CROP);
        r.text(I18n.f("Tk {}", st.coins), cx + 36, cy + 7, 14, CROP, false, true);

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int x = x0 + i * (sz + gap);
            boolean on = i == sel;
            boolean usable = i >= 2 || s.actionsAllowed();
            float lift = on ? -4 : 0;
            r.shadow(x, y0 + lift + 4, sz, sz, 16, 14, on ? 0x77000000 : 0x44000000);
            r.roundRect(x, y0 + lift, sz, sz, 16, on ? 0xF01C2C44 : PANEL);
            r.roundRing(x, y0 + lift, sz, sz, 16, on ? 2.2f : 1f, on ? ACCENT : BORDER);
            Icons.draw(r, it.icon(), x + sz / 2f, y0 + lift + 37, 36, usable ? (on ? 0xFFFFFFFF : 0xFFCFDCEB) : FAINT);
            r.textCentered(it.name(), x + sz / 2f, y0 + lift + 66, 11.5f, usable ? TEXT : FAINT, false, true);
            r.roundRect(x + 7, y0 + lift + 7, 18, 18, 5, 0x40FFFFFF);
            r.textCentered(it.key(), x + 16, y0 + lift + 10, 11, TEXT, false, true);
            String badge = switch (i) {
                case 0 -> s.cfg.isDrought() ? I18n.f("Tank {}", (int) Math.floor(st.tank)) : s.cfg.maxBundRaises - st.bundRaises > 0 ? I18n.f("Tk {}", s.cfg.bundRaiseCost) : "max";
                case 1 -> st.maturity >= s.cfg.minHarvestMaturity ? "ready" : "";
                default -> "";
            };
            if (!badge.isEmpty()) r.textRight(badge, x + sz - 7, y0 + lift + 9, 10, i == 0 && (s.cfg.isDrought() ? st.tank < 1 : s.cfg.maxBundRaises - st.bundRaises <= 0) ? BAD : CROP, false);
        }
        if (g.hotbarNameTimer() > 0) r.textCentered(items.get(Math.min(sel, items.size() - 1)).hint(), w / 2f, y0 - 62, 12, MUTED, true, false);
    }

    private static void crosshair(Game g, Renderer2D r, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        WorldScene.Target t = g.target();
        boolean hit = t.type() != WorldScene.TargetType.NONE;
        r.ring(cx, cy, hit ? 7 : 5, 1.6f, hit ? 0xFFFFFFFF : 0xB0FFFFFF);
        r.circle(cx, cy, 1.8f, hit ? ACCENT : 0xFFFFFFFF);
        String label = g.targetLabel();
        if (label != null) {
            float tw = r.textWidth(label, 13, true) + 24;
            r.shadow(cx - tw / 2, cy + 22, tw, 28, 14, 8, 0x55000000);
            r.roundRect(cx - tw / 2, cy + 22, tw, 28, 14, 0xE60F1826);
            r.roundRing(cx - tw / 2, cy + 22, tw, 28, 14, 1f, BORDER);
            r.textCentered(label, cx, cy + 29, 13, TEXT, false, true);
        }
    }

    private static void feed(Game g, Renderer2D r, float bottom) {
        List<Game.ChatLine> lines = g.chatLines();
        double now = g.time();
        float width = 340, x = r.guiW - 24 - width, y = bottom;
        int shown = 0;
        for (int i = lines.size() - 1; i >= 0 && shown < 4; i--) {
            Game.ChatLine l = lines.get(i);
            double age = now - l.born();
            if (age > 14) break;
            float alpha = age < 11 ? 1f : (float) (1 - (age - 11) / 3);
            List<String> wrapped = r.wrap(l.text(), width - 40, 13);
            float hh = 14 + wrapped.size() * 17;
            y -= hh + 8;
            r.roundRect(x, y, width, hh, 12, Renderer2D.withAlpha(0xE60C1420, alpha));
            r.roundRect(x + 8, y + 8, 4, hh - 16, 2, Renderer2D.withAlpha(l.color(), alpha));
            float ty = y + 7;
            for (String ln : wrapped) { r.text(ln, x + 22, ty, 13, Renderer2D.withAlpha(0xFFEAF1FA, alpha)); ty += 17; }
            shown++;
        }
    }

    private static void speech(Game g, Renderer2D r, int w, int h) {
        if (g.time() > g.speechUntil() || g.speechText() == null) return;
        float bw = Math.min(620, w - 80);
        List<String> lines = r.wrap(g.speechText(), bw - 48, 15);
        float bh = 54 + lines.size() * 21;
        float x = (w - bw) / 2f, y = h - 24 - 86 - 70 - bh;
        Theme.card(r, x, y, bw, bh, 16, 0xF2111C2C);
        r.circle(x + 28, y + 28, 15, 0xFF5E8F5A);
        r.text("R", x + 22, y + 18, 17, 0xFFFFFFFF, false, true);
        r.text("Rahim", x + 54, y + 12, 14, CROP, false, true);
        float ty = y + 38;
        for (String l : lines) { r.text(l, x + 24, ty, 15, TEXT); ty += 21; }
    }

    private static void prompt(Game g, Renderer2D r, int w, int h) {
        float bw = 780, bh = 92, x = (w - bw) / 2f, y = h * 0.20f;
        Theme.card(r, x, y, bw, bh, 16, 0xE60F1826);
        r.textCentered(I18n.f("Press {} to begin the season", g.keyName(KeyAction.PAUSE_TIME)), w / 2f, y + 16, 22, TEXT, false, true);
        r.textCentered(g.session().cfg.isDrought()
                ? "Look around first. Double-tap Space to fly up and see the dry fields, or press M for the NASA satellite view."
                : "Look around first. Double-tap Space to fly up and see over the embankment, or press M for the NASA satellite view.", w / 2f, y + 54, 13.5f, 0xFFC9D6E6, false, false);
    }

    // ------------------------------------------------------------------ satellite view

    private static void satellite(Game g, Renderer2D r, Session s, GameState st, Day d, int w, int h) {
        r.gradientV(0, 0, w, 150, 0x99050A12, 0x00050A12);
        r.gradientV(0, h - 200, w, 200, 0x00050A12, 0xB0050A12);
        r.textCentered("NASA SATELLITE VIEW", w / 2f, 78, 14, ACCENT, false, true);
        r.textCentered(s.cfg.isDrought() ? "When the rain stops, the soil dries out within days" : "Rain over the Meghalaya hills reaches the haor about two days later", w / 2f, 100, 13, MUTED, true, false);

        Landscape ls = g.scene().landscape();
        var farmPt = s.season.points.farm();
        var upPt = s.season.points.upstream();
        float[] farm = g.project(new Vector3f(0, ls.height(0, 0) + 8, 0));
        float[] up = g.project(new Vector3f(24, 60, -640));
        boolean dry = s.cfg.isDrought();
        if (!dry && up != null && farm != null) {
            for (int i = 0; i < 18; i++) {   // dashed flow line from the hills to the farm
                float t0 = i / 18f + (float) (Theme.clock * 0.1 % (1 / 18.0)), t1 = t0 + 0.03f;
                r.line(up[0] + (farm[0] - up[0]) * t0, up[1] + (farm[1] - up[1]) * t0, up[0] + (farm[0] - up[0]) * t1, up[1] + (farm[1] - up[1]) * t1, 2.2f, 0xAAFFFFFF);
            }
        }
        if (farm != null) r.triangle(farm[0], farm[1] - 14, farm[0] - 6, farm[1] - 26, farm[0] + 6, farm[1] - 26, 0xFFFFFFFF);
        if (!dry && up != null) pointLabel(r, up[0], up[1], "UPSTREAM  Meghalaya hills", I18n.f("{} N  {} E", f2(upPt.lat()), f2(upPt.lon())),
                I18n.f("rain {} mm/day   3-day {} mm", fmt0(d.rainUp()), fmt0(Engine.threeDayUpstream(s.season.days, st.i))), RAIN);
        if (farm != null) pointLabel(r, farm[0], farm[1], dry ? "FARM  Barind Tract" : "FARM  Sunamganj haor", I18n.f("{} N  {} E", f2(farmPt.lat()), f2(farmPt.lon())),
                dry ? I18n.f("rain {} mm   7-day {} mm   soil {}", fmt0(d.rainFarm()), fmt0(Engine.recentRain(s.season.days, st.i, 7)), f2(d.soil()))
                    : I18n.f("rain {} mm   soil {}   water {} m", fmt0(d.rainFarm()), f2(d.soil()), f2(st.level)), CROP);

        // legend
        float lx = 24, ly = h - 130;
        Theme.card(r, lx, ly, 262, 106, 14, PANEL);
        Theme.label(r, "Data layers", lx + 16, ly + 12);
        r.gradientH(lx + 16, ly + 34, 160, 10, 0xFF1A66FF, 0xFFFF4033);
        r.text(dry ? "rain intensity" : "rain intensity (upstream)", lx + 16, ly + 48, 11, MUTED);
        r.gradientH(lx + 16, ly + 70, 160, 10, 0xFFC09040, 0xFF1A59D9);
        r.text("soil wetness (dry to wet)", lx + 16, ly + 84, 11, MUTED);
        r.textRight("drag: look   wheel: zoom   WASD: move   M / Esc: back", w - 24, h - 34, 12, MUTED, true);
    }

    private static void pointLabel(Renderer2D r, float x, float y, String title, String coords, String data, int color) {
        r.line(x, y, x, y - 34, 1.6f, color);
        r.circle(x, y, 5, color);
        float tw = Math.max(Math.max(r.textWidth(title, 13, true), r.textWidth(coords, 12)), r.textWidth(data, 12)) + 28;
        float bx = Math.max(12, Math.min(r.guiW - tw - 12, x - tw / 2)), by = Math.max(130, y - 34 - 78);
        Theme.card(r, bx, by, tw, 78, 13, 0xF0111C2C);
        r.text(title, bx + 14, by + 10, 13, color, false, true);
        r.text(coords, bx + 14, by + 31, 12, TEXT);
        r.text(data, bx + 14, by + 50, 12, MUTED);
    }

    // ------------------------------------------------------------------ toasts and debug

    /** Advancement toast, below the NASA card. */
    public static void toast(Renderer2D r, Game.Toast t, double now) {
        double age = now - t.born();
        if (age > 5.5) return;
        float slide = (float) Math.min(1, Math.min(age * 4, (5.5 - age) * 4));
        float w = 340, h = 64, x = (r.guiW - w) / 2f, y = 72 - (1 - Theme.ease(slide)) * 90;
        Theme.card(r, x, y, w, h, 14, 0xF2132033);
        r.roundRect(x + 12, y + 12, 40, 40, 20, 0xFFFFC857);
        Icons.draw(r, Icons.Id.WHEAT, x + 32, y + 32, 24, 0xFF4A3000);
        r.text(I18n.caps(t.header()), x + 64, y + 11, 10.5f, 0xFFFFD27A, false, true);
        r.text(t.title(), x + 64, y + 28, 16, TEXT, false, true);
    }

    public static void debug(Game g, Renderer2D r, int fps) {
        Session s = g.session();
        List<String> lines = new ArrayList<>();
        lines.add("Agrocene 1.0.0   " + fps + " fps");
        lines.add("GPU: " + g.glInfo());
        var p = g.player();
        lines.add(String.format("XYZ %.2f / %.2f / %.2f   yaw %.0f pitch %.0f %s%s", p.pos.x, p.pos.y, p.pos.z, Math.toDegrees(p.yaw) % 360, Math.toDegrees(p.pitch),
                p.flying ? "[fly] " : "", p.swimming ? "[swim]" : p.onGround ? "[ground]" : ""));
        if (s != null) {
            GameState st = s.state;
            Day d = s.season.day(st.i);
            lines.add("Season " + s.season.id + (s.season.sample ? " (SAMPLE)" : " (NASA POWER)") + "  day " + st.i + "  planted " + st.transplant);
            lines.add(String.format("rainUp %.1f  rainFarm %.1f  tmax %.1f  soil %.3f", d.rainUp(), d.rainFarm(), d.tmax(), d.soil()));
            lines.add(String.format("3-day upstream %.0f mm  status %s", Engine.threeDayUpstream(s.season.days, st.i), st.status));
            lines.add(String.format("level %.3f m  bund %.2f m  flooded %s  underwater %d d", st.level, st.bund, st.flooded, st.underwaterDays));
            lines.add(String.format("maturity %.3f  alive %s  harvested %s  yield %.2f", st.maturity, st.alive, st.harvested, st.yieldPct));
            lines.add("Target " + g.target().type());
        }
        float y = 290;
        for (String l : lines) {
            r.roundRect(24, y - 2, r.monoWidth(l, 11) + 14, 17, 4, 0xB0000000);
            r.textMono(l, 31, y, 11, 0xFFE0E8F0);
            y += 19;
        }
    }
}
