#version 150
// 月骸: a porcelain moon. The shell is a Voronoi mosaic laid over longitude/latitude, so plates foreshorten
// toward the limb like a sphere. Seams open one by one as MoonState.x rises, so the crack spreads rather than
// appearing as a net; gold light seeps from the open ones. Plates come away in order (MoonState.y) to show the
// luminous core (MoonState.z). The 22-degree halo ring (MoonState.w) is the omen.
// MoonKind: x mirror, y shatter, z reflection, w unused. A reflection mirrors everything, including the core,
// so it cannot be told apart at a glance; only when it shatters does it turn out to be hollow.
// MoonLook: x daylight, y opacity, z quad extent, w little moon. Output is premultiplied alpha.
// MoonRenderer mirrors hash() and the cell layout on the CPU to drop falling plates in the right places.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float MoonTime;
uniform vec4 MoonState;
uniform vec4 MoonKind;
uniform vec4 MoonLook;
in vec2 moonUV;
in vec4 moonTint;
out vec4 fragColor;

const float K = 3.4;

uint hash(uvec2 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * 1664525u; v.y += v.x * 1664525u;
    v ^= (v >> 16u);
    v.x += v.y * 1664525u; v.y += v.x * 1664525u;
    v ^= (v >> 16u);
    return v.x ^ v.y;
}
float h01(ivec2 c, uint salt) {
    uvec2 u = uvec2(c + ivec2(1000)) + uvec2(salt * 7919u, salt * 104729u);
    return float(hash(u) >> 8u) / 16777216.0;
}
vec2 site(ivec2 c) { return vec2(c) + 0.15 + 0.7 * vec2(h01(c, 1u), h01(c, 2u)); }

void main() {
    vec2 p = moonUV;
    float r = length(p);
    float aa = max(fwidth(r) * 1.2, 0.002);
    float little = MoonLook.w;
    float crack = MoonState.x, shed = MoonState.y, coreGlow = MoonState.z;
    bool mirror = MoonKind.x > 0.5, reflection = MoonKind.z > 0.5;
    float shatter = MoonKind.y;
    vec3 gold = vec3(1.0, 0.76, 0.40);

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    if (r < 1.0 + aa) {
        vec3 n = vec3(p, sqrt(max(0.0, 1.0 - r * r)));
        float lon = atan(n.x, n.z), lat = asin(clamp(n.y, -1.0, 1.0));
        if (mirror) lon = -lon;
        vec2 q = vec2(lon, lat) * K;
        ivec2 base = ivec2(floor(q));
        float f1 = 9.0, f2 = 9.0;
        ivec2 near = base, near2 = base;
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            ivec2 c = base + ivec2(i, j);
            float d = length(site(c) - q);
            if (d < f1) { f2 = f1; near2 = near; f1 = d; near = c; } else if (d < f2) { f2 = d; near2 = c; }
        }
        float edge = f2 - f1;
        float cellLat = site(near).y / K;
        float key = 0.62 * h01(near, 3u) + 0.38 * (0.5 - 0.5 * sin(cellLat));
        float key2 = h01(near, 4u) * 0.8;
        bool shattered = key2 < shatter * 1.25;
        float gone = (key < shed || shattered) ? 1.0 : 0.0;
        float loosening = (1.0 - gone) * clamp(1.0 - (key - shed) / 0.06, 0.0, 1.0) * step(0.001, shed);

        // Glazed porcelain: soft lavender limb, a crazed glaze from the texture, one glossy highlight.
        vec2 tuv = vec2(lon / 6.28318 + 0.5, 0.5 - lat / 3.14159);
        vec3 tex = texture(Sampler0, tuv).rgb;
        vec3 lightDir = normalize(vec3(mirror ? 0.42 : -0.42, 0.52, 0.74));
        float diffuse = 0.78 + 0.22 * max(dot(n, lightDir), 0.0);
        vec3 limb = mix(vec3(0.62, 0.59, 0.78), vec3(1.0), smoothstep(0.0, 0.8, n.z));
        vec3 shell = tex * limb * diffuse;
        float spec = pow(max(dot(n, normalize(lightDir + vec3(0.0, 0.0, 1.0))), 0.0), 70.0);
        shell += vec3(0.95, 0.95, 1.0) * spec * 0.3;
        // Seams: each opens at its own moment, then glows; a seam is shared by exactly two plates.
        ivec2 lo = (near.x * 131 + near.y < near2.x * 131 + near2.y) ? near : near2;
        ivec2 hi = (lo == near) ? near2 : near;
        float seam = h01(lo * 7 + hi * 13, 5u);
        // At most about sixty per cent of the seams open before the molt; the rest stay as glaze hairlines.
        float open = smoothstep(seam, seam + 0.12, crack * 0.7);
        // Long seams break into segments along their length, like crazing that has not quite joined up.
        float along = h01(ivec2(floor(q * 3.0 + vec2(seam * 17.0))), 6u);
        open *= mix(1.0, step(0.3, along), 0.6 - 0.25 * crack);
        open = max(open, loosening);
        float w = mix(0.012, 0.034, crack) + loosening * 0.03;
        float line = (1.0 - smoothstep(w * 0.3, w, edge)) * open;
        float seep = exp(-edge / (0.025 + 0.045 * crack)) * crack * open;
        // The glaze is already near white, so light added on top would only clip; the seams replace it with amber
        // instead, and the glaze beside an open seam warms toward it.
        shell = mix(shell, shell * vec3(1.0, 0.86, 0.66), clamp(seep * 0.9, 0.0, 1.0));
        shell = mix(shell, vec3(1.0, 0.70, 0.30) * (1.05 + loosening * 0.2), clamp(line * 0.92, 0.0, 1.0));
        if (reflection) shell *= 0.97 + 0.03 * sin(MoonTime * 6.1 + lon * 3.0);

        // The bare moon: warm ivory light with a lace of brighter veins, dimmer just under the broken edge.
        vec2 lq = vec2(lon, lat) * 11.0;
        float lace = 0.5 + 0.5 * sin(lq.x + sin(lq.y * 1.3 + MoonTime * 0.15) * 1.7) * sin(lq.y * 0.9 + sin(lq.x * 0.7) * 1.9);
        vec3 inner = mix(vec3(1.0, 0.84, 0.55), vec3(1.0, 0.97, 0.88), smoothstep(0.1, 1.0, n.z));
        inner *= (0.58 + 0.44 * coreGlow) * (0.82 + 0.28 * pow(lace, 3.0));
        inner *= 0.55 + 0.45 * smoothstep(0.0, 0.14, edge);
        inner += gold * (1.0 - smoothstep(0.0, w * 1.4, edge)) * 0.8;
        if (reflection) inner *= vec3(0.96, 0.97, 1.03);
        float innerAlpha = 1.0;
        if (shattered) {
            // Where a reflection has broken there is nothing inside: a faint violet rim and the sky behind.
            inner = vec3(0.35, 0.28, 0.55) * (1.0 - smoothstep(0.0, 0.08, edge)) * 0.5;
            innerAlpha = 0.0;
        }
        float disc = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, r);
        float surfaceAlpha = mix(1.0, innerAlpha, gone);
        vec3 surface = mix(shell, inner, gone);
        color = surface * disc;
        alpha = surfaceAlpha * disc;
    }
    // Halo: a soft silver glow, the 22-degree ring with a faint warm inner edge, and core light spilling out.
    float d = max(r - 1.0, 0.0);
    float outside = smoothstep(1.0 - aa, 1.0 + aa, r);
    vec3 halo = vec3(0.74, 0.70, 0.95) * (exp(-d * 4.5) * 0.28 + exp(-d * 1.3) * 0.06) * outside * (1.0 - little * 0.4);
    float ring = exp(-pow((r - 1.9) / 0.06, 2.0)) * MoonState.w * (1.0 - little);
    halo += mix(vec3(1.0, 0.8, 0.62), vec3(0.72, 0.78, 1.0), smoothstep(1.84, 1.96, r)) * ring * 0.42;
    halo += gold * exp(-d * 3.0) * coreGlow * shed * 0.35 * outside;
    halo *= (1.0 - MoonLook.x * 0.55) * (1.0 - shatter);
    float edgeFade = 1.0 - smoothstep(MoonLook.z * 0.82, MoonLook.z, r);
    float opacity = MoonLook.y * moonTint.a;
    fragColor = vec4((color + halo * edgeFade) * moonTint.rgb * opacity, alpha * opacity) * ColorModulator;
}
