#version 300 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;
uniform vec3 TextColor;

void main() {
    FragColor = vec4(TextColor, texture(Texture, TexCoord).a);
}
