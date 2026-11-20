#include "vulkan_backend.hpp"

#include <cstdio>
#include <new>

#ifdef __ANDROID__
#include <vulkan/vulkan.h>
#endif

namespace slime::bedrock {
namespace {
std::string versionString(uint32_t version) {
    char value[32]{};
    std::snprintf(value, sizeof(value), "%u.%u.%u",
                  VK_VERSION_MAJOR(version), VK_VERSION_MINOR(version),
                  VK_VERSION_PATCH(version));
    return value;
}
}

struct VulkanBackend::State {
#ifdef __ANDROID__
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily = 0;
#endif
    bool initialized = false;
};

VulkanBackend::~VulkanBackend() { shutdown(); }

VulkanDeviceInfo VulkanBackend::initialize() {
    if (state_ != nullptr && state_->initialized) {
#ifdef __ANDROID__
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(state_->physicalDevice, &properties);
        VkPhysicalDeviceMemoryProperties memory{};
        vkGetPhysicalDeviceMemoryProperties(state_->physicalDevice, &memory);
        VulkanDeviceInfo info;
        info.available = true;
        info.status = "Vulkan device and graphics queue are ready.";
        info.deviceName = properties.deviceName;
        info.apiVersion = versionString(properties.apiVersion);
        info.driverVersion = std::to_string(properties.driverVersion);
        info.vendorId = properties.vendorID;
        info.deviceType = static_cast<uint32_t>(properties.deviceType);
        info.graphicsQueueFamily = state_->graphicsQueueFamily;
        for (uint32_t i = 0; i < memory.memoryHeapCount; ++i) {
            if ((memory.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0)
                info.deviceLocalMemoryBytes += memory.memoryHeaps[i].size;
        }
        return info;
#endif
    }
#ifndef __ANDROID__
    VulkanDeviceInfo info;
    info.status = "Vulkan backend requires an Android NDK build.";
    return info;
#else
    if (state_ == nullptr) {
        state_ = new (std::nothrow) State();
        if (state_ == nullptr) {
            VulkanDeviceInfo info;
            info.status = "Unable to allocate Vulkan backend state.";
            return info;
        }
    }

    VkApplicationInfo appInfo{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    appInfo.pApplicationName = "Slime Bedrock Engine";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 2, 0);
    appInfo.pEngineName = "Slime Native Voxel Engine";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 2, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo instanceInfo{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    instanceInfo.pApplicationInfo = &appInfo;
    VkResult result = vkCreateInstance(&instanceInfo, nullptr, &state_->instance);
    if (result != VK_SUCCESS) {
        VulkanDeviceInfo info;
        info.status = "vkCreateInstance failed: " + std::to_string(result);
        shutdown();
        return info;
    }

    uint32_t deviceCount = 0;
    result = vkEnumeratePhysicalDevices(state_->instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS || deviceCount == 0) {
        VulkanDeviceInfo info;
        info.status = result == VK_SUCCESS
            ? "No Vulkan physical devices were reported by this device."
            : "vkEnumeratePhysicalDevices failed: " + std::to_string(result);
        shutdown();
        return info;
    }

    VkPhysicalDevice* devices = new (std::nothrow) VkPhysicalDevice[deviceCount];
    if (devices == nullptr) {
        VulkanDeviceInfo info;
        info.status = "Unable to allocate Vulkan physical-device list.";
        shutdown();
        return info;
    }
    result = vkEnumeratePhysicalDevices(state_->instance, &deviceCount, devices);
    if (result != VK_SUCCESS) {
        delete[] devices;
        VulkanDeviceInfo info;
        info.status = "Unable to enumerate Vulkan physical devices: " + std::to_string(result);
        shutdown();
        return info;
    }

    int bestScore = -1;
    for (uint32_t i = 0; i < deviceCount; ++i) {
        uint32_t queueCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, nullptr);
        if (queueCount == 0) continue;
        VkQueueFamilyProperties* queues = new (std::nothrow) VkQueueFamilyProperties[queueCount];
        if (queues == nullptr) continue;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, queues);
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[i], &properties);
        for (uint32_t q = 0; q < queueCount; ++q) {
            if ((queues[q].queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0 || queues[q].queueCount == 0)
                continue;
            int score = properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ? 3 :
                        properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU ? 2 : 1;
            if (score > bestScore) {
                bestScore = score;
                state_->physicalDevice = devices[i];
                state_->graphicsQueueFamily = q;
            }
        }
        delete[] queues;
    }
    delete[] devices;

    if (state_->physicalDevice == VK_NULL_HANDLE) {
        VulkanDeviceInfo info;
        info.status = "No Vulkan graphics-capable queue family was found.";
        shutdown();
        return info;
    }

    const float queuePriority = 1.0F;
    VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queueInfo.queueFamilyIndex = state_->graphicsQueueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &queuePriority;

    VkPhysicalDeviceFeatures enabledFeatures{};
    VkDeviceCreateInfo deviceInfo{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.pEnabledFeatures = &enabledFeatures;
    result = vkCreateDevice(state_->physicalDevice, &deviceInfo, nullptr, &state_->device);
    if (result != VK_SUCCESS) {
        VulkanDeviceInfo info;
        info.status = "vkCreateDevice failed: " + std::to_string(result);
        shutdown();
        return info;
    }

    vkGetDeviceQueue(state_->device, state_->graphicsQueueFamily, 0, &state_->graphicsQueue);
    VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = state_->graphicsQueueFamily;
    result = vkCreateCommandPool(state_->device, &poolInfo, nullptr, &state_->commandPool);
    if (result != VK_SUCCESS) {
        VulkanDeviceInfo info;
        info.status = "vkCreateCommandPool failed: " + std::to_string(result);
        shutdown();
        return info;
    }
    state_->initialized = true;

    VkPhysicalDeviceProperties properties{};
    vkGetPhysicalDeviceProperties(state_->physicalDevice, &properties);
    VkPhysicalDeviceMemoryProperties memory{};
    vkGetPhysicalDeviceMemoryProperties(state_->physicalDevice, &memory);
    VulkanDeviceInfo info;
    info.available = true;
    info.status = "Vulkan instance, physical device, logical device, graphics queue and command pool are ready.";
    info.deviceName = properties.deviceName;
    info.apiVersion = versionString(properties.apiVersion);
    info.driverVersion = std::to_string(properties.driverVersion);
    info.vendorId = properties.vendorID;
    info.deviceType = static_cast<uint32_t>(properties.deviceType);
    info.graphicsQueueFamily = state_->graphicsQueueFamily;
    for (uint32_t i = 0; i < memory.memoryHeapCount; ++i) {
        if ((memory.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0)
            info.deviceLocalMemoryBytes += memory.memoryHeaps[i].size;
    }
    return info;
#endif
}

void VulkanBackend::shutdown() {
    if (state_ == nullptr) return;
#ifdef __ANDROID__
    if (state_->device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(state_->device);
        if (state_->commandPool != VK_NULL_HANDLE)
            vkDestroyCommandPool(state_->device, state_->commandPool, nullptr);
        vkDestroyDevice(state_->device, nullptr);
    }
    if (state_->instance != VK_NULL_HANDLE)
        vkDestroyInstance(state_->instance, nullptr);
#endif
    delete state_;
    state_ = nullptr;
}

bool VulkanBackend::isInitialized() const {
    return state_ != nullptr && state_->initialized;
}

} // namespace slime::bedrock
