#version 300 es
precision highp float;
uniform sampler2D Texture;
uniform sampler2D subtitleTexture;
uniform int enableSubtitle;
uniform float subtitleXOffset;
uniform float subtitleYOffset;

in vec2 TexCoord;
out vec4 FragColor;
void main() {
    if (enableSubtitle == 0) {
        FragColor = texture(Texture, TexCoord);
    } else {
        vec2 subtitleCoord = TexCoord;
        subtitleCoord.x += (subtitleXOffset - 0.5) * 2.0;
        subtitleCoord.y += (0.5 - subtitleYOffset) * 2.0;
        vec4 subtitleColor = texture(subtitleTexture, subtitleCoord);
        vec4 videoColor = texture(Texture, TexCoord);
        FragColor = mix(videoColor, vec4(subtitleColor.rgb, 1.0), subtitleColor.a);
    }
}
