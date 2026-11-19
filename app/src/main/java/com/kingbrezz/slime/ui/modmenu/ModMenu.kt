package com.kingbrezz.slime.ui.modmenu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.data.SettingDef
import com.kingbrezz.slime.native.PreloaderNative
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.components.Badge
import com.kingbrezz.slime.ui.components.BrandMark
import com.kingbrezz.slime.ui.components.Panel
import com.kingbrezz.slime.ui.components.SIcon
import com.kingbrezz.slime.ui.components.SlimeSwitch
import com.kingbrezz.slime.ui.theme.Bad
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.Faint
import com.kingbrezz.slime.ui.theme.Fg
import com.kingbrezz.slime.ui.theme.Ink
import com.kingbrezz.slime.ui.theme.Line
import com.kingbrezz.slime.ui.theme.Muted
import com.kingbrezz.slime.ui.theme.Raise
import com.kingbrezz.slime.ui.theme.SlimeGreen
import com.kingbrezz.slime.ui.theme.Surface2
import kotlin.math.roundToInt

private val TABS = listOf("Modul", "Preloader", "Hook", "Signature", "Offset", "Alat", "Log")

/**
 * Mod Menu: a draggable floating button on every screen that opens a full panel with
 * every launcher tool (modules, preloader, hooks, signatures, offsets, scanner, logs).
 * Place it once, above the Scaffold.
 */
@Composable
fun ModMenuLayer(vm: SlimeViewModel) {
    val open by vm.modMenuOpen.collectAsState()
    BackHandler(enabled = open) { vm.closeModMenu() }

    Box(Modifier.fillMaxSize()) {
        if (!open) {
            FloatingModMenuButton(onClick = { vm.openModMenu() })
        } else {
            ModMenuPanel(vm)
        }
    }
}

// ---------------------------------------------------------------- floating button

@Composable
private fun FloatingModMenuButton(onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        val density = LocalDensity.current
        val btn = with(density) { 54.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val maxX = with(density) { maxWidth.toPx() } - btn
        val maxY = with(density) { maxHeight.toPx() } - btn
        var x by remember(maxX) { mutableFloatStateOf((maxX - margin).coerceAtLeast(0f)) }
        var y by remember(maxY) { mutableFloatStateOf((maxY * 0.62f).coerceAtLeast(0f)) }

        Box(
            Modifier
                .offset { IntOffset(x.coerceIn(0f, maxX).roundToInt(), y.coerceIn(0f, maxY).roundToInt()) }
                .size(54.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(SlimeGreen)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        x = (x + drag.x).coerceIn(0f, maxX)
                        y = (y + drag.y).coerceIn(0f, maxY)
                    }
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            SIcon.Tools(Ink, 28.dp)
        }
    }
}

// ---------------------------------------------------------------------- panel

@Composable
private fun ModMenuPanel(vm: SlimeViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsState()
    val modules by vm.modules.collectAsState()
    val version = vm.activeVersion()

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .pointerInput(Unit) { detectTapGestures { } }, // swallow taps so nothing leaks to the screen below
    ) {
        Row(
            Modifier.fillMaxWidth().background(Raise).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Mod Menu", color = Fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${version?.id ?: "—"} · ${modules.count { it.value.enabled }}/${modules.size} modul aktif",
                        color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = SlimeGreen, strokeWidth = 2.dp)
                OutlinedButton(
                    onClick = { vm.closeModMenu() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    SIcon.Cross(Fg, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Tutup", color = Fg, fontSize = 13.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TABS.forEachIndexed { i, label ->
                FilterChip(
                    selected = tab == i,
                    onClick = { tab = i },
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

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ModulesTab(vm)
                1 -> PreloaderTab(vm)
                2 -> HooksTab(vm)
                3 -> SignaturesTab(vm)
                4 -> OffsetsTab(vm)
                5 -> ToolsTab(vm)
                else -> LogTab(vm)
            }
        }
    }
}

// ------------------------------------------------------------------- small bits

@Composable
private fun ActionBtn(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = SlimeGreen, contentColor = Ink,
                disabledContainerColor = Surface2, disabledContentColor = Faint,
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = pad,
        ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp), contentPadding = pad) {
            Text(label, color = if (enabled) Fg else Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun KeyValue(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(
            value, color = Fg, fontSize = 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun MonoText(text: String, color: androidx.compose.ui.graphics.Color = Fg, size: Int = 11, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, fontSize = 13.sp) },
        singleLine = true,
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SlimeGreen,
    unfocusedBorderColor = Line,
    focusedTextColor = Fg,
    unfocusedTextColor = Fg,
    cursorColor = SlimeGreen,
)

private fun fmt(v: Float): String = if (v >= 100f) v.roundToInt().toString() else "%.2f".format(v)

// ---------------------------------------------------------------------- Modul

private data class Preview(val kind: String, val base: Double, val label: String)

private val PREVIEWS = mapOf(
    "zoom" to Preview("fov", 70.0, "FOV 70° saat zoom ditahan →"),
    "fullbright" to Preview("gamma", 1.0, "Gamma 1 →"),
    "fog" to Preview("fog", 100.0, "Jarak kabut 100 →"),
    "time" to Preview("time", 0.0, "Tick langit →"),
    "viewmodel" to Preview("viewmodel", 1.0, "Skala tangan 1.0 →"),
)

@Composable
private fun ModulesTab(vm: SlimeViewModel) {
    val modules by vm.modules.collectAsState()
    val values by vm.moduleValues.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Semua") }
    val dirty = vm.isDirty()

    val filtered = vm.moduleDefs.filter { def ->
        (category == "Semua" || def.category == category) &&
            (query.isBlank() || def.name.contains(query, true) || def.blurb.contains(query, true))
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ActionRow {
                ActionBtn("Semua nyala") { vm.setAllModules(true) }
                ActionBtn("Semua mati") { vm.setAllModules(false) }
                ActionBtn("Default") { vm.resetModulesToDefaults() }
                ActionBtn("Reset nilai") { vm.resetModuleValues() }
                ActionBtn(if (dirty) "Simpan config*" else "Simpan config", primary = true) { vm.saveActiveConfig() }
            }
        }
        item { SearchField(query, { query = it }, "Cari modul") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SlimeGreen, selectedLabelColor = Ink,
                            containerColor = Surface2, labelColor = Fg,
                        ),
                    )
                }
            }
        }
        items(filtered, key = { it.id }) { def ->
            val state = modules[def.id]
            val on = state?.enabled == true
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(def.name, color = Fg, fontWeight = FontWeight.SemiBold)
                        Text(def.blurb, color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Badge(def.category)
                            if (state?.keybind != null) Badge("Key ${state.keybind}", good = on)
                        }
                    }
                    SlimeSwitch(on) { vm.toggleModule(def.id) }
                }
                if (on && def.settings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    def.settings.forEach { s ->
                        SettingSlider(
                            def = s,
                            value = values["${def.id}.${s.key}"] ?: s.def,
                            onCommit = { vm.setModuleValue(def.id, s.key, it) },
                        )
                    }
                    val preview = PREVIEWS[def.id]
                    if (preview != null) {
                        val out = PreloaderNative.evalEffect(preview.kind, preview.base)
                        MonoText(
                            "${preview.label} ${out?.let { if (preview.kind == "time") it.toInt().toString() else "%.2f".format(it) } ?: "native belum termuat"}",
                            color = SlimeGreen,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SettingSlider(def: SettingDef, value: Float, onCommit: (Float) -> Unit) {
    var tmp by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(def.label, color = Muted, fontSize = 12.sp)
            MonoText("${fmt(tmp)}${def.unit}", Fg, 12)
        }
        Slider(
            value = tmp.coerceIn(def.min, def.max),
            onValueChange = { tmp = it },
            onValueChangeFinished = { onCommit(tmp) },
            valueRange = def.min..def.max,
            colors = SliderDefaults.colors(
                thumbColor = SlimeGreen, activeTrackColor = SlimeGreen, inactiveTrackColor = Surface2,
            ),
        )
    }
}

// ------------------------------------------------------------------- Preloader

@Composable
private fun PreloaderTab(vm: SlimeViewModel) {
    val snap by vm.nativeState.collectAsState()
    val busy by vm.busy.collectAsState()
    val lastInject by vm.lastInject.collectAsState()
    val st = snap.status

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Preloader NDK", color = Fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Badge(st.phase, good = st.phase == "Ready", warn = st.phase == "Degraded" || st.phase == "Error")
                }
                Spacer(Modifier.height(8.dp))
                KeyValue("Library", if (snap.libLoaded) snap.libVersion else "tidak termuat", mono = true)
                if (!snap.libLoaded) KeyValue("Error", snap.loadError ?: "—")
                KeyValue("Versi", st.versionId.ifBlank { "—" }, mono = true)
                KeyValue("Signature", "${st.sigResolved}/${st.sigTotal}", mono = true)
                KeyValue("Hook aktif / pending", "${st.hooksActive} / ${st.hooksPending}", mono = true)
                KeyValue("libminecraftpe.so di proses ini", if (st.targetFound) "ya @ ${st.targetBase}" else "tidak", mono = true)
                KeyValue("Override pattern/offset", "${snap.overrides}", mono = true)
                KeyValue("Siap inject", if (st.canInject) "ya" else "tidak")
                Spacer(Modifier.height(6.dp))
                Text(st.message.ifBlank { "—" }, color = Muted, fontSize = 12.sp)
            }
        }
        item {
            ActionRow {
                ActionBtn("Init preloader", primary = true, enabled = !busy) { vm.initNative() }
                ActionBtn("Pipeline inject", primary = true, enabled = !busy) { vm.runPipelineFromMenu() }
                ActionBtn("Shutdown", enabled = !busy) { vm.shutdownNative() }
                ActionBtn("Buka Minecraft") { vm.launchGameFromMenu() }
                ActionBtn("Segarkan") { vm.refreshNative() }
            }
        }
        lastInject?.let { r ->
            item {
                Panel {
                    Text("Pipeline terakhir", color = Fg, fontWeight = FontWeight.SemiBold)
                    Text(r.summary, color = SlimeGreen, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    r.lines.take(14).forEach { MonoText(it, Muted, 10) }
                }
            }
        }
        item {
            Text(
                "Status jujur: hook hanya ACTIVE jika signature ter-resolve di modul target yang termuat di proses yang sama " +
                    "dan backend inline-hook tersedia. Minecraft stok berjalan di proses terpisah, jadi pada APK ini hasil normal adalah Degraded.",
                color = Faint, fontSize = 11.sp,
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

// ----------------------------------------------------------------------- Hook

@Composable
private fun HooksTab(vm: SlimeViewModel) {
    val snap by vm.nativeState.collectAsState()
    val busy by vm.busy.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionRow {
                ActionBtn("Coba pasang semua", primary = true, enabled = !busy) { vm.installAllHooks() }
                ActionBtn("Init ulang", enabled = !busy) { vm.initNative() }
            }
        }
        if (snap.hooks.isEmpty()) {
            item { Text("Belum ada hook. Jalankan Init preloader.", color = Muted, fontSize = 13.sp) }
        }
        items(snap.hooks, key = { it.name }) { h ->
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(h.name, color = Fg, fontWeight = FontWeight.SemiBold)
                    Badge(h.status, good = h.status == "ACTIVE", warn = h.status == "PENDING" || h.status == "FAILED")
                }
                MonoText(h.sigName, Muted)
                MonoText("target ${h.target}", Faint, 10)
                Text(h.reason, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                ActionBtn("Pasang", enabled = !busy) { vm.installHook(h.name) }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ------------------------------------------------------------------- Signature

@Composable
private fun SignaturesTab(vm: SlimeViewModel) {
    val snap by vm.nativeState.collectAsState()
    val overrides by vm.sigOverrides.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<PreloaderNative.SigInfo?>(null) }
    val rows = snap.sigs.filter { query.isBlank() || it.name.contains(query, true) }

    editing?.let { sig ->
        PatternDialog(
            title = sig.name,
            hint = "Pola IDA, contoh: F4 0F 1E F8 ?? ?? 00 91. Kosong = kembali ke bawaan tabel.",
            initial = overrides[sig.name] ?: sig.pattern,
            onDismiss = { editing = null },
            onSave = { vm.applySignature(sig.name, it) },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ActionRow {
                Badge("${snap.sigs.count { it.resolved }}/${snap.sigs.size} resolved", good = snap.sigs.any { it.resolved })
                Badge("${overrides.size} override", warn = overrides.isNotEmpty())
                ActionBtn("Hapus semua override", enabled = overrides.isNotEmpty() || snap.overrides > 0) { vm.clearOverrides() }
            }
        }
        item { SearchField(query, { query = it }, "Cari signature") }
        item {
            Text(
                "Ketuk baris untuk mengisi pattern. Disimpan permanen dan dipakai saat Init berikutnya, tanpa rebuild .so.",
                color = Faint, fontSize = 11.sp,
            )
        }
        if (snap.sigs.isEmpty()) {
            item { Text("Tabel kosong. Jalankan Init preloader dulu.", color = Muted, fontSize = 13.sp) }
        }
        items(rows, key = { it.name }) { s ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .background(Raise)
                    .clickable { editing = s }
                    .padding(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (overrides.containsKey(s.name)) Badge("override", warn = true)
                        Badge(if (s.resolved) "RESOLVED" else "PENDING", good = s.resolved)
                    }
                }
                MonoText(if (s.pattern.isBlank()) "(pattern kosong)" else s.pattern, if (s.pattern.isBlank()) Faint else Muted, 10, maxLines = 2)
                if (s.resolved) MonoText("@ ${s.address}", SlimeGreen, 10)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PatternDialog(
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> String,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Raise,
        title = { Text(title, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = fieldColors(),
                )
                error?.let { Text(it, color = Bad, fontSize = 12.sp) }
                Text(hint, color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = onSave(text)
                if (r == "OK") onDismiss() else error = r
            }) { Text("Simpan", color = SlimeGreen, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val r = onSave("")
                    if (r == "OK") onDismiss() else error = r
                }) { Text("Bawaan", color = Muted) }
                TextButton(onClick = onDismiss) { Text("Batal", color = Muted) }
            }
        },
    )
}

// ---------------------------------------------------------------------- Offset

@Composable
private fun OffsetsTab(vm: SlimeViewModel) {
    val snap by vm.nativeState.collectAsState()
    val overrides by vm.offsetOverrides.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    val rows = snap.offsets.filter { query.isBlank() || it.first.contains(query, true) }

    editing?.let { name ->
        val current = snap.offsets.firstOrNull { it.first == name }?.second ?: 0
        OffsetDialog(
            name = name,
            initial = "0x" + Integer.toHexString(overrides[name] ?: current).uppercase(),
            onDismiss = { editing = null },
            onSave = { vm.applyOffset(name, it) },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            ActionRow {
                Badge("${snap.offsets.size} offset")
                Badge("${snap.offsets.count { it.second != 0 }} terisi", good = snap.offsets.any { it.second != 0 })
                Badge("${overrides.size} override", warn = overrides.isNotEmpty())
            }
        }
        item { SearchField(query, { query = it }, "Cari offset") }
        if (snap.offsets.isEmpty()) {
            item { Text("Tabel kosong. Jalankan Init preloader dulu.", color = Muted, fontSize = 13.sp) }
        }
        items(rows, key = { it.first }) { (name, value) ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Raise)
                    .clickable { editing = name }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = Fg, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (overrides.containsKey(name)) {
                    Badge("override", warn = true)
                    Spacer(Modifier.width(6.dp))
                }
                MonoText("0x" + Integer.toHexString(value).uppercase(), if (value == 0) Faint else SlimeGreen, 12)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun OffsetDialog(name: String, initial: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parse(): Int? {
        val t = text.trim()
        return if (t.startsWith("0x", true)) t.substring(2).toIntOrNull(16) else t.toIntOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Raise,
        title = { Text(name, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    colors = fieldColors(),
                )
                error?.let { Text(it, color = Bad, fontSize = 12.sp) }
                Text("Hex (0x1B8) atau desimal. Nilai 0 = belum diisi.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = parse()
                if (v == null) error = "Angka tidak valid" else { onSave(v); onDismiss() }
            }) { Text("Simpan", color = SlimeGreen, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Muted) } },
    )
}

// ------------------------------------------------------------------------ Alat

@Composable
private fun ToolsTab(vm: SlimeViewModel) {
    val snap by vm.nativeState.collectAsState()
    val busy by vm.busy.collectAsState()
    val scanOut by vm.scanOutput.collectAsState()
    val selfTest by vm.selfTest.collectAsState()
    var module by rememberSaveable { mutableStateOf("libminecraftpe.so") }
    var pattern by rememberSaveable { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Text("Uji pattern pada modul", color = Fg, fontWeight = FontWeight.Bold)
                Text("Memindai segmen yang bisa dibaca dari modul yang termuat di proses Slime.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = module, onValueChange = { module = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Nama modul", fontSize = 12.sp) }, colors = fieldColors(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = pattern, onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text("Pattern (mis. 48 8B ?? 90)", fontSize = 12.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                ActionBtn("Uji scan", primary = true, enabled = !busy && module.isNotBlank() && pattern.isNotBlank()) {
                    vm.scanModule(module, pattern)
                }
                if (scanOut.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MonoText(scanOut, if (scanOut.startsWith("COCOK")) SlimeGreen else Muted, 11)
                }
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Self-test native", color = Fg, fontWeight = FontWeight.Bold)
                    ActionBtn("Jalankan", primary = true, enabled = !busy) { vm.runSelfTest() }
                }
                if (selfTest.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    selfTest.forEach { line ->
                        MonoText(line, if (line.startsWith("PASS")) SlimeGreen else Bad, 11)
                    }
                    Spacer(Modifier.height(4.dp))
                    val ok = selfTest.count { it.startsWith("PASS") }
                    Text("$ok/${selfTest.size} lulus", color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Text("Modul di proses ini (${snap.modules.size}) — ketuk untuk mengisi nama modul", color = Muted, fontSize = 12.sp)
        }
        items(snap.modules) { m ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Raise)
                    .clickable { module = m.name }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    MonoText(m.name, Fg, 12, maxLines = 1)
                    MonoText("${m.base} · ${m.size / 1024} KB", Faint, 10)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ------------------------------------------------------------------------- Log

@Composable
private fun LogTab(vm: SlimeViewModel) {
    val logs by vm.logs.collectAsState()
    val clipboard = LocalClipboardManager.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            ActionRow {
                ActionBtn("Salin log") {
                    clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                    vm.flash("Log disalin")
                }
                Badge("${logs.size} baris")
            }
        }
        items(logs.size) { i ->
            MonoText(logs[i], if (i == 0) Fg else Muted, 11)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
