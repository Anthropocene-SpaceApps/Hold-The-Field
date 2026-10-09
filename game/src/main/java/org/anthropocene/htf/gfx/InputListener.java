package org.anthropocene.htf.gfx;

/** Window events, delivered on the main thread from glfwPollEvents. */
public interface InputListener {
    default void onKey(int key, int action, int mods) {}
    default void onChar(int codepoint) {}
    default void onMouseButton(int button, int action, int mods) {}
    /** x, y in window coordinates; dx, dy since the last event. */
    default void onCursor(double x, double y, double dx, double dy) {}
    default void onScroll(double dy) {}
}
