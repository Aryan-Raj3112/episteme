package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity item B10. These cover the TTS playlist/stream rules lifted out of
 * Android's `TtsPlaybackManager.kt`.
 *
 * Android's own `TtsChunkNavigationTest` still exercises the same logic
 * through the delegating wrappers and must stay green — that pair is what
 * proves the move preserved behavior. These tests give the rules a home that
 * runs on every platform.
 */
class ReaderTtsPlaylistTest {

    @Test
    fun `only forward skips can reuse an existing playlist item`() {
        assertEquals(3, resolveSharedTtsReusablePlaylistIndex(playlistIndex = 3, direction = 1))
        assertNull(resolveSharedTtsReusablePlaylistIndex(playlistIndex = 3, direction = -1))
        assertNull(resolveSharedTtsReusablePlaylistIndex(playlistIndex = null, direction = 1))
        // A negative index is not a real playlist slot.
        assertNull(resolveSharedTtsReusablePlaylistIndex(playlistIndex = -1, direction = 1))
        // Direction 0 is not a skip in either direction.
        assertNull(resolveSharedTtsReusablePlaylistIndex(playlistIndex = 3, direction = 0))
    }

    @Test
    fun `playlist exposure waits for the contiguous previous chunk`() {
        assertTrue(canExposeSharedTtsChunkInPlaylist(2, listOf(0, 1)))
        assertFalse(canExposeSharedTtsChunkInPlaylist(3, listOf(0, 1)))
        // Chunk 0 has no predecessor, so it is always exposable.
        assertTrue(canExposeSharedTtsChunkInPlaylist(0, emptyList()))
        // Already present.
        assertTrue(canExposeSharedTtsChunkInPlaylist(1, listOf(0, 1)))
        // Negative chunk ids are never exposable.
        assertFalse(canExposeSharedTtsChunkInPlaylist(-1, listOf(0, 1)))
    }

    @Test
    fun `insert position keeps the playlist sorted and refuses gaps`() {
        assertEquals(2, resolveSharedTtsContiguousPlaylistInsertPosition(2, listOf(0, 1)))
        // Gap at 2 with 3 following: refused rather than leaving a hole.
        assertNull(resolveSharedTtsContiguousPlaylistInsertPosition(3, listOf(0, 1)))
        // Insert before a larger existing id.
        assertEquals(0, resolveSharedTtsContiguousPlaylistInsertPosition(0, listOf(1, 2)))
        // Insert at the end when the predecessor is already there.
        assertEquals(3, resolveSharedTtsContiguousPlaylistInsertPosition(3, listOf(0, 1, 2)))
        // Already present: nothing to do.
        assertNull(resolveSharedTtsContiguousPlaylistInsertPosition(1, listOf(0, 1)))
    }

    @Test
    fun `forward skip waits only while the target is prefetching without a playlist slot`() {
        assertTrue(
            shouldWaitForSharedInFlightTtsSkip(
                direction = 1,
                isTargetPrefetching = true,
                targetPlaylistIndex = null,
            ),
        )
        // Backward skips seek rather than reuse, so they never wait.
        assertFalse(
            shouldWaitForSharedInFlightTtsSkip(
                direction = -1,
                isTargetPrefetching = true,
                targetPlaylistIndex = null,
            ),
        )
        // Already has a playlist slot: no wait.
        assertFalse(
            shouldWaitForSharedInFlightTtsSkip(
                direction = 1,
                isTargetPrefetching = true,
                targetPlaylistIndex = 2,
            ),
        )
        // Not prefetching: nothing in flight to wait for.
        assertFalse(
            shouldWaitForSharedInFlightTtsSkip(
                direction = 1,
                isTargetPrefetching = false,
                targetPlaylistIndex = null,
            ),
        )
    }

    @Test
    fun `chunk generation gives up after bounded failures`() {
        assertFalse(shouldGiveUpSharedTtsChunkGeneration(0))
        assertFalse(shouldGiveUpSharedTtsChunkGeneration(1))
        assertTrue(shouldGiveUpSharedTtsChunkGeneration(MAX_TTS_CHUNK_GENERATION_FAILURES))
        assertTrue(shouldGiveUpSharedTtsChunkGeneration(99))
        // The limit is a parameter so a caller can be stricter or looser.
        assertTrue(shouldGiveUpSharedTtsChunkGeneration(1, maxFailures = 1))
        assertFalse(shouldGiveUpSharedTtsChunkGeneration(1, maxFailures = 5))
    }

    @Test
    fun `stream duration is derived from pcm byte length`() {
        // 48 bytes/ms means a 44-byte header plus one millisecond of audio.
        assertEquals(1L, resolveSharedTtsStreamPcmDurationMs(TTS_STREAM_WAV_HEADER_BYTES + 48))
        assertEquals(1000L, resolveSharedTtsStreamPcmDurationMs(TTS_STREAM_WAV_HEADER_BYTES + 48_000))
        // At or below the header there is no audio to measure.
        assertNull(resolveSharedTtsStreamPcmDurationMs(TTS_STREAM_WAV_HEADER_BYTES))
        assertNull(resolveSharedTtsStreamPcmDurationMs(0L))
        // A sliver of audio still reports at least one millisecond.
        assertEquals(1L, resolveSharedTtsStreamPcmDurationMs(TTS_STREAM_WAV_HEADER_BYTES + 1))
    }

    @Test
    fun `notification duration covers words punctuation and playback position`() {
        // Two words, no punctuation, below the 1.5s floor: the floor wins.
        assertEquals(1_500L, estimateSharedTtsNotificationDurationMs("hello world"))
        // Empty or whitespace-only text has nothing to show.
        assertNull(estimateSharedTtsNotificationDurationMs(""))
        assertNull(estimateSharedTtsNotificationDurationMs("   \n\t "))
        // Punctuation adds pauses on top of the word estimate.
        val plain = estimateSharedTtsNotificationDurationMs(
            "one two three four five six seven eight",
        )!!
        val punctuated = estimateSharedTtsNotificationDurationMs(
            "one. two! three? four: five; six seven eight",
        )!!
        assertTrue(punctuated > plain, "punctuation should lengthen the estimate")
        // Never shorter than the current playback position plus a trailing buffer,
        // or the notification would disappear mid-sentence.
        assertEquals(6_000L, estimateSharedTtsNotificationDurationMs("hi", currentPositionMs = 4_000L))
        // Word estimate still wins when it already exceeds that floor:
        // 100 words at 550ms each is 55s.
        val hundredWords = List(100) { "a" }.joinToString(" ")
        assertEquals(55_000L, estimateSharedTtsNotificationDurationMs(hundredWords))
        // A single unbroken token is one word, not one word per character.
        assertEquals(1_500L, estimateSharedTtsNotificationDurationMs("a".repeat(100)))
    }
}
