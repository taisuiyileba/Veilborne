#version 150
// 苍翠梦潮: the deer. Positions are camera-relative, rebuilt every frame; the halo pass swells the glass along its normals.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 DeerHalo;
out vec2 deerUV;
out vec4 deerTint;
out vec3 deerNormal;
out vec3 deerRel;
void main() {
    float kind = floor(Color.a * 8.0);
    vec3 pos = Position + Normal * DeerHalo.x * (kind == 0.0 || kind == 1.0 ? 1.0 : 0.0);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    deerUV = UV0;
    deerTint = Color;
    deerNormal = Normal;
    deerRel = pos;
}
