#version 300 es

layout (location = 0) in vec4 vert;
out vec2 TexCoord;

void main() {
    gl_Position = vec4(vert.xy, 0.0, 1.0);
    TexCoord = vert.zw;
}