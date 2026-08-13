package com.aryan.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.PREF_NATIVE_TTS_VOICE
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.epubreader.saveTtsPitch
import com.aryan.reader.epubreader.saveTtsSpeechRate
import com.aryan.reader.loadNativeVoice
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTts
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import com.aryan.reader.shared.ui.SharedMobileEpubVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Adapts shared reader controls to Android's existing MediaSessionService-backed
 * TTS implementation. Creating this adapter never connects to the service or
 * initializes TextToSpeech; only an explicit start or voice preview does so.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SharedMobileEpubTtsAdapter(context: Context) : SharedMobileEpubLocalTts {
    private val appContext = context.applicationContext
    private val controller = TtsController(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionId = 0L
    private var chunks: List<ReaderTtsChunk> = emptyList()
    private var pendingPauseOnStart = false
    private var lastFinished = false
    private var previewEngine: TextToSpeech? = null

    override var state by mutableStateOf(SharedMobileEpubLocalTtsState.IDLE)
        private set
    override var isSessionActive by mutableStateOf(false)
        private set
    override var progress by mutableStateOf(ReaderTtsProgress())
        private set
    override var speechRate by mutableStateOf(loadTtsSpeechRate(appContext))
        private set
    override var speechPitch by mutableStateOf(loadTtsPitch(appContext))
        private set
    override var availableVoices by mutableStateOf(emptyList<SharedMobileEpubVoice>())
        private set
    override var selectedVoiceIdentifier by mutableStateOf(loadNativeVoice(appContext))
        private set
    override var errorMessage by mutableStateOf<String?>(null)
        private set
    override var completionCount by mutableStateOf(0L)
        private set

    init {
        scope.launch {
            controller.ttsState.collectLatest { serviceState ->
                errorMessage = serviceState.errorMessage
                if (serviceState.sessionFinished && !lastFinished) completionCount += 1
                lastFinished = serviceState.sessionFinished
                val hasSession = sharedMobileTtsHasActiveSession(
                    isPlaying = serviceState.isPlaying,
                    isLoading = serviceState.isLoading,
                    totalChunks = serviceState.totalChunks,
                    sessionEndedByStop = serviceState.sessionEndedByStop,
                    sessionFinished = serviceState.sessionFinished,
                )
                isSessionActive = hasSession
                state = sharedMobileTtsUiState(serviceState.isPlaying, serviceState.isLoading, hasSession)
                val index = serviceState.currentChunkIndex.coerceIn(-1, chunks.lastIndex)
                progress = ReaderTtsProgress(
                    sessionId = sessionId,
                    chunks = chunks,
                    currentChunkIndex = index,
                )
                if (pendingPauseOnStart && (serviceState.isLoading || serviceState.totalChunks > 0)) {
                    pendingPauseOnStart = false
                    controller.pause()
                }
            }
        }
    }

    // Android parity: merely opening a reader must not bind or warm the TTS service.
    override fun prepare() = Unit

    override fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean,
    ) {
        val readable = chunks.filter { it.spokenText.isNotBlank() }
        if (readable.isEmpty()) return
        this.chunks = readable
        sessionId += 1
        isSessionActive = true
        pendingPauseOnStart = !playWhenReady
        progress = ReaderTtsProgress(
            sessionId = sessionId,
            chunks = readable,
            currentChunkIndex = startChunkIndex.coerceIn(0, readable.lastIndex),
        )
        val first = readable.getOrNull(startChunkIndex.coerceIn(0, readable.lastIndex))
        controller.start(
            chunks = readable.map { chunk ->
                TtsChunk(
                    text = chunk.text,
                    sourceCfi = chunk.sourceCfi.orEmpty(),
                    startOffsetInSource = chunk.startOffset,
                    spokenText = chunk.spokenText,
                )
            },
            bookTitle = bookTitle,
            chapterTitle = first?.chapterTitle,
            coverImageUri = null,
            bookId = bookId,
            chapterIndex = first?.chapterIndex,
            startChunkIndex = startChunkIndex.coerceIn(0, readable.lastIndex),
            ttsMode = TtsPlaybackManager.TtsMode.BASE,
            playbackSource = "READER",
        )
    }

    override fun pause() = controller.pause()
    override fun resume() = controller.resume()
    override fun skipPrevious() = controller.skipToPreviousChunk()
    override fun skipNext() = controller.skipToNextChunk()

    override fun setSpeechParameters(rate: Float, pitch: Float) {
        speechRate = rate.coerceIn(0.5f, 3f)
        speechPitch = pitch.coerceIn(0.5f, 2f)
        saveTtsSpeechRate(appContext, speechRate)
        saveTtsPitch(appContext, speechPitch)
        controller.setPlaybackParameters(speechRate, speechPitch)
    }

    override fun setVoice(identifier: String?) {
        selectedVoiceIdentifier = identifier?.takeIf { it.isNotBlank() }
        appContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit().apply {
            selectedVoiceIdentifier?.let { putString(PREF_NATIVE_TTS_VOICE, it) }
                ?: remove(PREF_NATIVE_TTS_VOICE)
        }.apply()
    }

    override fun previewVoice(identifier: String?) {
        previewEngine?.shutdown()
        previewEngine = TextToSpeech(appContext) { status ->
            val engine = previewEngine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            availableVoices = engine.voices.orEmpty().map { voice ->
                SharedMobileEpubVoice(voice.name, voice.name, voice.locale?.displayName.orEmpty())
            }.sortedWith(compareBy(SharedMobileEpubVoice::language, SharedMobileEpubVoice::name))
            identifier?.let { id -> engine.voices?.firstOrNull { it.name == id } }?.let { engine.voice = it }
            engine.setSpeechRate(speechRate)
            engine.setPitch(speechPitch)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = releasePreviewEngine()
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) = releasePreviewEngine()
            })
            engine.speak(PreviewText, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, PreviewId)
        }
    }

    override fun stop() {
        pendingPauseOnStart = false
        controller.stop()
        chunks = emptyList()
        progress = ReaderTtsProgress()
        isSessionActive = false
        state = SharedMobileEpubLocalTtsState.IDLE
    }

    override fun release() {
        releasePreviewEngine()
        controller.release()
        scope.cancel()
    }

    private fun releasePreviewEngine() {
        val engine = previewEngine
        previewEngine = null
        engine?.shutdown()
    }

    private companion object {
        const val PreviewId = "shared-reader-preview"
        const val PreviewText = "This is a sample of the selected reading voice."
    }
}

internal fun sharedMobileTtsHasActiveSession(
    isPlaying: Boolean,
    isLoading: Boolean,
    totalChunks: Int,
    sessionEndedByStop: Boolean,
    sessionFinished: Boolean,
): Boolean = isPlaying || isLoading ||
    (totalChunks > 0 && !sessionEndedByStop && !sessionFinished)

internal fun sharedMobileTtsUiState(
    isPlaying: Boolean,
    isLoading: Boolean,
    hasSession: Boolean,
): SharedMobileEpubLocalTtsState = when {
    isPlaying || isLoading -> SharedMobileEpubLocalTtsState.SPEAKING
    hasSession -> SharedMobileEpubLocalTtsState.PAUSED
    else -> SharedMobileEpubLocalTtsState.IDLE
}
