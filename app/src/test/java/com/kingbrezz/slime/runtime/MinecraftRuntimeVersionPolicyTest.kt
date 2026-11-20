package com.kingbrezz.slime.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftRuntimeVersionPolicyTest {

    @Test
    fun modernYearStyleVersionsUseTheOnePointTwentySixFamily() {
        assertTrue(MinecraftRuntimeVersionPolicy.isAtLeast("26.80.1", "1.26.70.0"))
        assertTrue(MinecraftRuntimeVersionPolicy.isAtLeast("26.70.2", "1.26.70.0"))
        assertFalse(MinecraftRuntimeVersionPolicy.isAtLeast("26.60.3", "1.26.70.0"))
    }

    @Test
    fun httpClientThresholdIsNumericNotLexicographic() {
        assertTrue(MinecraftRuntimeVersionPolicy.isAtLeast("1.21.130", "1.21.130"))
        assertTrue(MinecraftRuntimeVersionPolicy.isAtLeast("1.21.140.2", "1.21.130"))
        assertFalse(MinecraftRuntimeVersionPolicy.isAtLeast("1.21.99", "1.21.130"))
    }

    @Test
    fun requiredLibrariesFollowVersionThresholds() {
        val old = requireNotNull(MinecraftRuntimeVersionPolicy.requiredGameLibraries("1.21.90"))
        assertTrue(old.contains("libminecraftpe.so"))
        assertFalse(old.contains("libHttpClient.Android.so"))
        assertFalse(old.contains("libMallocRedirect.so"))

        val http = requireNotNull(MinecraftRuntimeVersionPolicy.requiredGameLibraries("1.21.130"))
        assertTrue(http.contains("libHttpClient.Android.so"))
        assertFalse(http.contains("libMallocRedirect.so"))

        val modern = requireNotNull(MinecraftRuntimeVersionPolicy.requiredGameLibraries("26.80.1"))
        assertTrue(modern.contains("libHttpClient.Android.so"))
        assertTrue(modern.contains("libMallocRedirect.so"))
    }


    @Test
    fun importedRuntimeMustContainEveryVersionRequiredLibrary() {
        val required = requireNotNull(MinecraftRuntimeVersionPolicy.requiredGameLibraries("26.80.1"))
        val root = Files.createTempDirectory("slime-runtime-policy").toFile()
        try {
            val nativeDir = File(root, "native").apply { mkdirs() }
            val present = required.filterNot { it == "libMallocRedirect.so" }
            present.forEach { File(nativeDir, it).writeBytes(byteArrayOf(0x7f.toByte())) }
            val manifest = MinecraftRuntimeManifest(
                packageName = "com.mojang.minecraftpe",
                versionName = "26.80.1",
                versionCode = 1L,
                abi = "arm64-v8a",
                apkFiles = emptyList(),
                apkSha256 = emptyMap(),
                nativeLibraries = present,
                nativeDirectory = "native",
                importedAtMillis = 0L,
            )
            assertFalse(ImportedMinecraftRuntime(manifest, root).isPrepared)

            val missing = File(nativeDir, "libMallocRedirect.so")
            missing.writeBytes(byteArrayOf(0x7f.toByte()))
            val completeManifest = manifest.copy(nativeLibraries = required)
            assertTrue(ImportedMinecraftRuntime(completeManifest, root).isPrepared)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unknownVersionLabelsFailClosed() {
        assertFalse(MinecraftRuntimeVersionPolicy.isRecognized("26.80-preview"))
        assertFalse(MinecraftRuntimeVersionPolicy.isAtLeast("unknown", "1.21.130"))
        assertTrue(MinecraftRuntimeVersionPolicy.requiredGameLibraries("unknown") == null)
    }
}
