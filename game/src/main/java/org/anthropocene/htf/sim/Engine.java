package org.anthropocene.htf.sim;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pure simulation. No rendering, no randomness: same data + same actions = same result. */
public final class Engine {
    private Engine() {}

    public enum ActionType { RAISE_BUND, HARVEST, IRRIGATE }
    public record ActionResult(GameState state, boolean ok, String reason) {}

    /** Season recap for the end screen. */
    public record Summary(double yieldPct, boolean lost, String warningDate, String floodDate,
                          Integer leadDays, int coinsLeft, int actions) {}

    public static String scoutStatus(List<Day> days, int i, Config cfg) {
        if (cfg.isDrought()) {
            double rain7 = recentRain(days, i, 7), soil = days.get(i).soil();
            if (rain7 < cfg.dryWarnMm && soil < cfg.soilWarn) return "warning";
            if (rain7 < cfg.dryWatchMm && soil < cfg.soilWatch) return "watch";
            return "calm";
        }
        double sum = threeDayUpstream(days, i);
        if (sum >= cfg.warningMm) return "warning";
        if (sum >= cfg.watchMm) return "watch";
        return "calm";
    }

    /** Rain on the farm over the last n days (drought scout). */
    public static double recentRain(List<Day> days, int i, int n) {
        double s = 0;
        for (int k = Math.max(0, i - n + 1); k <= i; k++) s += days.get(k).rainFarm();
        return s;
    }

    /** Variety-specific soil wetness below which the crop suffers (drought scenario). */
    public static double stressThreshold(Config cfg, String variety) { return cfg.varieties.get(variety).stressSoil(); }

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
        if (cfg.isDrought()) {
            s.tank = cfg.tankStart;
            s.moisture = data.day(s.i).soil();
            s.history.set(0, new GameState.Point(s.moisture, stressThreshold(cfg, variety)));
        }
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
                s.yieldPct = cfg.isDrought() ? droughtYield(s, cfg) : s.maturity * cfg.varieties.get(s.variety).potential();
                s.events.add(ev(s, "harvest", "Season ended: crop harvested"));
            }
            return s;
        }
        Day day = data.day(i);
        Day lagged = data.day(Math.max(0, i - cfg.lagDays));
        s.i = i;
        s.date = day.date();
        if (cfg.isDrought()) return stepDrought(s, state, data, cfg, day, i);
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


    /** One day of the drought scenario. */
    private static GameState stepDrought(GameState s, GameState prev, Season data, Config cfg, Day day, int i) {
        s.boost *= cfg.boostDecay;
        s.tank = Math.min(cfg.tankMax, s.tank + cfg.tankPerMm * day.rainFarm());
        s.moisture = Math.max(0, Math.min(1, day.soil() + s.boost));
        double thr = stressThreshold(cfg, s.variety);
        s.history.add(new GameState.Point(s.moisture, thr));
        s.status = scoutStatus(data.days, i, cfg);
        if (rank(s.status) > rank(prev.status) && !announcedRecently(s, s.status, 12)) {
            s.events.add(ev(s, s.status, s.status.equals("warning")
                    ? "Scout: DROUGHT WARNING, the rain has stopped and the soil is drying"
                    : "Scout: watch, rain is thinning out and the soil is drying"));
        }
        if (!s.harvested && s.alive) {
            s.maturity = maturityOn(day.date(), s.variety, s.transplant, cfg);
            double deficit = Math.max(0, thr - s.moisture) / thr;
            s.stressed = deficit > 0;
            if (s.stressed) {
                // flowering and grain filling (55% to 90% maturity) are the thirsty weeks; ripening hardly cares
                double sensitivity = s.maturity > 0.9 ? 0.3 : s.maturity >= 0.55 ? 2.0 : s.maturity >= 0.35 ? 1.3 : 1.0;
                double heat = day.tmax() > cfg.heatC ? 1.5 : 1.0;     // heat on top of dry soil hurts more
                if (s.stressDays == 0) s.events.add(ev(s, "stress", "Crop stress: the soil is too dry for the rice"));
                s.stressDays += 1;
                s.stressLoad += sensitivity * heat * deficit * cfg.stressRate;
            }
            if (s.stressLoad >= 1) {
                s.alive = false;
                s.yieldPct = 0;
                s.events.add(ev(s, "loss", "Crop failed in the drought"));
            }
            if (s.alive && s.maturity >= 1) {
                s.harvested = true;
                s.yieldPct = droughtYield(s, cfg);
                s.events.add(ev(s, "harvest", "Full harvest"));
            }
        }
        if (i == data.length() - 1) return step(s, data, cfg);
        return s;
    }

    /** True if the scout already raised this alert within the last n days (a flickering index must not nag). */
    private static boolean announcedRecently(GameState s, String type, int n) {
        for (int k = s.events.size() - 1; k >= 0; k--) {
            Event e = s.events.get(k);
            if (s.i - e.i() > n) break;
            if (e.type().equals(type)) return true;
        }
        return false;
    }

    /** Harvest share: maturity x variety potential x what the dry weeks left of the crop. */
    private static double droughtYield(GameState s, Config cfg) {
        return s.maturity * cfg.varieties.get(s.variety).potential() * Math.max(0, 1 - s.stressLoad);
    }

    /** Player actions. */
    public static ActionResult act(GameState state, ActionType type, Config cfg) {
        GameState s = state.copy();
        switch (type) {
            case RAISE_BUND -> {
                if (cfg.isDrought()) return new ActionResult(state, false, "Unknown action");
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
            case IRRIGATE -> {
                if (!cfg.isDrought()) return new ActionResult(state, false, "Unknown action");
                if (s.harvested || !s.alive) return new ActionResult(state, false, "The season is already decided");
                if (s.tank < 1) return new ActionResult(state, false, "The water tank is empty");
                if (s.coins < cfg.irrigationCost) return new ActionResult(state, false, "Not enough coins");
                s.coins -= cfg.irrigationCost;
                s.tank -= 1;
                s.irrigations += 1;
                s.boost = Math.min(cfg.boostMax, s.boost + cfg.irrigationBoost);
                s.moisture = Math.min(1, s.moisture + cfg.irrigationBoost);
                int last = s.history.size() - 1;
                s.history.set(last, new GameState.Point(s.moisture, stressThreshold(cfg, s.variety)));
                s.events.add(ev(s, "action", "Irrigated the field, " + Math.round(s.tank) + " tank loads left"));
                return new ActionResult(s, true, null);
            }
            case HARVEST -> {
                if (s.harvested || !s.alive) return new ActionResult(state, false, "Nothing to harvest");
                if (s.maturity < cfg.minHarvestMaturity) {
                    return new ActionResult(state, false, String.format("Rice is only %d%% mature (needs %d%%)",
                            Math.round(s.maturity * 100), Math.round(cfg.minHarvestMaturity * 100)));
                }
                s.harvested = true;
                s.yieldPct = cfg.isDrought() ? droughtYield(s, cfg) : s.maturity * cfg.varieties.get(s.variety).potential();
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
            if (policy.equals("scout") && !s.harvested && s.alive && cfg.isDrought()) {
                // irrigate when the scout is worried and the soil is about to cross the crop's stress line
                if (!s.status.equals("calm") && s.moisture < stressThreshold(cfg, s.variety) + 0.14) {
                    ActionResult r = act(s, ActionType.IRRIGATE, cfg);
                    if (r.ok()) s = r.state();
                }
            } else if (policy.equals("scout") && !s.harvested && s.alive) {
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
        if (flood == null) flood = first(state, "stress");       // drought: the first day the crop felt it
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
