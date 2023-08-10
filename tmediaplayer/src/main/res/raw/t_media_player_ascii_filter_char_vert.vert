#version 300 es

layout (location = 0) in vec4 vert;
layout (location = 1) in vec2 offset;
layout (location = 2) in vec4 colorAndTexture;

out vec3 CharColor;
out vec3 TexCoord;

void main() {
    gl_Position = vec4(vert.x + offset.x, vert.y + offset.y, 0.0, 1.0);
    TexCoord = vec3(vert.zw, colorAndTexture.w);
    CharColor = colorAndTexture.xyz;
}