#version 300 es
#extension GL_OES_EGL_image_external : require
precision mediump float;
in vec2 vTexCoord;
uniform samplerExternalOES oesTex;
out vec4 fragColor;

void main() {
    fragColor = texture(oesTex, vTexCoord);
}
