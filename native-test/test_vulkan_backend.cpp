#include "vulkan_backend.hpp"

#include <cassert>
#include <iostream>
#include <vector>

int main() {
    slime::bedrock::VulkanBackend backend;
    const auto info = backend.initialize();
#ifndef __ANDROID__
    assert(!info.available);
    assert(!backend.isInitialized());
    assert(info.status.find("Android NDK build") != std::string::npos);
    const std::vector<uint32_t> noShader;
    assert(backend.attachSurface(nullptr, noShader, noShader).find("Android-only") != std::string::npos);
    assert(!backend.setMesh({}, {}));
    assert(!backend.render());
#endif
    backend.detachSurface();
    backend.shutdown();
    assert(!backend.isInitialized());
    assert(!backend.isSurfaceReady());
    std::cout << "Vulkan renderer host contract OK\n";
    return 0;
}
