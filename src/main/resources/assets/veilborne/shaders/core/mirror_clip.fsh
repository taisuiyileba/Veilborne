#version 150
// A standing mirror (the three frames, the gallery wall). Geometry is the reflected land behind the glass; only
// what is seen through the glass rectangle is drawn, and it is drawn at the depth of the glass itself, so anything
// in front of the mirror still covers the reflection while nothing behind the mirror can (a hair behind it, in fact:
// see below).
// shadeReflection() is the same as in mirror_world.fsh.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 MirrorFog;
uniform vec4 MirrorLook;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float MirrorScale;
uniform vec4 ClipPlane;
uniform vec3 ClipCenter;
uniform vec4 ClipU;
uniform vec4 ClipV;
in vec2 texCoord;
in vec4 vertexColor;
in vec3 lightData;
in vec3 rel;
in vec3 worldPos;
out vec4 fragColor;

float mhash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float mnoise(vec3 x) {
    vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(mhash(i), mhash(i + vec3(1,0,0)), f.x), mix(mhash(i + vec3(0,1,0)), mhash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(mhash(i + vec3(0,0,1)), mhash(i + vec3(1,0,1)), f.x), mix(mhash(i + vec3(0,1,1)), mhash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
vec4 shadeReflection(vec4 tex, int kind) {
    float t = MirrorLook.x, day = MirrorLook.y;
    vec3 albedo = tex.rgb * vertexColor.rgb;
    if (kind == 5 || kind == 6) {
        // Untextured land and cloth: a little painted grain so distant fields are not flat plates.
        float g = mnoise(worldPos * 0.55) * 0.6 + mnoise(worldPos * 2.1) * 0.4;
        albedo *= 0.88 + 0.24 * g;
    }
    float sky = lightData.y, block = lightData.x;
    // The reflected land is lit as ours is, only cooler: pale silver by day, deep lapis moonlight by night,
    // so the lamps and windows of the upside-down town burn gold in it.
    vec3 ambient = mix(vec3(0.24, 0.3, 0.46), vec3(0.93, 0.97, 1.03), day);
    vec3 col = albedo * mix(0.42, 1.0, sky) * ambient;
    col += albedo * vec3(1.0, 0.72, 0.38) * block * block * mix(1.6, 1.1, day);
    if (kind == 4 || kind == 6 || kind == 8) col = albedo * mix(1.5, 1.3, day) + albedo * albedo * 0.5;
    if (kind == 2 || kind == 3) {
        // Water catches the silver above it.
        float ripple = mnoise(vec3(worldPos.xz * 0.7, t * 0.35));
        col = mix(col, mix(vec3(0.24, 0.31, 0.46), vec3(0.8, 0.88, 0.94), day), 0.22 + 0.2 * ripple);
    }
    if (kind == 7 || kind == 8) col *= 1.0 + 0.1 * sin(t * 1.3 + worldPos.y * 0.7);
    float lum = dot(col, vec3(0.3, 0.59, 0.11));
    col = mix(col, vec3(lum), 0.16);
    col *= mix(vec3(0.93, 0.99, 1.06), vec3(1.12, 0.95, 0.78), MirrorLook.z);
    float dist = length(rel);
    float fog = 1.0 - exp(-dist * MirrorFog.w);
    col = mix(col, MirrorFog.rgb, clamp(fog, 0.0, 0.93));
    // Toward the edge of what was built, fade out so the painted distance takes over without a seam.
    float edge = 1.0 - smoothstep(MirrorLook.w * 0.7, MirrorLook.w, length(rel.xz));
    float alpha = (kind == 2 ? 0.74 : kind == 3 ? 0.64 : 1.0) * edge;
    return vec4(col, alpha);
}

void main() {
    float denom = dot(ClipPlane.xyz, rel);
    if (abs(denom) < 1e-5) discard;
    float t = -ClipPlane.w / denom;
    if (t <= 0.0 || t > 1.0) discard;
    vec3 hit = rel * t;
    vec3 local = hit - ClipCenter;
    if (abs(dot(local, ClipU.xyz)) > ClipU.w || abs(dot(local, ClipV.xyz)) > ClipV.w) discard;
    int kind = int(lightData.z * 127.0 + 0.5);
    vec4 tex = (kind == 5 || kind == 6) ? vec4(1.0) : texture(Sampler0, texCoord);
    if (tex.a < 0.5 && kind != 2 && kind != 3) discard;
    // Drawn a hair behind the glass, further for what is further away in the reflection: in front of whatever is
    // really behind the mirror, and still in the right order among its own surfaces (all at exactly the glass depth
    // they would tie, and rounding would pick a winner per pixel).
    float beyond = max(length(rel) - length(hit), 0.0);
    vec3 at = hit + normalize(rel) * (0.024 * beyond / (beyond + 12.0));
    vec4 clipPos = ProjMat * ModelViewMat * vec4(at * MirrorScale, 1.0);
    gl_FragDepth = clamp(clipPos.z / clipPos.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = shadeReflection(tex, kind) * ColorModulator;
}
