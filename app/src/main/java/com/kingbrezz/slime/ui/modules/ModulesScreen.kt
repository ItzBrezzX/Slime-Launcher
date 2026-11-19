package com.kingbrezz.slime.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.kingbrezz.slime.ui.theme.Surface2

@Composable
fun ModulesScreen(vm: SlimeViewModel) {
    val modules by vm.modules.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    val dirty = vm.isDirty()

    val filtered = vm.moduleDefs.filter { def ->
        (category == "Semua" || def.category == category) &&
            (query.isBlank() || def.name.contains(query, true) || def.blurb.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionTitle("Modul", "Klien")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dirty) Badge("Kotor", warn = true) else Badge("Bersih", good = true)
                Button(
                    onClick = { vm.saveActiveConfig() },
                    colors = ButtonDefaults.buttonColors(containerColor = SlimeGreen, contentColor = Ink),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            placeholder = { Text("Cari modul") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SlimeGreen,
                unfocusedBorderColor = Line,
                focusedTextColor = Fg,
                unfocusedTextColor = Fg,
                cursorColor = SlimeGreen,
            ),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            items(vm.categories) { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SlimeGreen,
                        selectedLabelColor = Ink,
                        containerColor = Surface2,
                        labelColor = Fg,
                    ),
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { def ->
                val state = modules[def.id]
                val on = state?.enabled == true
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(def.name, color = Fg, fontWeight = FontWeight.SemiBold)
                            Text(def.blurb, color = Muted, fontSize = 13.sp)
                            if (state?.keybind != null) {
                                Text("Key: ${state.keybind}", color = SlimeGreen, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = { vm.toggleModule(def.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (on) SlimeGreen else Surface2,
                                contentColor = if (on) Ink else Fg,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(if (on) "Nyala" else "Mati", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
