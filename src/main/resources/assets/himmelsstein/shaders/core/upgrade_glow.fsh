#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// Flat aura colour in the item's silhouette; the texture only decides coverage.
void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, 1.0);
}
