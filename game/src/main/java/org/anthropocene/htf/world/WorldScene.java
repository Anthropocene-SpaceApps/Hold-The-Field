package org.anthropocene.htf.world;

import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.*;
import org.anthropocene.htf.sim.Config;
import org.anthropocene.htf.sim.Day;
import org.anthropocene.htf.sim.GameState;
import org.anthropocene.htf.sim.Season;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_R32F;

/** The 3D world: landscape, sky, water, embankment, farm, driven live by the simulation state. */
public final class WorldScene implements Player.Collider {
    public enum TargetType { NONE, BUND, CROP, RAHIM, GAUGE }
    public record Target(TargetType type, double x, double y, double z, double dist) {
        public static final Target NONE = new Target(TargetType.NONE, 0, 0, 0, Double.MAX_VALUE);
    }

    private static final int SHADOW_SIZE = 2048;
    private static final String SKIP = System.getProperty("htf.skip", "");
    private static boolean skip(String pass) { return SKIP.contains(pass); }
    public static final float REF = Landscape.DIKE_REF_H;

    private final Landscape land = new Landscape();
    private final Atmosphere atmo = new Atmosphere();
    private final Random rnd = new Random(42);

    private final Shader skyS = ShaderLoader.load2("fullscreen", "sky");
    private final Shader terrainS = ShaderLoader.load("terrain");
    private final Shader waterS = ShaderLoader.load("water");
    private final Shader dikeS = ShaderLoader.load("dike");
    private final Shader propsS = ShaderLoader.load("props");
    private final Shader propsDepthS = ShaderLoader.load("props_depth");
    private final Farmstead farm;
    private final StaticMesh propsMesh;
    private final Foliage foliage;
    private final Rahim rahim;
    private final Boat boat = new Boat();
    private final Weather weather = new Weather();
    private final StaticMesh riverMesh;
    private final Vector3f viewer = new Vector3f(0, 0, 40);
    private double riverSwell;
    private Framebuffer shadowFbo;
    private final Matrix4f lightVP = new Matrix4f();
    private boolean shadowActive;
    private final StaticMesh terrainMesh, waterMesh, dikeMesh;
    private final int heightTex, emptyVao;
    private final Post post = new Post();
    private Framebuffer sceneFbo, resolveFbo;
    private int lastW, lastH, lastSamples = -1;

    private Season season;
    private Config cfg = Config.DEFAULT;
    private GameState state;

    // smoothed visuals
    private double time, shownLevel, shownBund = Landscape.DIKE_REF_H, floodReach, storm, farmRain, upRain, flash, flashTimer = 8, cropMat;
    public boolean overlay;                       // satellite data layer
    private boolean lightningPending;
    private final List<float[]> particles = new ArrayList<>();

    public WorldScene() {
        terrainMesh = buildTerrain();
        waterMesh = buildWater();
        dikeMesh = buildDike();
        heightTex = buildHeightTexture();
        farm = new Farmstead(land);
        propsMesh = farm.builder().build();
        foliage = new Foliage(land, farm);
        rahim = new Rahim(land);
        riverMesh = buildRiver();
        emptyVao = org.lwjgl.opengl.GL30.glGenVertexArrays();
    }

    public Landscape landscape() { return land; }
    public void setViewer(Vector3f p) { viewer.set(p); }
    public Rahim rahim() { return rahim; }
    public Atmosphere atmosphere() { return atmo; }

    // ------------------------------------------------------------------ GPU resources

    private StaticMesh buildTerrain() {
        int nx = Landscape.NX + 1, nz = Landscape.NZ + 1;
        float[] v = new float[nx * nz * 6];
        int k = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            float x = Landscape.X0 + i * Landscape.CELL, z = Landscape.Z0 + j * Landscape.CELL, y = land.node(i, j);
            float dx = (land.node(i - 1, j) - land.node(i + 1, j)) / (2 * Landscape.CELL);
            float dz = (land.node(i, j - 1) - land.node(i, j + 1)) / (2 * Landscape.CELL);
            float len = (float) Math.sqrt(dx * dx + 1 + dz * dz);
            v[k++] = x; v[k++] = y; v[k++] = z; v[k++] = dx / len; v[k++] = 1 / len; v[k++] = dz / len;
        }
        int[] idx = new int[Landscape.NX * Landscape.NZ * 6];
        k = 0;
        for (int j = 0; j < Landscape.NZ; j++) for (int i = 0; i < Landscape.NX; i++) {
            int a = j * nx + i, b = a + 1, c = a + nx, d = c + 1;
            idx[k++] = a; idx[k++] = c; idx[k++] = b; idx[k++] = b; idx[k++] = c; idx[k++] = d;
        }
        return StaticMesh.create(v, idx, 3, 3);
    }

    private StaticMesh buildWater() {
        float step = 4f;
        int nx = (int) ((Landscape.X1 - Landscape.X0) / step) + 1, nz = (int) ((Landscape.Z1 - Landscape.Z0) / step) + 1;
        float[] v = new float[nx * nz * 3];
        int k = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) { v[k++] = Landscape.X0 + i * step; v[k++] = 0; v[k++] = Landscape.Z0 + j * step; }
        int[] idx = new int[(nx - 1) * (nz - 1) * 6];
        k = 0;
        for (int j = 0; j < nz - 1; j++) for (int i = 0; i < nx - 1; i++) {
            int a = j * nx + i, b = a + 1, c = a + nx, d = c + 1;
            idx[k++] = a; idx[k++] = c; idx[k++] = b; idx[k++] = b; idx[k++] = c; idx[k++] = d;
        }
        return StaticMesh.create(v, idx, 3);
    }

    private StaticMesh buildRiver() {
        int n = (int) ((-260 - -900) / 6f) + 1;
        float[] v = new float[n * 2 * 3];
        int[] idx = new int[(n - 1) * 6];
        int k = 0;
        for (int i = 0; i < n; i++) {
            float z = -900 + i * 6f, x = Landscape.riverX(z), y = Landscape.riverBed(z) + 0.85f;
            v[k++] = x - 22; v[k++] = y; v[k++] = z;
            v[k++] = x + 22; v[k++] = y; v[k++] = z;
        }
        k = 0;
        for (int i = 0; i < n - 1; i++) {
            int a = i * 2, b = a + 1, c = a + 2, d = a + 3;
            idx[k++] = a; idx[k++] = c; idx[k++] = b; idx[k++] = b; idx[k++] = c; idx[k++] = d;
        }
        return StaticMesh.create(v, idx, 3);
    }

    private StaticMesh buildDike() {
        float stepX = 1.5f;
        int cols = (int) ((Landscape.DIKE_X1 - Landscape.DIKE_X0) / stepX) + 1;
        float c = Landscape.DIKE_CROWN, s = Landscape.DIKE_SLOPE;
        float[] zoff = {-(c + s), -(c + s * 0.5f), -c, 0, c, c + s * 0.5f, c + s};
        int rows = zoff.length;
        float[] v = new float[cols * rows * 7];
        int k = 0;
        for (int i = 0; i < cols; i++) for (int r = 0; r < rows; r++) {
            float x = Landscape.DIKE_X0 + i * stepX, z = Landscape.DIKE_Z + zoff[r];
            float base = land.height(x, z) - 0.04f, rise = Landscape.dikeFraction(x, z) * REF;
            float e = 0.6f;
            float hx0 = land.height(x - e, z) + Landscape.dikeFraction(x - e, z) * REF, hx1 = land.height(x + e, z) + Landscape.dikeFraction(x + e, z) * REF;
            float hz0 = land.height(x, z - e) + Landscape.dikeFraction(x, z - e) * REF, hz1 = land.height(x, z + e) + Landscape.dikeFraction(x, z + e) * REF;
            float nx = hx0 - hx1, nz = hz0 - hz1, nl = (float) Math.sqrt(nx * nx + 4 * e * e + nz * nz);
            v[k++] = x; v[k++] = base; v[k++] = z; v[k++] = rise; v[k++] = nx / nl; v[k++] = 2 * e / nl; v[k++] = nz / nl;
        }
        int[] idx = new int[(cols - 1) * (rows - 1) * 6];
        k = 0;
        for (int i = 0; i < cols - 1; i++) for (int r = 0; r < rows - 1; r++) {
            int a = i * rows + r, b = a + 1, cc = a + rows, d = cc + 1;
            idx[k++] = a; idx[k++] = cc; idx[k++] = b; idx[k++] = b; idx[k++] = cc; idx[k++] = d;
        }
        return StaticMesh.create(v, idx, 3, 1, 3);
    }

    private int buildHeightTexture() {
        int w = Landscape.NX + 1, h = Landscape.NZ + 1;
        FloatBuffer fb = BufferUtils.createFloatBuffer(w * h);
        fb.put(land.raw()).flip();
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, w, h, 0, GL_RED, GL_FLOAT, fb);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return t;
    }

    // ------------------------------------------------------------------ session

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
        shownLevel = state.level;
        shownBund = state.bund;
        floodReach = state.flooded ? 500 : 0;
        Day d = season.day(state.i);
        upRain = clamp01(d.rainUp() / 150);
        farmRain = clamp01(d.rainFarm() / 60);
        storm = Math.max(upRain, farmRain);
        cropMat = state.maturity;
        particles.clear();
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    public double rainIntensityUp() { return upRain; }
    public double rainIntensityFarm() { return farmRain; }
    public double stormLevel() { return storm; }
    public double shownLevel() { return shownLevel; }
    public double bundHeight() { return shownBund; }

    // ------------------------------------------------------------------ update

    public void update(double dt, Settings st) {
        time += dt;
        if (state != null && season != null) {
            Day d = season.day(state.i);
            double k = Math.min(1, dt * 2.0);
            upRain += (clamp01(d.rainUp() / 150) - upRain) * k;
            farmRain += (clamp01(d.rainFarm() / 60) - farmRain) * k;
            storm += (Math.max(upRain, farmRain) - storm) * Math.min(1, dt * 1.2);
            shownLevel += (state.level - shownLevel) * Math.min(1, dt * 2.4);
            shownBund += (state.bund - shownBund) * Math.min(1, dt * 3.0);
            cropMat += (state.maturity - cropMat) * Math.min(1, dt * 3.0);
            Day lagged = season.day(Math.max(0, state.i - cfg.lagDays));
            riverSwell += (clamp01(lagged.rainUp() / 220) - riverSwell) * Math.min(1, dt * 1.5);
            if (state.flooded) floodReach = Math.min(500, floodReach + dt * 16);
            else floodReach = Math.max(0, floodReach - dt * 45);
        }
        // lightning in heavy storms
        flash = Math.max(0, flash - dt * 3.2);
        flashTimer -= dt;
        if (storm > 0.72 && flashTimer <= 0) {
            flash = 0.8 + rnd.nextDouble() * 0.5;
            lightningPending = true;
            flashTimer = 3 + rnd.nextDouble() * 9;
        }
        atmo.update(dt, st.timeMode, storm, flash);
        rahim.update(time, dt, state, viewer);
        for (int p = particles.size() - 1; p >= 0; p--) {
            float[] q = particles.get(p);
            q[4] -= 9.8f * (float) dt;
            q[0] += q[3] * (float) dt; q[1] += q[4] * (float) dt; q[2] += q[5] * (float) dt;
            q[6] -= (float) dt;
            if (q[6] <= 0) particles.remove(p);
        }
    }

    public void burst(float x, float y, float z, int count, float r, float g, float b) {
        for (int i = 0; i < count && particles.size() < 400; i++)
            particles.add(new float[]{x + (rnd.nextFloat() - .5f), y, z + (rnd.nextFloat() - .5f),
                    (rnd.nextFloat() - .5f) * 3f, 2f + rnd.nextFloat() * 3.5f, (rnd.nextFloat() - .5f) * 3f, 0.8f + rnd.nextFloat() * 0.6f, r, g, b});
    }

    public double flashLevel() { return flash; }

    /** True once for each new lightning flash (the game schedules the thunder after it). */
    public boolean pollLightning() { boolean p = lightningPending; lightningPending = false; return p; }

    // ------------------------------------------------------------------ collider

    private double terrainAndDike(double x, double z) {
        double y = land.height(x, z);
        if (x > Landscape.DIKE_X0 && x < Landscape.DIKE_X1 && Math.abs(z - Landscape.DIKE_Z) < Landscape.DIKE_CROWN + Landscape.DIKE_SLOPE)
            y += Landscape.dikeFraction((float) x, (float) z) * shownBund;
        return y;
    }

    @Override public double groundHeight(double x, double z) { return terrainAndDike(x, z); }

    @Override
    public boolean blocked(double x, double z, double radius, double feetY) {
        if (terrainAndDike(x, z) > feetY + Player.STEP) return true;
        for (Farmstead.Obstacle o : farm.obstacles()) {
            if (o.circle()) { if (Math.hypot(x - o.x(), z - o.z()) < o.hx() + radius) return true; }
            else if (Math.abs(x - o.x()) < o.hx() + radius && Math.abs(z - o.z()) < o.hz() + radius) return true;
        }
        return false;
    }

    @Override
    public double waterDepth(double x, double z) {
        double ground = land.height(x, z);
        double depth = Math.max(0, shownLevel - ground);
        if (z > Landscape.DIKE_Z) {
            double south = z - Landscape.DIKE_Z;
            depth = floodReach > 1 && south < floodReach ? depth : 0;
        }
        double pond = Math.hypot(x - Landscape.POND_X, z - Landscape.POND_Z);
        if (pond < 13) depth = Math.max(depth, Landscape.POND_LEVEL - ground);
        return Math.max(0, depth);
    }

    /** Surface height of the water here (ground + depth) or NaN. */
    public double waterSurface(double x, double z) {
        double d = waterDepth(x, z);
        return d > 0.01 ? land.height(x, z) + d : Double.NaN;
    }

    // ------------------------------------------------------------------ picking

    private float cropHeight() {
        if (state == null || !state.alive || state.harvested) return 0;
        return (float) (0.12 + 0.88 * state.maturity);
    }

    public Target pick(Vector3f o, Vector3f d, double reach) {
        Target best = Target.NONE;
        if (state == null) return best;
        double[] hit = new double[1];
        float bz0 = Landscape.DIKE_Z - Landscape.DIKE_CROWN - Landscape.DIKE_SLOPE, bz1 = Landscape.DIKE_Z + Landscape.DIKE_CROWN + Landscape.DIKE_SLOPE;
        if (rayBox(o, d, Landscape.DIKE_X0, -0.2f, bz0, Landscape.DIKE_X1, (float) shownBund + 0.15f, bz1, hit) && hit[0] <= reach) {
            double t = hit[0];
            best = new Target(TargetType.BUND, o.x + d.x * t, o.y + d.y * t, o.z + d.z * t, hit[0]);
        }
        float ch = cropHeight();
        if (ch > 0 && rayBox(o, d, Landscape.PLOT_X0, 0f, Landscape.PLOT_Z0, Landscape.PLOT_X1, ch + 0.05f, Landscape.PLOT_Z1, hit) && hit[0] <= reach && hit[0] < best.dist()) {
            double t = hit[0];
            best = new Target(TargetType.CROP, o.x + d.x * t, o.y + d.y * t, o.z + d.z * t, hit[0]);
        }
        float rg = rahim.groundY();
        if (rayBox(o, d, Rahim.X - 0.55f, rg, Rahim.Z - 0.55f, Rahim.X + 0.55f, rg + 1.95f, Rahim.Z + 0.55f, hit) && hit[0] <= reach && hit[0] < best.dist()) {
            double t = hit[0];
            best = new Target(TargetType.RAHIM, o.x + d.x * t, o.y + d.y * t, o.z + d.z * t, hit[0]);
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

    private void ensureTargets(int w, int h, int samples) {
        if (sceneFbo != null && w == lastW && h == lastH && samples == lastSamples) return;
        if (sceneFbo != null) { sceneFbo.destroy(); if (resolveFbo != sceneFbo) resolveFbo.destroy(); }
        lastW = w; lastH = h; lastSamples = samples;
        sceneFbo = Framebuffer.hdr(w, h, samples, true);
        resolveFbo = samples > 1 ? Framebuffer.hdr(w, h, 1, false) : sceneFbo;
    }

    private void common(Shader s, Camera cam) {
        s.use();
        s.set("uCamPos", cam.pos.x, cam.pos.y, cam.pos.z);
        s.set("uSunDir", atmo.sunDir.x, atmo.sunDir.y, atmo.sunDir.z);
        s.set("uSunColor", atmo.sunColor.x, atmo.sunColor.y, atmo.sunColor.z);
        s.set("uSkyZenith", atmo.zenith.x, atmo.zenith.y, atmo.zenith.z);
        s.set("uSkyHorizon", atmo.horizon.x, atmo.horizon.y, atmo.horizon.z);
        s.set("uGround", atmo.ground.x, atmo.ground.y, atmo.ground.z);
        s.set("uFogColor", atmo.fog.x, atmo.fog.y, atmo.fog.z);
        s.set("uFogDensity", atmo.fogDensity * (overlay ? 0.12f : 1f));
        s.set("uStorm", (float) storm);
        s.set("uTime", (float) time);
        s.set("uFlash", (float) Math.min(1, flash));
        s.set("uShadowOn", shadowActive ? 1f : 0f);
        s.set("uLightVP", lightVP);
        s.set("uShadow", 5);
    }

    private Matrix4f boatMatrix() {
        float g = land.height(Boat.X, Boat.Z);
        boolean floating = shownLevel > g + 0.12;
        float y = floating ? (float) shownLevel - 0.34f : g + 0.06f;
        float roll = floating ? (float) Math.sin(time * 1.2) * 0.04f : 0.05f, pitch = floating ? (float) Math.sin(time * 0.9 + 1) * 0.03f : -0.03f;
        return new Matrix4f().translate(Boat.X, y, Boat.Z).rotateY(Boat.YAW).rotateZ(roll).rotateX(pitch);
    }

    /** Fit an orthographic sun camera around the player and render the casters' depth. */
    private void shadowPass(Camera cam, Settings st) {
        shadowActive = st.shadows && atmo.sunDir.y > 0.07f;
        if (!shadowActive) return;
        if (shadowFbo == null) shadowFbo = Framebuffer.shadow(SHADOW_SIZE);
        float extent = 62f;
        Vector3f fwd = cam.forward();
        Vector3f centre = new Vector3f(cam.pos.x + fwd.x * 22f, Math.max(0, cam.pos.y * 0.2f), cam.pos.z + fwd.z * 22f);
        Vector3f sun = new Vector3f(atmo.sunDir);
        Matrix4f view = new Matrix4f().lookAt(new Vector3f(centre).add(new Vector3f(sun).mul(180f)), centre, new Vector3f(0, 1, 0));
        // snap the centre to whole shadow texels so the shadows do not shimmer when the camera moves
        float texel = 2 * extent / SHADOW_SIZE;
        Vector3f lc = view.transformPosition(new Vector3f(centre));
        lc.x = Math.round(lc.x / texel) * texel; lc.y = Math.round(lc.y / texel) * texel;
        Matrix4f inv = new Matrix4f(view).invert();
        Vector3f snapped = inv.transformPosition(new Vector3f(lc));
        view = new Matrix4f().lookAt(new Vector3f(snapped).add(new Vector3f(sun).mul(180f)), snapped, new Vector3f(0, 1, 0));
        lightVP.set(new Matrix4f().ortho(-extent, extent, -extent, extent, 5f, 420f)).mul(view);

        shadowFbo.bind();
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(2.0f, 4.0f);
        propsDepthS.use();
        propsDepthS.set("uLightVP", lightVP);
        propsDepthS.set("uModel", new Matrix4f());
        propsDepthS.set("uShadow", 5); propsDepthS.set("uShadowOn", 0f);
        propsMesh.draw();
        rahim.mesh().draw();
        propsDepthS.set("uModel", boatMatrix());
        boat.mesh().draw();
        glDisable(GL_POLYGON_OFFSET_FILL);
    }

    public void render(Camera cam, Settings st, Target highlight, int fbW, int fbH) {
        if (state == null || season == null) { glViewport(0, 0, fbW, fbH); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT); return; }
        int samples = st.msaa;
        ensureTargets(fbW, fbH, samples);

        shadowPass(cam, st);

        sceneFbo.bind();
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_MULTISAMPLE);
        glActiveTexture(GL_TEXTURE0 + 5);
        glBindTexture(GL_TEXTURE_2D, shadowFbo != null ? shadowFbo.depthTex : heightTex);
        glActiveTexture(GL_TEXTURE0 + 6);
        glBindTexture(GL_TEXTURE_2D, heightTex);
        glActiveTexture(GL_TEXTURE0);

        // sky (no depth)
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        common(skyS, cam);
        skyS.set("uInvVP", new Matrix4f(cam.viewProj).invert());
        org.lwjgl.opengl.GL30.glBindVertexArray(emptyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        org.lwjgl.opengl.GL30.glBindVertexArray(0);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDisable(GL_CULL_FACE);

        // terrain
        common(terrainS, cam);
        terrainS.set("uVP", cam.viewProj);
        terrainS.set("uLevel", (float) shownLevel);
        terrainS.set("uDry", (float) (1 - clamp01(shownLevel / 0.35)));
        terrainS.set("uCrop", (float) Math.min(1, cropMat * 0.9 + 0.1));
        terrainS.set("uFloodDmg", (float) clamp01((floodReach / 300.0) * (state.underwaterDays > 0 || !state.alive ? 1 : 0.4)));
        terrainS.set("uHarvested", state.harvested ? 1f : 0f);
        terrainS.set("uOverlay", overlay ? 1f : 0f);
        terrainS.set("uRainUp", (float) upRain);
        terrainS.set("uSoil", (float) clamp01(season.day(state.i).soil()));
        if (!skip("terrain")) terrainMesh.draw();

        // embankment
        common(dikeS, cam);
        dikeS.set("uVP", cam.viewProj);
        dikeS.set("uBundScale", (float) (shownBund / REF));
        dikeS.set("uRefH", REF);
        dikeMesh.draw();

        // props (house, trees, ...)
        common(propsS, cam);
        propsS.set("uVP", cam.viewProj);
        propsS.set("uModel", new Matrix4f());
        propsS.set("uWind", (float) (0.12 + storm * 0.9));
        propsS.set("uBundH", (float) shownBund);
        propsS.set("uWet", (float) clamp01(farmRain * 1.3));
        if (!skip("props")) propsMesh.draw();
        rahim.mesh().draw();
        propsS.set("uModel", boatMatrix());
        boat.mesh().draw();
        propsS.set("uModel", new Matrix4f());

        // plants
        common(foliage.shader(), cam);
        Shader fs = foliage.shader();
        fs.set("uVP", cam.viewProj);
        fs.set("uHeightTex", 6);
        float hsx = (float) Landscape.NX / (Landscape.NX + 1) / (Landscape.X1 - Landscape.X0), hsz = (float) Landscape.NZ / (Landscape.NZ + 1) / (Landscape.Z1 - Landscape.Z0);
        fs.set("uHeightRect", Landscape.X0 - 0.5f / (Landscape.NX + 1) / hsx, Landscape.Z0 - 0.5f / (Landscape.NZ + 1) / hsz, hsx, hsz);
        double wind = 0.10 + storm * 0.95;
        double grassFrac = st.grass == 2 ? 1.0 : st.grass == 1 ? 0.45 : 0.0;
        double riceFrac = st.grass == 2 ? 1.0 : st.grass == 1 ? 0.55 : 0.3;
        boolean lodged = state.flooded || !state.alive;
        double lean = !state.alive ? 0.9 : state.flooded ? clamp01(0.3 + shownLevel * 0.6) : 0;
        if (skip("foliage")) { } else if (state.harvested && state.alive) foliage.drawRice(0.11, 1.0, 0.0, 0.35, 0, wind, riceFrac, true);
        else foliage.drawRice(0.14 + 0.95 * Math.min(1, cropMat), cropMat, Math.max(0, (cropMat - 0.55) / 0.45), state.alive ? 0 : 1, lean, wind, riceFrac, true);
        if (!skip("foliage")) { foliage.drawGrass(wind, grassFrac); foliage.drawReeds(wind, st.grass == 0 ? 0.3 : 1.0); }

        // water
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        common(waterS, cam);
        waterS.set("uVP", cam.viewProj);
        waterS.set("uHeight", 6);
        float sx = (float) Landscape.NX / (Landscape.NX + 1) / (Landscape.X1 - Landscape.X0), sz = (float) Landscape.NZ / (Landscape.NZ + 1) / (Landscape.Z1 - Landscape.Z0);
        waterS.set("uHeightRect", Landscape.X0 - 0.5f / (Landscape.NX + 1) / sx, Landscape.Z0 - 0.5f / (Landscape.NZ + 1) / sz, sx, sz);
        waterS.set("uProtectZ", Landscape.DIKE_Z);
        waterS.set("uFloodReach", (float) floodReach);
        waterS.set("uTurbid", (float) (0.15 + 0.7 * clamp01((shownLevel - 0.3) / 0.8)));
        waterS.set("uRain", (float) farmRain);
        waterS.set("uRegion", 0f, 0f, 0f, 0f);
        waterS.set("uProtect", 1f);
        waterS.set("uFlow", 0f, 0f);
        waterS.set("uYAdd", 0f);
        waterS.set("uMeshY", (float) shownLevel);
        if (!skip("water")) waterMesh.draw();
        // the homestead pond
        waterS.set("uProtect", 0f);
        waterS.set("uMeshY", Landscape.POND_LEVEL);
        waterS.set("uRegion", Landscape.POND_X - 16, Landscape.POND_X + 16, Landscape.POND_Z - 16, Landscape.POND_Z + 16);
        waterS.set("uTurbid", 0.25f);
        if (!skip("water")) waterMesh.draw();
        waterS.set("uRegion", 0f, 0f, 0f, 0f);
        // the river coming down from the hills
        waterS.set("uMeshY", -999f);
        waterS.set("uYAdd", (float) (riverSwell * 1.5));
        waterS.set("uFlow", 0f, 2.2f);
        waterS.set("uTurbid", (float) (0.2 + riverSwell * 0.6));
        if (!skip("water")) riverMesh.draw();
        waterS.set("uFlow", 0f, 0f);

        // rain, hill rain curtains and sprite particles
        common(weather.curtainShader(), cam);
        weather.drawCurtains(cam, upRain);
        common(weather.rainShader(), cam);
        if (!overlay) weather.drawRain(cam, farmRain, 0.5 + storm * 0.9, 0.15);
        weather.drawParticles(cam, particles);
        glDepthMask(true);
        glDisable(GL_BLEND);

        // resolve + post
        if (resolveFbo != sceneFbo) sceneFbo.blitTo(resolveFbo);
        post.run(resolveFbo, fbW, fbH, atmo.exposure, st.bloom ? 0.045f : 0f, 1.0f, (float) time, 0.55f);
        glEnable(GL_DEPTH_TEST);
    }

    public void destroy() {
        terrainMesh.destroy(); waterMesh.destroy(); dikeMesh.destroy();
    }
}
