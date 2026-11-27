#version 150
// 绯线缝世: the sky as cloth. Drawn over the whole screen right after the vanilla sky; terrain covers it.
//
// Each view ray is followed to the dome of cloth anchored over the event (thread_seam.glsl), so the wound stays put over
// the land as you walk and everyone sees it in the same place. The cloth is a painted crimson cloudscape (Sampler0) that
// flows slowly toward the seam, with a faint weave that shows only near the wound, where its light falls on it. The wound
// itself is starless dark with a far red depth that does not move with the cloth; its edges are torn, burning scarlet with
// a gold core, and loose threads of the cloth hang across the gap. Once a stretch is drawn shut the seam is a hot line
// that later cools to gold and becomes an ordinary band of stars.
//
// SkyLook: x time (s), y daylight, z brightness, w the cloth's presence. Omen: x the hairline before the tear, y the pull's
// flare, z the star band's own presence (it outlasts the cloth a little), w unused. Flash: x seconds since a needle went in,
// y its azimuth, z strength. Hand: xyz where the palm's shadow lies on the cloth (unit, from the dome centre), w strength.
// SunDir: xyz the real sun, w how much of it shows. Output is premultiplied alpha.
#moj_import <veilborne:thread_seam.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform vec4 SkyLook;
uniform vec4 Omen;
uniform vec4 Flash;
uniform vec4 Hand;
uniform vec4 SunDir;
in vec2 ndc;
out vec4 fragColor;

float vhash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise3(vec3 x) {
    vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(vhash(i), vhash(i + vec3(1,0,0)), f.x), mix(vhash(i + vec3(0,1,0)), vhash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(vhash(i + vec3(0,0,1)), vhash(i + vec3(1,0,1)), f.x), mix(vhash(i + vec3(0,1,1)), vhash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
float fbm(vec3 p) { return noise3(p) * 0.5 + noise3(p * 2.02 + 7.0) * 0.28 + noise3(p * 4.1 + 13.0) * 0.14 + noise3(p * 8.3 + 29.0) * 0.08; }
vec2 pano(vec3 u) { return vec2(atan(u.z, u.x) / 6.2831853 + 0.5, clamp(1.0 - asin(clamp(u.y, 0.0, 1.0)) / 1.5707963, 0.002, 0.998)); }

/** Soft capsule distance in the plane of two tangent axes. */
float capsule(vec2 p, vec2 a, vec2 b, float r) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h) - r; }

void main() {
    vec4 far = InvProjView * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    float horizon = sstep(-0.05, 0.05, dir.y);
    if (horizon <= 0.0) discard;
    float time = SkyLook.x, day = SkyLook.y, key = SeamState.z;
    vec3 hit = domeHit(dir);
    vec3 u = normalize(hit - DomeInfo.xyz);
    vec2 s = seamCoords(u);
    float along = s.x, across = s.y;
    float onArc = 1.0 - sstep(SeamM.w * 0.96, SeamM.w * 1.01, abs(along));
    float hw = seamRagged(s) * onArc;
    float edge = abs(across) - hw;                 // < 0 inside the wound
    float closed = seamClosed(along);
    float cool = SeamLook.z, pull = SeamLook.y;

    // ---------------------------------------------------------------- the cloth
    float elev = clamp(dir.y, 0.0, 1.0);
    vec3 night = mix(vec3(0.2, 0.045, 0.055), vec3(0.055, 0.012, 0.028), pow(elev, 0.55));
    vec3 noon = mix(vec3(0.84, 0.46, 0.33), vec3(0.42, 0.16, 0.14), pow(elev, 0.7));
    vec3 base = mix(night, noon, day);
    // Clouds flow toward the seam: a two-phase flow map along the tangent that points at the seam line.
    vec3 n = seamN();
    vec3 toward = -sign(across) * (n - u * dot(n, u));
    float hz = u.x * u.x + u.z * u.z;
    vec2 flow = vec2((u.x * toward.z - u.z * toward.x) / max(hz, 1e-4) / 6.2831853, -toward.y / sqrt(max(1.0 - u.y * u.y, 1e-4)) / 1.5707963);
    float near = exp(-max(edge, 0.0) / 0.35);
    float rate = time * (0.006 + 0.03 * pull), ph = fract(rate), ph2 = fract(rate + 0.5);
    float amount = 0.018 * (0.4 + near) * (0.35 + pull);
    vec2 puv = pano(u);
    vec3 paint = texture(Sampler0, puv + flow * amount * ph).rgb * (1.0 - abs(2.0 * ph - 1.0))
               + texture(Sampler0, puv + flow * amount * ph2).rgb * (1.0 - abs(2.0 * ph2 - 1.0));
    vec3 cloth = base * (0.45 + 0.9 * paint * mix(vec3(1.0, 0.82, 0.8), vec3(1.05, 0.95, 0.9), day));
    // Stars in the cloth at night, dimmed where the clouds are thick.
    vec3 sp = u * 420.0;
    vec3 cell = floor(sp);
    float star = vhash(cell);
    float spark = step(0.985, star) * exp(-dot(fract(sp) - 0.5, fract(sp) - 0.5) * 30.0) * (0.6 + 0.4 * sin(time * (1.0 + star * 3.0) + star * 40.0));
    cloth += vec3(1.0, 0.86, 0.78) * spark * (1.0 - day) * (1.0 - smoothstep(0.25, 0.7, dot(paint, vec3(0.33)))) * 0.9;
    // The weave: warp and weft of the sky's cloth, only visible where the wound's light rakes across it.
    float glowOnCloth = exp(-max(edge, 0.0) / 0.028) * onArc;
    vec2 weaveUV = vec2(along, across) * 210.0;
    float warp = pow(abs(sin(weaveUV.x * 3.14159)), 0.5), weft = pow(abs(sin(weaveUV.y * 3.14159 + 1.57 * step(0.5, fract(weaveUV.x * 0.5)))), 0.5);
    float weave = mix(warp, weft, step(0.5, fract(weaveUV.x * 0.5 + weaveUV.y * 0.5)));
    float fade = clamp(1.0 - fwidth(weaveUV.x) * 1.6, 0.0, 1.0) * clamp(1.0 - fwidth(weaveUV.y) * 1.6, 0.0, 1.0);
    vec3 hot = mix(vec3(1.0, 0.18, 0.12), vec3(1.0, 0.72, 0.36), cool);
    cloth += hot * glowOnCloth * (0.3 + 0.25 * weave * fade) * (1.0 - 0.6 * closed) * mix(1.0, 0.55, day);
    cloth += hot * exp(-max(edge, 0.0) / 0.1) * onArc * 0.14 * (1.0 - 0.5 * closed);

    // ---------------------------------------------------------------- the hand that is never seen: only its shadow
    if (Hand.w > 0.001) {
        vec3 pc = Hand.xyz;
        vec3 fw = normalize(SeamM.xyz - pc * dot(SeamM.xyz, pc));
        vec3 rt = normalize(cross(pc, fw));
        vec2 q = vec2(dot(u - pc, rt), dot(u - pc, fw));
        float d = length(q / vec2(0.13, 0.1)) - 1.0;
        float lens[5] = float[5](0.2, 0.3, 0.33, 0.3, 0.24);
        float angs[5] = float[5](-0.95, -0.36, -0.04, 0.26, 0.56);
        for (int k = 0; k < 5; k++) {
            vec2 root = vec2(sin(angs[k]) * 0.1, cos(angs[k]) * 0.08);
            vec2 tipv = root + vec2(sin(angs[k] * 0.8), cos(angs[k] * 0.8)) * lens[k];
            float curl = sin(time * 0.3 + float(k)) * 0.01;
            d = min(d, capsule(q, root, tipv + vec2(curl, 0.0), 0.028 - 0.003 * float(k == 0)) / 0.028);
        }
        float shadow = 1.0 - sstep(-0.8, 1.6, d);
        // Like a shadow play: a lamp behind the cloth lights it warm around the hand, and the hand is dark on that light.
        float lamp = exp(-dot(q, q) / 0.09) * Hand.w;
        cloth += mix(vec3(0.62, 0.16, 0.1), vec3(0.95, 0.5, 0.3), day) * lamp * 0.55 * (1.0 - shadow);
        cloth += vec3(1.0, 0.55, 0.3) * lamp * exp(-abs(d - 0.2) / 0.22) * 0.12 * (1.0 - shadow);
        cloth *= 1.0 - 0.8 * shadow * Hand.w;
    }

    // ---------------------------------------------------------------- the sun and moon, dull through the cloth
    float sunDot = dot(dir, SunDir.xyz), moonDot = -sunDot;
    cloth += vec3(1.0, 0.42, 0.26) * (exp(-(1.0 - sunDot) / 0.00022) * 0.9 + exp(-(1.0 - sunDot) / 0.012) * 0.12) * SunDir.w * day;
    cloth += vec3(0.95, 0.72, 0.72) * (exp(-(1.0 - moonDot) / 0.00018) * 0.55 + exp(-(1.0 - moonDot) / 0.01) * 0.05) * SunDir.w * (1.0 - day);

    // ---------------------------------------------------------------- the wound
    vec3 col = cloth;
    float inside = 1.0 - sstep(-0.0012, 0.0012, edge);
    if (inside > 0.0) {
        // Starless: only a far red depth, fixed to the direction and not to the cloth, so it slides behind the tear.
        float deep = fbm(dir * 2.6 + vec3(key * 17.0, time * 0.004, 0.0));
        float deeper = fbm(dir * 6.0 - vec3(0.0, time * 0.006, key * 5.0));
        vec3 voidCol = vec3(0.006, 0.0015, 0.004) + vec3(0.1, 0.012, 0.022) * pow(deep, 3.0) * 0.9 + vec3(0.05, 0.004, 0.012) * pow(deeper, 4.0);
        // The cloth has thickness: its torn inner lip, lit from the burning edge.
        float lip = exp(edge / 0.004);
        voidCol += hot * lip * 0.22;
        // Loose threads of the torn cloth hang into the gap, a few to a stretch: each curls away from the edge it hangs
        // from and thins to nothing; now and then one still bridges the whole gap, sagging. They are lit from their roots.
        float cellW = 0.03;
        float ci = floor(along / cellW);
        for (int j = -1; j <= 1; j++) {
            float c = ci + float(j);
            float h = seamHash(vec2(c, key * 31.0));
            if (h < 0.52) continue;
            float side = fract(h * 5.13) > 0.5 ? 1.0 : -1.0;
            float bridge = step(0.93, h);
            float reach = mix(0.18 + 0.5 * fract(h * 7.31), 1.0, bridge);
            // t runs from the edge it hangs from (0) across the gap (1); it stops short, at its reach.
            float t = clamp((hw - side * across) / (2.0 * hw + 1e-5), 0.0, 1.0);
            float sway = sin(time * (0.35 + 0.5 * h) + c * 1.7) * 0.0022;
            float bend = (fract(h * 3.3) - 0.5) * 0.018;
            float x0 = (c + 0.5 + (fract(h * 13.7) - 0.5) * 0.5) * cellW;
            float x = x0 + (bend + sway) * (bridge > 0.5 ? sin(3.14159 * t) : t * t * (3.0 - 2.0 * t));
            float live = (1.0 - sstep(reach * 0.7, reach, t)) * (1.0 - closed);
            float dl = abs(along - x);
            float w = 0.0006 * (1.0 - 0.55 * t * (1.0 - bridge));
            float lit = mix(pow(1.0 - t, 1.5), 0.3 + 0.7 * pow(abs(2.0 * t - 1.0), 2.0), bridge);
            voidCol += (hot * 0.85 + vec3(0.12, 0.06, 0.04)) * (1.0 - sstep(w * 0.35, w, dl)) * live * (0.2 + 0.8 * lit);
            voidCol += hot * exp(-dl / 0.0022) * live * 0.05 * lit;
        }
        // Where it has been drawn shut the narrow pucker is cloth again, not void: it warms as the seam cools, and by
        // day it is never a dark line.
        voidCol = mix(voidCol, cloth * 0.75 + hot * 0.3, closed * max(cool, day * 0.85));
        col = mix(cloth, voidCol, inside);
    }
    // The torn edge burns: a gold-white core and a scarlet halo; embers crawl along it.
    float rimCore = exp(-abs(edge) / 0.0011) * onArc, rimHalo = exp(-abs(edge) / 0.009) * onArc;
    float ember = pow(seamNoise(vec2(along * 900.0 - time * 1.6 * sign(across), sign(across) * 5.0)), 6.0);
    float open = seamOpened(along);
    vec3 coreCol = mix(vec3(1.0, 0.86, 0.6), vec3(1.0, 0.93, 0.74), cool);
    float heat = (1.0 - 0.35 * cool) * open;
    col += coreCol * rimCore * (0.7 + 0.8 * ember) * heat;
    col += hot * rimHalo * (0.35 + 0.35 * ember) * heat;
    // Sutured: the shut seam glows along its length, brighter as it is pulled tight.
    float sutured = closed * open * exp(-abs(across) / 0.006) * onArc;
    col += hot * sutured * (0.35 + 0.8 * pull + 0.25 * sin(time * 2.0 - along * 30.0) * 0.3) * (1.0 - SeamLook.w * 0.9);
    // Before it tears: a hairline of gold that flickers where the sky will open.
    float hair = Omen.x * exp(-abs(across) / 0.0009) * onArc * (0.55 + 0.45 * sin(time * 9.0 + along * 60.0) * sin(time * 3.1));
    col += vec3(1.0, 0.83, 0.5) * hair * 1.4;

    // ---------------------------------------------------------------- light running along the horizon when the needle goes in
    if (Flash.z > 0.001) {
        float t = Flash.x;
        float az = atan(dir.z, dir.x), daz = abs(mod(az - Flash.y + 3.14159, 6.28318) - 3.14159);
        float line = exp(-pow((dir.y - 0.042) / 0.0035, 2.0)) * exp(-daz / (0.06 + t * 1.4)) * exp(-t * 1.7);
        col += vec3(1.0, 0.82, 0.55) * line * Flash.z * 2.4;
        col += hot * exp(-pow((dir.y - 0.035) / 0.025, 2.0)) * exp(-daz / 0.45) * exp(-t * 2.4) * Flash.z * 0.22;
    }
    // The pull: for a moment the whole cloth flares along its seam.
    col += hot * Omen.y * exp(-abs(across) / 0.08) * onArc * 0.35;

    // ---------------------------------------------------------------- the band of stars it becomes
    vec3 band = vec3(0.0);
    float bandW = SeamLook.w;
    if (bandW > 0.001) {
        float bw = 0.035 + 0.02 * seamProfile(along);
        float b = exp(-across * across / (bw * bw)) * onArc;
        vec3 fine = u * 1400.0;
        float fs = vhash(floor(fine));
        float fine1 = step(0.93, fs) * exp(-dot(fract(fine) - 0.5, fract(fine) - 0.5) * 22.0) * (0.6 + 0.4 * sin(time * (1.0 + fs * 4.0) + fs * 50.0));
        float milk = fbm(u * 38.0 + key * 9.0);
        float dust = sstep(0.55, 0.72, fbm(u * 22.0 + 3.0));
        band = (vec3(1.0, 0.94, 0.82) * fine1 * 1.3 + vec3(0.9, 0.78, 0.62) * milk * 0.22 * (1.0 - dust * 0.8)) * b * bandW;
        band += vec3(1.0, 0.8, 0.45) * exp(-abs(across) / 0.0015) * onArc * (1.0 - bandW) * cool * 0.6;
        band *= mix(1.0, 0.35, day);
    }

    col *= SkyLook.z;
    float alpha = SkyLook.w * horizon;
    // The hairline and the star band are light, not cloth: they add to whatever sky is behind.
    vec3 light = (vec3(1.0, 0.83, 0.5) * hair * 1.4) * (1.0 - SkyLook.w) * horizon + band * Omen.z * horizon;
    fragColor = vec4(col * alpha + light * SkyLook.z, alpha);
}
