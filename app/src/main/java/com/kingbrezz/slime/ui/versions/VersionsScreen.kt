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
fun VersionsScreen(vm: SlimeViewModel) {
    val versions by vm.versions.collectAsState()
    val activeId by vm.activeVersionId.collectAsState()
    val runtime by vm.installedRuntime.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        SectionTitle("Versi", "Release & Preview")
        Text(
            "Isolasi data per build, seperti LeviLauncher. Lencana Slime = dukungan klien.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        Panel {
            Text("MINECRAFT TERPASANG", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Siapkan sumber runtime langsung dari Minecraft yang sudah terpasang. APK dan split APK dibaca lokal; tidak perlu dikirim ke chat.",
                color = Fg,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (runtime.ready) {
                    Badge("Pustaka siap", good = true)
                } else {
                    Badge("Belum disiapkan", warn = true)
                }
                if (runtime.versionName.isNotBlank()) Badge(runtime.versionName)
                if (runtime.abi.isNotBlank()) Badge(runtime.abi)
            }
            Button(
                onClick = { vm.prepareInstalledMinecraft() },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Text(if (busy) "Menyiapkan..." else "Siapkan dari Minecraft terpasang", fontWeight = FontWeight.SemiBold)
            }
            Text(runtime.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (runtime.libraries.isNotEmpty()) {
                Text(
                    runtime.libraries.joinToString(" · "),
                    color = Faint,
                    fontSize = 10.sp,
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
