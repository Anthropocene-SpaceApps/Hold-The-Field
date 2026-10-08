package org.anthropocene.htf.world;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.*;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Day;
import org.anthropocene.htf.sim.GameState;
import org.anthropocene.htf.sim.Season;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.anthropocene.htf.gfx.MeshBuilder.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/** Everything you see in the 3D world: static terrain plus the live scene driven by the simulation state. */
public final class WorldScene implements Player.Collider {
    public enum TargetType { NONE, BUND, CROP }
    public record Target(TargetType type, int x, int y, int z, double dist) {
        public static final Target NONE = new Target(TargetType.NONE, 0, 0, 0, Double.MAX_VALUE);
    }

    private static final String VS = """
            #version 330 core
            layout(location=0) in vec3 pos; layout(location=1) in vec2 uv; layout(location=2) in vec4 col;
            uniform mat4 vp; uniform vec3 camPos;
            out vec2 vUv; out vec4 vCol; out float vDist;
            void main(){ vUv = uv; vCol = col; vDist = length(pos - camPos); gl_Position = vp * vec4(pos, 1.0); }""";
    private static final String FS = """
            #version 330 core
            in vec2 vUv; in vec4 vCol; in float vDist;
            uniform sampler2D tex; uniform vec3 fogColor; uniform float fogStart, fogEnd, light, cutoff;
            out vec4 frag;
            void main(){
                vec4 t = texture(tex, vUv);
                if (t.a * vCol.a < cutoff) discard;
                vec3 c = t.rgb * vCol.rgb * light;
                float f = smoothstep(fogStart, fogEnd, vDist);
                frag = vec4(mix(c, fogColor, f), t.a * vCol.a);
            }""";

    // geometry constants shared with gameplay
    public static final float BLOCK_M = 0.15f;                   // metres of water per block
    public static final int CLOUD_COUNT = 24;

    private final Terrain terrain = new Terrain();
    private final Shader shader = new Shader(VS, FS);
    private final GpuMesh staticMesh = new GpuMesh(false), dynOpaque = new GpuMesh(true), dynTrans = new GpuMesh(true);
    private final MeshBuilder mbO = new MeshBuilder(), mbT = new MeshBuilder();
    private final int atlasTex;
    private final Random rnd = new Random(1234);

    private Season season;
    private Config cfg = Config.DEFAULT;
    private GameState state;

    // smoothed visuals
    private double time, shownLevel, shownFlood, storm, farmRain, upRain, riverSwell;
    private int bundBlocks = 4;
    private double bundGrow = 1;
    private final float[][] clouds = new float[CLOUD_COUNT][4];   // x, y, z, size
    private final float[] dropX = new float[1600], dropY = new float[1600], dropZ = new float[1600];
    private final List<float[]> particles = new ArrayList<>();    // x y z vx vy vz life r g b
    public final float[] sky = {0.53f, 0.74f, 0.92f};

    public WorldScene(int atlasTex) {
        this.atlasTex = atlasTex;
        MeshBuilder mb = new MeshBuilder();
        terrain.buildStatic(mb);
        staticMesh.upload(mb);
        for (int i = 0; i < CLOUD_COUNT; i++) {
            clouds[i][0] = -70 + rnd.nextFloat() * 150;
            clouds[i][1] = 42 + rnd.nextFloat() * 8;
            clouds[i][2] = i < 18 ? -68 + rnd.nextFloat() * 56 : -8 + rnd.nextFloat() * 24;
            clouds[i][3] = 5 + rnd.nextFloat() * 5;
        }
        for (int i = 0; i < dropX.length; i++) {
            boolean up = i < 900;
            dropX[i] = up ? -22 + rnd.nextFloat() * 54 : -14 + rnd.nextFloat() * 36;
            dropZ[i] = up ? -66 + rnd.nextFloat() * 46 : -14 + rnd.nextFloat() * 32;
            dropY[i] = rnd.nextFloat() * 40;
        }
    }

    public Terrain terrain() { return terrain; }

    public void setSession(Season season, Config cfg, GameState state) {
        this.season = season;
        this.cfg = cfg;
        this.state = state;
        snap();
    }

    public void setState(GameState s) { this.state = s; }

    /** Jump visuals straight to the current state (new game, load, restart). */
    public void snap() {
        if (state == null) return;
        bundBlocks = Math.round((float) (state.bund / BLOCK_M));
        shownLevel = state.level;
        shownFlood = floodTarget();
        bundGrow = 1;
        Day d = season.day(state.i);
        upRain = clamp01(d.rainUp() / 150);
        farmRain = clamp01(d.rainFarm() / 60);
        storm = Math.max(upRain, farmRain);
        riverSwell = 0;
        particles.clear();
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    private double floodTarget() {
        if (state == null || !state.flooded) return 0;
        return Math.min(4, (state.level - state.bund) / BLOCK_M + 1);
    }

    public double rainIntensityUp() { return upRain; }
    public double rainIntensityFarm() { return farmRain; }
    public double stormLevel() { return storm; }

    // ------------------------------------------------------------------ update

    public void update(double dt, boolean particlesOn) {
        time += dt;
        if (state == null || season == null) return;
        int target = Math.round((float) (state.bund / BLOCK_M));
        if (target > bundBlocks) { bundBlocks = target; bundGrow = 0; }
        else if (target < bundBlocks) { bundBlocks = target; bundGrow = 1; }
        bundGrow = Math.min(1, bundGrow + dt * 2.2);

        Day d = season.day(state.i);
        double k = Math.min(1, dt * 2.5);
        upRain += (clamp01(d.rainUp() / 150) - upRain) * k;
        farmRain += (clamp01(d.rainFarm() / 60) - farmRain) * k;
        storm += (Math.max(upRain, farmRain) - storm) * Math.min(1, dt * 1.5);
        shownLevel += (state.level - shownLevel) * Math.min(1, dt * 3);
        shownFlood += (floodTarget() - shownFlood) * Math.min(1, dt * 1.6);
        Day lagged = season.day(Math.max(0, state.i - cfg.lagDays));
        riverSwell += (clamp01(lagged.rainUp() / 200) - riverSwell) * Math.min(1, dt * 2);

        // rain falls
        float speed = 30f;
        for (int i = 0; i < dropY.length; i++) {
            dropY[i] -= speed * dt;
            if (dropY[i] < -1) {
                boolean up = i < 900;
                dropY[i] += 42;
                dropX[i] = up ? -22 + rnd.nextFloat() * 54 : -14 + rnd.nextFloat() * 36;
                dropZ[i] = up ? -66 + rnd.nextFloat() * 46 : -14 + rnd.nextFloat() * 32;
            }
        }
        for (int c = 0; c < CLOUD_COUNT; c++) {
            clouds[c][0] += (float) (dt * (1.2 + storm * 3.0));
            if (clouds[c][0] > 85) clouds[c][0] = -85;
        }
        // particles
        for (int p = particles.size() - 1; p >= 0; p--) {
            float[] q = particles.get(p);
            q[4] -= 16f * (float) dt;
            q[0] += q[3] * (float) dt; q[1] += q[4] * (float) dt; q[2] += q[5] * (float) dt;
            q[6] -= (float) dt;
            if (q[6] <= 0) particles.remove(p);
        }
        if (!particlesOn) particles.clear();
    }

    public void burst(float x, float y, float z, int count, float r, float g, float b) {
        for (int i = 0; i < count && particles.size() < 220; i++) {
            particles.add(new float[]{x + (rnd.nextFloat() - .5f) * .6f, y, z + (rnd.nextFloat() - .5f) * .6f,
                    (rnd.nextFloat() - .5f) * 3.5f, 3f + rnd.nextFloat() * 4f, (rnd.nextFloat() - .5f) * 3.5f,
                    0.6f + rnd.nextFloat() * 0.5f, r, g, b});
        }
    }

    // ------------------------------------------------------------------ queries

    public double lakeSurfaceY() { return 1 + shownLevel / BLOCK_M; }
    public int bundTopY() { return 1 + bundBlocks; }

    @Override
    public double solidTop(int x, int z) {
        double t = terrain.solidTop(x, z);
        if (z == Terrain.BUND_Z && x >= Terrain.PLAIN_X0 && x < Terrain.PLAIN_X1) t = Math.max(t, bundTopY());
        return t;
    }

    @Override
    public double waterSurface(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        if (Terrain.inLake(ix, iz) && shownLevel > 0.02) return lakeSurfaceY();
        if (Terrain.inPlain(ix, iz) && shownFlood > 0.05 && terrain.height(ix, iz) < 1 + shownFlood) return 1 + shownFlood;
        if (x >= Terrain.RIVER_X0 && x < Terrain.RIVER_X1 && iz < Terrain.LAKE_Z0 && iz > -60) return -0.4 + riverSwell * 1.3;
        return Double.NaN;
    }

    private float cropHeight() {
        if (state == null || !state.alive || state.harvested) return 0;
        return (float) (0.45 + 0.55 * state.maturity);
    }

    /** Ray against the bund wall and the crop field. */
    public Target pick(Vector3f o, Vector3f d, double reach) {
        Target best = Target.NONE;
        if (state == null) return best;
        double[] hit = new double[1];
        float bx0 = Terrain.PLAIN_X0, bx1 = Terrain.PLAIN_X1, bz0 = Terrain.BUND_Z, bz1 = Terrain.BUND_Z + 1;
        if (rayBox(o, d, bx0, 1, bz0, bx1, bundTopY(), bz1, hit) && hit[0] <= reach) {
            double t = hit[0] + 0.002;
            int cx = (int) Math.floor(o.x + d.x * t), cy = (int) Math.floor(o.y + d.y * t);
            cy = Math.max(1, Math.min(bundTopY() - 1, cy));
            best = new Target(TargetType.BUND, cx, cy, Terrain.BUND_Z, hit[0]);
        }
        float ch = cropHeight();
        if (ch > 0 && rayBox(o, d, 0, 1, 0, Terrain.FIELD, 1 + ch, Terrain.FIELD, hit) && hit[0] <= reach && hit[0] < best.dist()) {
            double t = hit[0] + 0.002;
            int cx = (int) Math.floor(o.x + d.x * t), cz = (int) Math.floor(o.z + d.z * t);
            cx = Math.max(0, Math.min(Terrain.FIELD - 1, cx)); cz = Math.max(0, Math.min(Terrain.FIELD - 1, cz));
            best = new Target(TargetType.CROP, cx, 1, cz, hit[0]);
        }
        return best;
    }

    private static boolean rayBox(Vector3f o, Vector3f d, float x0, float y0, float z0, float x1, float y1, float z1, double[] out) {
        double tmin = 0, tmax = Double.MAX_VALUE;
        float[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-9) { if (oo[a] < lo[a] || oo[a] > hi[a]) return false; continue; }
            double t1 = (lo[a] - oo[a]) / dd[a], t2 = (hi[a] - oo[a]) / dd[a];
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
            if (tmin > tmax) return false;
        }
        out[0] = tmin;
        return true;
    }

    // ------------------------------------------------------------------ rendering

    public void render(Camera cam, Settings st, Target highlight, int fbW, int fbH) {
        float stormF = (float) storm;
        float[] clear = {0.53f * (1 - stormF * 0.5f) + 0.30f * stormF * 0.5f, 0.74f * (1 - stormF * 0.55f) + 0.34f * stormF * 0.55f, 0.92f * (1 - stormF * 0.45f) + 0.40f * stormF * 0.45f};
        System.arraycopy(clear, 0, sky, 0, 3);
        glViewport(0, 0, fbW, fbH);
        glClearColor(clear[0], clear[1], clear[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        glDisable(GL_BLEND);

        buildDynamic(cam, st, highlight);
        dynOpaque.upload(mbO);
        dynTrans.upload(mbT);

        float fogEnd = st.renderDistance * 12f + 24f;
        shader.use();
        shader.set("vp", cam.viewProj);
        shader.set("camPos", cam.pos.x, cam.pos.y, cam.pos.z);
        shader.set("fogColor", clear[0], clear[1], clear[2]);
        shader.set("fogStart", fogEnd * 0.55f);
        shader.set("fogEnd", fogEnd);
        shader.set("light", (float) ((0.72 + 0.55 * st.brightness) * (1 - 0.28 * storm)));
        shader.set("tex", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTex);

        shader.set("cutoff", 0.5f);
        staticMesh.draw();
        dynOpaque.draw();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        shader.set("cutoff", 0.02f);
        dynTrans.draw();
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void buildDynamic(Camera cam, Settings st, Target hl) {
        mbO.clear(); mbT.clear();
        if (state == null || season == null) return;
        field();
        bund();
        rahim();
        sacks();
        sun();
        satellite();
        particlesMesh();
        if (hl != null && hl.type() != TargetType.NONE) outline(hl);

        lake();
        flood();
        river();
        if (st.clouds) clouds();
        rain(cam);
    }

    // --- opaque pieces

    private void field() {
        boolean dead = !state.alive, stubble = state.harvested && state.alive;
        float tr = dead ? 0.55f : 1f, tg = dead ? 0.52f : 1f, tb = dead ? 0.5f : 1f;
        if (state.flooded) { tr *= 0.8f; tg *= 0.85f; tb *= 0.95f; }
        Tile tile = stubble ? Tile.STUBBLE : Tile.FARMLAND;
        for (int z = 0; z < Terrain.FIELD; z++) for (int x = 0; x < Terrain.FIELD; x++)
            mbO.flatTop(x, 1f, z, 1, 1, tile, tr, tg, tb, 1);
        float ch = cropHeight();
        if (!state.alive) ch = 0.55f;
        if (ch <= 0 || (state.harvested && state.alive)) return;
        Tile crop = !state.alive ? Tile.CROP_DEAD : state.maturity < 0.25 ? Tile.CROP0 : state.maturity < 0.6 ? Tile.CROP1 : state.maturity < 0.9 ? Tile.CROP2 : Tile.CROP3;
        float sway = (float) Math.sin(time * 1.4) * 0.0f;
        for (int z = 0; z < Terrain.FIELD; z++) for (int x = 0; x < Terrain.FIELD; x++)
            for (int sx = 0; sx < 2; sx++) for (int sz = 0; sz < 2; sz++) {
                float jx = ((x * 7 + z * 13 + sx * 3 + sz * 5) % 5 - 2) * 0.03f;
                mbO.cross(x + 0.27f + sx * 0.46f + jx + sway, 1f, z + 0.27f + sz * 0.46f - jx, 0.66f, ch, crop, 1, 1, 1, 1);
            }
    }

    private void bund() {
        float grow = (float) (1 - Math.pow(1 - bundGrow, 3));
        for (int row = 0; row < bundBlocks; row++) {
            float hgt = row == bundBlocks - 1 ? Math.max(0.05f, grow) : 1f;
            boolean top = row == bundBlocks - 1;
            for (int x = Terrain.PLAIN_X0; x < Terrain.PLAIN_X1; x++) {
                int faces = FACE_N | FACE_S;
                if (top) faces |= FACE_TOP;
                if (x == Terrain.PLAIN_X0) faces |= FACE_W;
                if (x == Terrain.PLAIN_X1 - 1) faces |= FACE_E;
                mbO.box(x, 1 + row, Terrain.BUND_Z, 1, hgt, 1, Tile.MUD_BRICK, Tile.MUD_BRICK, Tile.MUD_BRICK, 1, 1, 1, 1, faces);
            }
        }
    }

    private void rahim() {
        float x = 15.5f, y = Terrain.MOUND_TOP, z = 9.3f;
        boolean warn = state.status.equals("warning") && !state.harvested && state.alive;
        boolean cheer = state.harvested && state.yieldPct > 0.4;
        boolean sad = !state.alive;
        float wave = warn ? (float) (Math.sin(time * 9) * 0.5 + 0.5) : 0;
        mbO.box(x - 0.24f, y, z - 0.12f, 0.2f, 0.62f, 0.24f, Tile.PANTS, 1, 1, 1, 1);
        mbO.box(x + 0.04f, y, z - 0.12f, 0.2f, 0.62f, 0.24f, Tile.PANTS, 1, 1, 1, 1);
        mbO.box(x - 0.27f, y + 0.62f, z - 0.15f, 0.54f, 0.72f, 0.3f, Tile.SHIRT, 1, 1, 1, 1);
        float armY = (cheer || warn ? y + 0.9f + (cheer ? 0.35f : wave * 0.35f) : y + 0.62f), armH = cheer || warn ? 0.7f : 0.7f;
        mbO.box(x - 0.47f, armY, z - 0.1f, 0.2f, armH, 0.2f, Tile.SKIN, 1, 1, 1, 1);
        mbO.box(x + 0.27f, cheer ? armY : (warn ? y + 0.9f + (1 - wave) * 0.35f : armY), z - 0.1f, 0.2f, armH, 0.2f, Tile.SKIN, 1, 1, 1, 1);
        float hy = y + 1.34f + (sad ? -0.03f : 0);
        mbO.box(x - 0.23f, hy, z - 0.22f, 0.46f, 0.46f, 0.44f, Tile.SKIN, Tile.SKIN, Tile.SKIN, 1, 1, 1, 1, FACE_ALL);
        mbO.box(x - 0.24f, hy + 0.38f, z - 0.23f, 0.48f, 0.1f, 0.46f, Tile.HAIR, 1, 1, 1, 1);
        float[] uv = Tile.FACE.uv();
        mbO.quad(x - 0.23f, hy, z + 0.222f, x + 0.23f, hy, z + 0.222f, x + 0.23f, hy + 0.4f, z + 0.222f, x - 0.23f, hy + 0.4f, z + 0.222f, uv, 1, 1, 1, 1);
    }

    private void sacks() {
        if (!state.harvested || state.yieldPct <= 0) return;
        int n = Math.max(1, (int) Math.round(state.yieldPct * 8));
        for (int i = 0; i < n; i++) {
            float sx = 13.2f + (i % 4) * 0.8f, sz = 10.55f - (i / 4) * 0.0f;
            sz = Terrain.MOUND_Z1 - 0.7f + (i / 4) * -0.0f;
            float xx = 13.0f + (i % 4) * 0.85f, zz = 8.45f + (i / 4) * 0.75f;
            mbO.box(xx, Terrain.MOUND_TOP, zz, 0.65f, 0.75f, 0.55f, Tile.SACK, 1, 1, 1, 1);
        }
    }

    private void sun() {
        mbO.box(70, 95, -40, 10, 10, 10, Tile.WHITE, 1.0f, 0.93f, 0.55f, 1f);
    }

    private float[] satPos() {
        double a = time * 0.12;
        return new float[]{(float) (4 + Math.cos(a) * 38), (float) (66 + Math.sin(a * 3) * 3), (float) (-26 + Math.sin(a) * 26)};
    }

    private void satellite() {
        float[] p = satPos();
        mbO.box(p[0] - 0.9f, p[1] - 0.9f, p[2] - 0.9f, 1.8f, 1.8f, 1.8f, Tile.METAL, 1, 1, 1, 1);
        mbO.box(p[0] - 5.2f, p[1] - 0.12f, p[2] - 1.0f, 4.0f, 0.24f, 2.0f, Tile.SOLAR, 1, 1, 1, 1);
        mbO.box(p[0] + 1.2f, p[1] - 0.12f, p[2] - 1.0f, 4.0f, 0.24f, 2.0f, Tile.SOLAR, 1, 1, 1, 1);
        mbO.box(p[0] - 0.3f, p[1] + 0.9f, p[2] - 0.3f, 0.6f, 0.6f, 0.6f, Tile.WHITE, 0.9f, 0.9f, 1f, 1);
        // scanning beam straight down; colour follows the scout status
        float r = 0.6f, g = 0.9f, b = 1f, a = 0.20f;
        if (state.status.equals("watch")) { r = 1f; g = 0.75f; b = 0.25f; a = 0.34f; }
        if (state.status.equals("warning")) { r = 1f; g = 0.3f; b = 0.25f; a = 0.42f + (float) Math.sin(time * 8) * 0.12f; }
        int gx = (int) Math.floor(p[0]), gz = (int) Math.floor(p[2]);
        float ground = Math.max(terrain.height(gx, gz), 0.5f);
        mbT.box(p[0] - 1.1f, ground, p[2] - 1.1f, 2.2f, p[1] - ground - 0.9f, 2.2f, Tile.BEAM, Tile.BEAM, Tile.BEAM, r, g, b, a, FACE_N | FACE_S | FACE_E | FACE_W);
    }

    private void particlesMesh() {
        for (float[] q : particles) {
            float s = 0.12f * Math.min(1, q[6] * 2);
            mbO.box(q[0] - s / 2, q[1] - s / 2, q[2] - s / 2, s, s, s, Tile.WHITE, q[7], q[8], q[9], 1);
        }
    }

    private void outline(Target t) {
        float x0, y0, z0, x1, y1, z1;
        if (t.type() == TargetType.BUND) { x0 = t.x(); y0 = t.y(); z0 = t.z(); x1 = x0 + 1; y1 = y0 + 1; z1 = z0 + 1; }
        else { x0 = t.x(); y0 = 1; z0 = t.z(); x1 = x0 + 1; y1 = 1 + Math.max(0.5f, cropHeight()); z1 = z0 + 1; }
        float e = 0.012f, w = 0.035f;
        x0 -= e; y0 -= e; z0 -= e; x1 += e; y1 += e; z1 += e;
        float[] c = {0.05f, 0.05f, 0.05f};
        for (float y : new float[]{y0, y1 - w}) {
            mbO.box(x0, y, z0, x1 - x0, w, w, Tile.WHITE, c[0], c[1], c[2], 1);
            mbO.box(x0, y, z1 - w, x1 - x0, w, w, Tile.WHITE, c[0], c[1], c[2], 1);
            mbO.box(x0, y, z0, w, w, z1 - z0, Tile.WHITE, c[0], c[1], c[2], 1);
            mbO.box(x1 - w, y, z0, w, w, z1 - z0, Tile.WHITE, c[0], c[1], c[2], 1);
        }
        for (float x : new float[]{x0, x1 - w}) for (float z : new float[]{z0, z1 - w})
            mbO.box(x, y0, z, w, y1 - y0, w, Tile.WHITE, c[0], c[1], c[2], 1);
    }

    // --- transparent pieces

    private void lake() {
        if (shownLevel <= 0.02) return;
        float y = (float) lakeSurfaceY();
        Tile wt = Tile.water(time);
        for (int z = Terrain.LAKE_Z0; z < Terrain.BUND_Z; z++) for (int x = Terrain.PLAIN_X0; x < Terrain.PLAIN_X1; x++)
            mbT.flatTop(x, y, z, 1, 1, wt, 1, 1, 1, 0.86f);
        float top = bundTopY();
        if (y > top) {   // water standing higher than the bund: show the wall of water against it
            for (int x = Terrain.PLAIN_X0; x < Terrain.PLAIN_X1; x++)
                for (float yy = top; yy < y; yy += 1) {
                    float h1 = Math.min(y, yy + 1);
                    mbT.quad(x, yy, Terrain.BUND_Z, x + 1, yy, Terrain.BUND_Z, x + 1, h1, Terrain.BUND_Z, x, h1, Terrain.BUND_Z, wt.uv(), 0.9f, 0.9f, 0.9f, 0.8f);
                }
        }
    }

    private void flood() {
        if (shownFlood <= 0.05) return;
        float y = (float) (1 + shownFlood);
        Tile wt = Tile.water(time + 0.3);
        for (int z = Terrain.BUND_Z + 1; z < Terrain.PLAIN_Z1; z++) for (int x = Terrain.PLAIN_X0; x < Terrain.PLAIN_X1; x++) {
            if (terrain.height(x, z) >= y) continue;
            mbT.flatTop(x, y, z, 1, 1, wt, 1, 1, 1, 0.82f);
        }
    }

    private void river() {
        float y = (float) (-0.4 + riverSwell * 1.3);
        Tile wt = Tile.water(time * 1.5);
        for (int z = -60; z < Terrain.LAKE_Z0; z++) for (int x = (int) Terrain.RIVER_X0; x < Terrain.RIVER_X1; x++)
            if (terrain.height(x, z) < y) mbT.flatTop(x, y, z, 1, 1, wt, 1, 1, 1, 0.85f);
    }

    private void clouds() {
        float dark = (float) (1 - storm * 0.55);
        for (int c = 0; c < CLOUD_COUNT; c++) {
            if (c >= 18 && farmRain < 0.12) continue;
            float cx = clouds[c][0], cy = clouds[c][1], cz = clouds[c][2], s = clouds[c][3];
            int n = 3 + c % 3;
            for (int k = 0; k < n; k++) {
                float ox = (k - n / 2f) * s * 0.9f, oz = ((k * 7 + c) % 3 - 1) * s * 0.6f;
                mbT.box(cx + ox, cy + (k % 2) * 0.8f, cz + oz, s * 1.4f, 2.2f, s * 1.0f, Tile.CLOUD, dark, dark, Math.min(1f, dark * 1.05f), 0.92f);
            }
        }
    }

    private void rain(Camera cam) {
        int up = (int) (900 * upRain), fm = (int) (700 * farmRain);
        for (int i = 0; i < up; i++) drop(i);
        for (int i = 0; i < fm; i++) drop(900 + i);
    }

    private void drop(int i) {
        float x = dropX[i], y = dropY[i], z = dropZ[i];
        mbT.cross(x, y, z, 0.18f, 1.6f, Tile.RAIN, 1, 1, 1, 0.7f);
    }

    public void destroy() {
        staticMesh.destroy(); dynOpaque.destroy(); dynTrans.destroy();
    }
}
