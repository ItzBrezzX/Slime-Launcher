#version 450
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in uint inBlockId;
layout(location = 0) out vec3 normal;
layout(location = 1) flat out uint blockId;

void main() {
    // Isometric camera centered on a 16x16 spawn chunk. The previous
    // fixed offset placed most of the terrain near the lower clip edge.
    // Keep the whole chunk, including its exposed boundary walls, in view.
    float sx = (inPosition.x - inPosition.z) * 0.055;
    float sy = ((inPosition.x + inPosition.z) - 16.0) * 0.027
             - (inPosition.y - 60.0) * 0.008;
    float depth = clamp((inPosition.x + inPosition.z + inPosition.y) / 256.0,
                        0.0, 0.95);
    gl_Position = vec4(sx, sy, depth, 1.0);
    normal = inNormal;
    blockId = inBlockId;
}
