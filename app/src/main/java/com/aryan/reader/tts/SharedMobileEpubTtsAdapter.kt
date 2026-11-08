package com.aryan.reader.tts

import android.content.Context
import android.os.Bundle
import com.aryan.reader.shared.tts.createTextToSpeechOrNull
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
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
import com.aryan.reader.shared.ui.sharedMobileEpubVoiceQualityForAndroidQuality
import com.aryan.reader.shared.ui.sortedForTtsDisplay
import com.aryan.reader.shared.ui.toggleSharedMobileTtsVoiceFavorite
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
    // Service-side completion event counter (monotonic). The boolean edge it replaces could be
    // missed by a coalesced emission, which silently ended chapter chaining.
    private var lastServiceCompletionCount = 0L
    // Session identity, so a surface sharing this engine can tell its own session
    // apart from another surface's. Android benchmark: `TtsState.playbackSource`.
    private var activePlaybackSource: String? = null
    private var activeBookId: String? = null
    private var activeTotalChapters: Int = 0
    private var previewEngine: TextToSpeech? = null
    private var enumerationEngine: TextToSpeech? = null

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
    private var previewSampleTextState by mutableStateOf(effectiveTtsPreviewSampleText(appContext))
    override val previewSampleText: String get() = previewSampleTextState
    private var favoriteVoiceState by mutableStateOf(loadTtsFavoriteVoices(appContext))
    override val favoriteVoiceIdentifiers: Set<String> get() = favoriteVoiceState
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
                val completionDelta = serviceState.completionCount - lastServiceCompletionCount
                if (completionDelta > 0) completionCount += completionDelta
                lastServiceCompletionCount = serviceState.completionCount
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

    override val playbackSource: String? get() = activePlaybackSource
    override val sessionBookId: String? get() = activeBookId
    override val sessionTotalChapters: Int get() = activeTotalChapters
    // TtsPlaybackManager reports the spoken word position on its state stream; the reader does
    // not need it, so it is not mirrored here.
    override val currentSpokenOffset: Int get() = 0

    override fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean,
        playbackSource: String?,
        totalChapters: Int,
        continueSession: Boolean,
        authToken: String?,
    ) {
        val readable = chunks.filter { it.spokenText.isNotBlank() }
        if (readable.isEmpty()) return
        this.chunks = readable
        activePlaybackSource = playbackSource
        activeBookId = bookId
        activeTotalChapters = totalChapters
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

    override fun setPreviewSampleText(text: String) {
        saveTtsPreviewSampleText(appContext, text)
        previewSampleTextState = effectiveTtsPreviewSampleText(appContext)
    }

    override fun toggleFavoriteVoice(identifier: String) {
        if (identifier.isBlank()) return
        favoriteVoiceState = toggleSharedMobileTtsVoiceFavorite(favoriteVoiceState, identifier)
        saveTtsFavoriteVoices(appContext, favoriteVoiceState)
    }

    override fun setVoice(identifier: String?) {
        selectedVoiceIdentifier = identifier?.takeIf { it.isNotBlank() }
        appContext.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit().apply {
            selectedVoiceIdentifier?.let { putString(PREF_NATIVE_TTS_VOICE, it) }
                ?: remove(PREF_NATIVE_TTS_VOICE)
        }.apply()
    }

    /**
     * Fills [availableVoices] without speaking.
     *
     * The shared settings sheet enumerates the device voices when it opens, but the engine itself
     * must not: a reader or an AI dialog holding one would otherwise warm TextToSpeech for a sheet
     * nobody opened, which is the contract this adapter's construction keeps. OFFLINE builds cannot
     * use a network-backed voice, so those are hidden here and a saved pick that needs the network
     * is cleared for the host to persist through its own seam — Reader and Listen store the device
     * voice separately, so the adapter must not write the pref itself.
     */
    fun loadAvailableVoices(onSavedVoiceUnavailable: (String?) -> Unit = {}) {
        if (enumerationEngine != null) return
        enumerationEngine = createTextToSpeechOrNull(appContext) { status ->
            val engine = enumerationEngine ?: return@createTextToSpeechOrNull
            if (status != TextToSpeech.SUCCESS) return@createTextToSpeechOrNull
            availableVoices = engine.selectableVoices()
            val saved = selectedVoiceIdentifier
            if (BuildConfig.IS_OFFLINE && saved != null &&
                engine.voices.orEmpty().any { it.name == saved && it.isNetworkConnectionRequired }
            ) {
                // An OFFLINE build must not keep offering a voice it cannot speak with. This clears
                // the reader's key (the one this adapter owns) and reports the surviving selection
                // so the host can persist it through the prefs its surface owns instead.
                setVoice(null)
                onSavedVoiceUnavailable(selectedVoiceIdentifier)
            }
        }
    }

    override fun previewVoice(identifier: String?) {
        previewEngine?.shutdown()
        previewEngine = createTextToSpeechOrNull(appContext) { status ->
            val engine = previewEngine ?: return@createTextToSpeechOrNull
            if (status != TextToSpeech.SUCCESS) return@createTextToSpeechOrNull
            availableVoices = engine.selectableVoices()
            identifier?.let { id -> engine.voices?.firstOrNull { it.name == id } }?.let { engine.voice = it }
            engine.setSpeechRate(speechRate)
            engine.setPitch(speechPitch)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = releasePreviewEngine()
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) = releasePreviewEngine()
            })
            engine.speak(previewSampleText, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, PreviewId)
        }
    }

    override fun stop() {
        pendingPauseOnStart = false
        controller.stop()
        chunks = emptyList()
        progress = ReaderTtsProgress()
        isSessionActive = false
        // Clearing the session tag is what lets another surface claim the engine next.
        activePlaybackSource = null
        activeBookId = null
        activeTotalChapters = 0
        state = SharedMobileEpubLocalTtsState.IDLE
    }

    override fun release() {
        releasePreviewEngine()
        releaseEnumerationEngine()
        controller.release()
        scope.cancel()
    }

    private fun releasePreviewEngine() {
        val engine = previewEngine
        previewEngine = null
        engine?.shutdown()
    }

    private fun releaseEnumerationEngine() {
        val engine = enumerationEngine
        enumerationEngine = null
        engine?.shutdown()
    }

    private companion object {
        const val PreviewId = "shared-reader-preview"
    }
}

/** Device voices this build can offer, in display order. OFFLINE hides network-backed ones. */
private fun TextToSpeech.selectableVoices(): List<SharedMobileEpubVoice> =
    voices.orEmpty()
        .filter { !BuildConfig.IS_OFFLINE || !it.isNetworkConnectionRequired }
        .map { voice ->
            // Android benchmark (DeviceVoicesTab): the row headline is the locale's display name,
            // not the engine's `voice.name` ("en-us-x-sfg#female_1-local"), and the language filter
            // groups by display *language* ("English"). The shared row renders these as
            // "headline / subtitle" exactly like iOS does for its human-readable voice names.
            val locale = voice.locale
            val displayName = locale?.displayName?.takeIf { it.isNotBlank() }
                ?: runCatching { locale?.toLanguageTag() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: voice.name
            SharedMobileEpubVoice(
                identifier = voice.name,
                name = displayName,
                language = locale?.displayLanguage?.takeIf { it.isNotBlank() } ?: displayName,
                languageTag = runCatching { locale?.toLanguageTag().orEmpty() }.getOrDefault(""),
                quality = sharedMobileEpubVoiceQualityForAndroidQuality(voice.quality),
            )
        }
        .sortedForTtsDisplay()

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
