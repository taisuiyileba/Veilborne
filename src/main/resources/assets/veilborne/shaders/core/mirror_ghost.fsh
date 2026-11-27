#version 150
// Someone's reflection inside a standing mirror: a pale figure behind the glass, clipped to the glass and drawn at
// its depth (see mirror_clip.fsh). Positions are camera-relative and already reflected.
uniform vec4 ColorModulator;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ClipPlane;
uniform vec3 ClipCenter;
uniform vec4 ClipU;
uniform vec4 ClipV;
in vec4 vertexColor;
in vec3 rel;
out vec4 fragColor;
void main() {
    float denom = dot(ClipPlane.xyz, rel);
    if (abs(denom) < 1e-5) discard;
    float t = -ClipPlane.w / denom;
    if (t <= 0.0 || t > 1.0) discard;
    vec3 hit = rel * t;
    vec3 local = hit - ClipCenter;
    if (abs(dot(local, ClipU.xyz)) > ClipU.w || abs(dot(local, ClipV.xyz)) > ClipV.w) discard;
    if (vertexColor.a < 0.004) discard;
    // Drawn a hair behind the glass, further for what is further away in the reflection: in front of whatever is
    // really behind the mirror, and still in the right order among its own surfaces (all at exactly the glass depth
    // they would tie, and rounding would pick a winner per pixel).
    float beyond = max(length(rel) - length(hit), 0.0);
    vec3 at = hit + normalize(rel) * (0.024 * beyond / (beyond + 12.0));
    vec4 clipPos = ProjMat * ModelViewMat * vec4(at, 1.0);
    gl_FragDepth = clamp(clipPos.z / clipPos.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vertexColor * ColorModulator;
}
