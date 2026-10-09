package org.anthropocene.htf.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.anthropocene.htf.core.Paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Launcher-only options (JVM memory etc.). Game options live in options.json. */
public final class LauncherConfig {
    public int ramMb = 1024;
    public boolean closeOnLaunch = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return Paths.home().resolve("launcher.json"); }

    public static LauncherConfig load() {
        try {
            if (Files.isRegularFile(file())) {
                LauncherConfig c = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), LauncherConfig.class);
                if (c != null) { c.ramMb = Math.max(512, Math.min(16384, c.ramMb)); return c; }
            }
        } catch (Exception e) { System.err.println("Could not read launcher.json: " + e.getMessage()); }
        return new LauncherConfig();
    }

    public void save() {
        try { Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8); }
        catch (IOException e) { System.err.println("Could not save launcher.json: " + e.getMessage()); }
    }
}
