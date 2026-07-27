package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTtsFollowTest {
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
