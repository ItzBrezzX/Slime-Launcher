package com.kingbrezz.slime.native

import android.content.Context

/**
 * Reusable in-process bootstrap for a custom runtime that owns the game.
 * This must be called by the process that has mapped libminecraftpe.so.
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
            return StartResult(
                runtime,
                false,
                PreloaderNative.emptyStatus(runtime.error ?: "native library unavailable"),
            )
        }

        // Store overrides before init: native initialization rebuilds the
        // per-version tables and applies the stored signature/offset overrides.
        signatureOverrides.forEach { (name, pattern) ->
            PreloaderNative.setSignature(name, pattern)
        }
        offsetOverrides.forEach { (name, value) ->
            PreloaderNative.setOffset(name, value)
        }

        val initialized = PreloaderNative.init(versionId)
        if (initialized) {
            moduleFlags.forEach { (id, enabled) ->
                PreloaderNative.setModule(id, enabled)
            }
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
        }

        return StartResult(runtime, initialized, PreloaderNative.status())
    }

    @JvmStatic
    fun leave() {
        PreloaderNative.shutdown()
    }
}
