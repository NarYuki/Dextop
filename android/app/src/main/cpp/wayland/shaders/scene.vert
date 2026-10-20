#version 450
layout(push_constant) uniform Quad { vec4 src; vec4 dst; vec4 options; } quad;
layout(location=0) out vec2 uv;
const vec2 positions[6] = vec2[](vec2(0,0),vec2(1,0),vec2(0,1),vec2(0,1),vec2(1,0),vec2(1,1));
void main() {
    vec2 p = positions[gl_VertexIndex];
    uv = quad.src.xy + p * quad.src.zw;
    gl_Position = vec4(quad.dst.xy + p * quad.dst.zw, 0, 1);
}
