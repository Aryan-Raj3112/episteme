package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each case here is a bug that actually shipped: the first three were found on the simulator,
 * the rest are the shapes a hand-written call site got wrong on the way to those fixes.
 */
class SharedListeningArbitrationTest {

    private fun yield(
        winner: SharedListeningSurface,
        winnerEngine: SharedTtsEngine?,
        loser: SharedListeningSurface,
        loserEngine: SharedTtsEngine? = null,
    ) = sharedListeningYield(winner, winnerEngine, loser, loserEngine)

    @Test
    fun aSurfaceNeverYieldsToItself() {
        // The whole point: reader read-aloud starting must never stop the engine it just
        // started. This shipped as a live bug three separate times.
        assertEquals(
            SharedListeningYield.NONE,
            yield(
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.LOCAL,
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.LOCAL,
            ),
        )
        assertEquals(
            SharedListeningYield.NONE,
            yield(
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.CLOUD,
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.CLOUD,
            ),
        )
    }

    @Test
    fun readerLocalReadAloudReleasesListenOnTheSameEngine() {
        // Opening a book during Listen playback used to kill the audiobook session: the reader
        // saw a live session, reported it as its own, and stopped the engine it had just started.
        assertEquals(
            SharedListeningYield.RELEASE,
            yield(
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.LOCAL,
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.LOCAL,
            ),
        )
    }

    @Test
    fun readerCloudReadAloudReleasesListenOnTheSameCloudEngine() {
        // Listen's cloud session is the same object the reader drives, so a reader cloud session
        // replacing it must not stop the engine.
        assertEquals(
            SharedListeningYield.RELEASE,
            yield(
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.CLOUD,
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.CLOUD,
            ),
        )
    }

    @Test
    fun listenCloudIsStoppedWhenTheReaderTakesTheLocalEngine() {
        // Different engines either way round. The reader's local session never touched Listen's
        // cloud audio, so it has to be stopped explicitly or both surfaces speak at once.
        assertEquals(
            SharedListeningYield.STOP,
            yield(
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.LOCAL,
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.CLOUD,
            ),
        )
    }

    @Test
    fun listenLocalIsStoppedWhenTheReaderTakesTheCloudEngine() {
        // Different engines: the reader's cloud session never touched Listen's local audio, so
        // leaving it running would mean both surfaces speak at once.
        assertEquals(
            SharedListeningYield.STOP,
            yield(
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.CLOUD,
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.LOCAL,
            ),
        )
    }

    @Test
    fun audiobookAlwaysExcludesTts() {
        assertEquals(
            SharedListeningYield.STOP,
            yield(
                SharedListeningSurface.AUDIOBOOK,
                null,
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.LOCAL,
            ),
        )
        assertEquals(
            SharedListeningYield.STOP,
            yield(
                SharedListeningSurface.AUDIOBOOK,
                null,
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.CLOUD,
            ),
        )
    }

    @Test
    fun ttsAlwaysExcludesTheAudiobook() {
        assertEquals(
            SharedListeningYield.STOP,
            yield(
                SharedListeningSurface.LISTEN_TTS,
                SharedTtsEngine.LOCAL,
                SharedListeningSurface.AUDIOBOOK,
            ),
        )
    }

    @Test
    fun aSurfaceWithNoLiveSessionDoesNothing() {
        assertEquals(
            SharedListeningYield.NONE,
            yield(
                SharedListeningSurface.READER_TTS,
                SharedTtsEngine.LOCAL,
                SharedListeningSurface.LISTEN_TTS,
                null,
            ),
        )
    }

    @Test
    fun audiobookIsExcludedFromItsOwnOppositionSet() {
        assertEquals(
            listOf(SharedListeningSurface.READER_TTS, SharedListeningSurface.LISTEN_TTS),
            sharedListeningSurfacesOtherThan(SharedListeningSurface.AUDIOBOOK),
        )
        assertEquals(
            listOf(
                SharedListeningSurface.AUDIOBOOK,
                SharedListeningSurface.LISTEN_TTS,
            ),
            sharedListeningSurfacesOtherThan(SharedListeningSurface.READER_TTS),
        )
    }
}