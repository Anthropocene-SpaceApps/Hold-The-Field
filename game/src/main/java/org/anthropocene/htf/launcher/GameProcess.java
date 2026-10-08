package org.anthropocene.htf.launcher;

import org.anthropocene.htf.Main;
import org.anthropocene.htf.core.Paths;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Starts the game in its own JVM and streams its output back to the launcher. */
public final class GameProcess {
    private Process process;

    public boolean running() { return process != null && process.isAlive(); }

    public static List<String> command(LauncherConfig cfg) {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xmx" + cfg.ramMb + "m");
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) cmd.add("-XstartOnFirstThread");
        String home = System.getProperty("htf.home");
        if (home != null) cmd.add("-Dhtf.home=" + home);
        cmd.add("-cp");
        cmd.add(absoluteClasspath(System.getProperty("java.class.path")));
        cmd.add(Main.class.getName());
        return cmd;
    }

    /** The game runs in another working directory, so a relative classpath (java -jar game.jar) must be made absolute. */
    public static String absoluteClasspath(String cp) {
        List<String> out = new ArrayList<>();
        for (String entry : cp.split(File.pathSeparator)) if (!entry.isBlank()) out.add(new File(entry).getAbsolutePath());
        return String.join(File.pathSeparator, out);
    }

    /** Launch; output lines go to {@code out}; {@code onExit} receives the exit code. */
    public void launch(LauncherConfig cfg, Consumer<String> out, Consumer<Integer> onExit) throws IOException {
        if (running()) throw new IllegalStateException("The game is already running");
        List<String> cmd = command(cfg);
        out.accept("> " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).directory(Paths.home().toFile());
        process = pb.start();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.accept(line);
            } catch (IOException ignored) { /* process ended */ }
            int code;
            try { code = process.waitFor(); } catch (InterruptedException e) { code = -1; }
            onExit.accept(code);
        }, "game-output");
        t.setDaemon(true);
        t.start();
    }
}
