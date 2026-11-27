#version 150
// A shard of mirror turning in mid-air. It shows the world around it flipped left to right, in the warmer light
// of another hour, with a bright bevel and a glint that sweeps across when it faces you. Sampler0 is a copy of
// the frame so far; Sampler1 the shard shapes (alpha: outline, red: bevel, green: cracks).
// PaneInfo: x the shard's centre on screen (0..1), y opacity, z how squarely it faces the eye, w time.
// PaneWarm: x how warm the other hour is (0..1), y cracking (0..1).
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform vec4 PaneInfo;
uniform vec4 PaneWarm;
in vec2 paneUV;
in vec4 paneTint;
out vec4 fragColor;
void main() {
    vec4 shape = texture(Sampler1, paneUV);
    if (shape.a < 0.02) discard;
    vec2 s = gl_FragCoord.xy / ScreenSize;
    vec2 m = vec2(2.0 * PaneInfo.x - s.x, s.y);
    m.x += sin(s.y * 40.0 + PaneInfo.w * 0.7) * 0.0015;
    vec3 seen = texture(Sampler0, clamp(m, vec2(0.001), vec2(0.999))).rgb;
    vec3 warm = seen * mix(vec3(1.0), vec3(1.18, 0.98, 0.74), PaneWarm.x) + vec3(0.03, 0.022, 0.0) * PaneWarm.x;
    float bevel = shape.r;
    float sweep = exp(-pow((paneUV.x + paneUV.y * 0.6 - fract(PaneInfo.w * 0.05) * 2.4 + 0.4) / 0.06, 2.0));
    float face = PaneInfo.z;
    vec3 col = mix(warm * 0.92, vec3(0.93, 0.97, 1.0), bevel * 0.75);
    col += vec3(1.0, 0.95, 0.85) * sweep * (0.25 + 0.75 * face) * 0.6;
    col += vec3(0.95, 0.85, 0.6) * shape.g * PaneWarm.y * 0.9;
    col = mix(col, col * paneTint.rgb, 0.35);
    float alpha = shape.a * PaneInfo.y * paneTint.a * (0.72 + 0.28 * max(bevel, face));
    fragColor = vec4(col, alpha) * ColorModulator;
}
