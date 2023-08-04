#version 300 es
precision highp float;
uniform sampler2D yTexture;
uniform sampler2D uTexture;
uniform sampler2D vTexture;

in vec2 TexCoord;
out vec4 FragColor;

vec3 yuvToRgb(float y, float u, float v) {
    float r, g, b;
//    r = y + 1.280 * (v - 0.502);
//    g = y - 0.215 * (u - 0.502) - 0.381 * (v - 0.502);
//    b = y + 2.128 * (u - 0.502);

    r = 1.164 * (y - 0.063) + 1.793 * (v - 0.502);
    g = 1.164 * (y - 0.063) - 0.213 * (u - 0.502) - 0.533 * (v - 0.502);
    b = 1.164 * (y - 0.063) + 2.112 * (u - 0.502);
    return vec3(r, g, b);
}

void main() {
    float y, u, v;
    y = texture(yTexture, TexCoord).x;
    u = texture(uTexture, TexCoord).x;
    v = texture(vTexture, TexCoord).x;
    FragColor = vec4(yuvToRgb(y, u, v), 1.0);
}
