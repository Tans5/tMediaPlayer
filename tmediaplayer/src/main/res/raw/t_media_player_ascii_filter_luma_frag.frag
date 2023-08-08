#version 300 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;

void main() {
    vec4 textureColor = texture(Texture, TexCoord);
    float y = 0.183 * textureColor.r + 0.614 * textureColor.g + 0.062 * textureColor.b + 0.0625;
    FragColor = vec4(textureColor.rgb, y);
}
