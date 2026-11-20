#version 450
layout(location = 0) in vec3 normal;
layout(location = 1) flat in uint blockId;
layout(location = 0) out vec4 outColor;

void main() {
    vec3 base;
    if (blockId == 1u) base = vec3(0.24, 0.62, 0.25);
    else if (blockId == 2u) base = vec3(0.42, 0.27, 0.16);
    else if (blockId == 3u) base = vec3(0.48, 0.51, 0.55);
    else if (blockId == 4u) base = vec3(0.12, 0.40, 0.78);
    else if (blockId == 5u) base = vec3(0.78, 0.69, 0.43);
    else if (blockId == 6u) base = vec3(0.20, 0.21, 0.23);
    else base = vec3(0.8);
    vec3 lightDir = normalize(vec3(-0.35, 0.8, 0.45));
    float diffuse = 0.48 + 0.52 * max(dot(normalize(normal), lightDir), 0.0);
    outColor = vec4(base * diffuse, 1.0);
}
