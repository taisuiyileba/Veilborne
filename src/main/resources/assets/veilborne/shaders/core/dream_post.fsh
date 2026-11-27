#version 150
// 苍翠梦潮: light on the land under the forest. Drawn once the solid world and its creatures are in (before water, particles
// and clouds): the frame (Sampler0) and its depth (Sampler1) are read back, each pixel is put back into the world, and two
// things are laid on it.
//
// Leaf-light: sunflecks and leaf-shade moving slowly over whatever is out under the canopy (the ground, the outside walls,
// the creatures on it), as if it were a real forest's floor. The pattern (Sampler4, a tiling leaf shadow) is cast down along
// the light from the canopy's height, so it slants with the sun and lies across walls as well as the ground. Whether a spot
// is out in the open is read from a small map of the land's height round the camera (Sampler3): a pixel is lit only if the
// air just off its surface is at or above the land's top there, so rooms, overhangs and caves stay as they are. At night
// the flecks are the canopy's faint jade glow instead of the sun.
//
// Patterns: rings of flowers that open on the land where pollen settles, where the deer's hooves come down, round the
// dream tree and where the roots grip the ground; they follow the slope, since they are drawn on the land itself. A list
// of points (Sampler2: 64 x 2 texels; row 0 camera-relative centre and radius, row 1 kind, strength, age in seconds (for the
// vine ring, the angle of its gap), seed). Kinds: 0 pollen, 1 hoof ring, 2 vine ring, 3 a seed landing, 4 the dream tree,
// 5 a root's grip.
//
// Look: x leaf-light, y time, z daylight, w point count. Sun: xyz toward the light, w the canopy's height above the camera.
// Height: xy the map's corner (x, z) relative to the camera, z its size in blocks, w the camera's y. Tone: x gold, y the
// patterns' brightness, z night glow, w unused.
#moj_import <veilborne:dream_noise.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform vec4 Look;
uniform vec4 Sun;
uniform vec4 Height;
uniform vec4 Tone;
in vec2 texCoord;
out vec4 fragColor;

/** Distance-safe thin line: 1 on it, fading over a width that never falls under a pixel. */
float line(float d, float width, float px) { float w = max(width, px * 1.2); return exp(-d * d / (w * w)); }

/** One flower pattern round a centre, seen at polar (rho 0..1 of the radius, theta), opened as far as front (0..1). */
float flower(float rho, float th, float front, float seed, float r, float px) {
    float v = 0.0;
    // Rings of petals, each lit as the opening front passes it: five, then eight, then thirteen.
    for (int k = 0; k < 3; k++) {
        float n = k == 0 ? 5.0 : k == 1 ? 8.0 : 13.0;
        float r0 = 0.12 + 0.28 * float(k), h = 0.26;
        float t = (rho - r0) / h;
        if (t < 0.0 || t > 1.0) continue;
        float open = sstep(r0 - 0.05, r0 + h, front * 1.15);
        float phi = fract(th / 6.2831853 * n + seed * float(k + 1) + 0.25 * float(k)) - 0.5;
        float width = 0.42 * pow(sin(3.14159 * t), 0.75);
        float arc = 6.2831853 * rho * r / n;
        float d = (abs(phi) - width) * arc;
        v += (line(d, 0.07, px) * 0.9 + (1.0 - sstep(-0.06, 0.02, d)) * 0.18) * open;
        // The petal's midrib.
        v += line(phi * arc, 0.04, px) * 0.35 * open * sin(3.14159 * t);
    }
    // A seed-head at the heart, and a thin ring where the outermost petals end.
    v += (1.0 - sstep(0.05, 0.1, rho)) * 0.8 * sstep(0.0, 0.1, front);
    v += line((rho - 0.93) * r, 0.05, px) * 0.5 * sstep(0.8, 1.0, front);
    return v;
}
/** The hoof ring: a wreath of small flowers round the print, fern curls inside it, a soft glow in the middle. */
float wreath(float rho, float th, float front, float seed, float r, float px) {
    float v = (1.0 - sstep(0.0, 0.55, rho)) * 0.14;
    // Twelve small flowers on the wreath.
    float n = 12.0;
    float cell = fract(th / 6.2831853 * n + seed) - 0.5;
    vec2 local = vec2(cell * 6.2831853 * rho * r / n, (rho - 0.8) * r);
    float lr = length(local), la = atan(local.y, local.x);
    float petals = 0.55 + 0.45 * cos(la * 5.0 + seed * 9.0);
    float fr = 0.65 * r / 8.0;
    v += (line(lr - fr * petals, 0.06, px) + (1.0 - sstep(0.0, fr * 0.35, lr)) * 0.8) * sstep(0.7, 0.85, front);
    // Fern curls: spirals uncurling from the centre.
    for (int k = 0; k < 5; k++) {
        float a0 = float(k) * 1.2566 + seed * 6.0;
        float spiral = fract((th - a0) / 6.2831853 + rho * 0.9) - 0.5;
        v += line(spiral * 6.2831853 * rho * r * 0.35, 0.05, px) * sstep(0.12, 0.3, rho) * (1.0 - sstep(0.55, 0.72, rho)) * sstep(rho, rho + 0.2, front * 0.8) * 0.6;
    }
    v += line((rho - 0.96) * r, 0.06, px) * 0.4 * sstep(0.85, 1.0, front);
    return v;
}
/** Where a root grips the land: root lines spreading out from it, moss glow between them. */
float grip(float rho, float th, float seed, float r, float px) {
    float v = (1.0 - sstep(0.0, 0.9, rho)) * 0.12;
    for (int k = 0; k < 7; k++) {
        float a = float(k) * 0.8976 + seed * 6.0 + sin(rho * 5.0 + float(k)) * 0.25;
        float d = abs(mod(th - a + 3.14159, 6.2831853) - 3.14159) * rho * r;
        v += line(d, 0.08 * (1.0 - rho) + 0.02, px) * (1.0 - sstep(0.4 + 0.5 * dhash(vec2(float(k), seed)), 1.0, rho)) * 0.5;
    }
    return v;
}
/** The vine ring on the ground round the enclosure: two strands twined, leaves along them, a gap to walk out by. */
float vines(float rho, float th, float seed, float r, float px, float exitA) {
    float gap = sstep(0.18, 0.3, abs(mod(th - exitA + 3.14159, 6.2831853) - 3.14159));
    float v = 0.0;
    for (int k = 0; k < 2; k++) {
        float off = sin(th * 9.0 + float(k) * 3.14159 + seed * 4.0) * 0.035;
        v += line((rho - 0.96 - off) * r, 0.06, px);
    }
    float leaf = fract(th / 6.2831853 * 36.0 + seed) - 0.5;
    v += line(length(vec2(leaf * 6.2831853 * r / 36.0, (rho - 0.96) * r * 1.8)) - 0.22, 0.05, px) * 0.6;
    return v * gap;
}

void main() {
    vec4 src = texture(Sampler0, texCoord);
    vec3 c = src.rgb;
    float depth = texture(Sampler1, texCoord).r;
    if (depth >= 0.99999) { fragColor = src; return; }
    vec4 wp = InvProjView * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 P = wp.xyz / wp.w;
    float dist = length(P);
    vec3 N = normalize(cross(dFdx(P), dFdy(P)));
    if (dot(N, -P) < 0.0) N = -N;
    float px = max(length(dFdx(P)), length(dFdy(P)));

    // ---------------------------------------------------------------- is this spot out under the canopy?
    vec3 off = P + N * 0.55;
    vec2 tex = (off.xz - Height.xy) / Height.z;
    float exposed = 0.0;
    if (tex.x > 0.0 && tex.y > 0.0 && tex.x < 1.0 && tex.y < 1.0) {
        float top = textureLod(Sampler3, tex, 0.0).r - Height.w;
        exposed = sstep(top - 0.7, top + 0.05, off.y);
    }
    float reach = 1.0 - sstep(Height.z * 0.36, Height.z * 0.49, max(abs(P.x), abs(P.z)));
    exposed *= reach;

    // ---------------------------------------------------------------- leaf-light
    float day = Look.z, time = Look.y;
    if (Look.x > 0.001 && exposed > 0.001) {
        vec3 L = normalize(vec3(Sun.x, max(Sun.y, 0.35), Sun.z));
        vec3 A = P + vec3(0.0, Height.w, 0.0);
        vec3 Q = P + L * ((Sun.w - P.y) / L.y);
        vec2 sway = vec2(sin(time * 0.21) * 0.8 + sin(time * 0.53) * 0.3, cos(time * 0.17) * 0.7);
        float broad = textureLod(Sampler4, (Q.xz + sway * 3.0) / 46.0, 0.0).r;
        float fine = textureLod(Sampler4, (Q.xz + sway * 1.3) / 11.0 + vec2(0.37, 0.61), 0.0).r;
        float shimmer = textureLod(Sampler4, (Q.xz - sway * 0.8) / 7.0 + vec2(0.11, 0.83), 0.0).r;
        float fleck = sstep(0.55, 0.85, fine * 0.7 + shimmer * 0.4) * sstep(0.35, 0.65, broad);
        float shade = sstep(0.6, 0.25, broad);
        float facing = 0.55 + 0.45 * max(dot(N, L), 0.0);
        float s = Look.x * exposed * (1.0 - sstep(40.0, 62.0, dist));
        vec3 fleckCol = mix(vec3(0.3, 0.75, 0.55) * 0.35, vec3(1.0, 0.88, 0.55) * mix(1.0, 1.15, Tone.x), day);
        c *= 1.0 - s * mix(0.12, 0.26, day) * shade;
        c += c * fleckCol * s * fleck * facing * mix(0.5, 0.75, day);
        c += fleckCol * s * fleck * facing * mix(0.02, 0.03, day);
        // Deep leaf-shade takes a little of the canopy's green.
        c = mix(c, c * vec3(0.84, 1.03, 0.9), s * shade * 0.5);
    }

    // ---------------------------------------------------------------- flower patterns on the land
    int count = int(Look.w + 0.5);
    vec3 glow = vec3(0.0);
    for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 a = texelFetch(Sampler2, ivec2(i, 0), 0);
        vec4 k = texelFetch(Sampler2, ivec2(i, 1), 0);
        vec3 d = P - a.xyz;
        float r = a.w;
        float dh = length(d.xz);
        if (dh > r * 1.05 || abs(d.y) > 3.5 + dh * 0.35) continue;
        float rho = dh / r, th = atan(d.z, d.x);
        int kind = int(k.x + 0.5);
        float age = k.z, seed = k.w;
        float v = 0.0;
        vec3 colIn = vec3(0.45, 1.0, 0.62), colOut = vec3(1.0, 0.8, 0.36);
        if (kind == 0) v = flower(rho, th, sstep(0.0, 2.2, age), seed, r, px);
        else if (kind == 1) v = wreath(rho, th, sstep(0.0, 3.0, age), seed, r, px);
        else if (kind == 2) { v = vines(rho, th, seed, r, px, age); colIn = vec3(0.4, 0.95, 0.5); colOut = vec3(0.55, 1.0, 0.55); }
        else if (kind == 3) { v = line((rho - sstep(0.0, 1.4, age)) * r, 0.18, px) * (1.0 - sstep(0.6, 1.4, age)) + flower(rho, th, sstep(0.0, 1.0, age), seed, r, px) * 0.8; }
        else if (kind == 4) { v = flower(rho, th + time * 0.05, 1.0, seed, r, px) * (0.7 + 0.3 * sin(time * 0.9 + rho * 6.0)); colOut = vec3(1.0, 0.86, 0.5); }
        else v = grip(rho, th, seed, r, px);
        float fade = 1.0 - sstep(0.88, 1.05, rho);
        glow = max(glow, mix(colIn, colOut, clamp(rho * 1.1, 0.0, 1.0)) * v * k.y * fade);
    }
    // Drawn on the land: only on ground that faces up (never on walls or the undersides of overhangs), under a roof they stay
    // out; brighter in the dark, but still read by day.
    glow *= mix(0.25, 1.0, sstep(0.0, 0.6, exposed + (1.0 - reach))) * sstep(0.55, 0.85, N.y) * Tone.y;
    c += glow * mix(1.0, 0.55, day) + c * glow * 0.6 * day;
    fragColor = vec4(c, src.a);
}
