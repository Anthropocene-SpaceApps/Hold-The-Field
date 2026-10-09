package org.anthropocene.htf.gfx;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.*;

/** Off-screen render target: one colour attachment (texture, or multisampled renderbuffer) plus depth. */
public final class Framebuffer {
    public final int fbo, width, height, samples;
    public int colorTex, colorRb, depthRb, depthTex;

    private Framebuffer(int w, int h, int samples) {
        this.width = w; this.height = h; this.samples = samples;
        fbo = glGenFramebuffers();
    }

    /** Floating-point colour target. samples > 1 makes a multisampled renderbuffer (resolve with {@link #blitTo}). */
    public static Framebuffer hdr(int w, int h, int samples, boolean depth) {
        Framebuffer f = new Framebuffer(w, h, samples);
        glBindFramebuffer(GL_FRAMEBUFFER, f.fbo);
        if (samples > 1) {
            f.colorRb = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, f.colorRb);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA16F, w, h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, f.colorRb);
        } else {
            f.colorTex = texture(w, h, GL_RGBA16F, GL_RGBA, GL_FLOAT, GL_LINEAR);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, f.colorTex, 0);
        }
        if (depth) {
            f.depthRb = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, f.depthRb);
            if (samples > 1) glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, w, h);
            else glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, f.depthRb);
        }
        f.check();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return f;
    }

    /** Depth-only target for shadow maps. */
    public static Framebuffer shadow(int size) {
        Framebuffer f = new Framebuffer(size, size, 1);
        glBindFramebuffer(GL_FRAMEBUFFER, f.fbo);
        f.depthTex = texture(size, size, GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, f.depthTex, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        f.check();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return f;
    }

    private static int texture(int w, int h, int internal, int format, int type, int filter) {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return t;
    }

    private void check() {
        int st = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (st != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(st));
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, width, height);
    }

    /** Resolve (multisampled -> texture target) or copy the colour buffer. */
    public void blitTo(Framebuffer dst) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, dst.fbo);
        glBlitFramebuffer(0, 0, width, height, 0, 0, dst.width, dst.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void destroy() {
        glDeleteFramebuffers(fbo);
        if (colorTex != 0) glDeleteTextures(colorTex);
        if (depthTex != 0) glDeleteTextures(depthTex);
        if (colorRb != 0) glDeleteRenderbuffers(colorRb);
        if (depthRb != 0) glDeleteRenderbuffers(depthRb);
    }
}
