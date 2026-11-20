#include "vulkan_backend.hpp"

#include <cassert>
#include <iostream>

int main() {
    slime::bedrock::VulkanBackend backend;
    const auto info = backend.initialize();
#ifndef __ANDROID__
    assert(!info.available);
    assert(!backend.isInitialized());
    assert(info.status.find("Android NDK build") != std::string::npos);
#endif
    backend.shutdown();
    assert(!backend.isInitialized());
    std::cout << "Vulkan backend host contract OK\n";
    return 0;
}
