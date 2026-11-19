package com.kingbrezz.slime.native

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.kingbrezz.slime.data.Catalog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Discovers the already-installed Minecraft package as a local runtime source.
 *
 * No APK is uploaded or copied by this class. Android's PackageManager supplies the
 * installed base APK and public split-APK paths; later runtime preparation can consume
 * these paths in the same way as LeviLauncher's GamePackageManager.
 */
object MinecraftInstalledSource {

    data class Info(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val lastUpdateTime: Long,
        val installerPackageName: String?,
        val baseApkPath: String,
        val splitApkPaths: List<String>,
        val nativeLibraryDir: String,
        val deviceAbi: String,
        val libraryLocation: String,
    ) {
        val apkPaths: List<String>
            get() = listOf(baseApkPath) + splitApkPaths

        fun logSummary(): String = buildString {
            append("Minecraft source ready")
            append(" version=").append(versionName.ifBlank { "unknown" })
            append(" versionCode=").append(versionCode)
            append(" abi=").append(deviceAbi)
            append(" apks=").append(apkPaths.size)
            append(" installer=").append(installerPackageName ?: "unknown")
            append(" lib=").append(libraryLocation)
        }
    }

    sealed interface Inspection {
        data class Ready(val info: Info) : Inspection
        data class Unavailable(val reason: String) : Inspection
    }

    data class RuntimeStatus(
        val ready: Boolean,
        val message: String,
        val versionName: String = "",
        val abi: String = "",
        val directory: String = "",
        val libraries: List<String> = emptyList(),
        val reusedCache: Boolean = false,
    )

    fun inspect(context: Context): Inspection {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val packageInfo = try {
            getPackageInfo(packageManager)
        } catch (_: PackageManager.NameNotFoundException) {
            return Inspection.Unavailable("Minecraft package ${Catalog.MC_PACKAGE} tidak ditemukan")
        } catch (error: Throwable) {
            return Inspection.Unavailable("Tidak bisa membaca metadata Minecraft: ${error.message ?: error.javaClass.simpleName}")
        }

        val appInfo = packageInfo.applicationInfo
            ?: return Inspection.Unavailable("ApplicationInfo Minecraft tidak tersedia")

        val rawPaths = buildList {
            appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            appInfo.splitPublicSourceDirs?.forEach { path ->
                if (path.isNotBlank()) add(path)
            }
            if (size <= 1) {
                appInfo.splitSourceDirs?.forEach { path ->
                    if (path.isNotBlank()) add(path)
                }
            }
        }.distinct()

        if (rawPaths.isEmpty()) {
            return Inspection.Unavailable("Android tidak memberikan lokasi base APK Minecraft")
        }

        val apkFiles = rawPaths
            .map(::File)
            .filter { it.isFile && it.canRead() }

        val baseApk = apkFiles.firstOrNull { it.absolutePath == appInfo.sourceDir }
            ?: return Inspection.Unavailable(
                "Base APK Minecraft tidak dapat dibaca dari lokasi PackageManager"
            )

        val supportedAbis = (if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).orEmpty().toList()
        if (supportedAbis.isEmpty()) {
            return Inspection.Unavailable("Android tidak melaporkan ABI perangkat")
        }

        var selectedAbi: String? = null
        var libraryLocation: String? = null
        for (abi in supportedAbis) {
            val matchingApk = apkFiles.firstOrNull { apkContainsLibrary(it, abi) }
            if (matchingApk != null) {
                selectedAbi = abi
                libraryLocation = "apk:${matchingApk.name}!/lib/$abi/libminecraftpe.so"
                break
            }

            val extracted = appInfo.nativeLibraryDir
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it, "libminecraftpe.so") }
            if (extracted?.isFile == true && extracted.canRead()) {
                selectedAbi = abi
                libraryLocation = extracted.absolutePath
                break
            }
        }

        if (selectedAbi == null || libraryLocation == null) {
            return Inspection.Unavailable(
                "libminecraftpe.so tidak ditemukan untuk ABI perangkat (${supportedAbis.joinToString()})"
            )
        }

        return Inspection.Ready(
            Info(
                packageName = packageInfo.packageName ?: Catalog.MC_PACKAGE,
                versionName = packageInfo.versionName.orEmpty(),
                versionCode = packageInfoVersionCode(packageInfo),
                lastUpdateTime = packageInfo.lastUpdateTime,
                installerPackageName = installerPackageName(packageManager),
                baseApkPath = baseApk.absolutePath,
                splitApkPaths = apkFiles
                    .filterNot { it.absolutePath == baseApk.absolutePath }
                    .map { it.absolutePath },
                nativeLibraryDir = appInfo.nativeLibraryDir.orEmpty(),
                deviceAbi = selectedAbi,
                libraryLocation = libraryLocation,
            )
        )
    }

    private fun getPackageInfo(packageManager: PackageManager): PackageInfo {
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                Catalog.MC_PACKAGE,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(Catalog.MC_PACKAGE, 0)
        }
    }

    private fun packageInfoVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun installerPackageName(packageManager: PackageManager): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                packageManager.getInstallSourceInfo(Catalog.MC_PACKAGE).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(Catalog.MC_PACKAGE)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun apkContainsLibrary(apk: File, abi: String): Boolean {
        return try {
            ZipFile(apk).use { zip ->
                zip.getEntry("lib/$abi/libminecraftpe.so") != null
            }
        } catch (_: Throwable) {
            false
        }
    }
}
