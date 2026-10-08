package org.anthropocene.htf.sim;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pure simulation. No rendering, no randomness: same data + same actions = same result. */
public final class Engine {
    private Engine() {}

    public enum ActionType { RAISE_BUND, HARVEST }
    public record ActionResult(GameState state, boolean ok, String reason) {}

    /** Season recap for the end screen. */
    public record Summary(double yieldPct, boolean lost, String warningDate, String floodDate,
                          Integer leadDays, int coinsLeft, int actions) {}

    public static String scoutStatus(List<Day> days, int i, Config cfg) {
        double sum = threeDayUpstream(days, i);
        if (sum >= cfg.warningMm) return "warning";
        if (sum >= cfg.watchMm) return "watch";
        return "calm";
    }

    public static double threeDayUpstream(List<Day> days, int i) {
        double s = 0;
        for (int k = Math.max(0, i - 2); k <= i; k++) s += days.get(k).rainUp();
        return s;
    }

    public static GameState createState(Season data, Config cfg, String variety) {
        GameState s = new GameState();
        s.i = 0;
        s.date = data.day(0).date();
        s.variety = variety;
        s.coins = cfg.startCoins;
        s.bund = cfg.bundStart;
        s.history.add(new GameState.Point(0, cfg.bundStart));
        return s;
    }

    public static double maturityOn(String dayIso, String variety, Config cfg) {
        long d = ChronoUnit.DAYS.between(cfg.transplantDate, LocalDate.parse(dayIso));
        double m = d / (double) cfg.varieties.get(variety).fieldDays();
        return Math.max(0, Math.min(1, m));
    }

    private static int rank(String status) {
        return switch (status) { case "warning" -> 2; case "watch" -> 1; default -> 0; };
    }

    /** Advance one day. Returns a new state. */
    public static GameState step(GameState state, Season data, Config cfg) {
        if (state.finished) return state;
        GameState s = state.copy();
        int i = s.i + 1;
        if (i >= data.length()) {
            s.finished = true;
            if (s.alive && !s.harvested) {
                s.harvested = true;
                s.yieldPct = s.maturity;
                s.events.add(ev(s, "harvest", "Season ended: crop harvested"));
            }
            return s;
        }
        Day day = data.day(i);
        Day lagged = data.day(Math.max(0, i - cfg.lagDays));
        s.i = i;
        s.date = day.date();
        s.level = Math.max(0, s.level * cfg.drain + cfg.a * lagged.rainUp() + cfg.b * day.rainFarm() - cfg.baseLoss);
        s.history.add(new GameState.Point(s.level, s.bund));
        s.status = scoutStatus(data.days, i, cfg);
        if (rank(s.status) > rank(state.status)) {
            s.events.add(ev(s, s.status, s.status.equals("warning")
                    ? "Scout: FLOOD WARNING, heavy rain upstream"
                    : "Scout: watch, rain building upstream"));
        }

        boolean wasFlooded = s.flooded;
        s.flooded = s.level > s.bund;
        if (s.flooded && !wasFlooded) s.events.add(ev(s, "flood", "Flash flood: water over the bund"));

        if (!s.harvested && s.alive) {
            s.maturity = maturityOn(day.date(), s.variety, cfg);
            if (s.flooded) {
                s.underwaterDays += 1;
                if (s.underwaterDays >= cfg.daysUnderwaterToKill) {
                    s.alive = false;
                    s.yieldPct = 0;
                    s.events.add(ev(s, "loss", "Crop lost under water"));
                }
            } else {
                s.underwaterDays = 0;
            }
            if (s.alive && s.maturity >= 1) {
                s.harvested = true;
                s.yieldPct = 1;
                s.events.add(ev(s, "harvest", "Full harvest"));
            }
        }
        if (i == data.length() - 1) return step(s, data, cfg);
        return s;
    }

    /** Player actions. */
    public static ActionResult act(GameState state, ActionType type, Config cfg) {
        GameState s = state.copy();
        switch (type) {
            case RAISE_BUND -> {
                if (s.harvested || !s.alive) return new ActionResult(state, false, "The season is already decided");
                if (s.bundRaises >= cfg.maxBundRaises) return new ActionResult(state, false, "Bund is already at maximum height");
                if (s.coins < cfg.bundRaiseCost) return new ActionResult(state, false, "Not enough coins");
                s.coins -= cfg.bundRaiseCost;
                s.bund += cfg.bundRaise;
                s.bundRaises += 1;
                int last = s.history.size() - 1;
                s.history.set(last, new GameState.Point(s.history.get(last).level(), s.bund));
                s.events.add(ev(s, "action", String.format("Raised bund to %.2f m", s.bund)));
                return new ActionResult(s, true, null);
            }
            case HARVEST -> {
                if (s.harvested || !s.alive) return new ActionResult(state, false, "Nothing to harvest");
                if (s.maturity < cfg.minHarvestMaturity) {
                    return new ActionResult(state, false, String.format("Rice is only %d%% mature (needs %d%%)",
                            Math.round(s.maturity * 100), Math.round(cfg.minHarvestMaturity * 100)));
                }
                s.harvested = true;
                s.yieldPct = s.maturity;
                s.events.add(ev(s, "harvest", "Harvested early at " + Math.round(s.maturity * 100) + "%"));
                return new ActionResult(s, true, null);
            }
        }
        return new ActionResult(state, false, "Unknown action");
    }

    /** Run a whole season with a fixed policy. "rahim" = no actions; "scout" = act on warnings. */
    public static GameState autoplay(Season data, Config cfg, String policy, String variety) {
        GameState s = createState(data, cfg, variety);
        while (!s.finished) {
            s = step(s, data, cfg);
            if (policy.equals("scout") && !s.harvested && s.alive) {
                if (s.status.equals("warning")) {
                    ActionResult h = act(s, ActionType.HARVEST, cfg);
                    if (h.ok()) { s = h.state(); continue; }
                }
                if (!s.status.equals("calm")) {
                    ActionResult r = act(s, ActionType.RAISE_BUND, cfg);
                    if (r.ok()) s = r.state();
                }
            }
        }
        return s;
    }

    public static GameState autoplay(Season data, Config cfg, String policy) {
        return autoplay(data, cfg, policy, policy.equals("rahim") ? "long" : "short");
    }

    public static Summary summarize(GameState state) {
        Event warning = first(state, "warning"), flood = first(state, "flood");
        long actions = state.events.stream()
                .filter(e -> e.type().equals("action") || (e.type().equals("harvest") && e.text().contains("early"))).count();
        return new Summary(state.yieldPct, !state.alive,
                warning == null ? null : warning.date(), flood == null ? null : flood.date(),
                warning != null && flood != null ? flood.i() - warning.i() : null,
                state.coins, (int) actions);
    }

    private static Event first(GameState s, String type) {
        return s.events.stream().filter(e -> e.type().equals(type)).findFirst().orElse(null);
    }

    private static Event ev(GameState s, String type, String text) {
        return new Event(s.i, s.date, type, text);
    }
}
