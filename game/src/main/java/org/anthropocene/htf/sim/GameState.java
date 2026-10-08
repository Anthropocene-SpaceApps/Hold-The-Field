package org.anthropocene.htf.sim;

import java.util.ArrayList;
import java.util.List;

/** Mutable simulation state. Engine methods return copies, so history is easy to replay. */
public final class GameState {
    public record Point(double level, double bund) {}

    public int i;                       // index into season days (the day just simulated)
    public String date;
    public String variety;
    public int coins;
    public double bund;
    public int bundRaises;
    public double level;
    public double maturity;
    public boolean alive = true;
    public boolean harvested;
    public double yieldPct;
    public int underwaterDays;
    public boolean flooded;
    public String status = "calm";
    public boolean finished;
    public List<Event> events = new ArrayList<>();
    public List<Point> history = new ArrayList<>();

    public GameState copy() {
        GameState s = new GameState();
        s.i = i; s.date = date; s.variety = variety; s.coins = coins; s.bund = bund; s.bundRaises = bundRaises;
        s.level = level; s.maturity = maturity; s.alive = alive; s.harvested = harvested; s.yieldPct = yieldPct;
        s.underwaterDays = underwaterDays; s.flooded = flooded; s.status = status; s.finished = finished;
        s.events = new ArrayList<>(events);
        s.history = new ArrayList<>(history);
        return s;
    }

    /** True once the run is decided: crop saved, lost, or the season ran out. */
    public boolean over() { return finished || harvested || !alive; }
}
