#version 450
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in uint inBlockId;
layout(location = 0) out vec3 normal;
layout(location = 1) flat out uint blockId;

layout(push_constant) uniform Camera {
    vec4 parameters; // yaw, pitch, zoom, viewport aspect ratio
} camera;

void main() {
    vec3 p = inPosition - vec3(8.0, 60.0, 8.0);
    float cy = cos(camera.parameters.x);
    float sy = sin(camera.parameters.x);
    float cp = cos(camera.parameters.y);
    float sp = sin(camera.parameters.y);

    float viewX = p.x * cy - p.z * sy;
    float rotatedZ = p.x * sy + p.z * cy;
    float viewY = p.y * cp - rotatedZ * sp;
    float viewDepth = rotatedZ * cp + p.y * sp;

    float zoom = clamp(camera.parameters.z, 0.55, 2.5);
    float aspect = max(camera.parameters.w, 0.55);
    float sx = viewX * 0.0105 * zoom / aspect;
    float screenY = viewY * 0.0105 * zoom;
    float depth = clamp(0.5 - viewDepth * 0.002, 0.05, 0.95);

    gl_Position = vec4(sx, screenY, depth, 1.0);
    normal = inNormal;
    blockId = inBlockId;
}
