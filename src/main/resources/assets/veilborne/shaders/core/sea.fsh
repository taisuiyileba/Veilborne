#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 seaUV;
in vec4 seaTint;
out vec4 fragColor;
void main() {
    // Retain arbitrarily faint sea mist; alpha-test clipping breaks the opening and retreat.
    fragColor = texture(Sampler0, seaUV) * seaTint * ColorModulator;
}
