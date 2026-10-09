package org.anthropocene.htf.world;

import org.anthropocene.htf.gfx.Camera;
import org.anthropocene.htf.gfx.Shader;
import org.anthropocene.htf.gfx.ShaderLoader;
import org.anthropocene.htf.gfx.StaticMesh;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/** Rain streaks around the camera, rain curtains over the hills (the upstream rain) and sprite particles. */
public final class Weather {
    private final Shader rainS = ShaderLoader.load("rain");
    private final Shader curtainS = ShaderLoader.load("curtain");
    private final Shader particleS = ShaderLoader.load("particle");
    private final int emptyVao = glGenVertexArrays();
    private final StaticMesh curtains;
    private final int pVao, pVbo;
    private FloatBuffer pbuf = BufferUtils.createFloatBuffer(8 * 600);

    public Weather() {
        // vertical sheets arranged in an arc over the hills (the catchment upstream)
        int n = 14;
        float[] v = new float[n * 4 * 5];
        int[] idx = new int[n * 6];
        int k = 0;
        for (int i = 0; i < n; i++) {
            float a0 = -0.9f + 1.8f * i / n, a1 = -0.9f + 1.8f * (i + 1) / n;
            float rad = 560f + (i % 3) * 70f;
            float x0 = 20 + (float) Math.sin(a0) * rad * 1.05f, z0 = -330 - (float) Math.cos(a0) * rad * 0.55f - 160;
            float x1 = 20 + (float) Math.sin(a1) * rad * 1.05f, z1 = -330 - (float) Math.cos(a1) * rad * 0.55f - 160;
            float y0 = 40f, y1 = 330f;
            float[][] c = {{x0, y0, z0, 0, 0}, {x1, y0, z1, 1, 0}, {x1, y1, z1, 1, 1}, {x0, y1, z0, 0, 1}};
            int base = k / 5;
            for (float[] q : c) for (float f : q) v[k++] = f;
            int t = i * 6;
            idx[t] = base; idx[t + 1] = base + 1; idx[t + 2] = base + 2; idx[t + 3] = base; idx[t + 4] = base + 2; idx[t + 5] = base + 3;
        }
        curtains = StaticMesh.create(v, idx, 3, 2);
        pVao = glGenVertexArrays();
        pVbo = glGenBuffers();
        glBindVertexArray(pVao);
        glBindBuffer(GL_ARRAY_BUFFER, pVbo);
        glBufferData(GL_ARRAY_BUFFER, 8 * 4 * 600L, GL_STREAM_DRAW);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 32, 0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 32, 16);
        glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
        glVertexAttribDivisor(0, 1); glVertexAttribDivisor(1, 1);
        glBindVertexArray(0);
    }

    public Shader rainShader() { return rainS; }
    public Shader curtainShader() { return curtainS; }

    public void drawCurtains(Camera cam, double intensity) {
        if (intensity < 0.04) return;
        curtainS.set("uVP", cam.viewProj);
        curtainS.set("uIntensity", (float) intensity);
        curtains.draw();
    }

    public void drawRain(Camera cam, double intensity, double windX, double windZ) {
        if (intensity < 0.03) return;
        rainS.set("uVP", cam.viewProj);
        rainS.set("uWind", (float) windX, 0f, (float) windZ);
        rainS.set("uFall", 13f + (float) intensity * 7f);
        rainS.set("uIntensity", (float) Math.min(1, 0.45 + intensity));
        int count = (int) (14000 * Math.min(1, intensity));
        glBindVertexArray(emptyVao);
        glDrawArraysInstanced(GL_TRIANGLES, 0, 6, count);
        glBindVertexArray(0);
    }

    /** particles: x y z life vx vy vz ... r g b stored as float[]{x,y,z,vx,vy,vz,life,r,g,b} */
    public void drawParticles(Camera cam, List<float[]> particles) {
        if (particles.isEmpty()) return;
        int n = Math.min(600, particles.size());
        pbuf.clear();
        for (int i = 0; i < n; i++) {
            float[] q = particles.get(i);
            float a = Math.min(1f, q[6] * 1.5f);
            pbuf.put(q[0]).put(q[1]).put(q[2]).put(0.07f + 0.05f * q[6]);
            pbuf.put(q[7]).put(q[8]).put(q[9]).put(a * 0.9f);
        }
        pbuf.flip();
        glBindBuffer(GL_ARRAY_BUFFER, pVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, pbuf);
        particleS.use();
        Matrix4f inv = new Matrix4f(cam.view).invert();
        particleS.set("uVP", cam.viewProj);
        particleS.set("uRight", inv.m00(), inv.m01(), inv.m02());
        particleS.set("uUp", inv.m10(), inv.m11(), inv.m12());
        glBindVertexArray(pVao);
        glDrawArraysInstanced(GL_TRIANGLES, 0, 6, n);
        glBindVertexArray(0);
    }
}
