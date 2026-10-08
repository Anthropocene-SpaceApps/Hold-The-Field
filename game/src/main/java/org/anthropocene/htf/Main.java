package org.anthropocene.htf;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.game.Game;

/**
 * Game entry point. Normally started by the launcher; running it directly also works:
 *   java -cp hold-the-field.jar org.anthropocene.htf.Main [--width N --height N --fullscreen --windowed]
 * (add -XstartOnFirstThread on macOS).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Settings settings = Settings.load();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--width" -> settings.windowWidth = Integer.parseInt(args[++i]);
                case "--height" -> settings.windowHeight = Integer.parseInt(args[++i]);
                case "--fullscreen" -> settings.fullscreen = true;
                case "--windowed" -> settings.fullscreen = false;
                case "--home" -> System.setProperty("htf.home", args[++i]);
                default -> System.err.println("Unknown argument: " + args[i]);
            }
        }
        try {
            new Game(settings).run();
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
