package org.anthropocene.htf.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Player options, saved to ~/.hold-the-field/options.json. Shared by launcher and game. */
public final class Settings {
    // Video
    public int fov = 70;
    public int renderDistance = 10;       // fog end = renderDistance * 12 blocks
    public int guiScale = 0;              // 0 = auto
    public boolean fullscreen = false;
    public boolean vsync = true;
    public int maxFps = 120;              // 260 = unlimited
    public double brightness = 0.5;
    public boolean clouds = true;
    public boolean particles = true;
    public boolean viewBobbing = true;
    public boolean shadows = true;
    public boolean bloom = true;
    public int msaa = 4;                  // 0, 2, 4, 8
    public int timeMode = 0;              // see Atmosphere.TIME_MODES
    public int grass = 2;                 // 0 off, 1 medium, 2 high
    public int windowWidth = 1280;
    public int windowHeight = 720;
    // Controls
    public double mouseSensitivity = 0.5;
    public boolean invertY = false;
    public Map<String, Integer> keys = new HashMap<>();
    // Sound
    public double masterVolume = 0.8;
    public double effectsVolume = 1.0;
    public double ambientVolume = 0.6;
    // Game
    public boolean showHints = true;
    public boolean sampleDataWarning = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int key(KeyAction a) { return keys.getOrDefault(a.name(), a.defaultKey); }

    public void bind(KeyAction a, int key) { keys.put(a.name(), key); }

    public void resetKeys() { keys.clear(); }

    private static Path file() { return Paths.home().resolve("options.json"); }

    public static Settings load() {
        try {
            if (Files.isRegularFile(file())) {
                Settings s = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Settings.class);
                if (s != null) { s.sanitize(); return s; }
            }
        } catch (Exception e) {
            System.err.println("Could not read options.json, using defaults: " + e.getMessage());
        }
        return new Settings();
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save options.json: " + e.getMessage());
        }
    }

    private void sanitize() {
        if (keys == null) keys = new HashMap<>();
        fov = Math.max(40, Math.min(110, fov));
        renderDistance = Math.max(4, Math.min(16, renderDistance));
        guiScale = Math.max(0, Math.min(5, guiScale));
        maxFps = Math.max(30, Math.min(260, maxFps));
        msaa = (msaa == 2 || msaa == 4 || msaa == 8) ? msaa : 0;
        timeMode = Math.max(0, Math.min(4, timeMode));
        grass = Math.max(0, Math.min(2, grass));
        windowWidth = Math.max(640, windowWidth);
        windowHeight = Math.max(480, windowHeight);
        brightness = clamp01(brightness); mouseSensitivity = clamp01(mouseSensitivity);
        masterVolume = clamp01(masterVolume); effectsVolume = clamp01(effectsVolume); ambientVolume = clamp01(ambientVolume);
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
