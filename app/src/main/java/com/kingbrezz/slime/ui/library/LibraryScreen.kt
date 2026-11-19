package com.kingbrezz.slime.ui.library

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.kingbrezz.slime.ui.components.Panel
import com.kingbrezz.slime.ui.components.SectionTitle
import com.kingbrezz.slime.ui.components.SlimeSwitch
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.Surface2

@Composable
fun LibraryScreen(vm: SlimeViewModel) {
    val library by vm.library.collectAsState()
    var kind by remember { mutableStateOf("world") }
    val kinds = listOf(
        "world" to "Dunia",
        "resource" to "Resource",
        "behavior" to "Behavior",
        "skin" to "Skin",
        "server" to "Server",
    )
    val rows = library.filter { it.kind == kind }

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        SectionTitle("Pustaka", "Dunia & paket")
        Text(
            "World, resource, behavior, skin, server — ikut instance.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            items(kinds) { (id, label) ->
                FilterChip(
                    selected = kind == id,
                    onClick = { kind = id },
                    label = { Text(label) },
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
            items(rows, key = { it.id }) { item ->
                Panel {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(item.name, color = Fg, fontWeight = FontWeight.Medium)
                            Text(item.detail, color = Muted, fontSize = 13.sp)
                        }
                        SlimeSwitch(item.enabled) { vm.toggleLibrary(item.id) }
                    }
                }
            }
        }
    }
}
