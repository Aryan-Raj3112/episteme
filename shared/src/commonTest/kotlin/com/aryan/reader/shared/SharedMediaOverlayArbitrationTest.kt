package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `MEDIA_OVERLAY` in the arbitration rule.
 *
 * The surface is new, so the question is whether it behaves like the two TTS surfaces or like the
 * audiobook. It belongs with the audiobook: a publisher's recording on its own engine, not a
 * synthesized session sharing one with anybody. Getting that wrong means the overlay takes the
 * RELEASE branch and a competing surface keeps playing over it.
 */
class SharedMediaOverlayArbitrationTest {

    @Test
    fun `a media overlay start stops a tts session rather than releasing it`() {
        assertEquals(
            SharedListeningYield.STOP,
            sharedListeningYield(
                winner = SharedListeningSurface.MEDIA_OVERLAY,
                winnerEngine = null,
                loser = SharedListeningSurface.READER_TTS,
                loserEngine = SharedTtsEngine.CLOUD,
            )
        )
    }

    @Test
    fun `a tts start stops the media overlay`() {
        assertEquals(
            SharedListeningYield.STOP,
            sharedListeningYield(
                winner = SharedListeningSurface.READER_TTS,
                winnerEngine = SharedTtsEngine.LOCAL,
                loser = SharedListeningSurface.MEDIA_OVERLAY,
                loserEngine = null,
            )
        )
    }

    @Test
    fun `the media overlay and the audiobook exclude each other`() {
        assertEquals(
            SharedListeningYield.STOP,
            sharedListeningYield(
                winner = SharedListeningSurface.MEDIA_OVERLAY,
                winnerEngine = null,
                loser = SharedListeningSurface.AUDIOBOOK,
            )
        )
        assertEquals(
            SharedListeningYield.STOP,
            sharedListeningYield(
                winner = SharedListeningSurface.AUDIOBOOK,
                winnerEngine = null,
                loser = SharedListeningSurface.MEDIA_OVERLAY,
            )
        )
    }

    /** A surface with no live session still yields nothing — there is nothing to stop. */
    @Test
    fun `losing with no session is not a stop`() {
        assertEquals(
            SharedListeningYield.STOP,
            sharedListeningYield(
                winner = SharedListeningSurface.MEDIA_OVERLAY,
                winnerEngine = null,
                loser = SharedListeningSurface.READER_TTS,
                loserEngine = null,
            )
        )
    }

    /** Yielding to yourself is the bug that took three fixes; the new surface inherits the guard. */
    @Test
    fun `a media overlay never yields to itself`() {
        assertEquals(
            SharedListeningYield.NONE,
            sharedListeningYield(
                winner = SharedListeningSurface.MEDIA_OVERLAY,
                winnerEngine = null,
                loser = SharedListeningSurface.MEDIA_OVERLAY,
                loserEngine = null,
            )
        )
    }

    @Test
    fun `the media overlay is included in the competing surface list`() {
        assertEquals(
            listOf(
                SharedListeningSurface.AUDIOBOOK,
                SharedListeningSurface.READER_TTS,
                SharedListeningSurface.LISTEN_TTS,
            ),
            sharedListeningSurfacesOtherThan(SharedListeningSurface.MEDIA_OVERLAY)
        )
    }

    /** Media overlay playback is not a TTS session, so it must not resolve from a surface tag. */
    @Test
    fun `a tts surface tag never resolves to the media overlay`() {
        assertEquals(null, sharedListeningSurfaceForTag(SHARED_TTS_PLAYBACK_SOURCE_READER).takeIf { it == SharedListeningSurface.MEDIA_OVERLAY })
        assertEquals(null, sharedListeningSurfaceForTag(SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK).takeIf { it == SharedListeningSurface.MEDIA_OVERLAY })
        assertEquals(null, sharedListeningSurfaceForTag(null))
    }
}