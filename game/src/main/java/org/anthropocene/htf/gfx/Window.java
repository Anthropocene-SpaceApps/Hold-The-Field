package org.anthropocene.htf.gfx;

import org.anthropocene.htf.core.Settings;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** GLFW window + OpenGL 3.3 core context, cursor capture, fullscreen toggle. */
public final class Window {
    public long handle;
    public int fbWidth, fbHeight;        // framebuffer pixels
    public int winWidth, winHeight;      // window units (differs from fb on HiDPI)
    private boolean fullscreen;
    private int windowedX, windowedY, windowedW, windowedH;
    private boolean cursorCaptured;
    private double lastX, lastY;
    private boolean haveLast;
    public InputListener listener = new InputListener() {};
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] buttons = new boolean[8];
    public double cursorX, cursorY;      // window coords

    public void create(String title, Settings st) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Could not initialise GLFW (is a display available?)");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        handle = glfwCreateWindow(st.windowWidth, st.windowHeight, title, NULL, NULL);
        if (handle == NULL) throw new IllegalStateException("Could not create the game window (OpenGL 3.3 required)");

        GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vm != null) glfwSetWindowPos(handle, (vm.width() - st.windowWidth) / 2, (vm.height() - st.windowHeight) / 2);

        glfwSetKeyCallback(handle, (w, key, sc, action, mods) -> {
            if (key >= 0 && key < keys.length) keys[key] = action != GLFW_RELEASE;
            listener.onKey(key, action, mods);
        });
        glfwSetCharCallback(handle, (w, cp) -> listener.onChar(cp));
        glfwSetMouseButtonCallback(handle, (w, b, action, mods) -> {
            if (b >= 0 && b < buttons.length) buttons[b] = action != GLFW_RELEASE;
            listener.onMouseButton(b, action, mods);
        });
        glfwSetCursorPosCallback(handle, (w, x, y) -> {
            cursorX = x; cursorY = y;
            double dx = haveLast ? x - lastX : 0, dy = haveLast ? y - lastY : 0;
            lastX = x; lastY = y; haveLast = true;
            listener.onCursor(x, y, dx, dy);
        });
        glfwSetScrollCallback(handle, (w, sx, sy) -> listener.onScroll(sy));
        glfwSetFramebufferSizeCallback(handle, (w, fw, fh) -> { fbWidth = fw; fbHeight = fh; });
        glfwSetWindowSizeCallback(handle, (w, ww, wh) -> { winWidth = ww; winHeight = wh; });

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(st.vsync ? 1 : 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer a = stack.mallocInt(1), b = stack.mallocInt(1);
            glfwGetFramebufferSize(handle, a, b); fbWidth = a.get(0); fbHeight = b.get(0);
            glfwGetWindowSize(handle, a, b); winWidth = a.get(0); winHeight = b.get(0);
        }
        glfwShowWindow(handle);
        if (st.fullscreen) setFullscreen(true);
    }

    public boolean isFullscreen() { return fullscreen; }

    public void setFullscreen(boolean on) {
        if (on == fullscreen) return;
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vm = glfwGetVideoMode(monitor);
        if (vm == null) return;
        if (on) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), w = stack.mallocInt(1), h = stack.mallocInt(1);
                glfwGetWindowPos(handle, x, y); glfwGetWindowSize(handle, w, h);
                windowedX = x.get(0); windowedY = y.get(0); windowedW = w.get(0); windowedH = h.get(0);
            }
            glfwSetWindowMonitor(handle, monitor, 0, 0, vm.width(), vm.height(), vm.refreshRate());
        } else {
            glfwSetWindowMonitor(handle, NULL, windowedX, windowedY, Math.max(640, windowedW), Math.max(480, windowedH), 0);
        }
        fullscreen = on;
        haveLast = false;
    }

    public void setVsync(boolean on) { glfwSwapInterval(on ? 1 : 0); }

    public void setTitle(String t) { glfwSetWindowTitle(handle, t); }

    /** Capture the mouse for first-person look (raw, hidden) or release it for menus. */
    public void captureCursor(boolean capture) {
        if (capture == cursorCaptured) return;
        cursorCaptured = capture;
        glfwSetInputMode(handle, GLFW_CURSOR, capture ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        if (capture && glfwRawMouseMotionSupported()) glfwSetInputMode(handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        haveLast = false;
    }

    public boolean isCursorCaptured() { return cursorCaptured; }
    public boolean keyDown(int key) { return key >= 0 && key < keys.length && keys[key]; }
    public boolean buttonDown(int b) { return b >= 0 && b < buttons.length && buttons[b]; }
    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void requestClose() { glfwSetWindowShouldClose(handle, true); }
    public void pollEvents() { glfwPollEvents(); }
    public void swap() { glfwSwapBuffers(handle); }
    public boolean focused() { return glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE; }
    public boolean iconified() { return glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE; }
    public String clipboard() { String s = glfwGetClipboardString(handle); return s == null ? "" : s; }

    /** Ratio between framebuffer pixels and window units (2 on retina). */
    public double pixelRatio() { return winWidth == 0 ? 1 : (double) fbWidth / winWidth; }

    public void destroy() {
        if (handle != NULL) glfwDestroyWindow(handle);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }
}
