#version 150
// The golden needle. Antique gold that mirrors the crimson cloth above and the dark land below, a brushed sheen along its
// length, cloud scrolls engraved down its waist with embers in the grooves, a long eye slot through its flattened end, and a
// point that burns for a while after it has gone through. Behind the cloth it shows only through the wound, dark in the void.
//
// Color: r ground clearance (0.5 + blocks/64; under the land it is cut away), b heat, a presence (it resolves in and out
// grain by grain). NeedleLight: xyz toward the wound's light, w strength. NeedleSky: rgb the sky's colour, w daylight.
// NeedleLook: x time, y haze, z brightness.
#moj_import <veilborne:thread_seam.glsl>
uniform vec4 ColorModulator;
uniform vec4 NeedleLight;
uniform vec4 NeedleSky;
uniform vec4 NeedleLook;
in vec3 trueRel;
in vec2 nUV;
in vec4 nTint;
in vec3 nNormal;
out vec4 fragColor;

void main() {
    if ((nTint.r - 0.5) * 64.0 < -1.2) discard;
    float along = nUV.x, around = nUV.y;
    float grain = seamHash(floor(vec2(along * 520.0, around * 12.0)));
    float presence = nTint.a;
    if (grain > presence * 1.03) discard;
    float dissolveEdge = 1.0 - sstep(0.0, 0.06, presence * 1.03 - grain);
    // The eye: a slot through both flat faces near the back end, rounded at its ends.
    float slotLen = sstep(0.016, 0.022, along) * (1.0 - sstep(0.066, 0.072, along));
    float face = min(abs(around - 0.25), abs(around - 0.75));
    if (slotLen > 0.5 && face < 0.11) discard;
    float behind = step(DomeInfo.w + 0.6, length(trueRel - DomeInfo.xyz));
    float seen = 1.0;
    if (behind > 0.5) {
        seen = seamGap(normalize(trueRel));
        if (seen < 0.03) discard;
    }
    vec3 n = normalize(nNormal);
    vec3 v = normalize(-trueRel);
    if (dot(n, v) < 0.0) n = -n;
    // Down the waist, two scarlet threads are inlaid in the gold, wound round it in opposite senses so they cross in a
    // lattice, each with a slow wave in it; fine rings near both ends. Engraving fades out with distance instead of aliasing.
    float waist = sstep(0.12, 0.2, along) * (1.0 - sstep(0.78, 0.86, along));
    float turns = 24.0;
    float wv = 0.05 * sin(along * 150.0);
    float h1 = abs(fract(around + along * turns + wv) - 0.5), h2 = abs(fract(-around + along * turns - wv) - 0.5);
    float aa = fwidth(around) + fwidth(along * turns);
    float line = min(h1, h2);
    float legible = clamp(1.6 - aa * 22.0, 0.0, 1.0);
    float groove = (1.0 - sstep(0.012, 0.012 + aa * 1.5, line)) * waist * legible;
    float edge = (1.0 - sstep(0.024, 0.024 + aa * 1.5, line)) * waist * legible - groove;
    float ringAA = fwidth(along * 60.0);
    float rings = (1.0 - sstep(0.04, 0.04 + ringAA * 1.5, abs(fract(along * 60.0) - 0.5))) * (1.0 - waist) * sstep(0.08, 0.1, along) * (1.0 - sstep(0.9, 0.92, along)) * clamp(1.6 - ringAA * 3.0, 0.0, 1.0);
    float pits = seamNoise(vec2(along * 1700.0, around * 64.0));
    vec3 gold = mix(vec3(0.74, 0.56, 0.27), vec3(1.0, 0.86, 0.56), 0.62 + 0.14 * pits);
    gold *= 1.0 - 0.35 * edge - 0.2 * rings;
    gold = mix(gold, vec3(0.5, 0.06, 0.05), groove);
    vec3 l = normalize(NeedleLight.xyz);
    vec3 r = reflect(-v, n);
    vec3 below = mix(vec3(0.05, 0.03, 0.02), vec3(0.2, 0.14, 0.1), NeedleSky.w);
    // What it mirrors: dark land below; above, the red sky, which on polished gold reads warm and bright; and a hot line
    // where the two meet, running the length of the shaft.
    vec3 above = mix(NeedleSky.rgb * 1.2, vec3(0.72, 0.56, 0.38), 0.55);
    vec3 env = mix(below, above, sstep(-0.3, 0.5, r.y));
    env += vec3(1.0, 0.7, 0.42) * exp(-r.y * r.y / 0.01) * 0.4;
    env += vec3(1.0, 0.5, 0.3) * pow(max(dot(r, l), 0.0), 10.0) * NeedleLight.w * 1.1;
    float ndl = max(dot(n, l), 0.0);
    float fres = pow(1.0 - max(dot(n, v), 0.0), 4.0);
    // Brushed along its length: a streak that slides around the shaft as the view turns.
    float brush = pow(0.5 + 0.5 * sin(around * 25.13 + dot(v, n) * 6.0), 18.0) * 0.18;
    vec3 col = gold * (NeedleSky.rgb * 0.2 + ndl * vec3(1.0, 0.62, 0.4) * NeedleLight.w * 0.5);
    col += gold * env * 0.95 + vec3(1.0, 0.86, 0.62) * pow(max(dot(r, l), 0.0), 64.0) * NeedleLight.w * 1.3;
    col += gold * brush;
    col += vec3(1.0, 0.8, 0.5) * fres * 0.6;
    // It is not only lit: the needle of heaven gives a little light of its own, so it stays gold against the red.
    col += gold * vec3(0.22, 0.19, 0.12) * (0.8 + 0.2 * sin(NeedleLook.x * 1.3 + along * 9.0));
    float time = NeedleLook.x;
    // The inlaid thread glows faintly, a pulse running along it, brighter where the point has just gone through.
    col += vec3(1.0, 0.2, 0.1) * groove * (0.35 + 0.25 * sin(time * 1.7 - along * 40.0) + 0.7 * nTint.b);
    col += vec3(1.0, 0.72, 0.42) * nTint.b * sstep(0.82, 1.0, along) * 1.6;
    col += vec3(1.0, 0.45, 0.2) * dissolveEdge * 1.5;
    // Seen from the land through the wound, it is a dark gold shape in the void, lit only at its rim.
    if (behind > 0.5) col = col * vec3(0.55, 0.36, 0.26) + vec3(1.0, 0.45, 0.22) * fres * 0.6;
    col = mix(col, NeedleSky.rgb, NeedleLook.y);
    fragColor = vec4(col * NeedleLook.z, 1.0) * ColorModulator;
}
