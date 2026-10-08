#version 330 core
layout(location = 0) in vec4 aA;        // blade: offset x, offset z, yaw, height scale
layout(location = 1) in vec2 aB;        // t along the blade (0 base .. 1 tip), side (-1 / +1)
layout(location = 2) in vec4 aInst;     // instance: x, z, seed, scale
uniform mat4 uVP;
uniform float uTime;
uniform vec3 uCamPos;
uniform float uHeight;                  // full height of a blade (m)
uniform float uWidth;                   // half width at the base (m)
uniform float uWind;                    // 0 calm .. 1 storm
uniform float uDroop;                   // ripe heads bend over
uniform float uLean;                    // flood lodging
uniform float uFade;                    // distance at which blades vanish
uniform sampler2D uHeightTex;
uniform vec4 uHeightRect;
out vec3 vWorld;
out vec3 vNormal;
out float vT_;
out float vSeed;
void main() {
    float seed = aInst.z;
    vec2 base = aInst.xy + aA.xy;
    float gy = texture(uHeightTex, (base - uHeightRect.xy) * uHeightRect.zw).r;
    float dist = length(vec3(base.x, gy, base.y) - uCamPos);
    float fade = 1.0 - smoothstep(uFade * 0.65, uFade, dist);
    float t = aB.x;
    float h = uHeight * aA.w * (0.82 + 0.36 * fract(seed * 7.31)) * aInst.w * fade;
    float yaw = aA.z + seed * 6.2831;
    vec2 dirv = vec2(cos(yaw), sin(yaw));
    vec2 perp = vec2(-dirv.y, dirv.x);
    float arc = (0.18 + 0.28 * fract(seed * 3.17) + uDroop * 0.55) * t * t * h;
    float width = uWidth * (1.0 - t * 0.88) * (0.8 + 0.4 * fract(seed * 5.71)) * fade;
    vec3 p = vec3(0.0, t * h, 0.0);
    p.xz += dirv * arc + perp * (aB.y * width);
    float gust = sin(uTime * 1.6 + base.x * 0.085 + base.y * 0.07) * 0.5 + 0.5;
    float flutter = sin(uTime * 5.7 + seed * 41.0 + base.x * 1.3);
    float bend = (uWind * (0.30 + 0.70 * gust) + flutter * 0.04 * uWind + 0.03 * gust) * t * t * h * 0.5;
    p.x += bend * 0.93; p.z += bend * 0.37;
    p.xz += vec2(0.35, 0.94) * uLean * t * t * h * 0.85;
    p.y -= uLean * t * t * h * 0.38;
    vec3 world = vec3(base.x, gy, base.y) + p;
    float hh = max(h, 0.02);
    vec3 tangent = normalize(vec3(dirv.x * (2.0 * arc / max(t, 0.05)), hh, dirv.y * (2.0 * arc / max(t, 0.05))));
    vec3 side = vec3(perp.x, 0.0, perp.y);
    vec3 nn = cross(side, tangent);
    vNormal = dot(nn, nn) > 1e-8 ? normalize(nn) : vec3(0.0, 1.0, 0.0);
    vWorld = world;
    vT_ = t;
    vSeed = seed;
    gl_Position = uVP * vec4(world, 1.0);
}
