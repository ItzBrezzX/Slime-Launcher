package com.kingbrezz.slime.runtime

import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.SlimeTheme

/**
 * Runtime preparation/diagnostics screen.
 *
 * This Activity deliberately does not pretend to be Mojang's GameActivity.
 * The project still needs a compatible Java game Activity layer before the
 * imported native libraries can be used as a playable in-process runtime.
 */
class MinecraftRuntimeActivity : ComponentActivity() {
    private var runtime: ImportedMinecraftRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = MinecraftRuntimeInstaller(applicationContext).current()
        setContent {
            SlimeTheme {
                RuntimeScreen(
                    runtime = runtime,
                    onOpenOfficialMinecraft = ::openOfficialMinecraft,
                    onClose = ::finish,
                )
            }
        }
    }

    private fun openOfficialMinecraft() {
        val launch = packageManager.getLaunchIntentForPackage(Catalog.MC_PACKAGE)
        if (launch == null) return
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
            Text(
                "Slime Runtime Preparation",
                color = SlimeGreen,
                fontSize = 13.sp,
            )
            if (runtime == null) {
                Text(
                    "Belum ada APK Minecraft yang diimpor. Kembali ke Versi lalu pilih base APK dan split APK yang sesuai.",
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
                    if (runtime.minecraftLibrary.isFile) "Ditemukan" else "Tidak ditemukan",
                )
                RuntimeLine("Lokasi native", runtime.nativeLibraryDirectory.absolutePath)

                Spacer(Modifier.height(6.dp))
                Text(
                    if (runtime.isPrepared) {
                        "File runtime berhasil disiapkan dan divalidasi. Game belum dapat dijalankan di Activity Slime karena lapisan Java/GameActivity Minecraft belum tersedia di project ini."
                    } else {
                        "Paket runtime belum lengkap. Impor ulang base APK dan split APK Minecraft yang benar."
                    },
                    color = Muted,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onOpenOfficialMinecraft,
                enabled = runtime != null,
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
