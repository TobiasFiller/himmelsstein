#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 GlintMat1;
uniform mat4 GlintMat2;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 glintCoord1;
out vec2 glintCoord2;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    glintCoord1 = (GlintMat1 * vec4(UV0, 0.0, 1.0)).xy;
    glintCoord2 = (GlintMat2 * vec4(UV0, 0.0, 1.0)).xy;
}
