#version 330 core
in vec2 vUv;
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uRadius;
out vec4 frag;
void main() {
    vec2 r = uTexel * uRadius;
    vec3 c = texture(uTex, vUv).rgb * 4.0;
    c += texture(uTex, vUv + vec2(-r.x, 0)).rgb * 2.0 + texture(uTex, vUv + vec2(r.x, 0)).rgb * 2.0;
    c += texture(uTex, vUv + vec2(0, -r.y)).rgb * 2.0 + texture(uTex, vUv + vec2(0, r.y)).rgb * 2.0;
    c += texture(uTex, vUv + vec2(-r.x, -r.y)).rgb + texture(uTex, vUv + vec2(r.x, -r.y)).rgb;
    c += texture(uTex, vUv + vec2(-r.x, r.y)).rgb + texture(uTex, vUv + vec2(r.x, r.y)).rgb;
    frag = vec4(c / 16.0, 1.0);
}
