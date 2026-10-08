#version 330 core
layout(location = 0) in vec4 aPosSize;
layout(location = 1) in vec4 aColor;
uniform mat4 uVP;
uniform vec3 uRight;
uniform vec3 uUp;
out vec4 vColor;
out vec2 vUv;
const vec2 C[6] = vec2[6](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));
void main() {
    vec2 c = C[gl_VertexID % 6];
    vec3 p = aPosSize.xyz + (uRight * c.x + uUp * c.y) * aPosSize.w;
    vColor = aColor;
    vUv = c;
    gl_Position = uVP * vec4(p, 1.0);
}
