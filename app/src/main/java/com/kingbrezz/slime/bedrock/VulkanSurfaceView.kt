package com.kingbrezz.slime.bedrock

import android.content.Context
import android.view.MotionEvent
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
    private var cameraYaw = 0.7853982f
    private var cameraPitch = 0.65f
    private var cameraZoom = 1.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPinchDistance = 0f

    init {
        holder.addCallback(this)
        setBackgroundColor(android.graphics.Color.rgb(9, 14, 19))
        isClickable = true
    }

    fun updateMesh(data: ChunkMeshData) {
        mesh = data
        if (!attached) {
            onStatus("Mesh CPU siap; menunggu Vulkan Surface.")
            return
        }
        uploadAndDraw()
    }

    fun clearMesh() {
        mesh = null
        if (!attached) return
        runCatching {
            VulkanBackendNative.nativeClearMesh()
            applyCamera()
            check(VulkanBackendNative.nativeRender()) { "Vulkan clear frame failed." }
            onStatus("Vulkan Surface aktif; world mesh dikosongkan.")
        }.onFailure {
            onStatus("Vulkan clear error: ${it.message}")
        }
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
            if (attached) {
                applyCamera()
                if (mesh != null) uploadAndDraw()
            }
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
            drawCurrentFrame(data)
        }.onFailure {
            onStatus("Vulkan render error: ${it.message}")
        }
    }

    private fun applyCamera() {
        VulkanBackendNative.nativeSetCamera(cameraYaw, cameraPitch, cameraZoom)
    }

    private fun drawCurrentFrame(data: ChunkMeshData) {
        applyCamera()
        check(VulkanBackendNative.nativeRender()) {
            "Vulkan draw/present failed. Surface may need recreation."
        }
        onStatus("Vulkan: ${data.vertexCount} vertices · ${data.faceCount} faces · 3×3 chunks; drag to rotate, pinch to zoom.")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!attached) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                lastPinchDistance = 0f
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    lastPinchDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (lastPinchDistance > 0f && distance > 0f) {
                        cameraZoom = (cameraZoom * distance / lastPinchDistance).coerceIn(0.55f, 2.5f)
                    }
                    lastPinchDistance = distance
                } else {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    cameraYaw += dx * 0.008f
                    cameraPitch = (cameraPitch + dy * 0.006f).coerceIn(0.15f, 1.35f)
                }
                lastTouchX = event.x
                lastTouchY = event.y
                mesh?.let { data ->
                    runCatching { drawCurrentFrame(data) }
                        .onFailure { onStatus("Vulkan camera error: ${it.message}") }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastPinchDistance = 0f
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastPinchDistance = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        attached = false
        VulkanBackendNative.nativeDetachSurface()
        onStatus("Vulkan Surface released.")
    }
}
