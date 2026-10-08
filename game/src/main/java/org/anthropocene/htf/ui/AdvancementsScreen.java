package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Advancements;
import org.anthropocene.htf.gfx.Renderer2D;
import org.anthropocene.htf.gfx.Tile;

public final class AdvancementsScreen extends Screen {
    @Override
    protected void init() {
        button("Done", w / 2 - 100, h - 30, 200, this::onEscape);
    }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        Advancements adv = game.advancements();
        title(r, "Advancements", 10);
        r.textCentered(adv.count() + " / " + Advancements.ALL.size() + " completed", w / 2f, 27, 8, 0xFFB0B0B0, true, false);
        int rw = Math.min(330, w - 20), x = w / 2 - rw / 2, y = 42, rowH = Math.min(26, (h - 84) / Advancements.ALL.size());
        for (Advancements.Adv a : Advancements.ALL) {
            boolean got = adv.has(a.id());
            r.rect(x, y, rw, rowH - 2, got ? 0xFF000000 : 0xB0000000);
            r.border(x, y, rw, rowH - 2, 1, got ? 0xFFE0C040 : 0xFF444444);
            r.rect(x + 3, y + 3, rowH - 8, rowH - 8, got ? 0xFF6A5A1A : 0xFF303030);
            r.tile(Tile.ICON_WHEAT, x + 4, y + 4, rowH - 10, rowH - 10, got ? 0xFFFFFFFF : 0x55FFFFFF);
            r.text(a.title(), x + rowH + 2, y + 2, 8.5f, got ? 0xFFFFFF77 : 0xFF909090, true, true);
            r.text(a.description(), x + rowH + 2, y + 12, 7.5f, got ? 0xFFDDDDDD : 0xFF707070, true, false);
            y += rowH;
        }
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
