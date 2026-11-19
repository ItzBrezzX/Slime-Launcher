package com.kingbrezz.slime.runtime

import android.content.Intent
import android.os.Bundle
import android.os.Process
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.native.MinecraftRuntimeBridge
import com.kingbrezz.slime.native.PreloaderNative
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.SlimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class RuntimeExecutionReport(
    val processName: String,
    val processId: Int,
    val abi: String,
    val loadedLibraries: List<String>,
    val gameLibraryMapped: Boolean,
    val preloaderLoaded: Boolean,
    val signaturesResolved: Int,
    val signaturesTotal: Int,
    val hooksActive: Int,
    val message: String,
)

class MinecraftRuntimeActivity : ComponentActivity() {
    private var runtime: ImportedMinecraftRuntime? = null
    private val isLoading = mutableStateOf(false)
    private val report = mutableStateOf<RuntimeExecutionReport?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = MinecraftRuntimeInstaller(applicationContext).current()
        setContent {
            SlimeTheme {
                RuntimeScreen(
                    runtime = runtime,
                    loading = isLoading.value,
                    report = report.value,
                    onLoadRuntime = ::loadRuntimeLibraries,
                    onOpenOfficialMinecraft = ::openOfficialMinecraft,
                    onClose = ::finish,
                )
            }
        }
    }

    private fun loadRuntimeLibraries() {
        val selected = runtime ?: return
        if (!selected.isPrepared || isLoading.value || report.value != null) return

        isLoading.value = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val gameLoad = MinecraftNativeLibraryLoader.load(applicationContext, selected)
                if (!gameLoad.complete) {
                    return@withContext RuntimeExecutionReport(
                        processName = gameLoad.processName,
                        processId = gameLoad.processId,
                        abi = gameLoad.abi,
                        loadedLibraries = gameLoad.loadedLibraries,
                        gameLibraryMapped = gameLoad.gameLibraryLoaded,
                        preloaderLoaded = false,
                        signaturesResolved = 0,
                        signaturesTotal = 0,
                        hooksActive = 0,
                        message = buildString {
                            append(gameLoad.error ?: "Native runtime gagal dimuat.")
                            if (gameLoad.requiresProcessRestart) {
                                append(" Sebagian library sudah terpetakan; restart Slime sebelum mencoba lagi.")
                            }
                        },
                    )
                }

                val bridge = MinecraftRuntimeBridge.onRuntimeReady(
                    context = applicationContext,
                    versionId = selected.manifest.versionName,
                )
                RuntimeExecutionReport(
                    processName = gameLoad.processName,
                    processId = gameLoad.processId,
                    abi = gameLoad.abi,
                    loadedLibraries = gameLoad.loadedLibraries,
                    gameLibraryMapped = bridge.gameLibraryMapped,
                    preloaderLoaded = PreloaderNative.libraryLoaded,
                    signaturesResolved = bridge.status.sigResolved,
                    signaturesTotal = bridge.status.sigTotal,
                    hooksActive = bridge.hooksActive,
                    message = bridge.message,
                )
            }
            report.value = result
            isLoading.value = false
        }
    }

    private fun openOfficialMinecraft() {
        val launch = packageManager.getLaunchIntentForPackage(Catalog.MC_PACKAGE)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launch)
        } catch (_: Throwable) {
        }
    }
}

@Composable
private fun RuntimeScreen(
    runtime: ImportedMinecraftRuntime?,
    loading: Boolean,
    report: RuntimeExecutionReport?,
    onLoadRuntime: () -> Unit,
    onOpenOfficialMinecraft: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Bg).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Minecraft Runtime",
                color = Fg,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("Dedicated process: :game", color = SlimeGreen, fontSize = 13.sp)

            if (runtime == null) {
                Text(
                    "Belum ada APK Minecraft yang diimpor. Kembali ke Versi lalu impor paket resmi.",
                    color = Muted,
                    fontSize = 14.sp,
                )
            } else {
                val manifest = runtime.manifest
                RuntimeLine("Package", manifest.packageName)
                RuntimeLine("Versi", "${manifest.versionName} (${manifest.versionCode})")
                RuntimeLine("ABI", manifest.abi)
                RuntimeLine("APK tersimpan", manifest.apkFiles.size.toString())
                RuntimeLine("Native libraries", manifest.nativeLibraries.size.toString())
                RuntimeLine(
                    "libminecraftpe.so",
                    if (runtime.minecraftLibrary.isFile) "Siap dimuat" else "Tidak ditemukan",
                )
                RuntimeLine("Lokasi native", runtime.nativeLibraryDirectory.absolutePath)

                Spacer(Modifier.height(4.dp))
                if (runtime.isPrepared) {
                    Text(
                        "Library akan dimuat ke proses :game milik Slime. Ini bukan proses aplikasi Minecraft resmi.",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        "Paket runtime belum lengkap. Impor ulang base APK dan split APK yang sesuai.",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
            }

            if (loading) {
                Text("Memuat dependency ELF dan libminecraftpe.so…", color = SlimeGreen, fontSize = 13.sp)
            }

            report?.let { result ->
                Spacer(Modifier.height(4.dp))
                RuntimeLine("Proses", result.processName)
                RuntimeLine("PID", result.processId.toString())
                RuntimeLine("ABI proses", result.abi)
                RuntimeLine(
                    "libminecraftpe terpetakan",
                    if (result.gameLibraryMapped) "Ya" else "Belum terkonfirmasi",
                )
                RuntimeLine("Preloader", if (result.preloaderLoaded) "Termuat" else "Gagal / belum termuat")
                RuntimeLine(
                    "Signature",
                    "${result.signaturesResolved}/${result.signaturesTotal}",
                )
                RuntimeLine("Hook aktif", result.hooksActive.toString())
                Text(
                    "Library dimuat: ${result.loadedLibraries.size}",
                    color = Fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                result.loadedLibraries.forEach { name ->
                    Text("• $name", color = Muted, fontSize = 11.sp)
                }
                Text(result.message, color = Muted, fontSize = 13.sp)
                Text(
                    "Status ini hanya membuktikan pemetaan native pada proses :game. Game belum playable: Java/GameActivity Mojang, assets, resource context, dan lifecycle belum terintegrasi.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = onLoadRuntime,
                enabled = runtime?.isPrepared == true && !loading && report == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Memuat…" else "Muat .so ke proses :game")
            }
            OutlinedButton(
                onClick = onOpenOfficialMinecraft,
                enabled = runtime != null && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Buka Minecraft resmi (proses terpisah)")
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Kembali ke Slime")
            }
        }
    }
}

@Composable
private fun RuntimeLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Fg, fontSize = 13.sp)
    }
}
