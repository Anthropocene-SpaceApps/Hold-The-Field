package org.anthropocene.htf.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.anthropocene.htf.core.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Saved worlds. A save is just the choices that define a run plus every action the player took and on which
 * day, because the simulation is deterministic: replaying them reproduces the state exactly.
 */
public final class SaveManager {
    private SaveManager() {}

    public static final class Act {
        public int day;
        public String type;           // RAISE_BUND | HARVEST
        public Act() {}
        public Act(int day, String type) { this.day = day; this.type = type; }
    }

    public static final class SaveData {
        public String id, name, seasonId, variety, mode, plantDate;
        public long created, updated;
        public int day;
        public boolean ended;
        public double yieldPct;
        public List<Act> actions = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file(String id) { return Paths.savesDir().resolve(id + ".json"); }

    public static List<SaveData> list() {
        List<SaveData> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(Paths.savesDir())) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".json"))::iterator) {
                try {
                    SaveData d = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), SaveData.class);
                    if (d != null && d.id != null && d.seasonId != null) out.add(d);
                } catch (Exception e) {
                    System.err.println("Skipping unreadable save " + p.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list saves: " + e.getMessage());
        }
        out.sort(Comparator.comparingLong((SaveData d) -> d.updated).reversed());
        return out;
    }

    public static void save(SaveData d) {
        d.updated = System.currentTimeMillis();
        try {
            Files.writeString(file(d.id), GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save world: " + e.getMessage());
        }
    }

    public static void delete(String id) {
        try { Files.deleteIfExists(file(id)); } catch (IOException e) { System.err.println("Could not delete save: " + e.getMessage()); }
    }

    public static String newId() { return Long.toString(System.currentTimeMillis(), 36); }
}
