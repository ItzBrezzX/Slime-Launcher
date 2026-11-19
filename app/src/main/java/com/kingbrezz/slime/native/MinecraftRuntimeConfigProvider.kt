package com.kingbrezz.slime.native

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.data.ModuleState

/**
 * Reads the launch configuration saved by SlimeRepository.
 *
 * The launcher-owned Minecraft runtime runs under the same Android package
 * and UID, so it can read the same private preferences without exposing an
 * exported IPC endpoint. All parsing is defensive because preferences may
 * be absent or left by an older launcher version.
 */
object MinecraftRuntimeConfigProvider {
    data class Snapshot(
        val versionId: String,
        val moduleFlags: Map<String, Boolean>,
        val moduleValues: Map<String, Double>,
        val signatureOverrides: Map<String, String>,
        val offsetOverrides: Map<String, Int>,
    )

    private const val PREFS = "slime_v1"
    private const val KEY_ACTIVE_VERSION = "active_version"
    private const val KEY_MODULES = "modules"
    private const val KEY_MODULE_VALUES = "module_values"
    private const val KEY_SIG_OVERRIDES = "sig_overrides"
    private const val KEY_OFFSET_OVERRIDES = "offset_overrides"

    private val gson = Gson()
    private val moduleType = object : TypeToken<Map<String, ModuleState>>() {}.type
    private val valueType = object : TypeToken<Map<String, Float>>() {}.type
    private val signatureType = object : TypeToken<Map<String, String>>() {}.type
    private val offsetType = object : TypeToken<Map<String, Int>>() {}.type

    @JvmStatic
    fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val states = parse(prefs.getString(KEY_MODULES, null), moduleType)
            ?: Catalog.defaultModuleStates()
        val mergedStates = Catalog.defaultModuleStates().apply {
            states.forEach { (id, state) -> put(id, state) }
        }

        val values = parse<Map<String, Float>>(prefs.getString(KEY_MODULE_VALUES, null), valueType)
            .orEmpty()
        val signatures = parse<Map<String, String>>(prefs.getString(KEY_SIG_OVERRIDES, null), signatureType)
            .orEmpty()
        val offsets = parse<Map<String, Int>>(prefs.getString(KEY_OFFSET_OVERRIDES, null), offsetType)
            .orEmpty()

        return Snapshot(
            versionId = prefs.getString(KEY_ACTIVE_VERSION, "26.80") ?: "26.80",
            moduleFlags = mergedStates.mapValues { it.value.enabled },
            moduleValues = values.mapValues { it.value.toDouble() },
            signatureOverrides = signatures,
            offsetOverrides = offsets,
        )
    }

    private inline fun <reified T> parse(json: String?, type: java.lang.reflect.Type): T? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson<T>(json, type)
        } catch (_: Throwable) {
            null
        }
    }
}
