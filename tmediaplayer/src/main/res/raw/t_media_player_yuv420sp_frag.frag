#version 300 es
precision highp float;
uniform sampler2D yTexture;
uniform sampler2D uvTexture;
uniform int swapUv;

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

in vec2 TexCoord;
out vec4 FragColor;
void main() {
    float y, u, v;
    y = texture(yTexture, TexCoord).x;
    if (swapUv == 0) {
        u = texture(uvTexture, TexCoord).x;
        v = texture(uvTexture, TexCoord).a;
    } else {
        v = texture(uvTexture, TexCoord).x;
        u = texture(uvTexture, TexCoord).a;
    }
    FragColor = vec4(yuvToRgb(y, u, v), 1.0);
}
