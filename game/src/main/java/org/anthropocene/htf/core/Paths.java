package org.anthropocene.htf.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Where the launcher and game keep their files: ~/.hold-the-field (override with -Dhtf.home=...). */
public final class Paths {
    private Paths() {}

    public static Path home() {
        String override = System.getProperty("htf.home");
        Path p = override != null ? Path.of(override) : Path.of(System.getProperty("user.home"), ".hold-the-field");
        return ensure(p);
    }
    public static Path dataDir() { return ensure(home().resolve("data")); }
    public static Path savesDir() { return ensure(home().resolve("saves")); }
    public static Path screenshotsDir() { return ensure(home().resolve("screenshots")); }

    private static Path ensure(Path p) {
        try { Files.createDirectories(p); } catch (IOException ignored) { /* reported when a write fails */ }
        return p;
    }
}
