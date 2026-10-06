package com.aryan.reader.mediaoverlay

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aryan.reader.shared.reader.SharedMediaOverlayClip
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackBase
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackRequest
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android media overlay playback.
 *
 * **ExoPlayer is the SMIL sequencer, not just the audio.** Every clip becomes its own `MediaItem`
 * with a clipping configuration, so `onMediaItemTransition` *is* the active-fragment signal. The
 * alternative — one item per audio file, poll the position, work out which `par` you are inside —
 * is what a TTS engine does and it drifts, because the answer depends on a subtraction landing on
 * the right side of a boundary. Handing the boundaries to the player removes the arithmetic
 * entirely, and `seekTo(index, 0)` becomes the whole of "skip to clip".
 *
 * A sibling of `AudiobookPlaybackService`, deliberately not a reuse of it. That service persists to
 * `audiobookDao`, keys on a book id and applies a 10-second rewind on resume — all correct for a
 * chapter-at-a-time audiobook and all wrong here, where the unit of playback is a sentence and the
 * position must be exact.
 */
@OptIn(UnstableApi::class)
class AndroidSharedMediaOverlayPlayback(
    private val context: Context
) : SharedMediaOverlayPlaybackBase() {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingRequest: SharedMediaOverlayPlaybackRequest? = null
    /** The book currently attached to the service, so a reconnect does not re-attach. */
    private var attachedFile: File? = null
    private var positionPollJob: Job? = null
    /** Suppresses the position poll while a programmatic seek is in flight. */
    private var suppressPoll = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            // This is the whole design: the player telling us which fragment is active, rather than
            // us inferring it from a clock.
            val index = controller?.currentMediaItemIndex ?: return
            if (index < 0) return
            currentClip?.let { publishClip(it, index, controller?.isPlaying == true) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish { it.copy(isPlaying = isPlaying, isLoading = false) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> publishFinished()
                Player.STATE_READY -> publish { it.copy(isLoading = false) }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            publish {
                it.copy(
                    isPlaying = false,
                    isLoading = false,
                    error = error.errorCodeName
                )
            }
        }
    }

    /**
     * The archive the service should stream from.
     *
     * A path rather than a reader: the service owns the `ZipFile` so it can close it, and sending a
     * handle across would leave two open for one file. Set before [play]; null means "the last book
     * this controller attached", which is what makes a reconnection mid-chapter keep working.
     */
    var bookFile: File? = null
        private set

    fun attachBook(file: File) {
        bookFile = file
    }

    final override fun play(request: SharedMediaOverlayPlaybackRequest) {
        pendingRequest = request
        val connected = controller
        if (connected != null) {
            attachBookThenLoad(connected, request)
            return
        }
        connect()
    }

    /**
     * Attaches the book, then loads. Ordering is the whole point: the player's data source factory
     * is rebuilt when the archive is attached, so loading first would build every clip's `MediaItem`
     * against the previous book's archive.
     *
     * A failed attach still loads, so the failure surfaces as the player's own error on the first
     * clip rather than as a silent play that never makes a sound.
     */
    private fun attachBookThenLoad(controller: MediaController, request: SharedMediaOverlayPlaybackRequest) {
        val file = bookFile
        if (file == null || attachedFile == file) {
            load(controller, request)
            return
        }
        attachedFile = file
        val args = Bundle().apply {
            putString(MediaOverlayPlaybackService.KEY_ATTACH_BOOK_PATH, file.absolutePath)
        }
        val pending = controller.sendCustomCommand(MediaOverlayPlaybackService.ATTACH_BOOK_COMMAND, args)
        pending.addListener({
            load(controller, request)
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun connect() {
        if (controller != null || future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, MediaOverlayPlaybackService::class.java))
        val building = MediaController.Builder(appContext, token).buildAsync()
        future = building
        building.addListener({
            runCatching { building.get() }
                .onSuccess { connected ->
                    if (future !== building) {
                        connected.release()
                        return@onSuccess
                    }
                    future = null
                    controller = connected
                    connected.addListener(listener)
                    pendingRequest?.let { load(connected, it) }
                    startPositionPoll()
                }
                .onFailure {
                    future = null
                    publish { state -> state.copy(isLoading = false, error = it.message ?: "Playback unavailable") }
                }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /**
     * Builds one `MediaItem` per clip and hands the whole playlist to the player at the start index.
     *
     * [ClippingConfiguration] is what turns one long mp3 into a sequence of fragments. Its end is set
     * to the clip's declared `clipEnd`, not the clamped one: the plan already dropped anything
     * unplayable, and ExoPlayer clamps against the real media duration itself, which is the same
     * clamp applied twice would only risk disagreeing with.
     */
    private fun load(controller: MediaController, request: SharedMediaOverlayPlaybackRequest) {
        val mediaItems = request.clips.mapIndexed { index, clip ->
            clip.toMediaItem(request, index)
        }
        adopt(request)?.let { resolvedStart ->
            publish { it.copy(isLoading = true) }
            suppressPoll = true
            runCatching {
                controller.setMediaItems(mediaItems, resolvedStart, 0L)
                controller.prepare()
                if (request.playWhenReady) controller.play() else controller.pause()
            }.onFailure {
                publish { state -> state.copy(isLoading = false, error = it.message) }
            }
            suppressPoll = false
        }
    }

    private fun SharedMediaOverlayClip.toMediaItem(
        request: SharedMediaOverlayPlaybackRequest,
        index: Int
    ): MediaItem {
        val audio = audioPath ?: return MediaItem.Builder().setMediaId("$index").build()
        val builder = MediaItem.Builder()
            .setMediaId("$index")
            // A zip entry, not a file. The extraction cache is keyed to book loading and would have
            // to be invalidated by a parser bump every time this feature changed, and it would copy
            // most of a 124 MB book for a reader that may never press play.
            .setUri(MediaOverlayUri.uriFor(audio))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(request.bookTitle)
                    .setArtist(request.narrator)
                    .setTrackNumber(index + 1)
                    .build()
            )
        val clipping = androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clipBeginMs)
            .apply { clipEndMs?.let { setEndPositionMs(it) } }
            .build()
        return builder.setClippingConfiguration(clipping).build()
    }

    /**
     * Polls the player's position because the shared state exposes a millisecond position.
     *
     * The *sequence* needs no polling — media item transitions drive it — but the scrubber and the
     * per-clip progress do, and a `MediaController` cannot push those. A short interval is enough
     * because a missed tick only costs a slightly stale readout, never a wrong fragment.
     */
    private fun startPositionPoll() {
        if (positionPollJob != null) return
        positionPollJob = scope.launch {
            while (isActive) {
                delay(250)
                if (suppressPoll) continue
                val controller = controller ?: continue
                val position = controller.currentPosition
                publish { it.copy(positionMs = position) }
            }
        }
    }

    // --- engine hooks -------------------------------------------------------------------------

    override fun onClipChanged(playbackIndex: Int) {
        val clip = clips.getOrNull(playbackIndex) ?: return
        publishClip(clip, playbackIndex, isPlaying = state.value.isPlaying)
        val controller = controller ?: return
        suppressPoll = true
        runCatching {
            controller.seekTo(playbackIndex, clip.clipBeginMs)
            if (state.value.isPlaying) controller.play() else controller.pause()
        }
        suppressPoll = false
    }

    override fun onPlaybackFinished() = publishFinished()

    override fun onPauseRequested() {
        controller?.pause()
    }

    override fun onResumeRequested() {
        controller?.play()
    }

    override fun onRestartRequested() {
        val clip = currentClip ?: return
        onClipChanged(state.value.clipIndex)
        publish { it.copy(positionMs = clip.clipBeginMs) }
    }

    override fun onSpeedChanged(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    override fun onStopRequested() {
        suppressPoll = true
        runCatching {
            controller?.stop()
            controller?.clearMediaItems()
        }
        suppressPoll = false
    }

    override fun seekToClip(clipIndex: Int) {
        val playbackIndex = playbackIndexOfSourceClip(clipIndex)
        if (playbackIndex >= 0) advance(playbackIndex)
    }

    /**
     * Source clip index -> position in the loaded clip list.
     *
     * The plan may have dropped clips, so a caller holding a document index cannot use it directly;
     * the mapping lives on the request rather than being recomputed per seek.
     */
    private fun playbackIndexOfSourceClip(clipIndex: Int): Int =
        clips.indexOfFirst { it.clipIndex == clipIndex }
}