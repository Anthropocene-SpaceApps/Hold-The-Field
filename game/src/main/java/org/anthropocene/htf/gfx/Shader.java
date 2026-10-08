package org.anthropocene.htf.gfx;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/** Compiled GLSL program with cached uniform lookups. */
public final class Shader {
    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public Shader(String vertexSrc, String fragmentSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc), fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) throw new IllegalStateException("Shader link failed: " + glGetProgramInfoLog(program));
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) throw new IllegalStateException("Shader compile failed: " + glGetShaderInfoLog(id));
        return id;
    }

    public void use() { glUseProgram(program); }

    private int loc(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public void set(String name, int v) { glUniform1i(loc(name), v); }
    public void set(String name, float v) { glUniform1f(loc(name), v); }
    public void set(String name, float x, float y, float z) { glUniform3f(loc(name), x, y, z); }
    public void set(String name, float x, float y, float z, float w) { glUniform4f(loc(name), x, y, z, w); }

    public void set(String name, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            m.get(fb);
            glUniformMatrix4fv(loc(name), false, fb);
        }
    }
}
