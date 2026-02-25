/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
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
        if (mediaController != null || controllerFuture != null) return

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
                    updateStateFromController()
                    startPolling()
                } catch (e: Exception) {
                    Timber.w("Failed to connect MediaController: ${e.message}")
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
        ttsMode: String,
        playbackSource: String = "READER"
    ) {
        if (chunks.isEmpty()) {
            Timber.w("TtsController: start called with empty chunks!")
            return
        }
        Timber.d("UI sending START command with mode: $ttsMode")
        Timber.d("TtsController: Sending START command. Chunk count: ${chunks.size}. Mode: $ttsMode. First chunk len: ${chunks.first().text.length}")

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
            putString(KEY_TTS_MODE, ttsMode)
            putString(KEY_PLAYBACK_SOURCE, playbackSource)
        }
        mediaController?.sendCustomCommand(START_TTS_COMMAND, args)

        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setArtist(bookTitle)
            .setTitle(chapterTitle ?: "Reading Aloud")
        coverImageUri?.let { metadataBuilder.setArtworkUri(it.toUri()) }

        val metadata = MediaItem.Builder()
            .setMediaId("tts_session")
            .setMediaMetadata(metadataBuilder.build())
            .build()
        mediaController?.setMediaItem(metadata)
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
        val args = Bundle().apply {
            putString(KEY_TTS_MODE, mode)
        }
        mediaController?.sendCustomCommand(CHANGE_TTS_MODE_COMMAND, args)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        updateStateFromController()
    }

    private fun updateStateFromController() {
        mediaController?.let { controller ->
            val customState = controller.customLayout.firstOrNull()?.extras ?: Bundle.EMPTY
            val currentMediaItem = controller.currentMediaItem
            val currentTextFromMediaItem = currentMediaItem?.mediaMetadata?.subtitle?.toString()
            val isPlaybackActive = controller.isPlaying || controller.playbackState == Player.STATE_READY || controller.playbackState == Player.STATE_BUFFERING
            val serviceSpeaker = customState.getString("speakerId", _ttsState.value.speakerId)
            val sessionEndedByStop = customState.getBoolean("sessionEndedByStop", false)
            val isLoading = customState.getBoolean("isLoading", false)
            val isChangingConfig = customState.getBoolean("isChangingConfig", false)
            val sessionFinished = customState.getBoolean("sessionFinished", false)
            val playbackSource = customState.getString("playbackSource")

            val mediaItemExtras = currentMediaItem?.mediaMetadata?.extras
            val sourceCfi = mediaItemExtras?.getString("sourceCfi")
            val startOffset = mediaItemExtras?.getInt("startOffset", -1) ?: -1
            val currentWordSourceCfi = customState.getString("currentWordSourceCfi")
            val currentWordStartOffset = customState.getInt("currentWordStartOffset", -1)

            val currentState = _ttsState.value
            _ttsState.value = currentState.copy(
                isPlaying = controller.isPlaying,
                isLoading = isLoading,
                currentText = if (isPlaybackActive) {
                    currentTextFromMediaItem ?: customState.getString("currentText")
                } else {
                    if (isLoading) currentState.currentText else null
                },
                errorMessage = customState.getString("errorMessage"),
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
                isChangingConfig = isChangingConfig,
                sessionFinished = sessionFinished,
                playbackSource = playbackSource
            )
        }
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