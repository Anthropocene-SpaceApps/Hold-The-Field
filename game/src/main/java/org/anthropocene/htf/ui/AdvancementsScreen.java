package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Advancements;
import org.anthropocene.htf.gfx.Renderer2D;

import static org.anthropocene.htf.ui.Theme.*;

public final class AdvancementsScreen extends Screen {
    @Override
    protected void init() {
        layoutCard(700, 640);
        button("Done", cardX + 32, cardY + cardH - 66, cardW - 64, this::onEscape).primary();
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        Advancements adv = game.advancements();
        heading(r, "Advancements");
        r.textRight(adv.count() + " of " + Advancements.ALL.size() + " earned", cardX + cardW - 32, cardY + 34, 13, MUTED, false);
        int x = cardX + 32, rw = cardW - 64, y = cardY + 92, rowH = 58;
        for (Advancements.Adv a : Advancements.ALL) {
            boolean got = adv.has(a.id());
            r.roundRect(x, y, rw, rowH - 8, 12, got ? 0x24FFC857 : 0x12FFFFFF);
            r.roundRing(x, y, rw, rowH - 8, 12, 1f, got ? 0x88FFC857 : BORDER);
            r.circle(x + 28, y + (rowH - 8) / 2f, 17, got ? 0xFFFFC857 : 0x22FFFFFF);
            Icons.draw(r, got ? Icons.Id.CHECK : Icons.Id.WHEAT, x + 28, y + (rowH - 8) / 2f, 18, got ? 0xFF3A2A00 : FAINT);
            r.text(a.title(), x + 58, y + 7, 15, got ? TEXT : MUTED, false, true);
            r.text(a.description(), x + 58, y + 28, 12, got ? MUTED : FAINT);
            y += rowH;
        }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
