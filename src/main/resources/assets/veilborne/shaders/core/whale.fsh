#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float TideTime;
uniform vec3 TideOrigin;
uniform vec4 TideFog;
uniform vec2 TideHalo;
in vec2 whaleUV;
in vec4 whaleTint;
in vec3 whaleNormal;
in vec3 whaleRel;
out vec4 fragColor;

// Light filtering down through the inverted sea: a cheap iterative caustic pattern.
float caustic(vec2 p, float t) {
    vec2 i = p;
    float c = 1.0;
    for (int n = 0; n < 4; n++) {
        float tt = t * (1.0 - 3.5 / float(n + 1));
        i = p + vec2(cos(tt - i.x) + sin(tt + i.y), sin(tt - i.y) + cos(tt + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + tt) / 0.005), p.y / (cos(i.y + tt) / 0.005)));
    }
    c /= 4.0;
    c = 1.17 - pow(c, 1.4);
    return clamp(pow(abs(c), 8.0), 0.0, 1.0);
}

void main() {
    vec3 n = normalize(whaleNormal);
    vec3 v = normalize(-whaleRel);
    float facing = abs(dot(n, v));
    float fade = whaleTint.a;
    if (TideHalo.y > 0.0) {
        float rim = pow(1.0 - facing, 1.6) * 0.8;
        fragColor = vec4(vec3(0.28, 0.74, 0.92) * rim * TideHalo.y * fade, 1.0) * ColorModulator;
        return;
    }
    vec4 skin = texture(Sampler0, whaleUV);
    if (skin.a < 0.5) discard;
    vec3 base = skin.rgb * whaleTint.rgb;
    vec3 light = normalize(vec3(0.25, 1.0, 0.3));
    // Light from the sea above, and a cool bounce from the glowing land below so the belly keeps its detail.
    vec3 ambient = mix(vec3(0.21, 0.34, 0.42), vec3(0.36, 0.56, 0.68), n.y * 0.5 + 0.5);
    vec3 lit = base * (ambient + max(dot(n, light), 0.0) * vec3(0.5, 0.7, 0.8));
    vec3 world = whaleRel + TideOrigin;
    float caus = caustic(world.xz * 0.075 + vec2(world.y * 0.03) - 250.0, TideTime * 0.45);
    float pale = dot(base, vec3(0.33));
    lit += base * caus * smoothstep(-0.2, 0.7, n.y) * vec3(0.55, 1.0, 1.05) * (0.75 - pale * 0.45);
    lit += vec3(0.2, 0.6, 0.76) * pow(1.0 - facing, 3.0) * 0.32;
    lit = mix(lit, TideFog.rgb, TideFog.a);
    float twinkle = 0.7 + 0.3 * sin(TideTime * 1.3 + whaleUV.x * 91.0 + whaleUV.y * 57.0);
    lit += texture(Sampler1, whaleUV).rgb * twinkle * (1.0 - TideFog.a * 0.5) * 1.15;
    fragColor = vec4(lit, fade) * ColorModulator;
}
