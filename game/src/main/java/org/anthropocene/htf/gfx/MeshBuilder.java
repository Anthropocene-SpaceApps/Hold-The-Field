package org.anthropocene.htf.gfx;

import java.util.Arrays;

/**
 * CPU-side triangle list. Vertex = x y z u v r g b a (9 floats). Face shading is baked into the colour,
 * so the shader needs no normals.
 */
public final class MeshBuilder {
    public static final int STRIDE = 9;
    public static final float SHADE_TOP = 1f, SHADE_BOTTOM = 0.55f, SHADE_NS = 0.84f, SHADE_EW = 0.7f;

    public static final int FACE_TOP = 1, FACE_BOTTOM = 2, FACE_N = 4, FACE_S = 8, FACE_W = 16, FACE_E = 32, FACE_ALL = 63;

    float[] data = new float[1 << 15];
    int size;                                    // floats used

    public void clear() { size = 0; }
    public int vertexCount() { return size / STRIDE; }
    public boolean isEmpty() { return size == 0; }

    private void ensure(int more) {
        if (size + more > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + more));
    }

    public void vert(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        ensure(STRIDE);
        data[size++] = x; data[size++] = y; data[size++] = z; data[size++] = u; data[size++] = v;
        data[size++] = r; data[size++] = g; data[size++] = b; data[size++] = a;
    }

    /** Corners in order bottom-left, bottom-right, top-right, top-left as seen from the visible side. */
    public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     float[] uv, float r, float g, float b, float a) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        vert(x0, y0, z0, u0, v1, r, g, b, a);
        vert(x1, y1, z1, u1, v1, r, g, b, a);
        vert(x2, y2, z2, u1, v0, r, g, b, a);
        vert(x0, y0, z0, u0, v1, r, g, b, a);
        vert(x2, y2, z2, u1, v0, r, g, b, a);
        vert(x3, y3, z3, u0, v0, r, g, b, a);
    }

    /** Axis-aligned box with a tile per face group. Tint multiplies the baked face shade. */
    public void box(float x, float y, float z, float sx, float sy, float sz,
                    Tile top, Tile side, Tile bottom, float r, float g, float b, float a, int faces) {
        float x1 = x + sx, y1 = y + sy, z1 = z + sz;
        if ((faces & FACE_TOP) != 0)
            quad(x, y1, z1, x1, y1, z1, x1, y1, z, x, y1, z, top.uv(), r * SHADE_TOP, g * SHADE_TOP, b * SHADE_TOP, a);
        if ((faces & FACE_BOTTOM) != 0)
            quad(x, y, z, x1, y, z, x1, y, z1, x, y, z1, bottom.uv(), r * SHADE_BOTTOM, g * SHADE_BOTTOM, b * SHADE_BOTTOM, a);
        if ((faces & FACE_S) != 0)  // +z
            quad(x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1, side.uv(), r * SHADE_NS, g * SHADE_NS, b * SHADE_NS, a);
        if ((faces & FACE_N) != 0)  // -z
            quad(x1, y, z, x, y, z, x, y1, z, x1, y1, z, side.uv(), r * SHADE_NS, g * SHADE_NS, b * SHADE_NS, a);
        if ((faces & FACE_E) != 0)  // +x
            quad(x1, y, z1, x1, y, z, x1, y1, z, x1, y1, z1, side.uv(), r * SHADE_EW, g * SHADE_EW, b * SHADE_EW, a);
        if ((faces & FACE_W) != 0)  // -x
            quad(x, y, z, x, y, z1, x, y1, z1, x, y1, z, side.uv(), r * SHADE_EW, g * SHADE_EW, b * SHADE_EW, a);
    }

    public void box(float x, float y, float z, float sx, float sy, float sz, Tile all, float r, float g, float b, float a) {
        box(x, y, z, sx, sy, sz, all, all, all, r, g, b, a, FACE_ALL);
    }

    /** Two crossed vertical quads (Minecraft plants). Centre at (cx, cz), base at y. */
    public void cross(float cx, float y, float cz, float w, float h, Tile tile, float r, float g, float b, float a) {
        float hw = w / 2;
        float[] uv = tile.uv();
        quad(cx - hw, y, cz - hw, cx + hw, y, cz + hw, cx + hw, y + h, cz + hw, cx - hw, y + h, cz - hw, uv, r, g, b, a);
        quad(cx - hw, y, cz + hw, cx + hw, y, cz - hw, cx + hw, y + h, cz - hw, cx - hw, y + h, cz + hw, uv, r, g, b, a);
    }

    /** Flat horizontal quad (top of a field tile, water surface). */
    public void flatTop(float x, float y, float z, float sx, float sz, Tile tile, float r, float g, float b, float a) {
        quad(x, y, z + sz, x + sx, y, z + sz, x + sx, y, z, x, y, z, tile.uv(), r, g, b, a);
    }
}
