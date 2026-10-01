package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android benchmark (`audiobook/BookTtsListening.kt` `toSharedBookTtsListenState`, covered by
 * `UnifiedLibraryScreenTest.generatedAudiobookControllerProjectsServiceStateIntoSharedSessionState`).
 * The projector is shared now, so both platforms are held to the same expectations.
 */
class SharedTtsListenProjectionTest {
    private fun audiobookSnapshot(
        playbackSource: String? = SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK,
    ) = SharedTtsPlaybackSnapshot(
        playbackSource = playbackSource,
        bookId = "book-1",
        isPlaying = true,
        chapterIndex = 1,
        totalChapters = 4,
        currentChunkIndex = 4,
        totalChunks = 10,
        chapterTitle = "Chapter 2",
        transcriptStartIndex = 2,
        transcriptChunks = listOf("A", "B"),
    )

    @Test
    fun projectsAnAudiobookSessionIntoListenState() {
        val projected = audiobookSnapshot().toSharedBookTtsListenState(
            progress = SharedTtsListenSavedProgress(speechRate = 1.2f, pitch = 0.9f),
            preparedChapterCount = 0,
            sleepTimerRemainingMs = 90_000L,
        )

        assertTrue(projected.connected)
        assertTrue(projected.isPlaying)
        assertEquals(1, projected.chapterIndex)
        assertEquals(4, projected.chapterCount)
        assertEquals(4, projected.chunkIndex)
        assertEquals(10, projected.chunkCount)
        assertEquals(0.375f, projected.progressPercent)
        assertEquals(1.2f, projected.speechRate)
        assertEquals(0.9f, projected.pitch)
        assertEquals(90_000L, projected.sleepTimerRemainingMs)
        assertEquals(listOf("A", "B"), projected.transcriptChunks)
        assertEquals("Chapter 2", projected.chapterTitle)
    }

    @Test
    fun aReaderSessionNeverLooksConnectedToListen() {
        val projected = audiobookSnapshot(playbackSource = SHARED_TTS_PLAYBACK_SOURCE_READER)
            .toSharedBookTtsListenState()

        assertFalse(projected.connected)
        assertFalse(projected.isPlaying)
        assertFalse(projected.isLoading)
    }

    @Test
    fun anUntaggedSessionNeverLooksConnectedToListen() {
        val projected = audiobookSnapshot(playbackSource = null).toSharedBookTtsListenState()
        assertFalse(projected.connected)
    }

    @Test
    fun progressIsComputedWhenTheEngineReportsNoPercentage() {
        val projected = audiobookSnapshot().toSharedBookTtsListenState()
        // (1 chapter fully through 10 chunks + 1) / 4 chapters
        assertEquals(0.375f, projected.progressPercent)
    }

    @Test
    fun engineReportedPercentageWins() {
        val projected = audiobookSnapshot()
            .copy(bookProgressPercent = 50)
            .toSharedBookTtsListenState()
        assertEquals(0.5f, projected.progressPercent)
    }

    @Test
    fun fallsBackToSavedChapterAndPreparedCount() {
        val projected = audiobookSnapshot()
            .copy(chapterIndex = null, totalChapters = null)
            .toSharedBookTtsListenState(
                progress = SharedTtsListenSavedProgress(chapterIndex = 3),
                preparedChapterCount = 7,
            )
        assertEquals(3, projected.chapterIndex)
        assertEquals(7, projected.chapterCount)
    }

    @Test
    fun defaultsToOneWhenNoSavedOrPreparedProgressExists() {
        val projected = audiobookSnapshot().toSharedBookTtsListenState()
        assertEquals(1f, projected.speechRate)
        assertEquals(1f, projected.pitch)
        assertNull(projected.error)
        assertEquals(0L, projected.sleepTimerRemainingMs)
    }
}
