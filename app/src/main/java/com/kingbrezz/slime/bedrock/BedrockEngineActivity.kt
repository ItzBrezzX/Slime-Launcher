package com.kingbrezz.slime.bedrock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.SlimeTheme

class BedrockEngineActivity : ComponentActivity() {
    private val status = mutableStateOf("Engine belum diinisialisasi.")
    private val stats = mutableStateOf<ChunkStats?>(null)
    private val meshInfo = mutableStateOf("Mesh belum dibuat.")
    private val vulkanInfo = mutableStateOf("Vulkan belum diperiksa.")
    private val seed = mutableStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlimeTheme {
                Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Bg).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Horizon Bedrock Engine", color = Fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Native voxel runtime · ARM64 host", color = SlimeGreen, fontSize = 13.sp)
                        Text(
                            "Engine independen. Tidak memuat libminecraftpe.so atau menjalankan runtime Mojang.",
                            color = Muted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Seed: ${seed.value}", color = Fg, fontSize = 13.sp)
                        Text("Chunk aktif: ${runCatching { BedrockEngineNative.loadedChunkCount() }.getOrDefault(0)}", color = Fg, fontSize = 13.sp)
                        stats.value?.let { chunk ->
                            Text("Solid blocks: ${chunk.solidBlocks}", color = Fg, fontSize = 14.sp)
                            Text("Water blocks: ${chunk.waterBlocks}", color = Fg, fontSize = 14.sp)
                            Text("Visible faces: ${chunk.visibleFaces}", color = Fg, fontSize = 14.sp)
                            Text("Terrain height: ${chunk.minHeight}–${chunk.maxHeight}", color = Fg, fontSize = 14.sp)
                            Text(meshInfo.value, color = Fg, fontSize = 13.sp)
                        }
                        Text(vulkanInfo.value, color = if (vulkanInfo.value.startsWith("Vulkan siap")) SlimeGreen else Muted, fontSize = 12.sp)
                        Text(status.value, color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = ::generateSpawnArea,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Generate spawn chunks (3 × 3)")
                        }
                        OutlinedButton(
                            onClick = ::initializeVulkan,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Initialize Vulkan GPU")
                        }
                        OutlinedButton(
                            onClick = ::resetWorld,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Reset world")
                        }
                        OutlinedButton(
                            onClick = ::finish,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Kembali")
                        }
                    }
                }
            }
        }
        createWorld()
    }

    private fun createWorld() {
        runCatching {
            BedrockEngineNative.createWorld(seed.value)
            status.value = "World context siap. Generate chunk untuk menguji terrain."
        }.onFailure {
            status.value = "Native engine gagal dimuat: ${it.message}"
        }
    }

    private fun generateSpawnArea() {
        runCatching {
            if (BedrockEngineNative.loadedChunkCount() == 0) createWorld()
            for (z in -1..1) {
                for (x in -1..1) {
                    check(BedrockEngineNative.generateChunk(x, z)) {
                        "Chunk generation ditolak pada $x,$z"
                    }
                }
            }
            stats.value = BedrockEngineNative.chunkStats(0, 0)
            val mesh = BedrockEngineNative.chunkMesh(0, 0)
            val storage = BedrockEngineNative.chunkStorageBytes(0, 0)
            meshInfo.value = "Mesh: ${mesh.vertexCount} vertices · ${mesh.faceCount} faces · ${mesh.memoryBytes / 1024} KiB; chunk storage: ${storage / 1024} KiB"
            status.value = "9 chunk dibuat. Mesh CPU siap; belum dikirim ke GPU."
        }.onFailure {
            status.value = "Generation error: ${it.message}"
        }
    }

    private fun initializeVulkan() {
        runCatching {
            val info = VulkanBackendNative.initialize()
            vulkanInfo.value = if (info.available) {
                "Vulkan siap: ${info.deviceName} · API ${info.apiVersion} · driver ${info.driverVersion} · device-local ${info.deviceLocalMemoryMiB} MiB · queue ${info.deviceType}"
            } else {
                "Vulkan tidak tersedia: ${info.status}"
            }
        }.onFailure {
            vulkanInfo.value = "Vulkan error: ${it.message}"
        }
    }

    private fun resetWorld() {
        runCatching {
            BedrockEngineNative.reset()
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
        if (isFinishing) {
            runCatching { VulkanBackendNative.nativeShutdown() }
            runCatching { BedrockEngineNative.reset() }
        }
        super.onDestroy()
    }
}
