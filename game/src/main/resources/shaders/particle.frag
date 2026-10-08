#version 330 core
in vec4 vColor;
in vec2 vUv;
out vec4 frag;
void main() {
    float d = length(vUv);
    if (d > 1.0) discard;
    float a = (1.0 - d * d) * vColor.a;
    frag = vec4(vColor.rgb, a);
}
