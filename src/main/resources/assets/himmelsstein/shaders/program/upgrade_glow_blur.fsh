#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 sampleStep;

uniform float Radius;
uniform float RadiusMultiplier;
uniform float Gain;

out vec4 fragColor;

// Vanilla box blur (relies on GL_LINEAR sampling) plus a gain for the final pass.
void main() {
    vec4 blurred = vec4(0.0);
    float actualRadius = max(1.0, round(Radius * RadiusMultiplier));
    for (float a = -actualRadius + 0.5; a <= actualRadius; a += 2.0) {
        blurred += texture(DiffuseSampler, texCoord + sampleStep * a);
    }
    blurred += texture(DiffuseSampler, texCoord + sampleStep * actualRadius) / 2.0;
    fragColor = blurred / (actualRadius + 0.5) * Gain;
}
