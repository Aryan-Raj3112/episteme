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

val START_TTS_COMMAND = SessionCommand("com.aryan.reader.tts.START", Bundle.EMPTY)
val STOP_TTS_COMMAND = SessionCommand("com.aryan.reader.tts.STOP", Bundle.EMPTY)
val CHANGE_SPEAKER_COMMAND = SessionCommand("com.aryan.reader.tts.CHANGE_SPEAKER", Bundle.EMPTY)
private val STATE_UPDATE_COMMAND = SessionCommand("com.aryan.reader.tts.STATE_UPDATE", Bundle.EMPTY)
val CHANGE_TTS_MODE_COMMAND = SessionCommand("com.aryan.reader.tts.CHANGE_MODE", Bundle.EMPTY)

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

private const val PREFETCH_LOOKAHEAD = 2

@UnstableApi
class TtsPlaybackManager(
    private val player: Player,
    private val generateAudioChunk: suspend (textChunk: String, speakerId: String, mode: TtsMode) -> TtsAudioData
) : MediaSession.Callback, Player.Listener {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private val prefetchingJobs = mutableMapOf<Int, Job>()
    private var wordTrackingJob: Job? = null
    private var preparationJob: Job? = null
    private var isChangingConfig = false

    enum class TtsMode {
        CLOUD, BASE
    }

    data class TtsState(
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val currentText: String? = null,
        val errorMessage: String? = null,
        val speakerId: String = DEFAULT_SPEAKER_ID,
        val sourceCfi: String? = null,
        val startOffsetInSource: Int = -1,
        val playbackState: Int = Player.STATE_IDLE,
        val sessionEndedByStop: Boolean = false,
        val currentWordSourceCfi: String? = null,
        val currentWordStartOffset: Int = -1,
        val isChangingConfig: Boolean = false,
        val sessionFinished: Boolean = false,
        val playbackSource: String? = null
    )

    private val _ttsState = MutableStateFlow(TtsState())

    private var textChunks: List<TtsChunk> = emptyList()
    private var audioFiles: MutableMap<Int, File> = mutableMapOf()
    private var currentSpeakerId = DEFAULT_SPEAKER_ID
    private var bookTitle: String? = null
    private var chapterTitle: String? = null
    private var coverImageUri: String? = null
    private var currentTtsMode = TtsMode.CLOUD

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
        val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(START_TTS_COMMAND)
            .add(STOP_TTS_COMMAND)
            .add(CHANGE_SPEAKER_COMMAND)
            .add(CHANGE_TTS_MODE_COMMAND)
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
                val cfis = args.getStringArrayList(KEY_SOURCE_CFIS)
                val offsets = args.getIntegerArrayList(KEY_START_OFFSETS)
                val speakerId = args.getString(KEY_SPEAKER_ID, DEFAULT_SPEAKER_ID)
                val bookTitle = args.getString(KEY_BOOK_TITLE)
                val chapterTitle = args.getString(KEY_CHAPTER_TITLE)
                val coverImageUri = args.getString(KEY_COVER_IMAGE_URI)
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

                handleStartTts(richChunks, speakerId, bookTitle, chapterTitle, coverImageUri, ttsMode, playbackSource)
            }
            STOP_TTS_COMMAND -> {
                Timber.d("Received STOP command.")
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
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private fun handleChangeTtsMode(newMode: TtsMode) {
        if (currentTtsMode == newMode) return
        preparationJob?.cancel()
        isChangingConfig = true
        val wasPlaying = player.isPlaying
        val currentChunkIndex = player.currentMediaItem?.mediaId?.toIntOrNull()
        val currentPosition = player.currentPosition
        currentTtsMode = newMode
        if (textChunks.isEmpty()) {
            _ttsState.value = _ttsState.value.copy(isPlaying = false)
            isChangingConfig = false
            return
        }
        _ttsState.value = _ttsState.value.copy(isLoading = true, isPlaying = false, isChangingConfig = true)
        player.stop()
        player.clearMediaItems()
        prefetchingJobs.values.forEach { it.cancel() }
        prefetchingJobs.clear()
        scope.launch {
            clearAudioFiles()
        }
        preparationJob = scope.launch {
            val startIndex = currentChunkIndex ?: 0
            prepareAndPlayFirstChunk(startAtIndex = startIndex, playWhenReady = wasPlaying, startAtPosition = currentPosition)
        }
    }

    private fun handleStartTts(
        chunks: List<TtsChunk>,
        speakerId: String,
        bookTitle: String?,
        chapterTitle: String?,
        coverImageUri: String?,
        ttsMode: TtsMode,
        playbackSource: String?
    ) {
        if (isChangingConfig) {
            Timber.w("Ignoring START command because a config change is already in progress.")
            return
        }
        if (chunks.isEmpty()) {
            _ttsState.value = _ttsState.value.copy(errorMessage = "No text to read.")
            return
        }
        handleStopTts(clearState = false)
        textChunks = chunks
        currentSpeakerId = speakerId
        currentTtsMode = ttsMode
        this.bookTitle = bookTitle
        this.chapterTitle = chapterTitle
        this.coverImageUri = coverImageUri
        _ttsState.value = TtsState(isLoading = true, speakerId = speakerId, playbackSource = playbackSource)

        preparationJob = scope.launch {
            prepareAndPlayFirstChunk()
        }
    }

    private fun handleChangeSpeaker(newSpeakerId: String) {
        if (currentSpeakerId == newSpeakerId) return
        preparationJob?.cancel()
        isChangingConfig = true
        val wasPlaying = player.isPlaying
        val currentChunkIndex = player.currentMediaItem?.mediaId?.toIntOrNull()
        val currentPosition = player.currentPosition
        currentSpeakerId = newSpeakerId
        if (textChunks.isEmpty()) {
            _ttsState.value = _ttsState.value.copy(speakerId = newSpeakerId)
            isChangingConfig = false
            return
        }
        _ttsState.value = _ttsState.value.copy(speakerId = newSpeakerId, isLoading = true, isPlaying = false, isChangingConfig = true)
        player.stop()
        player.clearMediaItems()
        prefetchingJobs.values.forEach { it.cancel() }
        prefetchingJobs.clear()
        scope.launch {
            clearAudioFiles()
        }
        preparationJob = scope.launch {
            val startIndex = currentChunkIndex ?: 0
            prepareAndPlayFirstChunk(startAtIndex = startIndex, playWhenReady = wasPlaying, startAtPosition = currentPosition)
        }
    }

    private suspend fun prepareAndPlayFirstChunk(startAtIndex: Int = 0, playWhenReady: Boolean = true, startAtPosition: Long = 0L) {
        val firstChunk = textChunks.getOrNull(startAtIndex)
        if (firstChunk == null) {
            _ttsState.value = _ttsState.value.copy(isLoading = false, errorMessage = "Error starting playback.", isChangingConfig = false)
            withContext(Dispatchers.Main) { isChangingConfig = false }
            return
        }

        val ttsAudioData = generateAudioChunk(firstChunk.text, currentSpeakerId, currentTtsMode)
        val audioFile = ttsAudioData.audioFile
        val serverText = ttsAudioData.serverText

        if (audioFile != null && serverText != null) {
            audioFiles[startAtIndex] = audioFile

            val updatedChunk = processWordTimings(firstChunk, serverText, ttsAudioData.wordTimings)
            val mutableChunks = textChunks.toMutableList()
            mutableChunks[startAtIndex] = updatedChunk
            textChunks = mutableChunks.toList()

            val mediaItem = createMediaItem(serverText, audioFile.absolutePath, startAtIndex, updatedChunk)

            withContext(Dispatchers.Main) {
                player.setMediaItem(mediaItem)
                player.prepare()
                if (startAtPosition > 0) {
                    player.seekTo(startAtPosition)
                }
                player.playWhenReady = playWhenReady
                isChangingConfig = false
                _ttsState.value = _ttsState.value.copy(
                    isLoading = false,
                    isPlaying = playWhenReady,
                    currentText = serverText,
                    sourceCfi = updatedChunk.sourceCfi,
                    startOffsetInSource = updatedChunk.startOffsetInSource,
                    isChangingConfig = false
                )
            }
            prefetchNextChunkAudio(startAtIndex)
        } else {
            withContext(Dispatchers.Main) { isChangingConfig = false }
            _ttsState.value = _ttsState.value.copy(isLoading = false, errorMessage = "Failed to load audio.", isChangingConfig = false)
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
        prefetchingJobs.values.forEach { it.cancel() }
        prefetchingJobs.clear()

        scope.launch {
            clearAudioFiles()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val newPlaylistIndex = player.currentMediaItemIndex
        if (newPlaylistIndex == C.INDEX_UNSET) return

        val currentChunkIndex = mediaItem?.mediaId?.toIntOrNull() ?: return

        val newText = mediaItem.mediaMetadata.subtitle?.toString()
        val extras = mediaItem.mediaMetadata.extras
        val sourceCfi = extras?.getString("sourceCfi")
        val startOffset = extras?.getInt("startOffset", -1) ?: -1

        _ttsState.value = _ttsState.value.copy(
            currentText = newText,
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
                scope.launch {
                    audioFiles.remove(previousChunkIndex)?.delete()
                }
            }
        }
        prefetchNextChunkAudio(currentChunkIndex)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
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

            val currentChunkIndex = player.currentMediaItemIndex
            val isLastChunkInSession = textChunks.isNotEmpty() && currentChunkIndex == textChunks.size - 1

            if (player.playbackState == Player.STATE_ENDED) {
                if (isLastChunkInSession || textChunks.isEmpty()) {
                    nextState = nextState.copy(sessionFinished = true)
                } else {
                    // Check if prefetch is active for the next chunk
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
            if (isChangingConfig) {
                return
            }
            if (!nextState.sessionEndedByStop) {
                handleStopTts(userInitiated = true)
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        Timber.e(error, "Player error: ${error.message}")
        _ttsState.value = _ttsState.value.copy(errorMessage = "Playback error: ${error.message}")
        handleStopTts(userInitiated = true)
    }

    private fun prefetchNextChunkAudio(currentIndex: Int) {
        for (i in 1..PREFETCH_LOOKAHEAD) {
            val targetIndex = currentIndex + i
            if (targetIndex < textChunks.size) {
                if (prefetchingJobs.containsKey(targetIndex)) {
                    continue
                }

                if (audioFiles.containsKey(targetIndex)) {
                    continue
                }

                Timber.d("PlaybackManager: Scheduling prefetch for chunk $targetIndex")

                val job = scope.launch {
                    val nextChunk = textChunks[targetIndex]
                    val ttsAudioData = generateAudioChunk(nextChunk.text, currentSpeakerId, currentTtsMode)
                    val audioFile = ttsAudioData.audioFile
                    val serverText = ttsAudioData.serverText

                    if (audioFile != null && serverText != null) {
                        audioFiles[targetIndex] = audioFile

                        val updatedChunk = processWordTimings(nextChunk, serverText, ttsAudioData.wordTimings)
                        val mutableChunks = textChunks.toMutableList()
                        mutableChunks[targetIndex] = updatedChunk
                        textChunks = mutableChunks.toList()

                        val nextMediaItem = createMediaItem(serverText, audioFile.absolutePath, targetIndex, updatedChunk)
                        withContext(Dispatchers.Main) {
                            val wasLoading = _ttsState.value.isLoading

                            var exists = false
                            for (k in 0 until player.mediaItemCount) {
                                if (player.getMediaItemAt(k).mediaId == targetIndex.toString()) {
                                    exists = true
                                    break
                                }
                            }

                            if (!exists) {
                                player.addMediaItem(nextMediaItem)
                            }

                            if (player.playbackState == Player.STATE_ENDED && player.playWhenReady && targetIndex == player.currentMediaItemIndex + 1) {
                                player.seekToNextMediaItem()
                                player.play()
                            } else if (wasLoading && targetIndex == player.currentMediaItemIndex + 1) {
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
            }
        }
    }

    private suspend fun trackWordByWord() {
        while (true) {
            val currentMediaItem = withContext(Dispatchers.Main) { player.currentMediaItem } ?: break
            val playbackPosition = withContext(Dispatchers.Main) { player.currentPosition }

            val extras = currentMediaItem.mediaMetadata.extras ?: break
            val timestamps = extras.getDoubleArray(KEY_WORD_TIMESTAMPS) ?: break
            val offsets = extras.getIntArray(KEY_WORD_OFFSETS) ?: break
            val sourceCfi = extras.getString("sourceCfi") ?: break

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
            delay(100)
        }
    }

    private fun createMediaItem(text: String, path: String, index: Int, chunk: TtsChunk): MediaItem {
        val extras = Bundle().apply {
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
            .setArtist(bookTitle)
            .setTitle(chapterTitle)
            .setSubtitle(text)
            .setArtworkUri(coverImageUri?.toUri())
            .setTrackNumber(index + 1)
            .setTotalTrackCount(textChunks.size)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setUri(Uri.fromFile(File(path)))
            .setMediaId(index.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun clearAudioFiles() {
        withContext(Dispatchers.IO) {
            audioFiles.values.forEach { it.delete() }
            audioFiles.clear()
        }
    }

    @Suppress("Deprecation")
    private fun createStateButton(state: TtsState): CommandButton {
        val bundle = Bundle().apply {
            putBoolean("isLoading", state.isLoading)
            putString("errorMessage", state.errorMessage)
            putString("speakerId", state.speakerId)
            putBoolean("sessionEndedByStop", state.sessionEndedByStop)
            putString("currentWordSourceCfi", state.currentWordSourceCfi)
            putInt("currentWordStartOffset", state.currentWordStartOffset)
            putBoolean("isChangingConfig", state.isChangingConfig)
            putBoolean("sessionFinished", state.sessionFinished)
            putString("playbackSource", state.playbackSource)
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
}