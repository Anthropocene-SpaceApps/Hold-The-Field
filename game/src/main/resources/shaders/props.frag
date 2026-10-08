#version 330 core
#include "common.glsl"
#include "sky.glsl"
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in vec2 vUv;
flat in int vMat;
uniform float uBundH;
uniform float uWet;       // 0..1 surface wetness (rain)
out vec4 frag;

mat3 cotangentFrame(vec3 N, vec3 p, vec2 uv) {
    vec3 dp1 = dFdx(p), dp2 = dFdy(p);
    vec2 duv1 = dFdx(uv), duv2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, N), dp1perp = cross(N, dp1);
    vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
    float invmax = inversesqrt(max(max(dot(T, T), dot(B, B)), 1e-12));
    return mat3(T * invmax, B * invmax, N);
}

float leafAlpha(vec2 uv, int mat) {
    if (mat == 12) {   // pinnate palm frond: leaflets along a central rib
        float along = uv.y;
        float taper = 1.0 - along * 0.75;
        float lf = abs(fract(along * 16.0) - 0.5) * 2.0;
        float width = abs(uv.x - 0.5) * 2.0;
        float leaflet = step(width, taper * (1.0 - lf * 0.78));
        float rib = step(width, 0.07);
        return max(leaflet, rib);
    }
    vec2 p = (uv - 0.5) * vec2(2.0, 2.0);
    float shape = 1.0 - (p.x * p.x * 1.15 + pow(abs(p.y), 1.6) * 0.9);
    float edge = shape + (vnoise(uv * 14.0) - 0.5) * 0.35;
    return step(0.05, edge);
}

void main() {
    vec3 P = vWorld;
    vec3 N = normalize(vNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(uCamPos - P);
    vec3 albedo = vColor;
    float spec = 0.08, shine = 24.0, trans = 0.0, rough = 0.8;
    vec2 uv = vUv;
    vec2 slope = vec2(0.0);

    if (vMat == 1) {                       // sawn timber / bamboo-brown wood
        float g = vnoise(vec2(uv.x * 2.5, uv.y * 40.0)), g2 = vnoise(vec2(uv.x * 9.0, uv.y * 120.0));
        albedo *= 0.6 + 0.55 * g + 0.12 * g2;
        float seam = smoothstep(0.03, 0.0, abs(fract(uv.y * 3.5) - 0.5) - 0.465);
        albedo *= 1.0 - 0.45 * seam;
        slope = vec2(0.0, (g2 - 0.5) * 0.25);
    } else if (vMat == 2) {                // corrugated tin roof
        float ph = uv.x * 32.0;
        slope = vec2(cos(ph) * 0.55, 0.0);
        float rustMask = smoothstep(0.50, 0.78, fbm(uv * vec2(1.5, 3.0) + 7.0));
        float streak = smoothstep(0.45, 0.8, vnoise(vec2(uv.x * 4.0, uv.y * 0.6)));
        vec3 zinc = vec3(0.50, 0.52, 0.53) * (0.85 + 0.2 * vnoise(uv * 30.0));
        vec3 rust = mix(vec3(0.34, 0.15, 0.07), vec3(0.50, 0.24, 0.10), vnoise(uv * 12.0));
        albedo = mix(zinc, rust, clamp(rustMask * 0.8 + streak * 0.3, 0.0, 0.92));
        spec = mix(0.9, 0.12, rustMask); shine = 60.0; rough = 0.35;
    } else if (vMat == 3) {                // woven jute / bamboo mat
        vec2 g = uv * 7.0;
        float cell = mod(floor(g.x) + floor(g.y), 2.0);
        vec2 f = abs(fract(g) - 0.5);
        float strip = smoothstep(0.5, 0.35, max(f.x, f.y));
        albedo *= (0.55 + 0.4 * cell) * (0.6 + 0.4 * strip) * (0.8 + 0.4 * vnoise(g * 3.0));
        slope = vec2((cell - 0.5) * 0.25, 0.0) * strip;
    } else if (vMat == 4) {                // thatch / straw
        float n = vnoise(vec2(uv.x * 70.0, uv.y * 5.0)), n2 = vnoise(vec2(uv.x * 20.0, uv.y * 40.0));
        albedo *= 0.45 + 0.75 * n * (0.7 + 0.5 * n2);
        slope = vec2((n - 0.5) * 0.6, 0.0);
        rough = 0.95;
    } else if (vMat == 5 || vMat == 12) {  // leaves and fronds
        if (leafAlpha(uv, vMat) < 0.5) discard;
        float vein = smoothstep(0.03, 0.0, abs(uv.x - 0.5) - 0.012);
        albedo *= 0.62 + 0.55 * vnoise(uv * 11.0 + P.xz);
        albedo = mix(albedo, albedo * 1.35 + 0.03, vein * 0.5);
        trans = 0.55; rough = 0.55; spec = 0.12;
        N = normalize(mix(N, vec3(0.0, 1.0, 0.0), 0.35));
    } else if (vMat == 6) {                // mud plaster / packed earth
        float n = fbm(P.xz * 4.0 + P.y * 3.0);
        albedo *= 0.65 + 0.6 * n;
        slope = vec2(vnoise(uv * 20.0) - 0.5, vnoise(uv * 20.0 + 9.0) - 0.5) * 0.5;
        rough = 0.95;
    } else if (vMat == 7) {                // skin
        albedo *= 0.9 + 0.2 * vnoise(uv * 30.0);
        spec = 0.18; shine = 20.0; trans = 0.25;
    } else if (vMat == 8) {                // cloth
        float w = sin(uv.x * 220.0) * sin(uv.y * 220.0);
        albedo *= 0.88 + 0.12 * w + 0.1 * vnoise(uv * 25.0);
        rough = 0.95;
    } else if (vMat == 9) {                // checked lungi
        vec2 g = uv * 7.0;
        float c = step(0.5, fract(g.x)) + step(0.5, fract(g.y));
        albedo = mix(vColor, vec3(0.92, 0.92, 0.9), step(1.5, c) * 0.9) * (0.7 + 0.3 * mod(c, 2.0) + 0.1) ;
        albedo *= 0.88 + 0.12 * sin(uv.x * 260.0) * sin(uv.y * 260.0);
        rough = 0.95;
    } else if (vMat == 10) {               // painted flood gauge
        float y = P.y;
        float band = mod(floor(y / 0.1), 2.0);
        albedo = mix(vec3(0.88, 0.87, 0.82), vec3(0.78, 0.12, 0.08), band);
        albedo *= 0.8 + 0.2 * vnoise(vec2(uv.x * 30.0, y * 30.0));
        float major = smoothstep(0.014, 0.0, abs(fract(y / 0.5 + 0.5) - 0.5) * 0.5);
        albedo = mix(albedo, vec3(0.02), major * 0.9);
        float mark = smoothstep(0.02, 0.0, abs(y - uBundH));
        albedo = mix(albedo, vec3(1.0, 0.85, 0.0), mark);
        spec = 0.25;
    } else if (vMat == 11) {               // bark
        float f = vnoise(vec2(uv.x * 22.0, uv.y * 3.0)), f2 = vnoise(vec2(uv.x * 60.0, uv.y * 10.0));
        albedo *= 0.5 + 0.7 * f + 0.2 * f2;
        slope = vec2((f - 0.5) * 1.1, 0.0);
        rough = 0.95;
    } else if (vMat == 13) {               // rope / coir
        albedo *= 0.7 + 0.5 * vnoise(vec2(uv.y * 80.0, uv.x * 4.0));
    } else if (vMat == 14) {               // painted metal
        albedo *= 0.9 + 0.1 * vnoise(uv * 10.0);
        spec = 0.8; shine = 70.0; rough = 0.4;
    } else if (vMat == 15) {               // boat hull: tarred planks
        float seam = smoothstep(0.02, 0.0, abs(fract(uv.y * 8.0) - 0.5) - 0.47);
        float g = vnoise(vec2(uv.x * 6.0, uv.y * 60.0));
        albedo *= (0.55 + 0.5 * g) * (1.0 - 0.5 * seam);
        spec = 0.16; shine = 30.0;
    }

    if (dot(slope, slope) > 1e-6) {
        mat3 tbn = cotangentFrame(N, P, uv);
        N = normalize(tbn * vec3(-slope.x, -slope.y, 1.0));
    }

    // rain darkens and glosses
    albedo *= 1.0 - 0.35 * uWet;
    spec = mix(spec, max(spec, 0.35), uWet);

    float ndl = max(dot(N, uSunDir), 0.0);
    float sh = shadowFactor(P, normalize(vNormal));
    float wrap = clamp((dot(N, uSunDir) + 0.3) / 1.3, 0.0, 1.0);
    vec3 H = normalize(uSunDir + V);
    float sp = pow(max(dot(N, H), 0.0), shine) * spec;
    vec3 diffuse = albedo * (uSunColor * mix(ndl, wrap, 0.3) * sh + hemiAmbient(N));
    // backlit leaves glow
    diffuse += albedo * uSunColor * trans * max(dot(-V, uSunDir), 0.0) * 0.5 * sh;
    vec3 col = diffuse + uSunColor * sp * sh;
    if (vMat == 2 || vMat == 14) {          // metal picks up the sky
        vec3 R = reflect(-V, N);
        col += skyBase(normalize(vec3(R.x, abs(R.y), R.z))) * spec * 0.35 * (1.0 - rough * 0.6);
    }
    frag = vec4(applyFog(col, P), 1.0);
}
