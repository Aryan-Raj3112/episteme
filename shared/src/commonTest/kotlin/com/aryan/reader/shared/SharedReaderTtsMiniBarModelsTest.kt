package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedReaderTtsMiniBarModelsTest {

    private fun readerState(
        currentText: String? = "Playing text",
        playbackSource: String = "READER"
    ) = SharedReaderTtsMiniBarState(
        currentText = currentText,
        playbackSource = playbackSource
    )

    @Test
    fun showsOnlyForActiveReaderPlaybackOutsideReaderRoutes() {
        val active = readerState()

        assertTrue(shouldShowSharedReaderTtsMiniBar(active, isOnReaderRoute = false))
        assertFalse(shouldShowSharedReaderTtsMiniBar(active, isOnReaderRoute = true))
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                active.copy(playbackSource = "OTHER"),
                isOnReaderRoute = false
            )
        )
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                active.copy(sessionFinished = true),
                isOnReaderRoute = false
            )
        )
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                active.copy(sessionEndedByStop = true),
                isOnReaderRoute = false
            )
        )
    }

    @Test
    fun hiddenWhenStateIsAbsentOrIdle() {
        assertFalse(shouldShowSharedReaderTtsMiniBar(null, isOnReaderRoute = false))
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                readerState(currentText = "   "),
                isOnReaderRoute = false
            )
        )
    }

    @Test
    fun loadingSessionStaysVisibleWithoutText() {
        assertTrue(
            shouldShowSharedReaderTtsMiniBar(
                readerState(currentText = null).copy(isLoading = true),
                isOnReaderRoute = false
            )
        )
    }

    @Test
    fun clearsMainBottomNavigation() {
        assertEquals(96, sharedReaderTtsMiniBarBottomPaddingDp(isOnMainRoute = true))
        assertEquals(16, sharedReaderTtsMiniBarBottomPaddingDp(isOnMainRoute = false))
    }

    @Test
    fun chunkLabelIsOneBasedAndBlankOutOfRange() {
        assertEquals("Chunk 1/4", readerTtsChunkLabel(currentChunkIndex = 0, totalChunks = 4))
        assertEquals("Chunk 4/4", readerTtsChunkLabel(currentChunkIndex = 3, totalChunks = 4))
        assertEquals(null, readerTtsChunkLabel(currentChunkIndex = -1, totalChunks = 4))
        assertEquals(null, readerTtsChunkLabel(currentChunkIndex = 4, totalChunks = 4))
        assertEquals(null, readerTtsChunkLabel(currentChunkIndex = 0, totalChunks = 0))
    }

    @Test
    fun stateChunkLabelMatchesTheStandaloneFormatter() {
        val state = SharedReaderTtsMiniBarState(chunkIndex = 1, totalChunks = 3)
        assertEquals("Chunk 2/3", state.chunkLabel())
        assertEquals(readerTtsChunkLabel(1, 3), state.chunkLabel())

        val outOfRange = SharedReaderTtsMiniBarState(chunkIndex = 9, totalChunks = 3)
        assertEquals("", outOfRange.chunkLabel())
        assertEquals(null, readerTtsChunkLabel(9, 3))
    }

    @Test
    fun subtitleJoinsChunkLabelAndChapter() {
        val state = SharedReaderTtsMiniBarState(
            chapterTitle = "Chapter 2",
            chunkIndex = 0,
            totalChunks = 2
        )
        assertEquals("Chunk 1/2 - Chapter 2", state.subtitle())
    }

    @Test
    fun subtitleOmitsBlankSegments() {
        assertEquals(
            "Chapter 1",
            SharedReaderTtsMiniBarState(chapterTitle = "Chapter 1").subtitle()
        )
        assertEquals("", SharedReaderTtsMiniBarState().subtitle())
    }
}
