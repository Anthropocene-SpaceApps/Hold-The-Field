#version 330 core
#include "common.glsl"
#include "sky.glsl"
in vec2 vUv;
uniform mat4 uInvVP;
out vec4 frag;
void main() {
    vec4 w = uInvVP * vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(w.xyz / w.w - uCamPos);
    frag = vec4(skyColor(dir), 1.0);
}
