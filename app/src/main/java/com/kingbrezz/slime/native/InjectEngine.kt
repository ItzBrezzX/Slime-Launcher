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
        moduleValues: Map<String, Double> = emptyMap(),
        signatureOverrides: Map<String, String> = emptyMap(),
        offsetOverrides: Map<String, Int> = emptyMap(),
    ): Result {
        val lines = mutableListOf<String>()
        val log: (String) -> Unit = { s ->
            lines.add(s)
            android.util.Log.i(TAG, s)
        }

        // --- PrepareNative ---
        log("phase=PrepareNative")
        val runtime = NativeRuntimeLoader.load(context)
        log("runtime ABI=${runtime.abi} supported=${runtime.supportedAbis.joinToString()}")
        log("runtime dir=${runtime.libraryDirectory} libraries=${runtime.libraries.size}")
        runtime.libraries.forEach { lib ->
            log("native ${lib.name} size=${lib.sizeBytes} ELF=${lib.elfClass ?: "?"}/${lib.machine ?: "?"} compatible=${lib.compatible}")
        }
        if (!runtime.preloaderLoaded) {
            val err = runtime.error ?: PreloaderNative.loadError ?: "libslime_preloader.so missing"
            log("FAIL native runtime: $err")
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

        // --- Dedicated runtime host process ---
        log("phase=RuntimeHost process=:runtime")
        val host = RuntimeHostClient.start(
            context = context,
            versionId = versionId,
            moduleFlags = moduleFlags,
            moduleValues = moduleValues,
            signatureOverrides = signatureOverrides,
            offsetOverrides = offsetOverrides,
        )
        log("host ABI=${host.abi} libraryLoaded=${host.libraryLoaded} version=${host.libraryVersion}")
        log("host libraries=${host.libraries.size} dir=${host.libraryDirectory}")
        host.libraries.forEach { log("host lib=$it") }
        if (!host.libraryLoaded) {
            val err = host.error ?: host.message.ifBlank { "runtime host could not load native library" }
            log("FAIL runtime host: $err")
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
                summary = "Runtime host gagal: $err",
            )
        }
        st = PreloaderNative.Status(
            phase = host.phase,
            versionId = host.versionId,
            message = host.message,
            sigResolved = host.sigResolved,
            sigTotal = host.sigTotal,
            hooksActive = host.hooksActive,
            hooksPending = host.hooksPending,
            canInject = host.canInject,
            targetFound = host.targetFound,
            targetBase = host.targetBase,
        )
        log("host preloader phase=${st.phase} msg=${st.message}")
        log("host sig ${st.sigResolved}/${st.sigTotal} hooks active=${st.hooksActive} pending=${st.hooksPending}")

        // --- ProbeTarget ---
        log("phase=ProbeTarget pkg=${Catalog.MC_PACKAGE}")
        val installed = isMinecraftInstalled()
        log(if (installed) "Minecraft terpasang" else "Minecraft TIDAK terpasang")

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

        // Hook calls are sent to the runtime host, never to a different process.
        if (st.canInject && attach.first) {
            val hookResult = RuntimeHostClient.installHooks(context)
            log("runtime host hooks installed=${hookResult.installedHooks} error=${hookResult.error ?: "-"}")
            st = PreloaderNative.Status(
                phase = hookResult.phase,
                versionId = host.versionId,
                message = hookResult.message,
                sigResolved = hookResult.sigResolved,
                sigTotal = hookResult.sigTotal,
                hooksActive = hookResult.hooksActive,
                hooksPending = hookResult.hooksPending,
                canInject = hookResult.canInject,
                targetFound = hookResult.targetFound,
                targetBase = hookResult.targetBase,
            )
        } else {
            log("hooks gated: target game module not mapped in runtime host process")
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
            ok = initOk && host.ok,
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
