package com.kingbrezz.slime.native

import android.content.Context

/**
 * Entry point called by the Activity/Application that actually owns the
 * Minecraft runtime. This code must execute in the same process after
 * libminecraftpe.so has been loaded by that runtime.
 *
 * The launcher-side :runtime service is deliberately not used here: an
 * Android service in another process cannot see the game's mapped image.
 */
object MinecraftRuntimeBridge {
    data class Result(
        val initialized: Boolean,
        val gameLibraryMapped: Boolean,
        val canAttemptHooks: Boolean,
        val hooksActive: Int,
        val status: PreloaderNative.Status,
        val message: String,
    )

    /** Load the active version and settings directly from Slime's saved launcher config. */
    @JvmStatic
    fun onRuntimeReady(context: Context): Result {
        val config = MinecraftRuntimeConfigProvider.read(context)
        return onRuntimeReady(
            context = context,
            versionId = config.versionId,
            moduleFlags = config.moduleFlags,
            moduleValues = config.moduleValues,
            signatureOverrides = config.signatureOverrides,
            offsetOverrides = config.offsetOverrides,
        )
    }

    /**
     * Call only after the custom runtime has loaded Minecraft's native
     * libraries. The preloader library must be packaged by that runtime APK
     * for the same ABI as the current process.
     */
    @JvmStatic
    fun onRuntimeReady(
        context: Context,
        versionId: String,
        moduleFlags: Map<String, Boolean> = emptyMap(),
        moduleValues: Map<String, Double> = emptyMap(),
        signatureOverrides: Map<String, String> = emptyMap(),
        offsetOverrides: Map<String, Int> = emptyMap(),
    ): Result {
        val entry = RuntimeCoLoadEntry.enter(
            context = context.applicationContext,
            versionId = versionId,
            moduleFlags = moduleFlags,
            moduleValues = moduleValues,
            signatureOverrides = signatureOverrides,
            offsetOverrides = offsetOverrides,
        )
        val status = entry.status
        val mapped = status.targetFound
        val hookGate = entry.initialized && mapped && status.canInject
        val message = when {
            !entry.runtime.preloaderLoaded ->
                entry.runtime.error ?: "libslime_preloader.so tidak tersedia pada runtime APK"
            !entry.initialized ->
                status.message.ifBlank { "Preloader gagal diinisialisasi" }
            !mapped ->
                "libminecraftpe.so belum termuat di proses ini; panggil bridge setelah runtime game siap"
            !status.canInject ->
                "Runtime game terdeteksi, tetapi signature belum siap (resolved " +
                    status.sigResolved + "/" + status.sigTotal + ")"
            else ->
                "Runtime game dan signature terdeteksi; hook backend masih harus tersedia"
        }
        return Result(
            initialized = entry.initialized,
            gameLibraryMapped = mapped,
            canAttemptHooks = hookGate,
            hooksActive = status.hooksActive,
            status = status,
            message = message,
        )
    }

    /** Push live module changes from the in-game runtime host. */
    @JvmStatic
    fun syncModules(
        moduleFlags: Map<String, Boolean>,
        moduleValues: Map<String, Double> = emptyMap(),
    ): Result {
        if (!PreloaderNative.libraryLoaded) {
            val status = PreloaderNative.emptyStatus("preloader belum dimuat pada proses runtime")
            return Result(false, false, false, 0, status, status.message)
        }

        moduleFlags.forEach { (id, enabled) -> PreloaderNative.setModule(id, enabled) }
        moduleValues.forEach { (compoundKey, value) ->
            val split = compoundKey.indexOf('.')
            if (split > 0) {
                PreloaderNative.setModuleValue(
                    compoundKey.substring(0, split),
                    compoundKey.substring(split + 1),
                    value,
                )
            }
        }

        val status = PreloaderNative.status()
        val mapped = status.targetFound
        val gate = mapped && status.canInject
        return Result(
            initialized = true,
            gameLibraryMapped = mapped,
            canAttemptHooks = gate,
            hooksActive = status.hooksActive,
            status = status,
            message = if (mapped) "Konfigurasi modul disinkronkan pada proses runtime" else
                "Konfigurasi tersimpan; libminecraftpe.so belum terdeteksi pada proses runtime",
        )
    }

    /** Pull updated module toggles/values after the launcher UI changes them. */
    @JvmStatic
    fun syncFromLauncher(context: Context): Result {
        val config = MinecraftRuntimeConfigProvider.read(context)
        return syncModules(config.moduleFlags, config.moduleValues)
    }

    /**
     * Install is intentionally gated by same-process game mapping and
     * resolved signatures. The native backend may still reject installation.
     */
    @JvmStatic
    fun installHooks(): Result {
        if (!PreloaderNative.libraryLoaded) {
            val status = PreloaderNative.emptyStatus("preloader belum dimuat pada proses runtime")
            return Result(false, false, false, 0, status, status.message)
        }

        val before = PreloaderNative.status()
        if (!before.targetFound || !before.canInject) {
            val message = if (!before.targetFound) {
                "Ditolak: libminecraftpe.so tidak termuat pada proses pemanggil"
            } else {
                "Ditolak: signature belum siap (resolved " +
                    before.sigResolved + "/" + before.sigTotal + ")"
            }
            return Result(true, before.targetFound, false, before.hooksActive, before, message)
        }

        val installed = PreloaderNative.installAllHooks()
        val after = PreloaderNative.status()
        val message = if (installed > 0) {
            installed.toString() + " hook aktif"
        } else {
            "Tidak ada hook aktif; periksa alasan hook dan ketersediaan backend trampoline"
        }
        return Result(true, after.targetFound, true, after.hooksActive, after, message)
    }

    /** Call from the owning runtime's normal shutdown/destroy path. */
    @JvmStatic
    fun onRuntimeStopping() {
        RuntimeCoLoadEntry.leave()
    }
}
