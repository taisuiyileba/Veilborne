// 苍翠梦潮: shared noise for the canopy, the ground patterns and the grove. Plain value noise and cellular noise; nothing
// here depends on time, so the forest is fixed to the land and everyone sees the same leaves in the same places.

// Order-safe smoothstep: GLSL leaves smoothstep undefined when the edges are reversed.
float sstep(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
float dhash(vec2 p) { p = fract(p * vec2(0.1031, 0.1030)); p += dot(p, p.yx + 33.33); return fract((p.x + p.y) * p.x); }
vec2 dhash2(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy);
}
float dhash3(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float dnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(dhash(i), dhash(i + vec2(1.0, 0.0)), f.x), mix(dhash(i + vec2(0.0, 1.0)), dhash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float dnoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(dhash3(i), dhash3(i + vec3(1, 0, 0)), f.x), mix(dhash3(i + vec3(0, 1, 0)), dhash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(dhash3(i + vec3(0, 0, 1)), dhash3(i + vec3(1, 0, 1)), f.x), mix(dhash3(i + vec3(0, 1, 1)), dhash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float dfbm(vec2 p) { return dnoise(p) * 0.5 + dnoise(p * 2.03 + 7.1) * 0.27 + dnoise(p * 4.11 + 13.7) * 0.14 + dnoise(p * 8.3 + 29.3) * 0.09; }
float dfbm3(vec3 p) { return dnoise3(p) * 0.52 + dnoise3(p * 2.04 + 7.0) * 0.28 + dnoise3(p * 4.1 + 13.0) * 0.2; }
/** Cellular noise: x distance to the nearest cell point, y to the second nearest, z a hash of the nearest cell, w its angle. */
vec4 worley(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0, id = 0.0, ang = 0.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 g = vec2(float(x), float(y));
        vec2 o = dhash2(i + g);
        vec2 r = g + o - f;
        float d = dot(r, r);
        if (d < d1) { d2 = d1; d1 = d; id = dhash(i + g + 17.0); ang = atan(-r.y, -r.x); }
        else if (d < d2) d2 = d;
    }
    return vec4(sqrt(d1), sqrt(d2), id, ang);
}
