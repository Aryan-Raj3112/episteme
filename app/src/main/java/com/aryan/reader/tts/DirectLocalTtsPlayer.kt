package com.aryan.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aryan.reader.BuildConfig
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.loadNativeVoice
import timber.log.Timber
import java.util.Locale

internal const val TTS_LOCAL_SPEAK_DIAG_TAG = "TTS_LOCAL_SPEAK_DIAG"

data class LocalTtsUtterance(
    val generation: Int,
    val chunkIndex: Int,
    val spokenStartOffset: Int
)

internal fun localTtsUtteranceId(generation: Int, chunkIndex: Int, spokenStartOffset: Int): String =
    "episteme-local:$generation:$chunkIndex:$spokenStartOffset"

internal fun parseLocalTtsUtteranceId(value: String?): LocalTtsUtterance? {
    val parts = value?.split(':') ?: return null
    if (parts.size != 4 || parts[0] != "episteme-local") return null
    return LocalTtsUtterance(
        generation = parts[1].toIntOrNull() ?: return null,
        chunkIndex = parts[2].toIntOrNull() ?: return null,
        spokenStartOffset = parts[3].toIntOrNull() ?: return null
    )
}

internal fun resolveLocalTtsSourceOffset(
    sourceStartOffset: Int,
    spokenStartOffset: Int,
    rangeStart: Int,
    visibleTextMatchesSpokenText: Boolean
): Int {
    if (sourceStartOffset < 0) return -1
    if (!visibleTextMatchesSpokenText) return sourceStartOffset
    return sourceStartOffset + spokenStartOffset.coerceAtLeast(0) + rangeStart.coerceAtLeast(0)
}

interface DirectLocalTtsListener {
    fun onReady(generation: Int, chunkIndex: Int)
    fun onStart(utterance: LocalTtsUtterance)
    fun onRangeStart(utterance: LocalTtsUtterance, start: Int, end: Int)
    fun onDone(utterance: LocalTtsUtterance)
    fun onError(utterance: LocalTtsUtterance?, errorCode: Int)
}

internal class DirectLocalTtsPlayer(
    context: Context,
    private val listener: DirectLocalTtsListener
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false
    private val pending = ArrayDeque<PendingSpeak>()

    private data class PendingSpeak(
        val text: String,
        val utteranceId: String,
        val rate: Float,
        val pitch: Float,
        val queueMode: Int
    )

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val utterance = parseLocalTtsUtteranceId(utteranceId) ?: return
            Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("start id=$utteranceId")
            listener.onStart(utterance)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val utterance = parseLocalTtsUtteranceId(utteranceId) ?: return
            listener.onRangeStart(utterance, start, end)
        }

        override fun onDone(utteranceId: String?) {
            val utterance = parseLocalTtsUtteranceId(utteranceId) ?: return
            Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("done id=$utteranceId")
            listener.onDone(utterance)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String?, errorCode: Int) {
            Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).e("error id=$utteranceId code=$errorCode")
            listener.onError(parseLocalTtsUtteranceId(utteranceId), errorCode)
        }
    }

    fun speak(text: String, utteranceId: String, rate: Float, pitch: Float, queueMode: Int) {
        if (queueMode == TextToSpeech.QUEUE_FLUSH) pending.clear()
        pending.addLast(PendingSpeak(text, utteranceId, rate, pitch, queueMode))
        if (!initialized) {
            initialize()
        } else {
            drainPending()
        }
    }

    private fun initialize() {
        if (tts != null) return
        Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("engine-init-start")
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                tts?.setOnUtteranceProgressListener(progressListener)
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                applyPreferredVoice()
                Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i(
                    "engine-init-success engine=${tts?.defaultEngine} voice=${tts?.voice?.name}"
                )
                drainPending()
            } else {
                Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).e("engine-init-failed status=$status")
                val utterance = parseLocalTtsUtteranceId(pending.firstOrNull()?.utteranceId)
                pending.clear()
                listener.onError(utterance, status)
                tts = null
            }
        }
    }

    private fun drainPending() {
        while (pending.isNotEmpty()) {
            val request = pending.removeFirst()
            val utterance = parseLocalTtsUtteranceId(request.utteranceId) ?: continue
            tts?.setSpeechRate(request.rate)
            tts?.setPitch(request.pitch)
            listener.onReady(utterance.generation, utterance.chunkIndex)
            val result = tts?.speak(request.text, request.queueMode, Bundle.EMPTY, request.utteranceId)
                ?: TextToSpeech.ERROR
            Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i(
                "queued id=${request.utteranceId} result=$result mode=${request.queueMode} chars=${request.text.length} rate=${request.rate} pitch=${request.pitch}"
            )
            if (result == TextToSpeech.ERROR) listener.onError(utterance, result)
        }
    }

    private fun applyPreferredVoice() {
        val engine = tts ?: return
        val preferred = loadNativeVoice(appContext)
        if (!shouldResolveNativeTtsVoice(preferred, BuildConfig.IS_OFFLINE)) return
        val voice = resolveNativeTtsVoiceForBuild(
            preferredVoiceName = preferred,
            defaultVoice = engine.defaultVoice,
            availableVoices = engine.voices,
            defaultLocale = Locale.getDefault(),
            isOfflineBuild = BuildConfig.IS_OFFLINE
        )
        if (voice != null) engine.voice = voice
    }

    fun stop() {
        pending.clear()
        tts?.stop()
    }

    fun shutdown() {
        pending.clear()
        initialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("engine-shutdown")
    }
}
