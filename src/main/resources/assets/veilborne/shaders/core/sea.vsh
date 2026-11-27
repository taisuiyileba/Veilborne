#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 seaUV;
out vec4 seaTint;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    seaUV = UV0;
    seaTint = Color;
}
