#version 150
// Flat full-screen colour; MoonShadows uses it to reset and apply the alpha-channel shadow mask.
uniform vec4 ColorModulator;
out vec4 fragColor;
void main() {
    fragColor = ColorModulator;
}
