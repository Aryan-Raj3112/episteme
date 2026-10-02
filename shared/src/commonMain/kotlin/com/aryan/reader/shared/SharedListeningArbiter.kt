package com.aryan.reader.shared

import com.aryan.reader.shared.SharedListeningSurface
import com.aryan.reader.shared.SharedListeningYield
import com.aryan.reader.shared.SharedTtsEngine
import com.aryan.reader.shared.sharedListeningYield

/**
 * The single place iOS arbitrates between the surfaces that can produce audio.
 *
 * Every host used to decide this by hand, and got it wrong the same way three times: it stopped
 * the *winner* instead of the loser. Opening a book during Listen playback killed the audiobook
 * session; a Listen cloud session cancelled itself the instant it began; a reader cloud session
 * stopped Listen. Each was a `stop()` call aimed at the engine the winner had just started.
 *
 * The decision now comes from [sharedListeningYield], so those outcomes cannot be expressed.
 * Callers state intent — "the reader's read-aloud is now active" — and never say which engine to
 * stop.
 *
 * Android benchmark: `sharedListeningHandoff`, which encodes the same single-active-listening-
 * source policy for audiobook versus TTS. Android needs no TTS-versus-TTS rule because both
 * surfaces drive one engine there; iOS has two engines, which is why this file exists.
 *
 * Deliberately platform-free and takes only lambdas, so the wiring — which is where all three
 * bugs lived — is host-testable. Tests record the calls and assert no surface ever stops itself.
 */
class SharedListeningArbiter(
    /** Engine holding Listen's current session, or null when it has none. */
    private val listenEngine: () -> SharedTtsEngine?,
    private val stopAudiobook: () -> Unit,
    /** Stops Listen's session, including whichever engine it is using. */
    private val stopListen: () -> Unit,
    /**
     * Drops Listen's session bookkeeping without touching an engine.
     *
     * Used when the winner already speaks on the engine Listen was using, so Listen must simply
     * let go. Stopping here would silence the winner.
     */
    private val releaseListen: () -> Unit,
    private val stopReaderLocal: () -> Unit,
    private val stopReaderCloud: () -> Unit,
) {

    /**
     * A TTS session became active on [engine], owned by [owner].
     *
     * One entry point for every TTS handoff, because the caller cannot be trusted to distinguish
     * "a reader session started" from "this is the caller's own session": the reader and Listen
     * drive the same two engine objects, so a Listen cloud session is byte-for-byte
     * indistinguishable from a reader cloud session until the surface tag is read. Handing the
     * owner in lets the arbiter drop that case, which is the bug this replaced.
     *
     * [owner] null means an untagged session, which belongs to the reader.
     */
    fun onTtsSessionActivated(owner: SharedListeningSurface?, engine: SharedTtsEngine) {
        val winner = owner ?: SharedListeningSurface.READER_TTS
        // Listen's own session is not a handoff. Without this, Listen releasing or stopping
        // itself is what a cloud start used to do.
        if (winner == SharedListeningSurface.LISTEN_TTS) return
        stopAudiobook()
        yieldListenTo(winner, engine)
    }

    /**
     * Audiobook Listen is about to start a session.
     *
     * Stops the audiobook and both reader engines. Android stops only the audiobook here
     * (`sharedListeningHandoff(GENERATED_BOOK_TTS)`) because Listen and the reader are one engine
     * there and the start replaces the previous session for free. iOS has two engines, so the
     * previous ones have to be cleared explicitly.
     *
     * A plain stop is correct *here* and wrong everywhere else, and the difference is ordering:
     * this runs before Listen claims anything, so the engines being stopped belong to the
     * outgoing session. The three bugs this file replaced all stopped an engine *after* the
     * winner had already started on it.
     */
    fun onListenSessionStarting() {
        stopAudiobook()
        stopReaderLocal()
        stopReaderCloud()
    }

    /**
     * A narrated audiobook started, from an explicit user action.
     *
     * The one path that genuinely stops TTS: the audiobook engine is separate, so the previous
     * TTS session is still live and has to be silenced rather than released.
     */
    fun onAudiobookStarting() {
        stopListen()
        stopReaderLocal()
        stopReaderCloud()
    }

    /** Yields Listen's session to the winner, by the shared rule. */
    private fun yieldListenTo(winner: SharedListeningSurface, winnerEngine: SharedTtsEngine) {
        when (
            sharedListeningYield(
                winner = winner,
                winnerEngine = winnerEngine,
                loser = SharedListeningSurface.LISTEN_TTS,
                loserEngine = listenEngine(),
            )
        ) {
            SharedListeningYield.NONE -> Unit
            SharedListeningYield.RELEASE -> releaseListen()
            SharedListeningYield.STOP -> stopListen()
        }
    }
}