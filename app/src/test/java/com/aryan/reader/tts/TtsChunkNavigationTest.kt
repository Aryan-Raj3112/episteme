package com.aryan.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsChunkNavigationTest {
    @Test
    fun `chunk skip target moves one chunk at a time`() {
        assertEquals(1, resolveTtsChunkSkipTarget(currentChunkIndex = 2, totalChunks = 5, direction = -1))
        assertEquals(3, resolveTtsChunkSkipTarget(currentChunkIndex = 2, totalChunks = 5, direction = 1))
    }

    @Test
    fun `chunk skip target is absent at boundaries`() {
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = 0, totalChunks = 5, direction = -1))
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = 4, totalChunks = 5, direction = 1))
    }

    @Test
    fun `chunk skip target is absent for invalid state`() {
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = -1, totalChunks = 5, direction = 1))
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = 5, totalChunks = 5, direction = -1))
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = 0, totalChunks = 0, direction = 1))
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = 0, totalChunks = 5, direction = 0))
        assertNull(resolveTtsChunkSkipTarget(currentChunkIndex = 0, totalChunks = 5, direction = 2))
    }

    @Test
    fun `start chunk index is clamped to available chunks`() {
        assertEquals(2, resolveTtsStartChunkIndex(requestedChunkIndex = 2, totalChunks = 5))
        assertEquals(0, resolveTtsStartChunkIndex(requestedChunkIndex = -1, totalChunks = 5))
        assertEquals(4, resolveTtsStartChunkIndex(requestedChunkIndex = 7, totalChunks = 5))
        assertEquals(0, resolveTtsStartChunkIndex(requestedChunkIndex = 2, totalChunks = 0))
    }
}
