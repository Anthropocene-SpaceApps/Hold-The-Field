package org.anthropocene.htf.launcher;

import com.google.gson.*;
import org.anthropocene.htf.core.Paths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Downloads NASA POWER daily data (community AG) for the game's seasons and writes season files into the user's
 * data folder. This is the Java twin of data-pipeline/fetch_power.py.
 */
public final class DataUpdater {
    public record Target(String id, String region, String start, String end, String label) {}

    static final String API = "https://power.larc.nasa.gov/api/temporal/daily/point";
    static final List<String> PARAMS = List.of("PRECTOTCORR", "T2M_MAX", "GWETROOT");
    static final double FILL = -999.0;

    private DataUpdater() {}

    public static List<Target> targets() throws IOException {
        try (InputStream in = DataUpdater.class.getResourceAsStream("/data-targets.json")) {
            if (in == null) throw new IOException("data-targets.json missing");
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            List<Target> out = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                out.add(new Target(o.get("id").getAsString(), o.get("region").getAsString(), o.get("start").getAsString(),
                        o.get("end").getAsString(), o.get("label").getAsString()));
            }
            return out;
        }
    }

    public static JsonObject regions() throws IOException {
        try (InputStream in = DataUpdater.class.getResourceAsStream("/regions.json")) {
            if (in == null) throw new IOException("regions.json missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Is there a real (non-sample) downloaded file for this target? */
    public static String status(Target t) {
        Path p = Paths.dataDir().resolve(t.id() + ".json");
        if (!Files.isRegularFile(p)) return "Not downloaded (using bundled SAMPLE data)";
        try {
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean sample = o.has("sample") && o.get("sample").getAsBoolean();
            return sample ? "Sample file" : "Real NASA data, " + o.getAsJsonArray("days").size() + " days (" + Files.getLastModifiedTime(p).toString().substring(0, 10) + ")";
        } catch (Exception e) {
            return "Unreadable file: " + e.getMessage();
        }
    }

    /** Download and write every target. Returns the number written. Throws on the first hard failure. */
    public static int updateAll(Consumer<String> log) throws IOException, InterruptedException {
        JsonObject regions = regions();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
        int written = 0;
        for (Target t : targets()) {
            log.accept("Fetching " + t.label() + " ...");
            JsonObject reg = regions.getAsJsonObject(t.region());
            JsonObject farm = reg.getAsJsonObject("farm"), up = reg.getAsJsonObject("upstream");
            Map<String, Map<String, Double>> farmData = fetch(client, farm, t, log);
            boolean same = farm.get("lat").equals(up.get("lat")) && farm.get("lon").equals(up.get("lon"));
            Map<String, Map<String, Double>> upData = same ? farmData : fetch(client, up, t, log);
            String json = buildSeasonJson(t, reg, farmData, upData);
            Path out = Paths.dataDir().resolve(t.id() + ".json");
            Path tmp = Paths.dataDir().resolve(t.id() + ".json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.accept("Wrote " + out);
            written++;
        }
        return written;
    }

    private static Map<String, Map<String, Double>> fetch(HttpClient client, JsonObject point, Target t, Consumer<String> log) throws IOException, InterruptedException {
        String url = API + "?parameters=" + URLEncoder.encode(String.join(",", PARAMS), StandardCharsets.UTF_8)
                + "&community=AG&latitude=" + point.get("lat").getAsDouble() + "&longitude=" + point.get("lon").getAsDouble()
                + "&start=" + t.start() + "&end=" + t.end() + "&format=JSON";
        log.accept("GET " + url);
        HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("NASA POWER answered HTTP " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
        return parseResponse(resp.body());
    }

    /** parameter -> (yyyyMMdd -> value) */
    public static Map<String, Map<String, Double>> parseResponse(String body) throws IOException {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject p = root.has("properties") && root.getAsJsonObject("properties").has("parameter")
                ? root.getAsJsonObject("properties").getAsJsonObject("parameter") : null;
        if (p == null) throw new IOException("Unexpected response from NASA POWER (no properties.parameter)");
        Map<String, Map<String, Double>> out = new HashMap<>();
        for (String param : PARAMS) {
            if (!p.has(param)) throw new IOException("NASA POWER did not return " + param);
            Map<String, Double> byDate = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject(param).entrySet())
                byDate.put(e.getKey(), e.getValue().isJsonNull() ? FILL : e.getValue().getAsDouble());
            out.put(param, byDate);
        }
        return out;
    }

    private static double clean(Double v, double dflt) { return v == null || v <= FILL + 1 ? dflt : v; }
    private static double round(double v, int dp) { double k = Math.pow(10, dp); return Math.round(v * k) / k; }

    /** Pure: assemble the season file the game loads. */
    public static String buildSeasonJson(Target t, JsonObject region, Map<String, Map<String, Double>> farm, Map<String, Map<String, Double>> up) {
        JsonArray days = new JsonArray();
        int gaps = 0;
        for (String key : farm.get("PRECTOTCORR").keySet()) {
            Double rf = farm.get("PRECTOTCORR").get(key), ru = up.get("PRECTOTCORR").get(key);
            Double tm = farm.get("T2M_MAX").get(key), sw = farm.get("GWETROOT").get(key);
            for (Double v : new Double[]{rf, ru, tm, sw}) if (v == null || v <= FILL + 1) gaps++;
            JsonObject d = new JsonObject();
            d.addProperty("date", key.substring(0, 4) + "-" + key.substring(4, 6) + "-" + key.substring(6));
            d.addProperty("rainUp", round(clean(ru, 0), 1));
            d.addProperty("rainFarm", round(clean(rf, 0), 1));
            d.addProperty("tmax", round(clean(tm, 30), 1));
            d.addProperty("soil", round(clean(sw, 0.5), 3));
            days.add(d);
        }
        JsonObject out = new JsonObject();
        out.addProperty("region", t.region());
        out.addProperty("season", t.start() + "-" + t.end());
        out.addProperty("sample", false);
        out.addProperty("source", "NASA POWER Daily API, community AG");
        JsonObject points = new JsonObject();
        JsonObject f = new JsonObject(), u = new JsonObject();
        f.addProperty("lat", region.getAsJsonObject("farm").get("lat").getAsDouble());
        f.addProperty("lon", region.getAsJsonObject("farm").get("lon").getAsDouble());
        u.addProperty("lat", region.getAsJsonObject("upstream").get("lat").getAsDouble());
        u.addProperty("lon", region.getAsJsonObject("upstream").get("lon").getAsDouble());
        points.add("farm", f); points.add("upstream", u);
        out.add("points", points);
        out.addProperty("missing_values_filled", gaps);
        out.add("days", days);
        return new GsonBuilder().setPrettyPrinting().create().toJson(out);
    }
}
