package org.anthropocene.htf.gfx;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL30.*;

/** HDR post chain: threshold + dual-filter bloom, then exposure, ACES tonemap, grade, vignette, gamma. */
public final class Post {
    private static final int LEVELS = 5;
    private final Shader down = ShaderLoader.load2("fullscreen", "post_down");
    private final Shader up = ShaderLoader.load2("fullscreen", "post_up");
    private final Shader composite = ShaderLoader.load2("fullscreen", "post_composite");
    private final int emptyVao = glGenVertexArrays();
    private Framebuffer[] mips;
    private int w, h;

    private void ensure(int fbW, int fbH) {
        if (mips != null && w == fbW && h == fbH) return;
        if (mips != null) for (Framebuffer f : mips) f.destroy();
        w = fbW; h = fbH;
        mips = new Framebuffer[LEVELS];
        int mw = Math.max(1, fbW / 2), mh = Math.max(1, fbH / 2);
        for (int i = 0; i < LEVELS; i++) {
            mips[i] = Framebuffer.hdr(mw, mh, 1, false);
            mw = Math.max(1, mw / 2); mh = Math.max(1, mh / 2);
        }
    }

    private void tri() {
        glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    private static void tex(int unit, int id) { glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, id); }

    /** scene: resolved HDR target. Output goes to the default framebuffer. */
    public void run(Framebuffer scene, int outW, int outH, float exposure, float bloom, float threshold, float time, float vignette) {
        ensure(scene.width, scene.height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        down.use();
        down.set("uTex", 0);
        int src = scene.colorTex;
        float sw = scene.width, sh = scene.height;
        for (int i = 0; i < LEVELS; i++) {
            mips[i].bind();
            tex(0, src);
            down.set("uTexel", 1f / sw, 1f / sh);
            down.set("uThreshold", i == 0 ? threshold : 0f);
            tri();
            src = mips[i].colorTex;
            sw = mips[i].width; sh = mips[i].height;
        }
        up.use();
        up.set("uTex", 0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);
        glBlendEquation(GL_FUNC_ADD);
        for (int i = LEVELS - 1; i > 0; i--) {
            mips[i - 1].bind();
            tex(0, mips[i].colorTex);
            up.set("uTexel", 1f / mips[i].width, 1f / mips[i].height);
            up.set("uRadius", 1.3f);
            tri();
        }
        glDisable(GL_BLEND);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, outW, outH);
        composite.use();
        composite.set("uScene", 0);
        composite.set("uBloom", 1);
        tex(0, scene.colorTex);
        tex(1, mips[0].colorTex);
        composite.set("uExposure", exposure);
        composite.set("uBloomStrength", bloom);
        composite.set("uTime", time);
        composite.set("uVignette", vignette);
        tri();
        glActiveTexture(GL_TEXTURE0);
    }
}
