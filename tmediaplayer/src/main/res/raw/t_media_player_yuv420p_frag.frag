#version 300 es
precision highp float;
uniform sampler2D yTexture;
uniform sampler2D uTexture;
uniform sampler2D vTexture;

in vec2 TexCoord;
out vec4 FragColor;
void main() {
    float y, u, v;
    y = texture(yTexture, TexCoord).x;
    u = texture(uTexture, TexCoord).x;
    v = texture(vTexture, TexCoord).x;
    float r, g, b;
    r = y + 1.403 * (v - 0.5);
    g = y - 0.334 * (u - 0.5) - 0.714 * (v - 0.5);
    b = y + 1.770 * (u - 0.5);
    FragColor = vec4(r, g, b, 1.0);
}
