package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import java.util.ArrayList;
import java.util.List;

import static org.anthropocene.htf.ui.Theme.*;

/**
 * An interactive line/area chart over the season's days. Values beyond {@code visibleUntil} stay hidden (the scout
 * cannot see the future). Hovering shows a cursor and a tooltip with every series' value.
 */
public final class Chart {
    public record Series(String name, double[] values, int color, boolean area, boolean step, String unit) {}
    public record HLine(double value, int color, String label) {}
    public record VLine(int day, int color, String label) {}

    public String title;
    public final List<Series> series = new ArrayList<>();
    public final List<HLine> hlines = new ArrayList<>();
    public final List<VLine> vlines = new ArrayList<>();
    public double yMin = 0, yMax = Double.NaN;       // NaN = auto from data
    public int visibleUntil;                          // inclusive day index
    public int cursorDay;                             // "today" marker
    public int firstDay;                              // data before this day is shown dimmed (before planting)
    public int days;
    public String[] dates;
    public int decimals = 0;

    public Chart(String title) { this.title = title; }

    public Chart line(String name, double[] v, int color, String unit) { series.add(new Series(name, v, color, false, false, unit)); return this; }
    public Chart areaOf(String name, double[] v, int color, String unit) { series.add(new Series(name, v, color, true, false, unit)); return this; }
    public Chart stepped(String name, double[] v, int color, String unit) { series.add(new Series(name, v, color, false, true, unit)); return this; }
    public Chart hline(double v, int color, String label) { hlines.add(new HLine(v, color, label)); return this; }
    public Chart vline(int day, int color, String label) { vlines.add(new VLine(day, color, label)); return this; }

    private double top() {
        if (!Double.isNaN(yMax)) return yMax;
        double m = 1e-9;
        for (Series s : series) for (int i = 0; i < Math.min(s.values.length, visibleUntil + 1); i++) m = Math.max(m, s.values[i]);
        for (HLine hl : hlines) m = Math.max(m, hl.value * 1.05);
        return niceCeil(m * 1.08);
    }

    private static double niceCeil(double v) {
        double p = Math.pow(10, Math.floor(Math.log10(v))), f = v / p;
        double n = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return n * p;
    }

    private String fmt(double v) { return decimals == 0 ? String.valueOf(Math.round(v)) : String.format("%." + decimals + "f", v); }

    public void draw(Renderer2D r, int x, int y, int w, int h, int mx, int my) {
        r.roundRect(x, y, w, h, 13, 0x14FFFFFF);
        r.roundRing(x, y, w, h, 13, 1f, BORDER);
        r.text(title.toUpperCase(), x + 14, y + 11, LABEL, MUTED, false, true);
        // legend, on its own line under the title
        float lx = x + 14;
        for (Series se : series) {
            r.circle(lx + 3.5f, y + 31, 3.5f, se.color);
            r.text(se.name, lx + 12, y + 25, 11, MUTED);
            lx += 12 + r.textWidth(se.name, 11) + 16;
        }

        int px = x + 38, py = y + 46, pw = w - 52, ph = h - 70;
        double top = top(), lo = yMin, span = Math.max(1e-9, top - lo);
        for (int g = 0; g <= 3; g++) {
            float gy = py + ph - ph * g / 3f;
            r.rect(px, gy, pw, 1, g == 0 ? 0x66FFFFFF : 0x1EFFFFFF);
            String lbl = fmt(lo + span * g / 3);
            r.text(lbl, px - 6 - r.textWidth(lbl, 10.5f), gy - 6.5f, 10.5f, FAINT);
        }
        // before the season starts and in the future: dimmed
        if (firstDay > 0) r.rect(px, py, pw * (float) firstDay / Math.max(1, days - 1), ph, 0x1A000000);
        if (visibleUntil < days - 1) {
            float fx = px + pw * (visibleUntil + 0.5f) / (days - 1);
            r.rect(fx, py, px + pw - fx, ph, 0x30000000);
        }
        for (HLine hl : hlines) {
            float hy = py + ph - (float) ((hl.value - lo) / span) * ph;
            if (hy < py || hy > py + ph) continue;
            for (float dx = 0; dx < pw; dx += 8) r.rect(px + dx, hy, 4, 1.2f, hl.color);
            r.text(hl.label, px + pw - r.textWidth(hl.label, 10, true) - 2, hy - 13, 10, hl.color, false, true);
        }
        for (Series s : series) drawSeries(r, s, px, py, pw, ph, lo, span);
        int row = 0;
        for (VLine vl : vlines) {
            float vx = px + pw * (float) vl.day / Math.max(1, days - 1);
            r.rect(vx, py, 1.4f, ph, Renderer2D.withAlpha(vl.color, 0.8f));
            float tw = r.textWidth(vl.label, 10, true);
            r.roundRect(Math.min(vx + 3, px + pw - tw - 8), py + 2 + (row++ % 3) * 14, tw + 8, 13, 6, Renderer2D.withAlpha(vl.color, 0.30f));
            r.text(vl.label, Math.min(vx + 7, px + pw - tw - 4), py + 3 + ((row - 1) % 3) * 14, 10, vl.color, false, true);
        }
        float tx = px + pw * (float) cursorDay / Math.max(1, days - 1);
        r.rect(tx, py, 1.2f, ph, 0xAAFFFFFF);

        if (dates != null && dates.length > 0) {
            r.text(dates[0], px, py + ph + 6, 10.5f, FAINT);
            String last = dates[dates.length - 1];
            r.text(last, px + pw - r.textWidth(last, 10.5f), py + ph + 6, 10.5f, FAINT);
        }

        if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
            int d = Math.max(0, Math.min(days - 1, Math.round((mx - px) / (float) pw * (days - 1))));
            float hx = px + pw * (float) d / Math.max(1, days - 1);
            r.rect(hx, py, 1.2f, ph, 0xFFFFE08A);
            List<String> lines = new ArrayList<>();
            lines.add(dates != null && d < dates.length ? dates[d] : "Day " + (d + 1));
            if (d > visibleUntil) lines.add("not yet known");
            else for (Series s : series) if (d < s.values.length) lines.add(s.name + "  " + fmt(s.values[d]) + (s.unit.isEmpty() ? "" : " " + s.unit));
            float tw = 0;
            for (String l : lines) tw = Math.max(tw, r.textWidth(l, 12));
            float bw = tw + 22, bh = lines.size() * 17 + 14;
            float bx = hx + 12 + bw > x + w ? hx - 12 - bw : hx + 12, by = Math.max(y + 4, Math.min(my - 10, y + h - bh - 4));
            r.shadow(bx, by + 3, bw, bh, 9, 10, 0x66000000);
            r.roundRect(bx, by, bw, bh, 9, 0xF2101826);
            r.roundRing(bx, by, bw, bh, 9, 1f, BORDER_HI);
            for (int i = 0; i < lines.size(); i++) r.text(lines.get(i), bx + 11, by + 8 + i * 17, 12, i == 0 ? 0xFFFFE08A : TEXT, false, i == 0);
        }
    }

    private void drawSeries(Renderer2D r, Series s, int px, int py, int pw, int ph, double lo, double span) {
        int last = Math.min(visibleUntil, s.values.length - 1);
        if (last < 0) return;
        float prevX = 0, prevY = 0;
        for (int i = 0; i <= last; i++) {
            float vx = px + pw * (float) i / Math.max(1, days - 1);
            float vy = py + ph - (float) Math.max(0, Math.min(1, (s.values[i] - lo) / span)) * ph;
            if (s.area && i > 0) r.areaSegment(prevX, prevY, vx, vy, py + ph, Renderer2D.withAlpha(s.color, 0.50f), Renderer2D.withAlpha(s.color, 0.04f));
            if (i > 0) {
                if (s.step) { r.line(prevX, prevY, vx, prevY, 2f, s.color); r.line(vx, prevY, vx, vy, 2f, s.color); }
                else r.line(prevX, prevY, vx, vy, 2f, s.color);
            }
            prevX = vx; prevY = vy;
        }
        r.circle(prevX, prevY, 3.6f, s.color);
        r.ring(prevX, prevY, 6f, 1.4f, Renderer2D.withAlpha(s.color, 0.5f));
    }
}
