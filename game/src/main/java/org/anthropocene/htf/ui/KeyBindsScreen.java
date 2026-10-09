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
        layoutCard(900, 620);
        buttons.clear();
        KeyAction[] all = KeyAction.values();
        int rows = (all.length + 1) / 2, colW = (cardW - 64 - 28) / 2, top = cardY + 92, dy = 46;
        for (int i = 0; i < all.length; i++) {
            KeyAction a = all[i];
            int col = i / rows, row = i % rows;
            int x = cardX + 32 + col * (colW + 28);
            Widget.Button b = new Widget.Button("", () -> { listening = a; refresh(); });
            b.bounds(x + colW - 150, top + row * dy, 150, 38);
            buttons.add(b);
            add(b);
        }
        button("Reset to defaults", cardX + 32, cardY + cardH - 66, 220, () -> { game.settings().resetKeys(); listening = null; refresh(); });
        button("Done", cardX + cardW - 32 - 220, cardY + cardH - 66, 220, this::onEscape).primary();
        refresh();
    }

    private void refresh() {
        Settings s = game.settings();
        KeyAction[] all = KeyAction.values();
        for (int i = 0; i < all.length; i++) {
            Widget.Button b = buttons.get(i);
            b.label = all[i] == listening ? "Press a key..." : KeyNames.of(s.key(all[i]));
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
        drawCard(r);
        heading(r, "Key binds");
        KeyAction[] all = KeyAction.values();
        for (int i = 0; i < all.length; i++) {
            Widget.Button b = buttons.get(i);
            KeyAction a = all[i];
            r.text(a.label, b.x - (cardW - 64 - 28) / 2 + 150, b.y + 11, 14, conflict(a) ? Theme.BAD : Theme.TEXT);
            b.primary = a == listening;
        }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
