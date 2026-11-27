#version 150
// Threads of light: soft ribbons turned to the eye, bright in the middle. UV0.x runs across the ribbon (-1..1); UV0.y = 1 marks
// a thread that belongs to the sky, which behind the cloth shows only where the wound lets it through. Additive, premultiplied.
#moj_import <veilborne:thread_seam.glsl>
uniform vec4 ColorModulator;
in vec3 trueRel;
in vec2 lineUV;
in vec4 lineColor;
out vec4 fragColor;
void main() {
    float x = lineUV.x;
    float core = exp(-x * x * 14.0), glow = exp(-x * x * 2.2) * 0.3;
    float a = lineColor.a * (core + glow);
    vec3 col = mix(lineColor.rgb, vec3(1.0, 0.92, 0.78), core * 0.4);
    if (lineUV.y > 0.5) {
        float r = length(trueRel - DomeInfo.xyz);
        if (r > DomeInfo.w + 0.6) {
            float g = seamGap(normalize(trueRel));
            if (g < 0.03) discard;
            a *= g * 0.6;
            col *= vec3(0.85, 0.55, 0.5);
        }
    }
    if (a < 0.002) discard;
    fragColor = vec4(col * a, a) * ColorModulator;
}
