package org.anthropocene.htf.sim;

import java.util.ArrayList;
import java.util.List;

/** Mutable simulation state. Engine methods return copies, so history is easy to replay. */
public final class GameState {
    public record Point(double level, double bund) {}

    public int i;                       // index into season days (the day just simulated)
    public int startIndex;              // index of the transplanting day (history[0])
    public String transplant;           // ISO date the rice was transplanted
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
    // drought scenario
    public double moisture;             // root-zone wetness the crop feels today: NASA soil plus irrigation
    public double boost;                // irrigation still working in the soil
    public double tank = 0;             // irrigation water left
    public double stressLoad;           // accumulated crop stress; the crop fails at 1
    public int stressDays;
    public int irrigations;
    public boolean stressed;            // the crop is suffering today
    public String status = "calm";
    public boolean finished;
    public List<Event> events = new ArrayList<>();
    public List<Point> history = new ArrayList<>();

    public GameState copy() {
        GameState s = new GameState();
        s.i = i; s.startIndex = startIndex; s.transplant = transplant; s.date = date; s.variety = variety; s.coins = coins; s.bund = bund; s.bundRaises = bundRaises;
        s.level = level; s.maturity = maturity; s.alive = alive; s.harvested = harvested; s.yieldPct = yieldPct;
        s.underwaterDays = underwaterDays; s.flooded = flooded;
        s.moisture = moisture; s.boost = boost; s.tank = tank; s.stressLoad = stressLoad; s.stressDays = stressDays; s.irrigations = irrigations; s.stressed = stressed; s.status = status; s.finished = finished;
        s.events = new ArrayList<>(events);
        s.history = new ArrayList<>(history);
        return s;
    }

    /** True once the run is decided: crop saved, lost, or the season ran out. */
    public boolean over() { return finished || harvested || !alive; }
}
