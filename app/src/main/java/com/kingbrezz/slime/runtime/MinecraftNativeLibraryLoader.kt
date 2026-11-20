package com.kingbrezz.slime.runtime

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Maps the imported Minecraft ELF dependency graph into the current Slime
 * process. This is an in-process loader, not an injector for another package.
 *
 * The owning game Activity must call this before entering the game's Java
 * Activity lifecycle. Loading libminecraftpe.so alone does not start a game.
 */
object MinecraftNativeLibraryLoader {
    data class Result(
        val abi: String,
        val processName: String,
        val processId: Int,
        val loadedLibraries: List<String>,
        val gameLibraryLoaded: Boolean,
        val complete: Boolean,
        val requiresProcessRestart: Boolean,
        val error: String? = null,
    )

    private data class ElfInfo(
        val elfClass: Int,
        val machine: Int,
        val needed: List<String>,
    )

    private data class ProgramSegment(
        val type: Long,
        val offset: Long,
        val virtualAddress: Long,
        val fileSize: Long,
    )

    private data class LoadPlan(
        val directory: File,
        val files: Map<String, File>,
        val order: List<String>,
    )

    private val loadedPaths = linkedMapOf<String, String>()

    @Volatile
    private var cachedResult: Result? = null

    /**
     * Loads core Minecraft libraries and all private DT_NEEDED dependencies.
     * A partial load cannot be rolled back safely; after partial failure the
     * host process must be restarted.
     */
    @JvmStatic
    @Synchronized
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun load(context: Context, runtime: ImportedMinecraftRuntime): Result {
        cachedResult?.let { previous ->
            // A successful load is process-wide. A partial native load cannot be
            // rolled back safely and must keep requiring a process restart.
            if (previous.complete || previous.requiresProcessRestart) return previous
        }
        // Validation failures before the first System.load are retryable (for
        // example after re-importing a corrected APK) and must not be memoized.
        cachedResult = null

        val app = context.applicationContext
        val pid = Process.myPid()
        val processName = currentProcessName(app)
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        val abi = supportedAbis.firstOrNull { it == runtime.manifest.abi }.orEmpty()
        val loadedThisCall = mutableListOf<String>()

        fun fail(message: String): Result {
            val result = Result(
                abi = abi.ifBlank { runtime.manifest.abi },
                processName = processName,
                processId = pid,
                loadedLibraries = loadedThisCall.toList(),
                gameLibraryLoaded = "libminecraftpe.so" in loadedPaths,
                complete = false,
                requiresProcessRestart = loadedPaths.isNotEmpty(),
                error = message,
            )
            cachedResult = result.takeIf { it.requiresProcessRestart }
            return result
        }

        if (runtime.manifest.packageName != "com.mojang.minecraftpe") {
            return fail("Paket runtime bukan Minecraft resmi.")
        }
        if (!runtime.isPrepared) {
            return fail("Paket belum siap: library inti Minecraft belum lengkap.")
        }
        if (abi.isBlank()) {
            return fail("ABI ${runtime.manifest.abi} tidak didukung perangkat ini.")
        }
        if (Process.is64Bit() != (abi == "arm64-v8a" || abi == "x86_64")) {
            return fail("Bitness proses Slime tidak cocok dengan ABI runtime $abi.")
        }

        val expectedRoot = File(app.filesDir, "minecraft-runtime/active").canonicalFile
        val runtimeRoot = runtime.rootDirectory.canonicalFile
        if (runtimeRoot != expectedRoot) {
            return fail("Direktori runtime bukan direktori privat aktif milik Slime.")
        }

        val nativeDirectory = runtime.nativeLibraryDirectory.canonicalFile
        if (nativeDirectory.parentFile != runtimeRoot || !nativeDirectory.isDirectory) {
            return fail("Direktori native runtime tidak valid.")
        }

        val plan = try {
            createLoadPlan(nativeDirectory, runtime.manifest.nativeLibraries, abi)
        } catch (error: Exception) {
            return fail(error.message ?: "Gagal memvalidasi native runtime.")
        }

        try {
            for (name in plan.order) {
                val file = plan.files[name] ?: throw IOException("Library $name hilang dari load plan")
                val path = file.canonicalPath
                val previousPath = loadedPaths[name]
                if (previousPath == path) continue
                if (previousPath != null && previousPath != path) {
                    throw IOException("$name sudah termuat dari versi/path lain dalam proses ini; mulai ulang Slime.")
                }
                if (!file.setReadable(true, true) && !file.canRead()) {
                    throw IOException("Tidak bisa membaca $name")
                }

                System.load(path)
                loadedPaths[name] = path
                loadedThisCall += name
            }
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            return fail("Gagal memuat ${loadedThisCall.lastOrNull() ?: "library Minecraft"}: $detail")
        }

        val result = Result(
            abi = abi,
            processName = processName,
            processId = pid,
            loadedLibraries = loadedThisCall.toList(),
            gameLibraryLoaded = "libminecraftpe.so" in loadedPaths,
            complete = "libminecraftpe.so" in loadedPaths,
            requiresProcessRestart = false,
            error = null,
        )
        cachedResult = result
        return result
    }

    /** Returns the load result for this process, if loading has been attempted. */
    @JvmStatic
    fun lastResult(): Result? = cachedResult

    private fun createLoadPlan(
        directory: File,
        manifestNames: List<String>,
        abi: String,
    ): LoadPlan {
        val safeNames = manifestNames.filter { isSafeLibraryName(it) }.toSet()
        if (safeNames.isEmpty()) throw IOException("Manifest tidak berisi daftar native library yang valid.")

        val files = directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".so") && it.name in safeNames }
            .associateBy { it.name }

        val roots = listOf(
            "libc++_shared.so",
            "libfmod.so",
            "libMediaDecoders_Android.so",
            "libminecraftpe.so",
        )
        val missing = roots.filterNot { files[it]?.let { file -> file.length() > 0L } == true }
        if (missing.isNotEmpty()) {
            throw IOException("Library inti tidak ditemukan: ${missing.joinToString()}")
        }

        val parsed = mutableMapOf<String, ElfInfo>()
        val visiting = mutableSetOf<String>()
        val visited = linkedSetOf<String>()

        fun visit(name: String) {
            if (name in visited) return
            if (!visiting.add(name)) {
                throw IOException("Siklus dependensi ELF terdeteksi pada $name")
            }

            val file = files[name] ?: throw IOException("Dependensi privat $name tidak ditemukan.")
            val canonical = file.canonicalFile
            if (canonical.parentFile != directory) {
                throw IOException("Path native library di luar direktori runtime: $name")
            }

            val info = parsed.getOrPut(name) { readElf(canonical, abi) }
            for (dependency in info.needed) {
                if (!isSafeLibraryName(dependency)) {
                    throw IOException("Nama DT_NEEDED tidak valid pada $name: $dependency")
                }
                // Dependencies in this APK set are loaded first. Android
                // platform libraries are resolved by the system linker.
                if (dependency in files) visit(dependency)
            }

            visiting.remove(name)
            visited.add(name)
        }

        roots.forEach(::visit)
        return LoadPlan(directory, files, visited.toList())
    }

    private fun readElf(file: File, abi: String): ElfInfo {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 52L) throw IOException("${file.name}: ELF header terlalu pendek.")
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
                throw IOException("${file.name}: magic ELF tidak valid.")
            }

            val elfClass = input.readUnsignedByte()
            val dataEncoding = input.readUnsignedByte()
            if (dataEncoding != 1) throw IOException("${file.name}: hanya ELF little-endian didukung.")
            val expectedClass = if (abi == "arm64-v8a" || abi == "x86_64") 2 else 1
            val expectedMachine = when (abi) {
                "arm64-v8a" -> 183
                "armeabi-v7a" -> 40
                "x86_64" -> 62
                "x86" -> 3
                else -> throw IOException("ABI tidak dikenal: $abi")
            }
            input.seek(18L)
            val machine = readU16(input)
            if (elfClass != expectedClass || machine != expectedMachine) {
                throw IOException(
                    "${file.name}: ABI ELF tidak cocok (class=$elfClass machine=$machine, expected=$expectedClass/$expectedMachine)."
                )
            }

            val programOffset: Long
            val programEntrySize: Int
            val programCount: Int
            if (elfClass == 2) {
                input.seek(32L)
                programOffset = readU64(input)
                input.seek(54L)
                programEntrySize = readU16(input)
                programCount = readU16(input)
            } else {
                input.seek(28L)
                programOffset = readU32(input)
                input.seek(42L)
                programEntrySize = readU16(input)
                programCount = readU16(input)
            }

            val minimumEntrySize = if (elfClass == 2) 56 else 32
            if (programCount > 4096 || programEntrySize < minimumEntrySize ||
                programOffset < 0L || programOffset > input.length()) {
                throw IOException("${file.name}: program header ELF tidak valid.")
            }

            val segments = mutableListOf<ProgramSegment>()
            repeat(programCount) { index ->
                val entry = programOffset + index.toLong() * programEntrySize
                if (entry < 0L || entry + programEntrySize > input.length()) {
                    throw IOException("${file.name}: program header di luar file.")
                }
                input.seek(entry)
                val type = readU32(input)
                val offset: Long
                val virtualAddress: Long
                val fileSize: Long
                if (elfClass == 2) {
                    input.seek(entry + 8L)
                    offset = readU64(input)
                    virtualAddress = readU64(input)
                    input.seek(entry + 32L)
                    fileSize = readU64(input)
                } else {
                    input.seek(entry + 4L)
                    offset = readU32(input)
                    virtualAddress = readU32(input)
                    input.seek(entry + 16L)
                    fileSize = readU32(input)
                }
                if (offset < 0L || fileSize < 0L || offset > input.length() ||
                    fileSize > input.length() - offset) {
                    throw IOException("${file.name}: segment ELF di luar file.")
                }
                segments += ProgramSegment(type, offset, virtualAddress, fileSize)
            }

            val dynamic = segments.firstOrNull { it.type == PT_DYNAMIC }
                ?: return ElfInfo(elfClass, machine, emptyList())
            val entrySize = if (elfClass == 2) 16 else 8
            val dynamicCount = (dynamic.fileSize / entrySize).coerceAtMost(65_536L).toInt()
            val neededOffsets = mutableListOf<Long>()
            var stringTableAddress: Long? = null
            var stringTableSize: Long? = null

            input.seek(dynamic.offset)
            for (index in 0 until dynamicCount) {
                val tag: Long
                val value: Long
                if (elfClass == 2) {
                    tag = readU64(input)
                    value = readU64(input)
                } else {
                    tag = readU32(input)
                    value = readU32(input)
                }
                if (tag == DT_NULL) break
                when (tag) {
                    DT_NEEDED -> neededOffsets += value
                    DT_STRTAB -> stringTableAddress = value
                    DT_STRSZ -> stringTableSize = value
                }
            }

            if (neededOffsets.isEmpty()) return ElfInfo(elfClass, machine, emptyList())
            val strAddress = stringTableAddress
                ?: throw IOException("${file.name}: DT_STRTAB tidak ditemukan.")
            val stringSegment = segments.firstOrNull { segment ->
                segment.type == PT_LOAD &&
                    strAddress >= segment.virtualAddress &&
                    strAddress - segment.virtualAddress < segment.fileSize
            } ?: throw IOException("${file.name}: DT_STRTAB di luar LOAD segment.")

            val stringBase = stringSegment.offset + (strAddress - stringSegment.virtualAddress)
            val availableSize = (stringTableSize ?: (input.length() - stringBase))
                .coerceAtMost(input.length() - stringBase)
            val needed = neededOffsets.map { stringOffset ->
                if (stringOffset < 0L || stringOffset >= availableSize) {
                    throw IOException("${file.name}: offset DT_NEEDED tidak valid.")
                }
                readCString(input, stringBase + stringOffset, (availableSize - stringOffset).coerceAtMost(4096L).toInt())
            }.distinct()

            return ElfInfo(elfClass, machine, needed)
        }
    }

    private fun readCString(input: RandomAccessFile, offset: Long, limit: Int): String {
        if (limit <= 0) throw IOException("String ELF kosong.")
        input.seek(offset)
        val bytes = ByteArray(limit.coerceAtMost(4096))
        var count = 0
        while (count < bytes.size) {
            val value = input.read()
            if (value < 0) throw IOException("String ELF terpotong.")
            if (value == 0) break
            bytes[count++] = value.toByte()
        }
        if (count == bytes.size) throw IOException("String ELF terlalu panjang.")
        return String(bytes, 0, count, StandardCharsets.UTF_8)
    }

    private fun readU16(input: RandomAccessFile): Int =
        input.readUnsignedByte() or (input.readUnsignedByte() shl 8)

    private fun readU32(input: RandomAccessFile): Long =
        input.readUnsignedByte().toLong() or
            (input.readUnsignedByte().toLong() shl 8) or
            (input.readUnsignedByte().toLong() shl 16) or
            (input.readUnsignedByte().toLong() shl 24)

    private fun readU64(input: RandomAccessFile): Long {
        var result = 0L
        repeat(8) { shift ->
            result = result or (input.readUnsignedByte().toLong() shl (shift * 8))
        }
        return result
    }

    private fun isSafeLibraryName(name: String): Boolean =
        name.matches(Regex("lib[A-Za-z0-9_.+@-]+\\.so")) &&
            !name.contains('/') && !name.contains('\\')

    private fun currentProcessName(context: Context): String =
        if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else "${context.packageName} (pid=${Process.myPid()})"

    private const val PT_LOAD = 1L
    private const val PT_DYNAMIC = 2L
    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L
    private const val DT_STRSZ = 10L
}
