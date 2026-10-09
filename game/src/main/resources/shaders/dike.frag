#version 330 core
#include "common.glsl"
in vec3 vWorld;
in vec3 vNormal;
in float vRise;
uniform float uBundScale;
uniform float uRefH;
out vec4 frag;
void main() {
    vec3 P = vWorld;
    vec3 N = normalize(vNormal);
    float e = 0.3;
    vec2 q = P.xz * 3.0;
    N = normalize(N + 0.25 * vec3(fbm3(q + vec2(e, 0.)) - fbm3(q - vec2(e, 0.)), 0.0, fbm3(q + vec2(0., e)) - fbm3(q - vec2(0., e))));
    float n = fbm(P.xz * 0.5);
    vec3 grass = mix(vec3(0.12, 0.24, 0.05), vec3(0.22, 0.33, 0.08), n);
    vec3 earth = mix(vec3(0.36, 0.28, 0.18), vec3(0.45, 0.36, 0.23), fbm3(P.xz * 1.4));
    float crown = sstep(0.80, 0.98, vRise / uRefH);
    vec3 albedo = mix(grass, earth, crown * 0.75);
    // raised part: sandbags and fresh earth above the original crest
    float above = vRise * uBundScale - uRefH;
    if (above > 0.01) {
        float row = floor((above) / 0.14);
        float sx = P.x * 1.6 + (mod(row, 2.0)) * 0.5;
        vec3 bag = mix(vec3(0.62, 0.56, 0.42), vec3(0.50, 0.44, 0.32), hash21(vec2(floor(sx), row)));
        float seam = sstep(0.0, 0.06, fract(sx)) * sstep(0.0, 0.08, fract(above / 0.14));
        albedo = mix(albedo, bag * (0.7 + 0.3 * seam), sstep(0.01, 0.04, above));
    }
    float wetline = 1.0 - sstep(0.0, 0.2, abs(P.y - 0.0));
    albedo *= 1.0 - 0.0 * wetline;
    vec3 V = normalize(uCamPos - P);
    float ndl = max(dot(N, uSunDir), 0.0);
    float sh = shadowFactor(P, N);
    float wrap = clamp((dot(N, uSunDir) + 0.25) / 1.25, 0.0, 1.0);
    vec3 col = albedo * (uSunColor * mix(ndl, wrap, 0.35) * sh + hemiAmbient(N));
    frag = vec4(applyFog(col, P), 1.0);
}
