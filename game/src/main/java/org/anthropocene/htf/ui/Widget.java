package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import static org.anthropocene.htf.ui.Theme.*;

/** Menu widgets: flat rounded controls with hover animation. Coordinates are GUI units. */
public abstract class Widget {
    public int x, y, w, h;
    public boolean enabled = true, visible = true;
    protected float hover;                 // 0..1 animated

    public boolean contains(int mx, int my) { return visible && mx >= x && my >= y && mx < x + w && my < y + h; }

    public abstract void render(Renderer2D r, int mx, int my);

    public boolean mouseDown(int mx, int my) { return false; }
    public void mouseUp(int mx, int my) {}
    public void mouseDragged(int mx, int my) {}
    public boolean keyDown(int key, int mods) { return false; }
    public boolean charTyped(int cp) { return false; }

    public Widget bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }

    protected void animate(boolean over) { hover += ((over && enabled ? 1f : 0f) - hover) * 0.28f; }

    // ------------------------------------------------------------------ Button

    public static class Button extends Widget {
        public String label;
        public Runnable onClick;
        public boolean primary, danger;

        public Button(String label, Runnable onClick) { this.label = label; this.onClick = onClick; }

        public Button primary() { this.primary = true; return this; }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            if (!visible) return;
            animate(contains(mx, my));
            float lift = Theme.ease(hover);
            if (primary) {
                r.shadow(x, y + 3, w, h, 9, 12, Renderer2D.withAlpha(ACCENT, 0.35f * (enabled ? 1 : 0)));
                int top = Renderer2D.lerp(0xFF4B9DF5, 0xFF6DB4FF, lift), bottom = Renderer2D.lerp(0xFF2D70C8, 0xFF3F88E6, lift);
                r.roundGradient(x, y, w, h, 9, enabled ? top : 0xFF3A4A60, enabled ? bottom : 0xFF2E3B4E);
                r.roundRing(x, y, w, h, 9, 1f, Renderer2D.withAlpha(0xFFFFFFFF, enabled ? 0.28f : 0.08f));
            } else {
                int base = danger ? 0x33FF5D5D : 0x24FFFFFF;
                r.roundRect(x, y, w, h, 9, enabled ? Renderer2D.lerp(base, danger ? 0x66FF5D5D : 0x44FFFFFF, lift) : 0x14FFFFFF);
                r.roundRing(x, y, w, h, 9, 1f, Renderer2D.lerp(BORDER, enabled ? BORDER_HI : BORDER, lift));
            }
            int tc = !enabled ? FAINT : TEXT;
            float size = h >= 38 ? 15f : 13f;
            r.textCentered(label, x + w / 2f, y + (h - size) / 2f - 0.5f, size, tc, false, primary);
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
            animate(contains(mx, my) || dragging);
            r.roundRect(x, y, w, h, 9, 0x1AFFFFFF);
            r.text(label, x + 12, y + 7, BODY, enabled ? TEXT : FAINT);
            String v = format.apply(value);
            r.textRight(v, x + w - 12, y + 7, BODY, ACCENT, false);
            float tx = x + 12, tw = w - 24, ty = y + h - 11;
            r.roundRect(tx, ty, tw, 4, 2, 0x33FFFFFF);
            r.roundRect(tx, ty, (float) value * tw, 4, 2, ACCENT);
            float kx = tx + (float) value * tw;
            r.shadow(kx - 8, ty - 6, 16, 16, 8, 6, 0x55000000);
            r.circle(kx, ty + 2, 7 + hover * 1.5f, 0xFFFFFFFF);
        }

        private void setFrom(int mx) {
            value = Math.max(0, Math.min(1, (mx - (x + 12.0)) / (w - 24.0)));
            onChange.accept(value);
        }

        @Override public boolean mouseDown(int mx, int my) { if (enabled && contains(mx, my)) { dragging = true; setFrom(mx); return true; } return false; }
        @Override public void mouseDragged(int mx, int my) { if (dragging) setFrom(mx); }
        @Override public void mouseUp(int mx, int my) { dragging = false; }
    }

    // ------------------------------------------------------------------ Cycle (choice row)

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

        private void refresh() { label = text.apply(options.get(index)); }

        public void next() { index = (index + 1) % options.size(); refresh(); onChange.accept(options.get(index)); }
        public T value() { return options.get(index); }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            if (!visible) return;
            animate(contains(mx, my));
            float lift = Theme.ease(hover);
            r.roundRect(x, y, w, h, 9, enabled ? Renderer2D.lerp(0x1AFFFFFF, 0x30FFFFFF, lift) : 0x0EFFFFFF);
            r.text(name, x + 12, y + (h - BODY) / 2f - 0.5f, BODY, enabled ? TEXT : FAINT);
            String v = label;
            float vw = r.textWidth(v, BODY, true);
            r.text(v, x + w - 28 - vw, y + (h - BODY) / 2f - 0.5f, BODY, enabled ? ACCENT : FAINT, false, true);
            if (enabled) {
                r.line(x + w - 19, y + h / 2f - 4, x + w - 14, y + h / 2f, 1.8f, MUTED);
                r.line(x + w - 14, y + h / 2f, x + w - 19, y + h / 2f + 4, 1.8f, MUTED);
            }
        }
    }

    // ------------------------------------------------------------------ Toggle (switch)

    public static class Toggle extends Widget {
        public final String label;
        public boolean on;
        private final Consumer<Boolean> onChange;
        private float knob;

        public Toggle(String label, boolean on, Consumer<Boolean> onChange) {
            this.label = label; this.on = on; this.onChange = onChange; this.knob = on ? 1 : 0;
        }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            if (!visible) return;
            animate(contains(mx, my));
            knob += ((on ? 1f : 0f) - knob) * 0.3f;
            r.roundRect(x, y, w, h, 9, Renderer2D.lerp(0x1AFFFFFF, 0x30FFFFFF, Theme.ease(hover)));
            r.text(label, x + 12, y + (h - BODY) / 2f - 0.5f, BODY, enabled ? TEXT : FAINT);
            float sw = 40, sh = 22, sx = x + w - sw - 12, sy = y + (h - sh) / 2f;
            r.roundRect(sx, sy, sw, sh, sh / 2, Renderer2D.lerp(0x44FFFFFF, ACCENT, knob));
            r.circle(sx + 11 + knob * (sw - 22), sy + sh / 2f, 8, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseDown(int mx, int my) {
            if (!enabled || !contains(mx, my)) return false;
            on = !on; onChange.accept(on);
            return true;
        }
    }

    public static Toggle toggle(String name, boolean value, Consumer<Boolean> onChange) { return new Toggle(name, value, onChange); }

    // ------------------------------------------------------------------ Choice card (custom content)

    public static class Choice extends Widget {
        public interface Content { void draw(Renderer2D r, int x, int y, int w, int h, boolean selected, float hover); }

        public boolean selected;
        private final Content content;
        private final Runnable onClick;

        public Choice(Content content, Runnable onClick) { this.content = content; this.onClick = onClick; }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            if (!visible) return;
            animate(contains(mx, my));
            float lift = Theme.ease(hover);
            r.roundRect(x, y, w, h, 13, selected ? 0x2A4DA3FF : Renderer2D.lerp(0x16FFFFFF, 0x26FFFFFF, lift));
            r.roundRing(x, y, w, h, 13, selected ? 2f : 1f, selected ? ACCENT : BORDER);
            content.draw(r, x, y, w, h, selected, hover);
        }

        @Override
        public boolean mouseDown(int mx, int my) {
            if (!enabled || !contains(mx, my)) return false;
            if (onClick != null) onClick.run();
            return true;
        }
    }

    // ------------------------------------------------------------------ Text field

    public static class TextField extends Widget {
        public StringBuilder text = new StringBuilder();
        public boolean focused;
        public int maxLength = 32;
        public String placeholder = "";

        public TextField(String initial) { text.append(initial); }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            r.roundRect(x, y, w, h, 9, 0x66000000);
            r.roundRing(x, y, w, h, 9, focused ? 1.6f : 1f, focused ? ACCENT : BORDER);
            float size = 15;
            String shown = text.toString();
            float ty = y + (h - size) / 2f - 0.5f;
            if (shown.isEmpty() && !focused) r.text(placeholder, x + 12, ty, size, FAINT);
            else r.text(shown, x + 12, ty, size, TEXT);
            if (focused && ((int) (Theme.clock * 2) & 1) == 0) r.rect(x + 12 + r.textWidth(shown, size), y + 8, 1.6f, h - 16, TEXT);
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
        public int rowH = 64;
        private double scroll;
        private final Row<T> row;
        public IntConsumer onSelect = i -> {};
        public IntConsumer onDouble = i -> {};
        private long lastClick;
        private int lastIndex = -1;

        public ListBox(List<T> items, Row<T> row) { this.items = items; this.row = row; }

        private double maxScroll() { return Math.max(0, items.size() * (rowH + 6) - h); }

        @Override
        public void render(Renderer2D r, int mx, int my) {
            r.pushClip(x, y, w, h);
            for (int i = 0; i < items.size(); i++) {
                int ry = (int) (y + i * (rowH + 6) - scroll);
                if (ry + rowH < y || ry > y + h) continue;
                boolean hov = contains(mx, my) && my >= ry && my < ry + rowH;
                boolean sel = i == selected;
                r.roundRect(x, ry, w - 8, rowH, 11, sel ? 0x2E4DA3FF : hov ? 0x26FFFFFF : 0x16FFFFFF);
                if (sel) r.roundRing(x, ry, w - 8, rowH, 11, 1.6f, ACCENT);
                row.draw(r, items.get(i), x + 16, ry, w - 32, rowH, sel, hov);
            }
            r.popClip();
            if (maxScroll() > 0) {
                float th = Math.max(24, h * h / (float) (items.size() * (rowH + 6)));
                float ty = y + (float) (scroll / maxScroll()) * (h - th);
                r.roundRect(x + w - 5, ty, 4, th, 2, 0x66FFFFFF);
            }
        }

        @Override
        public boolean mouseDown(int mx, int my) {
            if (!contains(mx, my)) return false;
            int i = (int) ((my - y + scroll) / (rowH + 6));
            if (i >= 0 && i < items.size()) {
                long now = System.currentTimeMillis();
                if (i == lastIndex && now - lastClick < 400) onDouble.accept(i);
                lastClick = now; lastIndex = i; selected = i; onSelect.accept(i);
            }
            return true;
        }

        public void scrollBy(double dy) { scroll = Math.max(0, Math.min(maxScroll(), scroll - dy * 36)); }
    }
}
