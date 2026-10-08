#version 330 core
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uThreshold;     // > 0 only on the first pass
out vec4 frag;
vec3 prefilter(vec3 c) {
    if (any(isnan(c)) || any(isinf(c))) return vec3(0.0);
    c = min(c, vec3(64.0));
    if (uThreshold <= 0.0) return c;
    float b = max(c.r, max(c.g, c.b));
    float soft = clamp(b - uThreshold + 0.5, 0.0, 1.0);
    soft = soft * soft * 0.5;
    return c * max(soft, b - uThreshold) / max(b, 1e-4);
}
void main() {
    vec3 a = prefilter(texture(uTex, vUv + uTexel * vec2(-2, -2)).rgb);
    vec3 b = prefilter(texture(uTex, vUv + uTexel * vec2(0, -2)).rgb);
    vec3 c = prefilter(texture(uTex, vUv + uTexel * vec2(2, -2)).rgb);
    vec3 d = prefilter(texture(uTex, vUv + uTexel * vec2(-2, 0)).rgb);
    vec3 e = prefilter(texture(uTex, vUv).rgb);
    vec3 f = prefilter(texture(uTex, vUv + uTexel * vec2(2, 0)).rgb);
    vec3 g = prefilter(texture(uTex, vUv + uTexel * vec2(-2, 2)).rgb);
    vec3 h = prefilter(texture(uTex, vUv + uTexel * vec2(0, 2)).rgb);
    vec3 i = prefilter(texture(uTex, vUv + uTexel * vec2(2, 2)).rgb);
    vec3 j = prefilter(texture(uTex, vUv + uTexel * vec2(-1, -1)).rgb);
    vec3 k = prefilter(texture(uTex, vUv + uTexel * vec2(1, -1)).rgb);
    vec3 l = prefilter(texture(uTex, vUv + uTexel * vec2(-1, 1)).rgb);
    vec3 m = prefilter(texture(uTex, vUv + uTexel * vec2(1, 1)).rgb);
    vec3 col = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    frag = vec4(col, 1.0);
}
