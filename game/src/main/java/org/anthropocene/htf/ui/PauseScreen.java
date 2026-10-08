package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;

public final class PauseScreen extends Screen {
    @Override
    protected void init() {
        layoutCard(420, 480);
        int x = cardX + 32, rw = cardW - 64, y = cardY + 118;
        button("Resume", x, y, rw, () -> game.setScreen(null)).primary();
        button("Scout dashboard", x, y + 54, rw, () -> open(new DashboardScreen(game.session())));
        button("Data and model", x, y + 102, rw, () -> open(new AboutScreen()));
        button("Advancements", x, y + 150, rw, () -> open(new AdvancementsScreen()));
        button("Options", x, y + 198, rw, () -> open(new OptionsScreen()));
        Widget.Button quit = button("Save and quit to title", x, y + 262, rw, game::quitToTitle);
        quit.danger = true;
    }

    @Override protected void onEscape() { game.setScreen(null); }

    @Override
    public void onClose() { game.saveSession(); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        drawCard(r);
        heading(r, "Paused");
        Session s = game.session();
        if (s != null) r.text(s.name + "   |   " + Hud.date(s.state.date), cardX + 32, cardY + 78, 13, Theme.MUTED);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
