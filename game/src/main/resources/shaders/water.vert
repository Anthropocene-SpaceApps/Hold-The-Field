#version 330 core
layout(location = 0) in vec3 aPos;
uniform mat4 uVP;
uniform float uMeshY;        // < -900: use the vertex's own y (river ribbon)
uniform float uYAdd;
out vec3 vWorld;
void main() {
    vec3 p = aPos;
    if (uMeshY > -900.0) p.y = uMeshY; else p.y += uYAdd;
    vWorld = p;
    gl_Position = uVP * vec4(p, 1.0);
}
