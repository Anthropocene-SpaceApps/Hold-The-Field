package org.anthropocene.htf.sim;

import com.google.gson.Gson;
import org.anthropocene.htf.core.Paths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads a season file: the user's data folder first (launcher "Update NASA data"), then the bundled copy. */
public final class SeasonLoader {
    private SeasonLoader() {}

    public static Season load(String id) throws IOException {
        Path user = Paths.dataDir().resolve(id + ".json");
        Season s;
        if (Files.isRegularFile(user)) {
            try (Reader r = Files.newBufferedReader(user, StandardCharsets.UTF_8)) {
                s = new Gson().fromJson(r, Season.class);
            }
        } else {
            try (InputStream in = SeasonLoader.class.getResourceAsStream("/data/" + id + ".json")) {
                if (in == null) throw new IOException("No season data for '" + id + "'");
                s = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Season.class);
            }
        }
        validate(s, id);
        s.id = id;
        return s;
    }

    public static Season parse(String json) {
        Season s = new Gson().fromJson(json, Season.class);
        validate(s, "inline");
        return s;
    }

    static void validate(Season s, String id) {
        if (s == null || s.days == null || s.days.isEmpty()) throw new IllegalStateException("Season '" + id + "' has no days");
        if (s.points == null || s.points.farm() == null || s.points.upstream() == null) {
            throw new IllegalStateException("Season '" + id + "' is missing farm/upstream points");
        }
        for (Day d : s.days) {
            if (d == null || d.date() == null) throw new IllegalStateException("Season '" + id + "' has a malformed day");
        }
    }
}
