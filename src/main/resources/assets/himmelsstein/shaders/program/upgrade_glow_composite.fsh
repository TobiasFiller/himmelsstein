#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D WideSampler;
uniform sampler2D MistSampler;
uniform sampler2D WorldDepth;
uniform sampler2D ScreenDepth;

in vec2 texCoord;

out vec4 fragColor;

// The hand is rendered after the level pass, so the blurred glow is composited after it.
// WorldDepth is the level depth copied before the hand pass; ScreenDepth still holds the
// hand. Suppress the glow wherever the hand is closer than the world geometry behind it.
void main() {
    float worldDepth = texture(WorldDepth, texCoord).r;
    float screenDepth = texture(ScreenDepth, texCoord).r;
    float visible = step(worldDepth - 0.0002, screenDepth);

    vec3 color = texture(DiffuseSampler, texCoord).rgb
            + texture(WideSampler, texCoord).rgb
            + texture(MistSampler, texCoord).rgb;
    fragColor = vec4(color * visible, 1.0);
}
