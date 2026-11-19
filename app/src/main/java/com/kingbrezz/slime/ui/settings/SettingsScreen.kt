package com.kingbrezz.slime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.components.Panel
import com.kingbrezz.slime.ui.components.SectionTitle
import com.kingbrezz.slime.ui.components.SlimeSwitch
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Line
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen

@Composable
fun SettingsScreen(vm: SlimeViewModel) {
    val name by vm.displayName.collectAsState()
    val autoMc by vm.autoLaunchMc.collectAsState()
    val snap by vm.nativeState.collectAsState()
    val busy by vm.busy.collectAsState()
    val mcInstalled = vm.isMinecraftInstalled()
    val st = snap.status

    LaunchedEffect(Unit) { vm.refreshNative() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Setelan", "Peluncur")

        Panel {
            Text("Nama tampilan", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { vm.setDisplayName(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SlimeGreen,
                    unfocusedBorderColor = Line,
                    focusedTextColor = Fg,
                    unfocusedTextColor = Fg,
                    cursorColor = SlimeGreen,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Buka Minecraft saat Play", color = Fg, fontWeight = FontWeight.Medium)
                    Text(
                        if (mcInstalled) "Game terdeteksi di perangkat" else "Minecraft belum terpasang",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
                SlimeSwitch(autoMc) { vm.setAutoLaunchMc(!autoMc) }
            }
        }

        Panel {
            Text("Preloader NDK", color = Fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${if (snap.libLoaded) snap.libVersion else "no-so"} · ${st.phase} · sig ${st.sigResolved}/${st.sigTotal} · " +
                    "hooks ${st.hooksActive}/${st.hooksPending} · override ${snap.overrides}",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.initNative() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Init preloader", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(onClick = { vm.openModMenu() }, shape = RoundedCornerShape(8.dp)) {
                    Text("Buka Mod Menu", color = Fg)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tabel signature/offset, hook, dan alat uji ada di Mod Menu. Pattern bisa diisi langsung di sana " +
                    "(tanpa rebuild .so). Tanpa pattern yang cocok, hook tetap Pending.",
                color = Muted,
                fontSize = 12.sp,
            )
        }

        Panel {
            Text("${Catalog.CLIENT_NAME} ${Catalog.CLIENT_VERSION}", color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Penulis ${Catalog.CLIENT_AUTHOR}", color = SlimeGreen, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Launcher Android untuk mengelola versi, modul, config, dan pustaka. " +
                    "Terinspirasi alur Flarial (klien + ClickGUI) dan LeviLauncher (versi, isolasi, pack).",
                color = Muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Batasan: APK ini tidak menyuntik native DLL ke Minecraft. " +
                    "Play membuka game resmi (jika ada) dan sesi demo HUD/ClickGUI di dalam Slime. " +
                    "Injeksi klien sungguhan butuh native mod loader di perangkat.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}
