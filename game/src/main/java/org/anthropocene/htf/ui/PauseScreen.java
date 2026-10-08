package org.anthropocene.htf.ui;

import org.anthropocene.htf.game.Session;
import org.anthropocene.htf.gfx.Renderer2D;

public final class PauseScreen extends Screen {
    @Override
    protected void init() {
        int cx = w / 2 - 102, y = Math.max(h / 4 + 8, 56);
        button("Back to Game", cx, y, 204, () -> game.setScreen(null));
        button("Scout Dashboard", cx, y + 28, 204, () -> open(new DashboardScreen(game.session())));
        button("Data & Model", cx, y + 52, 100, () -> open(new AboutScreen()));
        button("Advancements", cx + 104, y + 52, 100, () -> open(new AdvancementsScreen()));
        button("Options...", cx, y + 76, 204, () -> open(new OptionsScreen()));
        button("Save and Quit to Title", cx, y + 104, 204, game::quitToTitle);
    }

    private void open(Screen s) { s.parent = this; game.setScreen(s); }

    @Override protected void onEscape() { game.setScreen(null); }

    @Override
    public void onClose() { game.saveSession(); }

    @Override
    public void render(Renderer2D r, int mx, int my) {
        background(r);
        Session s = game.session();
        int top = Math.max(h / 4 + 8, 56);
        title(r, "Game Menu", top - 26);
        if (s != null) r.textCentered(s.name + "  |  " + org.anthropocene.htf.ui.Hud.date(s.state.date), w / 2f, top - 12, 8, 0xFFB0B0B0, true, false);
        for (Widget wd : widgets) wd.render(r, mx, my);
    }
}
