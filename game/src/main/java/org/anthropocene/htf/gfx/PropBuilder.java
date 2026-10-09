package org.anthropocene.htf.gfx;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Builds indexed triangle meshes from primitives under a transform stack.
 * Vertex layout: position(3) normal(3) colour(3) uv(2) material(1).
 */
public final class PropBuilder {
    public static final int STRIDE = 12;
    public static final int[] LAYOUT = {3, 3, 3, 2, 1};

    // material ids understood by props.frag
    public static final int FLAT = 0, WOOD = 1, TIN = 2, MAT = 3, THATCH = 4, LEAF = 5, MUD = 6, SKIN = 7, CLOTH = 8,
            CHECK = 9, GAUGE = 10, BARK = 11, FROND = 12, ROPE = 13, METAL = 14, HULL = 15;

    private float[] v = new float[1 << 14];
    private int[] idx = new int[1 << 14];
    private int vn, in;
    private Matrix4f m = new Matrix4f();
    private final Matrix3f nm = new Matrix3f();
    private final Deque<Matrix4f> stack = new ArrayDeque<>();

    public PropBuilder push() { stack.push(new Matrix4f(m)); return this; }
    public PropBuilder pop() { m = stack.pop(); return this; }
    public PropBuilder identity() { m.identity(); return this; }
    public PropBuilder translate(float x, float y, float z) { m.translate(x, y, z); return this; }
    public PropBuilder rotY(float a) { m.rotateY(a); return this; }
    public PropBuilder rotX(float a) { m.rotateX(a); return this; }
    public PropBuilder rotZ(float a) { m.rotateZ(a); return this; }
    public PropBuilder scale(float x, float y, float z) { m.scale(x, y, z); return this; }
    public Matrix4f matrix() { return m; }
    public void setMatrix(Matrix4f mm) { m = new Matrix4f(mm); }
    public int vertexCount() { return vn; }

    private int vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float u, float vv, int mat) {
        Vector3f p = m.transformPosition(new Vector3f(x, y, z));
        m.normal(nm);
        Vector3f n = nm.transform(new Vector3f(nx, ny, nz)).normalize();
        if ((vn + 1) * STRIDE > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = vn * STRIDE;
        v[o] = p.x; v[o + 1] = p.y; v[o + 2] = p.z; v[o + 3] = n.x; v[o + 4] = n.y; v[o + 5] = n.z;
        v[o + 6] = r; v[o + 7] = g; v[o + 8] = b; v[o + 9] = u; v[o + 10] = vv; v[o + 11] = mat;
        return vn++;
    }

    private void tri(int a, int b, int c) {
        if (in + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
        idx[in++] = a; idx[in++] = b; idx[in++] = c;
    }

    /** Quad with an explicit normal; corners counter-clockwise from the normal side. uv scaled in metres. */
    public PropBuilder quad(float[] a, float[] b, float[] c, float[] d, float[] n, float[] col, int mat, float uMax, float vMax) {
        int i0 = vertex(a[0], a[1], a[2], n[0], n[1], n[2], col[0], col[1], col[2], 0, 0, mat);
        int i1 = vertex(b[0], b[1], b[2], n[0], n[1], n[2], col[0], col[1], col[2], uMax, 0, mat);
        int i2 = vertex(c[0], c[1], c[2], n[0], n[1], n[2], col[0], col[1], col[2], uMax, vMax, mat);
        int i3 = vertex(d[0], d[1], d[2], n[0], n[1], n[2], col[0], col[1], col[2], 0, vMax, mat);
        tri(i0, i1, i2); tri(i0, i2, i3);
        return this;
    }

    /** Single triangle with an explicit normal. */
    public PropBuilder triangle(float[] a, float[] b, float[] c, float[] n, float[] col, int mat, float scale) {
        int i0 = vertex(a[0], a[1], a[2], n[0], n[1], n[2], col[0], col[1], col[2], a[0] * scale, a[1] * scale, mat);
        int i1 = vertex(b[0], b[1], b[2], n[0], n[1], n[2], col[0], col[1], col[2], b[0] * scale, b[1] * scale, mat);
        int i2 = vertex(c[0], c[1], c[2], n[0], n[1], n[2], col[0], col[1], col[2], c[0] * scale, c[1] * scale, mat);
        tri(i0, i1, i2);
        return this;
    }

    /** Axis-aligned box: x/z centred, y from 0 to h. UVs run in metres (times uvScale) so textures keep their scale. */
    public PropBuilder box(float w, float h, float d, int mat, float r, float g, float b, float uvScale) {
        float x0 = -w / 2, x1 = w / 2, z0 = -d / 2, z1 = d / 2;
        float[] c = {r, g, b};
        float uw = w * uvScale, uh = h * uvScale, ud = d * uvScale;
        quad(new float[]{x0, h, z1}, new float[]{x1, h, z1}, new float[]{x1, h, z0}, new float[]{x0, h, z0}, new float[]{0, 1, 0}, c, mat, uw, ud);
        quad(new float[]{x0, 0, z0}, new float[]{x1, 0, z0}, new float[]{x1, 0, z1}, new float[]{x0, 0, z1}, new float[]{0, -1, 0}, c, mat, uw, ud);
        quad(new float[]{x0, 0, z1}, new float[]{x1, 0, z1}, new float[]{x1, h, z1}, new float[]{x0, h, z1}, new float[]{0, 0, 1}, c, mat, uw, uh);
        quad(new float[]{x1, 0, z0}, new float[]{x0, 0, z0}, new float[]{x0, h, z0}, new float[]{x1, h, z0}, new float[]{0, 0, -1}, c, mat, uw, uh);
        quad(new float[]{x1, 0, z1}, new float[]{x1, 0, z0}, new float[]{x1, h, z0}, new float[]{x1, h, z1}, new float[]{1, 0, 0}, c, mat, ud, uh);
        quad(new float[]{x0, 0, z0}, new float[]{x0, 0, z1}, new float[]{x0, h, z1}, new float[]{x0, h, z0}, new float[]{-1, 0, 0}, c, mat, ud, uh);
        return this;
    }

    /** Tapered cylinder along +y. r0 at the base, r1 at the top. */
    public PropBuilder cylinder(float r0, float r1, float h, int seg, int mat, float r, float g, float b, boolean capTop, float uvScale) {
        float slope = (r0 - r1) / h;
        int[] ring = new int[(seg + 1) * 2];
        for (int i = 0; i <= seg; i++) {
            double a = i * Math.PI * 2 / seg;
            float cx = (float) Math.cos(a), sz = (float) Math.sin(a);
            float nl = (float) Math.sqrt(1 + slope * slope), nx = cx / nl, ny = slope / nl, nz = sz / nl;
            float u = (float) (a * r0 * uvScale);
            ring[i * 2] = vertex(cx * r0, 0, sz * r0, nx, ny, nz, r, g, b, u, 0, mat);
            ring[i * 2 + 1] = vertex(cx * r1, h, sz * r1, nx, ny, nz, r, g, b, u, h * uvScale, mat);
        }
        for (int i = 0; i < seg; i++) {
            int a = ring[i * 2], bb = ring[i * 2 + 1], c = ring[i * 2 + 2], d = ring[i * 2 + 3];
            tri(a, c, bb); tri(bb, c, d);
        }
        if (capTop && r1 > 1e-4f) {
            int centre = vertex(0, h, 0, 0, 1, 0, r, g, b, 0, 0, mat);
            int first = -1, prev = -1;
            for (int i = 0; i <= seg; i++) {
                double a = i * Math.PI * 2 / seg;
                int vi = vertex((float) Math.cos(a) * r1, h, (float) Math.sin(a) * r1, 0, 1, 0, r, g, b, 0, 0, mat);
                if (prev >= 0) tri(centre, prev, vi);
                prev = vi; if (first < 0) first = vi;
            }
        }
        return this;
    }

    public PropBuilder sphere(float radius, int seg, int rings, int mat, float r, float g, float b) {
        int[][] grid = new int[rings + 1][seg + 1];
        for (int j = 0; j <= rings; j++) {
            double phi = Math.PI * j / rings;
            for (int i = 0; i <= seg; i++) {
                double th = Math.PI * 2 * i / seg;
                float nx = (float) (Math.sin(phi) * Math.cos(th)), ny = (float) Math.cos(phi), nz = (float) (Math.sin(phi) * Math.sin(th));
                grid[j][i] = vertex(nx * radius, ny * radius, nz * radius, nx, ny, nz, r, g, b, (float) i / seg, (float) j / rings, mat);
            }
        }
        for (int j = 0; j < rings; j++) for (int i = 0; i < seg; i++) {
            tri(grid[j][i], grid[j + 1][i], grid[j][i + 1]);
            tri(grid[j][i + 1], grid[j + 1][i], grid[j + 1][i + 1]);
        }
        return this;
    }

    /** Vertical card, centred on x, base at y=0, facing +z. uv 0..1 so alpha masks work. */
    public PropBuilder card(float w, float h, int mat, float r, float g, float b) {
        float[] c = {r, g, b};
        int i0 = vertex(-w / 2, 0, 0, 0, 0.6f, 0.8f, r, g, b, 0, 0, mat);
        int i1 = vertex(w / 2, 0, 0, 0, 0.6f, 0.8f, r, g, b, 1, 0, mat);
        int i2 = vertex(w / 2, h, 0, 0, 0.6f, 0.8f, r, g, b, 1, 1, mat);
        int i3 = vertex(-w / 2, h, 0, 0, 0.6f, 0.8f, r, g, b, 0, 1, mat);
        tri(i0, i1, i2); tri(i0, i2, i3);
        return this;
    }

    /**
     * Strip following a polyline of (x, y, z) centre points with a half-width per point, for fronds, leaves and
     * rope. Lies flat in the local xz plane's direction of travel.
     */
    public PropBuilder ribbon(float[][] pts, float[] halfWidth, int mat, float r, float g, float b, float side) {
        int n = pts.length;
        int[] left = new int[n], right = new int[n];
        for (int i = 0; i < n; i++) {
            float[] p = pts[i];
            float[] q = pts[Math.min(n - 1, i + 1)], o = pts[Math.max(0, i - 1)];
            Vector3f t = new Vector3f(q[0] - o[0], q[1] - o[1], q[2] - o[2]).normalize();
            Vector3f nrm = new Vector3f(0, 1, 0), s = new Vector3f(t).cross(nrm).normalize();
            if (s.lengthSquared() < 1e-6f) s.set(1, 0, 0);
            Vector3f up = new Vector3f(s).cross(t).normalize();
            float v = (float) i / (n - 1);
            left[i] = vertex(p[0] - s.x * halfWidth[i], p[1] - s.y * halfWidth[i], p[2] - s.z * halfWidth[i], up.x, up.y, up.z, r, g, b, 0, v, mat);
            right[i] = vertex(p[0] + s.x * halfWidth[i], p[1] + s.y * halfWidth[i], p[2] + s.z * halfWidth[i], up.x, up.y, up.z, r, g, b, 1, v, mat);
        }
        for (int i = 0; i < n - 1; i++) { tri(left[i], right[i], right[i + 1]); tri(left[i], right[i + 1], left[i + 1]); }
        return this;
    }

    /** Lathe: radius profile along y (pairs y, r) revolved around the y axis. */
    public PropBuilder lathe(float[][] profile, int seg, int mat, float r, float g, float b) {
        int rows = profile.length;
        int[][] grid = new int[rows][seg + 1];
        for (int j = 0; j < rows; j++) {
            float dy = profile[Math.min(rows - 1, j + 1)][0] - profile[Math.max(0, j - 1)][0];
            float dr = profile[Math.min(rows - 1, j + 1)][1] - profile[Math.max(0, j - 1)][1];
            float nl = (float) Math.hypot(dy, dr);
            for (int i = 0; i <= seg; i++) {
                double a = i * Math.PI * 2 / seg;
                float cx = (float) Math.cos(a), sz = (float) Math.sin(a);
                float nr = dy / nl, ny = -dr / nl;
                grid[j][i] = vertex(cx * profile[j][1], profile[j][0], sz * profile[j][1], cx * nr, ny, sz * nr, r, g, b, (float) i / seg, (float) j / (rows - 1), mat);
            }
        }
        for (int j = 0; j < rows - 1; j++) for (int i = 0; i < seg; i++) {
            tri(grid[j][i], grid[j + 1][i], grid[j][i + 1]);
            tri(grid[j][i + 1], grid[j + 1][i], grid[j + 1][i + 1]);
        }
        return this;
    }

    public StaticMesh build() {
        return StaticMesh.create(Arrays.copyOf(v, vn * STRIDE), Arrays.copyOf(idx, in), LAYOUT);
    }

    public float[] vertices() { return Arrays.copyOf(v, vn * STRIDE); }
    public int[] indices() { return Arrays.copyOf(idx, in); }

    public void clear() { vn = 0; in = 0; m.identity(); stack.clear(); }
}
