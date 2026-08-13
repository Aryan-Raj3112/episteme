package com.aryan.reader.shared.ui

import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import java.util.Locale

@Composable
internal actual fun rememberSharedMobileEpubLocalTts(): SharedMobileEpubLocalTts {
    val context = rememberAndroidSharedMobileContextForTts()
    val factory = SharedAndroidMobileEpubTtsRegistry.factory
    val controller = remember(context.applicationContext, factory) {
        factory?.create(context.applicationContext)
            ?: AndroidSharedMobileEpubLocalTts(context.applicationContext)
    }
    DisposableEffect(controller) { onDispose(controller::release) }
    return controller
}

fun interface SharedAndroidMobileEpubTtsFactory {
    fun create(context: android.content.Context): SharedMobileEpubLocalTts
}

fun installSharedAndroidMobileEpubTtsFactory(factory: SharedAndroidMobileEpubTtsFactory?) {
    SharedAndroidMobileEpubTtsRegistry.factory = factory
}

private object SharedAndroidMobileEpubTtsRegistry {
    @Volatile
    var factory: SharedAndroidMobileEpubTtsFactory? = null
}

@Composable
private fun rememberAndroidSharedMobileContextForTts() =
    androidx.compose.ui.platform.LocalContext.current.also {
        // Ensure URL/share/image platform helpers have the same application context.
        registerSharedAndroidMobileApplicationContext(it.applicationContext)
    }

private class AndroidSharedMobileEpubLocalTts(
    private val context: android.content.Context,
) : SharedMobileEpubLocalTts {
    private val preferences = context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var released = false
    private var generation = 0L
    private var chunks: List<ReaderTtsChunk> = emptyList()
    private var currentChunkIndex = -1
    private var wantsPlayback = false
    private var pendingStart = false

    override var state by mutableStateOf(SharedMobileEpubLocalTtsState.IDLE)
        private set
    override var isSessionActive by mutableStateOf(false)
        private set
    override var progress by mutableStateOf(ReaderTtsProgress())
        private set
    override var speechRate by mutableStateOf(preferences.getFloat(RateKey, 1f).coerceIn(0.5f, 3f))
        private set
    override var speechPitch by mutableStateOf(preferences.getFloat(PitchKey, 1f).coerceIn(0.5f, 2f))
        private set
    override var availableVoices by mutableStateOf(emptyList<SharedMobileEpubVoice>())
        private set
    override var selectedVoiceIdentifier by mutableStateOf(preferences.getString(VoiceKey, null))
        private set
    override var errorMessage by mutableStateOf<String?>(null)
        private set
    override var completionCount by mutableStateOf(0L)
        private set

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (isCurrent(utteranceId) && wantsPlayback) state = SharedMobileEpubLocalTtsState.SPEAKING
        }

        override fun onDone(utteranceId: String?) {
            if (!isCurrent(utteranceId)) return
            currentChunkIndex += 1
            speakCurrent()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (!isCurrent(utteranceId)) return
            errorMessage = "Text-to-speech failed ($errorCode)"
            state = SharedMobileEpubLocalTtsState.IDLE
        }
    }

    override fun prepare() {
        if (released) return
        isSessionActive = true
        ensureEngine()
    }

    override fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean,
    ) {
        val readable = chunks.filter { it.spokenText.isNotBlank() }
        if (readable.isEmpty() || released) return
        generation += 1
        engine?.stop()
        this.chunks = readable
        currentChunkIndex = startChunkIndex.coerceIn(0, readable.lastIndex)
        wantsPlayback = playWhenReady
        errorMessage = null
        isSessionActive = true
        pendingStart = true
        updateProgress()
        ensureEngine()
        if (initialized) speakCurrent()
    }

    override fun pause() {
        if (!isSessionActive) return
        wantsPlayback = false
        generation += 1
        engine?.stop()
        state = SharedMobileEpubLocalTtsState.PAUSED
    }

    override fun resume() {
        if (!isSessionActive || chunks.isEmpty()) return
        wantsPlayback = true
        generation += 1
        pendingStart = true
        ensureEngine()
        if (initialized) speakCurrent()
    }

    override fun skipPrevious() = moveBy(-1)
    override fun skipNext() = moveBy(1)

    private fun moveBy(offset: Int) {
        if (chunks.isEmpty()) return
        val target = (currentChunkIndex + offset).coerceIn(0, chunks.lastIndex)
        if (target == currentChunkIndex) return
        generation += 1
        engine?.stop()
        currentChunkIndex = target
        updateProgress()
        pendingStart = true
        if (initialized) speakCurrent()
    }

    override fun setSpeechParameters(rate: Float, pitch: Float) {
        speechRate = rate.coerceIn(0.5f, 3f)
        speechPitch = pitch.coerceIn(0.5f, 2f)
        preferences.edit().putFloat(RateKey, speechRate).putFloat(PitchKey, speechPitch).apply()
        if (isSessionActive) restartCurrent()
    }

    override fun setVoice(identifier: String?) {
        selectedVoiceIdentifier = identifier?.takeIf { candidate ->
            availableVoices.any { it.identifier == candidate }
        }
        preferences.edit().apply {
            selectedVoiceIdentifier?.let { putString(VoiceKey, it) } ?: remove(VoiceKey)
        }.apply()
        applyVoice()
        if (isSessionActive) restartCurrent()
    }

    override fun previewVoice(identifier: String?) {
        if (!initialized) {
            ensureEngine()
            return
        }
        identifier?.let { id -> engine?.voices?.firstOrNull { it.name == id } }?.let { engine?.voice = it }
        engine?.setSpeechRate(speechRate)
        engine?.setPitch(speechPitch)
        engine?.speak(PreviewText, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, PreviewUtteranceId)
    }

    override fun stop() {
        generation += 1
        engine?.stop()
        chunks = emptyList()
        currentChunkIndex = -1
        wantsPlayback = false
        pendingStart = false
        isSessionActive = false
        progress = ReaderTtsProgress()
        state = SharedMobileEpubLocalTtsState.IDLE
        errorMessage = null
    }

    override fun release() {
        if (released) return
        stop()
        released = true
        initialized = false
        engine?.shutdown()
        engine = null
    }

    private fun restartCurrent() {
        generation += 1
        engine?.stop()
        pendingStart = true
        if (initialized) speakCurrent()
    }

    private fun ensureEngine() {
        if (released || engine != null) return
        engine = TextToSpeech(context) { status ->
            if (released) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                engine?.setOnUtteranceProgressListener(listener)
                engine?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                refreshVoices()
                applyVoice()
                if (pendingStart) speakCurrent()
            } else {
                errorMessage = "Text-to-speech initialization failed ($status)"
                state = SharedMobileEpubLocalTtsState.IDLE
                engine = null
            }
        }
    }

    private fun refreshVoices() {
        availableVoices = engine?.voices.orEmpty()
            .map { SharedMobileEpubVoice(it.name, it.name, it.locale?.displayName.orEmpty()) }
            .sortedWith(compareBy(SharedMobileEpubVoice::language, SharedMobileEpubVoice::name))
        if (selectedVoiceIdentifier !in availableVoices.map { it.identifier }.toSet()) {
            selectedVoiceIdentifier = null
        }
    }

    private fun applyVoice() {
        val id = selectedVoiceIdentifier ?: return
        engine?.voices?.firstOrNull { it.name == id }?.let { engine?.voice = it }
    }

    private fun speakCurrent() {
        pendingStart = false
        val chunk = chunks.getOrNull(currentChunkIndex)
        if (chunk == null) {
            chunks = emptyList()
            currentChunkIndex = -1
            progress = ReaderTtsProgress()
            state = SharedMobileEpubLocalTtsState.IDLE
            isSessionActive = false
            completionCount += 1
            return
        }
        updateProgress()
        if (!wantsPlayback) {
            state = SharedMobileEpubLocalTtsState.PAUSED
            return
        }
        val tts = engine ?: run {
            pendingStart = true
            ensureEngine()
            return
        }
        tts.setSpeechRate(speechRate)
        tts.setPitch(speechPitch)
        applyVoice()
        state = SharedMobileEpubLocalTtsState.SPEAKING
        val result = tts.speak(
            chunk.spokenText,
            TextToSpeech.QUEUE_FLUSH,
            Bundle.EMPTY,
            utteranceId(generation, currentChunkIndex),
        )
        if (result == TextToSpeech.ERROR) {
            errorMessage = "Text-to-speech could not queue this passage"
            state = SharedMobileEpubLocalTtsState.IDLE
        }
    }

    private fun updateProgress() {
        progress = ReaderTtsProgress(
            sessionId = generation,
            chunks = chunks,
            currentChunkIndex = currentChunkIndex,
        )
    }

    private fun isCurrent(id: String?): Boolean = id == utteranceId(generation, currentChunkIndex)

    private fun utteranceId(generation: Long, chunkIndex: Int) = "shared:$generation:$chunkIndex"

    private companion object {
        const val RateKey = "reader.tts.speechRate"
        const val PitchKey = "reader.tts.pitch"
        const val VoiceKey = "reader.tts.voiceIdentifier"
        const val PreviewUtteranceId = "shared:preview"
        const val PreviewText = "This is a sample of the selected reading voice."
    }
}
