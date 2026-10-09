package org.anthropocene.htf.ui;

import org.anthropocene.htf.gfx.Renderer2D;

import static org.anthropocene.htf.ui.Theme.*;

/** Yes/No dialog. */
public final class ConfirmScreen extends Screen {
    private final String message, yes, no;
    private final Runnable onYes;

    public ConfirmScreen(String message, String yes, String no, Runnable onYes) {
        this.message = message; this.yes = yes; this.no = no; this.onYes = onYes;
    }

    @Override
    protected void init() {
        layoutCard(460, 200);
        Widget.Button b = button(yes, cardX + 32, cardY + cardH - 64, 190, () -> { game.setScreen(parent); onYes.run(); });
        b.danger = true;
        button(no, cardX + cardW - 222, cardY + cardH - 64, 190, () -> game.setScreen(parent));
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        float y = cardY + 34;
        for (String line : r.wrap(message, cardW - 64, 16)) { r.text(line, cardX + 32, y, 16, TEXT); y += 22; }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
