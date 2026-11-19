package com.kingbrezz.slime.ui.versions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.components.Badge
import com.kingbrezz.slime.ui.components.Mono
import com.kingbrezz.slime.ui.components.Panel
import com.kingbrezz.slime.ui.components.SectionTitle
import com.kingbrezz.slime.ui.components.SlimeSwitch
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Faint
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen

@Composable
fun VersionsScreen(
    vm: SlimeViewModel,
    onImportMinecraft: () -> Unit = {},
    onOpenRuntime: () -> Unit = {},
) {
    val versions by vm.versions.collectAsState()
    val activeId by vm.activeVersionId.collectAsState()
    val runtime by vm.runtimeInstall.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        SectionTitle("Versi", "Release & Preview")
        Text(
            "Kelola versi launcher dan siapkan APK Minecraft milikmu sendiri.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        Panel {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Minecraft Runtime", color = Fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (runtime?.isPrepared == true) Badge("APK siap", good = true) else Badge("Belum diimpor")
            }
            if (runtime == null) {
                Text(
                    "Pilih base APK dan split APK Minecraft dari penyimpanan. File disalin ke ruang privat Slime dan diperiksa sebelum digunakan.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            } else {
                Text(
                    "${runtime!!.manifest.versionName} · ${runtime!!.manifest.abi} · ${runtime!!.manifest.apkFiles.size} APK",
                    color = Fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Native library: ${runtime!!.manifest.nativeLibraries.size} · SHA-256 tersimpan · ${if (runtime!!.isPrepared) "libminecraftpe.so ditemukan" else "runtime tidak lengkap"}",
                    color = Muted,
                    fontSize = 11.sp,
                )
                Text(
                    "Paket sudah disiapkan, tetapi Java/GameActivity Minecraft belum terintegrasi sehingga belum dapat dimainkan di dalam Slime.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImportMinecraft,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (runtime == null) "Impor APK" else "Impor ulang", fontWeight = FontWeight.SemiBold)
                }
                if (runtime != null) {
                    OutlinedButton(onClick = onOpenRuntime, enabled = !busy, shape = RoundedCornerShape(8.dp)) {
                        Text("Periksa runtime", color = Fg)
                    }
                    OutlinedButton(onClick = { vm.removeMinecraftRuntime() }, enabled = !busy, shape = RoundedCornerShape(8.dp)) {
                        Text("Hapus", color = Fg)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(versions, key = { it.id }) { v ->
                val active = v.id == activeId
                Panel {
                    Mono(v.version, if (active) SlimeGreen else Fg, 22)
                    Text(v.alias, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(v.channel)
                        Badge(v.pkg)
                        Badge(v.arch)
                        Badge("${v.sizeMb} MB")
                        if (v.supported) Badge("Didukung", good = true) else Badge("Klien belum")
                        if (active) Badge("Aktif", good = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    Mono("Rilis ${v.released}", Faint, 11)
                    Spacer(Modifier.height(10.dp))
                    if (v.installed) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Isolasi", color = Muted, fontSize = 13.sp)
                            SlimeSwitch(v.isolated) { vm.toggleIsolation(v.id) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (v.installed && !active) {
                            Button(
                                onClick = { vm.setActiveVersion(v.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                                shape = RoundedCornerShape(8.dp),
                            ) { Text("Jadikan aktif", fontWeight = FontWeight.SemiBold) }
                        }
                        if (v.installed) {
                            OutlinedButton(onClick = { vm.uninstallVersion(v.id) }, shape = RoundedCornerShape(8.dp)) {
                                Text("Hapus", color = Fg)
                            }
                        } else {
                            Button(
                                onClick = { vm.installVersion(v.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                                shape = RoundedCornerShape(8.dp),
                            ) { Text("Pasang", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}
