#version 150
// 苍翠梦潮: the hanging crowns, their branches and houses, and the aerial roots. Positions are relative to the event's
// ground centre (Origin.xyz takes them to the camera). Roots that hang free sway from their tips; roots that grip the land
// sway only in the middle; crowns breathe. Beyond Origin.w the world is squeezed toward the camera along each view ray
// (monotonically, so what is further stays further), so the forest stays whole at any render distance.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 Origin;
uniform vec4 GroveLook;
out vec2 groveUV;
out vec4 groveInfo;
out vec3 groveNormal;
out vec3 groveRel;
out vec3 groveWorld;
out float groveDist;
void main() {
    float kind = floor(Color.r * 8.0);
    float t = UV0.x;
    vec3 p = Position;
    if (kind == 0.0 || kind == 5.0 || kind == 6.0) {
        float free = Color.a;
        float amp = mix(sin(3.14159 * t) * 1.3, t * t * 5.0, free) * (kind == 5.0 ? 0.0 : 1.0);
        float ph = GroveLook.x * 0.33 + Color.b * 40.0;
        p += vec3(sin(ph + t * 2.1), 0.0, cos(ph * 0.83 + t * 1.7)) * amp;
    } else if (kind == 1.0) {
        p += Normal * sin(GroveLook.x * 0.45 + Color.b * 30.0 + Position.y * 0.05) * 0.45;
    }
    groveWorld = p;
    vec3 cam = p + Origin.xyz;
    float d = length(cam);
    groveDist = d;
    float f0 = Origin.w * 0.62, f1 = Origin.w * 0.96;
    if (d > f0) cam *= (f0 + (f1 - f0) * (1.0 - exp(-(d - f0) / (f1 - f0)))) / d;
    groveRel = cam;
    gl_Position = ProjMat * ModelViewMat * vec4(cam, 1.0);
    groveUV = UV0;
    groveInfo = Color;
    groveNormal = Normal;
}
