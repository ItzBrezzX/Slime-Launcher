package com.kingbrezz.slime.data

data class McVersion(
    val id: String,
    val version: String,
    val channel: String,
    val pkg: String,
    val arch: String,
    val sizeMb: Int,
    val supported: Boolean,
    val released: String,
    var installed: Boolean = false,
    var isolated: Boolean = true,
    var alias: String = version,
)

/** A numeric knob of a module (slider in the Mod Menu, pushed to native ModuleBridge). */
data class SettingDef(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val def: Float,
    val unit: String = "",
)

data class ModuleDef(
    val id: String,
    val name: String,
    val category: String,
    val blurb: String,
    val defaultOn: Boolean = false,
    val keybind: String? = null,
    val settings: List<SettingDef> = emptyList(),
)

data class ModuleState(
    var enabled: Boolean = false,
    var keybind: String? = null,
)

data class ConfigProfile(
    val id: String,
    val name: String,
    val author: String,
    val note: String,
    val modules: Map<String, ModuleState>,
)

data class LibraryItem(
    val id: String,
    val kind: String,
    val name: String,
    val detail: String,
    var enabled: Boolean = false,
)

object Catalog {
    const val CLIENT_NAME = "Slime"
    const val CLIENT_VERSION = "1.5.0"
    const val CLIENT_AUTHOR = "KingBrezz"
    const val MC_PACKAGE = "com.mojang.minecraftpe"

    val versions: List<McVersion> = listOf(
        McVersion("26.81", "26.81.2", "Preview", "GDK", "x64", 846, false, "12 Nov 2026"),
        McVersion("26.80", "26.80.1", "Release", "GDK", "x64", 838, true, "4 Nov 2026", installed = true, alias = "Utama"),
        McVersion("26.72", "26.72.4", "Preview", "GDK", "x64", 840, true, "28 Okt 2026"),
        McVersion("26.70", "26.70.2", "Release", "GDK", "x64", 829, true, "21 Okt 2026", installed = true, isolated = false, alias = "Stabil"),
        McVersion("26.60", "26.60.3", "Release", "GDK", "x64", 812, true, "6 Okt 2026", installed = true, alias = "Cadangan"),
        McVersion("26.40", "26.40.1", "Release", "UWP", "x64", 790, true, "12 Agu 2026", alias = "UWP"),
        McVersion("1.21.130", "1.21.130", "Release", "UWP", "x64", 744, false, "2025", alias = "Lama"),
    )

    val modules: List<ModuleDef> = listOf(
        ModuleDef("fullbright", "Fullbright", "Visual", "Naikkan cahaya malam/gua", false,
            settings = listOf(SettingDef("gamma", "Gamma", 1f, 15f, 10f))),
        ModuleDef("zoom", "Zoom", "Visual", "Perbesar FOV saat tombol ditahan", true, "C",
            settings = listOf(SettingDef("level", "Level zoom", 1.5f, 10f, 4f, "×"))),
        ModuleDef("motionblur", "Motion Blur", "Visual", "Blur saat bergerak", false,
            settings = listOf(SettingDef("strength", "Kekuatan", 0f, 1f, 0.5f))),
        ModuleDef("crosshair", "Custom Crosshair", "Visual", "Bentuk & ukuran bidik", true,
            settings = listOf(SettingDef("size", "Ukuran", 4f, 40f, 16f, "px"))),
        ModuleDef("fog", "Fog", "Visual", "Kepadatan kabut", false,
            settings = listOf(SettingDef("density", "Kepadatan", 0f, 2f, 0.6f, "×"))),
        ModuleDef("time", "Time Changer", "Visual", "Kunci waktu langit (demo)", true,
            settings = listOf(SettingDef("hour", "Jam", 0f, 24f, 12f, "jam"))),
        ModuleDef("nohurtcam", "No Hurt Cam", "Visual", "Matikan guncangan kamera", true),
        ModuleDef("viewmodel", "Viewmodel", "Visual", "Skala tangan/hotbar", true,
            settings = listOf(SettingDef("scale", "Skala", 0.4f, 1.4f, 1f, "×"))),
        ModuleDef("watermark", "Watermark", "HUD", "Cap Slime di sudut", true),
        ModuleDef("fps", "FPS", "HUD", "Hitungan bingkai", true),
        ModuleDef("cps", "CPS", "HUD", "Klik per detik", true),
        ModuleDef("coords", "Coordinates", "HUD", "Koordinat & bioma", true),
        ModuleDef("direction", "Direction HUD", "HUD", "Arah mata angin", true),
        ModuleDef("keystrokes", "Keystrokes", "HUD", "WASD & tombol", true),
        ModuleDef("potions", "Potion HUD", "HUD", "Efek ramuan", true),
        ModuleDef("combo", "Combo Counter", "HUD", "Hitungan kombo", false),
        ModuleDef("chat", "Chat", "HUD", "Cuplikan chat + timestamp", true),
        ModuleDef("waila", "Waila", "HUD", "Blok yang dilihat", true),
        ModuleDef("sprint", "Sprint", "Pemain", "Kunci sprint", true, "R"),
        ModuleDef("sneak", "Sneak", "Pemain", "Toggle sneak", false, "Shift"),
        ModuleDef("chunks", "Chunk Border", "Dunia", "Garis batas chunk", false),
        ModuleDef("weather", "Weather Changer", "Dunia", "Cerah / hujan / kabut (demo)", false),
        ModuleDef("waypoints", "Waypoints", "Dunia", "Penanda Spawn", true),
        ModuleDef("nick", "Nick", "Lain", "Sembunyikan nama tampilan", false),
        ModuleDef("rpc", "Discord RPC", "Lain", "Status di beranda (lokal)", true),
    )

    val categories = listOf("Semua", "Visual", "HUD", "Pemain", "Dunia", "Lain")

    /** Default numeric value of a module setting. */
    fun settingDefault(moduleId: String, key: String): Float =
        modules.firstOrNull { it.id == moduleId }?.settings?.firstOrNull { it.key == key }?.def ?: 0f

    fun defaultModuleStates(): MutableMap<String, ModuleState> =
        modules.associate { it.id to ModuleState(enabled = it.defaultOn, keybind = it.keybind) }.toMutableMap()

    fun defaultConfigs(): List<ConfigProfile> {
        val base = defaultModuleStates()
        fun copy(map: Map<String, ModuleState>): Map<String, ModuleState> =
            map.mapValues { (_, v) -> ModuleState(v.enabled, v.keybind) }
        fun offExcept(vararg keep: String): Map<String, ModuleState> =
            base.mapValues { (id, v) -> ModuleState(enabled = id in keep, keybind = v.keybind) }
        fun withOff(vararg off: String): Map<String, ModuleState> =
            base.mapValues { (id, v) -> ModuleState(enabled = v.enabled && id !in off, keybind = v.keybind) }
        return listOf(
            ConfigProfile("default", "Default", CLIENT_AUTHOR, "HUD harian", copy(base)),
            ConfigProfile(
                "sinematik",
                "Sinematik",
                CLIENT_AUTHOR,
                "Kabut, blur, HUD sepi",
                offExcept("watermark", "crosshair", "zoom", "time", "fog", "motionblur", "weather"),
            ),
            ConfigProfile(
                "kompetitif",
                "HUD Kompetitif",
                CLIENT_AUTHOR,
                "Angka di depan",
                withOff("motionblur", "fog", "fullbright", "weather", "waypoints", "combo"),
            ),
            ConfigProfile(
                "minimal",
                "Minimal",
                CLIENT_AUTHOR,
                "Hampir kosong",
                offExcept("watermark", "crosshair"),
            ),
        )
    }

    val library: List<LibraryItem> = listOf(
        LibraryItem("w1", "world", "Rawa KingBrezz", "Survival · sulit", true),
        LibraryItem("w2", "world", "Pulau Lumut", "Kreatif", false),
        LibraryItem("w3", "world", "Lobby Slime", "Petualangan", true),
        LibraryItem("r1", "resource", "Slime Faithful", "32× · UI klien", true),
        LibraryItem("r2", "resource", "Kaca Bersih", "Kaca tembus", false),
        LibraryItem("r3", "resource", "GUI Gelap", "Hotbar kontras", true),
        LibraryItem("b1", "behavior", "Kuburan pemain", "Peti saat mati", false),
        LibraryItem("b2", "behavior", "Peringatan dura", "Chat alat hampir rusak", true),
        LibraryItem("s1", "skin", "KingBrezz", "Skin penulis", true),
        LibraryItem("s2", "skin", "Gelatin", "Blob hijau", false),
        LibraryItem("v1", "server", "Rawa SMP", "rawa.kingbrezz.net", true),
        LibraryItem("v2", "server", "Gel Lab", "gel.local", false),
    )
}
