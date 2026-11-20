package com.kingbrezz.slime.runtime

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
        val old = MinecraftRuntimeVersionPolicy.requiredGameLibraries("1.21.90")
        assertTrue(old!!.contains("libminecraftpe.so"))
        assertFalse(old.contains("libHttpClient.Android.so"))
        assertFalse(old.contains("libMallocRedirect.so"))

        val http = MinecraftRuntimeVersionPolicy.requiredGameLibraries("1.21.130")
        assertTrue(http!!.contains("libHttpClient.Android.so"))
        assertFalse(http.contains("libMallocRedirect.so"))

        val modern = MinecraftRuntimeVersionPolicy.requiredGameLibraries("26.80.1")
        assertTrue(modern!!.contains("libHttpClient.Android.so"))
        assertTrue(modern.contains("libMallocRedirect.so"))
    }

    @Test
    fun unknownVersionLabelsFailClosed() {
        assertFalse(MinecraftRuntimeVersionPolicy.isRecognized("26.80-preview"))
        assertFalse(MinecraftRuntimeVersionPolicy.isAtLeast("unknown", "1.21.130"))
        assertTrue(MinecraftRuntimeVersionPolicy.requiredGameLibraries("unknown") == null)
    }
}
