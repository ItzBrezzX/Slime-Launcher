#version 450
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in uint inBlockId;
layout(location = 0) out vec3 normal;
layout(location = 1) flat out uint blockId;

void main() {
    // Orthographic isometric framing for the complete 3x3 spawn region.
    // Chunk-local vertices are translated to world coordinates by the CPU.
    float sx = (inPosition.x - inPosition.z) * 0.018;
    float sy = ((inPosition.x + inPosition.z) - 16.0) * 0.0105
             - (inPosition.y - 60.0) * 0.008;
    float depth = clamp((inPosition.x + inPosition.z + inPosition.y + 64.0) / 256.0,
                        0.0, 0.95);
    gl_Position = vec4(sx, sy, depth, 1.0);
    normal = inNormal;
    blockId = inBlockId;
}
