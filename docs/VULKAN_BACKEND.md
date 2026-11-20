# Slime Vulkan backend bootstrap

This stage adds an Android Vulkan device bootstrap for Slime's independent native voxel engine.

## Runtime behavior

- Vulkan is an optional Android feature; devices without a Vulkan loader or graphics-capable physical device can still launch the app.
- The backend creates a Vulkan 1.0 instance, enumerates physical devices, selects a graphics-capable queue (preferring discrete, then integrated GPUs), creates a logical device, retrieves its graphics queue, and creates a resettable command pool.
- The diagnostic Activity reports the selected device name, Vulkan API version, driver version, vendor ID, device type, device-local heap size, and graphics queue-family index.
- The backend can be initialized more than once and explicitly shut down. Device and instance handles are destroyed in dependency order.
- The Android NDK's Vulkan headers and system `libvulkan` are used; no proprietary game runtime or `libminecraftpe.so` is loaded by this backend.

## Build integration

The Android CMake target compiles the Vulkan backend and JNI bridge and links the platform Vulkan loader. The manifest declares Vulkan 1.0 as an optional feature, so non-Vulkan devices are not filtered out of installation.

The host-native test build compiles the non-Android fallback and verifies that it reports Vulkan as unavailable rather than attempting to call Android Vulkan APIs.

## Scope boundary

This is device/queue/command-pool initialization, not a complete renderer. It does not yet create an Android surface or swapchain, compile/load SPIR-V shaders, allocate GPU vertex/index buffers, submit draw commands, or present frames. The existing chunk mesh remains CPU-side until those renderer stages are implemented and tested on Vulkan-capable hardware.
