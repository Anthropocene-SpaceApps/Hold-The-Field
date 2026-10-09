package org.anthropocene.htf.sim;

import java.time.LocalDate;
import java.util.Map;

/** Every tunable number in the simulation. Mirrors the web prototype's src/sim/config.js. */
public final class Config {
    /** potential = share of the full yield this variety can reach (game parameter, not agronomic advice). */
    public record Variety(String label, int fieldDays, double potential, String note) {}

    public static final Config DEFAULT = new Config(0.0011, 0.8);

    // --- Crop ---
    public final LocalDate defaultTransplant = LocalDate.parse("2017-01-05");
    public final Map<String, Variety> varieties = Map.of(
            "long", new Variety("Long-duration boro", 115, 1.00, "Highest yield, ripens late"),
            "short", new Variety("Short-duration boro", 90, 0.85, "Lower yield, ripens early"));
    /** Transplanting choices offered at the start of a season. */
    public final String[][] transplantOptions = {{"2016-12-20", "Early (20 Dec)"}, {"2017-01-05", "Usual (5 Jan)"}, {"2017-01-25", "Late (25 Jan)"}};
    public final double minHarvestMaturity = 0.8;
    public final int daysUnderwaterToKill = 4;

    // --- Water bucket: level = max(0, level*drain + a*rainUp[t-lag] + b*rainFarm[t] - baseLoss) ---
    public final double a;            // metres per mm of upstream rain
    public final double drain;
    public final double b = 0.0006;   // metres per mm of rain on the farm
    public final double baseLoss = 0.02;
    public final int lagDays = 2;

    // --- Defences ---
    public final double bundStart = 0.6;
    public final double bundRaise = 0.15;
    public final int bundRaiseCost = 40;
    public final int maxBundRaises = 2;
    public final int startCoins = 100;

    // --- Satellite scout: 3-day upstream rain sum (mm) ---
    public final double watchMm = 120;
    public final double warningMm = 250;

    public Config(double a, double drain) {
        this.a = a;
        this.drain = drain;
    }

    /** Copy with different water constants; used by calibration. */
    public Config withWater(double a, double drain) {
        return new Config(a, drain);
    }
}
