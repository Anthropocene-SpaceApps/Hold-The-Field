// Analytic sky: gradient, sun disc and glow, procedural cloud layer. Needs common.glsl.
vec3 skyBase(vec3 d) {
    float h = max(d.y, 0.0);
    vec3 col = mix(uSkyHorizon, uSkyZenith, pow(h, 0.48));
    float sd = max(dot(d, uSunDir), 0.0);
    col += uSunColor * (0.05 * pow(sd, 5.0) + 0.15 * pow(sd, 48.0)) * (1.0 - 0.7 * uStorm);
    col += uSunColor * smoothstep(0.99955, 0.99985, sd) * 12.0 * (1.0 - 0.95 * uStorm);
    if (d.y < 0.0) col = mix(col, uFogColor * 0.9, smoothstep(0.0, -0.12, d.y));
    return col;
}

vec3 skyClouds(vec3 d, vec3 base) {
    if (d.y < 0.012) return base;
    vec2 p = d.xz / (d.y + 0.10) * 0.85 + vec2(uTime * 0.010, uTime * 0.006);
    float n = fbm(p * 1.25) * 0.62 + fbm(p * 3.9 + 9.0) * 0.38;
    float cover = mix(0.50, 0.14, uStorm);
    float dens = smoothstep(cover, cover + 0.26, n);
    float fade = smoothstep(0.012, 0.16, d.y);
    float nl = fbm((p + uSunDir.xz * 0.10) * 1.25);
    float light = clamp(0.55 + (n - nl) * 3.2, 0.0, 1.0);
    vec3 shadowCol = mix(uSkyHorizon * 0.55, vec3(0.07, 0.08, 0.10) + uSkyZenith * 0.25, uStorm);
    vec3 litCol = uSkyHorizon * 0.55 + uSunColor * 0.42 * (1.0 - 0.8 * uStorm) + uSkyZenith * 0.3;
    vec3 cc = mix(shadowCol, litCol, light);
    cc += uFlash * 1.2 * vec3(0.7, 0.75, 1.0) * dens;
    return mix(base, cc, dens * fade * 0.96);
}

vec3 skyColor(vec3 d) { return skyClouds(d, skyBase(d)); }
