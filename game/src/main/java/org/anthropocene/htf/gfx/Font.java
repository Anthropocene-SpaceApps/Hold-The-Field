package org.anthropocene.htf.gfx;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_RGBA;
import static org.lwjgl.stb.STBTruetype.*;

/** A baked TrueType font atlas (Latin-1). Glyph sizes are given in GUI pixels at draw time. */
public final class Font {
    public static final float BAKE = 32f;
    private static final int W = 512, H = 512, FIRST = 32, COUNT = 224;

    public final int texture;
    private final STBTTBakedChar.Buffer chars = STBTTBakedChar.malloc(COUNT);

    public Font(String resource) {
        ByteBuffer ttf;
        try (InputStream in = Font.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing font resource " + resource);
            byte[] bytes = in.readAllBytes();
            ttf = BufferUtils.createByteBuffer(bytes.length);
            ttf.put(bytes).flip();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read font " + resource, e);
        }
        ByteBuffer bitmap = BufferUtils.createByteBuffer(W * H);
        if (stbtt_BakeFontBitmap(ttf, BAKE, bitmap, W, H, FIRST, chars) <= 0) throw new IllegalStateException("Font atlas too small");
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, W, H, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_RGBA, new int[]{GL_ONE, GL_ONE, GL_ONE, GL_RED});
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /** Map a char to a baked glyph index; unknown characters become '?'. */
    private static int index(char c) {
        if (c == '–' || c == '—') c = '-';
        if (c == '→') c = '>';
        if (c < FIRST || c >= FIRST + COUNT) c = '?';
        return c - FIRST;
    }

    public float width(String s, float size) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += chars.get(index(s.charAt(i))).xadvance();
        return w * size / BAKE;
    }

    /** Emit glyph quads into the renderer. (x, y) is the top-left of the line box. */
    void emit(Renderer2D r, String s, float x, float y, float size, int argb) {
        float k = size / BAKE, baseY = y + size * 0.82f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // stb advances xb itself, so every quad already includes the running pen position.
            FloatBuffer xb = stack.floats(0), yb = stack.floats(0);
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            for (int i = 0; i < s.length(); i++) {
                stbtt_GetBakedQuad(chars, W, H, index(s.charAt(i)), xb, yb, q, true);
                r.glyph(x + q.x0() * k, baseY + q.y0() * k, x + q.x1() * k, baseY + q.y1() * k,
                        q.s0(), q.t0(), q.s1(), q.t1(), argb);
            }
        }
    }
}
