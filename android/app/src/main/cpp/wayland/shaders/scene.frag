#version 450
layout(set=0,binding=0) uniform sampler2D pixels;
layout(push_constant) uniform Quad { vec4 src; vec4 dst; vec4 options; } quad;
layout(location=0) in vec2 uv;
layout(location=0) out vec4 color;
void main() {
    color = texture(pixels, uv);
    if (quad.options.x > 0.5) color.a = 1.0;
}
