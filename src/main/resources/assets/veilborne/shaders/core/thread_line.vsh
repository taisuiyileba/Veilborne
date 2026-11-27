#version 150
// Positions are true camera-relative world coordinates; anything past the far plane is drawn nearer at the same apparent size.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float FarFit;
out vec3 trueRel;
out vec2 lineUV;
out vec4 lineColor;
void main() {
    float k = min(1.0, FarFit / max(length(Position), 1e-3));
    gl_Position = ProjMat * ModelViewMat * vec4(Position * k, 1.0);
    trueRel = Position;
    lineUV = UV0;
    lineColor = Color;
}
