package com.kingbrezz.slime.bedrock

/** JNI facade for Slime's independent voxel-engine core. */
object BedrockEngineNative {
    const val ENGINE_VERSION = "0.2.0-dev"
    init { System.loadLibrary("slime_preloader") }
    external fun nativeCreateWorld(seed: Long)
    external fun nativeGenerateChunk(chunkX: Int, chunkZ: Int): Boolean
    external fun nativeGetBlock(worldX: Int, y: Int, worldZ: Int): Int
    external fun nativeGetChunkStats(chunkX: Int, chunkZ: Int): LongArray
    external fun nativeGetChunkMeshVertices(chunkX: Int, chunkZ: Int): FloatArray
    external fun nativeGetChunkMeshIndices(chunkX: Int, chunkZ: Int): IntArray
    external fun nativeChunkStorageBytes(chunkX: Int, chunkZ: Int): Long
    external fun nativeTotalStorageBytes(): Long
    external fun nativeLoadedChunkCount(): Int
    external fun nativeReset()
    fun createWorld(seed: Long) = nativeCreateWorld(seed)
    fun generateChunk(x: Int, z: Int) = nativeGenerateChunk(x, z)
    fun blockAt(x: Int, y: Int, z: Int) = BlockId.fromNative(nativeGetBlock(x,y,z))
    fun chunkStats(x: Int, z: Int): ChunkStats {
        val v=nativeGetChunkStats(x,z); require(v.size==5) { "Invalid native chunk stats" }
        return ChunkStats(v[0],v[1],v[2],v[3].toInt(),v[4].toInt())
    }
    fun chunkMesh(x: Int,z: Int): ChunkMeshData {
        val v=nativeGetChunkMeshVertices(x,z); val i=nativeGetChunkMeshIndices(x,z)
        require(v.size%7==0 && i.size%6==0) { "Invalid native mesh buffer" }
        return ChunkMeshData(v,i)
    }
    fun chunkStorageBytes(x:Int,z:Int)=nativeChunkStorageBytes(x,z)
    fun totalStorageBytes()=nativeTotalStorageBytes()
    fun loadedChunkCount()=nativeLoadedChunkCount()
    fun reset()=nativeReset()
}
enum class BlockId(val nativeId:Int) {
    AIR(0),GRASS(1),DIRT(2),STONE(3),WATER(4),SAND(5),BEDROCK(6);
    companion object { fun fromNative(value:Int)=entries.firstOrNull{it.nativeId==value}?:AIR }
}
data class ChunkStats(val solidBlocks:Long,val waterBlocks:Long,val visibleFaces:Long,val minHeight:Int,val maxHeight:Int)
data class ChunkMeshData(val vertices:FloatArray,val indices:IntArray) {
    val vertexCount get()=vertices.size/7
    val faceCount get()=indices.size/6
    val memoryBytes get()=vertices.size.toLong()*Float.SIZE_BYTES+indices.size.toLong()*Int.SIZE_BYTES
}
