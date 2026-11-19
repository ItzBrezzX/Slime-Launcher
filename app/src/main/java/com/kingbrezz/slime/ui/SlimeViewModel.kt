package com.kingbrezz.slime.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.data.ConfigProfile
import com.kingbrezz.slime.data.LibraryItem
import com.kingbrezz.slime.data.McVersion
import com.kingbrezz.slime.data.ModuleDef
import com.kingbrezz.slime.data.ModuleState
import com.kingbrezz.slime.data.SlimeRepository
import com.kingbrezz.slime.native.PreloaderNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the Mod Menu shows about the native side, captured off the main thread. */
data class NativeSnapshot(
    val libLoaded: Boolean = false,
    val libVersion: String = "",
    val loadError: String? = null,
    val status: PreloaderNative.Status = PreloaderNative.emptyStatus(),
    val hooks: List<PreloaderNative.HookInfo> = emptyList(),
    val sigs: List<PreloaderNative.SigInfo> = emptyList(),
    val offsets: List<Pair<String, Int>> = emptyList(),
    val modules: List<PreloaderNative.ProcModule> = emptyList(),
    val overrides: Int = 0,
)

class SlimeViewModel(private val repo: SlimeRepository) : ViewModel() {
    val versions: StateFlow<List<McVersion>> = repo.versions
    val runtimeInstall = repo.runtimeInstall
    val modules: StateFlow<Map<String, ModuleState>> = repo.modules
    val moduleValues: StateFlow<Map<String, Float>> = repo.moduleValues
    val sigOverrides: StateFlow<Map<String, String>> = repo.sigOverrides
    val offsetOverrides: StateFlow<Map<String, Int>> = repo.offsetOverrides
    val configs: StateFlow<List<ConfigProfile>> = repo.configs
    val library: StateFlow<List<LibraryItem>> = repo.library
    val activeVersionId: StateFlow<String> = repo.activeVersionId
    val activeConfigId: StateFlow<String> = repo.activeConfigId
    val displayName: StateFlow<String> = repo.displayName
    val autoLaunchMc: StateFlow<Boolean> = repo.autoLaunchMc
    val logs: StateFlow<List<String>> = repo.logs
    val toast: StateFlow<String?> = repo.toast

    val moduleDefs: List<ModuleDef> = Catalog.modules
    val categories: List<String> = Catalog.categories

    // ------------------------------------------------------------ Mod Menu state

    private val _modMenuOpen = MutableStateFlow(false)
    val modMenuOpen: StateFlow<Boolean> = _modMenuOpen.asStateFlow()

    private val _native = MutableStateFlow(NativeSnapshot())
    val nativeState: StateFlow<NativeSnapshot> = _native.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _scanOutput = MutableStateFlow("")
    val scanOutput: StateFlow<String> = _scanOutput.asStateFlow()

    private val _selfTest = MutableStateFlow<List<String>>(emptyList())
    val selfTest: StateFlow<List<String>> = _selfTest.asStateFlow()

    fun openModMenu() {
        _modMenuOpen.value = true
        refreshNative()
    }

    fun closeModMenu() {
        _modMenuOpen.value = false
    }

    fun toggleModMenu() {
        if (_modMenuOpen.value) closeModMenu() else openModMenu()
    }

    fun refreshNative() {
        viewModelScope.launch(Dispatchers.Default) { _native.value = buildSnapshot() }
    }

    private fun buildSnapshot(): NativeSnapshot = NativeSnapshot(
        libLoaded = PreloaderNative.libraryLoaded,
        libVersion = PreloaderNative.libraryVersion(),
        loadError = PreloaderNative.loadError,
        status = PreloaderNative.status(),
        hooks = PreloaderNative.hooks(),
        sigs = PreloaderNative.signatures(),
        offsets = PreloaderNative.offsets(),
        modules = PreloaderNative.processModules(),
        overrides = repo.overrideCount(),
    )

    /** Runs [block] off the main thread, then refreshes the native snapshot. Ignores taps while busy. */
    private fun work(block: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                block()
            } catch (t: Throwable) {
                repo.flash("Gagal: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                _native.value = buildSnapshot()
                _busy.value = false
            }
        }
    }

    // ------------------------------------------------------------ launcher actions

    fun importMinecraftApks(uris: List<Uri>) = work {
        repo.importMinecraftApks(uris)
    }

    fun removeMinecraftRuntime() = work {
        repo.removeMinecraftRuntime()
    }

    fun activeVersion(): McVersion? = repo.activeVersion()
    fun enabledModuleCount(): Int = repo.enabledModuleCount()
    fun libraryOnCount(): Int = repo.libraryOnCount()
    fun isDirty(): Boolean = repo.isDirty()
    fun isMinecraftInstalled(): Boolean = repo.isMinecraftInstalled()

    fun setDisplayName(name: String) = repo.setDisplayName(name)
    fun setAutoLaunchMc(on: Boolean) = repo.setAutoLaunchMc(on)
    fun setActiveVersion(id: String) = repo.setActiveVersion(id)
    fun installVersion(id: String) = repo.installVersion(id)
    fun uninstallVersion(id: String) = repo.uninstallVersion(id)
    fun toggleIsolation(id: String) = repo.toggleIsolation(id)
    fun renameVersion(id: String, alias: String) = repo.renameVersion(id, alias)
    fun toggleModule(id: String) = repo.toggleModule(id)
    fun setModuleKeybind(id: String, key: String?) = repo.setModuleKeybind(id, key)
    fun loadConfig(id: String) = repo.loadConfig(id)
    fun saveActiveConfig() = repo.saveActiveConfig()
    fun saveAs(name: String) = repo.saveAs(name)
    fun deleteConfig(id: String) = repo.deleteConfig(id)
    fun toggleLibrary(id: String) = repo.toggleLibrary(id)
    fun launchMinecraft(): Boolean = repo.launchMinecraft()
    fun runInjectPipeline(onComplete: () -> Unit = {}) = work {
        repo.runInjectPipeline()
        viewModelScope.launch(Dispatchers.Main) { onComplete() }
    }
    val lastInject = repo.lastInject
    fun log(text: String) = repo.log(text)
    fun initPreloader(): String = repo.initPreloader()
    fun preloaderStatusLine(): String = repo.preloaderStatusLine()
    fun flash(text: String) = repo.flash(text)
    fun clearToast() = repo.clearToast()

    fun moduleEnabled(id: String): Boolean = modules.value[id]?.enabled == true

    // ------------------------------------------------------------ module tools

    fun moduleValue(moduleId: String, key: String): Float = repo.moduleValue(moduleId, key)
    fun setModuleValue(moduleId: String, key: String, value: Float) = repo.setModuleValue(moduleId, key, value)
    fun resetModuleValues() = repo.resetModuleValues()
    fun setAllModules(on: Boolean) = repo.setAllModules(on)
    fun resetModulesToDefaults() = repo.resetModulesToDefaults()

    // --------------------------------------------------------- preloader tools

    fun runPipelineFromMenu() = work {
        val r = repo.runInjectPipeline()
        repo.log(r.summary)
    }

    fun initNative() = work { repo.flash(repo.initPreloader()) }

    fun shutdownNative() = work { repo.flash(repo.shutdownPreloader()) }

    fun launchGameFromMenu() {
        val ok = repo.launchMinecraft()
        repo.flash(if (ok) "Minecraft dibuka" else "Minecraft tidak bisa dibuka (belum terpasang?)")
        repo.log(if (ok) "Minecraft diluncurkan dari Mod Menu" else "Peluncuran Minecraft gagal")
    }

    fun installAllHooks() = work {
        val n = PreloaderNative.installAllHooks()
        repo.log("Install semua hook → aktif $n")
        repo.flash(if (n > 0) "$n hook aktif" else "Belum ada hook aktif — lihat alasan di tiap hook")
    }

    fun installHook(name: String) = work {
        val ok = PreloaderNative.tryInstallHook(name)
        val reason = PreloaderNative.hooks().firstOrNull { it.name == name }?.reason.orEmpty()
        repo.log("hook $name install=$ok")
        repo.flash(if (ok) "$name aktif" else "$name: $reason")
    }

    /** Returns "OK" or an error message (shown inside the edit dialog). Re-inits the preloader on success. */
    fun applySignature(name: String, pattern: String): String {
        val res = repo.setSignaturePattern(name, pattern)
        if (res == "OK") initNative()
        return res
    }

    fun applyOffset(name: String, value: Int) {
        repo.setOffsetValue(name, value)
        initNative()
    }

    fun clearOverrides() {
        repo.clearOverrides()
        initNative()
    }

    fun scanModule(module: String, pattern: String) = work {
        _scanOutput.value = when (val r = PreloaderNative.scanModule(module.trim(), pattern.trim())) {
            is PreloaderNative.ScanResult.Match ->
                "COCOK di ${r.address}\noffset ${r.offset} dari base ${r.base}"
            is PreloaderNative.ScanResult.NoMatch ->
                "Modul ada (base ${r.base}) tapi pattern tidak cocok"
            PreloaderNative.ScanResult.NotFound ->
                "Modul '${module.trim()}' tidak termuat di proses Slime.\n" +
                    "Minecraft stok berjalan di proses lain; pattern hanya bisa diuji pada modul in-process."
            is PreloaderNative.ScanResult.Error -> "Pattern salah: ${r.message}"
        }
        repo.log("scan ${module.trim()} → ${_scanOutput.value.lineSequence().first()}")
    }

    fun runSelfTest() = work {
        val lines = PreloaderNative.selfTest()
        _selfTest.value = lines
        val fail = lines.count { it.startsWith("FAIL") }
        repo.log("Self-test native: ${lines.size - fail}/${lines.size} lulus")
        repo.flash(if (fail == 0) "Self-test lulus (${lines.size})" else "$fail tes gagal")
    }
}

class SlimeViewModelFactory(private val repo: SlimeRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SlimeViewModel::class.java)) {
            return SlimeViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}
