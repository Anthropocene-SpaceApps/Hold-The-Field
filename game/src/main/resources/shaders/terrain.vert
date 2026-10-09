#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
uniform float uFlatten;      // 0..1: the Barind is flat, so the far hills are pressed down to low ridges
out vec3 vWorld;
out vec3 vNormal;
void main() {
    vec3 p = aPos;
    vec3 n = aNormal;
    if (uFlatten > 0.0 && p.y > 3.0) {
        float k = mix(1.0, 0.10, uFlatten);
        p.y = 3.0 + (p.y - 3.0) * k;
        n = normalize(vec3(n.x * k, n.y, n.z * k));
    }
    vWorld = p;
    vNormal = n;
    gl_Position = uVP * vec4(p, 1.0);
}
