package com.aryan.reader.shared

/**
 * Shared decision for "has this cloud-TTS chunk actually finished playing?".
 *
 * Android drives chunk transitions off the player's own callback
 * (`TtsPlaybackManager.onMediaItemTransition`) and *also* off the observed
 * playback position in `trackWordByWord`, because the callback is best-effort
 * across format/decoder changes. The iOS engine previously relied only on
 * `AVAudioPlayer.audioPlayerDidFinishPlaying`: when that delegate was not
 * delivered the playback loop stayed suspended on its completion deferred, so
 * the reader went silent after one chunk until the user pressed skip. iOS
 * therefore applies the same position-based end-of-stream rule Android already
 * uses, so a lost completion callback costs nothing on either platform.
 *
 * A mid-stream freeze (route drop, stall) is deliberately **not** completion:
 * skipping a chunk the user never heard would be worse than waiting, so that
 * case stays with the pause/resume and interruption paths.
 *
 * Lives in commonMain so the rule is unit-testable and so Android can adopt the
 * identical predicate instead of a second, drifting copy.
 */
object CloudTtsPlaybackMonitorPolicy {
    /** Polling cadence for the end-of-stream monitor. */
    const val MONITOR_MILLIS = 250L

    /**
     * How close to [position]'s stream end counts as finished. MP3 padding and
     * encoder delay keep a player's reported position a hair short of the
     * nominal duration at the tail, so a small cushion avoids advancing before
     * the last fraction of a second is audible.
     */
    const val END_TOLERANCE_SECONDS = 0.25

    /** Below this, a sample is treated as "position did not move". */
    const val MOVEMENT_EPSILON_SECONDS = 0.01

    /** Consecutive frozen samples before a mid-stream stall is reported. */
    const val STALL_SAMPLES = 2

    enum class Action {
        /** Position reached the end of the stream: advance to the next chunk. */
        FINISHED,

        /** Playing but position did not move: report, do not advance. */
        STALLED,

        /** Still playing and moving: nothing to do. */
        PLAYING,

        /** Paused / not playing / no usable duration: nothing to do. */
        IDLE,
    }

    /**
     * True once [position] has reached the end of a [duration]-second stream,
     * within [END_TOLERANCE_SECONDS].
     *
     * Evaluated before any "is it playing" test on purpose: a player that
     * finished on its own already reports `isPlaying == false`, so gating end
     * detection on that flag would re-create the hang this policy exists to
     * prevent.
     */
    fun hasReachedEnd(position: Double, duration: Double): Boolean =
        duration > 0.0 && position >= duration - END_TOLERANCE_SECONDS

    /**
     * @param position current playback position in seconds.
     * @param duration total stream duration in seconds; non-positive means unknown.
     * @param playing whether the player reports itself as playing.
     * @param stalledSamples consecutive frozen samples already seen.
     */
    fun evaluate(
        position: Double,
        duration: Double,
        playing: Boolean,
        stalledSamples: Int,
    ): Action {
        if (hasReachedEnd(position, duration)) return Action.FINISHED
        if (!playing) return Action.IDLE
        if (stalledSamples >= STALL_SAMPLES) return Action.STALLED
        return Action.PLAYING
    }

    /** Next frozen-sample counter for a position sample, or 0 once it moves. */
    fun nextStalledSamples(previous: Int, position: Double, lastPosition: Double): Int =
        if (position <= lastPosition + MOVEMENT_EPSILON_SECONDS) previous + 1 else 0
}
