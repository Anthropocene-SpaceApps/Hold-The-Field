package org.anthropocene.htf.gfx;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Batched GUI renderer. Coordinates are GUI units with the origin at the top-left; colours are 0xAARRGGBB.
 * Shapes (rounded rectangles, rings, soft shadows) are drawn as anti-aliased signed-distance fields, so they stay
 * crisp at any GUI scale. Switching texture or clip rectangle flushes the batch.
 */
public final class Renderer2D {
    private static final int STRIDE = 15;     // pos2 uv2 col4 local2 shape4 mode1
    /** The GUI is laid out for a canvas this tall; the scale adapts it to the real window. */
    public static final float DESIGN_HEIGHT = 720f;

    private final Shader shader = ShaderLoader.load("ui");
    private final int vao, vbo;
    private float[] buf = new float[STRIDE * 6 * 1024];
    private int n;                               // floats used
    private int currentTex = -1;
    private final FloatBuffer staging = BufferUtils.createFloatBuffer(STRIDE * 6 * 4096);
    private final Matrix4f proj = new Matrix4f();
    private final int whiteTex;
    public final Font font, bold, mono;

    public float scale = 1;
    public int guiW, guiH;
    private int fbH;
    private final List<float[]> clips = new ArrayList<>();

    public Renderer2D() {
        font = new Font("/fonts/DejaVuSans.ttf");
        bold = new Font("/fonts/DejaVuSans-Bold.ttf");
        mono = new Font("/fonts/DejaVuSansMono.ttf");
        whiteTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, whiteTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, new int[]{0xFFFFFFFF});
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) staging.capacity() * 4, GL_STREAM_DRAW);
        int[] sizes = {2, 2, 4, 2, 4, 1};
        int off = 0;
        for (int i = 0; i < sizes.length; i++) {
            glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, STRIDE * 4, off);
            glEnableVertexAttribArray(i);
            off += sizes[i] * 4;
        }
        glBindVertexArray(0);
    }

    /** GUI scale for a framebuffer: the design canvas (720 units tall) scaled to the window, times the user option. */
    public static float autoScale(int fbW, int fbH, int setting) {
        float[] factor = {1f, 0.75f, 0.9f, 1.1f, 1.25f, 1.5f};
        float base = Math.max(0.4f, fbH / DESIGN_HEIGHT);
        // very wide or very narrow windows: never let the canvas get narrower than 900 units
        float s = base * factor[Math.max(0, Math.min(factor.length - 1, setting))];
        return Math.min(s, fbW / 900f);
    }

    public void begin(int fbW, int fbH, float scale) {
        this.scale = scale;
        this.fbH = fbH;
        guiW = (int) Math.ceil(fbW / scale);
        guiH = (int) Math.ceil(fbH / scale);
        glViewport(0, 0, fbW, fbH);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        proj.identity().ortho(0, guiW, guiH, 0, -1, 1);
        shader.use();
        shader.set("uProj", proj);
        shader.set("uTex", 0);
        currentTex = -1;
        clips.clear();
        glDisable(GL_SCISSOR_TEST);
    }

    public void end() {
        flush();
        glDisable(GL_SCISSOR_TEST);
    }

    private void bind(int tex) {
        if (tex != currentTex) { flush(); currentTex = tex; }
    }

    public void flush() {
        if (n == 0) return;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, currentTex);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int off = 0;
        int piece = staging.capacity() / (STRIDE * 6) * (STRIDE * 6);
        while (off < n) {
            int chunk = Math.min(n - off, piece);
            staging.clear();
            staging.put(buf, off, chunk).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, staging);
            glDrawArrays(GL_TRIANGLES, 0, chunk / STRIDE);
            off += chunk;
        }
        glBindVertexArray(0);
        n = 0;
    }

    private void v(float x, float y, float u, float vv, int c, float lx, float ly, float hw, float hh, float rad, float param, float mode) {
        if (n + STRIDE > buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
        buf[n++] = x; buf[n++] = y; buf[n++] = u; buf[n++] = vv;
        buf[n++] = ((c >> 16) & 255) / 255f; buf[n++] = ((c >> 8) & 255) / 255f; buf[n++] = (c & 255) / 255f; buf[n++] = ((c >>> 24) & 255) / 255f;
        buf[n++] = lx; buf[n++] = ly; buf[n++] = hw; buf[n++] = hh; buf[n++] = rad; buf[n++] = param; buf[n++] = mode;
    }

    private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int c00, int c10, int c11, int c01) {
        v(x0, y0, u0, v0, c00, 0, 0, 0, 0, 0, 0, 0); v(x1, y0, u1, v0, c10, 0, 0, 0, 0, 0, 0, 0); v(x1, y1, u1, v1, c11, 0, 0, 0, 0, 0, 0, 0);
        v(x0, y0, u0, v0, c00, 0, 0, 0, 0, 0, 0, 0); v(x1, y1, u1, v1, c11, 0, 0, 0, 0, 0, 0, 0); v(x0, y1, u0, v1, c01, 0, 0, 0, 0, 0, 0, 0);
    }

    /** One quad (grown by pad on every side) carrying signed-distance shape data. */
    private void shape(float x, float y, float w, float h, float r, float param, float pad, int c, float mode) {
        shape(x, y, w, h, r, param, pad, c, c, mode);
    }

    private void shape(float x, float y, float w, float h, float r, float param, float pad, int c, int cb, float mode) {
        float hw = w / 2, hh = h / 2, cx = x + hw, cy = y + hh;
        r = Math.min(r, Math.min(hw, hh));
        float x0 = x - pad, y0 = y - pad, x1 = x + w + pad, y1 = y + h + pad;
        bind(whiteTex);
        v(x0, y0, 0, 0, c, x0 - cx, y0 - cy, hw, hh, r, param, mode); v(x1, y0, 0, 0, c, x1 - cx, y0 - cy, hw, hh, r, param, mode);
        v(x1, y1, 0, 0, cb, x1 - cx, y1 - cy, hw, hh, r, param, mode);
        v(x0, y0, 0, 0, c, x0 - cx, y0 - cy, hw, hh, r, param, mode); v(x1, y1, 0, 0, cb, x1 - cx, y1 - cy, hw, hh, r, param, mode);
        v(x0, y1, 0, 0, cb, x0 - cx, y1 - cy, hw, hh, r, param, mode);
    }

    // ---------------------------------------------------------------- shapes

    public void rect(float x, float y, float w, float h, int c) {
        bind(whiteTex);
        quad(x, y, x + w, y + h, 0, 0, 1, 1, c, c, c, c);
    }

    public void roundRect(float x, float y, float w, float h, float radius, int c) { shape(x, y, w, h, radius, 0, 2, c, 1); }

    /** Rounded rectangle with a vertical colour gradient. */
    public void roundGradient(float x, float y, float w, float h, float radius, int top, int bottom) { shape(x, y, w, h, radius, 0, 2, top, bottom, 1); }

    public void roundRing(float x, float y, float w, float h, float radius, float thickness, int c) { shape(x, y, w, h, radius, thickness, 2, c, 2); }

    /** Soft drop shadow: draw it before the shape it belongs to. */
    public void shadow(float x, float y, float w, float h, float radius, float soft, int c) {
        shape(x, y, w, h, radius, soft, soft + 2, c, 3);
    }

    public void gradientV(float x, float y, float w, float h, int top, int bottom) {
        bind(whiteTex);
        quad(x, y, x + w, y + h, 0, 0, 1, 1, top, top, bottom, bottom);
    }

    public void gradientH(float x, float y, float w, float h, int left, int right) {
        bind(whiteTex);
        quad(x, y, x + w, y + h, 0, 0, 1, 1, left, right, right, left);
    }

    public void border(float x, float y, float w, float h, float t, int c) {
        rect(x, y, w, t, c); rect(x, y + h - t, w, t, c); rect(x, y + t, t, h - 2 * t, c); rect(x + w - t, y + t, t, h - 2 * t, c);
    }

    /** Line with round-ish ends as a rotated quad; anti-aliased by MSAA. */
    public void line(float x1, float y1, float x2, float y2, float thick, int c) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * thick / 2, ny = dx / len * thick / 2;
        bind(whiteTex);
        v(x1 + nx, y1 + ny, 0, 0, c, 0, 0, 0, 0, 0, 0, 0); v(x2 + nx, y2 + ny, 0, 0, c, 0, 0, 0, 0, 0, 0, 0); v(x2 - nx, y2 - ny, 0, 0, c, 0, 0, 0, 0, 0, 0, 0);
        v(x1 + nx, y1 + ny, 0, 0, c, 0, 0, 0, 0, 0, 0, 0); v(x2 - nx, y2 - ny, 0, 0, c, 0, 0, 0, 0, 0, 0, 0); v(x1 - nx, y1 - ny, 0, 0, c, 0, 0, 0, 0, 0, 0, 0);
    }

    public void circle(float cx, float cy, float r, int c) { shape(cx - r, cy - r, 2 * r, 2 * r, r, 0, 2, c, 1); }

    public void ring(float cx, float cy, float r, float thickness, int c) { shape(cx - r, cy - r, 2 * r, 2 * r, r, thickness, 2, c, 2); }

    /** Filled triangle (icons, arrows). */
    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        bind(whiteTex);
        v(x1, y1, 0, 0, c, 0, 0, 0, 0, 0, 0, 0); v(x2, y2, 0, 0, c, 0, 0, 0, 0, 0, 0, 0); v(x3, y3, 0, 0, c, 0, 0, 0, 0, 0, 0, 0);
    }

    /** One slice of a filled area chart: sloped top edge down to a flat baseline, with a vertical colour fade. Slices tile exactly. */
    public void areaSegment(float x0, float yTop0, float x1, float yTop1, float yBase, int topColor, int bottomColor) {
        bind(whiteTex);
        v(x0, yTop0, 0, 0, topColor, 0, 0, 0, 0, 0, 0, 0); v(x1, yTop1, 0, 0, topColor, 0, 0, 0, 0, 0, 0, 0); v(x1, yBase, 0, 0, bottomColor, 0, 0, 0, 0, 0, 0, 0);
        v(x0, yTop0, 0, 0, topColor, 0, 0, 0, 0, 0, 0, 0); v(x1, yBase, 0, 0, bottomColor, 0, 0, 0, 0, 0, 0, 0); v(x0, yBase, 0, 0, bottomColor, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Polyline drawn as connected thick segments. */
    public void polyline(float[] xy, float thick, int c) {
        for (int i = 0; i + 3 < xy.length; i += 2) line(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], thick, c);
    }

    // ---------------------------------------------------------------- text

    void glyph(float x0, float y0, float x1, float y1, float s0, float t0, float s1, float t1, int c) {
        quad(x0, y0, x1, y1, s0, t0, s1, t1, c, c, c, c);
    }

    public float textWidth(String s, float size) { return font.width(s, size); }
    public float textWidth(String s, float size, boolean boldFace) { return (boldFace ? bold : font).width(s, size); }

    public void text(String s, float x, float y, float size, int color) { text(s, x, y, size, color, false, false); }

    public void textShadow(String s, float x, float y, float size, int color) { text(s, x, y, size, color, true, false); }

    public void text(String s, float x, float y, float size, int color, boolean shadow, boolean boldFace) {
        Font f = boldFace ? bold : font;
        bind(f.texture);
        if (shadow) f.emit(this, s, x + Math.max(0.8f, size / 16), y + Math.max(0.8f, size / 16), size, (0x99 << 24));
        f.emit(this, s, x, y, size, color);
    }

    public void textMono(String s, float x, float y, float size, int color) {
        bind(mono.texture);
        mono.emit(this, s, x, y, size, color);
    }

    public float monoWidth(String s, float size) { return mono.width(s, size); }

    public void textCentered(String s, float cx, float y, float size, int color, boolean shadow, boolean boldFace) {
        text(s, cx - textWidth(s, size, boldFace) / 2, y, size, color, shadow, boldFace);
    }

    public void textRight(String s, float rx, float y, float size, int color, boolean shadow) {
        text(s, rx - textWidth(s, size), y, size, color, shadow, false);
    }

    /** Greedy word wrap into lines no wider than maxW. */
    public List<String> wrap(String s, float maxW, float size) { return wrap(s, maxW, size, false); }

    public List<String> wrap(String s, float maxW, float size, boolean boldFace) {
        List<String> lines = new ArrayList<>();
        for (String para : s.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.isEmpty() ? word : line + " " + word;
                if (textWidth(trial, size, boldFace) > maxW && !line.isEmpty()) { lines.add(line.toString()); line = new StringBuilder(word); }
                else line = new StringBuilder(trial);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    // ---------------------------------------------------------------- clipping

    public void pushClip(float x, float y, float w, float h) {
        flush();
        clips.add(new float[]{x, y, w, h});
        applyClip();
    }

    public void popClip() {
        flush();
        if (!clips.isEmpty()) clips.remove(clips.size() - 1);
        applyClip();
    }

    private void applyClip() {
        if (clips.isEmpty()) { glDisable(GL_SCISSOR_TEST); return; }
        float[] c = clips.get(clips.size() - 1);
        glEnable(GL_SCISSOR_TEST);
        int sx = Math.max(0, Math.round(c[0] * scale)), sw = Math.max(0, Math.round(c[2] * scale));
        int sh = Math.max(0, Math.round(c[3] * scale)), sy = Math.max(0, fbH - Math.round((c[1] + c[3]) * scale));
        glScissor(sx, sy, sw, sh);
    }

    // ---------------------------------------------------------------- colour helpers

    public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    public static int withAlpha(int c, float alpha) { return (Math.round(((c >>> 24) & 255) * alpha) << 24) | (c & 0xFFFFFF); }

    public static int lerp(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t), ar = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int ag = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), ab = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }
}
