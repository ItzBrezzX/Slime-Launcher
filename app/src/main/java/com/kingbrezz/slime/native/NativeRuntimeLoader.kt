package com.kingbrezz.slime.native

import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile

/** Inspects Android-packaged native libraries and loads Slime JNI entry point. */
object NativeRuntimeLoader {
    data class Library(val name: String, val path: String, val sizeBytes: Long, val elfClass: Int?, val machine: Int?, val compatible: Boolean)
    data class Result(val abi: String, val supportedAbis: List<String>, val libraryDirectory: String, val libraries: List<Library>, val preloaderLoaded: Boolean, val error: String? = null)

    private const val PRELOADER = "libslime_preloader.so"
    @Volatile private var last: Result? = null

    fun inspect(context: Context): Result {
        val supported = Build.SUPPORTED_ABIS.toList()
        val abi = context.applicationInfo.primaryCpuAbi?.takeIf { it.isNotBlank() }\n            ?: supported.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }\n            ?: Build.CPU_ABI.orEmpty()
        val dir = context.applicationInfo.nativeLibraryDir?.let(::File) ?: File(context.applicationInfo.dataDir, "lib")
        val libraries = dir.listFiles().orEmpty().filter { it.isFile && it.extension == "so" }.sortedBy { it.name }.map { file ->
            val header = readElfHeader(file)
            val expected = expectedMachine(abi)
            Library(file.name, file.absolutePath, file.length(), header.first, header.second,
                header.first != null && header.second != null && (expected == null || header.second == expected) &&
                    (if (abi == "arm64-v8a" || abi == "x86_64") header.first == 2 else header.first == 1))
        }
        val preloader = libraries.firstOrNull { it.name == PRELOADER }
        val result = Result(abi, supported, dir.absolutePath, libraries, PreloaderNative.libraryLoaded, when {
            preloader == null -> "$PRELOADER not found in ${dir.absolutePath}"
            !preloader.compatible -> "$PRELOADER ELF ABI does not match $abi"
            else -> null
        })
        last = result
        return result
    }

    fun load(context: Context): Result {
        val checked = inspect(context)
        if (checked.error != null) return checked
        val path = File(checked.libraryDirectory, PRELOADER).absolutePath
        val loaded = PreloaderNative.ensureLoaded(path)
        return checked.copy(preloaderLoaded = loaded, error = if (loaded) null else PreloaderNative.loadError ?: "Failed to load $PRELOADER").also { last = it }
    }

    fun lastResult(): Result? = last

    private fun readElfHeader(file: File): Pair<Int?, Int?> = try {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 20) return null to null
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) return null to null
            val elfClass = input.readUnsignedByte()
            input.seek(18)
            val machine = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            elfClass to machine
        }
    } catch (_: Throwable) { null to null }

    private fun expectedMachine(abi: String): Int? = when (abi) {
        "arm64-v8a" -> 183
        "armeabi-v7a" -> 40
        "x86_64" -> 62
        "x86" -> 3
        else -> null
    }
}