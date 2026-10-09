package org.anthropocene.htf.core;

import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/** Human-readable GLFW key names for the controls screen. */
public final class KeyNames {
    private KeyNames() {}

    private static final Map<Integer, String> SPECIAL = Map.ofEntries(
            Map.entry(GLFW_KEY_SPACE, "Space"), Map.entry(GLFW_KEY_LEFT_SHIFT, "Left Shift"), Map.entry(GLFW_KEY_RIGHT_SHIFT, "Right Shift"),
            Map.entry(GLFW_KEY_LEFT_CONTROL, "Left Ctrl"), Map.entry(GLFW_KEY_RIGHT_CONTROL, "Right Ctrl"),
            Map.entry(GLFW_KEY_LEFT_ALT, "Left Alt"), Map.entry(GLFW_KEY_RIGHT_ALT, "Right Alt"), Map.entry(GLFW_KEY_TAB, "Tab"),
            Map.entry(GLFW_KEY_ENTER, "Enter"), Map.entry(GLFW_KEY_BACKSPACE, "Backspace"), Map.entry(GLFW_KEY_UP, "Up"),
            Map.entry(GLFW_KEY_DOWN, "Down"), Map.entry(GLFW_KEY_LEFT, "Left"), Map.entry(GLFW_KEY_RIGHT, "Right"),
            Map.entry(GLFW_KEY_LEFT_BRACKET, "["), Map.entry(GLFW_KEY_RIGHT_BRACKET, "]"), Map.entry(GLFW_KEY_COMMA, ","),
            Map.entry(GLFW_KEY_PERIOD, "."), Map.entry(GLFW_KEY_SEMICOLON, ";"), Map.entry(GLFW_KEY_APOSTROPHE, "'"),
            Map.entry(GLFW_KEY_MINUS, "-"), Map.entry(GLFW_KEY_EQUAL, "="), Map.entry(GLFW_KEY_SLASH, "/"),
            Map.entry(GLFW_KEY_GRAVE_ACCENT, "`"), Map.entry(GLFW_KEY_CAPS_LOCK, "Caps Lock"));

    public static String of(int key) {
        String s = SPECIAL.get(key);
        if (s != null) return s;
        if (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25) return "F" + (key - GLFW_KEY_F1 + 1);
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) return String.valueOf((char) key);
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) return String.valueOf((char) key);
        String n = glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase() : "Key " + key;
    }
}
