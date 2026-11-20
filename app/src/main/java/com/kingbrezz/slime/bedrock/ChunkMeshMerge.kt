package com.kingbrezz.slime.bedrock

/** Combines chunk-local meshes into one world-space indexed mesh. */
internal fun mergeChunkMeshes(parts: List<Triple<Int, Int, ChunkMeshData>>): ChunkMeshData {
    require(parts.isNotEmpty()) { "At least one chunk mesh is required." }
    var totalFloats = 0L
    var totalIndices = 0L
    for ((_, _, mesh) in parts) {
        require(mesh.vertices.size % 7 == 0) { "Chunk vertex data must use seven floats per vertex." }
        require(mesh.indices.all { it >= 0 && it < mesh.vertexCount }) { "Chunk mesh contains an invalid index." }
        totalFloats += mesh.vertices.size
        totalIndices += mesh.indices.size
    }
    require(totalFloats <= Int.MAX_VALUE && totalIndices <= Int.MAX_VALUE) {
        "Merged chunk mesh exceeds JVM array limits."
    }

    val vertices = FloatArray(totalFloats.toInt())
    val indices = IntArray(totalIndices.toInt())
    var floatOffset = 0
    var vertexOffset = 0
    var indexOffset = 0
    for ((chunkX, chunkZ, mesh) in parts) {
        mesh.vertices.copyInto(vertices, floatOffset)
        for (v in mesh.vertices.indices step 7) {
            vertices[floatOffset + v] += chunkX * 16f
            vertices[floatOffset + v + 2] += chunkZ * 16f
        }
        for (i in mesh.indices.indices) {
            indices[indexOffset + i] = mesh.indices[i] + vertexOffset
        }
        floatOffset += mesh.vertices.size
        vertexOffset += mesh.vertexCount
        indexOffset += mesh.indices.size
    }
    return ChunkMeshData(vertices, indices)
}
