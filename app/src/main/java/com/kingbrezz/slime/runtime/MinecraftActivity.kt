package com.kingbrezz.slime.runtime

import android.os.Bundle
import android.content.res.AssetManager
import com.mojang.minecraftpe.MainActivity
import java.io.File

/**
 * Same-process host for the installed Bedrock runtime.
 *
 * Minecraft's native dependencies must be loaded by the runtime bootstrap before
 * this Activity is enabled/launched. The manifest keeps this component disabled
 * until that bootstrap is implemented and verified.
 */
class MinecraftActivity : MainActivity() {
    private var runtimeSession: MinecraftActivitySession.Prepared? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fail before entering Mojang's GameActivity lifecycle if no runtime was
        // fully prepared by the launcher/native bootstrap.
        runtimeSession = MinecraftActivitySession.requirePrepared()

        try {
            super.onCreate(savedInstanceState)
        } catch (error: Throwable) {
            runtimeSession?.let(MinecraftActivitySession::release)
            runtimeSession = null
            throw error
        }
    }

    override fun getAssets(): AssetManager {
        return runtimeSession?.assets ?: super.getAssets()
    }

    override fun getFilesDir(): File {
        return runtimeSession?.filesDir ?: super.getFilesDir()
    }

    override fun getDataDir(): File {
        return runtimeSession?.dataDir ?: super.getDataDir()
    }

    override fun getExternalFilesDir(type: String?): File? {
        val base = runtimeSession?.externalFilesDir ?: return super.getExternalFilesDir(type)
        if (type.isNullOrBlank()) return base

        return File(base, type).also { directory ->
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IllegalStateException("Cannot create Minecraft external directory: ${directory.absolutePath}")
            }
        }
    }

    override fun getCacheDir(): File {
        return runtimeSession?.cacheDir ?: super.getCacheDir()
    }

    override fun getDatabasePath(name: String): File {
        val directory = File(getDataDir(), "databases")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IllegalStateException("Cannot create Minecraft database directory: ${directory.absolutePath}")
        }
        return File(directory, name)
    }

    override fun onDestroy() {
        val session = runtimeSession
        try {
            super.onDestroy()
        } finally {
            runtimeSession = null
            if (isFinishing && session != null) {
                MinecraftActivitySession.release(session)
            }
        }
    }
}
