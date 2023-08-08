#version 300 es
layout (location = 0) in vec4 aPos;
out vec2 TexCoord;

void main() {
    gl_Position = vec4(aPos.xy, 0.0, 1.0);
    TexCoord = aPos.zw;
}
