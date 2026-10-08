#version 330 core
#include "common.glsl"
in vec3 vWorld;
in vec3 vNormal;
uniform float uLevel;        // water level above the field (m)
uniform float uDry;          // 0 wet .. 1 baked dry (boro season haor mud)
uniform float uCrop;         // season crop maturity for the neighbouring fields, 0..1
uniform float uFloodDmg;     // 0..1 how drowned the fields look
uniform float uHarvested;    // 1 when the neighbours have harvested
uniform float uOverlay;      // 1 = satellite data layer
uniform float uRainUp;       // 0..1 upstream rain
uniform float uSoil;         // 0..1 soil wetness
out vec4 frag;

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float t = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * t);
}

vec3 cropColor(float mat, float r) {
    vec3 young = vec3(0.16, 0.36, 0.08), mid = vec3(0.24, 0.44, 0.10), ripe = vec3(0.70, 0.55, 0.17);
    float m = clamp(mat + (r - 0.5) * 0.12, 0.0, 1.0);
    vec3 c = m < 0.6 ? mix(young, mid, m / 0.6) : mix(mid, ripe, (m - 0.6) / 0.4);
    return c;
}

void main() {
    vec3 P = vWorld;
    vec3 N = normalize(vNormal);
    float dist = length(P - uCamPos);
    float slope = 1.0 - N.y;

    // fine bump from noise
    float e = 0.4;
    vec2 q = P.xz * 2.3;
    float nx = fbm3(q + vec2(e, 0.0)) - fbm3(q - vec2(e, 0.0));
    float nz = fbm3(q + vec2(0.0, e)) - fbm3(q - vec2(0.0, e));
    float bumpFade = 1.0 - sstep(30.0, 140.0, dist);
    N = normalize(N + vec3(nx, 0.0, nz) * 0.22 * bumpFade);

    // ---------- base ground cover
    float gn = fbm(P.xz * 0.30), gn2 = fbm(P.xz * 0.045);
    vec3 grass = mix(vec3(0.10, 0.22, 0.045), vec3(0.20, 0.32, 0.075), gn) * mix(0.8, 1.2, gn2);
    vec3 dryGrass = mix(vec3(0.30, 0.27, 0.10), vec3(0.40, 0.33, 0.13), gn);
    vec3 albedo = mix(grass, dryGrass, sstep(0.55, 0.9, gn2) * 0.7);
    float rough = 0.85;

    // ---------- floodplain patchwork of paddy fields
    float plain = sstep(-34.0, -30.0, P.z) * (1.0 - sstep(360.0, 420.0, abs(P.x)));
    vec2 cell = vec2(78.0, 56.0);
    vec2 fp = P.xz + vec2(-39.0, -28.0);
    vec2 cid = floor(fp / cell), cuv = fract(fp / cell);
    float cr = hash21(cid + 3.7);
    float isField = plain * step(0.12, cr) * step(0.04, cuv.x) * step(cuv.x, 0.96) * step(0.05, cuv.y) * step(cuv.y, 0.95);
    float nearHome = 1.0 - sstep(26.0, 46.0, length((P.xz - vec2(72.0, 4.0)) * vec2(0.85, 1.05)));
    isField *= 1.0 - nearHome;
    float rows = 0.5 + 0.5 * sin(P.z * 24.0 + hash21(cid) * 6.0);
    rows = mix(rows, 0.5, sstep(25.0, 110.0, dist));
    vec3 crop = cropColor(uCrop + (cr - 0.5) * 0.1, cr);
    crop = mix(crop, vec3(0.60, 0.50, 0.30), uHarvested);               // stubble after harvest
    crop = mix(crop, vec3(0.22, 0.17, 0.10), uFloodDmg * 0.8);           // drowned and silted
    crop *= 0.82 + 0.28 * rows;
    albedo = mix(albedo, crop, isField);
    rough = mix(rough, 0.9, isField);

    // ---------- Rahim's plot: puddled mud under the rice
    vec2 pd = max(abs(P.xz - vec2(0.0, 0.0)) - vec2(30.0, 22.0), vec2(0.0));
    float inPlot = 1.0 - sstep(0.0, 0.9, length(pd));
    vec3 mud = mix(vec3(0.075, 0.055, 0.035), vec3(0.11, 0.085, 0.055), fbm3(P.xz * 1.7));
    albedo = mix(albedo, mud, inPlot);
    rough = mix(rough, 0.35, inPlot * 0.7);

    // ---------- homestead: packed earth, paths
    float yard = 1.0 - sstep(9.0, 17.0, length((P.xz - vec2(72.0, 4.0)) * vec2(0.9, 1.1)));
    float path1 = 1.0 - sstep(0.9, 1.9, segDist(P.xz, vec2(31.0, 0.0), vec2(56.0, 6.0)));
    float path2 = 1.0 - sstep(0.8, 1.7, segDist(P.xz, vec2(72.0, 16.0), vec2(70.0, 80.0)));
    float path3 = 1.0 - sstep(0.7, 1.5, segDist(P.xz, vec2(-6.0, -36.0), vec2(-5.0, 24.0)));
    vec3 earth = mix(vec3(0.33, 0.25, 0.16), vec3(0.42, 0.33, 0.21), fbm3(P.xz * 1.1));
    float dirt = clamp(yard + path1 * 0.9 + path2 * 0.85 + path3 * 0.7, 0.0, 1.0);
    albedo = mix(albedo, earth, dirt);
    rough = mix(rough, 0.95, dirt);

    // ---------- the haor basin: cracked mud flats, wet mud and reeds
    float basin = sstep(-36.0, -40.0, P.z) * (1.0 - sstep(-300.0, -340.0, P.z));
    if (basin > 0.0) {
        vec2 vp = P.xz * 0.9;
        vec2 gi = floor(vp), gf = fract(vp);
        float md = 9.0, md2 = 9.0;
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash22(gi + g);
            float dd = length(g + o - gf);
            if (dd < md) { md2 = md; md = dd; } else if (dd < md2) md2 = dd;
        }
        float crack = sstep(0.0, 0.12, md2 - md);
        vec3 mudDry = mix(vec3(0.46, 0.40, 0.30), vec3(0.55, 0.49, 0.38), fbm3(P.xz * 0.8)) * mix(0.55, 1.0, crack);
        vec3 mudWet = vec3(0.17, 0.14, 0.10);
        vec3 flats = mix(mudWet, mudDry, uDry * sstep(-0.55, 0.12, P.y));
        float reeds = sstep(0.52, 0.70, fbm(P.xz * 0.11 + 4.0)) * sstep(-0.25, 0.08, P.y);
        flats = mix(flats, vec3(0.20, 0.30, 0.09), reeds * 0.8);
        albedo = mix(albedo, flats, basin);
        rough = mix(rough, 0.9, basin);
    }

    // ---------- hills: forest, scrub and rock
    float hill = sstep(2.5, 22.0, P.y);
    vec3 forest = mix(vec3(0.045, 0.12, 0.035), vec3(0.085, 0.19, 0.055), fbm(P.xz * 0.5)) * (0.7 + 0.5 * vnoise(P.xz * 1.8));
    vec3 scrub = mix(vec3(0.16, 0.25, 0.07), vec3(0.28, 0.30, 0.10), gn);
    vec3 hillCol = mix(forest, scrub, sstep(40.0, 160.0, P.y) * 0.6);
    vec3 rock = mix(vec3(0.30, 0.27, 0.23), vec3(0.45, 0.41, 0.35), fbm3(P.xz * 0.3));
    hillCol = mix(hillCol, rock, sstep(0.30, 0.55, slope) * 0.9);
    albedo = mix(albedo, hillCol, hill);

    // river sand and gravel
    float rb = abs(P.x - (-20.0 + 70.0 * sin(P.z * 0.0045) + 25.0 * sin(P.z * 0.013 + 1.3)));
    float bank = (1.0 - sstep(6.0, 22.0, rb)) * step(P.z, -250.0) * (1.0 - sstep(6.0, 30.0, P.y));
    albedo = mix(albedo, mix(vec3(0.55, 0.50, 0.40), vec3(0.34, 0.31, 0.27), vnoise(P.xz * 3.0)), bank * 0.85);

    // wet sheen near the water line, muddy shore
    float waterNear = 1.0 - sstep(0.0, 0.25, abs(P.y - uLevel));
    albedo = mix(albedo, albedo * 0.45, waterNear * 0.8 * step(P.z, -38.0));

    // ---------- satellite data overlay (soil wetness + rain)
    if (uOverlay > 0.5) {
        vec3 soilCol = mix(vec3(0.55, 0.42, 0.22), vec3(0.10, 0.35, 0.85), uSoil);
        float luminance_ = dot(albedo, vec3(0.3, 0.59, 0.11));
        albedo = mix(albedo, soilCol * (0.5 + 0.5 * luminance_), 0.38);
        vec2 rc = vec2(24.0, -640.0);
        float rr = length((P.xz - rc) * vec2(0.8, 1.0)) / 260.0;
        float cloud = (1.0 - sstep(0.35, 1.0, rr + (fbm(P.xz * 0.02) - 0.5) * 0.5)) * uRainUp;
        vec3 ramp = mix(vec3(0.1, 0.4, 1.0), mix(vec3(0.7, 0.2, 0.9), vec3(1.0, 0.25, 0.2), sstep(0.55, 1.0, cloud)), sstep(0.25, 0.6, cloud));
        albedo = mix(albedo, ramp, sstep(0.08, 0.35, cloud) * 0.6);
    }

    // ---------- lighting
    vec3 V = normalize(uCamPos - P);
    float ndl = max(dot(N, uSunDir), 0.0);
    float sh = shadowFactor(P, N);
    vec3 H = normalize(uSunDir + V);
    float spec = pow(max(dot(N, H), 0.0), mix(18.0, 90.0, 1.0 - rough)) * (1.0 - rough) * 0.5;
    float wrap = clamp((dot(N, uSunDir) + 0.25) / 1.25, 0.0, 1.0);
    vec3 col = albedo * (uSunColor * mix(ndl, wrap, 0.35) * sh + hemiAmbient(N)) + uSunColor * spec * sh;
    col = applyFog(col, P);
    frag = vec4(col, 1.0);
}
