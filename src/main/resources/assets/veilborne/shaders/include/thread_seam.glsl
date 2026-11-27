// 绯线缝世: the sky's cloth and its seam, shared by thread_sky, thread_needle and thread_line. ThreadTimeline mirrors the
// smooth parts on the CPU so the needle goes in exactly at the edges; the ragged detail exists only here.
//
// DomeInfo:  xyz dome centre relative to the camera, w radius (blocks). The camera is always inside the dome.
// SeamM:     xyz unit vector from the dome centre toward the seam's middle, w half arc (radians).
// SeamH:     xyz unit vector along the seam at its middle, w widest half-opening (radians).
// SeamState: x how far the tear has run (0..1), y along-arc front behind which it is drawn shut, z event key, w time (s).
// SeamLook:  x half-width of the shut seam, y the pull (0..1), z cooled (0..1), w star band (0..1).
uniform vec4 DomeInfo;
uniform vec4 SeamM;
uniform vec4 SeamH;
uniform vec4 SeamState;
uniform vec4 SeamLook;

// Order-safe smoothstep: GLSL leaves smoothstep undefined when the edges are reversed; the CPU side relies on it.
float sstep(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
vec3 seamN() { return normalize(cross(SeamM.xyz, SeamH.xyz)); }
float seamMeander(float a) { float k = SeamState.z; return 0.021 * sin(3.1 * a + k * 40.0) + 0.011 * sin(7.3 * a + k * 91.0 + 1.3); }
float seamProfile(float a) { float t = min(1.0, abs(a) / SeamM.w); return pow(max(0.0, 1.0 - t * t), 0.7); }
float seamOpened(float a) { return sstep(0.0, 0.18, SeamState.x * 1.18 - abs(a) / SeamM.w); }
float seamClosed(float a) { return sstep(SeamState.y + 0.03, SeamState.y - 0.06, a); }
float seamHalf(float a) { float o = seamOpened(a); return mix(SeamH.w * seamProfile(a) * o, SeamLook.x * o, seamClosed(a)); }
/** Seam coordinates of a unit vector from the dome centre: x along the arc, y across from the meandering seam line. */
vec2 seamCoords(vec3 u) {
    float a = atan(dot(u, SeamH.xyz), dot(u, SeamM.xyz));
    return vec2(a, asin(clamp(dot(u, seamN()), -1.0, 1.0)) - seamMeander(a));
}
/** Where a view ray from the camera meets the cloth (camera-relative). */
vec3 domeHit(vec3 dir) {
    vec3 c = DomeInfo.xyz;
    float b = dot(dir, c), q = dot(c, c) - DomeInfo.w * DomeInfo.w;
    return dir * (b + sqrt(max(0.0, b * b - q)));
}
float seamHash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float seamNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(seamHash(i), seamHash(i + vec2(1.0, 0.0)), f.x), mix(seamHash(i + vec2(0.0, 1.0)), seamHash(i + vec2(1.0, 1.0)), f.x), f.y);
}
/** Half-width with torn edges: each edge has its own rag, fixed on the cloth. */
float seamRagged(vec2 s) {
    float side = s.y > 0.0 ? 3.1 : 7.7;
    float n = seamNoise(vec2(s.x * 55.0, side)) * 0.6 + seamNoise(vec2(s.x * 210.0, side + 11.0)) * 0.4;
    return seamHalf(s.x) * (0.8 + 0.38 * n);
}
/** 0..1 how open the cloth is along a view direction (1 inside the wound). */
float seamGap(vec3 dir) {
    vec2 s = seamCoords(normalize(domeHit(dir) - DomeInfo.xyz));
    if (abs(s.x) > SeamM.w) return 0.0;
    float hw = seamRagged(s);
    return 1.0 - sstep(hw * 0.8, hw, abs(s.y));
}
