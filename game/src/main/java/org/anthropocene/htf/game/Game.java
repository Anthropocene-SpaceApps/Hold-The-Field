package org.anthropocene.htf.game;

import org.anthropocene.htf.audio.Audio;
import org.anthropocene.htf.core.KeyAction;
import org.anthropocene.htf.core.KeyNames;
import org.anthropocene.htf.core.Paths;
import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.*;
import org.anthropocene.htf.sim.*;
import org.anthropocene.htf.ui.*;
import org.anthropocene.htf.world.Player;
import org.anthropocene.htf.world.WorldScene;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImageWrite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** The application: window, main loop, screen stack, the running session and everything the HUD needs. */
public final class Game implements InputListener {
    public record ChatLine(String text, int color, double born) {}
    public record Toast(String header, String title, Tile icon, double born) {}

    private final Settings settings;
    private final Window window = new Window();
    private final Audio audio = new Audio();
    private final Advancements advancements = new Advancements();
    private Renderer2D r2;
    private WorldScene scene;
    private final Camera cam = new Camera();
    private final Player player = new Player();

    private Screen screen;
    private Session session;
    private Season titleSeason;
    private final List<ChatLine> chat = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();

    private double time, lastFrame, endTimer, thunderTimer, nameTimer, fovBoost;
    private int fps, frames;
    private double fpsClock;
    private int slot;
    private WorldScene.Target target = WorldScene.Target.NONE;
    private boolean debug, hideHud;
    private double lastJumpPress = -1;
    private float panoramaAngle;
    private String glInfo = "";
    private float guiScale = 2;
    private int mouseX, mouseY;
    private boolean closing;
    private DevCapture dev;

    public Game(Settings settings) { this.settings = settings; }

    // ------------------------------------------------------------------ accessors for UI

    public Settings settings() { return settings; }
    public Window window() { return window; }
    public Audio audio() { return audio; }
    public Advancements advancements() { return advancements; }
    public Session session() { return session; }
    public boolean hasSession() { return session != null; }
    public Player player() { return player; }
    public double time() { return time; }
    public int hotbarSlot() { return slot; }
    public double hotbarNameTimer() { return nameTimer; }
    public WorldScene.Target target() { return target; }
    public List<ChatLine> chatLines() { return chat; }
    public String glInfo() { return glInfo; }
    public Season titleSeason() { return titleSeason; }
    public Screen screen() { return screen; }
    public String keyName(KeyAction a) { return KeyNames.of(settings.key(a)); }

    public String targetLabel() {
        if (session == null) return null;
        switch (target.type()) {
            case BUND -> {
                if (!session.actionsAllowed()) return "Bund wall";
                int left = session.cfg.maxBundRaises - session.state.bundRaises;
                return slot == 0 ? (left > 0 ? "Bund wall: click to raise (" + session.cfg.bundRaiseCost + " coins)" : "Bund wall: at maximum height") : "Bund wall";
            }
            case CROP -> {
                int pct = (int) Math.round(session.state.maturity * 100);
                return slot == 1 ? "Rice " + pct + "%: click to harvest" : "Rice " + pct + "% mature";
            }
            default -> { return null; }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public void run() {
        window.create("Hold the Field", settings);
        window.listener = this;
        glInfo = glGetString(GL_RENDERER) + " | OpenGL " + glGetString(GL_VERSION);
        int atlas = Atlas.upload();
        r2 = new Renderer2D(atlas);
        scene = new WorldScene(atlas);
        audio.init(settings);
        try {
            titleSeason = SeasonCatalog.load(SeasonCatalog.all().get(0).id());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start: " + e.getMessage(), e);
        }
        setupPanorama();
        recomputeGui();
        setScreen(new TitleScreen());
        String capture = System.getProperty("htf.capture");
        if (capture != null) {
            try { dev = new DevCapture(this, capture); } catch (IOException e) { throw new IllegalStateException(e); }
        }

        lastFrame = glfwGetTime();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double dt = Math.min(0.1, now - lastFrame);
            lastFrame = now;
            window.pollEvents();
            if (window.iconified()) { sleep(50); continue; }
            update(dt);
            render(dt);
            window.swap();
            frames++;
            fpsClock += dt;
            if (fpsClock >= 1) { fps = frames; frames = 0; fpsClock = 0; }
            limitFps(now);
        }
        shutdown();
    }

    private void limitFps(double frameStart) {
        if (settings.vsync || settings.maxFps >= 260) return;
        double target = 1.0 / settings.maxFps, spent = glfwGetTime() - frameStart;
        if (spent < target) sleep((long) ((target - spent) * 1000));
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }

    private void shutdown() {
        saveSession();
        settings.save();
        audio.shutdown();
        window.destroy();
    }

    public void quit() { saveSession(); window.requestClose(); }

    // ------------------------------------------------------------------ panorama (title background)

    private void setupPanorama() {
        Config cfg = Config.DEFAULT;
        GameState s = Engine.createState(titleSeason, cfg, "long");
        // show the field just as the 2017 flood overtops the bund
        while (!s.finished && !s.flooded) s = Engine.step(s, titleSeason, cfg);
        for (int k = 0; k < 2 && !s.finished; k++) s = Engine.step(s, titleSeason, cfg);
        scene.setSession(titleSeason, cfg, s);
    }

    // ------------------------------------------------------------------ sessions

    public void startSession(Session s) {
        saveSession();
        this.session = s;
        endTimer = 0;
        scene.setSession(s.season, s.cfg, s.state);
        float yaw = 0;
        player.teleport(4.5f, 1f, 14.5f, yaw, -0.1f);
        player.flying = false;
        slot = 0;
        chat.clear();
        say("Rahim", "Welcome to the haor. Walk to the lake and watch the sky.", 0xFFFFD27A);
        say("Scout", "Satellite Scout online. NASA measures the rain in the hills upstream.", 0xFF8FD0FF);
        setScreen(null);
        s.timePaused = true;
        SaveManager.save(s.toSave());
    }

    public void quitToTitle() {
        saveSession();
        session = null;
        setupPanorama();
        setScreen(new TitleScreen());
    }

    public void saveSession() {
        if (session != null) SaveManager.save(session.toSave());
    }

    public void say(String sender, String msg, int color) {
        chat.add(new ChatLine("<" + sender + "> " + msg, color, time));
        if (chat.size() > 60) chat.remove(0);
    }

    private void grant(List<Advancements.Adv> list) {
        for (Advancements.Adv a : list) {
            toasts.add(new Toast("Advancement Made!", a.title(), Tile.ICON_WHEAT, time));
            audio.achieve();
            say("Advancement", a.title() + ": " + a.description(), 0xFFFFFF77);
        }
    }

    // ------------------------------------------------------------------ screens

    public void setScreen(Screen s) {
        if (screen != null) screen.onClose();
        screen = s;
        if (s != null) {
            s.open(this, r2 == null ? 400 : r2.guiW, r2 == null ? 300 : r2.guiH);
            window.captureCursor(false);
        } else if (session != null) {
            window.captureCursor(true);
        } else {
            window.captureCursor(false);
        }
    }

    private void recomputeGui() {
        guiScale = Renderer2D.autoScale(window.fbWidth, window.fbHeight, settings.guiScale);
        int gw = (int) Math.ceil(window.fbWidth / guiScale), gh = (int) Math.ceil(window.fbHeight / guiScale);
        if (r2.guiW != gw || r2.guiH != gh) {
            r2.guiW = gw; r2.guiH = gh;
            if (screen != null) screen.resize(gw, gh);
        }
        r2.scale = guiScale;
    }

    public void applySettings() {
        window.setVsync(settings.vsync);
        if (window.isFullscreen() != settings.fullscreen) window.setFullscreen(settings.fullscreen);
        audio.applyVolumes();
        recomputeGui();
        if (screen != null) screen.resize(r2.guiW, r2.guiH);
        settings.save();
    }

    // ------------------------------------------------------------------ per-frame update

    private void update(double dt) {
        if (dev != null) dev.update();
        time += dt;
        recomputeGui();
        double wx = window.cursorX * window.pixelRatio() / guiScale, wy = window.cursorY * window.pixelRatio() / guiScale;
        mouseX = (int) wx; mouseY = (int) wy;
        nameTimer = Math.max(0, nameTimer - dt);

        boolean running = session != null && screen == null;
        if (session != null && screen == null && !window.focused()) openPause();

        if (session != null) {
            if (screen == null || !screen.pausesGame()) session.tick(dt);
            handleEvents();
            checkEnd(dt);
        }
        if (screen != null) screen.tick(dt);

        if (session != null) scene.setState(session.state);     // the engine returns a fresh state object each step
        scene.update(dt, settings.particles);
        audio.setWeather(scene.rainIntensityUp() * 0.7 + scene.rainIntensityFarm() * 0.6);
        audio.tick(dt);

        if (running) updatePlayer(dt);
        toasts.removeIf(t -> time - t.born() > 6);
    }

    private void updatePlayer(double dt) {
        float fwd = (window.keyDown(settings.key(KeyAction.FORWARD)) ? 1 : 0) - (window.keyDown(settings.key(KeyAction.BACK)) ? 1 : 0);
        float str = (window.keyDown(settings.key(KeyAction.RIGHT)) ? 1 : 0) - (window.keyDown(settings.key(KeyAction.LEFT)) ? 1 : 0);
        boolean jump = window.keyDown(settings.key(KeyAction.JUMP)), sneak = window.keyDown(settings.key(KeyAction.SNEAK));
        boolean sprint = window.keyDown(settings.key(KeyAction.SPRINT));
        player.update((float) dt, scene, fwd, str, jump, sneak, sprint);
        fovBoost += ((player.sprinting || (player.flying && sprint) ? 7 : 0) - fovBoost) * Math.min(1, dt * 8);

        Vector3f eye = player.eye(settings.viewBobbing);
        Vector3f dir = new Vector3f((float) -Math.sin(player.yaw) * (float) Math.cos(player.pitch), (float) Math.sin(player.pitch),
                (float) -Math.cos(player.yaw) * (float) Math.cos(player.pitch));
        target = scene.pick(eye, dir, 9);
    }

    private void checkEnd(double dt) {
        if (!session.state.over() || session.endShown) { if (!session.state.over()) endTimer = 0; return; }
        endTimer += dt;
        if (endTimer >= 2.4 && (screen == null)) showEnd();
    }

    private void showEnd() {
        session.endShown = true;
        GameState base = session.baseline();
        grant(advancements.onEnd(session.state, base, session.actionsAllowed()));
        saveSession();
        setScreen(new EndScreen(session, base));
    }

    private void handleEvents() {
        for (Event e : session.drainEvents()) {
            switch (e.type()) {
                case "watch" -> say("Scout", "Rain is building in the Meghalaya hills.", 0xFFFFC266);
                case "warning" -> {
                    say("Scout", "FLOOD WARNING: heavy rain upstream. Water may reach the haor within days!", 0xFFFF6B6B);
                    say("Rahim", "The sky over the hills has been heavy all week. Something is coming.", 0xFFFFD27A);
                    audio.warning();
                }
                case "flood" -> {
                    say("Rahim", "The water is over the bund!", 0xFFFF9A6B);
                    audio.flood();
                    scene.burst(4, 1.3f, -0.5f, 40, 0.5f, 0.7f, 1f);
                }
                case "loss" -> {
                    say("Rahim", "Everything we planted... gone under the water.", 0xFFCCCCCC);
                    audio.error();
                }
                case "harvest" -> {
                    say("Rahim", session.state.yieldPct > 0 ? "The rice is home!" : "The season is over.", 0xFFB8F59A);
                    if (session.state.yieldPct > 0) { audio.harvest(); scene.burst(4, 1.6f, 4, 36, 0.95f, 0.8f, 0.25f); }
                }
                case "action" -> say("You", e.text(), 0xFFFFFFFF);
                default -> { }
            }
            grant(advancements.onEvent(e.type(), session.state));
        }
        // far-off thunder in heavy rain
        if (scene.stormLevel() > 0.7) {
            thunderTimer -= 1.0 / 60;
            if (thunderTimer <= 0) { audio.thunder(); thunderTimer = 6 + Math.random() * 10; }
        }
    }

    // ------------------------------------------------------------------ tools

    private void useTool() {
        if (session == null || screen != null) return;
        switch (slot) {
            case 0 -> {
                if (target.type() != WorldScene.TargetType.BUND) { toastMsg("Aim at the brick bund wall by the lake"); return; }
                Engine.ActionResult r = session.act(Engine.ActionType.RAISE_BUND);
                if (!r.ok()) { toastMsg(r.reason()); audio.error(); return; }
                audio.place();
                scene.burst(target.x() + 0.5f, session.state.bund > 0 ? (float) (1 + session.state.bund / WorldScene.BLOCK_M) : 2, target.z() + 0.5f, 24, 0.6f, 0.42f, 0.28f);
            }
            case 1 -> {
                if (target.type() != WorldScene.TargetType.CROP) { toastMsg("Aim at the rice field to harvest"); return; }
                Engine.ActionResult r = session.act(Engine.ActionType.HARVEST);
                if (!r.ok()) { toastMsg(r.reason()); audio.error(); }
                // the harvest sound and grain burst come with the sim event
            }
            case 2 -> { audio.click(); setScreen(new DashboardScreen(session)); }
            case 3 -> { audio.click(); AboutScreen a = new AboutScreen(); a.parent = null; setScreen(a); }
            default -> { }
        }
    }

    public void toastMsg(String msg) {
        chat.add(new ChatLine(msg, 0xFFFFAA55, time));
    }

    public void selectSlot(int i) {
        slot = Math.max(0, Math.min(8, i));
        nameTimer = 2;
    }

    // ------------------------------------------------------------------ rendering

    private void render(double dt) {
        updateCamera();
        scene.render(cam, settings, session != null && screen == null ? target : null, window.fbWidth, window.fbHeight);

        r2.begin(window.fbWidth, window.fbHeight, guiScale);
        if (screen != null) {
            screen.render(r2, mouseX, mouseY);
        } else if (session != null && !hideHud) {
            Hud.render(this, r2);
        }
        if (debug && session != null && screen == null) Hud.debug(this, r2, fps);
        if (!hideHud || screen != null) for (Toast t : toasts) Hud.toast(r2, t, time);
        r2.end();
        if (dev != null) dev.afterRender();
    }

    private void updateCamera() {
        cam.fovDeg = settings.fov + (float) fovBoost;
        if (session != null) {
            cam.pos.set(player.eye(settings.viewBobbing));
            cam.yaw = player.yaw;
            cam.pitch = player.pitch;
        } else {
            // a slow pan across the farm, the bund, the lake and the hills, staying well clear of the terrain
            panoramaAngle += 0.0016f;
            cam.pos.set(4 + (float) Math.sin(panoramaAngle) * 9, 10.5f + (float) Math.sin(panoramaAngle * 1.7) * 1.2f, 24f);
            cam.yaw = (float) Math.sin(panoramaAngle * 0.8) * 0.35f;
            cam.pitch = -0.2f;
        }
        cam.update(window.fbWidth, window.fbHeight);
    }

    // ------------------------------------------------------------------ input

    private void openPause() {
        setScreen(new PauseScreen());
    }

    @Override
    public void onKey(int key, int action, int mods) {
        if (action == GLFW_RELEASE) return;
        if (key == GLFW_KEY_F11 || key == settings.key(KeyAction.FULLSCREEN)) {
            if (action == GLFW_PRESS) { settings.fullscreen = !window.isFullscreen(); applySettings(); }
            return;
        }
        if (key == settings.key(KeyAction.SCREENSHOT) && action == GLFW_PRESS) { screenshot(); return; }

        if (screen != null) {
            if (screen.keyDown(key, mods)) return;
            return;
        }
        if (session == null) return;
        if (action == GLFW_REPEAT) return;

        if (key == GLFW_KEY_ESCAPE) { openPause(); return; }
        if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) { selectSlot(key - GLFW_KEY_1); return; }
        if (key == settings.key(KeyAction.DASHBOARD)) { setScreen(new DashboardScreen(session)); return; }
        if (key == settings.key(KeyAction.PAUSE_TIME)) {
            session.timePaused = !session.timePaused;
            audio.click();
            return;
        }
        if (key == settings.key(KeyAction.SPEED_UP)) { session.speedUp(); return; }
        if (key == settings.key(KeyAction.SPEED_DOWN)) { session.speedDown(); return; }
        if (key == settings.key(KeyAction.DEBUG)) { debug = !debug; return; }
        if (key == settings.key(KeyAction.HIDE_HUD)) { hideHud = !hideHud; return; }
        if (key == settings.key(KeyAction.JUMP)) {
            if (time - lastJumpPress < 0.3) { player.flying = !player.flying; player.vel.y = 0; lastJumpPress = -1; }
            else lastJumpPress = time;
        }
    }

    @Override
    public void onChar(int cp) { if (screen != null) screen.charTyped(cp); }

    @Override
    public void onMouseButton(int button, int action, int mods) {
        if (screen != null) {
            if (action == GLFW_PRESS) screen.mouseDown(mouseX, mouseY, button);
            else screen.mouseUp(mouseX, mouseY, button);
            return;
        }
        if (session == null || action != GLFW_PRESS) return;
        if (!window.isCursorCaptured()) { window.captureCursor(true); return; }
        if (button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_RIGHT) useTool();
    }

    @Override
    public void onCursor(double x, double y, double dx, double dy) {
        if (screen != null) {
            screen.mouseMoved((int) (x * window.pixelRatio() / guiScale), (int) (y * window.pixelRatio() / guiScale));
            return;
        }
        if (session != null && window.isCursorCaptured()) player.look(dx, dy, settings.mouseSensitivity, settings.invertY);
    }

    @Override
    public void onScroll(double dy) {
        if (screen != null) { screen.scroll(mouseX, mouseY, dy); return; }
        if (session != null) selectSlot(((slot - (int) Math.signum(dy)) % 9 + 9) % 9);
    }

    // ------------------------------------------------------------------ screenshots

    public void screenshot() {
        int w = window.fbWidth, h = window.fbHeight;
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buf);
        STBImageWrite.stbi_flip_vertically_on_write(true);
        Path out = Paths.screenshotsDir().resolve("htf-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".png");
        boolean ok = STBImageWrite.stbi_write_png(out.toString(), w, h, 3, buf, w * 3);
        if (session != null) say("System", ok ? "Saved screenshot as " + out.getFileName() : "Could not save screenshot", 0xFFFFFFFF);
        else System.out.println("Screenshot: " + out);
    }

    /** Test hook: render N frames then screenshot to an explicit path. */
    public void screenshotTo(Path out) {
        int w = window.fbWidth, h = window.fbHeight;
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 3);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buf);
        STBImageWrite.stbi_flip_vertically_on_write(true);
        STBImageWrite.stbi_write_png(out.toString(), w, h, 3, buf, w * 3);
    }
}
