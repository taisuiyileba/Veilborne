#version 150
// 苍翠梦潮: the hanging crowns, their branches and houses, and the aerial roots, lit as a forest seen from underneath.
//
// Kinds (groveInfo.r * 8): 0 aerial root, 1 foliage, 2 branch inside a crown, 3 a little hanging house, 4 its lit windows,
// 5 rootlet gripping the land, 6 a root of the deer's road, 7 the dream tree's own wood. groveInfo.g: the root's delay (0..1) within its growth window;
// b: a per-piece hash; a: 1 if it hangs free. groveUV.x runs from the canopy (0) to the tip (1).
//
// Roots grow down: nothing below how far this one has come, and a honey bud of light at its growing tip; afterwards,
// sap-light runs down them in slow pulses toward the land, and at the end they draw back up the same way. Foliage is dark
// in its depths and translucent toward the sun; as the leaves are let go it thins in patches, gold at their edges, down to
// the bare branches. Fireflies sit in the leaves. The little houses have lamps in their windows.
//
// GroveLook: x time, y daylight, z brightness, w haze. GroveGrow: x root growth, y roots withdrawn, z leaves lost, w gold.
// GroveSun: xyz toward the sun, w fireflies. GroveRoad: x the road's growth, y the canopy's front (as dream_sky's), z/w unused.
// GroveFog: rgb haze colour, w unused.
#moj_import <veilborne:dream_noise.glsl>
uniform vec4 ColorModulator;
uniform vec4 GroveLook;
uniform vec4 GroveGrow;
uniform vec4 GroveSun;
uniform vec4 GroveRoad;
uniform vec4 GroveFog;
in vec2 groveUV;
in vec4 groveInfo;
in vec3 groveNormal;
in vec3 groveRel;
in vec3 groveWorld;
in float groveDist;
out vec4 fragColor;

void main() {
    float kind = floor(groveInfo.r * 8.0);
    float t = groveUV.x, time = GroveLook.x, day = GroveLook.y, night = 1.0 - day;
    vec3 n = normalize(groveNormal);
    vec3 v = normalize(-groveRel);
    if (dot(n, v) < 0.0) n = -n;
    vec3 L = normalize(GroveSun.xyz + vec3(0.0, 0.25, 0.0));
    float delay = groveInfo.g, key = groveInfo.b;
    vec3 col;
    float glow = 0.0;
    // No crown hangs where the canopy has not yet spread (the same front as the sky's, seen from the event's centre). Roots
    // start later the further out they are, so they come down under canopy that is already there.
    if (kind >= 1.0 && kind <= 4.0 && acos(clamp(normalize(groveWorld - vec3(0.0, 1.6, 0.0)).y, -1.0, 1.0)) / 1.5707963 > GroveRoad.y) discard;

    if (kind == 1.0) {
        // ---------------------------------------------------------------- foliage
        bool dreamTree = groveInfo.a > 0.5;
        float loss = dreamTree ? 0.0 : GroveGrow.z;
        float hole = dnoise3(groveWorld * 0.075 + key * 9.0) * 0.75 + dnoise3(groveWorld * 0.22) * 0.25;
        float lossT = loss * 1.2 - 0.08;
        if (hole < lossT) discard;
        float leafEdge = 1.0 - sstep(lossT, lossT + 0.07, hole);
        // The outline is not a smooth hull but clumps of leaves: toward the silhouette, only the clumps that reach it remain.
        float scale = dreamTree ? 1.6 : 0.32;
        float clumpN = dnoise3(groveWorld * scale + key * 5.0) * 0.65 + dnoise3(groveWorld * scale * 2.7 + 11.0) * 0.35;
        float rim = 1.0 - abs(dot(n, v));
        if (rim > 0.42 + 0.55 * clumpN) discard;
        float clumps = dfbm3(groveWorld * 0.18 + key * 3.0);
        float fine = dnoise3(groveWorld * (dreamTree ? 3.0 : 0.9));
        vec3 deep = vec3(0.03, 0.13, 0.07), mid = vec3(0.14, 0.44, 0.22), lit = vec3(0.56, 0.9, 0.36);
        vec3 base = mix(deep, mid, sstep(0.25, 0.75, clumps)) * (0.8 + 0.4 * fine);
        float diff = max(dot(n, L), 0.0);
        // Lit from behind: thin edges and the side toward the sun glow green-gold with the light coming through them.
        float thin = pow(rim, 1.5) * (0.5 + 0.5 * clumpN);
        float through = pow(max(dot(-v, normalize(GroveSun.xyz)), 0.0), 3.0);
        vec3 dayCol = base * (0.62 + 0.45 * diff) + lit * (thin * (0.7 + 0.6 * through) + 0.12 * clumpN) + vec3(0.9, 0.85, 0.4) * through * thin * 0.35 * GroveSun.w;
        vec3 nightCol = base * 0.14 + vec3(0.06, 0.34, 0.26) * thin * 0.55;
        col = mix(nightCol, dayCol, day);
        if (dreamTree) {
            // The dream tree gives a soft green light of its own, and flowers in honey.
            col += vec3(0.25, 0.6, 0.35) * (0.25 + 0.5 * thin) * (0.35 + 0.65 * night);
            float bloom = sstep(0.8, 0.86, dnoise3(groveWorld * 6.5 + 3.0)) * sstep(0.35, 0.6, dnoise3(groveWorld * 1.3 + 9.0));
            col = mix(col, vec3(1.0, 0.82, 0.42) * (0.8 + 0.4 * night), bloom);
            glow += 0.45 + bloom;
        }
        // Gold before they fall, brightest at the edges of the patches already let go.
        // The same honey-gold the canopy turns, by the leaf's brightness.
        float lum = dot(col, vec3(0.3, 0.55, 0.15));
        float gt = clamp(lum * 1.8, 0.0, 1.0);
        vec3 golden = mix(vec3(0.36, 0.17, 0.04), vec3(1.0, 0.72, 0.28), gt) * (0.75 + 0.4 * gt) * mix(0.35, 1.0, day);
        col = mix(col, golden, dreamTree ? 0.0 : clamp(GroveGrow.w * 1.1 + leafEdge * 0.9, 0.0, 1.0) * 0.95);
        glow += leafEdge * 0.25 * (0.4 + night);
        // Fireflies in the leaves.
        vec3 cell = floor(groveWorld / 2.6);
        float h = dhash3(cell + key * 17.0);
        if (h > 0.93) {
            vec3 at = (cell + 0.5 + (vec3(dhash3(cell + 3.0), dhash3(cell + 5.0), dhash3(cell + 7.0)) - 0.5) * 0.7) * 2.6;
            float tw = 0.5 + 0.5 * sin(time * (1.0 + 2.5 * h) + h * 50.0);
            float f = exp(-dot(groveWorld - at, groveWorld - at) / 0.35) * tw * GroveSun.w;
            col += vec3(1.0, 0.8, 0.35) * f * (0.6 + 1.6 * night);
        }
    } else if (kind == 3.0) {
        // ---------------------------------------------------------------- the little hanging houses
        float diff = max(dot(n, L), 0.0);
        col = vec3(0.2, 0.13, 0.08) * (0.35 + 0.6 * diff) * mix(0.3, 1.0, day);
        col += vec3(0.9, 0.6, 0.25) * pow(1.0 - abs(dot(n, v)), 4.0) * 0.15;
    } else if (kind == 4.0) {
        col = vec3(1.0, 0.72, 0.36) * (0.85 + 0.15 * sin(time * 1.7 + key * 40.0));
        glow = 1.0;
    } else {
        // ---------------------------------------------------------------- roots and branches
        float grown = 1.0;
        if (kind == 0.0 || kind == 5.0 || kind == 6.0) {
            float g = kind == 6.0 ? GroveRoad.x : GroveGrow.x;
            float reach = kind == 5.0 ? sstep(delay + 0.42, delay + 0.56, g) : sstep(delay, delay + 0.45, g);
            float back = sstep(delay * 0.35, delay * 0.35 + 0.62, GroveGrow.y);
            grown = reach * (1.0 - back);
            if (t > grown + 0.002 || grown <= 0.0005) discard;
        } else if (kind == 2.0 && GroveGrow.y > 0.0) {
            // The bare branches draw back into the canopy with the roots.
            if (t > 1.0 - sstep(0.3, 0.9, GroveGrow.y)) discard;
        }
        float bark = dnoise(vec2(groveUV.y * 9.0, t * 120.0 + key * 30.0)) * 0.6 + dnoise(vec2(groveUV.y * 23.0, t * 300.0)) * 0.4;
        float moss = sstep(0.55, 0.75, dnoise3(groveWorld * 0.12 + key * 5.0)) * (0.4 + 0.6 * max(n.y, 0.0));
        // Pale, silvery bark gone green with moss, lit from above by the canopy's own green light.
        vec3 base = mix(vec3(0.42, 0.4, 0.34), vec3(0.66, 0.63, 0.53), bark);
        base = mix(base, vec3(0.2, 0.42, 0.2), moss * 0.85);
        float diff = max(dot(n, L), 0.0);
        col = base * (0.5 + 0.45 * diff + 0.18 * max(n.y, 0.0)) * mix(0.18, 1.0, day);
        col += vec3(0.16, 0.3, 0.14) * (0.5 + 0.5 * n.y) * 0.35 * day;
        col += vec3(0.45, 0.62, 0.38) * pow(1.0 - abs(dot(n, v)), 3.0) * mix(0.1, 0.26, day);
        // Veins of light running down the bark, and pulses of sap-light going down them toward the land.
        float vein = pow(abs(sin(groveUV.y * 3.14159 * 5.0 + t * 30.0 + key * 20.0)), 26.0);
        float pulse = pow(0.5 + 0.5 * sin((t * 9.0 - time * 0.35 + key * 7.0) * 6.2831853), 10.0) * step(0.99, grown);
        vec3 sap = mix(vec3(0.35, 1.0, 0.62), vec3(1.0, 0.82, 0.4), 0.3 + 0.4 * sin(key * 20.0));
        col += sap * (vein * 0.35 + pulse * 0.55) * (0.35 + 0.9 * night) * (kind == 2.0 ? 0.4 : 1.0);
        glow += (vein * 0.3 + pulse) * 0.4;
        // The growing tip: a bud of honey light.
        if (grown < 0.999) {
            float bud = exp(-pow((grown - t) * 40.0, 2.0));
            col += vec3(1.0, 0.86, 0.45) * bud * 2.2;
            glow = max(glow, bud);
        }
        col = mix(col, col * vec3(1.3, 1.0, 0.5), GroveGrow.w * 0.4);
    }

    // ---------------------------------------------------------------- haze with distance, less for what glows
    float hz = (sstep(60.0, 800.0, groveDist) * 0.62 + sstep(800.0, 1300.0, groveDist) * 0.3) * GroveLook.w * (kind == 1.0 ? 0.8 : 1.0);
    col = mix(col, GroveFog.rgb, hz * (1.0 - 0.6 * clamp(glow, 0.0, 1.0)));
    fragColor = vec4(col * GroveLook.z, 1.0) * ColorModulator;
}
