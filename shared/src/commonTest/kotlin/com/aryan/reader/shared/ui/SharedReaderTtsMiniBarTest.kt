package com.aryan.reader.shared.ui

import com.aryan.reader.shared.SharedReaderTtsMiniBarState
import com.aryan.reader.shared.chunkLabel
import com.aryan.reader.shared.sharedReaderTtsMiniBarBottomPaddingDp
import com.aryan.reader.shared.shouldShowSharedReaderTtsMiniBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedReaderTtsMiniBarTest {

    private fun activeState() = SharedReaderTtsMiniBarState(
        bookId = "book-1",
        bookTitle = "Pride and Prejudice",
        chapterTitle = "Chapter 1",
        chunkIndex = 0,
        totalChunks = 10,
        currentText = "It is a truth universally acknowledged",
        isLoading = false,
        isPlaying = true,
        playbackSource = "READER"
    )

    @Test
    fun `mini bar shows for active reader session off reader route`() {
        assertTrue(shouldShowSharedReaderTtsMiniBar(activeState(), isOnReaderRoute = false))
    }

    @Test
    fun `mini bar hidden on reader route`() {
        assertFalse(shouldShowSharedReaderTtsMiniBar(activeState(), isOnReaderRoute = true))
    }

    @Test
    fun `mini bar hidden for non-reader source`() {
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                activeState().copy(playbackSource = "LIBRARY"),
                isOnReaderRoute = false
            )
        )
    }

    @Test
    fun `mini bar hidden after stop or finish`() {
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                activeState().copy(sessionEndedByStop = true),
                isOnReaderRoute = false
            )
        )
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                activeState().copy(sessionFinished = true),
                isOnReaderRoute = false
            )
        )
    }

    @Test
    fun `mini bar hidden without text unless loading`() {
        assertFalse(
            shouldShowSharedReaderTtsMiniBar(
                activeState().copy(currentText = "  "),
                isOnReaderRoute = false
            )
        )
        assertTrue(
            shouldShowSharedReaderTtsMiniBar(
                activeState().copy(currentText = null, isLoading = true),
                isOnReaderRoute = false
            )
        )
        assertFalse(shouldShowSharedReaderTtsMiniBar(null, isOnReaderRoute = false))
    }

    @Test
    fun `mini bar bottom padding follows main route`() {
        assertEquals(96, sharedReaderTtsMiniBarBottomPaddingDp(isOnMainRoute = true))
        assertEquals(16, sharedReaderTtsMiniBarBottomPaddingDp(isOnMainRoute = false))
    }

    @Test
    fun `chunk label follows android chunk slash total format`() {
        assertEquals("Chunk 1/10", activeState().chunkLabel())
        assertEquals("", activeState().copy(chunkIndex = 10).chunkLabel())
        assertEquals("", activeState().copy(chunkIndex = -1).chunkLabel())
        assertEquals("", activeState().copy(totalChunks = 0).chunkLabel())
    }
}
