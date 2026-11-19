package com.kingbrezz.slime.native

import android.content.Context

/**
 * In-process bootstrap API for a runtime that owns the game process.
 * A custom/authorized game runtime calls enter() from its own process after
 * packaging libslime_preloader.so in that runtime APK.
 */
object RuntimeCoLoadEntry {
    data class StartResult(
        val runtime: NativeRuntimeLoader.Result,
        val initialized: Boolean,
        val status: PreloaderNative.Status,
    )

    @JvmStatic
    fun enter(
        context: Context,
        versionId: String,
        moduleFlags: Map<String, Boolean> = emptyMap(),
        moduleValues: Map<String, Double> = emptyMap(),
        signatureOverrides: Map<String, String> = emptyMap(),
        offsetOverrides: Map<String, Int> = emptyMap(),
    ): StartResult {
        val runtime = NativeRuntimeLoader.load(context)
        if (!runtime.preloaderLoaded) {
            return StartResult(runtime, false, PreloaderNative.emptyStatus(runtime.error ?: "native library unavailable"))
        }

        val initialized = PreloaderNative.init(versionId)
        moduleFlags.forEach { (id, enabled) -> PreloaderNative.setModule(id, enabled) }
        moduleValues.forEach { (compoundKey, value) ->
            val split = compoundKey.indexOf('.')
            if (split > 0) {
                PreloaderNative.setModuleValue(compoundKey.substring(0, split), compoundKey.substring(split + 1), value)
            }
        }
        signatureOverrides.forEach { (name, pattern) -> PreloaderNative.setSignature(name, pattern) }
        offsetOverrides.forEach { (name, value) -> PreloaderNative.setOffset(name, value) }

        return StartResult(runtime, initialized, PreloaderNative.status())
    }

    @JvmStatic
    fun leave() {
        PreloaderNative.shutdown()
    }
}