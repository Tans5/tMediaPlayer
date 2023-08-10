#version 300 es

layout (location = 0) in vec2 vert;
layout (location = 1) in vec3 aTexCoord;
out vec3 TexCoord;

void main() {
    gl_Position = vec4(vert, 0.0, 1.0);
    TexCoord = aTexCoord;
}