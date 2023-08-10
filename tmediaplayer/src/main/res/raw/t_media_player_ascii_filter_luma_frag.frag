#version 300 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;

void main() {
    vec4 textureColor = texture(Texture, TexCoord);
    float y = 0.299 * textureColor.r + 0.587 * textureColor.g + 0.114 * textureColor.b;
    FragColor = vec4(textureColor.rgb, y);
}
