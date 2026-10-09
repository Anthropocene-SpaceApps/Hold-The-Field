package org.anthropocene.htf.game;

import org.anthropocene.htf.core.Paths;
import org.anthropocene.htf.sim.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes a plain-text summary and a CSV of the run to ~/.agrocene/reports so a teacher or extension officer
 * can use the result outside the game.
 */
public final class ReportExporter {
    private ReportExporter() {}

    public static Path export(Session s, GameState baseline, List<Engine.Plan> noWarning, List<Engine.Plan> withScout) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String safe = s.name.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        Path dir = Paths.home().resolve("reports");
        Files.createDirectories(dir);
        Path txt = dir.resolve(safe + "-" + stamp + ".txt");
        Files.writeString(txt, text(s, baseline, noWarning, withScout), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(safe + "-" + stamp + ".csv"), csv(s), StandardCharsets.UTF_8);
        return txt;
    }

    static String text(Session s, GameState baseline, List<Engine.Plan> noWarning, List<Engine.Plan> withScout) {
        GameState st = s.state;
        Engine.Summary sum = Engine.summarize(st);
        Config cfg = s.cfg;
        StringBuilder b = new StringBuilder();
        b.append("AGROCENE - SEASON REPORT\n==============================\n\n");
        b.append("Farm:        ").append(s.name).append('\n');
        b.append("Season:      ").append(s.season.id).append(s.season.sample ? "  (SAMPLE DATA, not real measurements)" : "  (NASA POWER daily data)").append('\n');
        b.append("Mode:        ").append(s.mode.equals(Session.MODE_RAHIM) ? "Rahim's way (no warning, no actions)" : "Scout mode").append('\n');
        b.append("Variety:     ").append(cfg.varieties.get(s.variety).label()).append(", transplanted ").append(st.transplant).append("\n\n");
        b.append("RESULT\n------\n");
        b.append(String.format("Harvest saved:          %d%%%n", Math.round(st.yieldPct * 100)));
        b.append(String.format("Same field, no warning: %d%%%n", Math.round(baseline.yieldPct * 100)));
        b.append(cfg.isDrought() ? "Crop lost to drought:   " : "Crop lost to flood:     ").append(sum.lost() ? "yes" : "no").append('\n');
        if (sum.warningDate() != null) b.append("First scout warning:    ").append(sum.warningDate()).append('\n');
        if (sum.floodDate() != null) b.append(cfg.isDrought() ? "First crop stress:       " : "Water over embankment:  ").append(sum.floodDate()).append('\n');
        if (sum.leadDays() != null) b.append("Warning lead time:      ").append(sum.leadDays()).append(" day(s)\n");
        b.append("Actions taken:          ").append(sum.actions()).append("\n\n");
        b.append("EVERY PLAN ON THE SAME REAL WEATHER (share of the harvest kept)\n----------------------------------------------------------------\n");
        b.append(String.format("%-34s %-14s %-14s%n", "Plan", "No warning", "Acting on scout"));
        for (int i = 0; i < noWarning.size(); i++) {
            Engine.Plan a = noWarning.get(i), c = withScout.get(i);
            b.append(String.format("%-34s %-14s %-14s%n", cfg.varieties.get(a.variety()).label() + ", " + a.label(),
                    Math.round(a.yieldPct() * 100) + "%", Math.round(c.yieldPct() * 100) + "%"));
        }
        b.append("\nHow to read this: it is a learning game built on a simplified model (")
                .append(cfg.isDrought() ? "one soil layer, one rain point,\n" : "one water bucket, one upstream rain point,\n")
                .append("linear crop growth). Yield potentials are game parameters, not agronomic advice. Use it to discuss timing and warning,\n")
                .append("not to predict a real harvest.\n");
        return b.toString();
    }

    static String csv(Session s) {
        GameState st = s.state;
        StringBuilder b = new StringBuilder(s.cfg.isDrought()
                ? "date,rain_upstream_mm,rain_farm_mm,tmax_c,soil_wetness,soil_moisture_with_irrigation,crop_stress_line\n"
                : "date,rain_upstream_mm,rain_farm_mm,tmax_c,soil_wetness,water_level_m,embankment_m\n");
        for (int i = st.startIndex; i <= st.i && i < s.season.length(); i++) {
            Day d = s.season.day(i);
            GameState.Point p = st.history.get(Math.min(st.history.size() - 1, i - st.startIndex));
            b.append(String.format(java.util.Locale.ROOT, "%s,%.1f,%.1f,%.1f,%.3f,%.3f,%.2f%n", d.date(), d.rainUp(), d.rainFarm(), d.tmax(), d.soil(), p.level(), p.bund()));
        }
        return b.toString();
    }
}
