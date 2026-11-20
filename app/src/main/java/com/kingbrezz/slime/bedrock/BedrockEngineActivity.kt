package com.kingbrezz.slime.bedrock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.SlimeTheme

class BedrockEngineActivity : ComponentActivity() {
    private val status = mutableStateOf("Engine belum diinisialisasi.")
    private val stats = mutableStateOf<ChunkStats?>(null)
    private val meshInfo = mutableStateOf("Mesh belum dibuat.")
    private val renderStatus = mutableStateOf("Menunggu Android Surface untuk Vulkan.")
    private val seed = mutableStateOf(System.currentTimeMillis())
    private val renderer = mutableStateOf<VulkanSurfaceView?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlimeTheme {
                Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Bg)
                            .verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Horizon Bedrock Engine", color = Fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Independent native voxel runtime · Vulkan renderer", color = SlimeGreen, fontSize = 13.sp)
                        Text(
                            "Engine independen. Tidak memuat libminecraftpe.so atau menjalankan runtime Mojang.",
                            color = Muted,
                            fontSize = 13.sp,
                        )
                        Text("Seed: ${seed.value}", color = Fg, fontSize = 13.sp)
                        Text("Chunk aktif: ${runCatching { BedrockEngineNative.loadedChunkCount() }.getOrDefault(0)}", color = Fg, fontSize = 13.sp)
                        stats.value?.let { chunk ->
                            Text("Solid blocks: ${chunk.solidBlocks}", color = Fg, fontSize = 13.sp)
                            Text("Water blocks: ${chunk.waterBlocks}", color = Fg, fontSize = 13.sp)
                            Text("Visible faces: ${chunk.visibleFaces}", color = Fg, fontSize = 13.sp)
                            Text("Terrain height: ${chunk.minHeight}–${chunk.maxHeight}", color = Fg, fontSize = 13.sp)
                            Text(meshInfo.value, color = Fg, fontSize = 12.sp)
                        }
                        AndroidView(
                            factory = { context ->
                                VulkanSurfaceView(context) { renderStatus.value = it }
                                    .also { renderer.value = it }
                            },
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                        )
                        Text(renderStatus.value, color = if (renderStatus.value.startsWith("Vulkan:")) SlimeGreen else Muted, fontSize = 12.sp)
                        Text(status.value, color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = ::generateSpawnArea,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Generate & render spawn chunks (3 × 3)") }
                        OutlinedButton(
                            onClick = ::resetWorld,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Reset world") }
                        OutlinedButton(
                            onClick = ::finish,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Kembali") }
                    }
                }
            }
        }
        createWorld()
    }

    private fun createWorld() {
        runCatching {
            BedrockEngineNative.createWorld(seed.value)
            status.value = "World context siap. Generate chunk untuk membuat mesh GPU."
        }.onFailure {
            status.value = "Native engine gagal dimuat: ${it.message}"
        }
    }

    private fun generateSpawnArea() {
        runCatching {
            if (BedrockEngineNative.loadedChunkCount() == 0) createWorld()
            for (z in -1..1) for (x in -1..1) {
                check(BedrockEngineNative.generateChunk(x, z)) {
                    "Chunk generation ditolak pada $x,$z"
                }
            }
            stats.value = BedrockEngineNative.chunkStats(0, 0)
            val mesh = buildSpawnRegionMesh()
            val storage = BedrockEngineNative.totalStorageBytes()
            meshInfo.value = "CPU mesh 3×3: ${mesh.vertexCount} vertices · ${mesh.faceCount} faces · ${mesh.memoryBytes / 1024} KiB; world: ${storage / 1024} KiB"
            renderer.value?.updateMesh(mesh)
            if (renderer.value == null) renderStatus.value = "Mesh CPU siap; Surface renderer belum dibuat."
            status.value = "9 chunk dibuat dan mesh seluruh area spawn disiapkan untuk GPU."
        }.onFailure {
            status.value = "Generation error: ${it.message}"
        }
    }

    private fun buildSpawnRegionMesh(): ChunkMeshData {
        val parts = ArrayList<Triple<Int, Int, ChunkMeshData>>(9)
        var totalFloats = 0
        var totalIndices = 0
        for (z in -1..1) for (x in -1..1) {
            val part = BedrockEngineNative.chunkMesh(x, z)
            parts += Triple(x, z, part)
            totalFloats += part.vertices.size
            totalIndices += part.indices.size
        }
        val vertices = FloatArray(totalFloats)
        val indices = IntArray(totalIndices)
        var floatOffset = 0
        var vertexOffset = 0
        var indexOffset = 0
        for ((chunkX, chunkZ, part) in parts) {
            part.vertices.copyInto(vertices, floatOffset)
            for (v in part.vertices.indices step 7) {
                vertices[floatOffset + v] += chunkX * 16f
                vertices[floatOffset + v + 2] += chunkZ * 16f
            }
            for (i in part.indices.indices) {
                indices[indexOffset + i] = part.indices[i] + vertexOffset
            }
            floatOffset += part.vertices.size
            vertexOffset += part.vertexCount
            indexOffset += part.indices.size
        }
        return ChunkMeshData(vertices, indices)
    }

    private fun resetWorld() {
        runCatching {
            BedrockEngineNative.reset()
            renderer.value?.clearMesh()
            seed.value = System.currentTimeMillis()
            stats.value = null
            meshInfo.value = "Mesh belum dibuat."
            createWorld()
            status.value = "World di-reset. Seed baru siap."
        }.onFailure {
            status.value = "Reset error: ${it.message}"
        }
    }

    override fun onDestroy() {
        renderer.value = null
        runCatching { VulkanBackendNative.nativeShutdown() }
        runCatching { BedrockEngineNative.reset() }
        super.onDestroy()
    }
}
