#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 3) in vec2 aUv;
layout(location = 4) in float aMat;
uniform mat4 uLightVP;
uniform mat4 uModel;
out vec2 vUv;
flat out int vMat;
void main() {
    vUv = aUv;
    vMat = int(aMat + 0.5);
    gl_Position = uLightVP * (uModel * vec4(aPos, 1.0));
}
