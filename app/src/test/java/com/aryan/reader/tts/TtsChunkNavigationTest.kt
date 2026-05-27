package com.aryan.reader.tts

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    @Test
    fun `only forward chunk skips can reuse an existing playlist item`() {
        assertEquals(3, resolveReusableTtsPlaylistIndex(playlistIndex = 3, direction = 1))
        assertNull(resolveReusableTtsPlaylistIndex(playlistIndex = 3, direction = -1))
        assertNull(resolveReusableTtsPlaylistIndex(playlistIndex = null, direction = 1))
        assertNull(resolveReusableTtsPlaylistIndex(playlistIndex = -1, direction = 1))
    }

    @Test
    fun `automatic playlist advance must stay contiguous by chunk id`() {
        assertEquals(true, shouldAdvanceToTtsPlaylistChunk(currentChunkIndex = 8, playlistChunkIndex = 9))
        assertEquals(false, shouldAdvanceToTtsPlaylistChunk(currentChunkIndex = 8, playlistChunkIndex = 10))
        assertEquals(false, shouldAdvanceToTtsPlaylistChunk(currentChunkIndex = 8, playlistChunkIndex = null))
    }

    @Test
    fun `transition prefetch is deferred only for the rebuilding generation`() {
        assertEquals(false, shouldStartTtsTransitionPrefetch(currentGeneration = 6, deferredGeneration = 6))
        assertEquals(true, shouldStartTtsTransitionPrefetch(currentGeneration = 6, deferredGeneration = 5))
        assertEquals(true, shouldStartTtsTransitionPrefetch(currentGeneration = 6, deferredGeneration = -1))
    }

    @Test
    fun `prefetch stops only when generated chunk is neither loaded nor queued`() {
        assertEquals(true, shouldStopTtsPrefetchAfterMissingChunk(isLoaded = false, playlistIndex = null))
        assertEquals(false, shouldStopTtsPrefetchAfterMissingChunk(isLoaded = true, playlistIndex = null))
        assertEquals(false, shouldStopTtsPrefetchAfterMissingChunk(isLoaded = false, playlistIndex = 2))
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test
    fun `reader tts mini bar is visible only for active reader playback outside reader routes`() {
        val activeReaderState = TtsPlaybackManager.TtsState(
            currentText = "Playing text",
            playbackSource = "READER"
        )

        assertEquals(true, shouldShowReaderTtsMiniBar(activeReaderState, isOnReaderRoute = false))
        assertEquals(false, shouldShowReaderTtsMiniBar(activeReaderState, isOnReaderRoute = true))
        assertEquals(
            false,
            shouldShowReaderTtsMiniBar(activeReaderState.copy(playbackSource = "OTHER"), isOnReaderRoute = false)
        )
        assertEquals(
            false,
            shouldShowReaderTtsMiniBar(activeReaderState.copy(sessionFinished = true), isOnReaderRoute = false)
        )
        assertEquals(
            false,
            shouldShowReaderTtsMiniBar(activeReaderState.copy(sessionEndedByStop = true), isOnReaderRoute = false)
        )
    }

    @Test
    fun `reader tts mini bar clears main bottom navigation`() {
        assertEquals(96, readerTtsMiniBarBottomPaddingDp(isOnMainRoute = true))
        assertEquals(16, readerTtsMiniBarBottomPaddingDp(isOnMainRoute = false))
    }

    @Test
    fun `reader tts overlay size exposes the other two sizes as choices`() {
        assertEquals(
            listOf(ReaderTtsOverlaySize.MEDIUM, ReaderTtsOverlaySize.SMALL),
            readerTtsOverlayAlternativeSizes(ReaderTtsOverlaySize.LARGE)
        )
        assertEquals(
            listOf(ReaderTtsOverlaySize.LARGE, ReaderTtsOverlaySize.SMALL),
            readerTtsOverlayAlternativeSizes(ReaderTtsOverlaySize.MEDIUM)
        )
        assertEquals(
            listOf(ReaderTtsOverlaySize.LARGE, ReaderTtsOverlaySize.MEDIUM),
            readerTtsOverlayAlternativeSizes(ReaderTtsOverlaySize.SMALL)
        )
    }

    @Test
    fun `reader tts overlay stored size defaults to large for missing or invalid values`() {
        assertEquals(ReaderTtsOverlaySize.MEDIUM, resolveReaderTtsOverlaySize("MEDIUM"))
        assertEquals(ReaderTtsOverlaySize.LARGE, resolveReaderTtsOverlaySize(null))
        assertEquals(ReaderTtsOverlaySize.LARGE, resolveReaderTtsOverlaySize("FULL"))
    }

    @Test
    fun `reader tts overlay only aligns small state to the trailing edge`() {
        assertEquals(0f, readerTtsOverlayAlignmentBias(ReaderTtsOverlaySize.LARGE), 0f)
        assertEquals(0f, readerTtsOverlayAlignmentBias(ReaderTtsOverlaySize.MEDIUM), 0f)
        assertEquals(1f, readerTtsOverlayAlignmentBias(ReaderTtsOverlaySize.SMALL), 0f)
    }

    @Test
    fun `reader tts chunk label uses one based progress`() {
        assertEquals("Chunk 1/4", formatReaderTtsChunkLabel(currentChunkIndex = 0, totalChunks = 4))
        assertEquals("Chunk 4/4", formatReaderTtsChunkLabel(currentChunkIndex = 3, totalChunks = 4))
        assertNull(formatReaderTtsChunkLabel(currentChunkIndex = -1, totalChunks = 4))
        assertNull(formatReaderTtsChunkLabel(currentChunkIndex = 4, totalChunks = 4))
        assertNull(formatReaderTtsChunkLabel(currentChunkIndex = 0, totalChunks = 0))
    }

    @Test
    fun `stream pcm duration uses cloud tts audio format`() {
        assertEquals(1_000L, resolveTtsStreamPcmDurationMs(totalBytes = 44L + 48_000L))
        assertNull(resolveTtsStreamPcmDurationMs(totalBytes = 44L))
        assertNull(resolveTtsStreamPcmDurationMs(totalBytes = 0L))
    }

    @Test
    fun `notification duration estimate stays ahead of playback position`() {
        assertEquals(1_500L, estimateTtsNotificationDurationMs(text = "one two"))
        assertEquals(5_000L, estimateTtsNotificationDurationMs(text = "one", currentPositionMs = 3_000L))
        assertNull(estimateTtsNotificationDurationMs(text = "   "))
    }

    @Test
    fun `wav file duration is read from pcm byte rate`() {
        val file = createTempWavFile(pcmBytes = 48_000)

        try {
            assertEquals(1_000L, resolveWavFileDurationMs(file))
        } finally {
            file.delete()
        }
    }

    private fun createTempWavFile(pcmBytes: Int): File {
        val file = File.createTempFile("tts_chunk_navigation_", ".wav")
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(24_000)
            putInt(48_000)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes)
        }.array()

        file.outputStream().use { output ->
            output.write(header)
            output.write(ByteArray(pcmBytes))
        }
        return file
    }
}
