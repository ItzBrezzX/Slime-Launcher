package com.kingbrezz.slime.runtime

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kingbrezz.slime.data.Catalog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

data class MinecraftRuntimeManifest(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val abi: String,
    val apkFiles: List<String>,
    val apkSha256: Map<String, String>,
    val nativeLibraries: List<String>,
    val nativeDirectory: String,
    val importedAtMillis: Long,
)

data class ImportedMinecraftRuntime(
    val manifest: MinecraftRuntimeManifest,
    val rootDirectory: File,
) {
    val nativeLibraryDirectory: File
        get() = File(rootDirectory, manifest.nativeDirectory)

    val minecraftLibrary: File
        get() = File(nativeLibraryDirectory, "libminecraftpe.so")

    val isPrepared: Boolean
        get() = manifest.packageName == Catalog.MC_PACKAGE &&
            manifest.abi.isNotBlank() &&
            listOf(
                "libc++_shared.so",
                "libfmod.so",
                "libMediaDecoders_Android.so",
                "libminecraftpe.so",
            ).all { name ->
                File(nativeLibraryDirectory, name).let { it.isFile && it.length() > 0L }
            }
}

/**
 * Imports user-selected base/split APKs into Slime's private storage.
 *
 * This prepares and validates game files only. It does not install the APK,
 * alter its signature, bypass licensing, or load its code into another app.
 */
class MinecraftRuntimeInstaller(context: Context) {
    private val app = context.applicationContext
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val root = File(app.filesDir, ROOT_NAME)
    private val active = File(root, ACTIVE_NAME)

    fun current(): ImportedMinecraftRuntime? {
        val manifestFile = File(active, MANIFEST_NAME)
        if (!manifestFile.isFile) return null
        return try {
            val manifest = gson.fromJson(
                manifestFile.readText(),
                MinecraftRuntimeManifest::class.java,
            ) ?: return null
            ImportedMinecraftRuntime(manifest, active)
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun importApks(uris: List<Uri>): ImportedMinecraftRuntime {
        require(uris.isNotEmpty()) { "Pilih APK Minecraft terlebih dahulu" }
        require(uris.size <= MAX_APK_COUNT) { "Maksimal $MAX_APK_COUNT file APK per impor" }
        return importWithSource { apkDirectory ->
            var totalInputBytes = 0L
            uris.mapIndexed { index, uri ->
                val displayName = displayName(uri)
                if (!displayName.endsWith(".apk", ignoreCase = true)) {
                    throw IOException("File '$displayName' bukan APK. Pilih base APK dan split APK, bukan file XAPK/APKS.")
                }
                val destination = File(apkDirectory, "package-$index.apk")
                val copied = copyUri(uri, destination, MAX_TOTAL_APK_BYTES - totalInputBytes)
                totalInputBytes += copied
                destination
            }
        }
    }

    /**
     * Copies the installed Google Play Minecraft base and split APKs.
     * This is the preferred trusted source; no package installation is done.
     */
    @Synchronized
    fun importInstalledMinecraft(): ImportedMinecraftRuntime {
        return importWithSource { apkDirectory ->
            val installed = installedPackageInfo()
            verifyTrustedMinecraftSource(installed)
            val appInfo = installed.applicationInfo
                ?: throw IOException("ApplicationInfo Minecraft tidak tersedia")
            val paths = buildList {
                appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                appInfo.splitSourceDirs?.forEach { path ->
                    path?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }.distinct()
            if (paths.isEmpty()) throw IOException("File APK Minecraft terpasang tidak ditemukan")
            var totalBytes = 0L
            paths.mapIndexed { index, path ->
                val source = File(path)
                if (!source.isFile) throw IOException("APK Minecraft tidak ditemukan: $path")
                val destination = File(apkDirectory, "package-$index.apk")
                totalBytes += copyFile(source, destination, MAX_TOTAL_APK_BYTES - totalBytes)
                destination
            }
        }
    }

    private fun importWithSource(copySource: (File) -> List<File>): ImportedMinecraftRuntime {
        if (!root.exists() && !root.mkdirs()) {
            throw IOException("Tidak bisa membuat direktori runtime Slime")
        }
        val stage = File(root, ".stage-${UUID.randomUUID()}")
        if (!stage.mkdirs()) throw IOException("Tidak bisa membuat direktori staging")
        try {
            val apkDirectory = File(stage, "apks")
            val nativeDirectory = File(stage, "native/${targetAbi()}")
            if (!apkDirectory.mkdirs() || !nativeDirectory.mkdirs()) {
                throw IOException("Tidak bisa menyiapkan direktori APK/native")
            }

            val copiedApks = copySource(apkDirectory)
            if (copiedApks.isEmpty() || copiedApks.size > MAX_APK_COUNT) {
                throw IOException("Jumlah APK sumber tidak valid")
            }

            val packageInfos = copiedApks.map { apk ->
                readArchiveInfo(apk) ?: throw IOException("APK tidak valid atau tidak dapat dibaca: ${apk.name}")
            }
            val packageNames = packageInfos.map { it.packageName }.distinct()
            if (packageNames.size != 1 || packageNames.single() != Catalog.MC_PACKAGE) {
                throw IOException("Package harus ${Catalog.MC_PACKAGE}; ditemukan: ${packageNames.joinToString()}")
            }

            val baseIndexes = packageInfos.indices.filter { index ->
                packageInfos[index].splitNames.isNullOrEmpty()
            }
            if (baseIndexes.size != 1) {
                throw IOException("Pilih tepat satu base APK Minecraft dan seluruh split APK yang diperlukan")
            }

            val versionCodes = packageInfos.map(::versionCode).distinct()
            val versionNames = packageInfos.map { it.versionName.orEmpty() }.distinct()
            if (versionCodes.size != 1 || versionNames.size != 1) {
                throw IOException("Base APK dan split APK harus berasal dari versi Minecraft yang sama")
            }
            val basePackageInfo = packageInfos[baseIndexes.single()]
            verifyTrustedMinecraftSource(basePackageInfo)
            val baseCertificates = certificateDigests(basePackageInfo, includeHistory = false)
            packageInfos.forEachIndexed { index, packageInfo ->
                val splitCertificates = certificateDigests(packageInfo, includeHistory = false)
                if (splitCertificates.isEmpty() || splitCertificates != baseCertificates) {
                    throw IOException("Sertifikat split APK ke-${index + 1} tidak cocok dengan base APK")
                }
            }

            val abi = targetAbi()
            var extractedBytes = 0L
            val copiedNames = linkedMapOf<String, String>()
            copiedApks.forEach { apk ->
                extractedBytes = extractNativeLibraries(
                    apk = apk,
                    abi = abi,
                    outputDirectory = nativeDirectory,
                    previous = copiedNames,
                    bytesAlreadyExtracted = extractedBytes,
                )
            }

            val minecraftLibrary = File(nativeDirectory, "libminecraftpe.so")
            if (!minecraftLibrary.isFile || minecraftLibrary.length() == 0L) {
                throw IOException(
                    "libminecraftpe.so tidak ditemukan untuk ABI $abi. " +
                        "Pastikan base APK dan split ABI Minecraft ikut dipilih."
                )
            }
            copiedNames.keys.forEach { name ->
                verifyElf(File(nativeDirectory, name), abi)
            }
            val requiredLibraries = listOf(
                "libc++_shared.so",
                "libfmod.so",
                "libMediaDecoders_Android.so",
                "libminecraftpe.so",
            )
            val missingLibraries = requiredLibraries.filter { name ->
                !File(nativeDirectory, name).let { it.isFile && it.length() > 0L }
            }
            if (missingLibraries.isNotEmpty()) {
                throw IOException("Native dependency Minecraft belum lengkap: ${missingLibraries.joinToString()}")
            }

            val apkNames = copiedApks.map { it.name }
            val hashes = copiedApks.associate { it.name to sha256(it) }
            val manifest = MinecraftRuntimeManifest(
                packageName = Catalog.MC_PACKAGE,
                versionName = versionNames.single(),
                versionCode = versionCodes.single(),
                abi = abi,
                apkFiles = apkNames,
                apkSha256 = hashes,
                nativeLibraries = copiedNames.keys.sorted(),
                nativeDirectory = "native/$abi",
                importedAtMillis = System.currentTimeMillis(),
            )
            File(stage, MANIFEST_NAME).writeText(gson.toJson(manifest))

            val backup = File(root, ".previous")
            if (backup.exists() && !backup.deleteRecursively()) {
                throw IOException("Tidak bisa membersihkan backup runtime lama")
            }
            if (active.exists() && !active.renameTo(backup)) {
                throw IOException("Tidak bisa mengganti runtime aktif")
            }
            if (!stage.renameTo(active)) {
                if (backup.exists()) backup.renameTo(active)
                throw IOException("Tidak bisa mengaktifkan runtime yang baru diimpor")
            }
            backup.deleteRecursively()

            return current() ?: throw IOException("Manifest runtime gagal dibaca setelah impor")
        } catch (error: Throwable) {
            stage.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    fun removeCurrent(): Boolean {
        if (!active.exists()) return true
        return active.deleteRecursively()
    }

    private fun targetAbi(): String {
        val supported = if (Process.is64Bit()) {
            Build.SUPPORTED_64_BIT_ABIS.toList()
        } else {
            Build.SUPPORTED_32_BIT_ABIS.toList()
        }
        return supported.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86_64" || it == "x86" }
            ?: throw IOException("ABI proses Android tidak didukung: ${supported.joinToString()}")
    }

    private fun displayName(uri: Uri): String {
        var result = uri.lastPathSegment ?: "selected.apk"
        try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) result = cursor.getString(index) ?: result
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    private fun copyUri(uri: Uri, destination: File, remainingBudget: Long): Long {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("Tidak bisa membuka file terpilih")
        var copied = 0L
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > remainingBudget || copied > MAX_TOTAL_APK_BYTES) {
                        throw IOException("Ukuran APK melewati batas impor")
                    }
                    ensureFreeSpace()
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        if (copied == 0L) throw IOException("File APK kosong")
        return copied
    }

    private fun installedPackageInfo(): PackageInfo {
        val pm = app.packageManager
        return try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    Catalog.MC_PACKAGE,
                    PackageManager.PackageInfoFlags.of(signingFlags().toLong()),
                )
            } else {
                pm.getPackageInfo(Catalog.MC_PACKAGE, signingFlags())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            throw IOException(
                "Minecraft resmi dari Google Play harus terpasang terlebih dahulu untuk verifikasi lisensi."
            )
        }
    }

    private fun copyFile(source: File, destination: File, remainingBudget: Long): Long {
        var copied = 0L
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > remainingBudget || copied > MAX_TOTAL_APK_BYTES) {
                        throw IOException("Total APK melebihi batas impor")
                    }
                    ensureFreeSpace()
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        if (copied == 0L) throw IOException("File APK kosong")
        return copied
    }

    private fun readArchiveInfo(apk: File): PackageInfo? {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 33) {
            app.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_META_DATA or signingFlags()).toLong()
                ),
            )
        } else {
            app.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_META_DATA or signingFlags(),
            )
        }
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun verifyTrustedMinecraftSource(importedBase: PackageInfo) {
        val pm = app.packageManager
        val installed = try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    Catalog.MC_PACKAGE,
                    PackageManager.PackageInfoFlags.of(signingFlags().toLong()),
                )
            } else {
                pm.getPackageInfo(Catalog.MC_PACKAGE, signingFlags())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            throw IOException(
                "Minecraft resmi dari Google Play harus terpasang terlebih dahulu untuk verifikasi lisensi."
            )
        }

        val installer = try {
            if (Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(Catalog.MC_PACKAGE).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(Catalog.MC_PACKAGE)
            }
        } catch (_: Throwable) {
            null
        }
        if (installer != PLAY_STORE_PACKAGE) {
            throw IOException(
                "Sumber Minecraft terpasang tidak terverifikasi sebagai Google Play. " +
                    "Pasang versi resmi melalui Play Store terlebih dahulu."
            )
        }

        val importedCertificates = certificateDigests(importedBase, includeHistory = false)
        val trustedCertificates = certificateDigests(installed, includeHistory = true)
        if (importedCertificates.isEmpty() || trustedCertificates.isEmpty() ||
            importedCertificates.none { it in trustedCertificates }
        ) {
            throw IOException(
                "Sertifikat APK impor tidak cocok dengan Minecraft resmi yang terpasang. " +
                    "Gunakan APK resmi dari rilis yang sama penerbitnya."
            )
        }
    }

    private fun certificateDigests(info: PackageInfo, includeHistory: Boolean): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (includeHistory) signingInfo.signingCertificateHistory
            else signingInfo.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()

        return signatures.mapNotNull { signature ->
            try {
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            } catch (_: Throwable) {
                null
            }
        }.toSet()
    }

    private fun verifyElf(file: File, abi: String) {
        val expected = when (abi) {
            "arm64-v8a" -> 183
            "armeabi-v7a" -> 40
            "x86_64" -> 62
            "x86" -> 3
            else -> throw IOException("ABI tidak dikenal: $abi")
        }
        val header = ByteArray(20)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) throw IOException("ELF header terpotong: ${file.name}")
                offset += read
            }
        }
        val validMagic = header[0] == 0x7f.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        val expectedClass = if (abi == "arm64-v8a" || abi == "x86_64") 2 else 1
        val elfClass = header[4].toInt() and 0xff
        val elfData = header[5].toInt() and 0xff
        val machine = (header[18].toInt() and 0xff) or
            ((header[19].toInt() and 0xff) shl 8)
        if (!validMagic || elfClass != expectedClass || elfData != 1 || machine != expected) {
            throw IOException("ELF ABI tidak cocok pada ${file.name} (expected $abi)")
        }
    }

    private fun ensureFreeSpace() {
        val available = StatFs(app.filesDir.absolutePath).availableBytes
        if (available < MIN_FREE_SPACE_BYTES) {
            throw IOException("Penyimpanan hampir habis. Kosongkan setidaknya 64 MB lalu coba lagi.")
        }
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun extractNativeLibraries(
        apk: File,
        abi: String,
        outputDirectory: File,
        previous: MutableMap<String, String>,
        bytesAlreadyExtracted: Long,
    ): Long {
        var total = bytesAlreadyExtracted
        val prefix = "lib/$abi/"
        ZipFile(apk).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
                val name = entry.name.removePrefix(prefix)
                if (name.isBlank() || '/' in name || !name.endsWith(".so")) continue
                if (!name.matches(Regex("[A-Za-z0-9_.+\\-]+\\.so"))) continue

                val destination = File(outputDirectory, name)
                val temporary = File(outputDirectory, "$name.tmp")
                archive.getInputStream(entry).use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var fileBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            total += read
                            if (fileBytes > MAX_NATIVE_FILE_BYTES || total > MAX_TOTAL_NATIVE_BYTES) {
                                throw IOException("Ukuran native library melewati batas aman")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.fd.sync()
                    }
                }

                val hash = sha256(temporary)
                val oldHash = previous[name]
                if (oldHash != null) {
                    temporary.delete()
                    if (oldHash != hash) {
                        throw IOException("Konflik library $name antara base APK dan split APK")
                    }
                    continue
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    throw IOException("Gagal menyimpan native library $name")
                }
                previous[name] = hash
            }
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val ROOT_NAME = "minecraft-runtime"
        private const val ACTIVE_NAME = "active"
        private const val MANIFEST_NAME = "runtime-manifest.json"
        private const val MAX_APK_COUNT = 12
        private const val MAX_TOTAL_APK_BYTES = 2_500_000_000L
        private const val MAX_NATIVE_FILE_BYTES = 512_000_000L
        private const val MAX_TOTAL_NATIVE_BYTES = 1_500_000_000L
        private const val MIN_FREE_SPACE_BYTES = 64L * 1024L * 1024L
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
