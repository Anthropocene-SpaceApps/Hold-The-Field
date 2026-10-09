package org.anthropocene.htf.sim;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The playable seasons listed in seasons.json, with loaded data cached. */
public final class SeasonCatalog {
    public record Entry(String id, String name, String blurb, String hazard, String tag) {}

    private static List<Entry> entries;
    private static final Map<String, Season> cache = new HashMap<>();

    private SeasonCatalog() {}

    public static synchronized List<Entry> all() {
        if (entries == null) {
            try (InputStream in = SeasonCatalog.class.getResourceAsStream("/seasons.json")) {
                if (in == null) throw new IOException("seasons.json missing");
                entries = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<List<Entry>>() {}.getType());
            } catch (IOException e) {
                throw new IllegalStateException("Could not read seasons.json", e);
            }
        }
        return entries;
    }

    public static synchronized Season load(String id) throws IOException {
        Season s = cache.get(id);
        if (s == null) { s = SeasonLoader.load(id); cache.put(id, s); }
        return s;
    }

    /** Forget cached data after the launcher downloaded fresh files. */
    public static synchronized void reload() { cache.clear(); }

    public static Entry find(String id) {
        return all().stream().filter(e -> e.id().equals(id)).findFirst().orElse(all().get(0));
    }
}
