package org.anthropocene.htf.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.anthropocene.htf.core.Paths;
import org.anthropocene.htf.sim.Engine;
import org.anthropocene.htf.sim.GameState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Minecraft-style advancements, saved in ~/.hold-the-field/profile.json. */
public final class Advancements {
    public record Adv(String id, String title, String description) {}

    public static final List<Adv> ALL = List.of(
            new Adv("eyes", "Eyes in the Sky", "See your first Satellite Scout warning"),
            new Adv("wall", "Raise the Wall", "Raise the bund with mud bricks"),
            new Adv("ahead", "Ahead of the Water", "Bring in the harvest before the flood arrives"),
            new Adv("dry", "Dry Feet", "Survive a flash flood with your crop standing"),
            new Adv("revenge", "Better Than Rahim's Way", "Save more rice than the farmer who gets no warning"),
            new Adv("lesson", "A Hard Lesson", "Lose a crop to the flood or the drought"),
            new Adv("thirst", "Quench the Field", "Irrigate your rice from the village tank"),
            new Adv("drops", "Every Drop Counts", "Bring the rice through a dry spell and keep most of the harvest"),
            new Adv("full", "Golden Season", "Harvest every last grain"));

    private static class Profile { Set<String> unlocked = new LinkedHashSet<>(); }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Profile profile = load();

    private static Path file() { return Paths.home().resolve("profile.json"); }

    private static Profile load() {
        try {
            if (Files.isRegularFile(file())) {
                Profile p = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Profile.class);
                if (p != null && p.unlocked != null) return p;
            }
        } catch (Exception e) { System.err.println("Could not read profile.json: " + e.getMessage()); }
        return new Profile();
    }

    public boolean has(String id) { return profile.unlocked.contains(id); }

    /** Unlock if new. Returns the advancement when it was newly earned, otherwise null. */
    public Adv grant(String id) {
        if (profile.unlocked.contains(id)) return null;
        profile.unlocked.add(id);
        try { Files.writeString(file(), GSON.toJson(profile), StandardCharsets.UTF_8); }
        catch (IOException e) { System.err.println("Could not save profile: " + e.getMessage()); }
        return ALL.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    public int count() { return profile.unlocked.size(); }

    /** Advancements earned at the moment of a sim event. */
    public List<Adv> onEvent(String type, GameState s, boolean drought) {
        List<Adv> got = new ArrayList<>();
        switch (type) {
            case "warning" -> add(got, grant("eyes"));
            case "action" -> add(got, grant(drought ? "thirst" : "wall"));
            case "loss" -> add(got, grant("lesson"));
            case "harvest" -> {
                boolean floodedYet = s.events.stream().anyMatch(e -> e.type().equals("flood"));
                boolean warned = s.events.stream().anyMatch(e -> e.type().equals("warning"));
                if (!drought && !floodedYet && warned && s.yieldPct > 0) add(got, grant("ahead"));
                if (s.yieldPct >= 0.999) add(got, grant("full"));
            }
            default -> { }
        }
        return got;
    }

    /** Advancements decided only once the run is over. */
    public List<Adv> onEnd(GameState s, GameState baseline, boolean scoutMode, boolean drought) {
        List<Adv> got = new ArrayList<>();
        Engine.Summary sum = Engine.summarize(s);
        if (drought) { if (s.alive && s.stressDays > 0 && s.yieldPct >= 0.6) add(got, grant("drops")); }
        else if (s.alive && sum.floodDate() != null) add(got, grant("dry"));
        if (scoutMode && s.yieldPct > baseline.yieldPct) add(got, grant("revenge"));
        return got;
    }

    private static void add(List<Adv> l, Adv a) { if (a != null) l.add(a); }
}
