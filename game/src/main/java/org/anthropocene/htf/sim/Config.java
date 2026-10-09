package org.anthropocene.htf.sim;

import java.time.LocalDate;
import java.util.Map;

/**
 * Every tunable number in the simulation, for both scenarios: the haor flash flood ({@link #DEFAULT}) and the
 * Barind drought ({@link #DROUGHT}). Mirrors the web prototype's src/sim/config.js for the flood numbers.
 */
public final class Config {
    public enum Hazard { FLOOD, DROUGHT }

    /**
     * potential = share of the full yield this variety can reach (game parameter, not agronomic advice).
     * stressSoil = root-zone wetness below which the crop is stressed (drought scenario only; lower = more tolerant).
     */
    public record Variety(String label, int fieldDays, double potential, String note, double stressSoil) {
        public Variety(String label, int fieldDays, double potential, String note) { this(label, fieldDays, potential, note, 0); }
    }

    public static final Config DEFAULT = flood(0.0030, 0.85);
    public static final Config DROUGHT = drought();

    public final Hazard hazard;

    // --- Crop ---
    public final LocalDate defaultTransplant;
    public final Map<String, Variety> varieties;
    /** Transplanting choices offered at the start of a season. */
    public final String[][] transplantOptions;
    public final double minHarvestMaturity = 0.8;
    public final int daysUnderwaterToKill = 4;

    // --- Water bucket (flood): level = max(0, level*drain + a*rainUp[t-lag] + b*rainFarm[t] - baseLoss) ---
    public final double a;            // metres per mm of upstream rain
    public final double drain;
    public final double b = 0.0006;   // metres per mm of rain on the farm
    public final double baseLoss = 0.02;
    public final int lagDays = 2;

    // --- Defences (flood) ---
    public final double bundStart = 0.6;
    public final double bundRaise = 0.15;
    public final int bundRaiseCost = 40;
    public final int maxBundRaises = 2;
    public final int startCoins = 100;

    // --- Satellite scout (flood): 3-day upstream rain sum (mm) ---
    public final double watchMm = 100;
    public final double warningMm = 150;

    // --- Drought: NASA root-zone wetness plus irrigation, stress accumulates on dry days ---
    public final int irrigationCost = 12;
    public final double irrigationBoost = 0.28;   // wetness added by one irrigation, fading each day
    public final double boostDecay = 0.70;
    public final double boostMax = 0.45;
    public final double tankStart = 4, tankMax = 6, tankPerMm = 0.05;
    public final double heatC = 37;               // hotter days add stress
    public final double stressRate = 0.11;       // crop-stress load per day of full deficit at normal sensitivity
    // scout (drought): rain over the last 7 days and the NASA soil wetness today
    public final double dryWatchMm = 20, dryWarnMm = 8, soilWatch = 0.76, soilWarn = 0.68;

    private Config(Hazard hazard, double a, double drain, LocalDate defaultTransplant, Map<String, Variety> varieties, String[][] transplantOptions) {
        this.hazard = hazard;
        this.a = a;
        this.drain = drain;
        this.defaultTransplant = defaultTransplant;
        this.varieties = varieties;
        this.transplantOptions = transplantOptions;
    }

    private static Config flood(double a, double drain) {
        return new Config(Hazard.FLOOD, a, drain, LocalDate.parse("2017-01-05"), Map.of(
                "long", new Variety("Long-duration boro", 115, 1.00, "Highest yield, ripens late"),
                "short", new Variety("Short-duration boro", 90, 0.85, "Lower yield, ripens early")),
                new String[][]{{"2016-12-20", "Early (20 Dec)"}, {"2017-01-05", "Usual (5 Jan)"}, {"2017-01-25", "Late (25 Jan)"}});
    }

    private static Config drought() {
        return new Config(Hazard.DROUGHT, 0, 0, LocalDate.parse("2022-07-25"), Map.of(
                "long", new Variety("Standard Aman", 120, 1.00, "Highest yield, needs steady water", 0.50),
                "short", new Variety("Drought-tolerant Aman", 100, 0.88, "Lower yield, copes with dry soil", 0.44)),
                new String[][]{{"2022-07-10", "Early (10 Jul)"}, {"2022-07-25", "Usual (25 Jul)"}, {"2022-08-10", "Late (10 Aug)"}});
    }

    public boolean isDrought() { return hazard == Hazard.DROUGHT; }

    /** The scenario for a hazard name from seasons.json. */
    public static Config forHazard(String hazard) { return "drought".equalsIgnoreCase(hazard) ? DROUGHT : DEFAULT; }

    /** The scenario that goes with a season file. */
    public static Config forSeason(Season s) {
        boolean dry = s != null && ("drought".equalsIgnoreCase(s.hazard) || (s.region != null && s.region.toLowerCase().startsWith("barind")));
        return dry ? DROUGHT : DEFAULT;
    }

    /** Copy with different water constants; used by calibration. */
    public Config withWater(double a, double drain) {
        return flood(a, drain);
    }
}
