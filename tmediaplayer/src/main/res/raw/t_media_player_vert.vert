#version 300 es
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
uniform mat4 transform;
uniform mat4 model;
uniform mat4 view;
out vec2 TexCoord;

void main() {
    gl_Position = view * model * transform * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
}
