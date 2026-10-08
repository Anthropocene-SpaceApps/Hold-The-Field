package org.anthropocene.htf.gfx;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/** Generates every block/item texture in code (no art files needed) and uploads them as one atlas. */
public final class Atlas {
    private Atlas() {}

    private static final int N = Tile.ATLAS;
    private static int[] px;   // ARGB

    private static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
    private static int rgba(int r, int g, int b, int a) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static void set(Tile t, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= Tile.SIZE || y >= Tile.SIZE) return;
        px[(t.py() + y) * N + t.px() + x] = argb;
    }

    private static int get(Tile t, int x, int y) { return px[(t.py() + y) * N + t.px() + x]; }

    private static void noise(Tile t, int r, int g, int b, int spread, long seed) {
        Random rnd = new Random(seed);
        for (int y = 0; y < Tile.SIZE; y++) for (int x = 0; x < Tile.SIZE; x++) {
            int d = rnd.nextInt(spread * 2 + 1) - spread;
            set(t, x, y, rgb(r + d, g + d, b + d));
        }
    }

    private static void fill(Tile t, int argb) {
        for (int y = 0; y < Tile.SIZE; y++) for (int x = 0; x < Tile.SIZE; x++) set(t, x, y, argb);
    }

    private static void clear(Tile t) { fill(t, 0); }

    private static void shade(Tile t, int x, int y, int delta) {
        int c = get(t, x, y);
        set(t, x, y, (c & 0xFF000000) | (clamp(((c >> 16) & 255) + delta) << 16) | (clamp(((c >> 8) & 255) + delta) << 8) | clamp((c & 255) + delta));
    }

    public static int[] generate() {
        px = new int[N * N];
        Random rnd = new Random(7);

        noise(Tile.GRASS_TOP, 98, 160, 54, 16, 1);
        for (int i = 0; i < 14; i++) shade(Tile.GRASS_TOP, rnd.nextInt(16), rnd.nextInt(16), rnd.nextBoolean() ? 22 : -22);

        noise(Tile.DIRT, 134, 96, 67, 14, 2);
        for (int i = 0; i < 12; i++) shade(Tile.DIRT, rnd.nextInt(16), rnd.nextInt(16), -26);

        noise(Tile.GRASS_SIDE, 134, 96, 67, 14, 3);
        for (int x = 0; x < 16; x++) {
            int depth = 3 + (rnd.nextInt(3) == 0 ? 1 : 0) + (rnd.nextInt(5) == 0 ? 1 : 0);
            for (int y = 0; y < depth; y++) set(Tile.GRASS_SIDE, x, y, rgb(98 + rnd.nextInt(24) - 12, 160 + rnd.nextInt(24) - 12, 54 + rnd.nextInt(12) - 6));
        }

        noise(Tile.STONE, 125, 125, 128, 12, 4);
        for (int i = 0; i < 18; i++) shade(Tile.STONE, rnd.nextInt(16), rnd.nextInt(16), -30);
        for (int i = 0; i < 6; i++) { int x = rnd.nextInt(14), y = rnd.nextInt(16); for (int k = 0; k < 3; k++) shade(Tile.STONE, x + k, y, -38); }

        noise(Tile.SAND, 219, 207, 142, 8, 5);
        for (int i = 0; i < 10; i++) shade(Tile.SAND, rnd.nextInt(16), rnd.nextInt(16), -16);

        noise(Tile.FARMLAND, 112, 74, 44, 10, 6);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { if (x % 4 == 0) shade(Tile.FARMLAND, x, y, -26); if (x % 4 == 2) shade(Tile.FARMLAND, x, y, 8); }

        noise(Tile.STUBBLE, 206, 176, 112, 12, 7);
        for (int i = 0; i < 40; i++) { int x = rnd.nextInt(16), y = rnd.nextInt(14); set(Tile.STUBBLE, x, y, rgb(228, 200, 132)); set(Tile.STUBBLE, x, y + 1, rgb(188, 156, 92)); }

        // Mud brick bund
        noise(Tile.MUD_BRICK, 156, 108, 72, 10, 8);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean row = y % 4 == 3;
            int offset = (y / 4) % 2 == 0 ? 0 : 4;
            boolean col = (x + offset) % 8 == 7;
            if (row || col) set(Tile.MUD_BRICK, x, y, rgb(104 + rnd.nextInt(10), 74 + rnd.nextInt(8), 50));
        }

        noise(Tile.PLANKS, 168, 134, 80, 8, 9);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { if (y % 4 == 3) shade(Tile.PLANKS, x, y, -34); else if (rnd.nextInt(9) == 0) shade(Tile.PLANKS, x, y, -14); }

        noise(Tile.LOG_SIDE, 104, 82, 50, 10, 10);
        for (int x = 0; x < 16; x++) if (x % 3 == 0) for (int y = 0; y < 16; y++) shade(Tile.LOG_SIDE, x, y, -20);
        noise(Tile.LOG_TOP, 170, 138, 84, 8, 11);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
            if (d > 6.2) set(Tile.LOG_TOP, x, y, rgb(104 + rnd.nextInt(10), 82, 50));
            else if (((int) d) % 2 == 1) shade(Tile.LOG_TOP, x, y, -22);
        }

        noise(Tile.THATCH, 206, 176, 88, 14, 12);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { if ((x + y) % 5 == 0) shade(Tile.THATCH, x, y, -34); if ((x * 3 + y) % 7 == 0) shade(Tile.THATCH, x, y, 18); }

        // Leaves: cut-out holes
        noise(Tile.LEAVES, 62, 130, 44, 20, 13);
        for (int i = 0; i < 38; i++) set(Tile.LEAVES, rnd.nextInt(16), rnd.nextInt(16), 0);

        noise(Tile.HAY_SIDE, 212, 174, 56, 10, 14);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { if (x % 2 == 0 && rnd.nextInt(3) == 0) shade(Tile.HAY_SIDE, x, y, -28); if (y == 5 || y == 10) set(Tile.HAY_SIDE, x, y, rgb(142, 104, 36)); }
        noise(Tile.HAY_TOP, 196, 156, 44, 10, 15);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if ((x + y) % 4 == 0) shade(Tile.HAY_TOP, x, y, -26);

        noise(Tile.SACK, 196, 164, 104, 10, 16);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { if ((x + y) % 3 == 0) shade(Tile.SACK, x, y, -12); if (y == 2 && x > 3 && x < 12) set(Tile.SACK, x, y, rgb(120, 88, 50)); }

        // Water frames (translucent), flowing ripples
        for (int f = 0; f < 4; f++) {
            Tile t = Tile.values()[Tile.WATER0.ordinal() + f];
            Random wr = new Random(40 + f);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                double w = Math.sin((x + f * 4) * 0.7 + Math.sin(y * 0.6) * 1.5) * 0.5 + 0.5;
                int d = (int) (w * 22) - 8 + wr.nextInt(6);
                set(t, x, y, rgba(46 + d, 104 + d, 176 + d / 2, 186));
            }
        }

        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int g = 244 + rnd.nextInt(11) - ((x / 4 + y / 4) % 2 == 0 ? 6 : 0);
            set(Tile.CLOUD, x, y, rgba(g, g, g, 232));
        }

        clear(Tile.RAIN);
        for (int y = 0; y < 16; y++) set(Tile.RAIN, 7, y, rgba(170, 200, 255, y % 8 < 5 ? 190 : 60));
        for (int y = 0; y < 16; y++) set(Tile.RAIN, 8, y, rgba(170, 200, 255, y % 8 < 5 ? 110 : 30));

        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double edge = 1 - Math.abs(x - 7.5) / 8.0;
            set(Tile.BEAM, x, y, rgba(255, 255, 255, (int) (edge * edge * 150)));
        }
        fill(Tile.WHITE, rgb(255, 255, 255));

        crop(Tile.CROP0, 4, 100, 190, 80, 0);
        crop(Tile.CROP1, 8, 70, 160, 60, 0);
        crop(Tile.CROP2, 11, 150, 178, 60, 1);
        crop(Tile.CROP3, 14, 226, 186, 70, 2);
        crop(Tile.CROP_DEAD, 6, 120, 100, 70, 3);

        noise(Tile.SKIN, 224, 176, 140, 8, 20);
        noise(Tile.FACE, 224, 176, 140, 6, 21);
        for (int x = 3; x < 13; x++) for (int y = 0; y < 4; y++) set(Tile.FACE, x, y, rgb(46, 32, 26));
        set(Tile.FACE, 4, 7, rgb(255, 255, 255)); set(Tile.FACE, 5, 7, rgb(60, 40, 30));
        set(Tile.FACE, 11, 7, rgb(60, 40, 30)); set(Tile.FACE, 12, 7, rgb(255, 255, 255));
        for (int x = 6; x < 10; x++) set(Tile.FACE, x, 12, rgb(150, 84, 70));
        noise(Tile.SHIRT, 52, 96, 170, 8, 22);
        noise(Tile.PANTS, 70, 66, 92, 8, 23);
        noise(Tile.HAIR, 44, 32, 26, 6, 24);
        noise(Tile.METAL, 178, 182, 192, 8, 25);
        for (int i = 0; i < 6; i++) set(Tile.METAL, 1 + (i % 3) * 7, 1 + (i / 3) * 13, rgb(96, 100, 112));
        noise(Tile.SOLAR, 26, 52, 124, 6, 26);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (x % 4 == 0 || y % 4 == 0) set(Tile.SOLAR, x, y, rgb(96, 134, 208));
        noise(Tile.DOOR, 122, 88, 52, 8, 27);
        for (int y = 0; y < 16; y++) set(Tile.DOOR, 8, y, rgb(70, 48, 28));
        set(Tile.DOOR, 11, 8, rgb(230, 200, 90));
        noise(Tile.WINDOW, 150, 190, 220, 8, 28);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (x == 0 || y == 0 || x == 15 || y == 15 || x == 7 || y == 7) set(Tile.WINDOW, x, y, rgb(110, 80, 48));
        clear(Tile.FLOWER);
        for (int y = 5; y < 16; y++) set(Tile.FLOWER, 8, y, rgb(60, 140, 50));
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) set(Tile.FLOWER, 8 + dx, 4 + dy, rgb(240, 214, 70));
        set(Tile.FLOWER, 8, 4, rgb(210, 120, 40));
        noise(Tile.SNOW, 244, 247, 252, 6, 29);

        icons();
        return px;
    }

    /** kind: 0 plain blades, 1 grain heads, 2 heavy drooping heads, 3 dead and bent */
    private static void crop(Tile t, int height, int r, int g, int b, int kind) {
        clear(t);
        Random rnd = new Random(t.ordinal() * 31L);
        for (int blade = 0; blade < 7; blade++) {
            int x = 1 + blade * 2 + (blade % 2);
            int h = height - rnd.nextInt(3);
            for (int k = 0; k < h; k++) {
                int bend = kind == 3 ? (k > h / 2 ? 1 + (k - h / 2) / 2 : 0) : (kind == 2 && k > h - 4 ? (k - (h - 4)) / 2 : 0);
                int shadeD = (k * 6) / Math.max(1, h) * (kind == 3 ? -1 : 1);
                set(t, x + bend, 15 - k, rgb(r + shadeD - rnd.nextInt(14), g + shadeD - rnd.nextInt(14), b));
            }
            if (kind == 1 || kind == 2) {
                int top = 15 - h + 1, hx = x + (kind == 2 ? 2 : 0);
                set(t, hx, top, rgb(r + 30, g + 18, 70)); set(t, hx, top + 1, rgb(r + 20, g + 8, 60));
                if (kind == 2) { set(t, hx, top + 2, rgb(r + 14, g, 50)); set(t, hx - 1, top + 1, rgb(r + 24, g + 10, 64)); }
            }
        }
    }

    private static void icons() {
        for (Tile t : new Tile[]{Tile.ICON_COIN, Tile.ICON_SICKLE, Tile.ICON_SPYGLASS, Tile.ICON_BRICK, Tile.ICON_WHEAT, Tile.ICON_SATELLITE, Tile.ICON_DROP, Tile.ICON_HEART}) clear(t);
        // Coin
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double d = Math.hypot(x - 7.5, y - 7.5);
            if (d < 6.5) set(Tile.ICON_COIN, x, y, d > 5 ? rgb(176, 128, 20) : (x + y < 14 ? rgb(255, 224, 90) : rgb(240, 190, 50)));
        }
        for (int y = 4; y < 12; y++) set(Tile.ICON_COIN, 7, y, rgb(190, 140, 30));
        // Sickle: wooden handle + curved blade
        for (int k = 0; k < 7; k++) { set(Tile.ICON_SICKLE, 3 + k / 2, 13 - k, rgb(130, 90, 50)); set(Tile.ICON_SICKLE, 4 + k / 2, 13 - k, rgb(100, 70, 40)); }
        for (int k = 0; k < 9; k++) {
            double ang = Math.PI * 0.15 + k * 0.28;
            int x = 8 + (int) Math.round(Math.cos(ang) * 5), y = 7 - (int) Math.round(Math.sin(ang) * 5);
            set(Tile.ICON_SICKLE, x, y, rgb(214, 218, 226)); set(Tile.ICON_SICKLE, x, y + 1, rgb(150, 154, 166));
        }
        // Spyglass
        for (int k = 0; k < 11; k++) { int x = 2 + k, y = 13 - k; set(Tile.ICON_SPYGLASS, x, y, rgb(196, 144, 52)); set(Tile.ICON_SPYGLASS, x + 1, y, rgb(150, 104, 36)); }
        for (int k = 6; k < 11; k++) { int x = 2 + k, y = 13 - k; set(Tile.ICON_SPYGLASS, x, y, rgb(70, 76, 90)); set(Tile.ICON_SPYGLASS, x + 1, y, rgb(46, 52, 64)); }
        set(Tile.ICON_SPYGLASS, 13, 2, rgb(150, 210, 255));
        // Mud brick item
        for (int y = 3; y < 13; y++) for (int x = 1; x < 15; x++) {
            boolean mortar = (y - 3) % 5 == 4 || (x + ((y - 3) / 5) * 4) % 7 == 6;
            set(Tile.ICON_BRICK, x, y, mortar ? rgb(96, 68, 46) : rgb(168 - y, 112, 76));
        }
        // Wheat
        for (int s = -1; s <= 1; s++) {
            int bx = 8 + s * 3;
            for (int y = 6; y < 15; y++) set(Tile.ICON_WHEAT, bx + (y < 8 ? s : 0), y, rgb(150, 160, 50));
            for (int y = 1; y < 7; y++) { set(Tile.ICON_WHEAT, bx + s, y, rgb(232, 196, 70)); if (y % 2 == 0) { set(Tile.ICON_WHEAT, bx + s - 1, y, rgb(214, 176, 54)); set(Tile.ICON_WHEAT, bx + s + 1, y, rgb(214, 176, 54)); } }
        }
        // Satellite: body + panels
        for (int y = 6; y < 10; y++) for (int x = 6; x < 10; x++) set(Tile.ICON_SATELLITE, x, y, rgb(190, 194, 204));
        for (int y = 5; y < 11; y++) for (int x = 0; x < 5; x++) { set(Tile.ICON_SATELLITE, x, y, (x + y) % 2 == 0 ? rgb(40, 80, 170) : rgb(90, 130, 210)); set(Tile.ICON_SATELLITE, 15 - x, y, (x + y) % 2 == 0 ? rgb(40, 80, 170) : rgb(90, 130, 210)); }
        // Drop
        for (int y = 2; y < 15; y++) for (int x = 0; x < 16; x++) {
            double w = y < 8 ? (y - 2) * 0.9 : Math.sqrt(Math.max(0, 25 - (y - 10) * (y - 10)));
            if (Math.abs(x - 7.5) < w) set(Tile.ICON_DROP, x, y, x < 7 ? rgb(90, 160, 230) : rgb(56, 120, 196));
        }
        // Heart
        int[][] heart = {{1,1,0,1,1,0},{1,1,1,1,1,1},{1,1,1,1,1,1},{0,1,1,1,1,0},{0,0,1,1,0,0}};
        for (int y = 0; y < heart.length; y++) for (int x = 0; x < 6; x++) if (heart[y][x] == 1) for (int sx = 0; sx < 2; sx++) for (int sy = 0; sy < 2; sy++)
            set(Tile.ICON_HEART, 2 + x * 2 + sx, 3 + y * 2 + sy, (x < 2 && y < 2 ? rgb(255, 130, 130) : rgb(214, 40, 48)));
    }

    /** Upload the atlas as an OpenGL texture (nearest filtering, no mipmaps). */
    public static int upload() {
        int[] data = generate();
        ByteBuffer buf = BufferUtils.createByteBuffer(N * N * 4);
        for (int argb : data) {
            buf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
        }
        buf.flip();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, N, N, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        return tex;
    }
}
