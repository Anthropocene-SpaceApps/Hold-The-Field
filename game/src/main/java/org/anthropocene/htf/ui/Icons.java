package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

/** Vector icons drawn from primitives, so they stay sharp at every size. */
public final class Icons {
    private Icons() {}

    public enum Id { BUND, SICKLE, SATELLITE, CHART, DROP, COIN, RAIN, THERMO, SOIL, PLAY, PAUSE, WHEAT, BOOK, WARN, CHECK }

    public static void draw(Renderer2D r, Id id, float cx, float cy, float s, int c) {
        float u = s / 24f;
        switch (id) {
            case BUND -> {      // stacked earth bricks
                for (int row = 0; row < 3; row++) {
                    float y = cy + (row - 1.5f) * 7.2f * u, off = (row % 2) * 4 * u;
                    r.roundRect(cx - 11 * u + off, y, 10 * u, 6 * u, 1.5f * u, c);
                    r.roundRect(cx + 0.5f * u + off - (row % 2) * 8 * u, y, 10 * u, 6 * u, 1.5f * u, c);
                }
            }
            case SICKLE -> {
                float px = cx - 4 * u, py = cy + 2 * u;
                for (int i = 0; i < 12; i++) {
                    double a0 = Math.PI * (0.05 + i * 0.075), a1 = Math.PI * (0.05 + (i + 1) * 0.075);
                    r.line(px + (float) Math.cos(a0) * 11 * u, py - (float) Math.sin(a0) * 11 * u, px + (float) Math.cos(a1) * 11 * u, py - (float) Math.sin(a1) * 11 * u, 2.6f * u, c);
                }
                r.line(cx - 4 * u, cy + 3 * u, cx - 10 * u, cy + 11 * u, 3.4f * u, Renderer2D.withAlpha(c, 0.75f));
            }
            case SATELLITE -> {
                r.roundRect(cx - 4 * u, cy - 4 * u, 8 * u, 8 * u, 2 * u, c);
                r.roundRect(cx - 12 * u, cy - 3 * u, 6.5f * u, 6 * u, 1 * u, Renderer2D.withAlpha(c, 0.8f));
                r.roundRect(cx + 5.5f * u, cy - 3 * u, 6.5f * u, 6 * u, 1 * u, Renderer2D.withAlpha(c, 0.8f));
                r.line(cx, cy - 4 * u, cx + 3 * u, cy - 9 * u, 1.6f * u, c);
                r.circle(cx + 3.5f * u, cy - 9.5f * u, 1.8f * u, c);
            }
            case CHART -> {
                r.roundRect(cx - 10 * u, cy + 1 * u, 5 * u, 9 * u, 1.2f * u, c);
                r.roundRect(cx - 2.5f * u, cy - 4 * u, 5 * u, 14 * u, 1.2f * u, c);
                r.roundRect(cx + 5 * u, cy - 9 * u, 5 * u, 19 * u, 1.2f * u, c);
            }
            case DROP -> {
                r.triangle(cx, cy - 11 * u, cx - 6.5f * u, cy + 1 * u, cx + 6.5f * u, cy + 1 * u, c);
                r.circle(cx, cy + 3.5f * u, 7 * u, c);
            }
            case COIN -> {
                r.circle(cx, cy, 10 * u, c);
                r.ring(cx, cy, 6 * u, 1.6f * u, 0x66000000);
            }
            case RAIN -> {
                r.circle(cx - 3 * u, cy - 3 * u, 6 * u, c); r.circle(cx + 4 * u, cy - 2 * u, 5 * u, c);
                r.roundRect(cx - 9 * u, cy - 3 * u, 18 * u, 6 * u, 3 * u, c);
                for (int i = -1; i <= 1; i++) r.line(cx + i * 5 * u, cy + 5 * u, cx + i * 5 * u - 2 * u, cy + 10 * u, 1.8f * u, c);
            }
            case THERMO -> {
                r.roundRect(cx - 2 * u, cy - 10 * u, 4 * u, 14 * u, 2 * u, c);
                r.circle(cx, cy + 6 * u, 4.6f * u, c);
            }
            case SOIL -> {
                r.roundRect(cx - 10 * u, cy + 2 * u, 20 * u, 6 * u, 2 * u, c);
                for (int i = -1; i <= 1; i++) r.line(cx + i * 5 * u, cy + 1 * u, cx + i * 5 * u, cy - 7 * u, 2f * u, c);
            }
            case PLAY -> r.triangle(cx - 5 * u, cy - 8 * u, cx - 5 * u, cy + 8 * u, cx + 8 * u, cy, c);
            case PAUSE -> { r.roundRect(cx - 6 * u, cy - 7 * u, 4 * u, 14 * u, 1 * u, c); r.roundRect(cx + 2 * u, cy - 7 * u, 4 * u, 14 * u, 1 * u, c); }
            case WHEAT -> {
                r.line(cx, cy + 11 * u, cx, cy - 8 * u, 1.8f * u, c);
                for (int i = 0; i < 4; i++) {
                    float y = cy - 8 * u + i * 4.2f * u;
                    r.line(cx, y + 3 * u, cx - 5 * u, y - 1 * u, 2.4f * u, c);
                    r.line(cx, y + 3 * u, cx + 5 * u, y - 1 * u, 2.4f * u, c);
                }
            }
            case BOOK -> {
                r.roundRect(cx - 10 * u, cy - 9 * u, 20 * u, 18 * u, 2.5f * u, c);
                for (int i = 0; i < 3; i++) r.rect(cx - 6 * u, cy - 4.5f * u + i * 4.5f * u, 12 * u, 1.6f * u, 0x88000000);
            }
            case WARN -> {
                r.triangle(cx, cy - 10 * u, cx - 10 * u, cy + 8 * u, cx + 10 * u, cy + 8 * u, c);
                r.rect(cx - 1 * u, cy - 3 * u, 2 * u, 7 * u, 0xFF101820);
                r.circle(cx, cy + 6 * u, 1.3f * u, 0xFF101820);
            }
            case CHECK -> {
                r.line(cx - 8 * u, cy + 1 * u, cx - 2 * u, cy + 7 * u, 3 * u, c);
                r.line(cx - 2 * u, cy + 7 * u, cx + 9 * u, cy - 7 * u, 3 * u, c);
            }
        }
    }
}
