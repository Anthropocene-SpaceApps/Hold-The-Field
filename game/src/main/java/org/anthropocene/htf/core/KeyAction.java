package org.anthropocene.htf.core;

import static org.lwjgl.glfw.GLFW.*;

/** Rebindable actions. Hotbar digits and Escape are fixed. */
public enum KeyAction {
    FORWARD("Walk Forwards", GLFW_KEY_W),
    BACK("Walk Backwards", GLFW_KEY_S),
    LEFT("Strafe Left", GLFW_KEY_A),
    RIGHT("Strafe Right", GLFW_KEY_D),
    JUMP("Jump / Fly Up", GLFW_KEY_SPACE),
    SNEAK("Sneak / Fly Down", GLFW_KEY_LEFT_SHIFT),
    SPRINT("Sprint", GLFW_KEY_LEFT_CONTROL),
    DASHBOARD("Scout Dashboard", GLFW_KEY_E),
    PAUSE_TIME("Pause / Resume Time", GLFW_KEY_P),
    SPEED_UP("Faster Time", GLFW_KEY_RIGHT_BRACKET),
    SPEED_DOWN("Slower Time", GLFW_KEY_LEFT_BRACKET),
    DEBUG("Toggle Debug Info", GLFW_KEY_F3),
    SCREENSHOT("Screenshot", GLFW_KEY_F2),
    FULLSCREEN("Toggle Fullscreen", GLFW_KEY_F11),
    HIDE_HUD("Hide HUD", GLFW_KEY_F1);

    public final String label;
    public final int defaultKey;

    KeyAction(String label, int defaultKey) {
        this.label = label;
        this.defaultKey = defaultKey;
    }
}
