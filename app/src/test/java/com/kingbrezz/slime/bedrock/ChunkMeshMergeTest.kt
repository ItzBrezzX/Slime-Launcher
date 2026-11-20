package com.kingbrezz.slime.bedrock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkMeshMergeTest {
    private fun quad(): ChunkMeshData = ChunkMeshData(
        floatArrayOf(
            0f, 1f, 0f, 0f, 1f, 0f, 1f,
            1f, 1f, 0f, 0f, 1f, 0f, 1f,
            1f, 1f, 1f, 0f, 1f, 0f, 1f,
            0f, 1f, 1f, 0f, 1f, 0f, 1f,
        ),
        intArrayOf(0, 1, 2, 0, 2, 3),
    )

    @Test
    fun mergesChunkOffsetsAndRebasesIndices() {
        val merged = mergeChunkMeshes(
            listOf(
                Triple(0, 0, quad()),
                Triple(1, -1, quad()),
            ),
        )

        assertEquals(8, merged.vertexCount)
        assertEquals(12, merged.indices.size)
        assertArrayEquals(intArrayOf(0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7), merged.indices)
        assertEquals(16f, merged.vertices[7 * 4], 0.0f)
        assertEquals(-16f, merged.vertices[7 * 4 + 2], 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeChunkIndices() {
        mergeChunkMeshes(listOf(Triple(0, 0, ChunkMeshData(FloatArray(7), intArrayOf(0, 1, 2)))))
    }
}
