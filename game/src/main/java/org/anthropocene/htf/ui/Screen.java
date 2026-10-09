package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Game;
import org.anthropocene.htf.gfx.Renderer2D;

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
    protected int cardX, cardY, cardW, cardH;

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

    public boolean transparentBackground() { return true; }

    public void tick(double dt) {}

    /** Centre a card of the given size (clamped to the window) and remember its bounds. */
    protected void layoutCard(int cw, int ch) {
        cardW = Math.min(cw, w - 32);
        cardH = Math.min(ch, h - 32);
        cardX = (w - cardW) / 2;
        cardY = (h - cardH) / 2;
    }

    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }

    protected void drawCard(Renderer2D r) {
        if (cardW > 0) Theme.card(r, cardX, cardY, cardW, cardH);
    }

    /** Dark veil over the 3D scene so text stays legible. */
    protected void background(Renderer2D r) {
        r.gradientV(0, 0, w, h, game.hasSession() ? 0xC2060B14 : 0xA6060B14, game.hasSession() ? 0xD8060B14 : 0xCC060B14);
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
        b.bounds(x, y, w, 42);
        widgets.add(b);
        return b;
    }

    protected void heading(Renderer2D r, String text) { Theme.heading(r, text, cardX + 32, cardY + 26); }

    protected void open(Screen s) { s.parent = this; game.setScreen(s); }
}
