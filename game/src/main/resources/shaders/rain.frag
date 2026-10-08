#version 330 core
#include "common.glsl"
in float vAlpha;
uniform float uIntensity;
out vec4 frag;
void main() {
    vec3 c = hemiAmbient(vec3(0.0, 1.0, 0.0)) * 1.4 + uSunColor * 0.06 + vec3(0.25) * uFlash;
    frag = vec4(c, vAlpha * uIntensity * 0.42);
}
