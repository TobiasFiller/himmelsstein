#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D GlowSampler;

in vec2 texCoord;

out vec4 fragColor;

// Removes the mist where the item silhouette was rendered, so the item occludes the aura.
void main() {
    vec4 mist = texture(DiffuseSampler, texCoord);
    float item = texture(GlowSampler, texCoord).a;
    fragColor = mist * (1.0 - item);
}
