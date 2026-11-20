package com.kingbrezz.slime.bedrock

/** JNI bridge for the Android Vulkan swapchain and chunk mesh renderer. */
object VulkanBackendNative {
    init { System.loadLibrary("slime_preloader") }

    external fun nativeAttachSurface(surface: android.view.Surface, vertexShader: ByteArray, fragmentShader: ByteArray): String
    external fun nativeSetMesh(vertices: FloatArray, indices: IntArray): Boolean
    external fun nativeClearMesh()
    external fun nativeRender(): Boolean
    external fun nativeSetCamera(yaw: Float, pitch: Float, zoom: Float)
    external fun nativeDetachSurface()
    external fun nativeShutdown()
}
