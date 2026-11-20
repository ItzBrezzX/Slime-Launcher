# Slime Android Vulkan renderer

The independent voxel engine now has a native Android presentation path.

## Render path

1. Android `SurfaceView` exposes its `Surface`; JNI retains an `ANativeWindow` reference.
2. Vulkan enables `VK_KHR_surface` and `VK_KHR_android_surface`, creates a `VkAndroidSurfaceKHR`, and selects a physical device with one queue family supporting both graphics and presentation.
3. The logical device enables `VK_KHR_swapchain`; the renderer creates a FIFO swapchain, color image views, a depth attachment, render pass, framebuffers, command pool/buffer, semaphores, and a frame fence.
4. GLSL vertex/fragment shaders are compiled to Vulkan 1.0 SPIR-V by the Gradle pre-build tasks and packaged as Android assets.
5. The CPU chunk mesh is copied through JNI into Vulkan host-visible vertex and 32-bit index buffers. The graphics pipeline consumes the 7-field mesh vertex layout (position, normal, block ID).
6. A depth-tested indexed draw is submitted to the graphics/present queue and the acquired swapchain image is presented to the Android surface.

The spawn view combines all nine generated chunks into one indexed mesh, translates each chunk into world coordinates, and shades block IDs with directional face lighting. The camera supports one-finger orbit and two-finger pinch zoom. Resetting the world clears the GPU mesh and presents the empty background. Swapchain resources and presentation semaphores are recreated when the surface becomes out of date; Android surface destruction releases Vulkan and native-window handles.

## Build requirements

- Android NDK 27.0.12077973 and Vulkan loader from the Android system.
- `glslangValidator` from the `glslang-tools` package for compiling GLSL to SPIR-V. The GitHub Actions APK workflow installs and validates this compiler before Gradle runs.
- Vulkan is an optional Android feature in the manifest. Unsupported devices can still install and launch Slime; the renderer reports a Surface/device initialization error instead of silently claiming a successful render.

## Current renderer scope

Implemented: Android surface integration, present-capable graphics queue selection, swapchain, color/depth attachments, shader modules, push-constant camera, graphics pipeline, GPU vertex/index buffers, merged 3×3 spawn-region mesh, indexed drawing, orbit/zoom input, synchronization, out-of-date swapchain recovery, world-mesh clearing, and resource teardown.

Not yet implemented: chunk streaming beyond the generated spawn region, frustum/occlusion culling, greedy meshing, texture atlases, dynamic lighting/shadows, entities, player controls/physics, persistence, audio, and full Bedrock protocol/content compatibility. This is a native voxel-engine renderer and terrain prototype, not yet a playable Minecraft Bedrock replacement.

## Verification boundary

CI verifies host-side JNI compilation/fallback, shader compilation, Android NDK compilation, Kotlin compilation, unit tests, and APK packaging. It cannot prove that a particular phone's GPU/driver successfully creates a surface and presents frames. That final hardware check requires installing the APK on a Vulkan-capable Android device and opening the engine screen.
