/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.tts

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import timber.log.Timber
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aryan.reader.BuildConfig
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.tts.TtsPlaybackManager.TtsState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SETTINGS_PREFS_NAME = "epub_reader_settings"
private const val TTS_SPEAKER_KEY = "tts_speaker"

private fun saveSpeaker(context: Context, speakerId: String) {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(TTS_SPEAKER_KEY, speakerId) }
}

private fun loadSpeaker(context: Context): String {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(TTS_SPEAKER_KEY, DEFAULT_SPEAKER_ID) ?: DEFAULT_SPEAKER_ID
}

@OptIn(UnstableApi::class)
fun loadTtsMode(context: Context): TtsPlaybackManager.TtsMode {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val savedModeName = prefs.getString("tts_mode", TtsPlaybackManager.TtsMode.BASE.name)
        ?: TtsPlaybackManager.TtsMode.BASE.name

    val isCloudAllowed = BuildConfig.TTS_WORKER_URL.isNotBlank() || isByokCloudTtsAvailable(context)

    return if (isCloudAllowed) {
        try {
            TtsPlaybackManager.TtsMode.valueOf(savedModeName)
        } catch (_: Exception) {
            TtsPlaybackManager.TtsMode.BASE
        }
    } else {
        TtsPlaybackManager.TtsMode.BASE
    }
}

@UnstableApi
class TtsController(context: Context) : Player.Listener {
    
    private val context = context.applicationContext

    private val _ttsState = MutableStateFlow(TtsState())
    val ttsState = _ttsState.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pollingJob: Job? = null

    init {
        val initialSpeakerId = loadSpeaker(this.context)
        _ttsState.value = _ttsState.value.copy(speakerId = initialSpeakerId)
    }

    fun connect() {
        if (mediaController != null || controllerFuture != null) {
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
                "TtsController.connect skipped. hasController=${mediaController != null}, hasFuture=${controllerFuture != null}"
            )
            return
        }

        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("TtsController.connect building MediaController.")
        val sessionToken = SessionToken(context, ComponentName(context, TtsService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    if (future.isCancelled) return@addListener

                    val controller = future.get()

                    if (controllerFuture != future) {
                        Timber.d("MediaController connected after release. Releasing immediately.")
                        controller.release()
                        return@addListener
                    }

                    mediaController = controller
                    controllerFuture = null

                    mediaController?.addListener(this)
                    Timber.d("MediaController connected.")
                    Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
                        "MediaController connected. playbackState=${controller.playbackState}, isPlaying=${controller.isPlaying}, mediaItems=${controller.mediaItemCount}, customLayout=${controller.customLayout.size}"
                    )
                    updateStateFromController()
                    startPolling()
                } catch (e: Exception) {
                    Timber.w("Failed to connect MediaController: ${e.message}")
                    Timber.tag(TTS_NOTIFICATION_DIAG_TAG).e(e, "MediaController connection failed.")
                    if (controllerFuture == future) {
                        controllerFuture = null
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                updateStateFromController()
                delay(150)
            }
        }
    }

    fun start(
        chunks: List<com.aryan.reader.paginatedreader.TtsChunk>,
        bookTitle: String,
        chapterTitle: String?,
        coverImageUri: String?,
        chapterIndex: Int? = null,
        totalChapters: Int? = null,
        continueSession: Boolean = false,
        ttsMode: TtsPlaybackManager.TtsMode,
        playbackSource: String = "READER",
        authToken: String? = null
    ) {
        if (chunks.isEmpty()) {
            Timber.w("TtsController: start called with empty chunks!")
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("TtsController.start aborted because chunks is empty.")
            return
        }
        Timber.d("UI sending START command with mode: $ttsMode")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "TtsController.start. hasController=${mediaController != null}, chunks=${chunks.size}, continueSession=$continueSession, source=$playbackSource, mode=$ttsMode, book='${bookTitle.take(60)}', chapter='${chapterTitle.orEmpty().take(60)}', chapterIndex=$chapterIndex, totalChapters=$totalChapters"
        )

        val textList = ArrayList(chunks.map { it.text })
        val cfiList = ArrayList(chunks.map { it.sourceCfi })
        val offsetList = ArrayList(chunks.map { it.startOffsetInSource })

        val args = Bundle().apply {
            putStringArrayList(KEY_TEXT_CHUNKS, textList)
            putStringArrayList(KEY_SOURCE_CFIS, cfiList)
            putIntegerArrayList(KEY_START_OFFSETS, offsetList)
            putString(KEY_SPEAKER_ID, _ttsState.value.speakerId)
            putString(KEY_BOOK_TITLE, bookTitle)
            putString(KEY_CHAPTER_TITLE, chapterTitle)
            putString(KEY_COVER_IMAGE_URI, coverImageUri)
            chapterIndex?.let { putInt(KEY_CHAPTER_INDEX, it) }
            totalChapters?.let { putInt(KEY_TOTAL_CHAPTERS, it) }
            putBoolean(KEY_CONTINUE_SESSION, continueSession)
            putString(KEY_TTS_MODE, ttsMode.name)
            putString(KEY_PLAYBACK_SOURCE, playbackSource)
            putString(KEY_AUTH_TOKEN, authToken)
            putFloat("playback_speed", loadTtsSpeechRate(context))
            putFloat("playback_pitch", loadTtsPitch(context))
        }
        Timber.tag("TTS_CLOUD_DIAG").d("TtsController sending START. Mode: $ttsMode, Chunks: ${chunks.size}, Token present: ${!authToken.isNullOrBlank()}")
        val controller = mediaController
        if (controller == null) {
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).e("Cannot send START command because MediaController is null.")
        } else {
            val result = controller.sendCustomCommand(START_TTS_COMMAND, args)
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
                "START command sent. playbackState=${controller.playbackState}, isPlaying=${controller.isPlaying}, mediaItems=${controller.mediaItemCount}, resultDone=${result.isDone}"
            )
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun resume() {
        mediaController?.play()
    }

    fun stop() {
        Timber.d("UI sending STOP command.")
        mediaController?.sendCustomCommand(STOP_TTS_COMMAND, Bundle.EMPTY)
    }

    @Suppress("unused")
    fun changeSpeaker(speakerId: String) {
        Timber.d("UI sending CHANGE_SPEAKER command.")
        saveSpeaker(context, speakerId)
        _ttsState.value = _ttsState.value.copy(speakerId = speakerId)

        val args = Bundle().apply {
            putString(KEY_SPEAKER_ID, speakerId)
        }
        mediaController?.sendCustomCommand(CHANGE_SPEAKER_COMMAND, args)
    }

    @Suppress("unused")
    fun changeTtsMode(mode: String) {
        Timber.d("UI sending CHANGE_TTS_MODE command.")
        val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("tts_mode", mode) }
        _ttsState.value = _ttsState.value.copy(ttsMode = mode)

        val args = Bundle().apply {
            putString(KEY_TTS_MODE, mode)
        }
        mediaController?.sendCustomCommand(CHANGE_TTS_MODE_COMMAND, args)
    }

    fun flushPrefetch() {
        Timber.d("UI sending FLUSH_PREFETCH command.")
        mediaController?.sendCustomCommand(FLUSH_PREFETCH_COMMAND, Bundle.EMPTY)
    }

    fun sliceAndRetainPosition() {
        Timber.d("UI sending SLICE_CURRENT_AND_RELOAD command.")
        mediaController?.sendCustomCommand(SLICE_CURRENT_AND_RELOAD_COMMAND, Bundle.EMPTY)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "Controller onEvents. playbackState=${player.playbackState}, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, mediaItems=${player.mediaItemCount}, currentIndex=${player.currentMediaItemIndex}, events=$events"
        )
        updateStateFromController()
    }

    private fun updateStateFromController() {
        mediaController?.let { controller ->
            val customState = controller.customLayout.firstOrNull()?.extras ?: Bundle.EMPTY
            val currentMediaItem = controller.currentMediaItem
            val mediaItemExtras = currentMediaItem?.mediaMetadata?.extras
            val currentTextFromMediaItem = mediaItemExtras?.getString("ttsText")
                ?: currentMediaItem?.mediaMetadata?.subtitle?.toString()
            val isPlaybackActive = controller.isPlaying || controller.playbackState == Player.STATE_READY || controller.playbackState == Player.STATE_BUFFERING
            val serviceSpeaker = customState.getString("speakerId", _ttsState.value.speakerId)
            val sessionEndedByStop = customState.getBoolean("sessionEndedByStop", false)
            val isLoading = customState.getBoolean("isLoading", false)
            val sessionFinished = customState.getBoolean("sessionFinished", false)
            val playbackSource = customState.getString("playbackSource")
            val serviceBookTitle = customState.getString("bookTitle")
            val serviceChapterTitle = customState.getString("chapterTitle")
            val serviceChapterIndex = customState.getInt("chapterIndex", -1).takeIf { it >= 0 }
            val serviceTotalChapters = customState.getInt("totalChapters", -1).takeIf { it > 0 }
            val serviceCurrentChunkIndex = customState.getInt("currentChunkIndex", -1)
            val serviceTotalChunks = customState.getInt("totalChunks", 0)
            val serviceBookProgressPercent = customState.getInt("bookProgressPercent", -1).takeIf { it >= 0 }

            val sourceCfi = mediaItemExtras?.getString("sourceCfi")
            val startOffset = mediaItemExtras?.getInt("startOffset", -1) ?: -1
            val currentWordSourceCfi = customState.getString("currentWordSourceCfi")
            val currentWordStartOffset = customState.getInt("currentWordStartOffset", -1)
            val serviceMode = customState.getString("ttsMode", _ttsState.value.ttsMode)

            val currentState = _ttsState.value
            _ttsState.value = currentState.copy(
                isPlaying = controller.isPlaying,
                isLoading = isLoading,
                currentText = if (isPlaybackActive) {
                    currentTextFromMediaItem
                } else {
                    if (isLoading) currentState.currentText else null
                },
                errorMessage = customState.getString("errorMessage"),
                bookTitle = if (isPlaybackActive) {
                    currentMediaItem?.mediaMetadata?.artist?.toString() ?: serviceBookTitle
                } else {
                    if (isLoading) currentState.bookTitle else serviceBookTitle
                },
                chapterTitle = if (isPlaybackActive || isLoading) {
                    serviceChapterTitle ?: currentState.chapterTitle
                } else {
                    serviceChapterTitle
                },
                chapterIndex = if (isPlaybackActive || isLoading) {
                    serviceChapterIndex ?: currentState.chapterIndex
                } else {
                    serviceChapterIndex
                },
                totalChapters = if (isPlaybackActive || isLoading) {
                    serviceTotalChapters ?: currentState.totalChapters
                } else {
                    serviceTotalChapters
                },
                currentChunkIndex = serviceCurrentChunkIndex,
                totalChunks = serviceTotalChunks,
                bookProgressPercent = serviceBookProgressPercent,
                speakerId = serviceSpeaker,
                sourceCfi = if (isPlaybackActive) {
                    sourceCfi
                } else {
                    if (isLoading) currentState.sourceCfi else null
                },
                startOffsetInSource = if (isPlaybackActive) {
                    startOffset
                } else {
                    if (isLoading) currentState.startOffsetInSource else -1
                },
                playbackState = controller.playbackState,
                sessionEndedByStop = sessionEndedByStop,
                currentWordSourceCfi = if (isPlaybackActive) currentWordSourceCfi else null,
                currentWordStartOffset = if (isPlaybackActive) currentWordStartOffset else -1,
                sessionFinished = sessionFinished,
                playbackSource = playbackSource,
                ttsMode = serviceMode
            )
        }
    }



    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val args = Bundle().apply {
            putFloat("speed", speed)
            putFloat("pitch", pitch)
        }
        mediaController?.sendCustomCommand(SET_PLAYBACK_PARAMS_COMMAND, args)
    }

    fun release() {
        pollingJob?.cancel()
        scope.cancel()

        val future = controllerFuture
        controllerFuture = null
        if (future != null && !future.isDone) {
            future.cancel(true)
        }

        mediaController?.removeListener(this)
        mediaController?.release()
        mediaController = null
        Timber.d("MediaController released.")
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember {
        TtsController(context)
    }

    LaunchedEffect(controller) {
        controller.connect()
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    return controller
}
