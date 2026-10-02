package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Wiring tests for [SharedListeningArbiter].
 *
 * The decision rule is covered by [SharedListeningArbitrationTest]; what is tested here is that
 * each intent reaches the right engine — which is where all three real bugs were, since every one
 * of them was a correct-looking call to the wrong `stop`.
 */
class SharedListeningArbiterTest {

    /** Records which engine each intent reached. */
    private class Calls {
        val log = mutableListOf<String>()
        var listenEngine: SharedTtsEngine? = null

        fun arbiter() = SharedListeningArbiter(
            listenEngine = { listenEngine },
            stopAudiobook = { log += "stopAudiobook" },
            stopListen = { log += "stopListen" },
            releaseListen = { log += "releaseListen" },
            stopReaderLocal = { log += "stopReaderLocal" },
            stopReaderCloud = { log += "stopReaderCloud" },
        )
    }

    @Test
    fun readerLocalReadAloudReleasesListenRatherThanStoppingIt() {
        // Both surfaces are on the local engine, so the reader's session replaced Listen's.
        // Stopping here would silence the reader's own new session.
        val calls = Calls().apply { listenEngine = SharedTtsEngine.LOCAL }
        calls.arbiter().onTtsSessionActivated(SharedListeningSurface.READER_TTS, SharedTtsEngine.LOCAL)
        assertEquals(listOf("stopAudiobook", "releaseListen"), calls.log)
    }

    @Test
    fun readerCloudReadAloudReleasesListenOnTheCloudEngine() {
        val calls = Calls().apply { listenEngine = SharedTtsEngine.CLOUD }
        calls.arbiter().onTtsSessionActivated(SharedListeningSurface.READER_TTS, SharedTtsEngine.CLOUD)
        assertEquals(listOf("stopAudiobook", "releaseListen"), calls.log)
    }

    @Test
    fun readerReadAloudStopsListenOnTheOppositeEngine() {
        // Reader on local, Listen still on cloud: nothing the reader did touched Listen's cloud
        // audio, so it has to be stopped or both surfaces speak.
        val calls = Calls().apply { listenEngine = SharedTtsEngine.CLOUD }
        calls.arbiter().onTtsSessionActivated(SharedListeningSurface.READER_TTS, SharedTtsEngine.LOCAL)
        assertEquals(listOf("stopAudiobook", "stopListen"), calls.log)
    }

    @Test
    fun readerReadAloudLeavesListenAloneWhenItHasNoSession() {
        val calls = Calls()
        calls.arbiter().onTtsSessionActivated(SharedListeningSurface.READER_TTS, SharedTtsEngine.CLOUD)
        assertEquals(listOf("stopAudiobook"), calls.log)
    }

    @Test
    fun readerReadAloudNeverStopsTheEngineItJustClaimed() {
        // The regression, stated directly: whichever engine the reader claims, it is never the one
        // stopped by that same call.
        for (listenOn in listOf(SharedTtsEngine.LOCAL, SharedTtsEngine.CLOUD, null)) {
            for (readerTakes in listOf(SharedTtsEngine.LOCAL, SharedTtsEngine.CLOUD)) {
                val calls = Calls().apply { listenEngine = listenOn }
                val arbiter = calls.arbiter()
                arbiter.onTtsSessionActivated(SharedListeningSurface.READER_TTS, readerTakes)
                if (listenOn == readerTakes && listenOn != null) {
                    assertEquals(
                        listOf("stopAudiobook", "releaseListen"),
                        calls.log,
                        "reader took $readerTakes while Listen held $listenOn",
                    )
                }
            }
        }
    }

    @Test
    fun listensOwnSessionIsNotTreatedAsAHandoff() {
        // Listen drives the same cloud engine as the reader, so "the cloud engine is playing"
        // fires for Listen's own session too. Releasing or stopping there makes Listen cancel
        // itself the instant it starts — the exact shape of the cloud bug.
        val calls = Calls().apply { listenEngine = SharedTtsEngine.CLOUD }
        calls.arbiter().onTtsSessionActivated(SharedListeningSurface.LISTEN_TTS, SharedTtsEngine.CLOUD)
        assertEquals(emptyList(), calls.log)
    }

    @Test
    fun anUntaggedSessionIsTreatedAsTheReaders() {
        // Untagged means the reader's own session; it must still yield Listen normally.
        val calls = Calls().apply { listenEngine = SharedTtsEngine.LOCAL }
        calls.arbiter().onTtsSessionActivated(null, SharedTtsEngine.LOCAL)
        assertEquals(listOf("stopAudiobook", "releaseListen"), calls.log)
    }

    @Test
    fun surfaceTagResolvesToItsOwner() {
        assertEquals(
            SharedListeningSurface.LISTEN_TTS,
            sharedListeningSurfaceForTag(SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK),
        )
        assertEquals(
            SharedListeningSurface.READER_TTS,
            sharedListeningSurfaceForTag(SHARED_TTS_PLAYBACK_SOURCE_READER),
        )
        assertEquals(null, sharedListeningSurfaceForTag(null))
        // The two tags must never collide, or ownership checks invert silently.
        assertFalse(
            sharedListeningSurfaceForTag(SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK) ==
                sharedListeningSurfaceForTag(SHARED_TTS_PLAYBACK_SOURCE_READER),
        )
    }

    @Test
    fun listenStartingStopsTheAudiobookAndBothReaderEngines() {
        val calls = Calls().apply { listenEngine = SharedTtsEngine.LOCAL }
        calls.arbiter().onListenSessionStarting()
        // Runs before Listen claims anything, so these belong to the outgoing session.
        assertEquals(
            listOf("stopAudiobook", "stopReaderLocal", "stopReaderCloud"),
            calls.log,
        )
    }

    @Test
    fun audiobookStartingStopsEveryTtsSurface() {
        val calls = Calls().apply { listenEngine = SharedTtsEngine.LOCAL }
        calls.arbiter().onAudiobookStarting()
        assertEquals(
            listOf("stopListen", "stopReaderLocal", "stopReaderCloud"),
            calls.log,
        )
    }

    @Test
    fun listenStartingNeverStopsListen() {
        val calls = Calls().apply { listenEngine = SharedTtsEngine.CLOUD }
        calls.arbiter().onListenSessionStarting()
        assertEquals(false, calls.log.contains("stopListen"))
        assertEquals(false, calls.log.contains("releaseListen"))
    }
}