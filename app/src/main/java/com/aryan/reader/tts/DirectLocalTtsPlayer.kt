package com.aryan.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.aryan.reader.shared.tts.createTextToSpeechOrNull
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aryan.reader.BuildConfig
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.loadNativeVoice
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

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
    private val listener: DirectLocalTtsListener,
    /**
     * Runs every framework `TextToSpeech` call. Those calls are synchronous binder transactions that
     * execute inline on the calling thread instead of being posted to a handler
     * (`TextToSpeech.runAction` -> `Connection.runAction`), so `stop()`/`speak()`/`shutdown()` on the
     * main thread sit in `BinderProxy.transact` for as long as the engine takes to answer and ANR when
     * it is busy or hung (docs/crashlytics-triage.md#213). A single thread also keeps `speak`, `stop`
     * and `shutdown` ordered exactly as the playback manager requested them.
     */
    private val engineExecutor: ExecutorService = newLocalTtsEngineExecutor(),
    private val engineFactory: (Context, (Int) -> Unit) -> TextToSpeech? = { ctx, onInit ->
        createTextToSpeechOrNull(ctx, onInit)
    },
) {
    private val appContext = context.applicationContext
    // Listener callbacks are delivered on the main thread: the playback manager keeps its chunk
    // queue, playback generation and resume offsets there, so the engine thread never calls into it.
    private val mainHandler = Handler(Looper.getMainLooper())
    // Engine state is confined to [engineExecutor].
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
        runOnEngineThread {
            if (queueMode == TextToSpeech.QUEUE_FLUSH) pending.clear()
            pending.addLast(PendingSpeak(text, utteranceId, rate, pitch, queueMode))
            if (!initialized) {
                initialize()
            } else {
                drainPending()
            }
        }
    }

    private fun initialize() {
        if (tts != null) return
        Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("engine-init-start")
        // The framework reports init on the main thread; everything it triggers is engine IPC, so it
        // is handed straight back to the engine thread.
        tts = engineFactory(appContext) { status ->
            runOnEngineThread { onEngineInitialized(status) }
        }
    }

    private fun onEngineInitialized(status: Int) {
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
            notifyError(utterance, status)
            tts = null
        }
    }

    private fun drainPending() {
        while (pending.isNotEmpty()) {
            val request = pending.removeFirst()
            val utterance = parseLocalTtsUtteranceId(request.utteranceId) ?: continue
            tts?.setSpeechRate(request.rate)
            tts?.setPitch(request.pitch)
            notifyReady(utterance)
            val result = tts?.speak(request.text, request.queueMode, Bundle.EMPTY, request.utteranceId)
                ?: TextToSpeech.ERROR
            Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i(
                "queued id=${request.utteranceId} result=$result mode=${request.queueMode} chars=${request.text.length} rate=${request.rate} pitch=${request.pitch}"
            )
            if (result == TextToSpeech.ERROR) notifyError(utterance, result)
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
        runOnEngineThread {
            pending.clear()
            tts?.stop()
        }
    }

    fun shutdown() = runOnEngineThread(::shutdownEngine)

    /** Terminal: shuts the engine down and lets the engine thread exit. */
    fun release() {
        runOnEngineThread(::shutdownEngine)
        engineExecutor.shutdown()
    }

    private fun shutdownEngine() {
        pending.clear()
        initialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("engine-shutdown")
    }

    private fun notifyReady(utterance: LocalTtsUtterance) {
        mainHandler.post { listener.onReady(utterance.generation, utterance.chunkIndex) }
    }

    private fun notifyError(utterance: LocalTtsUtterance?, errorCode: Int) {
        mainHandler.post { listener.onError(utterance, errorCode) }
    }

    private fun runOnEngineThread(block: () -> Unit) {
        try {
            engineExecutor.execute(block)
        } catch (_: RejectedExecutionException) {
            // Released: late engine work for a dead session is dropped instead of crashing.
        }
    }
}

private fun newLocalTtsEngineExecutor(): ExecutorService =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "local-tts-engine").apply { isDaemon = true }
    }
