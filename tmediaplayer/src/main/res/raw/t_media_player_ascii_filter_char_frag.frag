#version 300 es

precision highp float; // Define float precision
precision highp sampler2DArray;

in vec3 CharColor;
in vec3 TexCoord;
uniform sampler2DArray Texture;

out vec4 FragColor;
void main() {
    float r = CharColor.x / 255.0;
    float g = CharColor.y / 255.0;
    float b = CharColor.z / 255.0;
    FragColor = vec4(r, g, b, texture(Texture, TexCoord).a);
}
