package org.anthropocene.htf.world;

import org.anthropocene.htf.gfx.PropBuilder;
import org.anthropocene.htf.gfx.StaticMesh;

import static org.anthropocene.htf.gfx.PropBuilder.*;

/** A wooden country boat (nouka), beached on the haor mud until the water lifts it. */
public final class Boat {
    public static final float X = -66f, Z = -58f, YAW = 0.5f;
    private final StaticMesh mesh;

    public Boat() {
        PropBuilder b = new PropBuilder();
        int stations = 18, around = 10;
        float L = 8f;
        int[][] grid = new int[stations + 1][around + 1];
        // lofted hull: half-beam and sheer vary along the length; hull sits with its keel at y=0
        float[][][] pts = new float[stations + 1][around + 1][];
        for (int i = 0; i <= stations; i++) {
            float t = i / (float) stations * 2 - 1;
            float beam = 0.78f * (float) Math.pow(Math.max(0, 1 - Math.pow(Math.abs(t), 2.4)), 0.55);
            float draft = 0.50f * (float) Math.pow(Math.max(0, 1 - Math.pow(Math.abs(t), 1.8)), 0.6) + 0.04f;
            float sheer = 0.52f + 0.55f * (float) Math.pow(Math.abs(t), 2.6);
            for (int j = 0; j <= around; j++) {
                float s = j / (float) around * 2 - 1;
                float x = beam * s;
                float y = (sheer - draft) + draft * (float) Math.pow(Math.abs(s), 2.2);
                pts[i][j] = new float[]{x, y, t * L / 2};
            }
        }
        // emit as a series of quads (double sided via the shader)
        for (int i = 0; i < stations; i++) for (int j = 0; j < around; j++) {
            float[] a = pts[i][j], c = pts[i][j + 1], d = pts[i + 1][j + 1], e = pts[i + 1][j];
            float[] n = normal(a, c, e);
            b.quad(a, c, d, e, n, new float[]{0.16f, 0.11f, 0.07f}, HULL, 1f, 1f);
        }
        // thwarts, a pole and a woven canopy frame
        for (float z : new float[]{-1.9f, -0.9f, 1.5f, 2.4f}) b.push().translate(0, 0.46f, z).box(1.35f, 0.045f, 0.18f, WOOD, 0.42f, 0.30f, 0.17f, 1f).pop();
        b.push().translate(0.25f, 0.62f, -3f).rotX((float) Math.PI / 2).cylinder(0.03f, 0.022f, 6f, 8, WOOD, 0.55f, 0.46f, 0.24f, true, 2f).pop();
        for (float z = -0.8f; z <= 1.0f; z += 0.45f) {
            float[][] arc = new float[9][3];
            float[] hw = new float[9];
            for (int k = 0; k < 9; k++) {
                double a = Math.PI * k / 8;
                arc[k] = new float[]{(float) Math.cos(a) * 0.68f, 0.5f + (float) Math.sin(a) * 0.82f, z};
                hw[k] = 0.02f;
            }
            b.ribbon(arc, hw, MAT, 0.66f, 0.54f, 0.32f, 1f);
        }
        mesh = b.build();
    }

    private static float[] normal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{nx / l, ny / l, nz / l};
    }

    public StaticMesh mesh() { return mesh; }
}
