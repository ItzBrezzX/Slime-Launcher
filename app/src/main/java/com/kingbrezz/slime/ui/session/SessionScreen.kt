package com.kingbrezz.slime.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.components.SIcon
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Line
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.Raise
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.Surface
import com.kingbrezz.slime.ui.theme.Surface2

@Composable
fun SessionScreen(vm: SlimeViewModel, onExit: () -> Unit) {
    val modules by vm.modules.collectAsState()
    val version = vm.activeVersion()
    val displayName by vm.displayName.collectAsState()
    val lastInject by vm.lastInject.collectAsState()
    var guiOpen by remember { mutableStateOf(false) }
    var cps by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(142) }

    fun on(id: String): Boolean = modules[id]?.enabled == true
    val nick = if (on("nick")) "Player" else displayName

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Raise).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onExit, shape = RoundedCornerShape(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIcon.Cross(Fg, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Keluar", color = Fg)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Slime Client", color = Fg, fontWeight = FontWeight.Bold)
                Text(
                    "${version?.id ?: "—"} · ${version?.alias ?: ""}",
                    color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                )
            }
            Button(
                onClick = { guiOpen = !guiOpen },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (guiOpen) SlimeGreen else Surface2,
                    contentColor = if (guiOpen) Ink else Fg,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (guiOpen) "Tutup GUI" else "ClickGUI", fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().background(Raise)
                .clickable(enabled = !guiOpen) {
                    cps = (cps + 1).coerceAtMost(24)
                    combo += 1
                    fps = (118..152).random()
                }
                .padding(12.dp),
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Surface)
                    .border(1.dp, Line, RoundedCornerShape(12.dp)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lastInject?.let { inj ->
                        HudChip("Inject: ${inj.summary.take(48)}")
                        HudChip("sig ${inj.sigResolved}/${inj.sigTotal} · hooks ${inj.hooksActive}/${inj.hooksPending}")
                    }
                    if (on("watermark")) HudChip("Slime · $nick · ${Catalog.CLIENT_VERSION}")
                    if (on("fps")) HudChip("$fps FPS")
                    if (on("cps")) HudChip("$cps CPS")
                    if (on("direction")) HudChip("Selatan")
                    if (on("coords")) HudChip("214  71  -88 · rawa")
                    if (on("waila")) HudChip("Rumput · blok padat")
                    if (on("waypoints")) HudChip("Spawn · 42m")
                    if (on("sprint")) HudChip("Sprint")
                    if (on("sneak")) HudChip("Sneak")
                    if (on("combo") && combo > 0) HudChip("$combo kombo")
                    if (on("chat")) {
                        HudChip("12:04 $nick selamat datang di rawa")
                        HudChip("12:04 Slime · buka ClickGUI")
                    }
                    if (on("potions")) {
                        HudChip("Penglihatan malam 2:10")
                        HudChip("Kecepatan 0:40")
                    }
                    if (on("fullbright")) HudChip("Fullbright on")
                    if (on("weather")) HudChip("Cuaca: demo")
                }
                Box(Modifier.align(Alignment.Center)) { SIcon.Cross(Fg.copy(alpha = 0.9f), 28.dp) }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                        .background(Bg.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("D", "D", "T", "P", "B", "A", "K", "E", "S").forEachIndexed { i, s ->
                        Box(
                            Modifier.size(32.dp)
                                .background(if (i == 0) SlimeGreen else Surface2, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(s, color = if (i == 0) Ink else Fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (guiOpen) {
                val cats = listOf("Visual", "HUD", "Pemain", "Dunia", "Lain")
                Row(
                    Modifier.fillMaxSize().background(Bg.copy(alpha = 0.94f))
                        .horizontalScroll(rememberScrollState()).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    cats.forEach { cat ->
                        Column(
                            Modifier.width(200.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Line, RoundedCornerShape(10.dp)).background(Raise),
                        ) {
                            Text(
                                cat, color = SlimeGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().background(Surface)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                Catalog.modules.filter { it.category == cat }.forEach { def ->
                                    val enabled = modules[def.id]?.enabled == true
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .background(if (enabled) SlimeGreen else Raise)
                                            .clickable { vm.toggleModule(def.id) }
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(def.name, color = if (enabled) Ink else Fg, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(
                                            modules[def.id]?.keybind ?: "",
                                            color = if (enabled) Ink else Muted,
                                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                        )
                                    }
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.5f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudChip(text: String) {
    Text(
        text = text, color = Fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
        modifier = Modifier.background(Bg.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
