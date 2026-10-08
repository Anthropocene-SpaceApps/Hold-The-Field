// Shared uniforms, noise and lighting helpers. Included after "#version 330 core".
uniform vec3  uCamPos;
uniform vec3  uSunDir;        // direction towards the sun (or moon at night)
uniform vec3  uSunColor;      // HDR radiance
uniform vec3  uSkyZenith;
uniform vec3  uSkyHorizon;
uniform vec3  uGround;
uniform vec3  uFogColor;
uniform float uFogDensity;
uniform float uStorm;         // 0 clear .. 1 thunderstorm
uniform float uTime;
uniform float uFlash;         // lightning flash 0..1
uniform mat4  uLightVP;
uniform sampler2D uShadow;
uniform float uShadowOn;

const float PI = 3.14159265;

// smoothstep that is also defined when edge0 > edge1
float sstep(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }

float hash11(float p) { p = fract(p * .1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash21(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * .1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec2  hash22(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * vec3(.1031, .1030, .0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xx + p3.yz) * p3.zy); }

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x), mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { s += a * vnoise(p); p = p * 2.03 + vec2(17.1, 9.2); a *= 0.5; }
    return s / 0.96875;
}

float fbm3(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) { s += a * vnoise(p); p = p * 2.07 + vec2(5.2, 1.7); a *= 0.5; }
    return s / 0.875;
}

// Percentage-closer filtered sun shadow (1 = lit)
float shadowFactor(vec3 wp, vec3 n) {
    if (uShadowOn < 0.5) return 1.0;
    vec4 lp = uLightVP * vec4(wp + n * 0.06, 1.0);
    vec3 c = lp.xyz / lp.w * 0.5 + 0.5;
    if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
    vec2 ts = 1.0 / vec2(textureSize(uShadow, 0));
    float bias = 0.0012;
    float s = 0.0;
    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
        s += (c.z - bias <= texture(uShadow, c.xy + vec2(x, y) * ts * 1.3).r) ? 1.0 : 0.0;
    s /= 9.0;
    float edge = min(min(c.x, 1.0 - c.x), min(c.y, 1.0 - c.y));
    return mix(1.0, s, smoothstep(0.0, 0.06, edge));
}

vec3 hemiAmbient(vec3 n) {
    float up = n.y * 0.5 + 0.5;
    vec3 sky = uSkyZenith * 0.75 + uSkyHorizon * 0.45;
    return mix(uGround, sky, up) * 0.9 + uFlash * vec3(0.5, 0.55, 0.7);
}

vec3 applyFog(vec3 col, vec3 wp) {
    vec3 v = wp - uCamPos;
    float d = length(v);
    vec3 dir = v / max(d, 1e-4);
    float heightFade = exp(-max(wp.y, 0.0) * 0.004);
    float f = 1.0 - exp(-d * uFogDensity * (0.35 + 0.65 * heightFade));
    float sunGlow = pow(max(dot(dir, uSunDir), 0.0), 8.0);
    vec3 fc = uFogColor + uSunColor * 0.11 * sunGlow;
    return mix(col, fc, clamp(f, 0.0, 1.0));
}
