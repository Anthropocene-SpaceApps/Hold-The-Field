package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

/** Colours, type sizes and card drawing shared by every screen. */
public final class Theme {
    private Theme() {}

    public static final int SCRIM = 0xB4060B14, SCRIM_LIGHT = 0x70060B14;
    public static final int PANEL = 0xEA0F1826, PANEL_HI = 0xF21A2638, PANEL_LOW = 0xCC0A121D;
    public static final int BORDER = 0x30FFFFFF, BORDER_HI = 0x66FFFFFF;
    public static final int TEXT = 0xFFEEF4FB, MUTED = 0xFF8FA3BC, FAINT = 0xFF5F7089;
    public static final int ACCENT = 0xFF4DA3FF, ACCENT_DARK = 0xFF2563B0, NASA_RED = 0xFFFC3D21;
    public static final int GOOD = 0xFF5BD18A, WARN = 0xFFFFB84D, BAD = 0xFFFF5D5D;
    public static final int WATER = 0xFF58A9E8, CROP = 0xFFE6BE4B, SOIL = 0xFFC49A6C, RAIN = 0xFF7FB8FF, TEMP = 0xFFFF9A5C;

    public static final float BODY = 14f, SMALL = 12f, LABEL = 11f, H2 = 22f, H1 = 34f;
    public static float clock;           // seconds, advanced by the game each frame (for animation)

    /** Rounded dark glass card with a soft shadow and a hairline border. */
    public static void card(Renderer2D r, float x, float y, float w, float h) { card(r, x, y, w, h, 16, PANEL); }

    public static void card(Renderer2D r, float x, float y, float w, float h, float radius, int fill) {
        r.shadow(x, y + 8, w, h, radius, 26, 0x66000000);
        r.roundRect(x, y, w, h, radius, fill);
        r.roundRing(x, y, w, h, radius, 1f, BORDER);
    }

    public static void chip(Renderer2D r, float x, float y, float w, float h, int fill) {
        r.roundRect(x, y, w, h, h / 2, fill);
    }

    /** Heading with a short accent bar under it. */
    public static void heading(Renderer2D r, String text, float x, float y) {
        r.text(text, x, y, H2, TEXT, false, true);
        r.roundRect(x, y + H2 + 6, 34, 3, 1.5f, ACCENT);
    }

    public static void label(Renderer2D r, String text, float x, float y) { r.text(text.toUpperCase(), x, y, LABEL, MUTED, false, true); }

    public static float ease(float t) { return t * t * (3 - 2 * t); }
}
