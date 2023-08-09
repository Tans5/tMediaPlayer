#version 300 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;
uniform ivec3 TextColor;

void main() {
    float r = float(TextColor.x) / 255.0;
    float g = float(TextColor.y) / 255.0;
    float b = float(TextColor.z) / 255.0;
    FragColor = vec4(r, g, b, texture(Texture, TexCoord).a);
}
