#version 300 es

precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;

void main() {
    vec4 textureColor = texture(Texture, TexCoord);
    float y = 0.183 * textureColor.x + 0.614 * textureColor.y + 0.062 * textureColor.z + 0.0625;
    FragColor = vec4(textureColor.xyz, y);
}
