#version 330 core
#include "common.glsl"
uniform mat4 uVP;
uniform vec3 uWind;
uniform float uFall;
out float vAlpha;
const vec2 C[6] = vec2[6](vec2(0, -1), vec2(0, 1), vec2(1, 1), vec2(0, -1), vec2(1, 1), vec2(1, -1));
void main() {
    float id = float(gl_InstanceID);
    vec3 h = vec3(hash11(id * 1.31 + 0.5), hash11(id * 2.17 + 3.0), hash11(id * 3.73 + 7.0));
    vec3 box = vec3(44.0, 28.0, 44.0);
    vec3 pos = vec3(h.x, fract(h.y - uTime * uFall / box.y), h.z) * box - vec3(box.x * 0.5, box.y * 0.3, box.z * 0.5);
    vec3 world = uCamPos + pos;
    world.xz += uWind.xz * (pos.y + box.y * 0.3) * 0.12;
    vec3 dir = normalize(vec3(uWind.x * 0.35, -1.0, uWind.z * 0.35));
    float len = 0.55 + h.z * 0.5;
    vec2 c = C[gl_VertexID % 6];
    vec3 toCam = normalize(uCamPos - world);
    vec3 side = normalize(cross(dir, toCam)) * 0.006;
    vec3 p = world + dir * len * c.x + side * c.y;
    float d = length(pos);
    vAlpha = (1.0 - c.x * 0.75) * smoothstep(1.2, 4.0, d) * (1.0 - smoothstep(22.0, 40.0, d));
    gl_Position = uVP * vec4(p, 1.0);
}
