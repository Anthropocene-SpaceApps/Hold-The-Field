#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in vec2 aUv;
layout(location = 4) in float aMat;
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uTime;
uniform float uWind;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out vec2 vUv;
flat out int vMat;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    int mat = int(aMat + 0.5);
    // leaves and fronds flutter in the wind
    if (mat == 5 || mat == 12) {
        float sway = sin(uTime * (1.6 + uWind * 1.4) + w.x * 0.7 + w.z * 0.5) * (0.018 + 0.05 * uWind) * aUv.y;
        w.x += sway; w.z += sway * 0.6;
    }
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    vColor = aColor;
    vUv = aUv;
    vMat = mat;
    gl_Position = uVP * w;
}
