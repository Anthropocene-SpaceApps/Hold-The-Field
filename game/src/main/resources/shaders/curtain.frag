#version 330 core
#include "common.glsl"
in vec2 vUv;
in vec3 vWorld;
uniform float uIntensity;
out vec4 frag;
void main() {
    // streaks falling at an angle, bunched into heavier shafts
    float x = vUv.x * 160.0 + vUv.y * 6.0;
    float streak = vnoise(vec2(x, vUv.y * 30.0 + uTime * 6.0));
    streak = smoothstep(0.35, 0.95, streak);
    float shafts = fbm(vec2(vUv.x * 7.0, uTime * 0.05 + vUv.y * 0.6));
    float body = smoothstep(0.30, 0.75, shafts) * 0.65 + 0.2;
    float edge = smoothstep(0.0, 0.18, vUv.x) * smoothstep(1.0, 0.82, vUv.x) * smoothstep(0.0, 0.2, vUv.y) * smoothstep(1.0, 0.75, vUv.y);
    float a = (streak * 0.5 + 0.5) * body * edge * uIntensity;
    vec3 c = mix(uSkyHorizon * 0.55, vec3(0.28, 0.31, 0.36) * (0.4 + 0.6 * max(uSunDir.y, 0.1)), 0.55) + vec3(0.2) * uFlash;
    vec3 fogged = applyFog(c, vWorld);
    frag = vec4(fogged, clamp(a * 0.75, 0.0, 0.85));
}
