#version 150
// 无声白昼: the pale sky. Drawn over the whole screen right after the vanilla sky; terrain and the held clouds cover it.
//
// A bleached, sourceless daylight (Sampler0: a painted frozen sky, fixed to the directions, never moving), the same by
// night. The sun (or the moon) is a disc of paper white with no halo. The black sun gives no light and has no glow: it is
// only a disc of black, but the sky bends round it like a lens, so the frozen clouds near it curve into arcs. While it
// covers the sun it wears a corona of darkness. On the horizon a white line may stand, straight and still, and after it
// fades a dark afterimage stays where it was, as a bright thing does in the eye.
//
// SkyLook: x time (s), y daylight, z brightness, w the pale sky's presence. White / Black: xyz direction, w angular radius.
// Eclipse: x how much of the pale disc is covered, y the black corona, z the lens, w the black sun's presence.
// Thunder: x the line's azimuth, y its brightness, z the afterimage, w the elevation of its top. Key: x seed, y totality.
// Output is premultiplied alpha.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform vec4 SkyLook;
uniform vec4 White;
uniform vec4 Black;
uniform vec4 Eclipse;
uniform vec4 Thunder;
uniform vec4 Key;
in vec2 ndc;
out vec4 fragColor;

vec2 pano(vec3 u) { return vec2(atan(u.z, u.x) / 6.2831853 + 0.5, clamp(1.0 - asin(clamp(u.y, 0.0, 1.0)) / 1.5707963, 0.002, 0.998)); }
float hash1(float x) { return fract(sin(x * 127.1) * 43758.5453); }
float wave(float a, float k) {
    // Smooth periodic noise round a circle: a few sines of whole frequencies, so it closes on itself.
    return 0.5 + 0.25 * sin(a * 5.0 + k * 19.0) + 0.15 * sin(a * 9.0 + k * 7.0 + 1.3) + 0.1 * sin(a * 17.0 + k * 3.0 + 2.1);
}

void main() {
    vec4 far = InvProjView * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    float day = SkyLook.y, cover = Eclipse.x, key = Key.x;
    vec3 b = Black.xyz; float rb = Black.w;
    float cb = clamp(dot(dir, b), -1.0, 1.0), th = acos(cb);
    vec3 q = dir - b * cb;
    float ql = length(q);
    vec3 away = ql > 1e-5 ? q / ql : vec3(0.0, 1.0, 0.0);

    // ---------------------------------------------------------------- the lens
    // Light passing close to the black sun is bent toward it: what shows at an angle th from it comes from th - k/th,
    // and very close, from its far side. The frozen clouds curve round it; nothing glows.
    vec3 sdir = dir;
    float lens = Eclipse.z * Eclipse.w;
    if (lens > 0.001 && th < rb * 10.0) {
        float k = rb * rb * 1.25 * lens;
        float beta = th - k / max(th, 1e-4);
        sdir = normalize(b * cos(beta) + away * sin(beta));
    }

    // ---------------------------------------------------------------- the pale sky
    float up = clamp(sdir.y, 0.0, 1.0);
    float bright = 0.07 * cover;
    vec3 horizon = vec3(mix(0.77, 0.885, day) + bright) * vec3(1.0, 0.996, 0.988);
    vec3 zenith = vec3(mix(0.67, 0.8, day) + bright * 1.3) * vec3(0.985, 0.99, 1.0);
    vec3 base = mix(horizon, zenith, pow(up, 0.55));
    vec3 paint = texture(Sampler0, pano(vec3(sdir.x, max(sdir.y, 0.003), sdir.z))).rgb;
    // At totality the painting pales toward blank: the sky goes white.
    // The painting is low in contrast already (a bleached noon); weight it enough that its held clouds still read.
    vec3 col = base * (0.42 + 0.75 * mix(paint, vec3(0.8), 0.55 * cover));
    // Below the horizon, the same pale haze.
    col = mix(horizon * 0.985, col, smoothstep(-0.14, 0.015, dir.y));

    // ---------------------------------------------------------------- the pale disc: paper white, a crisp cut edge, no halo
    float cw = acos(clamp(dot(sdir, White.xyz), -1.0, 1.0));
    float aa = max(fwidth(th), 1e-4) * 1.2;
    float disc = 1.0 - smoothstep(White.w - aa, White.w + aa, cw);
    vec3 paper = vec3(mix(0.95, 1.02, day) + 0.03 * cover) * vec3(1.0, 0.997, 0.99);
    float cut = exp(-pow((cw - White.w) / max(aa * 1.5, 0.0009), 2.0));
    col = mix(col, paper, disc);
    col *= 1.0 - 0.16 * cut;

    // ---------------------------------------------------------------- the black corona, while it covers the sun
    if (Eclipse.y > 0.001) {
        vec3 t1 = normalize(cross(b, abs(b.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
        vec3 t2 = cross(b, t1);
        float ang = atan(dot(q, t2), dot(q, t1));
        float r = th / rb;
        float rays = wave(ang, key) * wave(ang * 2.0 + 1.7, key + 0.37);
        float fine = pow(0.5 + 0.5 * sin(ang * 43.0 + sin(ang * 7.0 + key * 11.0) * 2.4), 3.0);
        float reach = 0.35 + 2.2 * rays;
        float cor = smoothstep(1.0, 1.035, r) * exp(-(r - 1.0) / reach * 1.5) * (0.5 + 0.5 * fine);
        cor += exp(-(r - 1.0) / 0.1) * smoothstep(1.0, 1.012, r) * 0.55;
        col *= 1.0 - Eclipse.y * clamp(cor, 0.0, 1.0) * 0.82;
    }

    // ---------------------------------------------------------------- the black sun: nothing but black
    float blackDisc = (1.0 - smoothstep(rb - aa, rb + aa, th)) * Eclipse.w;
    col = mix(col, vec3(0.012, 0.012, 0.014), blackDisc);

    // ---------------------------------------------------------------- the white line, and what it leaves
    if (Thunder.y > 0.001 || Thunder.z > 0.001) {
        float az = atan(dir.z, dir.x);
        float el = asin(clamp(dir.y, -1.0, 1.0));
        float daz = abs(mod(az - Thunder.x + 3.14159265, 6.2831853) - 3.14159265) * cos(el);
        float along = smoothstep(-0.03, -0.005, el) * (1.0 - smoothstep(Thunder.w - 0.015, Thunder.w, el));
        // The width from the angle to the line itself: atan jumps by a full turn behind you, and its fwidth with it.
        float w = max(fwidth(daz) * 0.9, 0.0005);
        float line = exp(-pow(daz / w, 2.0)) * along;
        // A little darkening either side so the white stands out of a white sky, as a scratch in film does.
        col *= 1.0 - 0.22 * exp(-daz / (w * 5.0)) * along * Thunder.y;
        col = mix(col, vec3(1.08), line * Thunder.y);
        float ghost = exp(-pow(daz / (w * 2.6), 2.0)) * along;
        col *= 1.0 - Thunder.z * (ghost * 0.45 + exp(-daz / (w * 12.0)) * along * 0.12);
    }

    col *= SkyLook.z;
    float alpha = SkyLook.w;
    fragColor = vec4(col * alpha, alpha) * ColorModulator;
}
