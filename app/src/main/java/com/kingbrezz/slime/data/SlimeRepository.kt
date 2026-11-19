package com.kingbrezz.slime.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kingbrezz.slime.native.InjectEngine
import com.kingbrezz.slime.native.RuntimeHostClient
import com.kingbrezz.slime.native.PreloaderNative
import kotlinx.coroutines.flow.update

class SlimeRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("slime_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val injectEngine = InjectEngine(app)

    private val _lastInject = MutableStateFlow<InjectEngine.Result?>(null)
    val lastInject: StateFlow<InjectEngine.Result?> = _lastInject.asStateFlow()

    private val _versions = MutableStateFlow(loadVersions())
    val versions: StateFlow<List<McVersion>> = _versions.asStateFlow()

    private val _modules = MutableStateFlow<Map<String, ModuleState>>(loadModules())
    val modules: StateFlow<Map<String, ModuleState>> = _modules.asStateFlow()

    private val _configs = MutableStateFlow(loadConfigs())
    val configs: StateFlow<List<ConfigProfile>> = _configs.asStateFlow()

    private val _library = MutableStateFlow(loadLibrary())
    val library: StateFlow<List<LibraryItem>> = _library.asStateFlow()

    private val _activeVersionId = MutableStateFlow(prefs.getString(KEY_ACTIVE_VERSION, "26.80") ?: "26.80")
    val activeVersionId: StateFlow<String> = _activeVersionId.asStateFlow()

    private val _activeConfigId = MutableStateFlow(prefs.getString(KEY_ACTIVE_CONFIG, "default") ?: "default")
    val activeConfigId: StateFlow<String> = _activeConfigId.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, Catalog.CLIENT_AUTHOR) ?: Catalog.CLIENT_AUTHOR)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _autoLaunchMc = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LAUNCH_MC, true))
    val autoLaunchMc: StateFlow<Boolean> = _autoLaunchMc.asStateFlow()

    private val _logs = MutableStateFlow(
        listOf(
            "Slime ${Catalog.CLIENT_VERSION} siap · ${Catalog.CLIENT_AUTHOR}",
            "Versi aktif ${_activeVersionId.value}",
        ),
    )
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Numeric module settings, key = "moduleId.settingKey". */
    private val _moduleValues = MutableStateFlow(loadModuleValues())
    val moduleValues: StateFlow<Map<String, Float>> = _moduleValues.asStateFlow()

    /** Runtime signature / offset overrides (Mod Menu), pushed to native on start. */
    private val _sigOverrides = MutableStateFlow(loadSigOverrides())
    val sigOverrides: StateFlow<Map<String, String>> = _sigOverrides.asStateFlow()

    private val _offsetOverrides = MutableStateFlow(loadOffsetOverrides())
    val offsetOverrides: StateFlow<Map<String, Int>> = _offsetOverrides.asStateFlow()

    init {
        syncNative()
    }

    fun activeVersion(): McVersion? = _versions.value.find { it.id == _activeVersionId.value }

    fun enabledModuleCount(): Int = _modules.value.count { it.value.enabled }

    fun libraryOnCount(): Int = _library.value.count { it.enabled }

    fun isDirty(): Boolean {
        val cfg = _configs.value.find { it.id == _activeConfigId.value } ?: return false
        return cfg.modules.mapValues { it.value.enabled to it.value.keybind } !=
            _modules.value.mapValues { it.value.enabled to it.value.keybind }
    }

    fun setDisplayName(name: String) {
        val trimmed = name.take(24)
        _displayName.value = trimmed
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
    }

    fun setAutoLaunchMc(on: Boolean) {
        _autoLaunchMc.value = on
        prefs.edit().putBoolean(KEY_AUTO_LAUNCH_MC, on).apply()
    }

    fun setActiveVersion(id: String) {
        if (_versions.value.none { it.id == id }) return
        _activeVersionId.value = id
        prefs.edit().putString(KEY_ACTIVE_VERSION, id).apply()
        log("Versi aktif → $id")
    }

    fun installVersion(id: String) {
        _versions.update { list ->
            list.map {
                if (it.id == id) it.copy(installed = true) else it
            }
        }
        persistVersions()
        log("$id terpasang")
        flash("$id siap")
    }

    fun uninstallVersion(id: String) {
        if (id == _activeVersionId.value) {
            flash("Pindahkan versi aktif dulu")
            return
        }
        _versions.update { list -> list.map { if (it.id == id) it.copy(installed = false) else it } }
        persistVersions()
        log("$id dihapus")
    }

    fun toggleIsolation(id: String) {
        _versions.update { list ->
            list.map {
                if (it.id == id && it.installed) it.copy(isolated = !it.isolated) else it
            }
        }
        persistVersions()
    }

    fun renameVersion(id: String, alias: String) {
        _versions.update { list ->
            list.map { if (it.id == id) it.copy(alias = alias.take(28).ifBlank { it.version }) else it }
        }
        persistVersions()
    }

    fun toggleModule(id: String) {
        _modules.update { map ->
            val cur = map[id] ?: return@update map
            val next = !cur.enabled
            PreloaderNative.setModule(id, next)
            map + (id to cur.copy(enabled = next))
        }
        persistModules()
    }

    fun setModuleKeybind(id: String, key: String?) {
        _modules.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to cur.copy(keybind = key))
        }
        persistModules()
    }

    fun loadConfig(id: String) {
        val cfg = _configs.value.find { it.id == id } ?: return
        _modules.value = cfg.modules.mapValues { (_, v) -> ModuleState(v.enabled, v.keybind) }.toMutableMap()
        _activeConfigId.value = id
        prefs.edit().putString(KEY_ACTIVE_CONFIG, id).apply()
        persistModules()
        syncNative()
        log("Config ${cfg.name} dimuat")
        flash("Memuat ${cfg.name}")
    }

    fun saveActiveConfig() {
        val id = _activeConfigId.value
        _configs.update { list ->
            list.map { cfg ->
                if (cfg.id == id) {
                    cfg.copy(modules = _modules.value.mapValues { (_, v) -> ModuleState(v.enabled, v.keybind) })
                } else cfg
            }
        }
        persistConfigs()
        flash("Config aktif disimpan")
    }

    fun saveAs(name: String) {
        val trimmed = name.trim().take(32)
        if (trimmed.isEmpty()) {
            flash("Beri nama config")
            return
        }
        val profile = ConfigProfile(
            id = "cfg_${System.currentTimeMillis()}",
            name = trimmed,
            author = _displayName.value,
            note = "Buatanmu",
            modules = _modules.value.mapValues { (_, v) -> ModuleState(v.enabled, v.keybind) },
        )
        _configs.update { listOf(profile) + it }
        _activeConfigId.value = profile.id
        prefs.edit().putString(KEY_ACTIVE_CONFIG, profile.id).apply()
        persistConfigs()
        flash("Config $trimmed dibuat")
    }

    fun deleteConfig(id: String) {
        if (_configs.value.size < 2) {
            flash("Sisakan minimal satu config")
            return
        }
        val next = _configs.value.filter { it.id != id }
        _configs.value = next
        if (_activeConfigId.value == id) {
            val first = next.first()
            _activeConfigId.value = first.id
            _modules.value = first.modules.mapValues { (_, v) -> ModuleState(v.enabled, v.keybind) }.toMutableMap()
            prefs.edit().putString(KEY_ACTIVE_CONFIG, first.id).apply()
            persistModules()
            syncNative()
        }
        persistConfigs()
    }

    fun toggleLibrary(id: String) {
        _library.update { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
        persistLibrary()
    }

    fun isMinecraftInstalled(): Boolean {
        return try {
            app.packageManager.getPackageInfo(Catalog.MC_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Launch official Minecraft Bedrock if installed. Returns true if intent fired. */
    fun launchMinecraft(): Boolean = injectEngine.launchMinecraft()

    /** Full live-inject pipeline: native → preloader → modules → launch MC → attach attempt. */
    fun runInjectPipeline(): InjectEngine.Result {
        syncNative()
        val flags = _modules.value.mapValues { it.value.enabled }
        val result = injectEngine.run(
            versionId = _activeVersionId.value,
            moduleFlags = flags,
            autoLaunchGame = _autoLaunchMc.value,
            moduleValues = _moduleValues.value.mapValues { it.value.toDouble() },
            signatureOverrides = _sigOverrides.value,
            offsetOverrides = _offsetOverrides.value,
        )
        _lastInject.value = result
        result.lines.forEach { log(it) }
        flash(result.summary)
        return result
    }

    fun log(text: String) {
        _logs.update { (listOf(text) + it).take(40) }
    }

    fun initPreloader(): String {
        syncNative()
        PreloaderNative.init(_activeVersionId.value)
        val host = RuntimeHostClient.start(
            context = app,
            versionId = _activeVersionId.value,
            moduleFlags = _modules.value.mapValues { it.value.enabled },
            moduleValues = _moduleValues.value.mapValues { it.value.toDouble() },
            signatureOverrides = _sigOverrides.value,
            offsetOverrides = _offsetOverrides.value,
        )
        val msg = if (host.libraryLoaded) {
            "Runtime host ${host.phase}: ${host.message} (ABI ${host.abi}, sig ${host.sigResolved}/${host.sigTotal})"
        } else {
            "Runtime host gagal: ${host.error ?: host.message}"
        }
        log(msg)
        return msg
    }

    fun shutdownPreloader(): String {
        val host = RuntimeHostClient.shutdown(app)
        PreloaderNative.shutdown()
        syncNative() // keep launcher-side UI state authoritative
        log("Runtime host shutdown: ${host.message}")
        return "Runtime host dimatikan"
    }

    /** Push flags, numeric values and signature/offset overrides to the native side. */
    fun syncNative() {
        if (!PreloaderNative.libraryLoaded) PreloaderNative.ensureLoaded()
        if (!PreloaderNative.libraryLoaded) return
        _modules.value.forEach { (id, st) -> PreloaderNative.setModule(id, st.enabled) }
        Catalog.modules.forEach { def ->
            def.settings.forEach { s ->
                PreloaderNative.setModuleValue(def.id, s.key, moduleValue(def.id, s.key).toDouble())
            }
        }
        _sigOverrides.value.forEach { (name, pattern) -> PreloaderNative.setSignature(name, pattern) }
        _offsetOverrides.value.forEach { (name, value) -> PreloaderNative.setOffset(name, value) }
    }

    fun moduleValue(moduleId: String, key: String): Float =
        _moduleValues.value["$moduleId.$key"] ?: Catalog.settingDefault(moduleId, key)

    fun setModuleValue(moduleId: String, key: String, value: Float) {
        _moduleValues.update { it + ("$moduleId.$key" to value) }
        PreloaderNative.setModuleValue(moduleId, key, value.toDouble())
        prefs.edit().putString(KEY_MODULE_VALUES, gson.toJson(_moduleValues.value)).apply()
    }

    fun resetModuleValues() {
        _moduleValues.value = emptyMap()
        prefs.edit().remove(KEY_MODULE_VALUES).apply()
        syncNative()
        log("Nilai modul direset")
    }

    fun setAllModules(on: Boolean) {
        _modules.update { map -> map.mapValues { (_, v) -> v.copy(enabled = on) } }
        persistModules()
        syncNative()
        log(if (on) "Semua modul dinyalakan" else "Semua modul dimatikan")
    }

    fun resetModulesToDefaults() {
        _modules.value = Catalog.defaultModuleStates()
        persistModules()
        syncNative()
        log("Modul dikembalikan ke default")
    }

    /** Returns "OK" or an error message. Blank pattern removes the override. */
    fun setSignaturePattern(name: String, pattern: String): String {
        val trimmed = pattern.trim()
        val res = PreloaderNative.setSignature(name, trimmed)
        if (res == "OK") {
            _sigOverrides.update { if (trimmed.isEmpty()) it - name else it + (name to trimmed) }
            prefs.edit().putString(KEY_SIG_OVERRIDES, gson.toJson(_sigOverrides.value)).apply()
            log(if (trimmed.isEmpty()) "Pattern $name dikembalikan" else "Pattern $name diubah")
        }
        return res
    }

    fun setOffsetValue(name: String, value: Int) {
        PreloaderNative.setOffset(name, value)
        _offsetOverrides.update { it + (name to value) }
        prefs.edit().putString(KEY_OFFSET_OVERRIDES, gson.toJson(_offsetOverrides.value)).apply()
        log("Offset $name = 0x${Integer.toHexString(value)}")
    }

    fun clearOverrides() {
        PreloaderNative.clearOverrides()
        _sigOverrides.value = emptyMap()
        _offsetOverrides.value = emptyMap()
        prefs.edit().remove(KEY_SIG_OVERRIDES).remove(KEY_OFFSET_OVERRIDES).apply()
        log("Semua override pattern/offset dihapus")
    }

    fun overrideCount(): Int = _sigOverrides.value.size + _offsetOverrides.value.size

    fun preloaderStatusLine(): String {
        val st = PreloaderNative.status()
        val lib = if (PreloaderNative.libraryLoaded) PreloaderNative.libraryVersion() else "no-so"
        return "$lib · ${st.phase} · sig ${st.sigResolved}/${st.sigTotal} · hooks ${st.hooksActive}/${st.hooksPending} · target ${if (st.targetFound) "ya" else "tidak"}"
    }

    fun flash(text: String) {
        _toast.value = text
    }

    fun clearToast() {
        _toast.value = null
    }

    private fun loadVersions(): List<McVersion> {
        val json = prefs.getString(KEY_VERSIONS, null) ?: return Catalog.versions.map { it.copy() }
        return try {
            val type = object : TypeToken<List<McVersion>>() {}.type
            gson.fromJson<List<McVersion>>(json, type) ?: Catalog.versions.map { it.copy() }
        } catch (_: Exception) {
            Catalog.versions.map { it.copy() }
        }
    }

    private fun loadModules(): MutableMap<String, ModuleState> {
        val json = prefs.getString(KEY_MODULES, null) ?: return Catalog.defaultModuleStates()
        return try {
            val type = object : TypeToken<Map<String, ModuleState>>() {}.type
            val parsed = gson.fromJson<Map<String, ModuleState>>(json, type) ?: emptyMap()
            Catalog.defaultModuleStates().apply {
                parsed.forEach { (k, v) -> put(k, v) }
            }
        } catch (_: Exception) {
            Catalog.defaultModuleStates()
        }
    }

    private fun loadConfigs(): List<ConfigProfile> {
        val json = prefs.getString(KEY_CONFIGS, null) ?: return Catalog.defaultConfigs()
        return try {
            val type = object : TypeToken<List<ConfigProfile>>() {}.type
            gson.fromJson<List<ConfigProfile>>(json, type) ?: Catalog.defaultConfigs()
        } catch (_: Exception) {
            Catalog.defaultConfigs()
        }
    }

    private fun loadLibrary(): List<LibraryItem> {
        val json = prefs.getString(KEY_LIBRARY, null) ?: return Catalog.library.map { it.copy() }
        return try {
            val type = object : TypeToken<List<LibraryItem>>() {}.type
            gson.fromJson<List<LibraryItem>>(json, type) ?: Catalog.library.map { it.copy() }
        } catch (_: Exception) {
            Catalog.library.map { it.copy() }
        }
    }

    private fun loadModuleValues(): Map<String, Float> {
        val json = prefs.getString(KEY_MODULE_VALUES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Float>>() {}.type
            gson.fromJson<Map<String, Float>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun loadSigOverrides(): Map<String, String> {
        val json = prefs.getString(KEY_SIG_OVERRIDES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun loadOffsetOverrides(): Map<String, Int> {
        val json = prefs.getString(KEY_OFFSET_OVERRIDES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson<Map<String, Int>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistVersions() {
        prefs.edit().putString(KEY_VERSIONS, gson.toJson(_versions.value)).apply()
    }

    private fun persistModules() {
        prefs.edit().putString(KEY_MODULES, gson.toJson(_modules.value)).apply()
    }

    private fun persistConfigs() {
        prefs.edit().putString(KEY_CONFIGS, gson.toJson(_configs.value)).apply()
    }

    private fun persistLibrary() {
        prefs.edit().putString(KEY_LIBRARY, gson.toJson(_library.value)).apply()
    }

    companion object {
        private const val KEY_VERSIONS = "versions"
        private const val KEY_MODULES = "modules"
        private const val KEY_CONFIGS = "configs"
        private const val KEY_LIBRARY = "library"
        private const val KEY_ACTIVE_VERSION = "active_version"
        private const val KEY_ACTIVE_CONFIG = "active_config"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AUTO_LAUNCH_MC = "auto_launch_mc"
        private const val KEY_MODULE_VALUES = "module_values"
        private const val KEY_SIG_OVERRIDES = "sig_overrides"
        private const val KEY_OFFSET_OVERRIDES = "offset_overrides"
    }
}
