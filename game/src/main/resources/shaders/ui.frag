#version 330 core
in vec2 vUv;
in vec4 vColor;
in vec2 vLocal;
in vec4 vShape;
in float vMode;
uniform sampler2D uTex;
out vec4 frag;
float sdRound(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
void main() {
    if (vMode < 0.5) { frag = texture(uTex, vUv) * vColor; return; }
    float d = sdRound(vLocal, vShape.xy, vShape.z);
    float aa = max(fwidth(d), 1e-3);
    float a;
    if (vMode < 1.5) a = clamp(0.5 - d / aa, 0.0, 1.0);                                   // fill
    else if (vMode < 2.5) a = clamp(0.5 - d / aa, 0.0, 1.0) * clamp(0.5 + (d + vShape.w) / aa, 0.0, 1.0);   // ring
    else a = 1.0 - smoothstep(-vShape.w, vShape.w, d);                                    // soft shadow
    frag = vec4(vColor.rgb, vColor.a * a);
}
