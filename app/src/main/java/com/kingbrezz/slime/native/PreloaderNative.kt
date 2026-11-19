package com.kingbrezz.slime.native

/**
 * JNI facade for libslime_preloader.so
 *
 * Every call is guarded: if the library is missing (or an old .so lacks a symbol) the
 * Java UI keeps working and the call returns a safe default.
 *
 * Status honesty:
 * - Signatures are placeholders until filled (Mod Menu → Signature) for each MC build.
 * - tryInstallHook only succeeds with resolved addresses + in-process load + a hook backend.
 */
object PreloaderNative {
    data class Status(
        val phase: String,
        val versionId: String,
        val message: String,
        val sigResolved: Int,
        val sigTotal: Int,
        val hooksActive: Int,
        val hooksPending: Int,
        val canInject: Boolean,
        val targetFound: Boolean = false,
        val targetBase: String = "0x0",
    )

    data class HookInfo(
        val name: String,
        val sigName: String,
        val status: String,
        val target: String,
        val reason: String,
    )

    data class SigInfo(
        val name: String,
        val pattern: String,
        val resolved: Boolean,
        val address: String,
    )

    data class ProcModule(val name: String, val base: String, val size: Long)

    sealed interface ScanResult {
        data class Match(val address: String, val offset: String, val base: String) : ScanResult
        data class NoMatch(val base: String) : ScanResult
        data object NotFound : ScanResult
        data class Error(val message: String) : ScanResult
    }

    @Volatile
    var libraryLoaded: Boolean = false
        private set

    @Volatile
    var loadError: String? = null
        private set

    @Synchronized
    fun ensureLoaded(absolutePath: String? = null): Boolean {
        if (libraryLoaded) return true
        return try {
            if (absolutePath.isNullOrBlank()) System.loadLibrary("slime_preloader")
            else System.load(absolutePath)
            libraryLoaded = true
            loadError = null
            true
        } catch (t: Throwable) {
            libraryLoaded = false
            loadError = t.message ?: t.javaClass.simpleName
            false
        }
    }

    private inline fun <T> guard(default: T, block: () -> T): T {
        if (!libraryLoaded) return default
        return try {
            block()
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
            default
        }
    }

    fun emptyStatus(message: String = "idle") =
        Status("Idle", "", message, 0, 0, 0, 0, false)

    // ---------------------------------------------------------------- lifecycle

    fun init(versionId: String): Boolean = guard(false) { nativeInit(versionId) }

    fun shutdown() = guard(Unit) { nativeShutdown() }

    fun status(): Status {
        if (!libraryLoaded) {
            return Status("Error", "", "native library not loaded: ${loadError ?: "unknown"}", 0, 0, 0, 0, false)
        }
        return guard(Status("Error", "", loadError ?: "status failed", 0, 0, 0, 0, false)) {
            val a = nativeStatus()
            Status(
                phase = a.getOrElse(0) { "Idle" },
                versionId = a.getOrElse(1) { "" },
                message = a.getOrElse(2) { "" },
                sigResolved = a.getOrElse(3) { "0" }.toIntOrNull() ?: 0,
                sigTotal = a.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
                hooksActive = a.getOrElse(5) { "0" }.toIntOrNull() ?: 0,
                hooksPending = a.getOrElse(6) { "0" }.toIntOrNull() ?: 0,
                canInject = a.getOrElse(7) { "0" } == "1",
                targetFound = a.getOrElse(8) { "0" } == "1",
                targetBase = a.getOrElse(9) { "0x0" },
            )
        }
    }

    fun libraryVersion(): String {
        if (!libraryLoaded) return "unavailable"
        return guard("error") { nativeLibraryVersion() }
    }

    // ------------------------------------------------------------------ modules

    fun setModule(id: String, enabled: Boolean) = guard(Unit) { nativeSetModule(id, enabled) }

    fun isModuleEnabled(id: String): Boolean = guard(false) { nativeIsModuleEnabled(id) }

    fun enabledModuleCount(): Int = guard(0) { nativeEnabledModuleCount() }

    fun setModuleValue(id: String, key: String, value: Double) =
        guard(Unit) { nativeSetModuleValue(id, key, value) }

    fun getModuleValue(id: String, key: String, def: Double): Double =
        guard(def) { nativeGetModuleValue(id, key, def) }

    /** Flags as seen by the native side: id → enabled. */
    fun moduleFlags(): Map<String, Boolean> = guard(emptyMap()) {
        nativeDumpModules().mapNotNull { row ->
            val p = row.split("|")
            if (p.size == 2) p[0] to (p[1] == "1") else null
        }.toMap()
    }

    /** Preview what the game would see for kind = fov | gamma | fog | time | viewmodel. */
    fun evalEffect(kind: String, base: Double): Double? = guard(null) {
        val v = nativeEvalEffect(kind, base)
        if (v.isNaN()) null else v
    }

    // -------------------------------------------------------------------- hooks

    fun tryInstallHook(name: String): Boolean = guard(false) { nativeTryInstallHook(name) }

    fun installAllHooks(): Int = guard(0) { nativeInstallAllHooks() }

    fun hooks(): List<HookInfo> = guard(emptyList()) {
        nativeDumpHooks().mapNotNull { row ->
            val p = row.split("|", limit = 5)
            if (p.size == 5) HookInfo(p[0], p[1], p[2], p[3], p[4]) else null
        }
    }

    // ------------------------------------------------- signatures + offsets

    fun signatures(): List<SigInfo> = guard(emptyList()) {
        nativeDumpSignatureDetail().mapNotNull { row ->
            val p = row.split("|", limit = 4)
            if (p.size == 4) SigInfo(p[0], p[1], p[2] == "RESOLVED", p[3]) else null
        }
    }

    /** Returns "OK" or an error message. Blank pattern removes the override. */
    fun setSignature(name: String, pattern: String): String =
        guard("native library tidak termuat") { nativeSetSignature(name, pattern) }

    fun setOffset(name: String, value: Int) = guard(Unit) { nativeSetOffset(name, value) }

    fun clearOverrides() = guard(Unit) { nativeClearOverrides() }

    fun offsets(): List<Pair<String, Int>> = guard(emptyList()) {
        nativeDumpOffsets().mapNotNull { row ->
            val i = row.lastIndexOf('=')
            val v = if (i > 0) row.substring(i + 1).toIntOrNull() else null
            if (v != null) row.substring(0, i) to v else null
        }
    }

    fun dumpOffsets(): List<String> {
        if (!libraryLoaded) return listOf("native not loaded")
        return guard(listOf("error: ${loadError ?: "?"}")) { nativeDumpOffsets().toList() }
    }

    fun dumpSignatures(): List<String> {
        if (!libraryLoaded) return listOf("native not loaded")
        return guard(listOf("error: ${loadError ?: "?"}")) { nativeDumpSignatures().toList() }
    }

    // ------------------------------------------------------------ process tools

    fun processModules(): List<ProcModule> = guard(emptyList()) {
        nativeListProcessModules().mapNotNull { row ->
            val p = row.split("|")
            if (p.size == 3) ProcModule(p[0], p[1], p[2].toLongOrNull() ?: 0L) else null
        }
    }

    /** Pattern tester against a module mapped in THIS process. */
    fun scanModule(module: String, pattern: String): ScanResult {
        if (!libraryLoaded) return ScanResult.Error("native library tidak termuat")
        return guard<ScanResult>(ScanResult.Error(loadError ?: "scan gagal")) {
            val raw = nativeScanModule(module, pattern)
            val p = raw.split("|")
            when (p[0]) {
                "OK" -> ScanResult.Match(p.getOrElse(1) { "?" }, p.getOrElse(2) { "?" }, p.getOrElse(3) { "?" })
                "NOMATCH" -> ScanResult.NoMatch(p.getOrElse(1) { "?" })
                "NOTFOUND" -> ScanResult.NotFound
                else -> ScanResult.Error(p.drop(1).joinToString("|").ifBlank { raw })
            }
        }
    }

    fun selfTest(): List<String> {
        if (!libraryLoaded) return listOf("FAIL native library tidak termuat: ${loadError ?: "?"}")
        return guard(listOf("FAIL self-test error: ${loadError ?: "?"}")) { nativeSelfTest().toList() }
    }

    private external fun nativeInit(versionId: String): Boolean
    private external fun nativeShutdown()
    private external fun nativeStatus(): Array<String>
    private external fun nativeSetModule(id: String, enabled: Boolean)
    private external fun nativeIsModuleEnabled(id: String): Boolean
    private external fun nativeEnabledModuleCount(): Int
    private external fun nativeSetModuleValue(id: String, key: String, value: Double)
    private external fun nativeGetModuleValue(id: String, key: String, def: Double): Double
    private external fun nativeDumpModules(): Array<String>
    private external fun nativeEvalEffect(kind: String, base: Double): Double
    private external fun nativeTryInstallHook(name: String): Boolean
    private external fun nativeInstallAllHooks(): Int
    private external fun nativeDumpHooks(): Array<String>
    private external fun nativeLibraryVersion(): String
    private external fun nativeDumpOffsets(): Array<String>
    private external fun nativeDumpSignatures(): Array<String>
    private external fun nativeDumpSignatureDetail(): Array<String>
    private external fun nativeSetSignature(name: String, pattern: String): String
    private external fun nativeSetOffset(name: String, value: Int)
    private external fun nativeClearOverrides()
    private external fun nativeListProcessModules(): Array<String>
    private external fun nativeScanModule(module: String, pattern: String): String
    private external fun nativeSelfTest(): Array<String>
}
