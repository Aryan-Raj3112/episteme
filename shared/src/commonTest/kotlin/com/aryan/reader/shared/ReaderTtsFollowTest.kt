package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTtsFollowTest {
    @Test
    fun `chunk navigation preserves Android bounds`() {
        assertEquals(1, resolveSharedTtsChunkSkipTarget(2, 5, -1))
        assertEquals(3, resolveSharedTtsChunkSkipTarget(2, 5, 1))
        assertEquals(null, resolveSharedTtsChunkSkipTarget(0, 5, -1))
        assertEquals(null, resolveSharedTtsChunkSkipTarget(4, 5, 1))
        assertEquals(null, resolveSharedTtsChunkSkipTarget(2, 5, 2))
    }

    @Test
    fun `start chunk clamps and transcript window keeps Android asymmetry`() {
        assertEquals(0, resolveSharedTtsStartChunkIndex(-4, 10))
        assertEquals(9, resolveSharedTtsStartChunkIndex(14, 10))
        assertEquals(0, resolveSharedTtsStartChunkIndex(4, 0))
        assertEquals(3 to (3..8), resolveSharedTtsTranscriptWindow(5, 20))
        assertEquals(0 to (0..3), resolveSharedTtsTranscriptWindow(-4, 4))
        assertEquals(0 to IntRange.EMPTY, resolveSharedTtsTranscriptWindow(0, 0))
    }

    @Test
    fun `manual navigation stays detached for the current spoken chunk`() {
        assertFalse(shouldFollowReaderTtsChunk(detachedChunkIndex = 4, currentChunkIndex = 4))
    }

    @Test
    fun `reader rejoins when speech advances to the next chunk`() {
        assertTrue(shouldFollowReaderTtsChunk(detachedChunkIndex = 4, currentChunkIndex = 5))
    }

    @Test
    fun `reader follows normally when it has not been detached`() {
        assertTrue(shouldFollowReaderTtsChunk(detachedChunkIndex = null, currentChunkIndex = 4))
    }

    @Test
    fun `background always requests an immediate position save`() {
        assertEquals(
            ReaderLifecycleAction.SAVE_POSITION,
            readerLifecycleAction(
                isActive = false,
                isTtsActive = true,
                detachedChunkIndex = null,
                currentChunkIndex = 4,
            ),
        )
    }

    @Test
    fun `resume locates speech unless reader is intentionally detached`() {
        assertEquals(
            ReaderLifecycleAction.LOCATE_TTS,
            readerLifecycleAction(true, true, null, 4),
        )
        assertEquals(
            ReaderLifecycleAction.NONE,
            readerLifecycleAction(true, true, 4, 4),
        )
    }
}
