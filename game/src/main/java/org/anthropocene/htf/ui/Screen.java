package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Game;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.gfx.Tile;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/** A menu or overlay. Opened on top of the game; closing returns to {@link #parent}. */
public abstract class Screen {
    protected Game game;
    public Screen parent;
    protected final List<Widget> widgets = new ArrayList<>();
    protected Widget pressed;
    protected int w, h;                      // GUI size, set before init()

    public final void open(Game game, int guiW, int guiH) {
        this.game = game;
        resize(guiW, guiH);
    }

    public void resize(int guiW, int guiH) {
        this.w = guiW; this.h = guiH;
        widgets.clear();
        init();
    }

    protected abstract void init();

    protected <T extends Widget> T add(T widget) { widgets.add(widget); return widget; }

    /** Time stands still while a screen is open unless the screen says otherwise. */
    public boolean pausesGame() { return true; }

    /** Whether to draw the 3D world behind this screen's own background. */
    public boolean transparentBackground() { return game.hasSession(); }

    public void tick(double dt) {}

    public void render(Renderer2D r, int mx, int my) {
        background(r);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }

    /** Default background: dirt over the title panorama, or a dark veil over the game. */
    protected void background(Renderer2D r) {
        if (game.hasSession()) {
            r.gradientV(0, 0, w, h, 0xC0101010, 0xD0101010);
        } else {
            r.tileRepeat(Tile.DIRT, 0, 0, w, h, 32, 0xFF404040);
        }
    }

    public boolean mouseDown(int mx, int my, int button) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) return false;
        for (Widget wd : widgets) wd.mouseDown(mx, my);   // text fields need to lose focus too
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget wd = widgets.get(i);
            if (wd.visible && wd.contains(mx, my)) { pressed = wd; game.audio().click(); return true; }
        }
        return false;
    }

    public void mouseUp(int mx, int my, int button) {
        if (pressed != null) { pressed.mouseUp(mx, my); pressed = null; }
        for (Widget wd : widgets) wd.mouseUp(mx, my);
    }

    public void mouseMoved(int mx, int my) {
        if (pressed != null) pressed.mouseDragged(mx, my);
    }

    public void scroll(int mx, int my, double dy) {
        for (Widget wd : widgets) if (wd instanceof Widget.ListBox<?> lb && lb.contains(mx, my)) lb.scrollBy(dy);
    }

    public boolean keyDown(int key, int mods) {
        for (Widget wd : widgets) if (wd.keyDown(key, mods)) return true;
        if (key == GLFW_KEY_ESCAPE) { onEscape(); return true; }
        return false;
    }

    public void charTyped(int cp) { for (Widget wd : widgets) if (wd.charTyped(cp)) return; }

    protected void onEscape() { game.setScreen(parent); }

    public void onClose() {}

    // ------------------------------------------------------------------ layout helpers

    protected Widget.Button button(String label, int x, int y, int w, Runnable action) {
        Widget.Button b = new Widget.Button(label, action);
        b.bounds(x, y, w, 20);
        widgets.add(b);
        return b;
    }

    protected void title(Renderer2D r, String text, int y) {
        r.textCentered(text, w / 2f, y, 14, 0xFFFFFFFF, true, true);
    }
}
