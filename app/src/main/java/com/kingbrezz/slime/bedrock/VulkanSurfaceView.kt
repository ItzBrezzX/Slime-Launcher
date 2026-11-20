package com.kingbrezz.slime.bedrock

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Owns Android Surface lifecycle and presents the current chunk mesh through Vulkan. */
class VulkanSurfaceView(
    context: Context,
    private val onStatus: (String) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val appContext = context
    private var mesh: ChunkMeshData? = null
    private var attached = false

    init {
        holder.addCallback(this)
        setBackgroundColor(android.graphics.Color.rgb(9, 14, 19))
    }

    fun updateMesh(data: ChunkMeshData) {
        mesh = data
        if (!attached) {
            onStatus("Mesh CPU siap; menunggu Vulkan Surface.")
            return
        }
        uploadAndDraw()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Wait for surfaceChanged so the swapchain uses the final buffer dimensions.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        runCatching {
            val vertex = appContext.assets.open("shaders/bedrock.vert.spv").use { it.readBytes() }
            val fragment = appContext.assets.open("shaders/bedrock.frag.spv").use { it.readBytes() }
            val result = VulkanBackendNative.nativeAttachSurface(holder.surface, vertex, fragment)
            attached = result.startsWith("Vulkan renderer ready")
            onStatus(result)
            if (attached && mesh != null) uploadAndDraw()
        }.onFailure {
            attached = false
            onStatus("Vulkan Surface error: ${it.message}")
        }
    }

    private fun uploadAndDraw() {
        val data = mesh ?: return
        runCatching {
            check(VulkanBackendNative.nativeSetMesh(data.vertices, data.indices)) {
                "GPU vertex/index buffer upload failed."
            }
            check(VulkanBackendNative.nativeRender()) {
                "Vulkan draw/present failed. Surface may need recreation."
            }
            onStatus("Vulkan: ${data.vertexCount} vertices · ${data.faceCount} faces uploaded; indexed draw presented.")
        }.onFailure {
            onStatus("Vulkan render error: ${it.message}")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        attached = false
        VulkanBackendNative.nativeDetachSurface()
        onStatus("Vulkan Surface released.")
    }
}
