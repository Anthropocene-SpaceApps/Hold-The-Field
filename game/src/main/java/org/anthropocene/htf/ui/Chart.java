package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import java.util.ArrayList;
import java.util.List;

/**
 * A small interactive line/area chart over the season's days. Values beyond {@code visibleUntil} are hidden
 * (the scout cannot see the future). Hovering shows a cursor and a tooltip with each series' value.
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
    public int days;
    public String[] dates;                            // for tooltips
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
        r.rect(x, y, w, h, 0xFF000000);
        r.rect(x + 1, y + 1, w - 2, h - 2, 0xE01A1F24);
        r.text(title, x + 5, y + 3, 8.5f, 0xFFFFFFFF, true, true);

        int px = x + 24, py = y + 17, pw = w - 30, ph = h - 29;
        double top = top(), lo = yMin, span = Math.max(1e-9, top - lo);
        // gridlines + y labels
        for (int g = 0; g <= 3; g++) {
            float gy = py + ph - ph * g / 3f;
            r.rect(px, gy, pw, 1, g == 0 ? 0xFF8A8A8A : 0x33FFFFFF);
            String lbl = fmt(lo + span * g / 3);
            r.text(lbl, px - 3 - r.textWidth(lbl, 7), gy - 4, 7, 0xFFB0B0B0);
        }
        // future shading
        if (visibleUntil < days - 1) {
            float fx = px + pw * (visibleUntil + 0.5f) / (days - 1);
            r.rect(fx, py, px + pw - fx, ph, 0x30000000);
        }
        for (HLine hl : hlines) {
            float hy = py + ph - (float) ((hl.value - lo) / span) * ph;
            if (hy < py || hy > py + ph) continue;
            for (float dx = 0; dx < pw; dx += 6) r.rect(px + dx, hy, 3, 1, hl.color);
            r.text(hl.label, px + pw - r.textWidth(hl.label, 7) - 1, hy - 8, 7, hl.color);
        }
        for (Series s : series) drawSeries(r, s, px, py, pw, ph, lo, span);
        int row = 0;
        for (VLine vl : vlines) {
            float vx = px + pw * (float) vl.day / Math.max(1, days - 1);
            r.rect(vx, py, 1.5f, ph, vl.color);
            r.text(vl.label, Math.min(vx + 2, px + pw - r.textWidth(vl.label, 7)), py + 1 + (row++ % 3) * 8, 7, vl.color);
        }

        // today marker
        float tx = px + pw * (float) cursorDay / Math.max(1, days - 1);
        r.rect(tx, py, 1, ph, 0xAAFFFFFF);

        // x labels: first / last date
        if (dates != null && dates.length > 0) {
            r.text(dates[0], px, py + ph + 2, 7, 0xFF909090);
            String last = dates[dates.length - 1];
            r.text(last, px + pw - r.textWidth(last, 7), py + ph + 2, 7, 0xFF909090);
        }

        // hover tooltip
        if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
            int d = Math.max(0, Math.min(days - 1, Math.round((mx - px) / (float) pw * (days - 1))));
            float hx = px + pw * (float) d / Math.max(1, days - 1);
            r.rect(hx, py, 1, ph, 0xFFFFFF55);
            List<String> lines = new ArrayList<>();
            lines.add(dates != null && d < dates.length ? dates[d] : "Day " + (d + 1));
            if (d > visibleUntil) lines.add("(in the future)");
            else for (Series s : series) if (d < s.values.length) lines.add(s.name + ": " + fmt(s.values[d]) + (s.unit.isEmpty() ? "" : " " + s.unit));
            float tw = 0;
            for (String l : lines) tw = Math.max(tw, r.textWidth(l, 8));
            float bx = hx + 8 + tw + 8 > x + w ? hx - 8 - tw - 8 : hx + 8, by = Math.max(y + 2, Math.min(my - 6, y + h - lines.size() * 10 - 8));
            r.rect(bx, by, tw + 8, lines.size() * 10 + 4, 0xF0100010);
            r.border(bx, by, tw + 8, lines.size() * 10 + 4, 1, 0xFF5000A0);
            for (int i = 0; i < lines.size(); i++) r.text(lines.get(i), bx + 4, by + 3 + i * 10, 8, i == 0 ? 0xFFFFFFA0 : 0xFFFFFFFF);
        }
    }

    private void drawSeries(Renderer2D r, Series s, int px, int py, int pw, int ph, double lo, double span) {
        int last = Math.min(visibleUntil, s.values.length - 1);
        if (last < 0) return;
        float prevX = 0, prevY = 0;
        for (int i = 0; i <= last; i++) {
            float vx = px + pw * (float) i / Math.max(1, days - 1);
            float vy = py + ph - (float) Math.max(0, Math.min(1, (s.values[i] - lo) / span)) * ph;
            if (s.area) {
                float nx = i < last ? px + pw * (float) (i + 1) / Math.max(1, days - 1) : vx;
                r.gradientV(vx, vy, Math.max(1, nx - vx + 0.5f), py + ph - vy, Renderer2D.withAlpha(s.color, 0.6f), Renderer2D.withAlpha(s.color, 0.12f));
            }
            if (i > 0) {
                if (s.step) { r.line(prevX, prevY, vx, prevY, 1.6f, s.color); r.line(vx, prevY, vx, vy, 1.6f, s.color); }
                else r.line(prevX, prevY, vx, vy, 1.6f, s.color);
            }
            prevX = vx; prevY = vy;
        }
        r.circle(prevX, prevY, 2, s.color);
    }
}
