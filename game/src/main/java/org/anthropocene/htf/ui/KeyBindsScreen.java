package org.anthropocene.htf.ui;

import org.anthropocene.htf.core.KeyAction;
import org.anthropocene.htf.core.KeyNames;
import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.Renderer2D;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** Click a binding, press a key. Esc cancels, conflicts are shown in red. */
public final class KeyBindsScreen extends Screen {
    private final List<Widget.Button> buttons = new ArrayList<>();
    private KeyAction listening;

    @Override
    protected void init() {
        buttons.clear();
        KeyAction[] all = KeyAction.values();
        int rows = (all.length + 1) / 2;
        int colW = Math.min(150, (w - 20) / 2 - 6), top = 30, dy = Math.min(24, (h - 76) / rows);
        for (int i = 0; i < all.length; i++) {
            KeyAction a = all[i];
            int col = i / rows, row = i % rows;
            int x = col == 0 ? w / 2 - colW - colW / 2 - 8 : w / 2 + 6 - colW / 2 + colW / 2;
            x = w / 2 - (colW * 2 + 20) / 2 + col * (colW + 20);
            Widget.Button b = new Widget.Button("", () -> { listening = a; refresh(); });
            b.bounds(x + colW / 2 + 4, top + row * dy, colW / 2 + 8, 20);
            buttons.add(b);
            add(b);
        }
        button("Reset Keys", w / 2 - 102, h - 28, 100, () -> { game.settings().resetKeys(); listening = null; refresh(); });
        button("Done", w / 2 + 2, h - 28, 100, this::onEscape);
        refresh();
    }

    private void refresh() {
        Settings s = game.settings();
        KeyAction[] all = KeyAction.values();
        for (int i = 0; i < all.length; i++) {
            Widget.Button b = buttons.get(i);
            KeyAction a = all[i];
            if (a == listening) b.label = "> ? <";
            else {
                String name = KeyNames.of(s.key(a));
                b.label = name.length() > 11 ? name.substring(0, 10) + "." : name;
            }
        }
    }

    private boolean conflict(KeyAction a) {
        Settings s = game.settings();
        for (KeyAction o : KeyAction.values()) if (o != a && s.key(o) == s.key(a)) return true;
        return false;
    }

    @Override
    public boolean keyDown(int key, int mods) {
        if (listening != null) {
            if (key != GLFW_KEY_ESCAPE) game.settings().bind(listening, key);
            listening = null;
            refresh();
            return true;
        }
        return super.keyDown(key, mods);
    }

    @Override
    public boolean mouseDown(int mx, int my, int button) {
        if (listening != null) { listening = null; refresh(); return true; }
        return super.mouseDown(mx, my, button);
    }

    @Override protected void onEscape() { game.settings().save(); game.setScreen(parent); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        title(r, "Key Binds", 11);
        KeyAction[] all = KeyAction.values();
        for (int i = 0; i < all.length; i++) {
            Widget.Button b = buttons.get(i);
            KeyAction a = all[i];
            int colW = Math.min(150, (w - 20) / 2 - 6);
            r.text(a.label, b.x - colW / 2 - 4, b.y + 5, 7.5f, conflict(a) ? 0xFFFF6666 : 0xFFFFFFFF, true, false);
            b.label = b.label;
        }
        for (Widget wd : widgets) wd.render(r, mx, my);
        if (listening != null) r.textCentered("Press a key for \"" + listening.label + "\"  (Esc cancels)", w / 2f, h - 42, 8.5f, 0xFFFFFF55, true, false);
    }
}
