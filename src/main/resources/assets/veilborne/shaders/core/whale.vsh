#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 TideHalo;
out vec2 whaleUV;
out vec4 whaleTint;
out vec3 whaleNormal;
out vec3 whaleRel;
void main() {
    // Positions are camera-relative world coordinates; the halo pass inflates the hull along its normals.
    vec3 pos = Position + Normal * TideHalo.x;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    whaleUV = UV0;
    whaleTint = Color;
    whaleNormal = Normal;
    whaleRel = pos;
}
