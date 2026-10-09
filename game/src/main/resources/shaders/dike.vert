#version 330 core
layout(location = 0) in vec3 aPos;      // x, terrain base y, z
layout(location = 1) in float aRise;    // rise above the base at the reference height
layout(location = 2) in vec3 aNormal;
uniform mat4 uVP;
uniform float uBundScale;               // current height / reference height
out vec3 vWorld;
out vec3 vNormal;
out float vRise;
void main() {
    vWorld = vec3(aPos.x, aPos.y + aRise * uBundScale, aPos.z);
    vNormal = aNormal;
    vRise = aRise;
    gl_Position = uVP * vec4(vWorld, 1.0);
}
