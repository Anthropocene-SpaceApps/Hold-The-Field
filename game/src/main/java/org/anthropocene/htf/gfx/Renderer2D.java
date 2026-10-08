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
 * Batched immediate-mode GUI renderer. Coordinates are GUI pixels with the origin at the top-left;
 * colours are 0xAARRGGBB. Switching texture or clip rectangle flushes the batch.
 */
public final class Renderer2D {
    private static final int STRIDE = 8;
    private static final String VS = """
            #version 330 core
            layout(location=0) in vec2 pos; layout(location=1) in vec2 uv; layout(location=2) in vec4 col;
            uniform mat4 proj; out vec2 vUv; out vec4 vCol;
            void main(){ vUv = uv; vCol = col; gl_Position = proj * vec4(pos, 0.0, 1.0); }""";
    private static final String FS = """
            #version 330 core
            in vec2 vUv; in vec4 vCol; uniform sampler2D tex; out vec4 frag;
            void main(){ frag = texture(tex, vUv) * vCol; }""";

    private final Shader shader = new Shader(VS, FS);
    private final int vao, vbo;
    private float[] buf = new float[STRIDE * 6 * 2048];
    private int n;                               // floats used
    private int currentTex = -1;
    private final FloatBuffer staging = BufferUtils.createFloatBuffer(STRIDE * 6 * 8192);
    private final Matrix4f proj = new Matrix4f();
    private final int whiteTex;
    private final int atlasTex;
    public final Font font, bold;

    public float scale = 2;
    public int guiW, guiH;
    private int fbH;
    private final List<float[]> clips = new ArrayList<>();

    public Renderer2D(int atlasTex) {
        this.atlasTex = atlasTex;
        font = new Font("/fonts/DejaVuSansMono.ttf");
        bold = new Font("/fonts/DejaVuSansMono-Bold.ttf");
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
        glVertexAttribPointer(0, 2, GL_FLOAT, false, STRIDE * 4, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE * 4, 8);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, STRIDE * 4, 16);
        glEnableVertexAttribArray(0); glEnableVertexAttribArray(1); glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    /** Pick the GUI scale: auto = largest integer keeping at least 320x240 GUI pixels. */
    public static float autoScale(int fbW, int fbH, int setting) {
        if (setting > 0) return setting;
        int s = 1;
        while (s < 6 && fbW / (s + 1) >= 400 && fbH / (s + 1) >= 270) s++;
        return s;
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
        shader.set("proj", proj);
        shader.set("tex", 0);
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
        while (off < n) {                                // upload in pieces no bigger than the VBO
            int chunk = Math.min(n - off, staging.capacity() / (STRIDE * 6) * (STRIDE * 6));
            staging.clear();
            staging.put(buf, off, chunk).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, staging);
            glDrawArrays(GL_TRIANGLES, 0, chunk / STRIDE);
            off += chunk;
        }
        glBindVertexArray(0);
        n = 0;
    }

    private void v(float x, float y, float u, float v, int c) {
        if (n + STRIDE > buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
        buf[n++] = x; buf[n++] = y; buf[n++] = u; buf[n++] = v;
        buf[n++] = ((c >> 16) & 255) / 255f; buf[n++] = ((c >> 8) & 255) / 255f;
        buf[n++] = (c & 255) / 255f; buf[n++] = ((c >>> 24) & 255) / 255f;
    }

    private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int c00, int c10, int c11, int c01) {
        v(x0, y0, u0, v0, c00); v(x1, y0, u1, v0, c10); v(x1, y1, u1, v1, c11);
        v(x0, y0, u0, v0, c00); v(x1, y1, u1, v1, c11); v(x0, y1, u0, v1, c01);
    }

    // ---------------------------------------------------------------- shapes

    public void rect(float x, float y, float w, float h, int c) {
        bind(whiteTex);
        quad(x, y, x + w, y + h, 0, 0, 1, 1, c, c, c, c);
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

    /** Thick line as a rotated quad. */
    public void line(float x1, float y1, float x2, float y2, float thick, int c) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * thick / 2, ny = dx / len * thick / 2;
        bind(whiteTex);
        v(x1 + nx, y1 + ny, 0, 0, c); v(x2 + nx, y2 + ny, 0, 0, c); v(x2 - nx, y2 - ny, 0, 0, c);
        v(x1 + nx, y1 + ny, 0, 0, c); v(x2 - nx, y2 - ny, 0, 0, c); v(x1 - nx, y1 - ny, 0, 0, c);
    }

    public void circle(float cx, float cy, float r, int c) {
        bind(whiteTex);
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            v(cx, cy, 0, 0, c);
            v(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, 0, 0, c);
            v(cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 0, 0, c);
        }
    }

    /** Draw an atlas tile (block face or item icon). */
    public void tile(Tile t, float x, float y, float w, float h, int tint) {
        bind(atlasTex);
        float[] uv = t.uv();
        quad(x, y, x + w, y + h, uv[0], uv[1], uv[2], uv[3], tint, tint, tint, tint);
    }

    /** Tile with a pixel-sharp edge: used for the panorama dirt background. */
    public void tileRepeat(Tile t, float x, float y, float w, float h, float cell, int tint) {
        for (float yy = y; yy < y + h; yy += cell)
            for (float xx = x; xx < x + w; xx += cell) {
                float cw = Math.min(cell, x + w - xx), ch = Math.min(cell, y + h - yy);
                bind(atlasTex);
                float[] uv = t.uv();
                quad(xx, yy, xx + cw, yy + ch, uv[0], uv[1], uv[0] + (uv[2] - uv[0]) * cw / cell, uv[1] + (uv[3] - uv[1]) * ch / cell, tint, tint, tint, tint);
            }
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
        if (shadow) {
            int sh = (color & 0xFF000000) | ((color >> 2) & 0x3F3F3F);
            f.emit(this, s, x + Math.max(1, size / 9), y + Math.max(1, size / 9), size, sh);
        }
        f.emit(this, s, x, y, size, color);
    }

    public void textCentered(String s, float cx, float y, float size, int color, boolean shadow, boolean boldFace) {
        text(s, cx - textWidth(s, size, boldFace) / 2, y, size, color, shadow, boldFace);
    }

    public void textRight(String s, float rx, float y, float size, int color, boolean shadow) {
        text(s, rx - textWidth(s, size), y, size, color, shadow, false);
    }

    /** Greedy word wrap into lines no wider than maxW. */
    public List<String> wrap(String s, float maxW, float size) {
        List<String> lines = new ArrayList<>();
        for (String para : s.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.isEmpty() ? word : line + " " + word;
                if (textWidth(trial, size) > maxW && !line.isEmpty()) { lines.add(line.toString()); line = new StringBuilder(word); }
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
}
