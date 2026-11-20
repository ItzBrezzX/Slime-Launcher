#pragma once

#include "bedrock_engine.hpp"
#include <cstdint>
#include <string>
#include <vector>

namespace slime::bedrock {

struct VulkanDeviceInfo {
    bool available = false;
    std::string status;
    std::string deviceName;
    std::string apiVersion;
    std::string driverVersion;
    uint32_t vendorId = 0;
    uint32_t deviceType = 0;
    uint64_t deviceLocalMemoryBytes = 0;
    uint32_t graphicsQueueFamily = 0;
};

class VulkanBackend final {
public:
    VulkanBackend() = default;
    VulkanBackend(const VulkanBackend&) = delete;
    VulkanBackend& operator=(const VulkanBackend&) = delete;
    ~VulkanBackend();

    VulkanDeviceInfo initialize();
    std::string attachSurface(void* nativeWindow,
                              const std::vector<uint32_t>& vertexShader,
                              const std::vector<uint32_t>& fragmentShader);
    void detachSurface();
    bool setMesh(const std::vector<MeshVertex>& vertices,
                 const std::vector<uint32_t>& indices);
    bool render();
    void shutdown();
    bool isInitialized() const;
    bool isSurfaceReady() const;

private:
    struct State;
    State* state_ = nullptr;
};

} // namespace slime::bedrock
