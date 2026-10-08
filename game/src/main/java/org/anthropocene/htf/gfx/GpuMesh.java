package org.anthropocene.htf.gfx;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** A VAO/VBO pair holding MeshBuilder vertices. Re-uploadable for per-frame dynamic geometry. */
public final class GpuMesh {
    private final int vao, vbo;
    private int count;
    private FloatBuffer staging;
    private final boolean dynamic;

    public GpuMesh(boolean dynamic) {
        this.dynamic = dynamic;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = MeshBuilder.STRIDE * 4;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 20);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    public void upload(MeshBuilder mb) {
        count = mb.vertexCount();
        if (staging == null || staging.capacity() < mb.size) staging = BufferUtils.createFloatBuffer(Math.max(mb.size, 1 << 14) * 2);
        staging.clear();
        staging.put(mb.data, 0, mb.size).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, staging, dynamic ? GL_STREAM_DRAW : GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        if (!dynamic) staging = null;
    }

    public void draw() {
        if (count == 0) return;
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, count);
        glBindVertexArray(0);
    }

    public int vertexCount() { return count; }

    public void destroy() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
