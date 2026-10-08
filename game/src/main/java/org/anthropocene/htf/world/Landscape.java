package org.anthropocene.htf.world;

/**
 * The terrain as a height field in metres. x east, y up, -z north (towards the Meghalaya hills).
 * A flat floodplain with Rahim's paddy and homestead lies south of a low crop-protection embankment (the bund);
 * north of it is the haor basin (dry mud in the boro season, a lake when the water rises), then the hills with
 * the river coming down from them.
 */
public final class Landscape {
    public static final float X0 = -600, X1 = 600, Z0 = -900, Z1 = 300;
    public static final float CELL = 2f;
    public static final int NX = (int) ((X1 - X0) / CELL), NZ = (int) ((Z1 - Z0) / CELL);

    // layout (metres)
    public static final float PLOT_X0 = -30, PLOT_X1 = 30, PLOT_Z0 = -22, PLOT_Z1 = 22;   // Rahim's paddy
    public static final float DIKE_Z = -40f, DIKE_X0 = -420f, DIKE_X1 = 420f;
    public static final float HOME_X = 72, HOME_Z = 4;                                    // homestead mound centre
    public static final float POND_X = 108, POND_Z = 34;
    public static final float POND_LEVEL = -0.35f;

    private final float[] h = new float[(NX + 1) * (NZ + 1)];

    public Landscape() {
        for (int j = 0; j <= NZ; j++) for (int i = 0; i <= NX; i++)
            h[j * (NX + 1) + i] = compute(X0 + i * CELL, Z0 + j * CELL);
    }

    // ------------------------------------------------------------------ noise (deterministic)

    private static float hash(int x, int z, int s) {
        int n = x * 374761393 + z * 668265263 + s * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float sm(float t) { return t * t * (3 - 2 * t); }
    private static float step(float a, float b, float x) { return sm(Math.max(0, Math.min(1, (x - a) / (b - a)))); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static float vnoise(float x, float z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = sm(x - xi), fz = sm(z - zi);
        float a = hash(xi, zi, seed), b = hash(xi + 1, zi, seed), c = hash(xi, zi + 1, seed), d = hash(xi + 1, zi + 1, seed);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }

    static float fbm(float x, float z, int seed, int oct) {
        float s = 0, amp = 0.5f, norm = 0;
        for (int o = 0; o < oct; o++) { s += amp * vnoise(x, z, seed + o * 17); norm += amp; x = x * 2.03f + 11.7f; z = z * 2.03f + 5.3f; amp *= 0.5f; }
        return s / norm;
    }

    /** x of the river's centre line at z (it meanders down from the hills into the haor). */
    public static float riverX(float z) { return -20 + 70 * (float) Math.sin(z * 0.0045) + 25 * (float) Math.sin(z * 0.013 + 1.3); }

    public static float riverBed(float z) { return -0.7f + Math.max(0, (-260 - z)) * 0.11f; }

    private float compute(float x, float z) {
        float hh = (fbm(x * 0.012f, z * 0.012f, 3, 3) - 0.5f) * 0.10f;                 // gentle undulation of the plain
        hh += Math.max(0, z - 150) * 0.012f + Math.max(0, Math.abs(x) - 380) * 0.01f * (z > -40 ? 1 : 0);

        // haor basin north of the embankment: dry mud flats with hummocks, filling when the water rises
        float basin = step(DIKE_Z - 4, DIKE_Z - 40, z) * (1 - step(330, 470, Math.abs(x))) * (1 - step(-300, -360, z));
        float bed = 0.14f - 0.24f * fbm(x * 0.006f, z * 0.006f, 8, 3) + (fbm(x * 0.05f, z * 0.05f, 12, 3) - 0.5f) * 0.50f;
        hh = lerp(hh, bed, basin);

        // hills and the ring of low ridges
        float hillN = step(-290, -430, z);
        float hillSide = step(360, 520, Math.abs(x)) * step(-40, -200, z);
        float ridged = 1 - Math.abs(2 * fbm(x * 0.006f, z * 0.006f, 21, 5) - 1);
        float hillH = 55 + 330 * step(-300, -880, z);
        float north = hillN * hillH * (0.30f + 0.70f * ridged) * (0.8f + 0.4f * fbm(x * 0.02f, z * 0.02f, 30, 3));
        float side = hillSide * (90 + 110 * ridged) * (0.5f + fbm(x * 0.01f, z * 0.01f, 33, 3));
        hh = lerp(hh, Math.max(hh, Math.max(north, side)), Math.max(hillN, hillSide));

        // river valley
        if (z < -250) {
            float dx = Math.abs(x - riverX(z));
            float width = 9 + (z < -400 ? 14 : 0);
            float carve = 1 - step(width, width + 45 + Math.max(0, -z - 400) * 0.08f, dx);
            float rb = riverBed(z) + dx * dx * 0.012f;
            hh = lerp(hh, Math.min(hh, rb), carve);
        }

        // homestead mound and the pond beside it
        float rm = (float) Math.hypot((x - HOME_X) * 0.85f, (z - HOME_Z) * 1.05f);
        hh += 1.7f * (1 - step(11, 27, rm));
        float rp = (float) Math.hypot(x - POND_X, z - POND_Z);
        hh -= 1.35f * (1 - step(5, 12, rp));
        return hh;
    }

    // ------------------------------------------------------------------ embankment (bund) footprint

    public static final float DIKE_REF_H = 0.6f;                         // height the mesh is authored at
    public static final float DIKE_CROWN = 1.3f, DIKE_SLOPE = 2.9f;      // half crown width and slope run (m)

    /** Fraction (0..1+) of the current embankment height at (x, z): crown, side slopes, tapered ends. */
    public static float dikeFraction(float x, float z) {
        float dz = Math.abs(z - DIKE_Z);
        if (dz >= DIKE_CROWN + DIKE_SLOPE) return 0;
        float f = dz <= DIKE_CROWN ? 1 : 1 - (dz - DIKE_CROWN) / DIKE_SLOPE;
        f = f * f * (3 - 2 * f);
        float taper = step(DIKE_X0, DIKE_X0 + 45, x) * (1 - step(DIKE_X1 - 45, DIKE_X1, x));
        float wobble = 1 + 0.07f * (vnoise(x * 0.07f, 3.1f, 55) - 0.5f);
        return f * taper * wobble;
    }

    // ------------------------------------------------------------------ queries

    public float node(int i, int j) { return h[Math.max(0, Math.min(NZ, j)) * (NX + 1) + Math.max(0, Math.min(NX, i))]; }

    public float height(double x, double z) {
        double gx = (x - X0) / CELL, gz = (z - Z0) / CELL;
        int i = (int) Math.floor(gx), j = (int) Math.floor(gz);
        float fx = (float) (gx - i), fz = (float) (gz - j);
        float a = node(i, j), b = node(i + 1, j), c = node(i, j + 1), d = node(i + 1, j + 1);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }

    public float[] normal(double x, double z) {
        float e = CELL;
        float dx = height(x + e, z) - height(x - e, z), dz = height(x, z + e) - height(x, z - e);
        float nx = -dx, ny = 2 * e, nz = -dz, len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new float[]{nx / len, ny / len, nz / len};
    }

    public float[] raw() { return h; }
}
