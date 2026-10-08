package org.anthropocene.htf.gfx;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Indexed triangle mesh on the GPU with interleaved float attributes. */
public final class StaticMesh {
    private final int vao, vbo, ibo;
    private int count;

    private StaticMesh(float[] verts, int[] idx, boolean dynamic, int[] sizes) {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ibo = glGenBuffers();
        count = idx.length;
        glBindVertexArray(vao);
        FloatBuffer fb = BufferUtils.createFloatBuffer(verts.length);
        fb.put(verts).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, dynamic ? GL_DYNAMIC_DRAW : GL_STATIC_DRAW);
        IntBuffer ib = BufferUtils.createIntBuffer(idx.length);
        ib.put(idx).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        int stride = 0;
        for (int s : sizes) stride += s * 4;
        int off = 0;
        for (int i = 0; i < sizes.length; i++) {
            glVertexAttribPointer(i, sizes[i], GL_FLOAT, false, stride, off);
            glEnableVertexAttribArray(i);
            off += sizes[i] * 4;
        }
        glBindVertexArray(0);
    }

    /** Attribute sizes in floats, e.g. (3, 3) for position + normal. */
    public static StaticMesh create(float[] verts, int[] idx, int... attribSizes) {
        return new StaticMesh(verts, idx, false, attribSizes);
    }

    /** Replace the whole mesh (used for the per-frame character mesh). */
    public void update(float[] verts, int[] idx) {
        count = idx.length;
        FloatBuffer fb = BufferUtils.createFloatBuffer(verts.length);
        fb.put(verts).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_DYNAMIC_DRAW);
        IntBuffer ib = BufferUtils.createIntBuffer(idx.length);
        ib.put(idx).flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_DYNAMIC_DRAW);
        glBindVertexArray(0);
    }

    public static StaticMesh createDynamic(float[] verts, int[] idx, int... attribSizes) {
        return new StaticMesh(verts, idx, true, attribSizes);
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public int indexCount() { return count; }

    public void destroy() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ibo);
        glDeleteVertexArrays(vao);
    }
}
