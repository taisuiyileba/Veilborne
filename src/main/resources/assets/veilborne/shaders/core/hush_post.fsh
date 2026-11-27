#version 150
// 无声白昼: the colour pass. The finished frame (Sampler0) and its depth (Sampler1) are read back and drawn again as the
// world looks under the silent daylight: pale grey, lifted and softly compressed, the far distance hazed and a little
// soft. The grey arrives as a tide from the horizon to your feet (Front.x, blocks) and leaves outward from where you stand
// at the return (Front.y). A list of points (Sampler2: 64 x 2 texels; row 0 camera-relative xyz and radius, row 1 kind,
// strength, softness) says where colour stays: kind 0 a sepia warmth round lamps and raised lights, softness saying how
// much of the true colour comes back with it (a silent lantern's is the most; all of it is lost at totality),
// 1 full colour (the island of sound, footfalls, the bell), 2 a ring of full colour (the bell's ringing, a knock), and
// 3 a pocket of the same grey where there is no weather (a still candle, a hollow bell rung empty).
//
// Look: x how drained the land is, y the sky, z time, w point count. Front: x grey front, y colour front, z far softening,
// w how much of the sun is covered. Tone: rgb haze, w haze level. Texel: xy one pixel.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform mat4 InvProjView;
uniform vec4 Look;
uniform vec4 Front;
uniform vec4 Tone;
uniform vec4 Texel;
in vec2 texCoord;
out vec4 fragColor;

float h1(float x) { return fract(sin(x * 91.3458) * 47453.5453); }
float n1(float x) { float i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f); return mix(h1(i), h1(i + 1.0), f); }

void main() {
    vec4 src = texture(Sampler0, texCoord);
    vec3 c = src.rgb;
    float depth = texture(Sampler1, texCoord).r;
    bool sky = depth >= 0.99999;
    vec4 wp = InvProjView * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 P = wp.xyz / wp.w;
    float dist = sky ? 1e5 : length(P);

    // ---------------------------------------------------------------- the far distance, a little soft
    if (!sky && Front.z > 0.001) {
        float r = Front.z * smoothstep(55.0, 250.0, dist) * 2.3 / (720.0 * Texel.y);
        if (r > 0.3) {
            vec3 sum = c;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398 + 0.39;
                sum += texture(Sampler0, texCoord + vec2(cos(a), sin(a)) * Texel.xy * r).rgb;
            }
            c = mix(c, sum / 9.0, 0.85);
        }
    }

    // ---------------------------------------------------------------- pale grey
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    // The land is lifted pale; the sky was drawn pale already and keeps its blacks (the black sun stays black).
    float g = sky ? lum : 0.15 + 0.81 * pow(lum, 0.8);
    if (!sky) g = mix(g, Tone.w, smoothstep(28.0, 430.0, dist) * 0.42);
    vec3 grey = vec3(g) * vec3(0.984, 0.99, 1.0);

    // ---------------------------------------------------------------- where the grey has reached
    float drained;
    if (sky) drained = Look.y;
    else {
        float az = atan(P.z, P.x);
        float wobble = (n1(az * 3.0 + 7.0) - 0.5) * 60.0 + (n1(az * 11.0 + 3.0) - 0.5) * 18.0;
        float inGrey = smoothstep(Front.x - 34.0, Front.x + 8.0, dist + wobble);
        float back = 1.0 - smoothstep(Front.y - 26.0, Front.y + 4.0, dist + wobble * 0.7);
        drained = Look.x * inGrey * (1.0 - back);
    }

    // ---------------------------------------------------------------- what keeps its colour
    float amber = 0.0, share = 0.0, full = 0.0, pocket = 0.0;
    int count = int(Look.w + 0.5);
    if (!sky) for (int i = 0; i < 64; i++) {
        if (i >= count) break;
        vec4 a = texelFetch(Sampler2, ivec2(i, 0), 0);
        vec4 k = texelFetch(Sampler2, ivec2(i, 1), 0);
        float d = distance(P, a.xyz), r = a.w;
        int kind = int(k.x + 0.5);
        if (kind == 0) { float w = k.y * (1.0 - smoothstep(r * 0.15, r, d)); if (w > amber) { amber = w; share = k.z; } }
        else if (kind == 1) full = max(full, k.y * (1.0 - smoothstep(r - max(k.z, 0.05), r, d)));
        else if (kind == 2) full = max(full, k.y * exp(-pow((d - r) / max(k.z, 0.05), 2.0)));
        else pocket = max(pocket, k.y * (1.0 - smoothstep(r * 0.4, r, d)));
    }
    amber *= 1.0 - 0.9 * Front.w;
    // Lamplight in the grey is an old photograph's: sepia, with only a trace of the true colour.
    vec3 sepia = vec3(g) * vec3(1.14, 0.97, 0.74) + vec3(0.025, 0.008, -0.012);
    vec3 warm = mix(sepia, c * vec3(1.05, 0.96, 0.84), share);
    vec3 outc = mix(c, grey, drained);
    // At totality the land goes whiter still, as if overexposed.
    if (!sky) outc = mix(outc, 1.0 - (1.0 - outc) * (1.0 - 0.34 * Front.w), drained);
    outc = mix(outc, warm, amber * drained);
    outc = mix(outc, src.rgb, full * drained);
    outc = mix(outc, grey, pocket * (1.0 - drained) * 0.84);
    fragColor = vec4(outc, src.a);
}
