package com.kingbrezz.slime.bedrock

/** Runtime Vulkan device bootstrap for the independent Slime engine. */
object VulkanBackendNative {
    init {
        System.loadLibrary("slime_preloader")
    }

    external fun nativeInitialize(): Array<String>
    external fun nativeShutdown()

    fun initialize(): VulkanDeviceInfo {
        val values = nativeInitialize()
        require(values.size == 8) { "Invalid Vulkan native response" }
        return VulkanDeviceInfo(
            available = values[0].toBoolean(),
            status = values[1],
            deviceName = values[2],
            apiVersion = values[3],
            driverVersion = values[4],
            vendorId = values[5].toLongOrNull() ?: 0L,
            deviceType = values[6].toIntOrNull() ?: 0,
            deviceLocalMemoryBytes = values[7].toLongOrNull() ?: 0L,
        )
    }
}

data class VulkanDeviceInfo(
    val available: Boolean,
    val status: String,
    val deviceName: String,
    val apiVersion: String,
    val driverVersion: String,
    val vendorId: Long,
    val deviceType: Int,
    val deviceLocalMemoryBytes: Long,
) {
    val deviceLocalMemoryMiB: Long
        get() = deviceLocalMemoryBytes / (1024L * 1024L)
}
