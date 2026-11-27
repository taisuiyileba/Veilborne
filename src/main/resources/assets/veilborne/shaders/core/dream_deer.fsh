#version 150
// 苍翠梦潮: the deer, almost transparent. Its body is glass tinted with jade: nearly clear where you look straight through it,
// bright at the edges where you see it side-on, with the veins of a leaf running through it and tiny lights drifting inside
// like fireflies caught in amber. Its antlers are pale living wood with moss on them, and on them grows a woodland: small
// trees, trailing moss, a lamp in a little house. Its eyes are honey. Fading uses a depth pre-pass, so the half-transparent
// body never shows its own far side; the halo pass is a soft additive rim, wider from far away.
//
// Kinds (deerTint.a * 8): 0 glass, 1 antler, 2 leaves on the antlers, 3 their trunks, 4 eye, 5 hoof, 6 lamplight, 7 hanging
// moss. deerTint.rgb: a per-vertex shade (the eye's lid when it blinks). DeerLook: x time, y daylight, z brightness,
// w visibility. DeerHalo: x swell (halo pass), y halo strength, z/w unused. DeerFog: rgb haze, w amount. DeerSun: xyz toward
// the sun, w night glow.
#moj_import <veilborne:dream_noise.glsl>
uniform vec4 ColorModulator;
uniform vec4 DeerLook;
uniform vec4 DeerHalo;
uniform vec4 DeerFog;
uniform vec4 DeerSun;
in vec2 deerUV;
in vec4 deerTint;
in vec3 deerNormal;
in vec3 deerRel;
out vec4 fragColor;

void main() {
    float kind = floor(deerTint.a * 8.0);
    vec3 n = normalize(deerNormal);
    vec3 v = normalize(-deerRel);
    float facing = abs(dot(n, v));
    float f = 1.0 - facing;
    float time = DeerLook.x, day = DeerLook.y, night = 1.0 - day, vis = DeerLook.w;
    vec3 jade = mix(vec3(0.42, 0.92, 0.72), vec3(0.78, 1.0, 0.9), day);
    if (DeerHalo.y > 0.0) {
        if (kind > 1.0) discard;
        float rim = pow(f, 1.7) * 0.9;
        fragColor = vec4(jade * rim * DeerHalo.y * vis, 1.0) * ColorModulator;
        return;
    }
    vec3 L = normalize(DeerSun.xyz + vec3(0.0, 0.3, 0.0));
    float diff = max(dot(n, L), 0.0);
    vec3 col;
    float alpha;
    if (kind == 0.0) {
        // Not glass so much as light held in the shape of a deer: a faint mist of it all through, brightest where you see
        // the body side-on; veins running through it as through a leaf; fireflies caught in it, drifting.
        vec2 q = vec2(deerUV.x * 26.0, deerUV.y * 13.0);
        float r1 = 1.0 - abs(2.0 * dnoise(q * 0.55 + vec2(0.0, time * 0.02)) - 1.0);
        float r2 = 1.0 - abs(2.0 * dnoise(q * 1.4 + 7.0) - 1.0);
        float vein = pow(r1, 14.0) + pow(r2, 18.0) * 0.5;
        float mist = dnoise(q * 0.35 + vec2(time * 0.05, -time * 0.03)) * 0.6 + dnoise(q * 0.9 - vec2(time * 0.04, 0.0)) * 0.4;
        vec2 sc = floor(q * 1.9);
        float h = dhash(sc);
        float spark = 0.0;
        if (h > 0.9) {
            vec2 at = sc + 0.5 + (dhash2(sc + 4.0) - 0.5) * 0.6;
            vec2 drift = vec2(sin(time * 0.4 + h * 30.0), cos(time * 0.33 + h * 20.0)) * 0.18;
            spark = exp(-dot(q * 1.9 - at - drift, q * 1.9 - at - drift) * 18.0) * (0.5 + 0.5 * sin(time * (1.0 + h * 2.0) + h * 40.0));
        }
        vec3 inner = mix(jade, vec3(1.0, 0.93, 0.7), 0.25 + 0.2 * mist);
        col = inner * (0.34 + 0.35 * mist + 1.05 * pow(f, 2.0)) + vec3(0.55, 1.0, 0.72) * vein * (0.3 + 0.45 * night) + vec3(1.0, 0.82, 0.4) * spark * 1.4;
        col += vec3(1.0, 0.95, 0.8) * pow(max(dot(reflect(-v, n), L), 0.0), 16.0) * 0.15 * day;
        alpha = vis * clamp(0.2 + 0.12 * mist + 0.45 * pow(f, 2.4) + vein * (0.12 + 0.1 * night) + spark * 0.6, 0.0, 1.0);
    } else if (kind == 1.0) {
        // Living wood: pale grey-green bark, deep in moss, with veins of the same light as the body in it.
        float bark = dnoise(vec2(deerUV.y * 8.0, deerUV.x * 90.0)) * 0.6 + dnoise(vec2(deerUV.y * 20.0, deerUV.x * 240.0)) * 0.4;
        float moss = sstep(0.42, 0.66, dnoise(deerUV * vec2(40.0, 6.0) + 3.0) * 0.7 + max(n.y, 0.0) * 0.4);
        vec3 base = mix(vec3(0.5, 0.5, 0.42), vec3(0.74, 0.72, 0.6), bark);
        base = mix(base, mix(vec3(0.18, 0.4, 0.2), vec3(0.36, 0.6, 0.28), bark), moss);
        col = base * (0.5 + 0.55 * diff) * mix(0.3, 1.0, day) + jade * pow(f, 3.0) * 0.3;
        float sapVein = pow(abs(sin(deerUV.y * 18.85 + deerUV.x * 40.0)), 30.0);
        col += vec3(0.45, 1.0, 0.65) * sapVein * (0.08 * day + 0.35 * night);
        alpha = vis * 0.96;
    } else if (kind == 2.0) {
        // The woodland's leaves: clumps with ragged edges, lit from behind.
        float cl = dnoise(deerUV * vec2(12.0, 8.0)) * 0.6 + dnoise(deerUV * vec2(31.0, 19.0)) * 0.4;
        if (f > 0.45 + 0.5 * cl) discard;
        vec3 base = mix(vec3(0.05, 0.2, 0.1), vec3(0.3, 0.62, 0.3), cl * (0.5 + 0.5 * max(n.y, 0.0)));
        float thin = pow(f, 1.5);
        col = base * (0.5 + 0.6 * diff) * mix(0.25, 1.0, day) + vec3(0.5, 0.85, 0.35) * thin * 0.5 * day + vec3(0.1, 0.45, 0.3) * thin * 0.5 * night;
        alpha = vis;
    } else if (kind == 3.0) {
        col = vec3(0.3, 0.22, 0.15) * (0.4 + 0.6 * diff) * mix(0.3, 1.0, day);
        alpha = vis;
    } else if (kind == 4.0) {
        float lid = deerTint.r;
        float pupil = 1.0 - sstep(0.05, 0.1, abs(deerUV.y - 0.5)) * 1.0;
        col = mix(vec3(1.0, 0.78, 0.32), vec3(1.0, 0.95, 0.75), pow(facing, 3.0)) * (1.3 + 0.5 * night);
        col = mix(col, vec3(0.25, 0.12, 0.04), pupil * 0.85 * sstep(0.2, 0.5, deerUV.x));
        col *= lid;
        alpha = vis;
    } else if (kind == 5.0) {
        col = vec3(1.0, 0.82, 0.45) * (0.3 + 0.8 * f) + jade * 0.2;
        alpha = vis * (0.35 + 0.5 * f);
    } else if (kind == 6.0) {
        col = vec3(1.0, 0.74, 0.38) * (1.4 + 0.2 * sin(time * 2.0));
        alpha = vis;
    } else {
        col = mix(vec3(0.18, 0.5, 0.3), vec3(0.5, 0.9, 0.5), f) * mix(0.4, 1.0, day) + vec3(0.2, 0.7, 0.45) * 0.2 * night;
        alpha = vis * 0.7;
    }
    col = mix(col, DeerFog.rgb, DeerFog.w * (kind == 4.0 || kind == 6.0 ? 0.3 : 1.0));
    fragColor = vec4(col * DeerLook.z, alpha) * ColorModulator;
}
