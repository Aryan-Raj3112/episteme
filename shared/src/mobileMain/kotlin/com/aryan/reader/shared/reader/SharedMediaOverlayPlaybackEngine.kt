package com.aryan.reader.shared.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The engine both platforms implement, and the composable that produces one.
 *
 * Deliberately narrow. Everything sequencing-related is a pure function in this package, so the
 * engines only have to move audio and report where they are — which is the part that genuinely
 * differs (ExoPlayer clips per `MediaItem`; `AVPlayer` seeks and watches the clock). A wider
 * interface would put sequencing decisions back where they can drift.
 */
interface SharedMediaOverlayPlayback {
    val state: StateFlow<SharedMediaOverlayPlaybackState>

    /**
     * Loads a chapter and optionally begins playing.
     *
     * Replaces any current session. [SharedMediaOverlayPlaybackRequest.clips] must already be the
     * filtered playable list — build it with `sharedMediaOverlayPlaybackPlan` — because an engine
     * that filtered again would filter differently on the two platforms.
     */
    fun play(request: SharedMediaOverlayPlaybackRequest)

    fun pause()

    fun resume()

    /** The previous clip. A no-op at the first clip. */
    fun skipPrevious()

    /** The next clip. Ends playback at the last clip rather than wrapping. */
    fun skipNext()

    /** Jumps to a clip, starting from the loaded chapter's own indices. */
    fun seekToClip(clipIndex: Int)

    /** Restarts the active clip from its beginning. */
    fun restartClip()

    fun setSpeed(speed: Float)

    /** Stops playback and drops the loaded chapter, but keeps the engine alive. */
    fun stop()

    /** Stops and releases platform resources. The instance is unusable afterwards. */
    fun release()
}

/** Everything an engine needs to play one chapter's narration. */
data class SharedMediaOverlayPlaybackRequest(
    val sourceId: String,
    val bookTitle: String,
    val spineItemIndex: Int,
    /**
     * Playable clips in playback order, from `sharedMediaOverlayPlaybackPlan`. Indices are the
     * *source* document indices, so a UI can map a highlighted fragment back to it.
     */
    val clips: List<SharedMediaOverlayClip>,
    /** Where within [clips] to start. Coerced into range. */
    val startPlaybackIndex: Int = 0,
    val playWhenReady: Boolean = true,
    val narrator: String? = null,
    /** The book's whole runtime, when known, for the player's total-time readout. */
    val totalDurationMs: Long? = null,
)

/**
 * A base engine holding everything that is genuinely platform-free: the loaded request, the plan
 * arithmetic, and the state transitions both platforms must agree on.
 *
 * Subclasses own the audio and call [publish] as it moves. Keeping the shared bookkeeping here means
 * "what does skip-next mean at the last clip" is answered once rather than twice.
 */
abstract class SharedMediaOverlayPlaybackBase : SharedMediaOverlayPlayback {

    protected val mutableState = MutableStateFlow(SharedMediaOverlayPlaybackState())

    final override val state: StateFlow<SharedMediaOverlayPlaybackState> = mutableState.asStateFlow()

    protected var request: SharedMediaOverlayPlaybackRequest? = null
        private set

    protected var released: Boolean = false
        private set

    /** The loaded clips, or an empty list before anything is loaded. */
    protected val clips: List<SharedMediaOverlayClip> get() = request?.clips.orEmpty()

    protected val currentClip: SharedMediaOverlayClip? get() = clips.getOrNull(mutableState.value.clipIndex)

    /** The play index [skipNext] should move to, or null when that means "end playback". */
    protected fun nextPlaybackIndex(from: Int): Int? = (from + 1).takeIf { it in clips.indices }

    /** The play index [skipPrevious] should move to, or null when already at the first clip. */
    protected fun previousPlaybackIndex(from: Int): Int? =
        (from - 1).takeIf { it >= 0 && it < clips.size }

    /**
     * Moves to a play index, or ends playback when [target] is null.
     *
     * Ending rather than clamping is deliberate: a "next" at the last clip should stop, and a
     * "previous" at the first should do nothing rather than restart. Clamping would turn one of them
     * into the other.
     */
    protected fun advance(target: Int?) {
        val current = request
        if (current == null) return
        if (target == null) {
            onPlaybackFinished()
            return
        }
        onClipChanged(target)
    }

    /** The engine's cue to stop and clear. */
    protected abstract fun onPlaybackFinished()

    /** The engine's cue to start [playbackIndex], reporting progress as it goes. */
    protected abstract fun onClipChanged(playbackIndex: Int)

    protected abstract fun onPauseRequested()

    protected abstract fun onResumeRequested()

    protected abstract fun onRestartRequested()

    protected abstract fun onSpeedChanged(speed: Float)

    protected abstract fun onStopRequested()

    /**
     * Publishes state. Every platform transition goes through here so the two can never diverge on
     * what "playing" or "finished" mean.
     */
    protected fun publish(transform: (SharedMediaOverlayPlaybackState) -> SharedMediaOverlayPlaybackState) {
        mutableState.value = transform(mutableState.value)
    }

    override fun pause() {
        if (released || request == null) return
        publish { it.copy(isPlaying = false, isLoading = false) }
        onPauseRequested()
    }

    override fun resume() {
        if (released || request == null) return
        publish { it.copy(isPlaying = true, error = null) }
        onResumeRequested()
    }

    override fun skipNext() = advance(nextPlaybackIndex(mutableState.value.clipIndex))

    override fun skipPrevious() = advance(previousPlaybackIndex(mutableState.value.clipIndex))

    override fun restartClip() {
        if (released || request == null) return
        onRestartRequested()
    }

    override fun setSpeed(speed: Float) {
        val resolved = sharedMediaOverlaySpeed(speed)
        publish { it.copy(speed = resolved) }
        onSpeedChanged(resolved)
    }

    override fun stop() {
        if (released) return
        onStopRequested()
        request = null
        mutableState.value = SharedMediaOverlayPlaybackState(speed = mutableState.value.speed)
    }

    final override fun release() {
        if (released) return
        stop()
        released = true
    }

    /**
     * Adopts a new request and publishes the opening state.
     *
     * Returns the play index to start at, or null when there is nothing to play — in which case the
     * session is dropped rather than loaded empty, so a chapter with no usable audio does not leave
     * a player on screen with zero progress.
     */
    protected fun adopt(request: SharedMediaOverlayPlaybackRequest): Int? {
        if (released) return null
        if (request.clips.isEmpty()) {
            this.request = null
            mutableState.value = SharedMediaOverlayPlaybackState(speed = mutableState.value.speed)
            return null
        }
        this.request = request
        val startIndex = request.startPlaybackIndex.coerceIn(0, request.clips.lastIndex)
        val first = request.clips[startIndex]
        mutableState.value = SharedMediaOverlayPlaybackState(
            isPlaying = false,
            isLoading = request.playWhenReady,
            spineItemIndex = request.spineItemIndex,
            clipIndex = startIndex,
            positionMs = first.clipBeginMs,
            clipStartMs = first.clipBeginMs,
            clipEndMs = first.clipEndMs,
            speed = mutableState.value.speed,
            bookTitle = request.bookTitle,
            narrator = request.narrator,
            totalDurationMs = request.totalDurationMs,
            error = null
        )
        return startIndex
    }

    /** Publishes the state for a clip the engine has moved to. */
    protected fun publishClip(clip: SharedMediaOverlayClip, clipIndex: Int, isPlaying: Boolean) {
        publish {
            it.copy(
                isPlaying = isPlaying,
                isLoading = false,
                clipIndex = clipIndex,
                positionMs = clip.clipBeginMs,
                clipStartMs = clip.clipBeginMs,
                clipEndMs = clip.clipEndMs
            )
        }
    }

    /** Publishes the state for a finished chapter. */
    protected fun publishFinished() {
        onStopRequested()
        val speed = mutableState.value.speed
        request = null
        mutableState.value = SharedMediaOverlayPlaybackState(speed = speed)
    }
}