#version 330 core
#include "common.glsl"
in vec2 vUv;
flat in int vMat;
void main() {
    if (vMat == 5 || vMat == 12) {
        float a;
        if (vMat == 12) {
            float lf = abs(fract(vUv.y * 16.0) - 0.5) * 2.0;
            float width = abs(vUv.x - 0.5) * 2.0;
            a = max(step(width, (1.0 - vUv.y * 0.75) * (1.0 - lf * 0.78)), step(width, 0.07));
        } else {
            vec2 p = (vUv - 0.5) * 2.0;
            a = step(0.05, 1.0 - (p.x * p.x * 1.15 + pow(abs(p.y), 1.6) * 0.9) + (vnoise(vUv * 14.0) - 0.5) * 0.35);
        }
        if (a < 0.5) discard;
    }
}
