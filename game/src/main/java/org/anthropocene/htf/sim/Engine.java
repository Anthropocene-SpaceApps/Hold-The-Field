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
        return createState(data, cfg, variety, cfg.defaultTransplant.toString());
    }

    /** First day at or after the requested date that the data covers. */
    public static int indexOnOrAfter(Season data, String iso) {
        for (int k = 0; k < data.length(); k++) if (data.day(k).date().compareTo(iso) >= 0) return k;
        return data.length() - 1;
    }

    public static GameState createState(Season data, Config cfg, String variety, String transplantIso) {
        GameState s = new GameState();
        s.startIndex = indexOnOrAfter(data, transplantIso);
        s.transplant = data.day(s.startIndex).date();
        s.i = s.startIndex;
        s.date = data.day(s.i).date();
        s.variety = variety;
        s.coins = cfg.startCoins;
        s.bund = cfg.bundStart;
        s.history.add(new GameState.Point(0, cfg.bundStart));
        return s;
    }

    public static double maturityOn(String dayIso, String variety, Config cfg) {
        return maturityOn(dayIso, variety, cfg.defaultTransplant.toString(), cfg);
    }

    public static double maturityOn(String dayIso, String variety, String transplantIso, Config cfg) {
        long d = ChronoUnit.DAYS.between(LocalDate.parse(transplantIso), LocalDate.parse(dayIso));
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
                s.yieldPct = s.maturity * cfg.varieties.get(s.variety).potential();
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
            s.maturity = maturityOn(day.date(), s.variety, s.transplant, cfg);
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
                s.yieldPct = cfg.varieties.get(s.variety).potential();
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
                s.yieldPct = s.maturity * cfg.varieties.get(s.variety).potential();
                s.events.add(ev(s, "harvest", "Harvested early at " + Math.round(s.maturity * 100) + "% maturity"));
                return new ActionResult(s, true, null);
            }
        }
        return new ActionResult(state, false, "Unknown action");
    }

    /** Run a whole season with a fixed policy. "rahim" = no actions; "scout" = act on warnings. */
    public static GameState autoplay(Season data, Config cfg, String policy, String variety) {
        return autoplay(data, cfg, policy, variety, cfg.defaultTransplant.toString());
    }

    public static GameState autoplay(Season data, Config cfg, String policy, String variety, String transplantIso) {
        GameState s = createState(data, cfg, variety, transplantIso);
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

    /** One cell of the planting-window analysis: what a plan yields if the farmer takes no action at all. */
    public record Plan(String variety, String transplant, String label, double yieldPct, boolean lost) {}

    /** Try every variety and transplanting option on the same real weather, with no defences, no warning. */
    public static java.util.List<Plan> planningWindow(Season data, Config cfg) { return planningWindow(data, cfg, "rahim"); }

    /** policy "rahim" = no warning, no action; "scout" = act on the satellite warning. */
    public static java.util.List<Plan> planningWindow(Season data, Config cfg, String policy) {
        java.util.List<Plan> out = new java.util.ArrayList<>();
        for (String variety : new String[]{"short", "long"}) for (String[] opt : cfg.transplantOptions) {
            if (data.day(0).date().compareTo(opt[0]) > 0) continue;
            GameState s = autoplay(data, cfg, policy, variety, opt[0]);
            out.add(new Plan(variety, opt[0], opt[1], s.yieldPct, !s.alive));
        }
        return out;
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
