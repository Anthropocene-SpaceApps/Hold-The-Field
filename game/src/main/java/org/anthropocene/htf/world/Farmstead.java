package org.anthropocene.htf.world;

import org.anthropocene.htf.gfx.PropBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.anthropocene.htf.gfx.PropBuilder.*;

/**
 * Everything built by hand on the landscape: Rahim's house and shed, yard props, trees, the plot bunds and the
 * flood gauge. Produces one static mesh plus the obstacles the player bumps into.
 */
public final class Farmstead {
    /** Axis-aligned footprint or a circle (trunks, pump). */
    public record Obstacle(float x, float z, float hx, float hz, boolean circle) {}

    public static final float GAUGE_X = 8f, GAUGE_Z = -45.5f;
    public static final float HOUSE_X = 72f, HOUSE_Z = 2f;

    private final Landscape land;
    private final PropBuilder b = new PropBuilder();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final Random rnd = new Random(7);

    public Farmstead(Landscape land) {
        this.land = land;
        house();
        shed();
        yard();
        plotBunds();
        gauge();
        trees();
    }

    public PropBuilder builder() { return b; }
    public List<Obstacle> obstacles() { return obstacles; }

    private float gy(float x, float z) { return land.height(x, z); }

    private void rect(float cx, float cz, float hx, float hz) { obstacles.add(new Obstacle(cx, cz, hx, hz, false)); }
    private void circle(float cx, float cz, float r) { obstacles.add(new Obstacle(cx, cz, r, r, true)); }

    // ------------------------------------------------------------------ buildings

    private void house() {
        float cx = HOUSE_X, cz = HOUSE_Z;
        float L = 7.4f, D = 4.6f, wallH = 2.3f, y0 = 0.45f;
        b.push().translate(cx, gy(cx, cz) - 0.05f, cz).rotY((float) -Math.PI / 2);
        b.push().box(8.2f, y0 + 0.05f, 5.4f, MUD, 0.52f, 0.42f, 0.30f, 0.6f).pop();                // plinth
        float[] jute = {0.80f, 0.66f, 0.42f};
        b.push().translate(0, y0, -D / 2).box(L, wallH, 0.12f, MAT, jute[0], jute[1], jute[2], 1f).pop();
        b.push().translate(-L / 2, y0, 0).box(0.12f, wallH, D, MAT, jute[0], jute[1], jute[2], 1f).pop();
        b.push().translate(L / 2, y0, 0).box(0.12f, wallH, D, MAT, jute[0], jute[1], jute[2], 1f).pop();
        b.push().translate(-2.7f, y0, D / 2).box(2.0f, wallH, 0.12f, MAT, jute[0], jute[1], jute[2], 1f).pop();
        b.push().translate(1.5f, y0, D / 2).box(4.4f, wallH, 0.12f, MAT, jute[0], jute[1], jute[2], 1f).pop();
        b.push().translate(-1.2f, y0 + 1.95f, D / 2).box(1.0f, 0.35f, 0.12f, MAT, jute[0], jute[1], jute[2], 1f).pop();
        b.push().translate(-1.2f, y0, D / 2 + 0.02f).box(0.95f, 1.95f, 0.05f, WOOD, 0.36f, 0.24f, 0.13f, 1f).pop();   // door
        for (float wx : new float[]{1.2f, 2.8f}) {
            b.push().translate(wx, y0 + 0.95f, D / 2 + 0.03f).box(0.72f, 0.8f, 0.05f, WOOD, 0.42f, 0.30f, 0.17f, 1f).pop();
        }
        for (float sx : new float[]{-L / 2, L / 2}) for (float sz : new float[]{-D / 2, D / 2})
            b.push().translate(sx, y0, sz).cylinder(0.07f, 0.065f, wallH + 0.4f, 8, WOOD, 0.52f, 0.42f, 0.22f, true, 2f).pop();
        for (float px : new float[]{-3.4f, -1.2f, 1.0f, 3.2f})
            b.push().translate(px, y0, D / 2 + 0.95f).cylinder(0.065f, 0.06f, wallH + 0.1f, 8, WOOD, 0.52f, 0.42f, 0.22f, true, 2f).pop();

        // corrugated tin gable roof
        float pitch = (float) Math.toRadians(28), halfSpan = 3.4f, slopeLen = halfSpan / (float) Math.cos(pitch), rise = (float) Math.tan(pitch) * halfSpan;
        float base = y0 + wallH + 0.12f;
        b.push().translate(0, base + rise / 2f, halfSpan / 2).rotX(pitch).translate(0, -0.025f, 0).box(L + 1.3f, 0.05f, slopeLen, TIN, 0.6f, 0.6f, 0.6f, 1f).pop();
        b.push().translate(0, base + rise / 2f, -halfSpan / 2).rotX(-pitch).translate(0, -0.025f, 0).box(L + 1.3f, 0.05f, slopeLen, TIN, 0.6f, 0.6f, 0.6f, 1f).pop();
        b.push().translate(0, base + rise - 0.02f, 0).box(L + 1.35f, 0.1f, 0.5f, TIN, 0.5f, 0.5f, 0.5f, 1f).pop();
        for (float gx : new float[]{-L / 2, L / 2}) {
            float apex = y0 + wallH + rise * (D / 2) / halfSpan;
            b.triangle(new float[]{gx, y0 + wallH, -D / 2}, new float[]{gx, y0 + wallH, D / 2}, new float[]{gx, apex + 0.1f, 0f},
                    new float[]{gx > 0 ? 1 : -1, 0, 0}, jute, MAT, 1.2f);
        }
        b.pop();
        // footprint after the -90 degree turn: length along world z
        rect(cx, cz, 2.9f, 3.9f);
    }

    private void shed() {
        float cx = 86f, cz = 12f;
        float L = 4.6f, D = 3.2f, wallH = 1.9f, y0 = 0.3f;
        b.push().translate(cx, gy(cx, cz) - 0.05f, cz).rotY(0.35f);
        b.push().box(L + 0.6f, y0 + 0.05f, D + 0.6f, MUD, 0.5f, 0.4f, 0.28f, 0.6f).pop();
        float[] mudw = {0.62f, 0.50f, 0.36f};
        b.push().translate(0, y0, -D / 2).box(L, wallH, 0.2f, MUD, mudw[0], mudw[1], mudw[2], 0.7f).pop();
        b.push().translate(-L / 2, y0, 0).box(0.2f, wallH, D, MUD, mudw[0], mudw[1], mudw[2], 0.7f).pop();
        b.push().translate(L / 2, y0, 0).box(0.2f, wallH, D, MUD, mudw[0], mudw[1], mudw[2], 0.7f).pop();
        b.push().translate(-1.4f, y0, D / 2).box(1.6f, wallH, 0.2f, MUD, mudw[0], mudw[1], mudw[2], 0.7f).pop();
        b.push().translate(1.4f, y0, D / 2).box(1.6f, wallH, 0.2f, MUD, mudw[0], mudw[1], mudw[2], 0.7f).pop();
        float pitch = (float) Math.toRadians(40), halfSpan = 2.35f, slope = halfSpan / (float) Math.cos(pitch), rise = (float) Math.tan(pitch) * halfSpan;
        float base = y0 + wallH + 0.05f;
        float[] straw = {0.82f, 0.68f, 0.36f};
        b.push().translate(0, base + rise / 2, halfSpan / 2).rotX(pitch).translate(0, -0.12f, 0).box(L + 1.1f, 0.24f, slope, THATCH, straw[0], straw[1], straw[2], 1f).pop();
        b.push().translate(0, base + rise / 2, -halfSpan / 2).rotX(-pitch).translate(0, -0.12f, 0).box(L + 1.1f, 0.24f, slope, THATCH, straw[0], straw[1], straw[2], 1f).pop();
        b.push().translate(0, base + rise - 0.06f, 0).box(L + 1.15f, 0.2f, 0.45f, THATCH, straw[0] * 0.9f, straw[1] * 0.9f, straw[2] * 0.9f, 1f).pop();
        b.pop();
        rect(cx, cz, 3.0f, 2.4f);
    }

    // ------------------------------------------------------------------ yard

    private void yard() {
        // haystack
        float hx = 62f, hz = -8f;
        b.push().translate(hx, gy(hx, hz) - 0.05f, hz);
        b.lathe(new float[][]{{0f, 1.25f}, {0.5f, 1.3f}, {1.1f, 1.1f}, {1.7f, 0.75f}, {2.2f, 0.35f}, {2.55f, 0.0f}}, 20, THATCH, 0.86f, 0.72f, 0.38f);
        b.pop();
        circle(hx, hz, 1.3f);
        // firewood stack
        for (int i = 0; i < 12; i++) {
            float fx = 78f + (i % 4) * 0.27f, fz = -5f, fy = (i / 4) * 0.25f;
            b.push().translate(fx, gy(fx, fz) + 0.12f + fy, fz).rotX((float) Math.PI / 2).cylinder(0.11f, 0.11f, 1.6f, 8, BARK, 0.45f, 0.33f, 0.2f, true, 2f).pop();
        }
        // tubewell hand pump
        float px = 66f, pz = 8f;
        b.push().translate(px, gy(px, pz), pz);
        b.box(0.7f, 0.18f, 0.7f, MUD, 0.5f, 0.45f, 0.4f, 1f);
        b.cylinder(0.07f, 0.07f, 0.95f, 12, METAL, 0.15f, 0.35f, 0.55f, true, 2f);
        b.push().translate(0, 0.9f, 0).rotZ(0.5f).cylinder(0.025f, 0.025f, 0.7f, 8, METAL, 0.75f, 0.1f, 0.08f, true, 2f).pop();
        b.pop();
        circle(px, pz, 0.5f);
        // clay pots
        for (int i = 0; i < 3; i++) {
            float cx = 70f + i * 0.5f, cz = 9.5f;
            b.push().translate(cx, gy(cx, cz), cz);
            b.lathe(new float[][]{{0f, 0.12f}, {0.08f, 0.2f}, {0.22f, 0.27f}, {0.38f, 0.22f}, {0.47f, 0.13f}, {0.52f, 0.17f}}, 14, MUD, 0.62f, 0.30f, 0.17f);
            b.pop();
        }
        // grain drying mats and baskets
        for (int i = 0; i < 3; i++) {
            float mx = 76f + (i % 2) * 2.2f, mz = 9f + i * 1.0f;
            b.push().translate(mx, gy(mx, mz) + 0.02f, mz).rotY(i * 0.3f).box(2.0f, 0.04f, 1.4f, THATCH, 0.9f, 0.76f, 0.35f, 1f).pop();
        }
        // bamboo yard fence on the west side
        for (float z = -9f; z <= 12f; z += 0.85f) {
            float fx = 63.5f;
            b.push().translate(fx, gy(fx, z) - 0.05f, z).cylinder(0.032f, 0.03f, 1.15f + rnd.nextFloat() * 0.15f, 6, WOOD, 0.55f, 0.46f, 0.24f, true, 2f).pop();
        }
        for (float ry : new float[]{0.4f, 0.85f})
            b.push().translate(63.5f, gy(63.5f, 0f) + ry, -9f).rotX((float) Math.PI / 2).cylinder(0.022f, 0.022f, 21f, 6, WOOD, 0.55f, 0.46f, 0.24f, true, 2f).pop();
        // coir rope between two posts
        b.push().translate(73f, gy(73f, 14f) + 1.6f, 14f).rotZ((float) Math.PI / 2).cylinder(0.012f, 0.012f, 6f, 6, ROPE, 0.6f, 0.5f, 0.3f, true, 2f).pop();
    }

    // ------------------------------------------------------------------ plot bunds and gauge

    private void plotBunds() {
        float x0 = Landscape.PLOT_X0, x1 = Landscape.PLOT_X1, z0 = Landscape.PLOT_Z0, z1 = Landscape.PLOT_Z1;
        float[] mud = {0.34f, 0.25f, 0.16f};
        for (float z : new float[]{z0 - 0.3f, z1 + 0.3f}) {
            for (float x = x0 - 0.6f; x < x1 + 0.6f; x += 3f) {
                b.push().translate(x + 1.5f, gy(x, z) + 0.06f, z).rotZ((float) -Math.PI / 2).scale(0.55f, 1f, 1f).cylinder(0.3f, 0.3f, 3.1f, 10, MUD, mud[0], mud[1], mud[2], false, 0.8f).pop();
            }
        }
        for (float x : new float[]{x0 - 0.3f, x1 + 0.3f}) {
            for (float z = z0; z < z1; z += 3f) {
                b.push().translate(x, gy(x, z) + 0.06f, z + 1.5f).rotX((float) Math.PI / 2).scale(1f, 1f, 0.55f).cylinder(0.3f, 0.3f, 3.1f, 10, MUD, mud[0], mud[1], mud[2], false, 0.8f).pop();
            }
        }
    }

    private void gauge() {
        b.push().translate(GAUGE_X, gy(GAUGE_X, GAUGE_Z) - 0.4f, GAUGE_Z).box(0.14f, 3.2f, 0.07f, GAUGE, 1f, 1f, 1f, 1f).pop();
        b.push().translate(GAUGE_X, gy(GAUGE_X, GAUGE_Z) - 0.5f, GAUGE_Z - 0.05f).box(0.2f, 0.7f, 0.2f, WOOD, 0.4f, 0.3f, 0.2f, 1f).pop();
        circle(GAUGE_X, GAUGE_Z, 0.25f);
    }

    // ------------------------------------------------------------------ trees

    private void trees() {
        float[][] mango = {{60, -12}, {82, -10}, {64, 16}, {92, 2}, {50, 10}};
        for (float[] p : mango) mango(p[0], p[1], 0.95f + rnd.nextFloat() * 0.35f);
        for (int i = 0; i < 12; i++) areca(56 + rnd.nextFloat() * 42, -10 + rnd.nextFloat() * 28, 8f + rnd.nextFloat() * 5f);
        float[][] coco = {{94, 20}, {97, 8}, {55, 22}};
        for (float[] p : coco) palm(p[0], p[1], 11f + rnd.nextFloat() * 3, 3.4f, 0.4f);
        for (int i = 0; i < 10; i++) banana(62 + rnd.nextFloat() * 8, 10 + rnd.nextFloat() * 7);
        bamboo(98, -14, 14); bamboo(58, -16, 10); bamboo(122, 42, 12);
        for (float z = 34; z < 90; z += 17) { mango(66 + rnd.nextFloat() * 2, z, 1.3f); mango(76 + rnd.nextFloat() * 2, z + 6, 1.2f); }
        float[][] woods = {{170, -18}, {-120, 24}, {-95, 62}, {150, 70}};
        for (float[] w : woods) for (int i = 0; i < 6; i++) mango(w[0] + rnd.nextFloat() * 26, w[1] + rnd.nextFloat() * 22, 1.0f + rnd.nextFloat() * 0.5f);
    }

    private void mango(float x, float z, float s) {
        float y = gy(x, z) - 0.05f;
        b.push().translate(x, y, z).rotY(rnd.nextFloat() * 6.28f).scale(s, s, s);
        b.cylinder(0.34f, 0.2f, 2.7f, 10, BARK, 0.30f, 0.23f, 0.16f, false, 1.6f);
        int limbs = 5;
        for (int i = 0; i < limbs; i++) {
            float yaw = (float) (i * Math.PI * 2 / limbs) + rnd.nextFloat() * 0.5f, tilt = 0.7f + rnd.nextFloat() * 0.35f;
            b.push().translate(0, 2.3f, 0).rotY(yaw).rotZ(-tilt).cylinder(0.14f, 0.07f, 2.3f, 7, BARK, 0.30f, 0.23f, 0.16f, true, 1.6f).pop();
        }
        for (int i = 0; i < 280; i++) {
            double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(1 - rnd.nextDouble() * 1.1);
            float rr = 1.0f + rnd.nextFloat() * 2.5f;
            float px = (float) (Math.sin(ph) * Math.cos(th)) * rr * 1.25f, pz = (float) (Math.sin(ph) * Math.sin(th)) * rr * 1.25f, py = 3.5f + (float) Math.cos(ph) * rr * 0.95f;
            float g = 0.20f + rnd.nextFloat() * 0.14f, rr2 = 0.05f + rnd.nextFloat() * 0.06f;
            b.push().translate(px, py, pz).rotY(rnd.nextFloat() * 6.28f).rotX((rnd.nextFloat() - 0.5f) * 1.2f).card(1.05f, 1.3f, LEAF, rr2, g, 0.035f).pop();
        }
        b.pop();
        circle(x, z, 0.4f * s);
    }

    private void palm(float x, float z, float h, float frondLen, float lean) {
        float y = gy(x, z) - 0.05f;
        float yaw = rnd.nextFloat() * 6.28f;
        b.push().translate(x, y, z).rotY(yaw);
        int segs = 6;
        float seg = h / segs, ox = 0, oy = 0;
        for (int i = 0; i < segs; i++) {
            float r0 = 0.17f - 0.07f * i / segs, r1 = 0.17f - 0.07f * (i + 1) / segs;
            float a0 = lean * (i / (float) segs), a1 = lean * ((i + 1) / (float) segs);
            b.push().translate(ox, oy, 0).rotZ(-(a0 + a1) / 2).cylinder(r0, r1, seg * 1.02f, 8, BARK, 0.42f, 0.36f, 0.27f, i == segs - 1, 1.4f).pop();
            ox += (float) Math.sin((a0 + a1) / 2) * seg;
            oy += (float) Math.cos((a0 + a1) / 2) * seg;
        }
        b.translate(ox, oy, 0);
        int fronds = 11;
        for (int i = 0; i < fronds; i++) {
            float fy = (float) (i * Math.PI * 2 / fronds) + rnd.nextFloat() * 0.3f, up = 0.35f + rnd.nextFloat() * 0.55f;
            int n = 9;
            float[][] pts = new float[n][3];
            float[] hw = new float[n];
            for (int k = 0; k < n; k++) {
                float t = k / (float) (n - 1);
                float len = frondLen * t;
                pts[k][0] = (float) Math.cos(fy) * len;
                pts[k][2] = (float) Math.sin(fy) * len;
                pts[k][1] = (float) (Math.sin(up) * len * (1 - 0.0f) - t * t * frondLen * 0.55f) + 0.2f;
                hw[k] = 0.42f * (float) Math.sin(Math.PI * Math.min(1, t * 1.15 + 0.08)) + 0.03f;
            }
            float g = 0.10f + rnd.nextFloat() * 0.05f;
            b.ribbon(pts, hw, FROND, g * 0.8f, 0.26f + g, 0.05f, 1f);
        }
        b.pop();
        circle(x, z, 0.25f);
    }

    private void areca(float x, float z, float h) { palm(x, z, h, 2.1f, 0.05f + rnd.nextFloat() * 0.12f); }

    private void banana(float x, float z) {
        float y = gy(x, z) - 0.03f;
        b.push().translate(x, y, z).rotY(rnd.nextFloat() * 6.28f);
        b.cylinder(0.13f, 0.09f, 2.0f, 8, FLAT, 0.38f, 0.5f, 0.2f, true, 1f);
        for (int i = 0; i < 7; i++) {
            float yaw = (float) (i * Math.PI * 2 / 7) + rnd.nextFloat() * 0.5f, up = 0.5f + rnd.nextFloat() * 0.5f;
            int n = 7;
            float[][] pts = new float[n][3];
            float[] hw = new float[n];
            for (int k = 0; k < n; k++) {
                float t = k / (float) (n - 1);
                float len = 2.6f * t;
                pts[k][0] = (float) Math.cos(yaw) * len;
                pts[k][2] = (float) Math.sin(yaw) * len;
                pts[k][1] = 1.9f + (float) Math.sin(up) * len * 0.8f - t * t * 1.5f;
                hw[k] = 0.55f;
            }
            b.ribbon(pts, hw, LEAF, 0.12f + rnd.nextFloat() * 0.05f, 0.36f + rnd.nextFloat() * 0.1f, 0.06f, 1f);
        }
        b.pop();
        circle(x, z, 0.2f);
    }

    private void bamboo(float x, float z, int culms) {
        float y = gy(x, z) - 0.05f;
        for (int i = 0; i < culms; i++) {
            float ox = (rnd.nextFloat() - 0.5f) * 3.2f, oz = (rnd.nextFloat() - 0.5f) * 3.2f, h = 7.5f + rnd.nextFloat() * 3f;
            float lean = (rnd.nextFloat() - 0.5f) * 0.35f, yaw = rnd.nextFloat() * 6.28f;
            b.push().translate(x + ox, y, z + oz).rotY(yaw).rotZ(lean);
            b.cylinder(0.045f, 0.022f, h, 6, WOOD, 0.45f, 0.5f, 0.2f, true, 3f);
            for (float ny = 0.6f; ny < h; ny += 0.9f) b.push().translate(0, ny, 0).cylinder(0.052f, 0.052f, 0.04f, 6, WOOD, 0.35f, 0.4f, 0.15f, false, 3f).pop();
            for (int k = 0; k < 7; k++) {
                b.push().translate(0, h - 2.2f + k * 0.4f, 0).rotY(rnd.nextFloat() * 6.28f).rotX(0.9f + rnd.nextFloat() * 0.4f).card(0.28f, 1.1f, LEAF, 0.12f, 0.3f, 0.06f).pop();
            }
            b.pop();
        }
        circle(x, z, 1.8f);
    }
}
