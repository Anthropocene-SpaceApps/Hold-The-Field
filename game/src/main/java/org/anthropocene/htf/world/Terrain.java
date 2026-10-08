package org.anthropocene.htf.world;

import org.anthropocene.htf.gfx.MeshBuilder;
import org.anthropocene.htf.gfx.Tile;

import static org.anthropocene.htf.gfx.MeshBuilder.*;

/**
 * The static voxel world: haor lake and embankment (south), farm field, homestead mound with Rahim's hut,
 * and the Meghalaya hills with a river valley to the north. Deterministic, built from a height map.
 *
 * Axes: +x east, +y up, -z north (towards the hills). A block at integer (x,y,z) fills [x,x+1]x[y,y+1]x[z,z+1].
 * Surface height h(x,z) is the y of the top face of the highest block in that column.
 */
public final class Terrain {
    public static final int X0 = -40, X1 = 48, Z0 = -70, Z1 = 30;      // grid bounds, exclusive upper
    public static final int PLAIN_X0 = -14, PLAIN_X1 = 22;              // lake + plain span (blocks)
    public static final int LAKE_Z0 = -16, BUND_Z = -1, PLAIN_Z1 = 20;
    public static final int FIELD = 8;                                  // field is FIELD x FIELD blocks at (0,0)
    public static final int MOUND_X0 = 12, MOUND_X1 = 20, MOUND_Z0 = 2, MOUND_Z1 = 10, MOUND_TOP = 4;
    public static final int HUT_X0 = 13, HUT_X1 = 18, HUT_Z0 = 4, HUT_Z1 = 8;
    public static final int LAKE_FLOOR = -2;
    public static final float RIVER_X0 = 3, RIVER_X1 = 6;

    private static final int W = X1 - X0, D = Z1 - Z0;
    private final int[] h = new int[W * D];
    private final int[] obstacle = new int[W * D];                      // extra solid tops (hut, tree trunks)
    private final Tile[] topTile = new Tile[W * D];

    public Terrain() {
        generate();
        decorateObstacles();
    }

    // ------------------------------------------------------------------ noise

    private static int hash(int x, int z, int seed) {
        int n = x * 374761393 + z * 668265263 + seed * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return n ^ (n >>> 16);
    }

    private static double rand01(int x, int z, int seed) { return (hash(x, z, seed) & 0xFFFFFF) / (double) 0x1000000; }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double vnoise(double x, double z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fz = smooth(z - zi);
        double a = rand01(xi, zi, seed), b = rand01(xi + 1, zi, seed), c = rand01(xi, zi + 1, seed), d = rand01(xi + 1, zi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    // ------------------------------------------------------------------ queries

    private static int idx(int x, int z) { return (z - Z0) * W + (x - X0); }
    private static boolean inGrid(int x, int z) { return x >= X0 && x < X1 && z >= Z0 && z < Z1; }

    public int height(int x, int z) {
        if (!inGrid(x, z)) { x = Math.max(X0, Math.min(X1 - 1, x)); z = Math.max(Z0, Math.min(Z1 - 1, z)); }
        return h[idx(x, z)];
    }

    /** Collision top of a column: terrain plus hut and tree trunks. */
    public double solidTop(int x, int z) {
        if (!inGrid(x, z)) return 64;
        return Math.max(h[idx(x, z)], obstacle[idx(x, z)]);
    }

    public static boolean isField(int x, int z) { return x >= 0 && x < FIELD && z >= 0 && z < FIELD; }
    public static boolean inLake(int x, int z) { return x >= PLAIN_X0 && x < PLAIN_X1 && z >= LAKE_Z0 && z < BUND_Z; }
    public static boolean inPlain(int x, int z) { return x >= PLAIN_X0 && x < PLAIN_X1 && z >= BUND_Z && z < PLAIN_Z1; }
    public static boolean onMound(int x, int z) { return x >= MOUND_X0 - 1 && x <= MOUND_X1 && z >= MOUND_Z0 - 1 && z <= MOUND_Z1; }

    // ------------------------------------------------------------------ generation

    private void generate() {
        for (int z = Z0; z < Z1; z++) for (int x = X0; x < X1; x++) {
            double cx = x + 0.5, cz = z + 0.5;
            double ex = Math.max(0, Math.max(PLAIN_X0 - cx, cx - PLAIN_X1));
            double en = Math.max(0, LAKE_Z0 - cz), es = Math.max(0, cz - PLAIN_Z1);
            double n = vnoise(x / 9.0, z / 9.0, 3) * 0.7 + vnoise(x / 4.0, z / 4.0, 5) * 0.3;
            int height;
            Tile top = Tile.GRASS_TOP;
            if (ex == 0 && en == 0 && es == 0) {
                if (inLake(x, z)) { height = LAKE_FLOOR; top = Tile.SAND; }
                else height = 1;
                if (x >= MOUND_X0 && x < MOUND_X1 && z >= MOUND_Z0 && z < MOUND_Z1) height = MOUND_TOP;
                else if (onMound(x, z)) height = 2;
            } else {
                double ramp = en * 0.5 + ex * 3.0 + es * 0.5;
                height = 1 + (int) Math.round(ramp + (n - 0.4) * (3 + ramp * 0.22));
                height = Math.max(1, Math.min(34, height));
                if (height > 20) top = Tile.STONE;
                if (en > 0 && Math.abs(cx - 4.5) < 12) {      // river valley carved through the hills
                    double t = smooth(Math.max(0, Math.min(1, (Math.abs(cx - 4.5) - 1.5) / 8.5)));
                    int valley = (int) Math.round(-1 + (height + 1) * t);
                    height = Math.min(height, Math.max(-1, valley));
                    if (valley <= 0) top = Tile.SAND;
                }
            }
            h[idx(x, z)] = height;
            topTile[idx(x, z)] = top;
        }
    }

    private boolean treeAt(int x, int z) {
        int hv = height(x, z);
        if (isField(x, z) || onMound(x, z) || inLake(x, z) || hv < 1 || hv > 16) return false;
        if (x >= -3 && x <= 10 && z >= -3 && z <= 18) return false;                 // keep the farm and spawn clear
        if (z >= BUND_Z - 1 && z <= BUND_Z) return false;
        if (x >= 3 - 2 && x <= 6 + 1 && z < LAKE_Z0) return false;                  // river bank
        int roll = hash(x, z, 77) & 1023;
        int limit = inPlain(x, z) ? 7 : 11;
        return roll < limit && hash(x, z, 91) % 2 == 0;
    }

    private void decorateObstacles() {
        for (int z = HUT_Z0 - 1; z <= HUT_Z1; z++) for (int x = HUT_X0 - 1; x <= HUT_X1; x++)
            obstacle[idx(x, z)] = MOUND_TOP + 4;
        for (int z = Z0 + 1; z < Z1 - 1; z++) for (int x = X0 + 1; x < X1 - 1; x++)
            if (treeAt(x, z)) obstacle[idx(x, z)] = height(x, z) + 4;
    }

    // ------------------------------------------------------------------ static mesh

    public void buildStatic(MeshBuilder mb) {
        // Ground columns
        for (int z = Z0; z < Z1; z++) for (int x = X0; x < X1; x++) {
            int hv = h[idx(x, z)];
            int nN = nh(x, z - 1), nS = nh(x, z + 1), nW = nh(x - 1, z), nE = nh(x + 1, z);
            int start = Math.min(hv - 1, Math.min(Math.min(nN, nS), Math.min(nW, nE)));
            Tile tt = topTile[idx(x, z)];
            Tile sideTop = tt == Tile.GRASS_TOP ? Tile.GRASS_SIDE : tt == Tile.SAND ? Tile.DIRT : tt;
            for (int y = start; y < hv; y++) {
                boolean isTop = y == hv - 1;
                int faces = 0;
                if (isTop && !isField(x, z)) faces |= FACE_TOP;
                if (nN <= y) faces |= FACE_N;
                if (nS <= y) faces |= FACE_S;
                if (nW <= y) faces |= FACE_W;
                if (nE <= y) faces |= FACE_E;
                if (faces == 0) continue;
                Tile side = isTop ? sideTop : (y >= hv - 3 ? Tile.DIRT : Tile.STONE);
                float shade = tt == Tile.GRASS_TOP && isTop ? 0.92f + (float) (rand01(x, z, 9) * 0.1) : 1f;
                mb.box(x, y, z, 1, 1, 1, tt, side, Tile.DIRT, shade, shade, shade, 1f, faces);
            }
        }
        // Flowers scattered on the plain
        for (int z = BUND_Z + 1; z < PLAIN_Z1; z++) for (int x = PLAIN_X0; x < PLAIN_X1; x++) {
            if (isField(x, z) || onMound(x, z) || (x >= -2 && x <= 9 && z >= 0 && z <= 9)) continue;
            if ((hash(x, z, 31) & 31) == 0) mb.cross(x + 0.5f, 1, z + 0.5f, 0.7f, 0.7f, Tile.FLOWER, 1, 1, 1, 1);
        }
        trees(mb);
        hut(mb);
        fence(mb);
        hay(mb);
    }

    private int nh(int x, int z) { return inGrid(x, z) ? h[idx(x, z)] : -64; }

    private void trees(MeshBuilder mb) {
        for (int z = Z0 + 1; z < Z1 - 1; z++) for (int x = X0 + 1; x < X1 - 1; x++) {
            if (!treeAt(x, z)) continue;
            int base = h[idx(x, z)], trunk = 4 + (hash(x, z, 5) & 1);
            for (int y = 0; y < trunk; y++) mb.box(x, base + y, z, 1, 1, 1, Tile.LOG_TOP, Tile.LOG_SIDE, Tile.LOG_TOP, 1, 1, 1, 1, FACE_ALL);
            float tint = 0.9f + (float) (rand01(x, z, 6) * 0.15);
            for (int dy = 0; dy < 2; dy++) for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (dx == 0 && dz == 0 && dy == 0) continue;
                mb.box(x + dx, base + trunk - 2 + dy, z + dz, 1, 1, 1, Tile.LEAVES, tint, tint, tint, 1);
            }
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (Math.abs(dx) + Math.abs(dz) == 2) continue;
                mb.box(x + dx, base + trunk, z + dz, 1, 1, 1, Tile.LEAVES, tint, tint, tint, 1);
            }
        }
    }

    private void hut(MeshBuilder mb) {
        int y0 = MOUND_TOP;
        for (int x = HUT_X0; x < HUT_X1; x++) for (int z = HUT_Z0; z < HUT_Z1; z++) {
            boolean edge = x == HUT_X0 || x == HUT_X1 - 1 || z == HUT_Z0 || z == HUT_Z1 - 1;
            if (!edge) continue;
            boolean corner = (x == HUT_X0 || x == HUT_X1 - 1) && (z == HUT_Z0 || z == HUT_Z1 - 1);
            for (int y = 0; y < 3; y++) {
                Tile side = Tile.PLANKS;
                if (corner) side = Tile.LOG_SIDE;
                boolean door = z == HUT_Z1 - 1 && x == HUT_X0 + 2 && y < 2;
                boolean window = z == HUT_Z1 - 1 && x == HUT_X0 + 3 && y == 1 || (x == HUT_X0 && z == HUT_Z0 + 1 && y == 1);
                if (door) side = y == 0 ? Tile.DOOR : Tile.DOOR;
                if (window) side = Tile.WINDOW;
                mb.box(x, y0 + y, z, 1, 1, 1, Tile.PLANKS, side, Tile.PLANKS, 1, 1, 1, 1, FACE_ALL);
            }
        }
        // thatched roof, three stepped layers
        for (int layer = 0; layer < 3; layer++) {
            int inset = layer;
            float rx = HUT_X0 - 1 + inset, rz = HUT_Z0 - 1 + inset;
            float sx = (HUT_X1 - HUT_X0) + 2 - inset * 2, sz = (HUT_Z1 - HUT_Z0) + 2 - inset * 2;
            if (sx <= 0 || sz <= 0) break;
            for (int bx = 0; bx < (int) sx; bx++) for (int bz = 0; bz < (int) sz; bz++)
                mb.box(rx + bx, y0 + 3 + layer, rz + bz, 1, 1, 1, Tile.THATCH, 0.95f, 0.95f, 0.95f, 1);
        }
    }

    private void fence(MeshBuilder mb) {
        float post = 0.22f, hgt = 0.95f;
        for (int i = -1; i <= FIELD; i++) {
            fencePost(mb, i, FIELD, post, hgt);                 // south edge
            if (i >= 0 && i < FIELD) { fencePost(mb, -1, i, post, hgt); fencePost(mb, FIELD, i, post, hgt); }
        }
        // two rails along the south edge and both sides (the north edge is the bund)
        float[] railY = {1.3f, 1.65f};
        for (float ry : railY) {
            mb.box(-0.5f, ry, FIELD + 0.45f, FIELD + 1, 0.1f, 0.1f, Tile.PLANKS, 1, 1, 1, 1);
            mb.box(-0.55f, ry, 0, 0.1f, 0.1f, FIELD, Tile.PLANKS, 1, 1, 1, 1);
            mb.box(FIELD + 0.45f, ry, 0, 0.1f, 0.1f, FIELD, Tile.PLANKS, 1, 1, 1, 1);
        }
    }

    private void fencePost(MeshBuilder mb, int x, int z, float post, float hgt) {
        mb.box(x + 0.5f - post / 2, 1, z + 0.5f - post / 2, post, hgt, post, Tile.LOG_TOP, Tile.LOG_SIDE, Tile.LOG_TOP, 1, 1, 1, 1, FACE_ALL);
    }

    private void hay(MeshBuilder mb) {
        int[][] bales = {{HUT_X0 - 2, HUT_Z1 + 1}, {HUT_X0 - 2, HUT_Z1 + 2}, {HUT_X1 + 0, HUT_Z0}, {HUT_X1 + 0, HUT_Z0 + 1}};
        for (int[] b : bales) {
            if (b[0] < MOUND_X0 || b[0] >= MOUND_X1 || b[1] >= MOUND_Z1) continue;
            mb.box(b[0], MOUND_TOP, b[1], 1, 1, 1, Tile.HAY_TOP, Tile.HAY_SIDE, Tile.HAY_TOP, 1, 1, 1, 1, FACE_ALL);
        }
    }
}
