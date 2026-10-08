package org.anthropocene.htf.gfx;

/** Tiles of the 16x16 texture atlas (each 16x16 pixels). Order = atlas index. */
public enum Tile {
    GRASS_TOP, GRASS_SIDE, DIRT, STONE, SAND, FARMLAND, STUBBLE, MUD_BRICK,
    PLANKS, LOG_SIDE, LOG_TOP, THATCH, LEAVES, HAY_SIDE, HAY_TOP, SACK,
    WATER0, WATER1, WATER2, WATER3, CLOUD, RAIN, BEAM, WHITE,
    CROP0, CROP1, CROP2, CROP3, CROP_DEAD, SKIN, FACE, SHIRT,
    PANTS, HAIR, METAL, SOLAR, DOOR, WINDOW, FLOWER, SNOW,
    ICON_COIN, ICON_SICKLE, ICON_SPYGLASS, ICON_BRICK, ICON_WHEAT, ICON_SATELLITE, ICON_DROP, ICON_HEART;

    public static final int GRID = 16, SIZE = 16, ATLAS = GRID * SIZE;

    private final float[] uv = new float[4];

    Tile() {
        int i = ordinal(), cx = i % GRID, cy = i / GRID;
        float e = 0.02f / ATLAS;  // tiny inset against bleeding
        uv[0] = (float) cx / GRID + e;
        uv[1] = (float) cy / GRID + e;
        uv[2] = (float) (cx + 1) / GRID - e;
        uv[3] = (float) (cy + 1) / GRID - e;
    }

    /** u0, v0, u1, v1 */
    public float[] uv() { return uv; }

    public int px() { return (ordinal() % GRID) * SIZE; }
    public int py() { return (ordinal() / GRID) * SIZE; }

    /** Water animation frame for a time in seconds. */
    public static Tile water(double t) {
        return switch ((int) (t * 3) & 3) { case 0 -> WATER0; case 1 -> WATER1; case 2 -> WATER2; default -> WATER3; };
    }
}
