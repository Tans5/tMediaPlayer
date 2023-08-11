#version 300 es

precision highp float; // Define float precision
precision highp sampler2DArray;

in vec3 CharColor;
in vec3 TexCoord;
uniform sampler2DArray Texture;
uniform int reverseColor;
uniform float colorFillRate;

out vec4 FragColor;
void main() {
    float r = CharColor.x / 255.0;
    float g = CharColor.y / 255.0;
    float b = CharColor.z / 255.0;
    float a;
    if (reverseColor == 0) {
        a = texture(Texture, TexCoord).a;
    } else {
        a = 1.0 - texture(Texture, TexCoord).a;
    }
    float y = 0.299 * r + 0.587 * g + 0.114 * b;
    if (colorFillRate > y) {
        FragColor = vec4(r, g, b, a);
    } else {
        FragColor = vec4(1.0, 1.0, 1.0, a);
    }
}
