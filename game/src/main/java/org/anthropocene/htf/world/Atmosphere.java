package org.anthropocene.htf.world;

import org.joml.Vector3f;

/**
 * Time of day and weather turned into the numbers the shaders need: sun direction and radiance, sky colours,
 * fog, exposure. All colours are linear HDR.
 */
public final class Atmosphere {
    public static final String[] TIME_MODES = {"Golden hour", "Morning", "Noon", "Dusk", "Day cycle"};
    private static final float[] FIXED_HOURS = {16.9f, 7.6f, 12.0f, 18.15f};

    public float hour = 16.9f;
    public final Vector3f sunDir = new Vector3f(), sunColor = new Vector3f();
    public final Vector3f zenith = new Vector3f(), horizon = new Vector3f(), ground = new Vector3f(), fog = new Vector3f();
    public float fogDensity, exposure = 1f, daylight = 1f;
    public float dust;                  // 0..1 warm dusty haze of a dry season

    public void update(double dt, int timeMode, double storm, double flash) {
        if (timeMode >= 4) hour = (float) ((hour + dt * 0.04) % 24);   // a full day in about 10 real minutes
        else hour += (FIXED_HOURS[Math.max(0, timeMode)] - hour) * (float) Math.min(1, dt * 2);
        compute((float) storm, (float) flash);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static Vector3f mix(Vector3f a, Vector3f b, float t) { return new Vector3f(a).lerp(b, t); }

    private void compute(float storm, float flash) {
        float a = (hour - 6f) / 12f * (float) Math.PI;
        float sinEl = (float) Math.sin(a) * 0.93f;
        Vector3f sd = new Vector3f((float) Math.cos(a), sinEl, 0.38f).normalize();
        float day = smooth(0.02f, 0.38f, sinEl);                       // 0 night/dusk .. 1 full day
        float lit = smooth(-0.06f, 0.10f, sinEl);                      // sun above the horizon
        float twilight = (1 - smooth(0.0f, 0.30f, Math.abs(sinEl))) * (sinEl > -0.2f ? 1 : 0);

        // sunlight: warm and weak near the horizon, white and strong high up
        Vector3f warm = new Vector3f(2.5f, 1.05f, 0.40f), white = new Vector3f(2.75f, 2.5f, 2.2f);   // irradiance-ish; albedo/pi is folded in
        Vector3f sun = mix(warm, white, smooth(0.03f, 0.5f, sinEl)).mul(lit);
        // moonlight keeps the night readable
        Vector3f moon = new Vector3f(0.025f, 0.04f, 0.075f);
        if (lit < 0.5f) { sun.add(new Vector3f(moon).mul(1 - lit * 2)); }
        sunDir.set(lit < 0.05f ? new Vector3f(-sd.x, Math.abs(sd.y) + 0.3f, -sd.z).normalize() : sd);

        Vector3f zNight = new Vector3f(0.004f, 0.008f, 0.025f), zDusk = new Vector3f(0.10f, 0.14f, 0.34f), zDay = new Vector3f(0.10f, 0.27f, 0.68f);
        Vector3f hNight = new Vector3f(0.015f, 0.022f, 0.045f), hDay = new Vector3f(0.62f, 0.76f, 0.92f), hGold = new Vector3f(1.05f, 0.58f, 0.30f);
        Vector3f z = mix(zNight, mix(zDusk, zDay, day), smooth(-0.14f, 0.06f, sinEl));
        Vector3f h = mix(hNight, mix(hDay, hGold, twilight * 0.85f), smooth(-0.14f, 0.04f, sinEl));
        if (sinEl > 0.04f) h = mix(h, hDay, day * 0.55f);

        // overcast: everything pulls towards a flat grey, sunlight drops
        float dayGrey = 0.12f + 0.88f * Math.max(day, lit * 0.4f);
        Vector3f grey = new Vector3f(0.30f, 0.33f, 0.37f).mul(dayGrey);
        float s = (float) Math.pow(storm, 0.8);
        z = mix(z, new Vector3f(grey).mul(0.7f), s * 0.85f);
        h = mix(h, new Vector3f(grey).mul(1.05f), s * 0.85f);
        sun.mul(1 - 0.84f * s);

        zenith.set(z); horizon.set(h);
        sunColor.set(sun);
        ground.set(new Vector3f(0.20f, 0.17f, 0.12f).mul(0.25f + 0.75f * dayGrey));
        fog.set(mix(h, new Vector3f(grey), 0.2f));
        fogDensity = 0.00085f + s * 0.0013f;
        if (dust > 0.001f) {                                           // hot, dusty haze: warm and thick near the ground
            Vector3f haze = new Vector3f(0.78f, 0.62f, 0.42f).mul(0.35f + 0.65f * Math.max(day, 0.25f));
            horizon.set(mix(horizon, haze, dust * 0.40f));
            zenith.set(mix(zenith, new Vector3f(zenith).mul(1.0f, 0.92f, 0.78f), dust * 0.6f));
            fog.set(mix(fog, haze, dust * 0.7f));
            fogDensity += dust * 0.0011f;
            sunColor.mul(1 - 0.15f * dust);
        }
        daylight = Math.max(day, 0.12f) * (1 - 0.55f * s);
        exposure = (0.95f + 0.45f * (1 - day)) * (1 + flash * 0.3f);
    }

    /** Ambient light colour for a surface facing up (used for tinting small effects). */
    public Vector3f ambientUp() { return new Vector3f(zenith).mul(0.8f).add(new Vector3f(horizon).mul(0.4f)); }
}
