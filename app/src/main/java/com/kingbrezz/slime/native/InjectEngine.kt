package com.kingbrezz.slime.native

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.kingbrezz.slime.data.Catalog

/**
 * Launcher-side preparation and launch flow.
 *
 * The official Minecraft package is an external Android application. This
 * class never treats the Slime-owned :runtime service as the game process.
 * A custom launcher runtime must call MinecraftRuntimeBridge from the same
 * process that has loaded libminecraftpe.so.
 */
class InjectEngine(private val context: Context) {

    enum class Phase {
        Idle,
        PrepareNative,
        InitPreloader,
        SyncModules,
        ProbeTarget,
        LaunchGame,
        AttachAttempt,
        Done,
        Failed,
    }

    data class Result(
        val phase: Phase,
        val ok: Boolean,
        val canInject: Boolean,
        val minecraftInstalled: Boolean,
        val minecraftLaunched: Boolean,
        val libraryLoaded: Boolean,
        val sigResolved: Int,
        val sigTotal: Int,
        val hooksActive: Int,
        val hooksPending: Int,
        val lines: List<String>,
        val summary: String,
    )

    fun run(
        versionId: String,
        moduleFlags: Map<String, Boolean>,
        autoLaunchGame: Boolean,
        moduleValues: Map<String, Double> = emptyMap(),
        signatureOverrides: Map<String, String> = emptyMap(),
        offsetOverrides: Map<String, Int> = emptyMap(),
    ): Result {
        val lines = mutableListOf<String>()
        val log: (String) -> Unit = { line ->
            lines.add(line)
            android.util.Log.i(TAG, line)
        }

        log("phase=PrepareNative")
        val runtime = NativeRuntimeLoader.load(context)
        log("launcher ABI=" + runtime.abi + " supported=" + runtime.supportedAbis.joinToString())
        log("launcher native dir=" + runtime.libraryDirectory + " libraries=" + runtime.libraries.size)
        runtime.libraries.forEach { lib ->
            log("native " + lib.name + " size=" + lib.sizeBytes +
                " ELF=" + (lib.elfClass ?: "?") + "/" + (lib.machine ?: "?") +
                " compatible=" + lib.compatible)
        }
        if (!runtime.preloaderLoaded) {
            val err = runtime.error ?: PreloaderNative.loadError ?: "libslime_preloader.so missing"
            log("FAIL native runtime: " + err)
            return Result(
                phase = Phase.Failed,
                ok = false,
                canInject = false,
                minecraftInstalled = isMinecraftInstalled(),
                minecraftLaunched = false,
                libraryLoaded = false,
                sigResolved = 0,
                sigTotal = 0,
                hooksActive = 0,
                hooksPending = 0,
                lines = lines,
                summary = "Native .so tidak termuat: " + err,
            )
        }
        log("native OK: " + PreloaderNative.libraryVersion())

        log("phase=InitPreloader version=" + versionId + " process=launcher")
        val initOk = PreloaderNative.init(versionId)
        if (!initOk) {
            val error = PreloaderNative.status().message
            log("FAIL preloader init: " + error)
            return Result(
                phase = Phase.Failed,
                ok = false,
                canInject = false,
                minecraftInstalled = isMinecraftInstalled(),
                minecraftLaunched = false,
                libraryLoaded = true,
                sigResolved = 0,
                sigTotal = 0,
                hooksActive = 0,
                hooksPending = 0,
                lines = lines,
                summary = "Preloader gagal diinisialisasi: " + error,
            )
        }

        log("phase=SyncModules count=" + moduleFlags.size)
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
        signatureOverrides.forEach { (name, pattern) -> PreloaderNative.setSignature(name, pattern) }
        offsetOverrides.forEach { (name, value) -> PreloaderNative.setOffset(name, value) }
        log("modules enabled=" + moduleFlags.count { it.value } +
            " native=" + PreloaderNative.moduleFlags().count { it.value })

        var status = PreloaderNative.status()
        log("launcher preloader phase=" + status.phase + " message=" + status.message)
        log("launcher targetFound=" + status.targetFound +
            " signatures=" + status.sigResolved + "/" + status.sigTotal)

        log("phase=ProbeTarget package=" + Catalog.MC_PACKAGE)
        val installed = isMinecraftInstalled()
        log(if (installed) "Minecraft terpasang" else "Minecraft TIDAK terpasang")

        var launched = false
        if (autoLaunchGame && installed) {
            log("phase=LaunchGame")
            launched = launchMinecraft()
            log(if (launched) "intent MAIN terkirim ke aplikasi Minecraft resmi" else "intent gagal")
        } else {
            log("phase=LaunchGame skipped auto=" + autoLaunchGame + " installed=" + installed)
        }

        log("phase=AttachAttempt process=launcher")
        if (status.targetFound) {
            log("libminecraftpe.so terdeteksi pada proses Slime @ " + status.targetBase)
        } else {
            log("libminecraftpe.so tidak ada pada proses Slime")
            log("Minecraft resmi tetap proses terpisah; bridge harus dipanggil dari runtime kustom")
        }

        if (status.targetFound && status.canInject) {
            val attempted = PreloaderNative.installAllHooks()
            status = PreloaderNative.status()
            log("same-process hook attempt=" + attempted +
                " active=" + status.hooksActive + " pending=" + status.hooksPending)
        } else {
            log("hook install dilewati: game library belum co-load atau signature belum siap")
        }

        val hooksOperational = status.targetFound && status.hooksActive > 0
        val summary = when {
            !installed -> "Minecraft tidak ada — sesi demo saja"
            !status.targetFound ->
                if (launched) "Minecraft resmi dibuka; runtime co-load belum terhubung" else
                    "Preloader Slime siap; runtime Minecraft belum terhubung"
            !status.canInject ->
                "Runtime game terdeteksi, tetapi signature belum siap (" +
                    status.sigResolved + "/" + status.sigTotal + ")"
            !hooksOperational ->
                "Runtime game dan signature terdeteksi; backend hook belum mengaktifkan hook"
            else -> "Runtime game satu proses; hook aktif " + status.hooksActive
        }
        log("DONE summary=" + summary)

        return Result(
            phase = Phase.Done,
            ok = initOk,
            canInject = hooksOperational,
            minecraftInstalled = installed,
            minecraftLaunched = launched,
            libraryLoaded = true,
            sigResolved = status.sigResolved,
            sigTotal = status.sigTotal,
            hooksActive = status.hooksActive,
            hooksPending = status.hooksPending,
            lines = lines,
            summary = summary,
        )
    }

    fun isMinecraftInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    Catalog.MC_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(Catalog.MC_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchMinecraft(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(Catalog.MC_PACKAGE) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "SlimeInject"
    }
}
