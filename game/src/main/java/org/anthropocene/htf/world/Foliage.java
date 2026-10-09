package org.anthropocene.htf.world;

import org.anthropocene.htf.gfx.Shader;
import org.anthropocene.htf.gfx.ShaderLoader;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/** Instanced animated plants: Rahim's rice, meadow grass and reeds. Each instance is a clump of blades. */
public final class Foliage {
    public enum Kind { RICE, GRASS, REED }

    private static final int BLADES = 6, ROWS = 4;
    private final Shader shader = ShaderLoader.load("rice");
    private final int vao, bladeVbo, ibo, indexCount;

    private static final class Set {
        int vbo, count;
        Kind kind;
    }

    private final Set rice = new Set(), grass = new Set(), reed = new Set();

    public Foliage(Landscape land, Farmstead farm) {
        // one clump of blades
        Random r = new Random(11);
        float[] v = new float[BLADES * ROWS * 2 * 6];
        int[] idx = new int[BLADES * (ROWS - 1) * 6];
        int vi = 0, ii = 0;
        for (int bl = 0; bl < BLADES; bl++) {
            float ox = (r.nextFloat() - 0.5f) * 0.14f, oz = (r.nextFloat() - 0.5f) * 0.14f, yaw = (float) (bl * Math.PI * 2 / BLADES) + r.nextFloat() * 0.8f, hs = 0.8f + r.nextFloat() * 0.35f;
            int first = vi / 6;
            for (int row = 0; row < ROWS; row++) for (int side = -1; side <= 1; side += 2) {
                v[vi++] = ox; v[vi++] = oz; v[vi++] = yaw; v[vi++] = hs;
                v[vi++] = row / (float) (ROWS - 1); v[vi++] = side;
            }
            for (int row = 0; row < ROWS - 1; row++) {
                int a = first + row * 2, b = a + 1, c = a + 2, d = a + 3;
                idx[ii++] = a; idx[ii++] = b; idx[ii++] = c; idx[ii++] = b; idx[ii++] = d; idx[ii++] = c;
            }
        }
        indexCount = ii;
        vao = glGenVertexArrays();
        bladeVbo = glGenBuffers();
        ibo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, bladeVbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(v.length);
        fb.put(v).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 24, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 24, 16);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        IntBuffer ib = BufferUtils.createIntBuffer(idx.length);
        ib.put(idx).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        glBindVertexArray(0);

        rice.kind = Kind.RICE;
        grass.kind = Kind.GRASS;
        reed.kind = Kind.REED;
        upload(rice, ricePositions());
        upload(grass, grassPositions(land, farm));
        upload(reed, reedPositions(land));
    }

    // ------------------------------------------------------------------ instance generation

    private static List<float[]> ricePositions() {
        Random r = new Random(5);
        List<float[]> out = new ArrayList<>();
        float x0 = Landscape.PLOT_X0 + 0.7f, x1 = Landscape.PLOT_X1 - 0.7f, z0 = Landscape.PLOT_Z0 + 0.7f, z1 = Landscape.PLOT_Z1 - 0.7f;
        float sp = 0.27f;
        for (float z = z0; z < z1; z += sp) for (float x = x0; x < x1; x += sp)
            out.add(new float[]{x + (r.nextFloat() - 0.5f) * 0.08f, z + (r.nextFloat() - 0.5f) * 0.08f, r.nextFloat(), 0.9f + r.nextFloat() * 0.2f});
        Collections.shuffle(out, r);
        return out;
    }

    private static float segDist(float px, float pz, float ax, float az, float bx, float bz) {
        float pax = px - ax, paz = pz - az, bax = bx - ax, baz = bz - az;
        float t = Math.max(0, Math.min(1, (pax * bax + paz * baz) / (bax * bax + baz * baz)));
        return (float) Math.hypot(pax - bax * t, paz - baz * t);
    }

    private static List<float[]> grassPositions(Landscape land, Farmstead farm) {
        Random r = new Random(9);
        List<float[]> out = new ArrayList<>();
        // regions (cx, cz, half-x, half-z, count)
        float[][] zones = {{72, 6, 55, 42, 42000}, {0, -26, 60, 16, 7000}, {0, 0, 52, 40, 16000}, {20, 60, 70, 40, 9000}, {-60, 10, 50, 40, 5000}};
        for (float[] zn : zones) {
            int attempts = (int) zn[4] * 2, got = 0;
            for (int i = 0; i < attempts && got < zn[4]; i++) {
                float x = zn[0] + (r.nextFloat() * 2 - 1) * zn[2], z = zn[1] + (r.nextFloat() * 2 - 1) * zn[3];
                if (!grassAllowed(land, farm, x, z)) continue;
                out.add(new float[]{x, z, r.nextFloat(), 0.8f + r.nextFloat() * 0.6f});
                got++;
            }
        }
        Collections.shuffle(out, r);
        return out;
    }

    private static boolean grassAllowed(Landscape land, Farmstead farm, float x, float z) {
        if (x > Landscape.PLOT_X0 - 2.2f && x < Landscape.PLOT_X1 + 2.2f && z > Landscape.PLOT_Z0 - 2.2f && z < Landscape.PLOT_Z1 + 2.2f) return false;
        float yard = (float) Math.hypot((x - 72) * 0.9, (z - 4) * 1.1);
        if (yard < 11) return false;
        if (segDist(x, z, 31, 0, 56, 6) < 1.6f || segDist(x, z, 72, 16, 70, 80) < 1.5f || segDist(x, z, -6, -36, -5, 24) < 1.4f) return false;
        if (land.height(x, z) < -0.2f) return false;
        if (z < -31) return false;                                   // basin side belongs to reeds
        for (Farmstead.Obstacle o : farm.obstacles()) {
            if (o.circle() ? Math.hypot(x - o.x(), z - o.z()) < o.hx() + 0.2f : Math.abs(x - o.x()) < o.hx() + 1.2f && Math.abs(z - o.z()) < o.hz() + 1.2f) return false;
        }
        return true;
    }

    private static List<float[]> reedPositions(Landscape land) {
        Random r = new Random(13);
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < 60000 && out.size() < 16000; i++) {
            float x = -300 + r.nextFloat() * 600, z = -42 - r.nextFloat() * 150;
            float y = land.height(x, z);
            if (y < -0.12f || y > 0.28f) continue;
            float n = (float) (Math.sin(x * 0.035) * Math.cos(z * 0.05) + Math.sin(x * 0.11 + z * 0.07)) * 0.5f;
            if (n < 0.12f) continue;
            out.add(new float[]{x, z, r.nextFloat(), 0.8f + r.nextFloat() * 0.5f});
        }
        Collections.shuffle(out, r);
        return out;
    }

    private void upload(Set s, List<float[]> pts) {
        s.count = pts.size();
        FloatBuffer fb = BufferUtils.createFloatBuffer(Math.max(4, s.count * 4));
        for (float[] p : pts) fb.put(p);
        fb.flip();
        s.vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, s.vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
    }

    // ------------------------------------------------------------------ drawing

    public int riceCount() { return rice.count; }

    private void draw(Set s, double fraction) {
        int n = (int) (s.count * fraction);
        if (n <= 0) return;
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, s.vbo);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(2);
        glVertexAttribDivisor(2, 1);
        glDrawElementsInstanced(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0, n);
        glVertexAttribDivisor(2, 0);
        glBindVertexArray(0);
    }

    /** common() must already have been applied to {@link #shader()} by the caller. */
    public Shader shader() { return shader; }

    public void drawRice(double height, double mat, double droop, double dead, double lean, double wind, double fraction, boolean visible) {
        if (!visible) return;
        shader.set("uHeight", (float) height);
        shader.set("uWidth", 0.018f);
        shader.set("uWind", (float) wind);
        shader.set("uDroop", (float) droop);
        shader.set("uLean", (float) lean);
        shader.set("uFade", 95f);
        shader.set("uMat", (float) mat);
        shader.set("uDead", (float) dead);
        shader.set("uPalette", 0f);
        draw(rice, fraction);
    }

    public void drawGrass(double wind, double fraction) {
        shader.set("uHeight", 0.34f);
        shader.set("uWidth", 0.016f);
        shader.set("uWind", (float) wind);
        shader.set("uDroop", 0.25f);
        shader.set("uLean", 0f);
        shader.set("uFade", 70f);
        shader.set("uMat", 0f);
        shader.set("uDead", 0f);
        shader.set("uPalette", 1f);
        draw(grass, fraction);
    }

    public void drawReeds(double wind, double fraction) {
        shader.set("uHeight", 1.5f);
        shader.set("uWidth", 0.02f);
        shader.set("uWind", (float) wind);
        shader.set("uDroop", 0.3f);
        shader.set("uLean", 0f);
        shader.set("uFade", 150f);
        shader.set("uMat", 0f);
        shader.set("uDead", 0f);
        shader.set("uPalette", 2f);
        draw(reed, fraction);
    }
}
