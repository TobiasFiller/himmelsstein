#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GlintAlpha;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 glintCoord1;
in vec2 glintCoord2;

out vec4 fragColor;

// Sampler0: item atlas (only for coverage), Sampler1: energy vein glint, two overlaid layers.
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    float veins = max(texture(Sampler1, glintCoord1).r, texture(Sampler1, glintCoord2).r * 0.7);
    float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd) * GlintAlpha;
    fragColor = vec4(vertexColor.rgb * veins * fade, 1.0);
}
