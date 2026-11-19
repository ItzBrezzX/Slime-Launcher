package com.kingbrezz.slime.native

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.kingbrezz.slime.data.Catalog
import java.io.File

/**
 * Live-inject pipeline for Slime.
 *
 * Android reality (Levi-style co-load vs Windows MinHook):
 * - Stock Minecraft (com.mojang.minecraftpe) runs in its own process.
 * - Another app cannot System.load() a .so into that process without
 *   root/ptrace, a modified game APK, or a custom runtime the game loads.
 * - This engine runs the full pipeline, reports phase results, launches MC,
 *   and keeps native preloader + module flags ready for when signatures
 *   are filled and a co-load host is available.
 *
 * Status labels are explicit — no fake "injected OK" without resolved sigs.
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
    ): Result {
        val lines = mutableListOf<String>()
        val log: (String) -> Unit = { s ->
            lines.add(s)
            android.util.Log.i(TAG, s)
        }

        // --- PrepareNative ---
        log("phase=PrepareNative")
        if (!PreloaderNative.libraryLoaded) {
            val err = PreloaderNative.loadError ?: "libslime_preloader.so missing"
            log("FAIL native library: $err")
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
                summary = "Native .so tidak termuat: $err",
            )
        }
        log("native OK: ${PreloaderNative.libraryVersion()}")
        stageNativeLibs(log)

        // --- InitPreloader ---
        log("phase=InitPreloader version=$versionId")
        val initOk = PreloaderNative.init(versionId)
        var st = PreloaderNative.status()
        log("preloader phase=${st.phase} msg=${st.message}")
        log("sig ${st.sigResolved}/${st.sigTotal} hooks active=${st.hooksActive} pending=${st.hooksPending}")

        // --- SyncModules ---
        log("phase=SyncModules count=${moduleFlags.size}")
        moduleFlags.forEach { (id, on) -> PreloaderNative.setModule(id, on) }
        log("modules enabled=${moduleFlags.count { it.value }} native=${PreloaderNative.moduleFlags().count { it.value }}")

        // --- ProbeTarget ---
        log("phase=ProbeTarget pkg=${Catalog.MC_PACKAGE}")
        val installed = isMinecraftInstalled()
        log(if (installed) "Minecraft terpasang" else "Minecraft TIDAK terpasang")

        // Inspect the installed package locally; no APK upload or file-picker export is needed.
        if (installed) {
            when (val source = MinecraftInstalledSource.inspect(context)) {
                is MinecraftInstalledSource.Inspection.Ready -> {
                    log(source.info.logSummary())
                }
                is MinecraftInstalledSource.Inspection.Unavailable -> {
                    log("Minecraft source unavailable: ${source.reason}")
                }
            }
        }

        // --- LaunchGame ---
        var launched = false
        if (autoLaunchGame && installed) {
            log("phase=LaunchGame")
            launched = launchMinecraft()
            log(if (launched) "intent MAIN terkirim" else "intent gagal")
        } else {
            log("phase=LaunchGame skipped auto=$autoLaunchGame installed=$installed")
        }

        // --- AttachAttempt ---
        log("phase=AttachAttempt")
        st = PreloaderNative.status()
        val attach = attemptAttach(st, installed, log)
        log("attach=${attach.first} detail=${attach.second}")

        // Try install hooks only if canInject
        if (st.canInject) {
            listOf(
                "ClientInstanceUpdate",
                "ActorBaseTick",
                "OptionsGamma",
                "CameraFov",
            ).forEach { name ->
                val ok = PreloaderNative.tryInstallHook(name)
                log("hook $name install=$ok")
            }
            st = PreloaderNative.status()
        } else {
            log("hooks gated: signatures unresolved atau bukan in-process co-load")
        }

        val can = st.canInject && attach.first
        val summary = when {
            !installed -> "Minecraft tidak ada — sesi demo saja"
            !st.canInject -> "Preloader Degraded (sig ${st.sigResolved}/${st.sigTotal}) — isi pattern RE dulu"
            !attach.first -> "Game diluncurkan; attach in-process belum tersedia di stock MC"
            else -> "Pipeline Ready — hooks dapat diinstall"
        }
        log("DONE summary=$summary")

        return Result(
            phase = Phase.Done,
            ok = initOk,
            canInject = can,
            minecraftInstalled = installed,
            minecraftLaunched = launched,
            libraryLoaded = true,
            sigResolved = st.sigResolved,
            sigTotal = st.sigTotal,
            hooksActive = st.hooksActive,
            hooksPending = st.hooksPending,
            lines = lines,
            summary = summary,
        )
    }

    private fun stageNativeLibs(log: (String) -> Unit) {
        try {
            val dir = File(context.filesDir, "slime_native")
            if (!dir.exists()) dir.mkdirs()
            // Extract packaged jniLibs from APK is handled by system; stage path for future co-load hosts
            val staged = File(dir, "arm64-v8a")
            if (!staged.exists()) staged.mkdirs()
            log("stage dir=${dir.absolutePath}")
        } catch (t: Throwable) {
            log("stage warn: ${t.message}")
        }
    }

    private fun attemptAttach(
        st: PreloaderNative.Status,
        mcInstalled: Boolean,
        log: (String) -> Unit,
    ): Pair<Boolean, String> {
        // Same-process load only. Cross-process inject into stock MC is not performed.
        if (st.targetFound) {
            log("libminecraftpe.so termuat di proses ini @ ${st.targetBase}")
        }
        if (!mcInstalled) {
            return false to "no target package"
        }
        if (!st.canInject) {
            return false to "preloader not Ready (need resolved signatures)"
        }
        if (st.targetFound) {
            return true to "target in-process @ ${st.targetBase}"
        }
        // Stock package will not load our .so. Explicit refusal to claim success.
        log("stock package ${Catalog.MC_PACKAGE} is separate process — no silent ptrace")
        return false to "co-load host required (custom runtime / mod loader entry)"
    }

    fun isMinecraftInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(Catalog.MC_PACKAGE, PackageManager.PackageInfoFlags.of(0))
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
