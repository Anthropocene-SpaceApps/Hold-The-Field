package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

/** Yes/No dialog. */
public final class ConfirmScreen extends Screen {
    private final String message, yes, no;
    private final Runnable onYes;

    public ConfirmScreen(String message, String yes, String no, Runnable onYes) {
        this.message = message; this.yes = yes; this.no = no; this.onYes = onYes;
    }

    @Override
    protected void init() {
        button(yes, w / 2 - 102, h / 2 + 10, 100, () -> { game.setScreen(parent); onYes.run(); });
        button(no, w / 2 + 2, h / 2 + 10, 100, () -> game.setScreen(parent));
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        int y = h / 2 - 28;
        for (String line : r.wrap(message, Math.min(320, w - 40), 9)) { r.textCentered(line, w / 2f, y, 9, 0xFFFFFFFF, true, false); y += 11; }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
