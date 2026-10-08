#version 330 core
#include "common.glsl"
#include "sky.glsl"
in vec3 vWorld;
uniform sampler2D uHeight;
uniform vec4  uHeightRect;   // x0, z0, 1/width, 1/depth
uniform float uProtectZ;     // embankment line
uniform float uFloodReach;   // metres south of the embankment the flood has reached
uniform float uTurbid;       // 0 clear .. 1 sediment-laden flood water
uniform float uRain;         // 0..1
uniform vec4  uRegion;       // minX, maxX, minZ, maxZ clip rectangle for local water bodies (pond)
uniform float uProtect;      // 1 for the haor (held back by the embankment), 0 for pond and river
uniform vec2  uFlow;         // water current in m/s (river)
out vec4 frag;

float terrainH(vec2 xz) { return texture(uHeight, (xz - uHeightRect.xy) * uHeightRect.zw).r; }

vec3 waveNormal(vec2 p, float t, float chop) {
    vec2 g = vec2(0.0);
    // sum of travelling waves (analytic slopes)
    const int N = 5;
    for (int i = 0; i < N; i++) {
        float fi = float(i);
        float ang = 0.6 + fi * 1.13 + 0.3 * sin(fi * 7.0);
        vec2 d = vec2(cos(ang), sin(ang));
        float k = 0.55 + fi * 0.9;
        float w = sqrt(9.8 * k);
        float amp = 0.020 / (1.0 + fi * 0.9);
        float ph = dot(d, p) * k + t * w * 0.6;
        g += d * k * amp * cos(ph);
    }
    g *= chop;
    g += (vec2(vnoise(p * 3.1 + t * 0.35), vnoise(p * 3.1 + 40.0 - t * 0.31)) - 0.5) * 0.06 * chop;
    return normalize(vec3(-g.x, 1.0, -g.y));
}

float ripples(vec2 p, float intensity) {
    if (intensity < 0.02) return 0.0;
    float r = 0.0;
    vec2 gp = p * 2.2;
    vec2 id = floor(gp), f = fract(gp) - 0.5;
    float seed = hash21(id);
    float phase = fract(uTime * (0.8 + seed) + seed * 7.0);
    float d = length(f - (hash22(id) - 0.5) * 0.4);
    float ring = sstep(0.03, 0.0, abs(d - phase * 0.5)) * (1.0 - phase);
    return ring * step(1.0 - intensity * 0.9, hash21(id + 11.0));
}

void main() {
    vec2 xz = vWorld.xz;
    float hgt = terrainH(xz);
    float depth = vWorld.y - hgt;

    // the embankment holds the water back until it is overtopped
    float south = xz.y - uProtectZ;
    if (uProtect > 0.5 && south > 0.0) depth *= (1.0 - sstep(uFloodReach - 30.0, uFloodReach, south)) * step(0.5, uFloodReach);
    if (uRegion.y > uRegion.x) {
        if (xz.x < uRegion.x || xz.x > uRegion.y || xz.y < uRegion.z || xz.y > uRegion.w) discard;
    }
    if (depth < 0.004) discard;

    vec3 P = vWorld;
    vec3 V = normalize(uCamPos - P);
    float dist = length(uCamPos - P);
    float chop = (0.55 + uStorm * 1.6 + uRain * 0.8) * (1.0 - sstep(80.0, 600.0, dist) * 0.7);
    vec2 wxz = xz - uFlow * uTime;
    vec3 N = waveNormal(wxz, uTime, chop);
    float rp = ripples(xz, uRain);
    N = normalize(N + vec3(rp * 0.5, 0.0, rp * 0.3));
    N = normalize(mix(N, vec3(0.0, 1.0, 0.0), sstep(150.0, 700.0, dist) * 0.6));

    float cosT = clamp(dot(N, V), 0.0, 1.0);
    float fres = 0.02 + 0.98 * pow(1.0 - cosT, 5.0);
    vec3 R = reflect(-V, N);
    R.y = abs(R.y) * 0.9 + 0.04;
    vec3 refl = skyColor(normalize(R));

    // body colour from depth: clear tea-green water vs sediment-laden flood
    vec3 clearShallow = vec3(0.07, 0.17, 0.15), clearDeep = vec3(0.01, 0.05, 0.07);
    vec3 muddyShallow = vec3(0.28, 0.21, 0.12), muddyDeep = vec3(0.17, 0.12, 0.07);
    vec3 shallow = mix(clearShallow, muddyShallow, uTurbid), deep = mix(clearDeep, muddyDeep, uTurbid);
    float k = mix(0.9, 2.6, uTurbid);
    float a = 1.0 - exp(-depth * k);
    vec3 body = mix(shallow, deep, a) * (hemiAmbient(vec3(0, 1, 0)) * 0.9 + uSunColor * 0.45 * max(uSunDir.y, 0.0));

    vec3 H = normalize(uSunDir + V);
    float spec = pow(max(dot(N, H), 0.0), 220.0) * (1.0 - uStorm * 0.9);
    vec3 col = mix(body, refl, fres) + uSunColor * spec * 5.0;

    // foam along shores and on rough water
    float foamN = fbm(xz * 1.4 + uTime * 0.15);
    float shore = (1.0 - sstep(0.0, 0.22, depth)) * sstep(0.30, 0.65, foamN);
    float whitecap = sstep(0.62, 0.9, foamN) * uStorm * 0.5 * sstep(0.4, 1.2, depth);
    col = mix(col, vec3(0.75, 0.78, 0.76) * (0.35 + 0.65 * max(uSunDir.y, 0.15)), clamp(shore * 0.85 + whitecap, 0.0, 1.0));
    col += vec3(rp) * 0.25;

    float alpha = clamp(sstep(0.0, 0.07, depth) * (0.30 + 0.70 * (1.0 - exp(-depth * (k * 1.7)))) + fres * 0.4, 0.0, 1.0);
    col = applyFog(col, P);
    frag = vec4(col, alpha);
}
