package com.kingbrezz.slime.bedrock

/**
 * JNI facade for Slime's independent Bedrock-style engine core.
 *
 * This runtime does not load libminecraftpe.so and does not call Mojang's
 * Activity or native bootstrap. It is an original voxel-engine foundation,
 * not yet a complete Minecraft Bedrock implementation.
 */
object BedrockEngineNative {
    const val ENGINE_VERSION = "0.1.0-dev"

    init {
        System.loadLibrary("slime_preloader")
    }

    external fun nativeCreateWorld(seed: Long)
    external fun nativeGenerateChunk(chunkX: Int, chunkZ: Int): Boolean
    external fun nativeGetBlock(worldX: Int, y: Int, worldZ: Int): Int
    external fun nativeGetChunkStats(chunkX: Int, chunkZ: Int): LongArray
    external fun nativeLoadedChunkCount(): Int
    external fun nativeReset()

    fun createWorld(seed: Long) = nativeCreateWorld(seed)
    fun generateChunk(x: Int, z: Int) = nativeGenerateChunk(x, z)
    fun blockAt(x: Int, y: Int, z: Int): BlockId =
        BlockId.fromNative(nativeGetBlock(x, y, z))
    fun chunkStats(x: Int, z: Int): ChunkStats {
        val values = nativeGetChunkStats(x, z)
        require(values.size == 5) { "Invalid native chunk stats" }
        return ChunkStats(
            solidBlocks = values[0],
            waterBlocks = values[1],
            visibleFaces = values[2],
            minHeight = values[3].toInt(),
            maxHeight = values[4].toInt(),
        )
    }
    fun loadedChunkCount() = nativeLoadedChunkCount()
    fun reset() = nativeReset()
}

enum class BlockId(val nativeId: Int) {
    AIR(0), GRASS(1), DIRT(2), STONE(3), WATER(4), SAND(5), BEDROCK(6);

    companion object {
        fun fromNative(value: Int) = entries.firstOrNull { it.nativeId == value } ?: AIR
    }
}

data class ChunkStats(
    val solidBlocks: Long,
    val waterBlocks: Long,
    val visibleFaces: Long,
    val minHeight: Int,
    val maxHeight: Int,
)
