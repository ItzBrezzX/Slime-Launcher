package com.kingbrezz.slime.native

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.kingbrezz.slime.data.Catalog
import com.kingbrezz.slime.runtime.MinecraftRuntimeVersionPolicy
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


    /**
     * Extract required game-native libraries to Slime-private storage from the installed
     * package APK and split APKs. This prepares files only; it does not load Minecraft.
     */
    fun prepare(context: Context): RuntimeStatus {
        val info = when (val result = inspect(context)) {
            is Inspection.Ready -> result.info
            is Inspection.Unavailable -> return RuntimeStatus(false, result.reason)
        }
        return try {
            val versionDirectory = safeVersion(info.versionName, info.versionCode)
            val directory = File(context.applicationContext.filesDir,
                "minecraft_runtime/" + info.deviceAbi + "/" + versionDirectory)
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Tidak bisa membuat direktori runtime")
            }

            val required = requiredLibraries(info.versionName)
            val optional = emptyList<String>()
            val manifest = buildManifest(info, required, optional)
            val marker = File(directory, ".slime-runtime-manifest")
            val cached = try { marker.isFile && marker.readText() == manifest } catch (_: Throwable) { false }
            val validCache = cached && required.all { name ->
                val file = File(directory, name)
                file.isFile && file.length() > 0L && isElfForAbi(file, info.deviceAbi)
            }

            if (!validCache) {
                val missing = required.filterNot { name ->
                    val target = File(directory, name)
                    if (target.isFile && target.length() > 0L && isElfForAbi(target, info.deviceAbi)) {
                        true
                    } else {
                        stageLibrary(info, name, target)
                    }
                }
                if (missing.isNotEmpty()) {
                    throw IOException("Library wajib tidak ditemukan untuk " + info.deviceAbi + ": " + missing.joinToString())
                }

                optional.forEach { name ->
                    val target = File(directory, name)
                    if (!target.isFile || target.length() == 0L) stageLibrary(info, name, target)
                }

                val temporary = File(directory, ".slime-runtime-manifest.tmp")
                temporary.writeText(manifest)
                if (marker.exists() && !marker.delete()) {
                    temporary.delete()
                    throw IOException("Gagal memperbarui manifest runtime")
                }
                if (!temporary.renameTo(marker)) {
                    temporary.delete()
                    throw IOException("Gagal menerbitkan manifest runtime")
                }
            }

            val staged = (required + optional).filter { name ->
                val file = File(directory, name)
                file.isFile && file.length() > 0L
            }
            RuntimeStatus(
                ready = required.all { name ->
                    val file = File(directory, name)
                    file.isFile && file.length() > 0L && isElfForAbi(file, info.deviceAbi)
                },
                message = if (validCache) "Cache runtime Minecraft sudah cocok"
                    else "Pustaka Minecraft berhasil disiapkan secara lokal",
                versionName = info.versionName,
                abi = info.deviceAbi,
                directory = directory.absolutePath,
                libraries = staged,
                reusedCache = validCache,
            )
        } catch (error: Throwable) {
            RuntimeStatus(
                ready = false,
                message = "Persiapan runtime gagal: " + (error.message ?: error.javaClass.simpleName),
                versionName = info.versionName,
                abi = info.deviceAbi,
            )
        }
    }

    private fun requiredLibraries(versionName: String): List<String> =
        MinecraftRuntimeVersionPolicy.requiredGameLibraries(versionName)
            ?: throw IOException("Format versi Minecraft tidak dikenali: $versionName")

    private fun stageLibrary(info: Info, name: String, target: File): Boolean {
        val nativeFile = info.nativeLibraryDir.takeIf { it.isNotBlank() }?.let { File(it, name) }
        if (nativeFile != null && nativeFile.isFile && nativeFile.canRead()) {
            try {
                FileInputStream(nativeFile).use { input ->
                    publishLibrary(input, nativeFile.length(), target, info.deviceAbi)
                }
                return true
            } catch (_: Throwable) {
                // Retry from the installed APK/split archive.
            }
        }

        val entryName = "lib/" + info.deviceAbi + "/" + name
        val paths = listOf(info.baseApkPath) + info.splitApkPaths
        for (path in paths) {
            try {
                val copied = ZipFile(path).use { zip ->
                    val entry = zip.getEntry(entryName)
                    if (entry == null) {
                        false
                    } else {
                        zip.getInputStream(entry).use { input ->
                            publishLibrary(input, entry.size, target, info.deviceAbi)
                        }
                        true
                    }
                }
                if (copied) return true
            } catch (_: Throwable) {
                // A different split may contain this library.
            }
        }
        return false
    }

    private fun publishLibrary(
        input: java.io.InputStream,
        expectedSize: Long,
        target: File,
        abi: String,
    ) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.delete()
        try {
            val copied = FileOutputStream(temp).use { output ->
                val count = input.copyTo(output, 256 * 1024)
                output.fd.sync()
                count
            }
            if (expectedSize >= 0L && copied != expectedSize) {
                throw IOException("Ukuran " + target.name + " tidak cocok (" + copied + "/" + expectedSize + ")")
            }
            if (!isElfForAbi(temp, abi)) throw IOException(target.name + " bukan ELF " + abi)
            if (target.exists() && !target.delete()) throw IOException("Gagal mengganti " + target.name)
            if (!temp.renameTo(target)) throw IOException("Gagal menerbitkan " + target.name)
        } finally {
            temp.delete()
        }
    }

    private fun isElfForAbi(file: File, abi: String): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(20)
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count < 0) return false
                    offset += count
                }
                if (header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() ||
                    header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
                ) return false
                val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
                when (abi) {
                    "arm64-v8a" -> machine == 183
                    "armeabi-v7a" -> machine == 40
                    "x86_64" -> machine == 62
                    "x86" -> machine == 3
                    else -> false
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun safeVersion(version: String, code: Long): String =
        (if (version.isBlank()) code.toString() else version)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)

    private fun buildManifest(info: Info, required: List<String>, optional: List<String>): String = buildString {
        append("package=").append(info.packageName).append('\n')
        append("version=").append(info.versionName).append('\n')
        append("versionCode=").append(info.versionCode).append('\n')
        append("lastUpdate=").append(info.lastUpdateTime).append('\n')
        append("abi=").append(info.deviceAbi).append('\n')
        (listOf(info.baseApkPath) + info.splitApkPaths).forEach { path ->
            val file = File(path)
            append("apk=").append(file.name).append('|')
                .append(file.length()).append('|').append(file.lastModified()).append('\n')
        }
        required.forEach { append("required=").append(it).append('\n') }
        optional.forEach { append("optional=").append(it).append('\n') }
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
