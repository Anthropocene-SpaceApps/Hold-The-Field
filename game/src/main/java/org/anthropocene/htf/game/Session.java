package org.anthropocene.htf.game;

import org.anthropocene.htf.sim.*;

import java.util.ArrayList;
import java.util.List;

/** One run of a season: the sim state, the clock, and the recorded actions (for saving). */
public final class Session {
    public static final String MODE_SCOUT = "scout", MODE_RAHIM = "rahim";
    public static final double[] SPEEDS = {1, 2, 4, 8};          // simulated days per real second
    public static final String[] SPEED_LABELS = {"1x", "2x", "4x", "8x"};

    public final Season season;
    public final Config cfg = Config.DEFAULT;
    public final String mode, variety;
    public GameState state;
    public String id, name;
    public long created;
    public int speedIdx = 0;
    public boolean timePaused = true;
    public final List<SaveManager.Act> actions = new ArrayList<>();
    private double acc;
    private int processedEvents;
    public boolean endShown;

    public Session(Season season, String mode, String variety, String name) {
        this.season = season;
        this.mode = mode;
        this.variety = mode.equals(MODE_RAHIM) ? "long" : variety;
        this.name = name;
        this.id = SaveManager.newId();
        this.created = System.currentTimeMillis();
        this.state = Engine.createState(season, cfg, this.variety);
    }

    public boolean actionsAllowed() { return mode.equals(MODE_SCOUT); }

    public static Session restore(Season season, SaveManager.SaveData d) {
        Session s = new Session(season, d.mode, d.variety, d.name);
        s.id = d.id;
        s.created = d.created;
        s.actions.addAll(d.actions);
        for (int day = 0; day < d.day && !s.state.finished; day++) {
            s.replayActionsOn(day);
            s.state = Engine.step(s.state, season, s.cfg);
        }
        s.replayActionsOn(d.day);
        s.processedEvents = s.state.events.size();     // old events are history, not news
        s.endShown = d.ended;
        return s;
    }

    private void replayActionsOn(int day) {
        for (SaveManager.Act a : actions) {
            if (a.day != day) continue;
            Engine.ActionResult r = Engine.act(state, Engine.ActionType.valueOf(a.type), cfg);
            if (r.ok()) state = r.state();
        }
    }

    public SaveManager.SaveData toSave() {
        SaveManager.SaveData d = new SaveManager.SaveData();
        d.id = id; d.name = name; d.seasonId = season.id; d.variety = variety; d.mode = mode;
        d.created = created; d.day = state.i; d.ended = endShown; d.yieldPct = state.yieldPct;
        d.actions = new ArrayList<>(actions);
        return d;
    }

    public void speedUp() { speedIdx = Math.min(SPEEDS.length - 1, speedIdx + 1); }
    public void speedDown() { speedIdx = Math.max(0, speedIdx - 1); }

    public void tick(double dt) {
        if (timePaused || state.over()) return;
        acc += dt * SPEEDS[speedIdx];
        while (acc >= 1 && !state.over()) {
            acc -= 1;
            state = Engine.step(state, season, cfg);
        }
    }

    public Engine.ActionResult act(Engine.ActionType type) {
        if (!actionsAllowed()) return new Engine.ActionResult(state, false, "Rahim's way: you can only watch this season");
        Engine.ActionResult r = Engine.act(state, type, cfg);
        if (r.ok()) {
            state = r.state();
            actions.add(new SaveManager.Act(state.i, type.name()));
        }
        return r;
    }

    /** Events produced since the last call. */
    public List<Event> drainEvents() {
        List<Event> fresh = new ArrayList<>(state.events.subList(processedEvents, state.events.size()));
        processedEvents = state.events.size();
        return fresh;
    }

    public GameState baseline() {
        return Engine.autoplay(season, cfg, mode.equals(MODE_RAHIM) ? "scout" : "rahim");
    }
}
