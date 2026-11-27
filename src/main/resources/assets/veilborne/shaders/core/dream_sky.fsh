#version 150
// 苍翠梦潮: the canopy of the inverted forest. Drawn over the whole screen right after the vanilla sky; terrain, the hanging
// crowns and the roots cover it.
//
// Each view ray is followed to a dome anchored over the event (apex some 235 blocks up, curving down to the horizon), so
// the forest overhead stays put as you walk and everyone sees the same leaves. The upper layer is the painted canopy seen
// from below (Sampler0, projected as it was painted, from the event's centre): the far, deep forest. Under it, on a second
// dome 24 blocks lower, hang the crowns of the nearest trees, drawn the way a tree looks from beneath: its trunk goes up
// into the cloud at the centre, branches fan out and fork from it, and the leaves hang thickest along the branches toward
// the outer shell, their edges broken into clumps. Between crowns there are gaps (trees keep a little apart), through which
// the deep forest shows. Leaves are lit from behind: thin ones glow green-gold, thick ones stay dark; toward the sun they
// glow through, and flecks of it get through the gaps. Fireflies drift in the underside, a few lamps of little hanging
// houses show deep in it, and at night the thinnest leaves glow a faint bioluminescent jade.
//
// It grows: the crowns come out from the zenith one tree at a time, each spreading from its own trunk in young lime leaves,
// with the deep forest spreading behind them. Before the end each crown turns honey-gold in its own time and lets its
// leaves go, which leaves the bare branches fanning out against the sky, and those fade into ordinary cloud. Where the
// leaves have gone the real sky shows; the sun (or the moon), hidden while the canopy covered it, is drawn there.
//
// SkyLook: x time (s), y daylight, z brightness, w presence. Dome: xyz centre relative to the camera, w radius.
// Eye: xyz the event's centre at eye height, relative to the camera; w seed key. Grow: x spread (0..1), y leaves lost,
// z branches faded, w fireflies. SunDir: xyz toward the sun, w how clear the sky is. Tint: x gold, y the moon's light
// (night), z the growing front's strength, w how far the real sun and moon are hidden (they are drawn here instead).
// Output is premultiplied alpha.
#moj_import <veilborne:dream_noise.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform vec4 SkyLook;
uniform vec4 Dome;
uniform vec4 Eye;
uniform vec4 Grow;
uniform vec4 SunDir;
uniform vec4 Tint;
in vec2 ndc;
out vec4 fragColor;

const float CROWN = 38.0;

vec2 pano(vec3 u) { return vec2(atan(u.z, u.x) / 6.2831853 + 0.5, clamp(1.0 - asin(clamp(u.y, 0.0, 1.0)) / 1.5707963, 0.002, 0.998)); }
vec3 sphereHit(vec3 dir, vec3 c, float r) {
    float b = dot(dir, c), q = dot(c, c) - r * r;
    return dir * (b + sqrt(max(0.0, b * b - q)));
}
/** A honey-gold version of a leaf colour, by its brightness. */
vec3 honey(vec3 c) {
    float l = dot(c, vec3(0.3, 0.55, 0.15));
    float t = clamp(l * 1.8, 0.0, 1.0);
    return mix(vec3(0.36, 0.17, 0.04), vec3(1.0, 0.72, 0.28), t) * (0.75 + 0.4 * t) + vec3(0.3, 0.22, 0.08) * max(l - 0.55, 0.0);
}

struct Crown { float cover; float thick; float branch; float id; float r; float grown; float hue; };
/**
 * The crowns over this point, seen from below: round, ragged at the edge, of different sizes, and overlapping, the higher
 * tree over the lower; here and there no tree at all. Returns the uppermost crown here. cover: leaves here (0..1); thick:
 * how many leaves the light comes through (0 thin..1); branch: a branch here; id: the tree's hash; r: 0 at the trunk to 1
 * at the crown's reach; grown: 0..1 how far it has grown; hue: its own shade of green. px is one pixel in blocks. No
 * derivatives are taken in here.
 */
Crown crown(vec2 w, float px, float grow) {
    Crown k;
    k.cover = 0.0; k.thick = 0.0; k.branch = 0.0; k.id = 0.0; k.r = 1.0; k.grown = 1.0; k.hue = 0.5;
    vec2 p = w / CROWN + Eye.w * 7.0;
    vec2 ip = floor(p), fp = fract(p);
    float pc = px / CROWN;
    float soft = clamp(pc * 7.0, 0.0, 1.0);
    // Ragged leaf clumps at three sizes, shared by all the crowns (so where two meet their edges agree).
    float n1 = dnoise(p * 3.1 + 11.0), n2 = dnoise(p * 8.3 + 7.0), n3 = dnoise(p * 21.0 + 3.0);
    float leafy = n1 * 0.5 + mix(n2 * 0.32 + n3 * 0.18, 0.25, soft);
    float top = -1.0;
    vec2 best = vec2(0.0);
    float bestR = 1.0, bestD = 1.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 g = vec2(float(x), float(y));
        vec2 cell = ip + g;
        float id = dhash(cell + 17.0);
        if (id < 0.1) continue;
        vec2 at = g + dhash2(cell) * 0.9 + 0.05;
        vec2 trunk = (cell + dhash2(cell) * 0.9 + 0.05 - Eye.w * 7.0) * CROWN;
        // Crowns come out from the zenith one by one, each spreading from its own trunk.
        float zc = atan(length(trunk), 200.0) / 1.5707963;
        float due = zc * 1.02 + 0.05 * id;
        float gr = sstep(due - 0.02, due + 0.1, grow * 1.2);
        float big = dhash(cell + 5.0);
        float R = (0.36 + 0.32 * big + 0.3 * pow(dhash(cell + 9.0), 5.0)) * (0.2 + 0.8 * gr);
        float d = length(fp - at);
        float shape = R - d + (leafy - 0.5) * (0.22 + 0.2 * R);
        float cov = sstep(-0.03, 0.03 + pc * 1.5, shape) * step(0.01, gr);
        if (cov > 0.02 && id > top) {
            top = id; k.cover = max(k.cover, cov); k.id = id; k.r = d / max(R, 1e-3); k.grown = gr;
            best = fp - at; bestR = R; bestD = d;
        } else k.cover = max(k.cover, cov);
    }
    if (top < 0.0) return k;
    float r = k.r;
    k.hue = dhash(vec2(k.id * 71.0, 3.0));
    // Thickest over the trunk, thinning to single leaves at the rim; clumps and small holes of light between them.
    float clump = mix(dnoise(p * 6.0 + k.id * 9.0) * 0.6 + dnoise(p * 15.0) * 0.4, 0.5, soft);
    k.thick = clamp(pow(clamp(1.0 - r, 0.0, 1.0), 0.6) * (0.55 + 0.6 * clump) + (n2 - 0.5) * 0.2, 0.0, 1.0);
    // A few holes of light, only where the leaves are thin, toward the rim.
    float hole = sstep(0.74, 0.8, mix(n3, 0.3, soft) * 0.5 + clump * 0.3 + (1.0 - k.thick) * 0.45) * sstep(0.45, 0.8, r);
    k.cover *= 1.0 - hole * 0.8 * (1.0 - soft);
    // Its branches: three to five limbs from the trunk, bending as they go and forking twice; a haze of fine twigs
    // fills out the crown's reach beyond them.
    float th = atan(best.y, best.x);
    int nb = 3 + int(k.id * 2.99);
    float b = 0.0;
    for (int j = 0; j < 5; j++) {
        if (j >= nb) break;
        float fj = float(j);
        float h1 = dhash(vec2(k.id * 97.0, fj)), h2 = dhash(vec2(fj, k.id * 53.0)), h3 = dhash(vec2(fj * 7.0, k.id * 29.0));
        float phi = (fj + 0.8 * h1) * 6.2831853 / float(nb) + (h2 - 0.5) * 2.2 * r;
        float f1 = 0.22 + 0.2 * h2, f2 = f1 + 0.22 + 0.15 * h3;
        float spread1 = 0.3 + 0.25 * h1, spread2 = 0.18 + 0.14 * h3;
        for (int s = 0; s < 4; s++) {
            float first = (s < 2 ? -1.0 : 1.0), second = (s % 2 == 0 ? -1.0 : 1.0);
            float ang = phi + first * spread1 * sstep(f1, f1 + 0.2, r) + second * spread2 * sstep(f2, f2 + 0.2, r);
            float da = abs(mod(th - ang + 3.14159265, 6.2831853) - 3.14159265);
            float dd = da * bestD;
            float ws = 0.034 * bestR * (1.0 - 0.8 * r) * (r < f1 ? 1.0 : r < f2 ? 0.72 : 0.5) + 0.0015;
            b = max(b, (1.0 - sstep(ws, ws + pc * 1.3, dd)) * mix(1.0, 0.35, clamp(pc / ws - 0.6, 0.0, 1.0)) * (1.0 - sstep(0.8, 1.0, r)));
        }
    }
    b = max(b, 1.0 - sstep(0.045 * bestR, 0.045 * bestR + pc * 1.3, bestD));
    float twigs = sstep(0.55, 0.8, mix(n3 * 0.5 + n2 * 0.5, 0.55, soft)) * sstep(0.35, 0.6, r) * (1.0 - sstep(0.85, 1.05, r));
    b = max(b, twigs * 0.32);
    k.branch = b * k.grown;
    return k;
}

void main() {
    vec4 far = InvProjView * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    float horizon = sstep(-0.06, 0.035, dir.y);
    if (horizon <= 0.0 || SkyLook.w <= 0.001) discard;
    float time = SkyLook.x, day = SkyLook.y, key = Eye.w;
    float night = 1.0 - day;

    vec3 hit = sphereHit(dir, Dome.xyz, Dome.w);
    vec3 hitL = sphereHit(dir, Dome.xyz, Dome.w - 24.0);
    float dist = length(hit);
    vec2 w = hit.xz - Eye.xz, wl = hitL.xz - Eye.xz;
    vec3 u = normalize(hit - Eye.xyz);
    // Detail finer than a pixel is let go of rather than left to sparkle.
    float fw = max(fwidth(w.x) + fwidth(w.y), 1e-3);
    float fwl = max(fwidth(wl.x) + fwidth(wl.y), 1e-3);
    float sd = max(dot(dir, SunDir.xyz), 0.0);

    // ---------------------------------------------------------------- how far the deep forest has spread, and its losses
    float zen = acos(clamp(u.y, -1.0, 1.0)) / 1.5707963;
    float edgeN = dfbm(w * 0.004 + key * 13.0) * 0.7 + dnoise(w * 0.02 + 5.0) * 0.3;
    float front = Grow.x * 1.16 - 0.14 + (edgeN - 0.5) * 0.3;
    float grownU = sstep(front + 0.03, front - 0.07, zen);
    float frontGlow = exp(-pow((zen - front + 0.01) / 0.05, 2.0)) * (0.45 + 0.55 * dnoise(w * 0.02 + time * 0.04)) * (1.0 - sstep(0.94, 1.0, Grow.x)) * step(0.001, Grow.x);
    float leafN = dfbm(w * 0.018 + 3.7) * 0.8 + dnoise(w * 0.09 + 1.3) * 0.2;
    float lossT = Grow.y * 1.2 - 0.1;
    float leafU = sstep(lossT - 0.02, lossT + 0.07, leafN);
    float goldU = clamp(Tint.x * 1.15 + 0.5 * sstep(lossT + 0.12, lossT, leafN) * step(0.001, Grow.y), 0.0, 1.0);

    // ---------------------------------------------------------------- the deep forest (the painting)
    vec3 paint = texture(Sampler0, pano(u)).rgb;
    float grain = mix(dnoise(w * 0.33) * 0.6 + dnoise(w * 0.8 + 5.0) * 0.4, 0.5, clamp(fw * 1.2, 0.0, 1.0));
    vec3 deepDay = paint * vec3(0.9, 0.98, 0.9) * (0.9 + 0.2 * grain);
    vec3 deepNight = paint * vec3(0.13, 0.22, 0.2) + vec3(0.006, 0.016, 0.016);
    vec3 upper = mix(deepNight, deepDay, day);
    float fleck = sstep(0.62, 0.8, dnoise(w * 0.16 + vec2(time * 0.015, -time * 0.011)) * 0.7 + grain * 0.3) * (1.0 - clamp(fw * 0.6, 0.0, 1.0));
    float sunThrough = (pow(sd, 10.0) * 0.5 + pow(sd, 60.0) * 1.0) * SunDir.w * day;
    upper += vec3(1.0, 0.86, 0.52) * sunThrough * (0.3 + 1.5 * fleck);
    upper += vec3(0.5, 0.62, 0.66) * pow(max(dot(dir, -SunDir.xyz), 0.0), 8.0) * 0.1 * Tint.y * night;
    upper = mix(upper, honey(upper), goldU * 0.75);

    // ---------------------------------------------------------------- the near crowns
    Crown k = crown(wl, fwl, Grow.x);
    // Each crown turns gold and sheds in its own time, in patches.
    float mine = k.id * 0.35 + (leafN - 0.5) * 0.35;
    float shedN = mix(dnoise(wl * 0.12 + 7.0), 0.5, clamp(fwl * 0.2, 0.0, 1.0));
    float shed = sstep(-0.04, 0.04, Grow.y * 1.2 - 0.12 - mine - (shedN - 0.5) * 0.34);
    float goldK = clamp(Tint.x * 1.4 - k.id * 0.35, 0.0, 1.0);
    float T = exp(-k.thick * 2.0);
    float speck = mix(dnoise(wl * 0.75 + 3.0) * 0.6 + dnoise(wl * 1.9) * 0.4, 0.5, clamp(fwl * 0.9, 0.0, 1.0));
    vec3 litLeaf = mix(vec3(0.3, 0.8, 0.44), vec3(0.68, 0.9, 0.32), k.hue);
    vec3 leafD = mix(vec3(0.018, 0.085, 0.055), litLeaf, T) * (0.8 + 0.4 * speck);
    // Young leaves on a crown still growing are a paler, brighter lime.
    leafD = mix(leafD, vec3(0.72, 0.95, 0.42) * (0.75 + 0.35 * speck), (1.0 - k.grown) * 0.7 * Tint.z);
    leafD += vec3(1.0, 0.86, 0.45) * (pow(sd, 5.0) * 0.45 + pow(sd, 30.0) * 0.6) * T * SunDir.w;
    vec3 leafN8 = mix(vec3(0.008, 0.024, 0.024), vec3(0.05, 0.15, 0.13), T) + vec3(0.1, 0.5, 0.38) * pow(T, 2.2) * 0.3 * (0.55 + 0.45 * speck);
    vec3 leafC = mix(leafN8, leafD, day);
    leafC = mix(leafC, honey(leafC) * mix(0.35, 1.0, day), goldK * 0.95);
    float leafA = k.cover * (1.0 - shed);
    vec3 bark = mix(vec3(0.012, 0.022, 0.022), vec3(0.14, 0.11, 0.08), day) + vec3(0.9, 0.6, 0.25) * 0.08 * day * goldK;
    float barkA = k.branch * (1.0 - leafA) * (1.0 - Grow.z) * 0.82;

    // ---------------------------------------------------------------- put the layers together
    float aU = grownU * leafU;
    vec3 pre = upper * aU;
    float a = aU;
    pre = pre * (1.0 - leafA) + leafC * leafA; a = a * (1.0 - leafA) + leafA;
    pre = pre * (1.0 - barkA) + bark * barkA; a = a * (1.0 - barkA) + barkA;

    // ---------------------------------------------------------------- fireflies, and the lamps of little hanging houses
    vec2 cellF = floor(wl / 5.0);
    float hf = dhash(cellF + key);
    if (hf > 0.955) {
        vec2 at = (cellF + 0.2 + 0.6 * dhash2(cellF + 3.0)) * 5.0;
        float rr = max(0.45, fwl * 1.4);
        float tw = 0.5 + 0.5 * sin(time * (0.8 + 2.0 * hf) + hf * 60.0);
        float f = exp(-dot(wl - at, wl - at) / (rr * rr)) * tw;
        pre += vec3(1.0, 0.8, 0.36) * f * Grow.w * (0.5 + 0.9 * night) * (a * 0.7 + 0.3);
    }
    vec2 cellH = floor(wl / 70.0);
    if (dhash(cellH + key + 5.0) > 0.72 && leafA > 0.5) {
        vec2 at = (cellH + 0.3 + 0.4 * dhash2(cellH + 8.0)) * 70.0;
        vec2 d = wl - at;
        float rr = max(0.55, fwl * 1.2);
        float windows = 0.0;
        for (int i = 0; i < 3; i++) { vec2 o = vec2(float(i) * 2.2 - 2.2, 0.0); windows += exp(-dot(d - o, d - o) / (rr * rr)); }
        pre += vec3(1.0, 0.7, 0.34) * windows * (0.35 + 1.2 * night) * leafA;
        pre += vec3(1.0, 0.62, 0.3) * exp(-dot(d, d) / 90.0) * 0.07 * (0.3 + night) * leafA;
    }

    // ---------------------------------------------------------------- haze toward the horizon
    vec3 haze = mix(vec3(0.03, 0.08, 0.075), vec3(0.6, 0.75, 0.62), day);
    float hz = sstep(240.0, 1100.0, dist) * 0.8;
    pre = mix(pre, haze * a, hz);

    // ---------------------------------------------------------------- the growing front's light
    vec3 frontCol = vec3(0.8, 1.0, 0.55) * frontGlow * (0.18 + 0.4 * night) * Tint.z;

    pre *= SkyLook.z;
    float fade = horizon * SkyLook.w;
    pre = pre * fade + frontCol * fade;
    float alpha = clamp(a, 0.0, 1.0) * fade;
    // Where the leaves have gone the real sky shows, but its sun and moon were hidden while the canopy covered them.
    float open = 1.0 - clamp(a, 0.0, 1.0);
    float sun = SunDir.w * (1.0 - smoothstep(0.012, 0.02, acos(clamp(dot(dir, SunDir.xyz), -1.0, 1.0))));
    float moon = SunDir.w * (1.0 - smoothstep(0.009, 0.015, acos(clamp(dot(dir, -SunDir.xyz), -1.0, 1.0))));
    pre += (vec3(1.0, 0.95, 0.8) * (sun * 1.2 + pow(sd, 400.0) * 0.4) * day + vec3(0.85, 0.9, 0.95) * moon * night * 0.8) * open * fade * Tint.w;
    fragColor = vec4(pre, alpha) * ColorModulator;
}
