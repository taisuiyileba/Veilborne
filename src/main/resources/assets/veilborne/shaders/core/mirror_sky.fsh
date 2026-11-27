#version 150
// 镜天: the sky as a great, uneven mirror. Drawn over the whole screen right after the vanilla sky; terrain covers it.
//
// The dome is broken into large facets: jittered cells in 3D evaluated on the unit sphere (no poles, no seams), with
// their borders roughened so they read as hammered glass. Each facet is tilted a little, so the reflected land
// (Sampler0, drawn this frame by MirrorWorld) jumps slightly at every border, and each has its own sheen; the seams
// themselves are hairlines that only flash where a slow band of light passes. Beyond the land that was built, a
// painted distance (Sampler1: an upside-down horizon in silver haze) shows through. By day the glass is pale silver;
// by night it is deep lapis, and the lamps of the upside-down town burn gold in it.
//
// Cracks are very slow lightning: a trunk runs along the great circle through two impact points and on across the
// whole dome, and branches grow out from each point along the seams of a finer broken glass that run away from it,
// a gold tip at the front of every branch. Inside the cracked reach the broken pieces sit a little askew, so the
// reflection is split along fractures too fine to glow. At the end the large facets fall out, lowest first, and the
// real sky shows through the gaps; MirrorRenderer mirrors the facet hash to drop falling pieces where they leave.
// The pilgrim's steps send rings across the glass.
//
// MirrorState: x silvering reach (0 zenith only .. 1 down to the horizon), y reflection, z cracks, w shatter.
// MirrorLook: x time in seconds, y daylight, z brightness, w event seed (0..1). Output is premultiplied alpha;
// ColorModulator.a is the mirror's overall presence.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform mat4 ProjView;
uniform vec4 MirrorState;
uniform vec4 MirrorLook;
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
uniform vec4 Glow0;
uniform vec4 Glow1;
uniform vec4 CrackFrom;
uniform vec4 CrackFrom2;
in vec2 ndc;
out vec4 fragColor;

const float CELLS = 3.2;
const float SHARDS = 7.0;

uint hash3(ivec3 c) {
    uvec3 v = uvec3(c + ivec3(1024)) * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}
float h01(ivec3 c, int salt) { return float(hash3(c + salt * ivec3(7919, 104729, 1299709)) >> 8u) / 16777216.0; }
vec3 site(ivec3 c, int seed) { return vec3(c) + 0.15 + 0.7 * vec3(h01(c, seed + 1), h01(c, seed + 2), h01(c, seed + 3)); }
float vhash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise3(vec3 x) {
    vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(vhash(i), vhash(i + vec3(1,0,0)), f.x), mix(vhash(i + vec3(0,1,0)), vhash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(vhash(i + vec3(0,0,1)), vhash(i + vec3(1,0,1)), f.x), mix(vhash(i + vec3(0,1,1)), vhash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
float fbm(vec3 p) { return noise3(p) * 0.55 + noise3(p * 2.03 + 11.0) * 0.3 + noise3(p * 4.1 + 23.0) * 0.15; }
vec3 ripple(vec3 d, vec4 r, inout float ring) {
    if (r.w < 0.0) return d;
    float th = acos(clamp(dot(d, r.xyz), -1.0, 1.0));
    float front = r.w * 0.32, dt = th - front;
    float amp = exp(-r.w * 0.45) * exp(-dt * dt / 0.006);
    ring += amp * (0.5 + 0.5 * sin(dt * 90.0));
    vec3 tangent = d - r.xyz * dot(d, r.xyz);
    return normalize(d + tangent * amp * sin(dt * 70.0) * 0.012);
}
/** Nearest two jittered cells to q: their ids and sites. */
void cells(vec3 q, int seed, out ivec3 c1, out ivec3 c2, out vec3 s1, out vec3 s2) {
    ivec3 b = ivec3(floor(q));
    float f1 = 99.0, f2 = 99.0;
    c1 = b; c2 = b; s1 = vec3(0.0); s2 = vec3(0.0);
    for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        ivec3 c = b + ivec3(x, y, z);
        vec3 s = site(c, seed);
        float d = dot(s - q, s - q);
        if (d < f1) { f2 = f1; c2 = c1; s2 = s1; f1 = d; c1 = c; s1 = s; }
        else if (d < f2) { f2 = d; c2 = c; s2 = s; }
    }
}
vec3 tangentOf(vec3 v, vec3 at) { vec3 t = v - at * dot(v, at); float l = length(t); return l > 1e-5 ? t / l : vec3(0.0); }

void main() {
    vec4 far = InvProjView * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    float horizon = smoothstep(-0.06, 0.05, dir.y);
    if (horizon <= 0.0) discard;
    float zen = acos(clamp(dir.y, -1.0, 1.0)) / 1.5707963;
    float time = MirrorLook.x, day = MirrorLook.y;
    int seed = int(MirrorLook.w * 997.0);

    // The silver creeps down from the zenith with a frosted edge.
    float frost = fbm(dir * 7.0 + MirrorLook.w * 13.0);
    float front = MirrorState.x * 1.14 - zen - (frost - 0.5) * 0.12;
    float silvered = smoothstep(0.0, 0.045, front);
    float frostLine = exp(-front * front / 0.0009) * (0.4 + frost);
    if (silvered + frostLine < 0.002) discard;

    // Facets.
    vec3 q = dir * CELLS + (vec3(noise3(dir * 9.0), noise3(dir * 9.0 + 19.1), noise3(dir * 9.0 + 41.7)) - 0.5) * 0.18;
    ivec3 c1, c2; vec3 s1, s2;
    cells(q, seed, c1, c2, s1, s2);
    float edge = dot(0.5 * (s1 + s2) - q, normalize(s2 - s1)) / CELLS;
    float key = h01(c1, seed + 4), key2 = h01(c2, seed + 4);

    // At the end the facets fall out, lowest first; the real sky shows through.
    float gone = step(0.62 * key + 0.38 * (1.0 - normalize(s1).y), MirrorState.w * 1.08);
    float goneNext = step(0.62 * key2 + 0.38 * (1.0 - normalize(s2).y), MirrorState.w * 1.08);

    // The cracked reach, measured from the nearer impact point.
    float crack = MirrorState.z;
    float toA = acos(clamp(dot(dir, CrackFrom.xyz), -1.0, 1.0)), toB = acos(clamp(dot(dir, CrackFrom2.xyz), -1.0, 1.0));
    float dist = min(toA, toB) / 3.14159;
    vec3 origin = toA < toB ? CrackFrom.xyz : CrackFrom2.xyz;
    float reach = crack * 0.95 - dist;

    // The finer broken glass the branches follow.
    vec3 w = dir * SHARDS + (vec3(noise3(dir * 5.0 + 3.1), noise3(dir * 5.0 + 17.3), noise3(dir * 5.0 + 29.9)) - 0.5) * 0.5;
    ivec3 k1, k2; vec3 t1, t2;
    cells(w, seed + 40, k1, k2, t1, t2);
    vec3 nrm = normalize(t2 - t1);
    float jag = (noise3(dir * 95.0) - 0.5) * 0.0022 + (noise3(dir * 31.0) - 0.5) * 0.004;
    float ce = abs(dot(0.5 * (t1 + t2) - w, nrm) / SHARDS + jag);
    ivec3 klo = (k1.x * 131 + k1.y * 17 + k1.z < k2.x * 131 + k2.y * 17 + k2.z) ? k1 : k2;
    ivec3 khi = (klo == k1) ? k2 : k1;
    float pairKey = h01(klo * 3 + khi * 7, seed + 41);
    float away = sqrt(max(0.0, 1.0 - pow(dot(tangentOf(nrm, dir), tangentOf(origin, dir)), 2.0)));
    float chosen = max(max(smoothstep(0.62, 0.86, away) * step(0.18, pairKey), step(pairKey, 0.09)), 1.0 - smoothstep(0.015, 0.06, dist));
    float broken = smoothstep(0.0, 0.03, reach) * smoothstep(0.3, 0.85, crack);

    // Each facet is tilted a little, each broken piece a little more, and the whole glass is faintly hammered.
    vec3 tilt = vec3(h01(c1, seed + 5), h01(c1, seed + 6), h01(c1, seed + 7)) - 0.5;
    vec3 d2 = normalize(dir + tilt * 0.036 * (0.35 + 0.65 * MirrorState.y));
    d2 = normalize(d2 + (vec3(h01(k1, seed + 42), h01(k1, seed + 43), h01(k1, seed + 44)) - 0.5) * 0.009 * broken);
    d2 = normalize(d2 + (vec3(noise3(dir * 38.0), noise3(dir * 38.0 + 5.0), noise3(dir * 38.0 + 9.0)) - 0.5) * 0.004);
    float ring = 0.0;
    d2 = ripple(d2, Ripple0, ring);
    d2 = ripple(d2, Ripple1, ring);
    d2 = ripple(d2, Ripple2, ring);
    d2 = ripple(d2, Ripple3, ring);

    // The reflected land, split slightly by colour near the facet borders.
    vec4 clip = ProjView * vec4(d2, 0.0);
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    vec4 land = vec4(0.0);
    if (clip.w > 0.0 && uv.x > 0.0 && uv.x < 1.0 && uv.y > 0.0 && uv.y < 1.0) {
        float split = (1.0 - smoothstep(0.0, 0.02, edge)) * 0.0022 + broken * (1.0 - smoothstep(0.0, 0.006, ce)) * 0.0016;
        land = texture(Sampler0, uv);
        land.r = texture(Sampler0, uv + vec2(split, 0.0)).r;
        land.b = texture(Sampler0, uv - vec2(split, 0.0)).b;
    }
    vec2 puv = vec2(atan(d2.z, d2.x) / 6.2831853 + 0.5, clamp(acos(clamp(d2.y, 0.0, 1.0)) / 1.5707963, 0.001, 0.999));
    vec3 paint = texture(Sampler1, puv).rgb * mix(vec3(0.24, 0.29, 0.42), vec3(1.0), day);
    vec3 world = mix(paint, land.rgb, land.a);

    // The glass itself: pale silver by day, lapis by night, more mirror than window toward the horizon.
    float fres = pow(zen, 3.0);
    vec3 silver = mix(vec3(0.17, 0.22, 0.34), vec3(0.74, 0.8, 0.86), day);
    silver *= (0.92 + 0.36 * fres) * (0.95 + 0.1 * key);
    vec3 col = mix(silver, world, MirrorState.y * mix(0.92, 0.7, fres));
    col *= mix(vec3(0.97, 1.0, 1.035), vec3(1.035, 1.0, 0.965), h01(c1, seed + 8));
    vec3 band = normalize(vec3(cos(time * 0.021), 0.35, sin(time * 0.021)));
    float sweep = exp(-pow(dot(dir, band) / 0.09, 2.0));
    col += vec3(0.9, 0.95, 1.0) * sweep * 0.14 * mix(0.45, 1.0, day);
    col += vec3(0.8, 0.9, 1.0) * ring * 0.35 * mix(0.6, 1.0, day);

    // Seams: a hairline, and a bevel that only catches the light where the slow band passes over it.
    float line = 1.0 - smoothstep(0.0, 0.0011 + 0.0009 * zen, edge);
    col *= 1.0 - 0.16 * line;
    float glint = pow(0.5 + 0.5 * sin(key * 6.28 + time * 0.12 + dot(dir, band) * 5.0), 8.0);
    col += vec3(0.85, 0.92, 1.0) * exp(-edge / 0.0028) * (0.03 + 0.2 * glint + 0.25 * sweep) * mix(0.6, 1.0, day);

    // Cracks. Branches: chosen seams of the broken glass inside the reach, burning bright when fresh, then cooling.
    float fr = reach - pairKey * 0.05;
    float open = smoothstep(0.0, 0.015, fr) * chosen;
    float fresh = 1.0 - clamp(fr / 0.22, 0.0, 1.0);
    float width = (0.0006 + 0.0012 * exp(-dist * 5.0)) * (0.7 + 0.6 * h01(klo + khi, seed + 45));
    float core = (1.0 - smoothstep(width * 0.35, width, ce)) * open;
    float halo = exp(-ce / (0.0025 + 0.008 * fresh)) * open;
    float tip = exp(-fr * fr / 0.00005) * smoothstep(0.8, 0.95, away) * step(0.18, pairKey) * exp(-ce / 0.0016);
    // Fractures too fine to glow: a dark hairline between the askew pieces.
    float hair = (1.0 - smoothstep(0.0, 0.0009, ce)) * broken * (1.0 - chosen);
    // The trunk: the great circle through both points, wandering, running a little ahead of the branches.
    vec3 axis = normalize(cross(CrackFrom.xyz, CrackFrom2.xyz));
    float wob = (fbm(dir * 3.6 + 3.0) - 0.5) * 0.1 + (noise3(dir * 24.0) - 0.5) * 0.012 + (noise3(dir * 70.0) - 0.5) * 0.003;
    float td = abs(dot(dir, axis) + wob);
    float tr = crack * 0.95 + 0.05 - dist;
    float trunkOpen = smoothstep(0.0, 0.02, tr);
    float trunkFresh = 1.0 - clamp(tr / 0.3, 0.0, 1.0);
    float trunk = (1.0 - smoothstep(0.0009, 0.0022, td)) * trunkOpen;
    float trunkHalo = exp(-td / (0.004 + 0.01 * trunkFresh)) * trunkOpen;
    float trunkTip = exp(-tr * tr / 0.00012) * exp(-td / 0.003);
    float flicker = 0.75 + 0.25 * sin(time * 2.6 + pairKey * 40.0);
    col *= 1.0 - 0.3 * hair;
    col = mix(col, vec3(0.94, 0.99, 1.0), clamp(max(core, trunk), 0.0, 1.0) * 0.92);
    col += vec3(0.32, 0.8, 0.95) * (halo * (0.25 + 0.5 * fresh) + trunkHalo * (0.35 + 0.5 * trunkFresh)) * 0.55 * flicker;
    col += vec3(1.0, 0.8, 0.46) * (tip * 1.2 + trunkTip * 1.5);
    // Just before it falls, every facet's outline gives.
    float giving = smoothstep(0.86, 1.0, crack) * (1.0 - smoothstep(0.0, 0.3, MirrorState.w));
    col += vec3(0.5, 0.85, 1.0) * exp(-edge / 0.0018) * giving * (0.35 + 0.25 * sin(time * 3.0 + key * 20.0));

    // Glows in the reflection: the extra door, and the pilgrim's lantern or mirror.
    float g0 = max(dot(d2, Glow0.xyz), 0.0), g1 = max(dot(d2, Glow1.xyz), 0.0);
    col += (vec3(1.0, 0.85, 0.52) * pow(g0, 2400.0) * 1.3 + vec3(0.45, 0.9, 1.0) * pow(g0, 260.0) * 0.28) * Glow0.w;
    col += (vec3(1.0, 0.9, 0.7) * pow(g1, 1800.0) * 1.6 + vec3(1.0, 0.8, 0.5) * pow(g1, 120.0) * 0.26) * Glow1.w;

    // Frost glitter along the silvering edge.
    col += vec3(0.85, 0.93, 1.0) * frostLine * 0.55 * (1.0 - MirrorState.y * 0.6) * mix(0.6, 1.0, day);

    col *= MirrorLook.z;
    float gapRim = abs(gone - goneNext) * exp(-edge / 0.003);
    col += vec3(0.9, 0.97, 1.0) * gapRim * 0.9;
    float alpha = max(silvered * (1.0 - gone), gapRim * 0.9) * horizon;
    alpha = max(alpha, frostLine * 0.6 * horizon);
    float presence = ColorModulator.a;
    fragColor = vec4(col * alpha * presence, alpha * presence);
}
