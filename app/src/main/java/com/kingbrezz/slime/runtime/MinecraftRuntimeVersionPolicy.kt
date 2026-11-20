package com.kingbrezz.slime.runtime

/**
 * Shared Minecraft version parsing and native-library requirements.
 *
 * Mojang's 26.x labels map to the 1.26.x version family for comparison with
 * LeviLauncher's native dependency thresholds. Unknown/non-numeric labels fail
 * closed instead of silently receiving the oldest library set.
 */
internal object MinecraftRuntimeVersionPolicy {
    private val numericVersion = Regex("^\\d+(?:\\.\\d+)*$")

    fun isRecognized(versionName: String): Boolean = parse(versionName) != null

    fun isAtLeast(versionName: String, minimum: String): Boolean {
        val current = parse(versionName) ?: return false
        val target = parse(minimum) ?: return false
        for (index in 0 until maxOf(current.size, target.size)) {
            val left = current.getOrElse(index) { 0L }
            val right = target.getOrElse(index) { 0L }
            if (left != right) return left > right
        }
        return true
    }

    /**
     * Libraries extracted from the game's own APKs for this version family.
     * Launcher-side MAE/PlayFab/gxcore dependencies are intentionally not
     * listed here; they require a separate, version-specific bootstrap.
     */
    fun requiredGameLibraries(versionName: String): List<String>? {
        if (!isRecognized(versionName)) return null
        return buildList {
            add("libc++_shared.so")
            add("libfmod.so")
            add("libMediaDecoders_Android.so")
            if (isAtLeast(versionName, "1.21.130")) {
                add("libHttpClient.Android.so")
            }
            if (isAtLeast(versionName, "1.26.70.0")) {
                add("libMallocRedirect.so")
            }
            add("libminecraftpe.so")
        }
    }

    private fun parse(raw: String): List<Long>? {
        val trimmed = raw.trim().removePrefix("v")
        if (trimmed.isEmpty()) return null
        val normalized = if (trimmed.startsWith("26.")) "1.$trimmed" else trimmed
        if (!numericVersion.matches(normalized)) return null
        return normalized.split('.').map { it.toLongOrNull() ?: return null }
    }
}
