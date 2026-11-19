package com.kingbrezz.slime.runtime

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.native.MinecraftInstalledSource
import java.io.File
import java.io.IOException
import java.lang.reflect.Method

/**
 * Prepared Java-side environment consumed by MinecraftActivity.
 *
 * This class deliberately does not load native libraries. The next runtime stage
 * must load and verify the version-specific native dependency graph before
 * launching MinecraftActivity.
 */
internal object MinecraftActivitySession {
    internal data class Prepared(
        val assets: AssetManager,
        val versionName: String,
        val versionCode: Long,
        val abi: String,
        val filesDir: File,
        val dataDir: File,
        val externalFilesDir: File,
        val cacheDir: File,
    )

    @Volatile
    private var active: Prepared? = null

    @Synchronized
    fun prepare(context: Context, info: MinecraftInstalledSource.Info): Prepared {
        check(active == null) { "A Minecraft runtime session is already active" }
        require(info.packageName == Catalog.MC_PACKAGE) {
            "Unsupported game package: ${info.packageName}"
        }

        val appContext = context.applicationContext
        val versionSegment = buildString {
            append(info.versionName.ifBlank { info.versionCode.toString() })
            append('-').append(info.versionCode)
        }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

        val sessionKey = "${info.deviceAbi}/$versionSegment"
        val storageRoot = File(appContext.filesDir, "minecraft_host/$sessionKey")
        val files = ensureDirectory(File(storageRoot, "files"))
        val data = ensureDirectory(File(storageRoot, "data"))
        val cache = ensureDirectory(File(storageRoot, "cache"))

        val externalBase = appContext.getExternalFilesDir(null)
        val external = ensureDirectory(
            externalBase?.let { File(it, "minecraft_host/$sessionKey") }
                ?: File(storageRoot, "external")
        )

        val gameApks = info.apkPaths.distinct()
        if (gameApks.isEmpty() || gameApks.first() != info.baseApkPath) {
            throw IOException("Minecraft base/split APK list is invalid")
        }
        gameApks.forEach { path ->
            val apk = File(path)
            if (!apk.isFile || !apk.canRead()) {
                throw IOException("Minecraft APK is not readable: $path")
            }
        }

        val hostApks = buildList {
            appContext.applicationInfo.sourceDir?.takeIf(String::isNotBlank)?.let(::add)
            appContext.applicationInfo.splitSourceDirs?.forEach { path ->
                if (path.isNotBlank()) add(path)
            }
        }
        val assetPaths = (gameApks + hostApks).distinct()

        val assets = createAssetManager(assetPaths)
        val prepared = Prepared(
            assets = assets,
            versionName = info.versionName,
            versionCode = info.versionCode,
            abi = info.deviceAbi,
            filesDir = files,
            dataDir = data,
            externalFilesDir = external,
            cacheDir = cache,
        )
        active = prepared
        return prepared
    }

    fun requirePrepared(): Prepared = checkNotNull(active) {
        "Minecraft runtime session was not prepared before MinecraftActivity launch"
    }

    @Synchronized
    fun release(session: Prepared) {
        if (active !== session) return
        active = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                session.assets.close()
            } catch (_: Throwable) {
                // The Activity is already finishing; asset cleanup is best-effort.
            }
        }
    }

    private fun ensureDirectory(directory: File): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create Minecraft storage directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory || !directory.canWrite()) {
            throw IOException("Minecraft storage directory is not writable: ${directory.absolutePath}")
        }
        return directory
    }

    private fun createAssetManager(paths: List<String>): AssetManager {
        val assets = try {
            AssetManager::class.java.getDeclaredConstructor().newInstance()
        } catch (error: Throwable) {
            throw IOException("Cannot create Minecraft AssetManager", error)
        }

        try {
            val addAssetPath: Method = AssetManager::class.java.getMethod(
                "addAssetPath",
                String::class.java,
            )
            paths.forEach { path ->
                val cookie = (addAssetPath.invoke(assets, path) as? Number)?.toInt() ?: 0
                if (cookie <= 0) {
                    throw IOException("AssetManager rejected APK path: $path")
                }
            }
            return assets
        } catch (error: Throwable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    assets.close()
                } catch (_: Throwable) {
                    // Preserve the original failure.
                }
            }
            if (error is IOException) throw error
            throw IOException("Cannot attach installed Minecraft APK assets", error)
        }
    }
}
