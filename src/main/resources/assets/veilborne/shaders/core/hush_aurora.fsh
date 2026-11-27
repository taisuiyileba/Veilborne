#version 150
// 无声白昼: what is left in the sky, drawn after the colour pass over the sky and the held clouds (Sampler0 is the frame's
// depth: the land covers all of this, the clouds only some of it). From all round the horizon the world's colours go up
// into the black sun in thin, wavering ribbons, pulses of them travelling up the way water is drawn up a wick; once it
// has covered the sun they stop, and as it sinks it gives a little back, the pulses running down again. The black sun
// is a hole in the sky, not a thing in it, so the clouds do not cover it: over cloud it is drawn again, black, with its
// corona. When it hears you, its rim shows amber for a moment.
//
// Black: xyz direction, w angular radius. Flow: x ribbons (negative: flowing back down), y time, z the black sun's
// presence, w seed. Heard: x the answering rim, y the black corona, z cloud base above the eye (blocks), w one pixel
// (radians). Output is premultiplied alpha.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform vec4 Black;
uniform vec4 Flow;
uniform vec4 Heard;
in vec2 ndc;
out vec4 fragColor;

float h1(float x) { return fract(sin(x * 91.3458) * 47453.5453); }
float wave(float a, float k) {
    return 0.5 + 0.25 * sin(a * 5.0 + k * 19.0) + 0.15 * sin(a * 9.0 + k * 7.0 + 1.3) + 0.1 * sin(a * 17.0 + k * 3.0 + 2.1);
}

void main() {
    float depth = texture(Sampler0, ndc * 0.5 + 0.5).r;
    bool sky = depth >= 0.99999;
    vec4 wp = InvProjView * vec4(ndc, sky ? 1.0 : depth * 2.0 - 1.0, 1.0);
    vec3 P = wp.xyz / wp.w;
    // The held clouds: far, and at their own height. Everything else that is not sky covers the sky.
    bool cloud = !sky && P.y > Heard.z - 1.5 && length(P) > 24.0;
    if (!sky && !cloud) discard;
    vec3 dir = normalize(P);
    if (dir.y < -0.06) discard;

    vec3 B = Black.xyz;
    float rb = Black.w, key = Flow.w, pix = Heard.w;
    float cb = clamp(dot(dir, B), -1.0, 1.0), th = acos(cb);

    // ---------------------------------------------------------------- the ribbons
    float s = abs(Flow.x), sgn = Flow.x < 0.0 ? -1.0 : 1.0;
    vec3 hues[6] = vec3[6](vec3(0.46, 0.78, 0.36), vec3(0.42, 0.64, 1.0), vec3(1.0, 0.5, 0.58), vec3(1.0, 0.78, 0.3), vec3(0.66, 0.5, 0.96), vec3(0.32, 0.76, 0.72));
    vec3 col = vec3(0.0);
    float a = 0.0;
    if (s > 0.002) for (int i = 0; i < 14; i++) {
        float hi = h1(float(i) * 3.1 + key * 17.0);
        float az = (float(i) + 0.6 * hi) / 14.0 * 6.2831853;
        vec3 H = normalize(vec3(cos(az), 0.012 + 0.05 * h1(float(i) * 7.7 + key), sin(az)));
        vec3 n = cross(H, B);
        float nl = length(n);
        float total = acos(clamp(dot(H, B), -1.0, 1.0));
        vec3 pr = dir - n / max(nl, 1e-6) * dot(dir, n / max(nl, 1e-6));
        float pl = length(pr);
        if (nl < 1e-3 || pl < 1e-4) continue;
        n /= nl;
        pr /= pl;
        float t = acos(clamp(dot(pr, H), -1.0, 1.0)) / total;
        if (dot(cross(H, pr), n) < 0.0) t = -t;
        if (t < 0.0 || t > 1.0) continue;
        float wig = (0.034 * sin(t * 7.0 + hi * 40.0 + Flow.y * 0.03) + 0.01 * sin(t * 19.0 + hi * 13.0 - Flow.y * 0.05)) * (1.0 - t);
        float off = asin(clamp(dot(dir, n), -1.0, 1.0)) - wig;
        // Thin near the horizon and thinner toward the black sun, never under a pixel.
        float width = max(mix(0.007, 0.0012, pow(t, 0.7)), pix * 0.8);
        float core = exp(-pow(off / width, 2.0));
        float glow = exp(-pow(off / (width * 4.5), 2.0)) * 0.16;
        // Pulses travel up it (down it, as they come back), each a slow swell.
        float ph = t * 9.0 - Flow.y * (0.18 + 0.1 * hi) * sgn + hi * 9.0;
        float pulse = pow(0.5 + 0.5 * sin(ph * 2.0943951), 3.0);
        float edge = rb / max(total, 1e-3);
        float fade = smoothstep(0.0, 0.14, t) * (1.0 - smoothstep(1.0 - edge * 2.6, 1.0 - edge * 1.02, t));
        float w = (core * (0.36 + 0.64 * pulse) + glow * (0.4 + 0.6 * pulse)) * fade * s * 0.78;
        // A little more light than it covers: the colours glow faintly against the pale sky.
        col += hues[i - (i / 6) * 6] * w * 1.12;
        a += w * 0.92;
    }
    if (cloud) { col *= 0.8; a *= 0.8; }

    // ---------------------------------------------------------------- the black sun over the clouds, with its corona
    float dark = 0.0, black = 0.0;
    if (cloud && Flow.z > 0.001) {
        float aa = max(pix, 1e-4) * 1.2;
        black = (1.0 - smoothstep(rb - aa, rb + aa, th)) * Flow.z;
        if (Heard.y > 0.001) {
            vec3 q = dir - B * cb;
            vec3 t1 = normalize(cross(B, abs(B.y) < 0.95 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
            vec3 t2 = cross(B, t1);
            float ang = atan(dot(q, t2), dot(q, t1));
            float r = th / rb;
            float rays = wave(ang, key) * wave(ang * 2.0 + 1.7, key + 0.37);
            float fine = pow(0.5 + 0.5 * sin(ang * 43.0 + sin(ang * 7.0 + key * 11.0) * 2.4), 3.0);
            float cor = smoothstep(1.0, 1.035, r) * exp(-(r - 1.0) / (0.35 + 2.2 * rays) * 1.5) * (0.5 + 0.5 * fine);
            cor += exp(-(r - 1.0) / 0.1) * smoothstep(1.0, 1.012, r) * 0.55;
            dark = Heard.y * clamp(cor, 0.0, 1.0) * 0.82;
        }
        dark = max(dark, black);
    }

    // ---------------------------------------------------------------- the black sun answering: its rim, amber, for a moment
    float ring = exp(-pow((th - rb * 1.015) / (rb * 0.045 + pix), 2.0)) * Heard.x;
    col += vec3(1.0, 0.64, 0.28) * ring;
    a += ring * 0.9;
    a = min(a, 0.9);
    // Ribbons over the darkening: premultiplied "over".
    vec3 outc = col + (1.0 - a) * vec3(0.012, 0.012, 0.014) * black;
    float outa = 1.0 - (1.0 - a) * (1.0 - dark);
    if (outa < 0.002) discard;
    fragColor = vec4(outc, outa) * ColorModulator;
}
