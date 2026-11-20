package com.kingbrezz.slime.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.components.Badge
import com.kingbrezz.slime.ui.components.BrandMark
import com.kingbrezz.slime.ui.components.Mono
import com.kingbrezz.slime.ui.components.Panel
import com.kingbrezz.slime.ui.components.SIcon
import com.kingbrezz.slime.ui.components.SectionTitle
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Faint
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.Raise
import com.kingbrezz.slime.ui.theme.SlimeGreen

@Composable
fun HomeScreen(
    vm: SlimeViewModel,
    onPlay: () -> Unit,
    onOpenVersions: () -> Unit,
    onOpenConfigs: () -> Unit,
    onOpenBedrockEngine: () -> Unit,
) {
    val version = vm.activeVersion()
    val logs by vm.logs.collectAsState()
    val modules by vm.modules.collectAsState()
    val configs by vm.configs.collectAsState()
    val activeConfigId by vm.activeConfigId.collectAsState()
    val displayName by vm.displayName.collectAsState()
    val mcInstalled = vm.isMinecraftInstalled()
    val dirty = vm.isDirty()
    val rpcOn = modules["rpc"]?.enabled == true
    val activeConfigName = configs.find { it.id == activeConfigId }?.name ?: "Config"

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Slime", color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(Catalog.CLIENT_AUTHOR, color = Muted, fontSize = 12.sp)
                }
            }
            Mono("v${Catalog.CLIENT_VERSION}", Faint, 11)
        }
        SectionTitle("Beranda", "Siap main")
        Panel {
            Text("VERSI AKTIF", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Mono(version?.version ?: "—", SlimeGreen, 28)
            Text(version?.alias ?: "", color = Fg, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(version?.channel ?: "—")
                Badge(version?.pkg ?: "—")
                Badge(version?.arch ?: "—")
                if (version?.supported == true) Badge("Slime ok", good = true) else Badge("Tidak didukung")
                if (version?.isolated == true) Badge("Isolasi", warn = true) else Badge("Shared")
            }
            Spacer(Modifier.height(8.dp))
            Mono(if (version?.isolated == true) "isolasi://slime/versions/${version.id}/game" else "shared://minecraft/default", Faint, 11)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                shape = RoundedCornerShape(10.dp),
            ) {
                SIcon.Play(Ink, 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Mainkan", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenVersions, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp)) {
                Text(if (version?.installed == true) "Ganti versi" else "Pasang versi", color = Fg)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenBedrockEngine, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp)) {
                Text("Horizon Bedrock Engine (dev)", color = Fg)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.openModMenu() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp)) {
                SIcon.Tools(SlimeGreen, 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("Mod Menu", color = Fg)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (mcInstalled) "Minecraft Bedrock terdeteksi. Play membuka game + sesi ClickGUI Slime."
                else "Minecraft belum terpasang. Play membuka sesi demo klien Slime (HUD + ClickGUI).",
                color = Muted, fontSize = 13.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Modul", "${vm.enabledModuleCount()}", Modifier.weight(1f))
            StatCard("Pustaka", "${vm.libraryOnCount()}", Modifier.weight(1f))
            StatCard("Config", if (dirty) "Kotor" else "Bersih", Modifier.weight(1f))
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(activeConfigName, color = Fg, fontWeight = FontWeight.Medium)
                if (dirty) Badge("Belum disimpan", warn = true) else Badge("Tersimpan", good = true)
            }
            TextButton(onClick = onOpenConfigs) { Text("Kelola config", color = SlimeGreen) }
        }
        if (rpcOn) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("$displayName · Slime", color = Fg, fontWeight = FontWeight.Medium)
                        Text("Di launcher · ${version?.version} ${version?.alias}", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Panel {
            Text("LOG PELUNCUR", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            logs.take(10).forEach { line ->
                Text(line, color = Fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Raise, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
