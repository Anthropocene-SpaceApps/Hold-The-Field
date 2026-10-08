#version 330 core
in vec2 vUv;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uExposure;
uniform float uBloomStrength;
uniform float uTime;
uniform float uVignette;
out vec4 frag;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 sc = texture(uScene, vUv).rgb;
    if (any(isnan(sc)) || any(isinf(sc))) sc = vec3(0.0);
    vec3 hdr = sc + texture(uBloom, vUv).rgb * uBloomStrength;
    vec3 col = aces(hdr * uExposure);
    // gentle grade: a touch of warmth in the lights, cooler shadows
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col * vec3(0.97, 0.99, 1.04), col * vec3(1.04, 1.0, 0.95), smoothstep(0.2, 0.9, l));
    col = mix(vec3(l), col, 1.06);
    vec2 q = vUv - 0.5;
    col *= 1.0 - uVignette * dot(q, q) * 1.6;
    col = pow(col, vec3(1.0 / 2.2));
    float g = fract(sin(dot(vUv * 1000.0 + uTime, vec2(12.9898, 78.233))) * 43758.5453);
    col += (g - 0.5) / 255.0 * 2.0;
    frag = vec4(col, 1.0);
}
