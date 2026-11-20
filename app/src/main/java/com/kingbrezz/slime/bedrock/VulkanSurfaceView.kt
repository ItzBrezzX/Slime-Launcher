package com.kingbrezz.slime.bedrock

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/** SurfaceView bridge: owns the Android Surface lifecycle and submits the current chunk mesh. */
class VulkanSurfaceView(
    context: Context,
    private val onStatus: (String) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {
    private var shaders: Pair<ByteArray, ByteArray>? = null
    private var mesh: ChunkMeshData? = null
    private var attached = false

    init {
        holder.addCallback(this)
        setBackgroundColor(android.graphics.Color.rgb(9, 14, 19))
    }

    fun updateMesh(data: ChunkMeshData) {
        mesh = data
        if (!attached) {
            onStatus("Mesh siap; menunggu Vulkan Surface.")
            return
        }
        uploadAndDraw()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        attach(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) attach(holder)
    }

    private fun attach(holder: SurfaceHolder) {
        runCatching {
            val vertex = context.assets.open("shaders/bedrock.vert.spv").use { it.readBytes() }
            val fragment = context.assets.open("shaders/bedrock.frag.spv").use { it.readBytes() }
            shaders = vertex to fragment
            val status = VulkanBackendNative.nativeAttachSurface(holder.surface, vertex, fragment)
            attached = status.startsWith("Vulkan renderer ready")
            onStatus(status)
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
            onStatus("Vulkan: mesh uploaded to GPU · ${data.vertexCount} vertices · ${data.faceCount} faces · indexed draw presented.")
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
