#include "vulkan_backend.hpp"

#include <algorithm>
#include <cstdio>
#include <cstddef>
#include <cmath>
#include <cstring>
#include <new>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/native_window.h>
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <vulkan/vulkan.h>
#endif

namespace slime::bedrock {
#ifdef __ANDROID__
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
    ANativeWindow* window = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    uint32_t queueFamily = 0;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat colorFormat = VK_FORMAT_UNDEFINED;
    VkExtent2D extent{};
    std::vector<VkImage> swapImages;
    std::vector<VkImageView> swapViews;
    VkFormat depthFormat = VK_FORMAT_D32_SFLOAT;
    VkImage depthImage = VK_NULL_HANDLE;
    VkDeviceMemory depthMemory = VK_NULL_HANDLE;
    VkImageView depthView = VK_NULL_HANDLE;
    VkRenderPass renderPass = VK_NULL_HANDLE;
    std::vector<VkFramebuffer> framebuffers;
    VkShaderModule vertexModule = VK_NULL_HANDLE;
    VkShaderModule fragmentModule = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkBuffer vertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory vertexMemory = VK_NULL_HANDLE;
    VkBuffer indexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory indexMemory = VK_NULL_HANDLE;
    uint32_t indexCount = 0;
    float cameraYaw = 0.785398163F;
    float cameraPitch = 0.65F;
    float cameraZoom = 1.0F;
    VkSemaphore imageAvailable = VK_NULL_HANDLE;
    VkSemaphore renderFinished = VK_NULL_HANDLE;
    VkFence inFlight = VK_NULL_HANDLE;
    bool initialized = false;
    bool surfaceReady = false;
    std::string lastError;

    uint32_t memoryType(uint32_t bits, VkMemoryPropertyFlags flags) const {
        VkPhysicalDeviceMemoryProperties props{};
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, &props);
        for (uint32_t i = 0; i < props.memoryTypeCount; ++i) {
            if ((bits & (1u << i)) != 0 &&
                (props.memoryTypes[i].propertyFlags & flags) == flags) return i;
        }
        return UINT32_MAX;
    }

    bool createBuffer(VkDeviceSize size, VkBufferUsageFlags usage,
                      VkBuffer& buffer, VkDeviceMemory& memory) {
        VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        info.size = size;
        info.usage = usage;
        info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(device, &info, nullptr, &buffer) != VK_SUCCESS) return false;
        VkMemoryRequirements req{};
        vkGetBufferMemoryRequirements(device, buffer, &req);
        const uint32_t type = memoryType(req.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (type == UINT32_MAX) return false;
        VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        alloc.allocationSize = req.size;
        alloc.memoryTypeIndex = type;
        if (vkAllocateMemory(device, &alloc, nullptr, &memory) != VK_SUCCESS) return false;
        return vkBindBufferMemory(device, buffer, memory, 0) == VK_SUCCESS;
    }

    void destroyMesh() {
        if (device == VK_NULL_HANDLE) return;
        if (vertexBuffer) vkDestroyBuffer(device, vertexBuffer, nullptr);
        if (vertexMemory) vkFreeMemory(device, vertexMemory, nullptr);
        if (indexBuffer) vkDestroyBuffer(device, indexBuffer, nullptr);
        if (indexMemory) vkFreeMemory(device, indexMemory, nullptr);
        vertexBuffer = VK_NULL_HANDLE; vertexMemory = VK_NULL_HANDLE;
        indexBuffer = VK_NULL_HANDLE; indexMemory = VK_NULL_HANDLE;
        indexCount = 0;
    }

    void destroySwapchain() {
        if (device == VK_NULL_HANDLE) return;
        vkDeviceWaitIdle(device);
        for (auto fb : framebuffers) vkDestroyFramebuffer(device, fb, nullptr);
        framebuffers.clear();
        if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
        if (pipelineLayout) vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
        if (renderPass) vkDestroyRenderPass(device, renderPass, nullptr);
        if (depthView) vkDestroyImageView(device, depthView, nullptr);
        if (depthImage) vkDestroyImage(device, depthImage, nullptr);
        if (depthMemory) vkFreeMemory(device, depthMemory, nullptr);
        for (auto view : swapViews) vkDestroyImageView(device, view, nullptr);
        if (swapchain) vkDestroySwapchainKHR(device, swapchain, nullptr);
        framebuffers.clear(); swapViews.clear(); swapImages.clear();
        pipeline = VK_NULL_HANDLE; pipelineLayout = VK_NULL_HANDLE;
        renderPass = VK_NULL_HANDLE; depthView = VK_NULL_HANDLE;
        depthImage = VK_NULL_HANDLE; depthMemory = VK_NULL_HANDLE;
        swapchain = VK_NULL_HANDLE;
    }

    void destroyAll() {
        if (device != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(device);
            destroyMesh();
            destroySwapchain();
            if (vertexModule) vkDestroyShaderModule(device, vertexModule, nullptr);
            if (fragmentModule) vkDestroyShaderModule(device, fragmentModule, nullptr);
            if (imageAvailable) vkDestroySemaphore(device, imageAvailable, nullptr);
            if (renderFinished) vkDestroySemaphore(device, renderFinished, nullptr);
            if (inFlight) vkDestroyFence(device, inFlight, nullptr);
            if (commandPool) vkDestroyCommandPool(device, commandPool, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (surface && instance) vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance) vkDestroyInstance(instance, nullptr);
        if (window) ANativeWindow_release(window);
        window = nullptr; instance = VK_NULL_HANDLE; surface = VK_NULL_HANDLE;
        physicalDevice = VK_NULL_HANDLE; device = VK_NULL_HANDLE;
        graphicsQueue = VK_NULL_HANDLE; commandPool = VK_NULL_HANDLE;
        commandBuffer = VK_NULL_HANDLE; initialized = false; surfaceReady = false;
    }

    bool createDepth() {
        VkFormatProperties props{};
        vkGetPhysicalDeviceFormatProperties(physicalDevice, depthFormat, &props);
        if ((props.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) == 0) {
            depthFormat = VK_FORMAT_D16_UNORM;
            vkGetPhysicalDeviceFormatProperties(physicalDevice, depthFormat, &props);
            if ((props.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) == 0)
                return false;
        }
        VkImageCreateInfo image{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        image.imageType = VK_IMAGE_TYPE_2D;
        image.format = depthFormat;
        image.extent = {extent.width, extent.height, 1};
        image.mipLevels = 1; image.arrayLayers = 1;
        image.samples = VK_SAMPLE_COUNT_1_BIT;
        image.tiling = VK_IMAGE_TILING_OPTIMAL;
        image.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
        image.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateImage(device, &image, nullptr, &depthImage) != VK_SUCCESS) return false;
        VkMemoryRequirements req{};
        vkGetImageMemoryRequirements(device, depthImage, &req);
        const uint32_t type = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (type == UINT32_MAX) return false;
        VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        alloc.allocationSize = req.size; alloc.memoryTypeIndex = type;
        if (vkAllocateMemory(device, &alloc, nullptr, &depthMemory) != VK_SUCCESS) return false;
        if (vkBindImageMemory(device, depthImage, depthMemory, 0) != VK_SUCCESS) return false;
        VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        view.image = depthImage; view.viewType = VK_IMAGE_VIEW_TYPE_2D;
        view.format = depthFormat;
        view.subresourceRange.aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
        view.subresourceRange.levelCount = 1; view.subresourceRange.layerCount = 1;
        return vkCreateImageView(device, &view, nullptr, &depthView) == VK_SUCCESS;
    }

    bool createRenderPass() {
        VkAttachmentDescription attachments[2]{};
        attachments[0].format = colorFormat;
        attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
        attachments[0].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachments[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachments[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[0].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        attachments[0].finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        attachments[1].format = depthFormat;
        attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
        attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachments[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        VkAttachmentReference colorRef{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkAttachmentReference depthRef{1, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1; subpass.pColorAttachments = &colorRef;
        subpass.pDepthStencilAttachment = &depthRef;
        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL; dependency.dstSubpass = 0;
        dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT |
                                  VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
        dependency.dstStageMask = dependency.srcStageMask;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT |
                                   VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        VkRenderPassCreateInfo info{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        info.attachmentCount = 2; info.pAttachments = attachments;
        info.subpassCount = 1; info.pSubpasses = &subpass;
        info.dependencyCount = 1; info.pDependencies = &dependency;
        return vkCreateRenderPass(device, &info, nullptr, &renderPass) == VK_SUCCESS;
    }

    bool createPipeline(const std::vector<uint32_t>& vert, const std::vector<uint32_t>& frag) {
        VkShaderModuleCreateInfo shader{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        shader.codeSize = vert.size() * sizeof(uint32_t); shader.pCode = vert.data();
        if (vkCreateShaderModule(device, &shader, nullptr, &vertexModule) != VK_SUCCESS) return false;
        shader.codeSize = frag.size() * sizeof(uint32_t); shader.pCode = frag.data();
        if (vkCreateShaderModule(device, &shader, nullptr, &fragmentModule) != VK_SUCCESS) return false;
        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT; stages[0].module = vertexModule; stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module = fragmentModule; stages[1].pName = "main";
        VkVertexInputBindingDescription binding{0, sizeof(MeshVertex), VK_VERTEX_INPUT_RATE_VERTEX};
        VkVertexInputAttributeDescription attrs[3] = {
            {0, 0, VK_FORMAT_R32G32B32_SFLOAT, static_cast<uint32_t>(offsetof(MeshVertex, x))},
            {1, 0, VK_FORMAT_R32G32B32_SFLOAT, static_cast<uint32_t>(offsetof(MeshVertex, nx))},
            {2, 0, VK_FORMAT_R32_UINT, static_cast<uint32_t>(offsetof(MeshVertex, blockId))}
        };
        VkPipelineVertexInputStateCreateInfo vertex{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        vertex.vertexBindingDescriptionCount = 1; vertex.pVertexBindingDescriptions = &binding;
        vertex.vertexAttributeDescriptionCount = 3; vertex.pVertexAttributeDescriptions = attrs;
        VkPipelineInputAssemblyStateCreateInfo assembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
        assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo viewport{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewport.viewportCount = 1; viewport.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        raster.polygonMode = VK_POLYGON_MODE_FILL; raster.cullMode = VK_CULL_MODE_NONE;
        raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE; raster.lineWidth = 1.0F;
        VkPipelineMultisampleStateCreateInfo multisample{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineDepthStencilStateCreateInfo depth{VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
        depth.depthTestEnable = VK_TRUE; depth.depthWriteEnable = VK_TRUE;
        depth.depthCompareOp = VK_COMPARE_OP_LESS;
        VkPipelineColorBlendAttachmentState blend{};
        blend.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                               VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendStateCreateInfo color{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
        color.attachmentCount = 1; color.pAttachments = &blend;
        VkDynamicState dynamics[2] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamic{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
        dynamic.dynamicStateCount = 2; dynamic.pDynamicStates = dynamics;
        VkPushConstantRange cameraRange{};
        cameraRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
        cameraRange.offset = 0;
        cameraRange.size = sizeof(float) * 4;
        VkPipelineLayoutCreateInfo layout{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layout.pushConstantRangeCount = 1;
        layout.pPushConstantRanges = &cameraRange;
        if (vkCreatePipelineLayout(device, &layout, nullptr, &pipelineLayout) != VK_SUCCESS) return false;
        VkGraphicsPipelineCreateInfo info{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        info.stageCount = 2; info.pStages = stages;
        info.pVertexInputState = &vertex; info.pInputAssemblyState = &assembly;
        info.pViewportState = &viewport; info.pRasterizationState = &raster;
        info.pMultisampleState = &multisample; info.pDepthStencilState = &depth;
        info.pColorBlendState = &color; info.pDynamicState = &dynamic;
        info.layout = pipelineLayout; info.renderPass = renderPass; info.subpass = 0;
        return vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline) == VK_SUCCESS;
    }

    bool createSwapchain(const std::vector<uint32_t>& vert, const std::vector<uint32_t>& frag) {
        VkSurfaceCapabilitiesKHR caps{};
        if (vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, &caps) != VK_SUCCESS) return false;
        uint32_t formatCount = 0;
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, &formatCount, nullptr);
        if (!formatCount) return false;
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, &formatCount, formats.data());
        VkSurfaceFormatKHR chosen = formats[0];
        for (const auto& f : formats) {
            if (f.format == VK_FORMAT_R8G8B8A8_UNORM || f.format == VK_FORMAT_B8G8R8A8_UNORM) {
                chosen = f; break;
            }
        }
        colorFormat = chosen.format;
        if (caps.currentExtent.width != UINT32_MAX) extent = caps.currentExtent;
        else {
            extent.width = std::clamp(static_cast<uint32_t>(ANativeWindow_getWidth(window)),
                                      caps.minImageExtent.width, caps.maxImageExtent.width);
            extent.height = std::clamp(static_cast<uint32_t>(ANativeWindow_getHeight(window)),
                                       caps.minImageExtent.height, caps.maxImageExtent.height);
        }
        uint32_t imageCount = caps.minImageCount + 1;
        if (caps.maxImageCount && imageCount > caps.maxImageCount) imageCount = caps.maxImageCount;
        VkSwapchainCreateInfoKHR swap{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        swap.surface = surface; swap.minImageCount = imageCount;
        swap.imageFormat = chosen.format; swap.imageColorSpace = chosen.colorSpace;
        swap.imageExtent = extent; swap.imageArrayLayers = 1;
        swap.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        swap.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        swap.preTransform = caps.currentTransform;
        if ((caps.supportedUsageFlags & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) == 0) return false;
        if ((caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) != 0)
            swap.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        else if ((caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR) != 0)
            swap.compositeAlpha = VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
        else if ((caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR) != 0)
            swap.compositeAlpha = VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
        else if ((caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR) != 0)
            swap.compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
        else return false;
        swap.presentMode = VK_PRESENT_MODE_FIFO_KHR; swap.clipped = VK_TRUE;
        if (vkCreateSwapchainKHR(device, &swap, nullptr, &swapchain) != VK_SUCCESS) return false;
        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr);
        swapImages.resize(imageCount);
        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, swapImages.data());
        for (auto image : swapImages) {
            VkImageViewCreateInfo view{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            view.image = image; view.viewType = VK_IMAGE_VIEW_TYPE_2D; view.format = colorFormat;
            view.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            view.subresourceRange.levelCount = 1; view.subresourceRange.layerCount = 1;
            VkImageView handle = VK_NULL_HANDLE;
            if (vkCreateImageView(device, &view, nullptr, &handle) != VK_SUCCESS) return false;
            swapViews.push_back(handle);
        }
        if (!createDepth() || !createRenderPass() || !createPipeline(vert, frag)) return false;
        for (auto view : swapViews) {
            VkImageView attachments[2] = {view, depthView};
            VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
            fb.renderPass = renderPass; fb.attachmentCount = 2; fb.pAttachments = attachments;
            fb.width = extent.width; fb.height = extent.height; fb.layers = 1;
            VkFramebuffer handle = VK_NULL_HANDLE;
            if (vkCreateFramebuffer(device, &fb, nullptr, &handle) != VK_SUCCESS) return false;
            framebuffers.push_back(handle);
        }
        return true;
    }
};
#else
struct VulkanBackend::State {
    bool initialized = false;
    bool surfaceReady = false;
};
#endif

VulkanBackend::~VulkanBackend() { shutdown(); }

VulkanDeviceInfo VulkanBackend::initialize() {
    VulkanDeviceInfo info;
#ifndef __ANDROID__
    info.status = "Vulkan rendering requires an Android NDK build and a native Surface.";
#else
    info.status = "Vulkan renderer is ready to attach to an Android Surface.";
#endif
    return info;
}

std::string VulkanBackend::attachSurface(void* nativeWindow,
                                         const std::vector<uint32_t>& vertexShader,
                                         const std::vector<uint32_t>& fragmentShader) {
#ifndef __ANDROID__
    (void)nativeWindow; (void)vertexShader; (void)fragmentShader;
    return "Vulkan surface rendering is Android-only.";
#else
    detachSurface();
    if (!nativeWindow || vertexShader.empty() || fragmentShader.empty())
        return "Surface or SPIR-V shader data is empty.";
    state_ = new (std::nothrow) State();
    if (!state_) return "Unable to allocate Vulkan renderer state.";
    state_->window = static_cast<ANativeWindow*>(nativeWindow);
    state_->instance = VK_NULL_HANDLE;
    const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "Slime Bedrock Engine";
    app.applicationVersion = VK_MAKE_VERSION(0, 3, 0);
    app.pEngineName = "Slime Vulkan Voxel Renderer";
    app.engineVersion = VK_MAKE_VERSION(0, 3, 0);
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo instance{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    instance.pApplicationInfo = &app;
    instance.enabledExtensionCount = 2; instance.ppEnabledExtensionNames = extensions;
    VkResult result = vkCreateInstance(&instance, nullptr, &state_->instance);
    if (result != VK_SUCCESS) { shutdown(); return "vkCreateInstance failed: " + std::to_string(result); }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
    surfaceInfo.window = state_->window;
    result = vkCreateAndroidSurfaceKHR(state_->instance, &surfaceInfo, nullptr, &state_->surface);
    if (result != VK_SUCCESS) { shutdown(); return "vkCreateAndroidSurfaceKHR failed: " + std::to_string(result); }

    uint32_t count = 0;
    result = vkEnumeratePhysicalDevices(state_->instance, &count, nullptr);
    if (result != VK_SUCCESS || count == 0) { shutdown(); return "No Vulkan physical devices found."; }
    std::vector<VkPhysicalDevice> devices(count);
    result = vkEnumeratePhysicalDevices(state_->instance, &count, devices.data());
    if (result != VK_SUCCESS) { shutdown(); return "Physical-device enumeration failed."; }
    int bestScore = -1;
    for (auto physical : devices) {
        uint32_t extensionCount = 0;
        vkEnumerateDeviceExtensionProperties(physical, nullptr, &extensionCount, nullptr);
        std::vector<VkExtensionProperties> availableExtensions(extensionCount);
        vkEnumerateDeviceExtensionProperties(physical, nullptr, &extensionCount, availableExtensions.data());
        bool hasSwapchain = false;
        for (const auto& extension : availableExtensions) {
            if (std::strcmp(extension.extensionName, VK_KHR_SWAPCHAIN_EXTENSION_NAME) == 0)
                hasSwapchain = true;
        }
        if (!hasSwapchain) continue;
        uint32_t queuesCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(physical, &queuesCount, nullptr);
        std::vector<VkQueueFamilyProperties> queues(queuesCount);
        vkGetPhysicalDeviceQueueFamilyProperties(physical, &queuesCount, queues.data());
        VkPhysicalDeviceProperties props{};
        vkGetPhysicalDeviceProperties(physical, &props);
        for (uint32_t q = 0; q < queuesCount; ++q) {
            VkBool32 present = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(physical, q, state_->surface, &present);
            if (!present || queues[q].queueCount == 0 ||
                (queues[q].queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0) continue;
            const int score = props.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ? 3 :
                              props.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU ? 2 : 1;
            if (score > bestScore) {
                bestScore = score; state_->physicalDevice = physical; state_->queueFamily = q;
            }
        }
    }
    if (state_->physicalDevice == VK_NULL_HANDLE) { shutdown(); return "No graphics queue supports presentation to this Android Surface."; }

    const float priority = 1.0F;
    VkDeviceQueueCreateInfo queue{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queue.queueFamilyIndex = state_->queueFamily; queue.queueCount = 1; queue.pQueuePriorities = &priority;
    const char* deviceExtensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkPhysicalDeviceFeatures features{};
    VkDeviceCreateInfo device{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    device.queueCreateInfoCount = 1; device.pQueueCreateInfos = &queue;
    device.enabledExtensionCount = 1; device.ppEnabledExtensionNames = deviceExtensions;
    device.pEnabledFeatures = &features;
    result = vkCreateDevice(state_->physicalDevice, &device, nullptr, &state_->device);
    if (result != VK_SUCCESS) { shutdown(); return "vkCreateDevice with VK_KHR_swapchain failed: " + std::to_string(result); }
    vkGetDeviceQueue(state_->device, state_->queueFamily, 0, &state_->graphicsQueue);
    VkCommandPoolCreateInfo pool{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    pool.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pool.queueFamilyIndex = state_->queueFamily;
    result = vkCreateCommandPool(state_->device, &pool, nullptr, &state_->commandPool);
    if (result != VK_SUCCESS) { shutdown(); return "vkCreateCommandPool failed."; }
    VkCommandBufferAllocateInfo cmd{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cmd.commandPool = state_->commandPool; cmd.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; cmd.commandBufferCount = 1;
    if (vkAllocateCommandBuffers(state_->device, &cmd, &state_->commandBuffer) != VK_SUCCESS) { shutdown(); return "Command-buffer allocation failed."; }
    VkSemaphoreCreateInfo semaphore{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    if (vkCreateSemaphore(state_->device, &semaphore, nullptr, &state_->imageAvailable) != VK_SUCCESS ||
        vkCreateSemaphore(state_->device, &semaphore, nullptr, &state_->renderFinished) != VK_SUCCESS) { shutdown(); return "Vulkan semaphore creation failed."; }
    VkFenceCreateInfo fence{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    fence.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    if (vkCreateFence(state_->device, &fence, nullptr, &state_->inFlight) != VK_SUCCESS) { shutdown(); return "Vulkan frame fence creation failed."; }
    if (!state_->createSwapchain(vertexShader, fragmentShader)) { shutdown(); return "Swapchain, depth buffer, render pass, framebuffer or graphics pipeline creation failed."; }
    state_->initialized = true; state_->surfaceReady = true;
    VkPhysicalDeviceProperties props{}; vkGetPhysicalDeviceProperties(state_->physicalDevice, &props);
    return "Vulkan renderer ready: " + std::string(props.deviceName) + " · swapchain " +
           std::to_string(state_->extent.width) + "x" + std::to_string(state_->extent.height) +
           " · graphics/present queue " + std::to_string(state_->queueFamily);
#endif
}

bool VulkanBackend::setMesh(const std::vector<MeshVertex>& vertices,
                            const std::vector<uint32_t>& indices) {
#ifndef __ANDROID__
    (void)vertices; (void)indices; return false;
#else
    if (!state_ || !state_->initialized || vertices.empty() || indices.empty() ||
        indices.size() > UINT32_MAX) return false;
    for (uint32_t index : indices) if (index >= vertices.size()) return false;
    vkDeviceWaitIdle(state_->device);
    state_->destroyMesh();
    if (!state_->createBuffer(vertices.size() * sizeof(MeshVertex),
                              VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                              state_->vertexBuffer, state_->vertexMemory)) {
        state_->destroyMesh(); return false;
    }
    if (!state_->createBuffer(indices.size() * sizeof(uint32_t),
                              VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                              state_->indexBuffer, state_->indexMemory)) {
        state_->destroyMesh(); return false;
    }
    void* mapped = nullptr;
    if (vkMapMemory(state_->device, state_->vertexMemory, 0,
                    vertices.size() * sizeof(MeshVertex), 0, &mapped) != VK_SUCCESS) {
        state_->destroyMesh(); return false;
    }
    std::memcpy(mapped, vertices.data(), vertices.size() * sizeof(MeshVertex));
    vkUnmapMemory(state_->device, state_->vertexMemory);
    if (vkMapMemory(state_->device, state_->indexMemory, 0,
                    indices.size() * sizeof(uint32_t), 0, &mapped) != VK_SUCCESS) {
        state_->destroyMesh(); return false;
    }
    std::memcpy(mapped, indices.data(), indices.size() * sizeof(uint32_t));
    vkUnmapMemory(state_->device, state_->indexMemory);
    state_->indexCount = static_cast<uint32_t>(indices.size());
    return true;
#endif
}

bool VulkanBackend::render() {
#ifndef __ANDROID__
    return false;
#else
    if (!state_ || !state_->surfaceReady || !state_->pipeline || !state_->indexCount) return false;
    VkResult result = vkWaitForFences(state_->device, 1, &state_->inFlight, VK_TRUE, UINT64_MAX);
    if (result != VK_SUCCESS) return false;
    uint32_t imageIndex = 0;
    result = vkAcquireNextImageKHR(state_->device, state_->swapchain, UINT64_MAX,
                                   state_->imageAvailable, VK_NULL_HANDLE, &imageIndex);
    if (result == VK_ERROR_OUT_OF_DATE_KHR) return false;
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) return false;
    vkResetCommandBuffer(state_->commandBuffer, 0);
    VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    if (vkBeginCommandBuffer(state_->commandBuffer, &begin) != VK_SUCCESS) return false;
    VkClearValue clear[2]{};
    clear[0].color = {{0.035F, 0.055F, 0.075F, 1.0F}};
    clear[1].depthStencil = {1.0F, 0};
    VkRenderPassBeginInfo pass{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    pass.renderPass = state_->renderPass; pass.framebuffer = state_->framebuffers[imageIndex];
    pass.renderArea.extent = state_->extent; pass.clearValueCount = 2; pass.pClearValues = clear;
    vkCmdBeginRenderPass(state_->commandBuffer, &pass, VK_SUBPASS_CONTENTS_INLINE);
    VkViewport viewport{0.0F, 0.0F, static_cast<float>(state_->extent.width),
                        static_cast<float>(state_->extent.height), 0.0F, 1.0F};
    VkRect2D scissor{{0,0}, state_->extent};
    vkCmdSetViewport(state_->commandBuffer, 0, 1, &viewport);
    vkCmdSetScissor(state_->commandBuffer, 0, 1, &scissor);
    vkCmdBindPipeline(state_->commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, state_->pipeline);
    const float aspect = state_->extent.height > 0
        ? static_cast<float>(state_->extent.width) / static_cast<float>(state_->extent.height)
        : 1.0F;
    const float camera[4] = {state_->cameraYaw, state_->cameraPitch,
                             state_->cameraZoom, aspect};
    vkCmdPushConstants(state_->commandBuffer, state_->pipelineLayout,
                       VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(camera), camera);
    const VkDeviceSize offset = 0;
    vkCmdBindVertexBuffers(state_->commandBuffer, 0, 1, &state_->vertexBuffer, &offset);
    vkCmdBindIndexBuffer(state_->commandBuffer, state_->indexBuffer, 0, VK_INDEX_TYPE_UINT32);
    vkCmdDrawIndexed(state_->commandBuffer, state_->indexCount, 1, 0, 0, 0);
    vkCmdEndRenderPass(state_->commandBuffer);
    if (vkEndCommandBuffer(state_->commandBuffer) != VK_SUCCESS) return false;
    vkResetFences(state_->device, 1, &state_->inFlight);
    const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submit.waitSemaphoreCount = 1; submit.pWaitSemaphores = &state_->imageAvailable;
    submit.pWaitDstStageMask = &waitStage;
    submit.commandBufferCount = 1; submit.pCommandBuffers = &state_->commandBuffer;
    submit.signalSemaphoreCount = 1; submit.pSignalSemaphores = &state_->renderFinished;
    if (vkQueueSubmit(state_->graphicsQueue, 1, &submit, state_->inFlight) != VK_SUCCESS) return false;
    VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
    present.waitSemaphoreCount = 1; present.pWaitSemaphores = &state_->renderFinished;
    present.swapchainCount = 1; present.pSwapchains = &state_->swapchain;
    present.pImageIndices = &imageIndex;
    result = vkQueuePresentKHR(state_->graphicsQueue, &present);
    // A single present semaphore is reused; wait for the present queue to consume it.
    vkQueueWaitIdle(state_->graphicsQueue);
    return result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR;
#endif
}

void VulkanBackend::setCamera(float yaw, float pitch, float zoom) {
#ifdef __ANDROID__
    if (!state_) return;
    if (!std::isfinite(yaw) || !std::isfinite(pitch) || !std::isfinite(zoom)) return;
    state_->cameraYaw = yaw;
    state_->cameraPitch = std::clamp(pitch, 0.15F, 1.35F);
    state_->cameraZoom = std::clamp(zoom, 0.55F, 2.5F);
#else
    (void)yaw; (void)pitch; (void)zoom;
#endif
}

void VulkanBackend::detachSurface() {
#ifdef __ANDROID__
    if (state_) state_->destroyAll();
#endif
    delete state_;
    state_ = nullptr;
}
void VulkanBackend::shutdown() { detachSurface(); }
bool VulkanBackend::isInitialized() const {
#ifdef __ANDROID__
    return state_ && state_->initialized;
#else
    return false;
#endif
}
bool VulkanBackend::isSurfaceReady() const {
#ifdef __ANDROID__
    return state_ && state_->surfaceReady;
#else
    return false;
#endif
}
} // namespace slime::bedrock
