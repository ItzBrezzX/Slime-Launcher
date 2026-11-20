#pragma once

#include <cstdint>
#include <string>

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
    void shutdown();
    bool isInitialized() const;

private:
    struct State;
    State* state_ = nullptr;
};

} // namespace slime::bedrock
