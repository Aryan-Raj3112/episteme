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

import android.net.Uri
import android.os.Bundle
import timber.log.Timber
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aryan.reader.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri
import com.aryan.reader.paginatedreader.TimedWord
import com.aryan.reader.paginatedreader.TtsChunk
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val START_TTS_COMMAND = SessionCommand("com.aryan.reader.tts.START", Bundle.EMPTY)
val STOP_TTS_COMMAND = SessionCommand("com.aryan.reader.tts.STOP", Bundle.EMPTY)
val CHANGE_SPEAKER_COMMAND = SessionCommand("com.aryan.reader.tts.CHANGE_SPEAKER", Bundle.EMPTY)
val FLUSH_PREFETCH_COMMAND = SessionCommand("com.aryan.reader.tts.FLUSH_PREFETCH", Bundle.EMPTY)
private val STATE_UPDATE_COMMAND = SessionCommand("com.aryan.reader.tts.STATE_UPDATE", Bundle.EMPTY)
val CHANGE_TTS_MODE_COMMAND = SessionCommand("com.aryan.reader.tts.CHANGE_MODE", Bundle.EMPTY)
val SLICE_CURRENT_AND_RELOAD_COMMAND = SessionCommand("com.aryan.reader.tts.SLICE_AND_RELOAD", Bundle.EMPTY)
val SET_PLAYBACK_PARAMS_COMMAND = SessionCommand("com.aryan.reader.tts.SET_PLAYBACK_PARAMS", Bundle.EMPTY)
const val TTS_NOTIFICATION_DIAG_TAG = "TTS_NOTIFICATION_DIAG"

const val KEY_TEXT_CHUNKS = "KEY_TEXT_CHUNKS"
const val KEY_SOURCE_CFIS = "KEY_SOURCE_CFIS"
const val KEY_START_OFFSETS = "KEY_START_OFFSETS"
const val KEY_SPEAKER_ID = "KEY_SPEAKER_ID"
const val KEY_BOOK_TITLE = "KEY_BOOK_TITLE"
const val KEY_CHAPTER_TITLE = "KEY_CHAPTER_TITLE"
const val KEY_COVER_IMAGE_URI = "KEY_COVER_IMAGE_URI"
const val KEY_TTS_MODE = "KEY_TTS_MODE"
const val KEY_WORD_TIMESTAMPS = "KEY_WORD_TIMESTAMPS"
const val KEY_WORD_OFFSETS = "KEY_WORD_OFFSETS"
const val KEY_PLAYBACK_SOURCE = "KEY_PLAYBACK_SOURCE"
const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"
const val KEY_CHAPTER_INDEX = "KEY_CHAPTER_INDEX"
const val KEY_TOTAL_CHAPTERS = "KEY_TOTAL_CHAPTERS"
const val KEY_CONTINUE_SESSION = "KEY_CONTINUE_SESSION"

private const val PREFETCH_LOOKAHEAD = 3

@UnstableApi
class TtsPlaybackManager(
    private val player: Player,
    private val generateAudioChunk: suspend (bookTitle: String, chapterTitle: String?, chunkIndex: Int, totalChunks: Int, textChunk: String, speakerId: String, mode: TtsMode, authToken: String?) -> TtsAudioData,
    private val onResetContext: () -> Unit
) : MediaSession.Callback, Player.Listener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private val prefetchingJobs = java.util.concurrent.ConcurrentHashMap<Int, Job>()
    private var wordTrackingJob: Job? = null
    private var preparationJob: Job? = null
    private var prefetchLoopJob: Job? = null
    private var lastPrefetchIndex = -1
    private var currentAuthToken: String? = null
    private val loadedChunks: MutableSet<Int> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    private val chunkStreamIds = java.util.concurrent.ConcurrentHashMap<Int, String>()

    enum class TtsMode {
        CLOUD, BASE
    }

    data class TtsState(
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val currentText: String? = null,
        val errorMessage: String? = null,
        val bookTitle: String? = null,
        val chapterTitle: String? = null,
        val chapterIndex: Int? = null,
        val totalChapters: Int? = null,
        val currentChunkIndex: Int = -1,
        val totalChunks: Int = 0,
        val bookProgressPercent: Int? = null,
        val speakerId: String = DEFAULT_SPEAKER_ID,
        val sourceCfi: String? = null,
        val startOffsetInSource: Int = -1,
        val playbackState: Int = Player.STATE_IDLE,
        val sessionEndedByStop: Boolean = false,
        val currentWordSourceCfi: String? = null,
        val currentWordStartOffset: Int = -1,
        val sessionFinished: Boolean = false,
        val playbackSource: String? = null,
        val ttsMode: String = TtsMode.CLOUD.name
    )

    private val _ttsState = MutableStateFlow(TtsState())

    private var textChunks: List<TtsChunk> = emptyList()
    private val audioFiles = java.util.concurrent.ConcurrentHashMap<Int, File>()
    private var currentSpeakerId = DEFAULT_SPEAKER_ID
    private var bookTitle: String? = null
    private var chapterTitle: String? = null
    private var coverImageUri: String? = null
    private var currentTtsMode = TtsMode.CLOUD
    private var chapterIndex: Int? = null
    private var totalChapters: Int? = null

    init {
        player.addListener(this)
        _ttsState.onEach { newState ->
            mediaSession?.let { session ->
                val layout = listOf(
                    createStateButton(newState),
                    createStopCommandButton()
                )
                session.setCustomLayout(layout)
            }
        }.launchIn(scope)
    }

    fun setMediaSession(session: MediaSession) {
        this.mediaSession = session
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "MediaSession onConnect. package=${controller.packageName}, uid=${controller.uid}"
        )
        val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(START_TTS_COMMAND)
            .add(STOP_TTS_COMMAND)
            .add(CHANGE_SPEAKER_COMMAND)
            .add(CHANGE_TTS_MODE_COMMAND)
            .add(FLUSH_PREFETCH_COMMAND)
            .add(SLICE_CURRENT_AND_RELOAD_COMMAND)
            .add(SET_PLAYBACK_PARAMS_COMMAND)
            .build()
        val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableSessionCommands)
            .setAvailablePlayerCommands(availablePlayerCommands)
            .build()
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return Futures.immediateFuture(mediaItems)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand) {
            START_TTS_COMMAND -> {
                val chunks = args.getStringArrayList(KEY_TEXT_CHUNKS) ?: emptyList()
                Timber.d("TtsService: START command received. Size: ${chunks.size}")
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
                    "START command received. chunks=${chunks.size}, continueSession=${args.getBoolean(KEY_CONTINUE_SESSION, false)}, source=${args.getString(KEY_PLAYBACK_SOURCE)}, mode=${args.getString(KEY_TTS_MODE)}, chapterIndex=${args.getInt(KEY_CHAPTER_INDEX, -1)}, totalChapters=${args.getInt(KEY_TOTAL_CHAPTERS, -1)}"
                )
                val cfis = args.getStringArrayList(KEY_SOURCE_CFIS)
                val offsets = args.getIntegerArrayList(KEY_START_OFFSETS)
                val speakerId = args.getString(KEY_SPEAKER_ID, DEFAULT_SPEAKER_ID)
                val bookTitle = args.getString(KEY_BOOK_TITLE)
                val chapterTitle = args.getString(KEY_CHAPTER_TITLE)
                val coverImageUri = args.getString(KEY_COVER_IMAGE_URI)
                val chapterIndex = args.getInt(KEY_CHAPTER_INDEX, -1).takeIf { it >= 0 }
                val totalChapters = args.getInt(KEY_TOTAL_CHAPTERS, -1).takeIf { it > 0 }
                val ttsModeName = args.getString(KEY_TTS_MODE, TtsMode.CLOUD.name)
                val playbackSource = args.getString(KEY_PLAYBACK_SOURCE)
                val ttsMode = try { TtsMode.valueOf(ttsModeName ?: TtsMode.CLOUD.name) } catch (_: Exception) { TtsMode.CLOUD }

                val richChunks = if (cfis != null && offsets != null && chunks.size == cfis.size && chunks.size == offsets.size) {
                    chunks.mapIndexed { index, text ->
                        val safeOffset = offsets.getOrNull(index) ?: -1
                        TtsChunk(text, cfis[index], safeOffset)
                    }
                } else {
                    chunks.map { TtsChunk(it, "", -1) }
                }

                val authToken = args.getString(KEY_AUTH_TOKEN)
                Timber.tag("TTS_CLOUD_DIAG").d("TtsPlaybackManager received START. Token present: ${!authToken.isNullOrBlank()}")
                handleStartTts(richChunks, speakerId, bookTitle, chapterTitle, coverImageUri, chapterIndex, totalChapters, ttsMode, playbackSource, args)
            }
            STOP_TTS_COMMAND -> {
                Timber.d("Received STOP command.")
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("STOP command received.")
                handleStopTts(userInitiated = true)
            }
            CHANGE_SPEAKER_COMMAND -> {
                val newSpeakerId = args.getString(KEY_SPEAKER_ID, DEFAULT_SPEAKER_ID)
                handleChangeSpeaker(newSpeakerId)
            }
            CHANGE_TTS_MODE_COMMAND -> {
                val newModeName = args.getString(KEY_TTS_MODE, TtsMode.CLOUD.name)
                val newMode = try { TtsMode.valueOf(newModeName) } catch (_: Exception) { TtsMode.CLOUD }
                handleChangeTtsMode(newMode)
            }
            FLUSH_PREFETCH_COMMAND -> {
                Timber.d("Flushing prefetched TTS chunks for new parameters.")
                onResetContext()
                lastPrefetchIndex = -1
                prefetchLoopJob?.cancel()
                prefetchingJobs.values.forEach { it.cancel() }
                prefetchingJobs.clear()

                scope.launch(Dispatchers.Main) {
                    val currentIdx = player.currentMediaItemIndex
                    if (currentIdx == C.INDEX_UNSET) return@launch

                    val keysToRemove = loadedChunks.filter { it > currentIdx }
                    withContext(Dispatchers.IO) {
                        keysToRemove.forEach { key ->
                            loadedChunks.remove(key)
                            val file = audioFiles.remove(key)
                            deleteTempFile(file)
                            val streamId = chunkStreamIds.remove(key)
                            if (streamId != null) {
                                StreamRegistry.remove(streamId)
                            }
                        }
                    }

                    val itemsToRemove = mutableListOf<Int>()
                    for (k in 0 until player.mediaItemCount) {
                        val id = player.getMediaItemAt(k).mediaId.toIntOrNull() ?: -1
                        if (id > currentIdx) {
                            itemsToRemove.add(k)
                        }
                    }
                    itemsToRemove.reversed().forEach {
                        player.removeMediaItem(it)
                    }

                    prefetchNextChunkAudio(currentIdx)
                }
            }
            SLICE_CURRENT_AND_RELOAD_COMMAND -> {
                handleSliceAndReload()
            }
            SET_PLAYBACK_PARAMS_COMMAND -> {
                val speed = args.getFloat("speed", 1f)
                val pitch = args.getFloat("pitch", 1f)
                if (currentTtsMode == TtsMode.CLOUD) {
                    scope.launch(Dispatchers.Main) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
                    }
                }
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private fun handleSliceAndReload() {
        val currentIdx = player.currentMediaItemIndex
        if (currentIdx == C.INDEX_UNSET) return

        player.pause()
        _ttsState.value = _ttsState.value.copy(isLoading = true)

        onResetContext()

        val offset = _ttsState.value.currentWordStartOffset
        val currentChunk = textChunks.getOrNull(currentIdx) ?: return

        preparationJob?.cancel()
        wordTrackingJob?.cancel()
        player.stop()
        player.clearMediaItems()
        lastPrefetchIndex = -1
        prefetchLoopJob?.cancel()
        prefetchingJobs.values.forEach { it.cancel() }
        prefetchingJobs.clear()

        preparationJob = scope.launch {
            clearAudioFiles()
            loadedChunks.clear()

            if (offset == -1) {
                prepareAndPlayFirstChunk(startAtIndex = currentIdx, playWhenReady = false)
                return@launch
            }

            val relativeOffset = (offset - currentChunk.startOffsetInSource).coerceIn(0, currentChunk.text.length)

            if (relativeOffset >= currentChunk.text.length) {
                if (currentIdx + 1 < textChunks.size) {
                    prepareAndPlayFirstChunk(startAtIndex = currentIdx + 1, playWhenReady = false)
                }
                return@launch
            }

            val slicedText = currentChunk.text.substring(relativeOffset)
            val newChunk = currentChunk.copy(text = slicedText, startOffsetInSource = offset)

            val mutableChunks = textChunks.toMutableList()
            mutableChunks[currentIdx] = newChunk
            textChunks = mutableChunks.toList()

            prepareAndPlayFirstChunk(startAtIndex = currentIdx, playWhenReady = false)
        }
    }

    private fun handleChangeTtsMode(newMode: TtsMode) {
        if (currentTtsMode == newMode) return
        currentTtsMode = newMode
        _ttsState.value = _ttsState.value.copy(ttsMode = newMode.name)
        Timber.d("TTS Mode changed to $newMode (pending next start)")
    }

    private fun handleStartTts(
        chunks: List<TtsChunk>,
        speakerId: String,
        bookTitle: String?,
        chapterTitle: String?,
        coverImageUri: String?,
        chapterIndex: Int?,
        totalChapters: Int?,
        ttsMode: TtsMode,
        playbackSource: String?,
        args: Bundle // Added this parameter
    ) {
        if (chunks.isEmpty()) {
            _ttsState.value = _ttsState.value.copy(errorMessage = "No text to read.")
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("handleStartTts aborted because chunks is empty.")
            return
        }

        // --- YOUR SNIPPET START ---
        val authToken = args.getString(KEY_AUTH_TOKEN)
        val continueSession = args.getBoolean(KEY_CONTINUE_SESSION, false)
        val speed = args.getFloat("playback_speed", 1f)
        val pitch = args.getFloat("playback_pitch", 1f)

        scope.launch(Dispatchers.Main) {
            if (ttsMode == TtsMode.CLOUD) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
            } else {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(1f, 1f)
            }
        }

        Timber.tag("TTS_CLOUD_DIAG").d("TtsPlaybackManager received START. Token present: ${!authToken.isNullOrBlank()}")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "handleStartTts. continueSession=$continueSession, chunks=${chunks.size}, book='${bookTitle.orEmpty().take(60)}', chapter='${chapterTitle.orEmpty().take(60)}', chapterIndex=$chapterIndex, totalChapters=$totalChapters, mode=$ttsMode, playbackSource=$playbackSource"
        )

        if (!continueSession) {
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("New TTS session. Calling handleStopTts(clearState=false) before start.")
            handleStopTts(clearState = false)
        }

        textChunks = chunks
        currentSpeakerId = speakerId
        currentTtsMode = ttsMode
        this.bookTitle = bookTitle
        this.chapterTitle = chapterTitle
        this.coverImageUri = coverImageUri
        this.chapterIndex = chapterIndex
        this.totalChapters = totalChapters

        loadedChunks.clear()
        lastPrefetchIndex = -1

        _ttsState.value = TtsState(
            isLoading = true,
            bookTitle = bookTitle,
            chapterTitle = chapterTitle,
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            currentChunkIndex = -1,
            totalChunks = chunks.size,
            bookProgressPercent = calculateBookProgressPercent(-1),
            speakerId = speakerId,
            playbackSource = playbackSource,
            ttsMode = ttsMode.name,
            currentText = if (continueSession) _ttsState.value.currentText else null
        )
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "TTS state set to loading. bookProgress=${_ttsState.value.bookProgressPercent}, currentTextRetained=${_ttsState.value.currentText != null}"
        )

        if (continueSession) {
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("Continuation start. Cancelling prefetch/tracking but keeping player session alive until replacement media is ready.")
            preparationJob?.cancel()
            wordTrackingJob?.cancel()
            prefetchLoopJob?.cancel()
            prefetchingJobs.values.forEach { it.cancel() }
            prefetchingJobs.clear()
            clearPlaylistForContinuation()
        }

        currentAuthToken = authToken
        preparationJob = scope.launch {
            prepareAndPlayFirstChunk()
        }
    }

    fun forceStopWithError(errorMessage: String) {
        scope.launch(Dispatchers.Main) {
            _ttsState.value = _ttsState.value.copy(
                isLoading = false,
                isPlaying = false,
                errorMessage = errorMessage
            )
            handleStopTts(clearState = false)
        }
    }

    private fun handleChangeSpeaker(newSpeakerId: String) {
        if (currentSpeakerId == newSpeakerId) return
        currentSpeakerId = newSpeakerId
        _ttsState.value = _ttsState.value.copy(speakerId = newSpeakerId)
        Timber.d("Speaker changed to $newSpeakerId (pending next start)")
    }

    private fun currentChunkIndexFromPlayer(): Int {
        return player.currentMediaItem?.mediaId?.toIntOrNull()
            ?: player.currentMediaItemIndex
    }

    private fun calculateBookProgressPercent(chunkIndex: Int): Int? {
        val chapter = chapterIndex ?: return null
        val chapterCount = totalChapters?.takeIf { it > 0 } ?: return null
        val safeChunkProgress = if (textChunks.isNotEmpty() && chunkIndex >= 0) {
            ((chunkIndex + 1).toDouble() / textChunks.size.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return (((chapter.toDouble() + safeChunkProgress) / chapterCount.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun markSessionFinishedNaturally(chunkIndex: Int) {
        val currentState = _ttsState.value
        if (currentState.isLoading && currentState.currentChunkIndex == -1) {
            Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d(
                "Ignoring stale streamed completion while a continuation session is loading."
            )
            return
        }

        val safeChunkIndex = if (textChunks.isNotEmpty()) {
            chunkIndex.coerceIn(0, textChunks.lastIndex)
        } else {
            -1
        }

        Timber.tag("TTS_CHAPTER_CHANGE_DIAG").i(
            "Setting sessionFinished = true for naturally completed streamed TTS. chunk=$safeChunkIndex, totalChunks=${textChunks.size}"
        )

        _ttsState.value = _ttsState.value.copy(
            isPlaying = false,
            isLoading = false,
            currentChunkIndex = safeChunkIndex,
            totalChunks = textChunks.size,
            bookProgressPercent = calculateBookProgressPercent(safeChunkIndex),
            currentWordSourceCfi = null,
            currentWordStartOffset = -1,
            sessionFinished = true
        )
    }

    private fun clearPlaylistForContinuation() {
        val filesToDelete = audioFiles.values.toList()
        val streamsToRemove = chunkStreamIds.values.toList()
        audioFiles.clear()
        chunkStreamIds.clear()
        loadedChunks.clear()

        lastPrefetchIndex = -1
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "Cleared continuation temp resources. oldFiles=${filesToDelete.size}, oldStreams=${streamsToRemove.size}"
        )

        scope.launch(Dispatchers.IO) {
            filesToDelete.forEach { deleteTempFile(it) }
            streamsToRemove.forEach { StreamRegistry.remove(it) }
        }
    }

    private suspend fun prepareAndPlayFirstChunk(startAtIndex: Int = 0, playWhenReady: Boolean = true, startAtPosition: Long = 0L) {
        val firstChunk = textChunks.getOrNull(startAtIndex)
        if (firstChunk == null) {
            _ttsState.value = _ttsState.value.copy(isLoading = false, errorMessage = "Error starting playback.")
            return
        }

        val chunkStartTime = System.currentTimeMillis()
        Timber.tag("TTS_CLOUD_DIAG").i("Starting audio generation for first chunk (index=$startAtIndex).")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "Preparing first chunk. startAtIndex=$startAtIndex, playWhenReady=$playWhenReady"
        )

        val ttsAudioData = generateAudioChunk(bookTitle ?: "Unknown Book", chapterTitle, startAtIndex, textChunks.size, firstChunk.text, currentSpeakerId, currentTtsMode, currentAuthToken)
        Timber.tag("TTS_CLOUD_DIAG").i("generateAudioChunk returned in ${System.currentTimeMillis() - chunkStartTime}ms")

        if (ttsAudioData.error == "INSUFFICIENT_CREDITS") {
            withContext(Dispatchers.Main) {
                _ttsState.value = _ttsState.value.copy(isLoading = false, isPlaying = false, errorMessage = "INSUFFICIENT_CREDITS")
                handleStopTts(clearState = false)
            }
            return
        }

        val audioFile = ttsAudioData.audioFile
        val streamUri = ttsAudioData.streamUri
        val serverText = ttsAudioData.serverText

        if ((audioFile != null || streamUri != null) && serverText != null) {
            if (audioFile != null) {
                audioFiles[startAtIndex] = audioFile
            }
            loadedChunks.add(startAtIndex)

            val updatedChunk = processWordTimings(firstChunk, serverText, ttsAudioData.wordTimings)
            val mutableChunks = textChunks.toMutableList()
            mutableChunks[startAtIndex] = updatedChunk
            textChunks = mutableChunks.toList()

            if (streamUri != null) {
                val uriStr = streamUri.toUri()
                val id = uriStr.host ?: uriStr.lastPathSegment
                if (id != null) chunkStreamIds[startAtIndex] = id
            }
            val pathToUse = streamUri ?: audioFile!!.absolutePath
            val mediaItem = createMediaItem(serverText, pathToUse, startAtIndex, updatedChunk)

            withContext(Dispatchers.Main) {
                val prepStartTime = System.currentTimeMillis()
                player.setMediaItem(mediaItem)
                player.prepare()
                if (startAtPosition > 0) {
                    player.seekTo(startAtPosition)
                }
                player.playWhenReady = playWhenReady
                Timber.tag("TTS_CLOUD_DIAG").i("ExoPlayer setMediaItem & prepare called in ${System.currentTimeMillis() - prepStartTime}ms")
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
                    "Player prepared for TTS. mediaId=${mediaItem.mediaId}, title='${mediaItem.mediaMetadata.title}', playWhenReady=${player.playWhenReady}, playbackState=${player.playbackState}, mediaItems=${player.mediaItemCount}"
                )
                _ttsState.value = _ttsState.value.copy(
                    isLoading = false,
                    isPlaying = playWhenReady,
                    currentText = serverText,
                    chapterTitle = chapterTitle,
                    chapterIndex = chapterIndex,
                    totalChapters = totalChapters,
                    currentChunkIndex = startAtIndex,
                    totalChunks = textChunks.size,
                    bookProgressPercent = calculateBookProgressPercent(startAtIndex),
                    sessionFinished = false,
                    sourceCfi = updatedChunk.sourceCfi,
                    startOffsetInSource = updatedChunk.startOffsetInSource
                )
            }
            prefetchNextChunkAudio(startAtIndex)
        } else {
            _ttsState.value = _ttsState.value.copy(
                isLoading = false,
                errorMessage = ttsAudioData.error ?: "Failed to load audio."
            )
        }
    }

    private fun processWordTimings(
        originalChunk: TtsChunk,
        @Suppress("unused") serverText: String,
        wordTimings: List<WordTimingInfo>?
    ): TtsChunk {
        if (wordTimings.isNullOrEmpty()) {
            return originalChunk
        }

        val timedWords = mutableListOf<TimedWord>()
        var currentSearchIndex = 0
        wordTimings.forEach { timingInfo ->
            val wordIndex = originalChunk.text.indexOf(timingInfo.word, startIndex = currentSearchIndex, ignoreCase = false)
            if (wordIndex != -1) {
                timedWords.add(
                    TimedWord(
                        word = timingInfo.word,
                        startTime = timingInfo.startTime,
                        startOffset = originalChunk.startOffsetInSource + wordIndex
                    )
                )
                currentSearchIndex = wordIndex + timingInfo.word.length
            } else {
                Timber.w("Could not find server word '${timingInfo.word}' in original chunk text")
            }
        }
        return originalChunk.copy(timedWords = timedWords)
    }

    private fun handleStopTts(clearState: Boolean = true, userInitiated: Boolean = false) {
        Timber.tag("TTS_CLOUD_DIAG").d("handleStopTts called. clearState=$clearState, userInitiated=$userInitiated")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "handleStopTts. clearState=$clearState, userInitiated=$userInitiated"
        )
        onResetContext()
        preparationJob?.cancel()
        wordTrackingJob?.cancel()
        if (clearState) {
            val finalState = TtsState(sessionEndedByStop = userInitiated)
            _ttsState.value = finalState
            mediaSession?.let { session ->
                val layout = listOf(
                    createStateButton(finalState),
                    createStopCommandButton()
                )
                session.setCustomLayout(layout)
            }
        }

        player.stop()
        player.clearMediaItems()
        textChunks = emptyList()
        bookTitle = null
        chapterTitle = null
        coverImageUri = null
        chapterIndex = null
        totalChapters = null
        lastPrefetchIndex = -1
        prefetchLoopJob?.cancel()
        prefetchingJobs.values.forEach { it.cancel() }
        prefetchingJobs.clear()
        loadedChunks.clear()

        scope.launch {
            clearAudioFiles()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val newPlaylistIndex = player.currentMediaItemIndex
        Timber.tag("TTS_CLOUD_DIAG").d("onMediaItemTransition to playlistIndex: $newPlaylistIndex, mediaId: ${mediaItem?.mediaId}, reason: $reason")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "onMediaItemTransition. playlistIndex=$newPlaylistIndex, mediaId=${mediaItem?.mediaId}, reason=$reason, title='${mediaItem?.mediaMetadata?.title}', playbackState=${player.playbackState}, isPlaying=${player.isPlaying}"
        )
        if (newPlaylistIndex == C.INDEX_UNSET) return

        val currentChunkIndex = mediaItem?.mediaId?.toIntOrNull() ?: return

        val extras = mediaItem.mediaMetadata.extras
        val newText = extras?.getString("ttsText") ?: mediaItem.mediaMetadata.subtitle?.toString()
        val sourceCfi = extras?.getString("sourceCfi")
        val startOffset = extras?.getInt("startOffset", -1) ?: -1

        _ttsState.value = _ttsState.value.copy(
            currentText = newText,
            chapterTitle = chapterTitle,
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            currentChunkIndex = currentChunkIndex,
            totalChunks = textChunks.size,
            bookProgressPercent = calculateBookProgressPercent(currentChunkIndex),
            sessionFinished = false,
            sourceCfi = sourceCfi,
            startOffsetInSource = startOffset
        )

        wordTrackingJob?.cancel()
        if (player.isPlaying) {
            wordTrackingJob = scope.launch {
                trackWordByWord()
            }
        }
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && newPlaylistIndex > 0) {
            val previousMediaItem = player.getMediaItemAt(newPlaylistIndex - 1)
            val previousChunkIndex = previousMediaItem.mediaId.toIntOrNull()

            if (previousChunkIndex != null) {
                scope.launch(Dispatchers.IO) {
                    val file = audioFiles.remove(previousChunkIndex)
                    deleteTempFile(file)
                    loadedChunks.remove(previousChunkIndex)
                    val streamId = chunkStreamIds.remove(previousChunkIndex)
                    if (streamId != null) {
                        StreamRegistry.remove(streamId)
                    }
                }
            }
        }
        prefetchNextChunkAudio(currentChunkIndex)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "onIsPlayingChanged. isPlaying=$isPlaying, playbackState=${player.playbackState}, playWhenReady=${player.playWhenReady}, mediaItems=${player.mediaItemCount}, currentIndex=${player.currentMediaItemIndex}"
        )
        var nextState = _ttsState.value.copy(isPlaying = isPlaying)

        if (isPlaying) {
            if (nextState.isLoading) {
                nextState = nextState.copy(isLoading = false)
            }
            wordTrackingJob?.cancel()
            wordTrackingJob = scope.launch {
                trackWordByWord()
            }
        } else {
            wordTrackingJob?.cancel()
            nextState = nextState.copy(
                currentWordSourceCfi = null,
                currentWordStartOffset = -1
            )

            val currentChunkIndex = currentChunkIndexFromPlayer()
            val isLastChunkInSession = textChunks.isNotEmpty() && currentChunkIndex == textChunks.size - 1

            if (player.playbackState == Player.STATE_ENDED) {
                Timber.tag("TTS_CHAPTER_CHANGE_DIAG").d("ExoPlayer STATE_ENDED. currentChunkIndex: $currentChunkIndex, isLastChunk: $isLastChunkInSession, totalChunks: ${textChunks.size}")
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
                    "Player reached ENDED. currentChunkIndex=$currentChunkIndex, isLastChunk=$isLastChunkInSession, totalChunks=${textChunks.size}, sessionFinishedWillBeSet=${isLastChunkInSession || textChunks.isEmpty()}"
                )
                if (isLastChunkInSession || textChunks.isEmpty()) {
                    Timber.tag("TTS_CHAPTER_CHANGE_DIAG").i("Setting sessionFinished = true")
                    nextState = nextState.copy(
                        currentChunkIndex = currentChunkIndex,
                        totalChunks = textChunks.size,
                        bookProgressPercent = calculateBookProgressPercent(currentChunkIndex),
                        sessionFinished = true
                    )
                } else {
                    val nextIdx = currentChunkIndex + 1
                    val isPrefetching = prefetchingJobs.containsKey(nextIdx)

                    if (!isPrefetching) {
                        Timber.w("BUFFERING: Stalled at chunk $currentChunkIndex. Restarting prefetch for $nextIdx.")
                        prefetchNextChunkAudio(currentChunkIndex)
                    }
                    nextState = nextState.copy(isLoading = true)
                }
            }
        }

        _ttsState.value = nextState

        if (!isPlaying && player.playbackState == Player.STATE_IDLE) {
            if (!nextState.sessionEndedByStop && !nextState.isLoading && preparationJob?.isActive != true) {
                Timber.tag("TTS_CLOUD_DIAG").d("Auto-stopping TTS from onIsPlayingChanged (IDLE and not loading)")
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("Auto-stopping from IDLE/not-loading path.")
                handleStopTts(userInitiated = true)
            } else {
                Timber.tag("TTS_CLOUD_DIAG").d("Ignoring STATE_IDLE in onIsPlayingChanged because isLoading=${nextState.isLoading}, preparationJob.isActive=${preparationJob?.isActive}")
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        Timber.tag("TTS_CLOUD_DIAG").e(error, "Player error: [${error.errorCodeName}] ${error.message}")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).e(error, "Player error. code=${error.errorCodeName}, message=${error.message}")
        _ttsState.value = _ttsState.value.copy(errorMessage = "Playback error: ${error.message}")
        handleStopTts(userInitiated = true)
    }

    private fun prefetchNextChunkAudio(currentIndex: Int) {
        if (currentIndex == lastPrefetchIndex && prefetchLoopJob?.isActive == true) {
            return
        }
        lastPrefetchIndex = currentIndex

        prefetchLoopJob?.cancel()
        prefetchLoopJob = scope.launch {
            for (i in 1..PREFETCH_LOOKAHEAD) {
                val targetIndex = currentIndex + i
                if (targetIndex < textChunks.size) {
                    if (prefetchingJobs.containsKey(targetIndex)) continue
                    if (audioFiles.containsKey(targetIndex)) continue
                    if (loadedChunks.contains(targetIndex)) continue

                    Timber.d("PlaybackManager: Scheduling prefetch for chunk $targetIndex")

                    val job = launch {
                        val nextChunk = textChunks[targetIndex]
                        val prefetchStartTime = System.currentTimeMillis()
                        Timber.tag("TTS_CLOUD_DIAG").i("Starting prefetch generation for chunk $targetIndex")

                        val ttsAudioData = generateAudioChunk(bookTitle ?: "Unknown Book", chapterTitle, targetIndex, textChunks.size, nextChunk.text, currentSpeakerId, currentTtsMode, currentAuthToken)

                        Timber.tag("TTS_CLOUD_DIAG").i("Prefetch audio setup for chunk $targetIndex took ${System.currentTimeMillis() - prefetchStartTime}ms")

                        if (ttsAudioData.error == "INSUFFICIENT_CREDITS") {
                            withContext(Dispatchers.Main) {
                                _ttsState.value = _ttsState.value.copy(isLoading = false, isPlaying = false, errorMessage = "INSUFFICIENT_CREDITS")
                                handleStopTts(clearState = false)
                            }
                            return@launch
                        }

                        val audioFile = ttsAudioData.audioFile
                        val streamUri = ttsAudioData.streamUri
                        val serverText = ttsAudioData.serverText

                        if ((audioFile != null || streamUri != null) && serverText != null) {
                            val updatedChunk = processWordTimings(nextChunk, serverText, ttsAudioData.wordTimings)
                            val pathToUse = streamUri ?: audioFile!!.absolutePath
                            val nextMediaItem = createMediaItem(serverText, pathToUse, targetIndex, updatedChunk)

                            withContext(Dispatchers.Main) {
                                if (audioFile != null) {
                                    audioFiles[targetIndex] = audioFile
                                }
                                loadedChunks.add(targetIndex)

                                val mutableChunks = textChunks.toMutableList()
                                mutableChunks[targetIndex] = updatedChunk
                                textChunks = mutableChunks.toList()

                                if (streamUri != null) {
                                    val uriStr = streamUri.toUri()
                                    val id = uriStr.host ?: uriStr.lastPathSegment
                                    if (id != null) chunkStreamIds[targetIndex] = id
                                }

                                val wasLoading = _ttsState.value.isLoading

                                var exists = false
                                for (k in 0 until player.mediaItemCount) {
                                    if (player.getMediaItemAt(k).mediaId == targetIndex.toString()) {
                                        exists = true
                                        break
                                    }
                                }

                                if (!exists) {
                                    var insertPosition = player.mediaItemCount
                                    for (k in 0 until player.mediaItemCount) {
                                        val id = player.getMediaItemAt(k).mediaId.toIntOrNull() ?: -1
                                        if (id > targetIndex) {
                                            insertPosition = k
                                            break
                                        }
                                    }
                                    player.addMediaItem(insertPosition, nextMediaItem)
                                }

                                val currentChunkIndex = currentChunkIndexFromPlayer()
                                val isImmediateNextChunk = targetIndex == currentChunkIndex + 1

                                if (player.playbackState == Player.STATE_ENDED && player.playWhenReady && isImmediateNextChunk) {
                                    player.seekToNextMediaItem()
                                    player.play()
                                } else if (wasLoading && isImmediateNextChunk) {
                                    _ttsState.value = _ttsState.value.copy(isLoading = false)
                                }
                            }
                        } else {
                            Timber.e("Prefetch: Failed to download chunk $targetIndex")
                        }
                    }
                    prefetchingJobs[targetIndex] = job
                    job.invokeOnCompletion {
                        prefetchingJobs.remove(targetIndex)
                    }

                    job.join()
                }
            }
        }
    }

    private suspend fun trackWordByWord() {
        var loopCount = 0
        while (true) {
            val currentIdx = withContext(Dispatchers.Main) { player.currentMediaItemIndex }
            val currentMediaItem = withContext(Dispatchers.Main) { player.currentMediaItem } ?: break
            val playbackPosition = withContext(Dispatchers.Main) { player.currentPosition }

            if (loopCount % 20 == 0) {
                withContext(Dispatchers.Main) { player.playbackState }
                withContext(Dispatchers.Main) { player.isPlaying }
            }

            val uri = currentMediaItem.localConfiguration?.uri
            if (uri?.scheme == "ttsstream") {
                val streamId = uri.host ?: uri.lastPathSegment
                if (streamId != null) {
                    val (isFinished, totalBytes) = StreamRegistry.getStreamMetadata(streamId)
                    if (isFinished && totalBytes > 44) {
                        val expectedDurationMs = (totalBytes - 44) / 48

                        if (playbackPosition >= expectedDurationMs) {
                            Timber.tag("TTS_CLOUD_DIAG").i("Stream finished naturally: pos=$playbackPosition, expected=$expectedDurationMs. Transitioning.")
                            withContext(Dispatchers.Main) {
                                if (player.currentMediaItemIndex == currentIdx) {
                                    if (player.hasNextMediaItem()) {
                                        player.seekToNextMediaItem()
                                    } else {
                                        val finishedChunkIndex = currentMediaItem.mediaId.toIntOrNull()
                                            ?: currentChunkIndexFromPlayer()
                                        markSessionFinishedNaturally(finishedChunkIndex)
                                        player.pause()
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            }

            val extras = currentMediaItem.mediaMetadata.extras ?: break
            val sourceCfi = extras.getString("sourceCfi") ?: break

            val timestamps = extras.getDoubleArray(KEY_WORD_TIMESTAMPS)
            val offsets = extras.getIntArray(KEY_WORD_OFFSETS)

            if (timestamps != null && offsets != null) {
                val currentWordIndex = timestamps.indexOfLast { (it * 1000).toLong() <= playbackPosition }
                if (currentWordIndex != -1) {
                    val currentWordOffset = offsets[currentWordIndex]
                    if (_ttsState.value.currentWordStartOffset != currentWordOffset || _ttsState.value.currentWordSourceCfi != sourceCfi) {
                        _ttsState.value = _ttsState.value.copy(
                            currentWordSourceCfi = sourceCfi,
                            currentWordStartOffset = currentWordOffset
                        )
                    }
                }
            }

            delay(50)
            loopCount++
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        Timber.tag("TTS_CLOUD_DIAG").d("onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reason")
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        Timber.tag("TTS_CLOUD_DIAG").d("onPositionDiscontinuity: reason=$reason")
    }

    private fun createMediaItem(text: String, path: String, index: Int, chunk: TtsChunk): MediaItem {
        val progress = calculateBookProgressPercent(index)
        val chunkLabel = if (textChunks.isNotEmpty()) {
            "Chunk ${index + 1}/${textChunks.size}"
        } else {
            null
        }
        val chapterLabel = buildString {
            val chapter = chapterIndex
            val chapterCount = totalChapters
            if (chapter != null && chapterCount != null) {
                append("Chapter ${chapter + 1} of $chapterCount")
                if (!chapterTitle.isNullOrBlank()) append(": $chapterTitle")
            } else if (!chapterTitle.isNullOrBlank()) {
                append(chapterTitle)
            }
            if (progress != null) {
                if (isNotEmpty()) append(" - ")
                append("$progress%")
            }
            if (chunkLabel != null) {
                if (isNotEmpty()) append(" - ")
                append(chunkLabel)
            }
        }.ifBlank { chapterTitle ?: chunkLabel ?: "TTS" }
        val chunkPreview = text
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)

        val extras = Bundle().apply {
            putString("ttsText", text)
            putString("sourceCfi", chunk.sourceCfi)
            putInt("startOffset", chunk.startOffsetInSource)
            if (chunk.timedWords.isNotEmpty()) {
                val timestamps = chunk.timedWords.map { it.startTime }.toDoubleArray()
                val offsets = chunk.timedWords.map { it.startOffset }.toIntArray()
                putDoubleArray(KEY_WORD_TIMESTAMPS, timestamps)
                putIntArray(KEY_WORD_OFFSETS, offsets)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(bookTitle ?: chapterLabel)
            .setDisplayTitle(bookTitle ?: chapterLabel)
            .setArtist(chapterLabel)
            .setSubtitle(chunkPreview)
            .setDescription(chunkPreview)
            .setArtworkUri(coverImageUri?.toUri())
            .setTrackNumber(index + 1)
            .setTotalTrackCount(textChunks.size)
            .setExtras(extras)
            .build()

        val uri = if (path.startsWith("ttsstream://")) path.toUri() else Uri.fromFile(File(path))

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(index.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun deleteTempFile(file: File?) {
        file?.let {
            if (it.name.startsWith("tts_audio_chunk_") || it.name.startsWith("base_tts_") || it.name.startsWith("tts_live_")) {
                it.delete()
            }
        }
    }

    private suspend fun clearAudioFiles() {
        withContext(Dispatchers.IO) {
            audioFiles.values.forEach { deleteTempFile(it) }
            audioFiles.clear()
            chunkStreamIds.values.forEach { StreamRegistry.remove(it) } // ADDED
            chunkStreamIds.clear() // ADDED
            loadedChunks.clear()
        }
    }

    @Suppress("Deprecation")
    private fun createStateButton(state: TtsState): CommandButton {
        val bundle = Bundle().apply {
            putBoolean("isLoading", state.isLoading)
            putString("errorMessage", state.errorMessage)
            putString("bookTitle", state.bookTitle)
            putString("chapterTitle", state.chapterTitle)
            putInt("chapterIndex", state.chapterIndex ?: -1)
            putInt("totalChapters", state.totalChapters ?: -1)
            putInt("currentChunkIndex", state.currentChunkIndex)
            putInt("totalChunks", state.totalChunks)
            putInt("bookProgressPercent", state.bookProgressPercent ?: -1)
            putString("speakerId", state.speakerId)
            putBoolean("sessionEndedByStop", state.sessionEndedByStop)
            putString("currentWordSourceCfi", state.currentWordSourceCfi)
            putInt("currentWordStartOffset", state.currentWordStartOffset)
            putBoolean("sessionFinished", state.sessionFinished)
            putString("playbackSource", state.playbackSource)
            putString("ttsMode", state.ttsMode)
        }
        return CommandButton.Builder()
            .setSessionCommand(STATE_UPDATE_COMMAND)
            .setDisplayName("TtsState")
            .setExtras(bundle)
            .build()
    }

    @Suppress("Deprecation")
    private fun createStopCommandButton(): CommandButton {
        return CommandButton.Builder()
            .setDisplayName("Stop TTS")
            .setSessionCommand(STOP_TTS_COMMAND)
            .setIconResId(R.drawable.close)
            .build()
    }

    fun release() {
        player.removeListener(this)
        handleStopTts(userInitiated = true)
        Timber.d("TtsPlaybackManager released.")
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val stateName = when (playbackState) {
            Player.STATE_IDLE -> "STATE_IDLE"
            Player.STATE_BUFFERING -> "STATE_BUFFERING"
            Player.STATE_READY -> "STATE_READY"
            Player.STATE_ENDED -> "STATE_ENDED"
            else -> "UNKNOWN"
        }
        Timber.tag("TTS_CLOUD_DIAG").d("ExoPlayer playback state changed: $stateName")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "onPlaybackStateChanged. state=$stateName, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, mediaItems=${player.mediaItemCount}, currentIndex=${player.currentMediaItemIndex}"
        )
    }
}
