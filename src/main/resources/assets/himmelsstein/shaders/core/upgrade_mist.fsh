#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// The mist texture's alpha defines its soft coverage, so premultiply the aura colour with it.
void main() {
    float alpha = texture(Sampler0, texCoord0).a;
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
}
