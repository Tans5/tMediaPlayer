#version 300 es
precision highp float;
uniform sampler2D Texture;
uniform sampler2D subtitleTexture;
uniform int enableSubtitle;

in vec2 TexCoord;
out vec4 FragColor;
void main() {
    if (enableSubtitle == 0) {
        FragColor = texture(Texture, TexCoord);
    } else {
        vec4 subtitleColor = texture(subtitleTexture, TexCoord);
        FragColor = mix(texture(Texture, TexCoord), vec4(subtitleColor.rgb, 1.0), subtitleColor.a);
    }
}
