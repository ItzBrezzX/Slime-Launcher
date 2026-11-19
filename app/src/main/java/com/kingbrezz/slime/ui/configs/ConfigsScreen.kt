package com.kingbrezz.slime.ui.configs

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.components.Badge
import com.kingbrezz.slime.ui.components.Panel
import com.kingbrezz.slime.ui.components.SectionTitle
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Line
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen

@Composable
fun ConfigsScreen(vm: SlimeViewModel) {
    val configs by vm.configs.collectAsState()
    val activeId by vm.activeConfigId.collectAsState()
    var name by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        SectionTitle("Config", "Profil modul")
        Text(
            "Simpan saklar modul ke profil. Aktif dipakai saat Play.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        Panel {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nama config baru") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SlimeGreen,
                    unfocusedBorderColor = Line,
                    focusedTextColor = Fg,
                    unfocusedTextColor = Fg,
                    cursorColor = SlimeGreen,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.saveAs(name)
                        name = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Simpan baru", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(onClick = { vm.saveActiveConfig() }, shape = RoundedCornerShape(8.dp)) {
                    Text("Timpa aktif", color = Fg)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(configs, key = { it.id }) { cfg ->
                val active = cfg.id == activeId
                val onCount = cfg.modules.count { it.value.enabled }
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cfg.name, color = Fg, fontWeight = FontWeight.Medium)
                        if (active) Badge("Aktif", good = true)
                    }
                    Text("${cfg.author} · ${cfg.note} · $onCount modul", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!active) {
                            Button(
                                onClick = { vm.loadConfig(cfg.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                                shape = RoundedCornerShape(8.dp),
                            ) { Text("Muat", fontWeight = FontWeight.SemiBold) }
                        }
                        OutlinedButton(onClick = { vm.deleteConfig(cfg.id) }, shape = RoundedCornerShape(8.dp)) {
                            Text("Hapus", color = Fg)
                        }
                    }
                }
            }
        }
    }
}
