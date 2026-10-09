#version 330 core
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec4 aColor;
layout(location = 3) in vec2 aLocal;     // position relative to the shape centre (GUI px)
layout(location = 4) in vec4 aShape;     // half width, half height, radius, border or softness
layout(location = 5) in float aMode;     // 0 textured, 1 rounded fill, 2 rounded ring, 3 soft shadow
uniform mat4 uProj;
out vec2 vUv;
out vec4 vColor;
out vec2 vLocal;
out vec4 vShape;
out float vMode;
void main() {
    vUv = aUv; vColor = aColor; vLocal = aLocal; vShape = aShape; vMode = aMode;
    gl_Position = uProj * vec4(aPos, 0.0, 1.0);
}
