#version 330 core
#include "common.glsl"
in vec3 vWorld;
in vec3 vNormal;
in float vT_;
in float vSeed;
uniform float uMat;        // 0 young .. 1 ripe
uniform float uDead;       // 0..1 drowned / dead
uniform float uWilt;       // 0..1 parched: straw yellow, rolled leaves
uniform float uPalette;    // 0 rice, 1 grass, 2 reed
out vec4 frag;
void main() {
    float vT = clamp(vT_, 0.0, 1.0);
    float v = fract(vSeed * 13.7);
    vec3 col;
    if (uPalette < 0.5) {
        vec3 youngBase = vec3(0.045, 0.13, 0.025), youngTip = vec3(0.18, 0.38, 0.07);
        vec3 ripeBase = vec3(0.22, 0.22, 0.06), ripeTip = vec3(0.80, 0.62, 0.17);
        vec3 young = mix(youngBase, youngTip, vT);
        vec3 ripe = mix(ripeBase, ripeTip, smoothstep(0.1, 0.9, vT));
        col = mix(young, ripe, smoothstep(0.50, 0.95, uMat));
        float heads = smoothstep(0.78, 0.9, vT) * smoothstep(0.6, 0.95, uMat);
        col = mix(col, vec3(0.86, 0.68, 0.20), heads * 0.6);
        col = mix(col, vec3(0.25, 0.19, 0.11) * (0.7 + 0.5 * vT), uDead);
        col = mix(col, mix(vec3(0.30, 0.28, 0.10), vec3(0.74, 0.64, 0.30), vT) * (0.8 + 0.4 * v), uWilt * 0.9);
    } else if (uPalette < 1.5) {
        col = mix(vec3(0.07, 0.15, 0.03), mix(vec3(0.24, 0.36, 0.09), vec3(0.45, 0.42, 0.15), v), vT);
        col = mix(col, mix(vec3(0.34, 0.28, 0.12), vec3(0.62, 0.52, 0.28), vT), uWilt);
    } else {
        col = mix(vec3(0.14, 0.2, 0.06), mix(vec3(0.45, 0.45, 0.20), vec3(0.62, 0.52, 0.26), v), vT);
        col = mix(col, mix(vec3(0.40, 0.33, 0.16), vec3(0.68, 0.58, 0.32), vT), uWilt);
    }
    col *= 0.85 + 0.3 * v;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    N = normalize(mix(N, vec3(0.0, 1.0, 0.0), 0.5));
    float ao = mix(0.22, 1.0, pow(vT, 0.7));
    float wrap = clamp((dot(N, uSunDir) + 0.5) / 1.5, 0.0, 1.0);
    float sh = shadowFactor(vWorld, vec3(0.0, 1.0, 0.0));
    float trans = pow(max(dot(-V, uSunDir), 0.0), 2.0) * 0.55;
    vec3 c = col * ao * (uSunColor * (wrap * 0.85 + trans) * sh + hemiAmbient(N));
    vec3 outc = applyFog(c, vWorld);
    if (any(isnan(outc)) || any(isinf(outc))) outc = vec3(50.0, 0.0, 50.0);
    frag = vec4(outc, 1.0);
}
