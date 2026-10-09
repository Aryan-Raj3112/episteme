package com.aryan.reader.shared.reader

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ios.ReaderIosBridge
import com.aryan.reader.shared.ios.resolveIosEpubSourcePath
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.dataWithLength
import platform.posix.memcpy

/**
 * iOS media overlay playback.
 *
 * **The mechanism is Android's, as far as AVFoundation allows.** Android hands ExoPlayer one
 * `MediaItem` per clip with a `ClippingConfiguration`, and `onMediaItemTransition` *is* the
 * active-fragment signal — no polling, no arithmetic, nothing to get wrong at a boundary. iOS builds
 * one `AVPlayerItem` per clip over a shared asset, carrying the clip's end as
 * `forwardPlaybackEndTime`, and `AVPlayerItemDidPlayToEndTime` is that same transition.
 *
 * One asymmetry is real. `AVPlayerItem` declares `forwardPlaybackEndTime` and
 * `reversePlaybackEndTime` and **no start counterpart**, so a clip's beginning cannot be a property
 * the way ExoPlayer's `setStartPositionMs` is; it is a seek. That seek must be issued while the
 * player is paused, and the first iOS design got that wrong in the way that is easiest to get wrong:
 * it sought while still playing, which does not stop playback, so at every boundary the reader heard
 * the opening of the next line and then heard it again once the seek landed. A word doubled on every
 * line. Pausing before the seek fixes it, and the end of a clip no longer needs polling at all.
 *
 * Every decision about *which* clip comes next still lives in
 * [SharedMediaOverlayPlaybackPlan] and [SharedMediaOverlaySession], both platform-free, and this
 * class only moves audio and reports where it is. A drift between platforms would be a highlight
 * that does not match the voice, which is the one bug this feature exists to avoid.
 *
 * A sibling of the audiobook's `IosAudiobookPlayback`, deliberately not a reuse of it. That one
 * persists a position to the database, keys on a book id and seeks by absolute time; all correct for
 * a chapter-at-a-time audiobook and all wrong here, where the unit of playback is a sentence and the
 * position must be exact.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSharedMediaOverlayPlayback(
    private val bridge: ReaderIosBridge
) : SharedMediaOverlayPlaybackBase(), SharedMediaOverlayEngine {

    /**
     * The archive the recording is streamed from, while the reader that owns it is open.
     *
     * Null between books, and that is the whole lifetime contract: the bytes belong to a reader
     * screen, and a resource loader still answering after the screen is gone would serve a book
     * the reader has already left.
     */
    private var archive: SharedMediaOverlayArchiveReader? = null

    /**
     * Points the engine at the open book's archive.
     *
     * The path resolution is here rather than at the call site because it is an iOS concern: a
     * linked-folder book stores a provider ref, not a filesystem path, and resolving it is what makes
     * such a book narratable at all.
     *
     * Not throwing when the archive cannot be borrowed is deliberate. A book whose archive is not
     * resident has no narration, which `RS §9` explicitly allows a reader to report by simply not
     * offering the feature — and the alternative, a narration button that fails when pressed, is
     * worse than no button.
     *
     * The bridge callbacks are reinstalled here rather than once in `init` so that they close over
     * *this* engine's current archive. That makes a null archive the whole of "detached": there is no
     * callback left pointing at a reader that has gone away, and a loader still asking gets a null
     * range rather than the wrong recording's bytes.
     */
    override fun attachArchive(book: BookItem?) {
        val path = book?.path?.resolveIosEpubSourcePath()
        archive = path?.let { SharedMediaOverlayArchiveReader.at(it) }
        bridge.setMediaOverlayAudioSource(
            readEntryRange = { entryPath, offset, length -> readEntryRange(entryPath, offset, length) },
            entryLength = { entryPath -> archive?.entryLengthOrNull(entryPath) ?: 0L }
        )
    }

    private fun readEntryRange(entryPath: String, offset: Long, length: Long): NSData? {
        val bytes = archive?.readEntryRange(entryPath, offset, length) ?: return null
        if (bytes.isEmpty()) {
            // A zero-length NSData is how a resource loader is told "this is the end of the resource",
            // which is the honest answer for an entry the archive could not produce. `null` would be
            // read as "this URI is not ours" and reported as a missing file.
            return NSData()
        }
        // A mutable buffer then a copy, rather than `NSData(bytes:length:)`: the pinned pointer has to
        // outlive the call that reads it, and handing the pointer straight to the constructor is the
        // one spelling that would let it dangle.
        val data = NSMutableData.dataWithLength(bytes.size.toULong()) ?: return NSData()
        bytes.usePinned { pinned ->
            memcpy(data.mutableBytes, pinned.addressOf(0), bytes.size.toULong())
        }
        return data
    }

    private fun clipAtSourceIndex(clipIndex: Int): SharedMediaOverlayClip? =
        clips.firstOrNull { it.clipIndex == clipIndex }

    final override fun play(request: SharedMediaOverlayPlaybackRequest) {
        val startIndex = adopt(request) ?: return
        publish { it.copy(isLoading = true) }
        bridge.mediaOverlayPlayHandler?.invoke(
            ReaderIosBridge.SharedIosMediaOverlayPlaybackSetup(
                spineItemIndex = request.spineItemIndex,
                bookTitle = request.bookTitle,
                narrator = request.narrator,
                startClipIndex = startIndex,
                playWhenReady = request.playWhenReady,
                clips = request.clips
            )
        )
    }

    /**
     * A native transition, translated into shared state.
     *
     * Two translations happen here, and both are load-bearing:
     *
     * - **Source clip index to play index.** Swift reports the index the clip carries in the
     *   *document*, because that is what the plan it was handed contains, while
     *   [SharedMediaOverlayPlaybackState.clipIndex] is an index into the *loaded* list — and the plan
     *   drops clips (a TTS `par`, one with no audio), so the two are not the same number. Passing
     *   Swift's index straight through would freeze the state at the first dropped clip, which reads
     *   as the highlight sticking to one line while the voice moves on.
     * - **Bounds from the plan, not from Swift.** `clipProgress` is derived from the clip's own
     *   `clipBegin` and `clipEnd`, and the plan is the only copy that cannot disagree with itself.
     */
    fun onNativeUpdate(update: ReaderIosBridge.SharedIosMediaOverlayUpdate) {
        val clip = clipAtSourceIndex(update.clipIndex)
        // Falls back to the current play index when the reported clip is not in the loaded list,
        // rather than publishing a wrong one: an unrecognised index means the native side and this
        // plan have diverged, and holding the last known position is closer to the truth than
        // jumping to the top of the chapter.
        val playIndex = playbackIndexOfSourceClip(update.clipIndex).takeIf { it >= 0 }
            ?: mutableState.value.clipIndex
        publish { state ->
            state.copy(
                spineItemIndex = state.spineItemIndex ?: update.spineItemIndex.takeIf { it >= 0 },
                clipIndex = playIndex,
                isPlaying = update.isPlaying,
                isLoading = update.isLoading,
                positionMs = update.positionMs,
                clipStartMs = clip?.clipBeginMs ?: state.clipStartMs,
                clipEndMs = clip?.clipEndMs ?: state.clipEndMs,
                error = update.error
            )
        }
    }

    /** The loaded chapter's clips played out. Carries on only if the session says there is more. */
    fun onNativeChapterFinished(spineItemIndex: Int) {
        publishFinished()
    }

    /** The native player tore itself down; the state must not keep claiming a book is loaded. */
    fun onNativeSessionEnded() {
        publish { it.copy(spineItemIndex = null, isPlaying = false, isLoading = false) }
    }

    // --- engine hooks -------------------------------------------------------------------------

    override fun onClipChanged(playbackIndex: Int) {
        val clip = clips.getOrNull(playbackIndex) ?: return
        publishClip(clip, playbackIndex, isPlaying = state.value.isPlaying)
        bridge.mediaOverlaySeekToClipHandler?.invoke(state.value.spineItemIndex ?: 0, clip.clipIndex)
    }

    override fun onPlaybackFinished() = publishFinished()

    override fun onPauseRequested() {
        bridge.mediaOverlayPauseHandler?.invoke()
    }

    override fun onResumeRequested() {
        bridge.mediaOverlayResumeHandler?.invoke()
    }

    override fun onRestartRequested() {
        bridge.mediaOverlayRestartClipHandler?.invoke()
    }

    override fun onSpeedChanged(speed: Float) {
        bridge.mediaOverlaySpeedHandler?.invoke(speed)
    }

    override fun onStopRequested() {
        bridge.mediaOverlayStopHandler?.invoke()
    }

    override fun seekToClip(clipIndex: Int) {
        val playbackIndex = playbackIndexOfSourceClip(clipIndex)
        if (playbackIndex >= 0) advance(playbackIndex)
    }

    /**
     * Source clip index -> position in the loaded clip list.
     *
     * Delegates to the shared rule rather than keeping a second copy, because the whole point of
     * hoisting it is that this engine holds a clip *list* where Android holds a plan, and the two must
     * not answer the same question differently.
     */
    private fun playbackIndexOfSourceClip(clipIndex: Int): Int =
        clips.playbackIndexOfSourceClip(clipIndex)
}