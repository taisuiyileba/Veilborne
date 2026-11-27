#version 150
// 镜天巡礼: the land drawn again, reflected. Chunk meshes hold real-space positions relative to their chunk;
// ChunkOffset moves them to camera-relative space, MirrorMat reflects them (across the sky's mirror plane, or a
// standing mirror's glass), and MirrorScale draws everything proportionally nearer so it fits the far plane.
// Normal carries light data rather than a normal: x block light, y sky light, z a surface kind (mirror_world.fsh).
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 MirrorMat;
uniform vec3 ChunkOffset;
uniform float MirrorScale;
uniform vec3 MirrorOrigin;
out vec2 texCoord;
out vec4 vertexColor;
out vec3 lightData;
out vec3 rel;
out vec3 worldPos;
void main() {
    vec3 local = ChunkOffset + Position;
    vec3 m = (MirrorMat * vec4(local, 1.0)).xyz;
    rel = m;
    worldPos = local + MirrorOrigin;
    gl_Position = ProjMat * ModelViewMat * vec4(m * MirrorScale, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    lightData = Normal;
}
