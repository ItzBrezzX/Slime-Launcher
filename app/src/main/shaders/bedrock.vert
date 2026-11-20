#version 450
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in uint inBlockId;
layout(location = 0) out vec3 normal;
layout(location = 1) flat out uint blockId;

void main() {
    // Stable isometric projection for the first native chunk renderer.
    float sx = (inPosition.x - inPosition.z) / 22.0;
    float sy = (34.0 + (inPosition.x + inPosition.z) * 0.5 - inPosition.y) / 48.0;
    float depth = (inPosition.x + inPosition.z + inPosition.y) / 256.0;
    gl_Position = vec4(sx, sy, depth, 1.0);
    normal = inNormal;
    blockId = inBlockId;
}
