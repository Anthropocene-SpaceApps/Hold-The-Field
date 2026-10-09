#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
uniform mat4 uVP;
out vec2 vUv;
out vec3 vWorld;
void main() {
    vUv = aUv;
    vWorld = aPos;
    gl_Position = uVP * vec4(aPos, 1.0);
}
