package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.List;

/** Menu widgets in the Minecraft style. Coordinates are GUI pixels. */
public abstract class Widget {
    public int x, y, w, h;
    public boolean enabled = true, visible = true;

    public boolean contains(int mx, int my) { return visible && mx >= x && my >= y && mx < x + w && my < y + h; }

    public abstract void render(Renderer2D r, int mx, int my);

    /** Return true if the click was consumed. */
    public boolean mouseDown(int mx, int my) { return false; }
    public void mouseUp(int mx, int my) {}
    public void mouseDragged(int mx, int my) {}
    public boolean keyDown(int key, int mods) { return false; }
    public boolean charTyped(int cp) { return false; }

    public Widget bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }

    // ------------------------------------------------------------------ drawing helpers

    static void panel(Renderer2D r, int x, int y, int w, int h, boolean hover, boolean enabled) {
        int top = !enabled ? 0xFF4A4A4A : hover ? 0xFF6D7DA8 : 0xFF6F6F6F;
        int bottom = !enabled ? 0xFF3B3B3B : hover ? 0xFF55618A : 0xFF5A5A5A;
        int dark = !enabled ? 0xFF2E2E2E : hover ? 0xFF3A466A : 0xFF3B3B3B;
        int light = !enabled ? 0xFF5E5E5E : hover ? 0xFFA9B6E0 : 0xFFB5B5B5;
        r.rect(x, y, w, h, 0xFF000000);
        r.gradientV(x + 1, y + 1, w - 2, h - 2, top, bottom);
        r.rect(x + 1, y + 1, w - 2, 1, light);
        r.rect(x + 1, y + 1, 1, h - 2, light);
        r.rect(x + 1, y + h - 2, w - 2, 1, dark);
        r.rect(x + w - 2, y + 1, 1, h - 2, dark);
        if (hover && enabled) r.border(x, y, w, h, 1, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ Button

    public static class Button extends Widget {
        public String label;
        public Runnable onClick;

        public Button(String label, Runnable onClick) { this.label = label; this.onClick = onClick; }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            if (!visible) return;
            boolean hover = enabled && contains(mx, my);
            panel(r, x, y, w, h, hover, enabled);
            int c = !enabled ? 0xFFA0A0A0 : hover ? 0xFFFFFFA0 : 0xFFFFFFFF;
            float size = Math.min(10, h - 6);
            r.textCentered(label, x + w / 2f, y + (h - size) / 2f - 0.5f, size, c, enabled, false);
        }

        @Override
        public boolean mouseDown(int mx, int my) {
            if (enabled && contains(mx, my)) { if (onClick != null) onClick.run(); return true; }
            return false;
        }
    }

    // ------------------------------------------------------------------ Slider

    public static class Slider extends Widget {
        public final String label;
        public double value;                              // 0..1
        public final Function<Double, String> format;
        public final DoubleConsumer onChange;
        private boolean dragging;

        public Slider(String label, double value, Function<Double, String> format, DoubleConsumer onChange) {
            this.label = label; this.value = value; this.format = format; this.onChange = onChange;
        }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            if (!visible) return;
            boolean hover = enabled && (contains(mx, my) || dragging);
            r.rect(x, y, w, h, 0xFF000000);
            r.rect(x + 1, y + 1, w - 2, h - 2, enabled ? 0xFF2F2F2F : 0xFF222222);
            r.rect(x + 1, y + h - 2, w - 2, 1, 0xFF555555);
            int hx = x + 1 + (int) Math.round(value * (w - 10));
            int base = hover ? 0xFF6D7DA8 : 0xFF6F6F6F;
            r.rect(hx, y + 1, 8, h - 2, 0xFF000000);
            r.gradientV(hx + 1, y + 2, 6, h - 4, base, hover ? 0xFF55618A : 0xFF5A5A5A);
            r.rect(hx + 1, y + 2, 6, 1, hover ? 0xFFA9B6E0 : 0xFFB5B5B5);
            float size = Math.min(10, h - 6);
            r.textCentered(label + ": " + format.apply(value), x + w / 2f, y + (h - size) / 2f - 0.5f, size, hover ? 0xFFFFFFA0 : 0xFFFFFFFF, true, false);
        }

        private void setFrom(int mx) {
            value = Math.max(0, Math.min(1, (mx - x - 5.0) / (w - 10.0)));
            onChange.accept(value);
        }

        @Override public boolean mouseDown(int mx, int my) { if (enabled && contains(mx, my)) { dragging = true; setFrom(mx); return true; } return false; }
        @Override public void mouseDragged(int mx, int my) { if (dragging) setFrom(mx); }
        @Override public void mouseUp(int mx, int my) { dragging = false; }
        @Override public boolean keyDown(int key, int mods) { return false; }
    }

    // ------------------------------------------------------------------ Cycle (toggle / choice)

    public static class Cycle<T> extends Button {
        private final String name;
        private final List<T> options;
        private int index;
        private final Function<T, String> text;
        private final Consumer<T> onChange;

        public Cycle(String name, List<T> options, int index, Function<T, String> text, Consumer<T> onChange) {
            super("", null);
            this.name = name; this.options = options; this.index = Math.max(0, Math.min(options.size() - 1, index));
            this.text = text; this.onChange = onChange;
            this.onClick = this::next;
            refresh();
        }

        private void refresh() { label = name + ": " + text.apply(options.get(index)); }

        public void next() { index = (index + 1) % options.size(); refresh(); onChange.accept(options.get(index)); }
        public T value() { return options.get(index); }
    }

    public static Cycle<Boolean> toggle(String name, boolean value, Consumer<Boolean> onChange) {
        return new Cycle<>(name, List.of(false, true), value ? 1 : 0, b -> b ? "ON" : "OFF", onChange);
    }

    // ------------------------------------------------------------------ Text field

    public static class TextField extends Widget {
        public StringBuilder text = new StringBuilder();
        public boolean focused;
        public int maxLength = 32;
        public String placeholder = "";
        private double blink;

        public TextField(String initial) { text.append(initial); }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            r.rect(x, y, w, h, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
            r.rect(x + 1, y + 1, w - 2, h - 2, 0xFF000000);
            float size = Math.min(10, h - 6);
            String shown = text.toString();
            if (shown.isEmpty() && !focused) r.text(placeholder, x + 4, y + (h - size) / 2f - 0.5f, size, 0xFF707070);
            else r.text(shown, x + 4, y + (h - size) / 2f - 0.5f, size, 0xFFE0E0E0);
            blink += 0.03;
            if (focused && ((int) (blink * 2) & 1) == 0) r.rect(x + 4 + r.textWidth(shown, size), y + 3, 1.5f, h - 6, 0xFFE0E0E0);
        }

        @Override public boolean mouseDown(int mx, int my) { focused = contains(mx, my); return focused; }

        @Override
        public boolean charTyped(int cp) {
            if (!focused || cp < 32 || cp > 255 || text.length() >= maxLength) return false;
            text.appendCodePoint(cp);
            return true;
        }

        @Override
        public boolean keyDown(int key, int mods) {
            if (!focused) return false;
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && text.length() > 0) { text.setLength(text.length() - 1); return true; }
            return false;
        }
    }

    // ------------------------------------------------------------------ Scroll list

    public static class ListBox<T> extends Widget {
        public interface Row<T> { void draw(Renderer2D r, T item, int x, int y, int w, int h, boolean selected, boolean hover); }

        public final List<T> items;
        public int selected = -1;
        public int rowH = 36;
        private double scroll;
        private final Row<T> row;
        public IntConsumer onSelect = i -> {};
        public IntConsumer onDouble = i -> {};
        private long lastClick;
        private int lastIndex = -1;

        public ListBox(List<T> items, Row<T> row) { this.items = items; this.row = row; }

        private double maxScroll() { return Math.max(0, items.size() * rowH - h); }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            r.rect(x, y, w, h, 0xFF000000);
            r.rect(x + 1, y + 1, w - 2, h - 2, 0x99000000);
            r.pushClip(x + 1, y + 1, w - 2, h - 2);
            for (int i = 0; i < items.size(); i++) {
                int ry = (int) (y + i * rowH - scroll);
                if (ry + rowH < y || ry > y + h) continue;
                boolean hover = contains(mx, my) && my >= ry && my < ry + rowH;
                boolean sel = i == selected;
                if (sel) { r.rect(x + 2, ry + 1, w - 4, rowH - 2, 0xFFFFFFFF); r.rect(x + 3, ry + 2, w - 6, rowH - 4, 0xFF000000); }
                else if (hover) r.rect(x + 2, ry + 1, w - 4, rowH - 2, 0x33FFFFFF);
                row.draw(r, items.get(i), x + 6, ry, w - 12, rowH, sel, hover);
            }
            r.popClip();
            if (maxScroll() > 0) {
                float th = Math.max(16, h * h / (float) (items.size() * rowH));
                float ty = y + (float) (scroll / maxScroll()) * (h - th);
                r.rect(x + w - 4, ty, 3, th, 0xFF9A9A9A);
            }
        }

        @Override
        public boolean mouseDown(int mx, int my) {
            if (!contains(mx, my)) return false;
            int i = (int) ((my - y + scroll) / rowH);
            if (i >= 0 && i < items.size()) {
                long now = System.currentTimeMillis();
                if (i == lastIndex && now - lastClick < 400) onDouble.accept(i);
                lastClick = now; lastIndex = i; selected = i; onSelect.accept(i);
            }
            return true;
        }

        public void scrollBy(double dy) { scroll = Math.max(0, Math.min(maxScroll(), scroll - dy * 20)); }
    }
}
