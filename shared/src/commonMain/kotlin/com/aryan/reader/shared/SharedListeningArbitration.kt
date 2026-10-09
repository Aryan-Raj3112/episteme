package com.aryan.reader.shared

/**
 * The surfaces in this app that can produce audio, and how they yield to each other.
 *
 * Android enforces a single-active-listening-source policy ([sharedListeningHandoff]): a narrated
 * audiobook and generated TTS compete, so whichever starts stops the other. It has nothing to say
 * about TTS versus TTS, because on Android both surfaces drive the *same* engine — a start
 * replaces the session and nothing needs stopping.
 *
 * iOS has two engine objects, so "a start replaces the session" is no longer automatic, and every
 * host that arbitrated by hand got it wrong in the same way: it called `stop()` on the engine the
 * *winner* had just started, killing the session it was meant to yield to. Three separate bugs
 * came from that — opening a book during Listen playback, a Listen cloud session cancelling itself,
 * and a reader cloud session stopping Listen.
 *
 * This file makes the rule a pure function so call sites cannot express the bug. The invariant:
 *
 *  - never yield to yourself;
 *  - surfaces sharing an engine RELEASE (drop their session, leave the engine alone);
 *  - surfaces on different engines, and the audiobook, STOP.
 */

/** A source of audio in the app. */
enum class SharedListeningSurface {
    /** An imported/narrated audiobook, played by the audiobook player. */
    AUDIOBOOK,

    /** In-book reader read-aloud. */
    READER_TTS,

    /** Audiobook "Listen" TTS. */
    LISTEN_TTS,

    /**
     * EPUB media overlay playback: a publisher's pre-recorded narration synchronized to the text.
     *
     * Carries real audio on its own engine, exactly like [AUDIOBOOK], so it always excludes in both
     * directions rather than participating in the engine-sharing RELEASE rule below. Two engines
     * speaking at once is the failure this file exists to prevent.
     */
    MEDIA_OVERLAY,
}

/**
 * Surfaces that own real audio on an engine nobody else shares.
 *
 * They STOP-yield like [AUDIOBOOK] for the same reason: a start on a different engine cannot
 * silence them by accident, so the loser has to be stopped explicitly.
 */
private val SharedListeningSurface.ownsRealAudio: Boolean
    get() = this == SharedListeningSurface.AUDIOBOOK || this == SharedListeningSurface.MEDIA_OVERLAY

/** Which speech engine a TTS surface is speaking through. */
enum class SharedTtsEngine {
    /** On-device speech synthesis. */
    LOCAL,

    /** Cloud synthesis. */
    CLOUD,
}

/** What a losing surface must do to give up the audio output. */
enum class SharedListeningYield {
    /** Nothing to do: no live session, or the surface is the one claiming the output. */
    NONE,

    /** Stop the audio; it lives on an engine the winner is not using. */
    STOP,

    /**
     * Drop this surface's session without stopping the engine.
     *
     * The winner is already speaking on this same engine, so stopping it would silence the
     * winner. Releasing clears the loser's own bookkeeping and lets the engine's new session
     * stand.
     */
    RELEASE,
}

/**
 * Decides what [loser] must do now that [winner] has claimed the audio output.
 *
 * @param winnerEngine engine [winner] is speaking on, or null for a non-TTS surface.
 * @param loserEngine engine [loser] currently holds a session on, or null if it has no session.
 */
fun sharedListeningYield(
    winner: SharedListeningSurface,
    winnerEngine: SharedTtsEngine?,
    loser: SharedListeningSurface,
    loserEngine: SharedTtsEngine? = null,
): SharedListeningYield = when {
    // Yielding to yourself is the bug that took three separate fixes: the caller stops the very
    // session it just started.
    winner == loser -> SharedListeningYield.NONE

    // Android's single-active-listening-source policy: audiobook, media overlay and TTS always
    // exclude. The overlay belongs here rather than in the engine-sharing branch below because its
    // audio is a publisher's recording on its own engine, not a synthesized session.
    winner.ownsRealAudio || loser.ownsRealAudio -> SharedListeningYield.STOP

    loserEngine == null -> SharedListeningYield.NONE

    // One engine: the winner's session replaced the loser's, so only the loser's bookkeeping
    // needs clearing.
    loserEngine == winnerEngine -> SharedListeningYield.RELEASE

    // Different engines: nothing about the winner touched this surface, so its audio has to be
    // stopped explicitly or both surfaces speak at once.
    else -> SharedListeningYield.STOP
}

/** Every surface except [winner], for applying a decision across all competing surfaces. */
fun sharedListeningSurfacesOtherThan(
    winner: SharedListeningSurface,
): List<SharedListeningSurface> = SharedListeningSurface.entries.filter { it != winner }

/**
 * Which surface a TTS engine's current session belongs to, from its surface tag.
 *
 * Needed because "the cloud engine is playing" does not imply "the reader started it": Listen
 * drives the same cloud engine, so a Listen cloud session looks identical to a reader one until
 * the tag is read. A handoff that ignores this releases the winner's own session.
 *
 * Null for an untagged session, which by [isReaderOwnedTtsSession] belongs to the reader.
 */
fun sharedListeningSurfaceForTag(playbackSource: String?): SharedListeningSurface? =
    when (playbackSource) {
        SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK -> SharedListeningSurface.LISTEN_TTS
        SHARED_TTS_PLAYBACK_SOURCE_READER -> SharedListeningSurface.READER_TTS
        else -> null
    }
