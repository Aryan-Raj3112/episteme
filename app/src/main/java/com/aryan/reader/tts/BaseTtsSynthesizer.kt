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

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.aryan.reader.BuildConfig
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.loadNativeVoice
import timber.log.Timber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay

private const val START_TIMEOUT_FAST_MS = 3000L
private const val START_TIMEOUT_RETRY_MS = 4000L
private const val PROCESS_IDLE_TIMEOUT_MS = 15000L
private const val PROCESS_MAX_TIMEOUT_MS = 60000L
private const val MAX_RETRY_ATTEMPTS = 3
internal const val TTS_LOCAL_DIAG_TAG = "TTS_LOCAL_DIAG"

internal fun resolveNativeTtsVoiceForBuild(
    preferredVoiceName: String?,
    defaultVoice: Voice?,
    availableVoices: Collection<Voice>?,
    defaultLocale: Locale,
    isOfflineBuild: Boolean
): Voice? {
    val voices = availableVoices.orEmpty()
    val preferredVoice = preferredVoiceName
        ?.takeIf { it.isNotBlank() }
        ?.let { name -> voices.firstOrNull { it.name == name } }

    if (preferredVoice != null && (!isOfflineBuild || !preferredVoice.isNetworkConnectionRequired)) {
        return preferredVoice
    }

    val localeOfflineVoice = voices.firstOrNull { voice ->
        voice.locale == defaultLocale && !voice.isNetworkConnectionRequired
    }
    val anyOfflineVoice = voices.firstOrNull { voice -> !voice.isNetworkConnectionRequired }

    return if (isOfflineBuild) {
        localeOfflineVoice
            ?: anyOfflineVoice
            ?: defaultVoice?.takeUnless { it.isNetworkConnectionRequired }
    } else {
        defaultVoice
            ?: localeOfflineVoice
            ?: voices.firstOrNull { voice -> voice.locale == defaultLocale }
    }
}

internal fun shouldResolveNativeTtsVoice(
    preferredVoiceName: String?,
    isOfflineBuild: Boolean
): Boolean {
    return isOfflineBuild || !preferredVoiceName.isNullOrBlank()
}

internal fun shouldTimeoutNativeTtsProcessing(
    requestElapsedMs: Long,
    idleElapsedMs: Long,
    maxTimeoutMs: Long = PROCESS_MAX_TIMEOUT_MS,
    idleTimeoutMs: Long = PROCESS_IDLE_TIMEOUT_MS
): Boolean {
    return requestElapsedMs >= maxTimeoutMs || idleElapsedMs >= idleTimeoutMs
}

class BaseTtsSynthesizer(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mutex = Mutex()

    private data class RequestContext(
        val resultDeferred: CompletableDeferred<Pair<File?, String?>>,
        val startSignal: CompletableDeferred<Unit>,
        val file: File,
        val text: String,
        val attempt: Int,
        val requestStartedAtMs: Long,
        var synthesisStartedAtMs: Long? = null,
        var lastAudioAvailableAtMs: Long? = null,
        var audioChunkCount: Int = 0,
        var audioByteCount: Long = 0L
    )

    private val requests = ConcurrentHashMap<String, RequestContext>()

    private val sharedListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Timber.d("BaseTts: onStart $utteranceId [Thread: ${Thread.currentThread().name}]")
            utteranceId?.let { id ->
                requests[id]?.let { req ->
                    val now = System.currentTimeMillis()
                    req.synthesisStartedAtMs = now
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        "utterance-start id=$id attempt=${req.attempt} queuedMs=${now - req.requestStartedAtMs} " +
                            "textChars=${req.text.length} thread=${Thread.currentThread().name}"
                    )
                    req.startSignal.complete(Unit)
                }
            }
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { id ->
                val req = requests.remove(id)
                if (req != null) {
                    Timber.d("BaseTts: onDone $id. [Thread: ${Thread.currentThread().name}]")
                    val now = System.currentTimeMillis()
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        "utterance-done id=$id attempt=${req.attempt} totalMs=${now - req.requestStartedAtMs} " +
                            "synthesisMs=${req.synthesisStartedAtMs?.let { now - it }} fileBytes=${req.file.length()} " +
                            "audioChunks=${req.audioChunkCount} audioBytes=${req.audioByteCount}"
                    )
                    req.resultDeferred.complete(Pair(req.file, req.text))
                }
            }
        }

        override fun onBeginSynthesis(
            utteranceId: String?,
            sampleRateInHz: Int,
            audioFormat: Int,
            channelCount: Int
        ) {
            utteranceId?.let { id ->
                requests[id]?.let { req ->
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        "utterance-begin-synthesis id=$id attempt=${req.attempt} " +
                            "queuedMs=${System.currentTimeMillis() - req.requestStartedAtMs} " +
                            "sampleRate=$sampleRateInHz audioFormat=$audioFormat channels=$channelCount"
                    )
                }
            }
        }

        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
            utteranceId?.let { id ->
                requests[id]?.let { req ->
                    val bytes = audio?.size ?: 0
                    req.audioChunkCount += 1
                    req.audioByteCount += bytes.toLong()
                    req.lastAudioAvailableAtMs = System.currentTimeMillis()
                    Timber.tag(TTS_LOCAL_DIAG_TAG).d(
                        "utterance-audio-chunk id=$id attempt=${req.attempt} " +
                            "chunk=${req.audioChunkCount} bytes=$bytes totalBytes=${req.audioByteCount} " +
                            "elapsedMs=${System.currentTimeMillis() - req.requestStartedAtMs}"
                    )
                }
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            onError(utteranceId, -1)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Timber.e("BaseTts: onError $utteranceId code=$errorCode [Thread: ${Thread.currentThread().name}]")
            utteranceId?.let { id ->
                val req = requests.remove(id)
                if (req != null) {
                    Timber.tag(TTS_LOCAL_DIAG_TAG).e(
                        "utterance-error id=$id attempt=${req.attempt} code=$errorCode " +
                            "elapsedMs=${System.currentTimeMillis() - req.requestStartedAtMs} " +
                            "textChars=${req.text.length} audioChunks=${req.audioChunkCount} audioBytes=${req.audioByteCount}"
                    )
                }
                req?.resultDeferred?.complete(Pair(null, null))
            }
        }
    }

    suspend fun initialize() {
        mutex.withLock {
            if (!isInitialized) {
                initializeEngineLocked()
            }
        }
    }

    private suspend fun initializeEngineLocked() {
        if (isInitialized) return
        return suspendCancellableCoroutine { continuation ->
            Timber.d("BaseTts: Initializing TextToSpeech engine...")
            Timber.tag(TTS_LOCAL_DIAG_TAG).i("engine-init-start")
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    Timber.d("TextToSpeech engine initialized successfully.")
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        "engine-init-success defaultEngine=${tts?.defaultEngine} defaultVoice=${tts?.defaultVoice?.name}"
                    )
                    tts?.setOnUtteranceProgressListener(sharedListener)
                    if (continuation.isActive) continuation.resume(Unit)
                } else {
                    Timber.e("Failed to initialize TextToSpeech engine. Status: $status")
                    Timber.tag(TTS_LOCAL_DIAG_TAG).e("engine-init-failed status=$status")
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("TTS initialization failed"))
                }
            }
        }
    }

    private suspend fun shutdownEngineLocked() {
        Timber.w("BaseTts: Shutting down TTS engine for recovery.")
        Timber.tag(TTS_LOCAL_DIAG_TAG).w("engine-shutdown-for-recovery pendingRequests=${requests.size}")
        try {
            requests.clear()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Timber.e(e, "Error shutting down TTS")
        } finally {
            tts = null
            isInitialized = false
            delay(350)
        }
    }

    private suspend fun stopEngineForRetryLocked() {
        Timber.w("BaseTts: Stopping current TTS utterance before retry.")
        Timber.tag(TTS_LOCAL_DIAG_TAG).w("engine-stop-for-retry pendingRequests=${requests.size}")
        try {
            tts?.stop()
        } catch (e: Exception) {
            Timber.e(e, "BaseTts: Failed to stop TTS during retry recovery")
        } finally {
            delay(350)
        }
    }

    private fun applyPreferredVoice() {
        if (tts == null) return

        try {
            val preferredVoiceName = loadNativeVoice(context)
            if (!shouldResolveNativeTtsVoice(preferredVoiceName, BuildConfig.IS_OFFLINE)) {
                Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                    "voice-selection-skip preferredVoiceSet=false offline=${BuildConfig.IS_OFFLINE} " +
                        "defaultEngine=${tts?.defaultEngine} defaultVoice=${tts?.defaultVoice?.name}"
                )
                return
            }
            val defaultLocale = Locale.getDefault()
            val defaultVoice = tts?.defaultVoice
            val availableVoices = tts?.voices
            val targetVoice = resolveNativeTtsVoiceForBuild(
                preferredVoiceName = preferredVoiceName,
                defaultVoice = defaultVoice,
                availableVoices = availableVoices,
                defaultLocale = defaultLocale,
                isOfflineBuild = BuildConfig.IS_OFFLINE
            )

            if (targetVoice == null) {
                Timber.w("BaseTts: No suitable local voice found for locale $defaultLocale.")
                Timber.tag(TTS_LOCAL_DIAG_TAG).w(
                    "voice-selection-none preferredVoice=$preferredVoiceName locale=$defaultLocale " +
                        "voices=${availableVoices?.size ?: 0} offline=${BuildConfig.IS_OFFLINE}"
                )
                return
            }

            if (
                !preferredVoiceName.isNullOrBlank() &&
                targetVoice.name != preferredVoiceName &&
                BuildConfig.IS_OFFLINE
            ) {
                Timber.w("BaseTts: Saved voice '$preferredVoiceName' requires network or is unavailable in offline build. Using ${targetVoice.name}.")
            }

            Timber.d("BaseTts: Setting native voice to ${targetVoice.name} (${targetVoice.locale})")
            Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                "voice-selection-applied voice=${targetVoice.name} locale=${targetVoice.locale} " +
                    "networkRequired=${targetVoice.isNetworkConnectionRequired} preferredVoice=$preferredVoiceName " +
                    "defaultEngine=${tts?.defaultEngine}"
            )
            tts?.voice = targetVoice
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "BaseTts: Skipping optional voice selection due to low memory")
            Timber.tag(TTS_LOCAL_DIAG_TAG).e(e, "voice-selection-oom")
        } catch (e: Exception) {
            Timber.e(e, "BaseTts: Failed to apply preferred voice")
            Timber.tag(TTS_LOCAL_DIAG_TAG).e(e, "voice-selection-failed")
        }
    }

    suspend fun synthesizeToFile(text: String): Pair<File?, String?> {
        if (text.isBlank()) {
            Timber.tag(TTS_LOCAL_DIAG_TAG).w("synthesize-skip-blank")
            return Pair(null, text)
        }

        return mutex.withLock {
            var result: Pair<File?, String?> = Pair(null, null)
            Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                "synthesize-start textChars=${text.length} maxAttempts=$MAX_RETRY_ATTEMPTS"
            )

            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                val utteranceId = UUID.randomUUID().toString()
                val tempFile = File.createTempFile("base_tts_", ".wav", context.cacheDir)

                val resultDeferred = CompletableDeferred<Pair<File?, String?>>()
                val startSignal = CompletableDeferred<Unit>()

                try {
                    if (!isInitialized) {
                        try {
                            initializeEngineLocked()
                        } catch (e: Exception) {
                            Timber.e(e, "BaseTts: Init failed on attempt $attempt")
                            Timber.tag(TTS_LOCAL_DIAG_TAG).e(e, "synthesize-init-failed attempt=$attempt id=$utteranceId")
                            if (attempt == MAX_RETRY_ATTEMPTS) return@withLock Pair(null, null)
                            delay(200)
                            continue
                        }
                    }

                    applyPreferredVoice()

                    tts?.setSpeechRate(loadTtsSpeechRate(context))
                    tts?.setPitch(loadTtsPitch(context))

                    Timber.d("BaseTts: Requesting synthesis (Attempt $attempt). ID: $utteranceId")
                    val requestStartedAtMs = System.currentTimeMillis()
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        "synthesize-request attempt=$attempt id=$utteranceId textChars=${text.length} " +
                            "speechRate=${loadTtsSpeechRate(context)} pitch=${loadTtsPitch(context)} " +
                            "engine=${tts?.defaultEngine} voice=${tts?.voice?.name} file=${tempFile.name}"
                    )

                    requests[utteranceId] = RequestContext(
                        resultDeferred = resultDeferred,
                        startSignal = startSignal,
                        file = tempFile,
                        text = text,
                        attempt = attempt,
                        requestStartedAtMs = requestStartedAtMs
                    )

                    val ttsResult = tts?.synthesizeToFile(text, Bundle.EMPTY, tempFile, utteranceId)

                    if (ttsResult == TextToSpeech.ERROR) {
                        Timber.e("synthesizeToFile returned immediate ERROR for $utteranceId.")
                        Timber.tag(TTS_LOCAL_DIAG_TAG).e(
                            "synthesize-immediate-error attempt=$attempt id=$utteranceId " +
                                "elapsedMs=${System.currentTimeMillis() - requestStartedAtMs}"
                        )
                        requests.remove(utteranceId)
                        throw IllegalStateException("TTS Engine returned ERROR")
                    }
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        "synthesize-queued attempt=$attempt id=$utteranceId result=$ttsResult " +
                            "elapsedMs=${System.currentTimeMillis() - requestStartedAtMs}"
                    )

                    val startTimeout = if (attempt == 1) START_TIMEOUT_FAST_MS else START_TIMEOUT_RETRY_MS

                    try {
                        withTimeout(startTimeout) {
                            select {
                                startSignal.onAwait { }
                                resultDeferred.onAwait { }
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        Timber.w(
                            "BaseTts: onStart not received within ${startTimeout}ms for $utteranceId. " +
                                "Continuing to wait for onDone because some engines omit or delay onStart for file synthesis."
                        )
                        Timber.tag(TTS_LOCAL_DIAG_TAG).w(
                            "synthesize-start-timeout attempt=$attempt id=$utteranceId timeoutMs=$startTimeout " +
                                "pendingRequests=${requests.size}"
                        )
                    }

                    var finalResult: Pair<File?, String?>? = null
                    var lastObservedFileBytes = tempFile.length()
                    var lastFileProgressAtMs = requestStartedAtMs
                    while (finalResult == null) {
                        val now = System.currentTimeMillis()
                        val req = requests[utteranceId]
                        val currentFileBytes = tempFile.length()
                        if (currentFileBytes > lastObservedFileBytes) {
                            lastObservedFileBytes = currentFileBytes
                            lastFileProgressAtMs = now
                        }
                        val lastProgressAt = listOfNotNull(
                            req?.lastAudioAvailableAtMs,
                            req?.synthesisStartedAtMs,
                            lastFileProgressAtMs,
                            requestStartedAtMs
                        ).maxOrNull() ?: requestStartedAtMs
                        val requestElapsedMs = now - requestStartedAtMs
                        val idleElapsedMs = now - lastProgressAt

                        if (shouldTimeoutNativeTtsProcessing(requestElapsedMs, idleElapsedMs)) {
                            Timber.w(
                                "BaseTts: PROCESSING STUCK. No progress for ${idleElapsedMs}ms " +
                                    "after ${requestElapsedMs}ms total."
                            )
                            Timber.tag(TTS_LOCAL_DIAG_TAG).w(
                                "synthesize-process-timeout attempt=$attempt id=$utteranceId " +
                                    "idleTimeoutMs=$PROCESS_IDLE_TIMEOUT_MS maxTimeoutMs=$PROCESS_MAX_TIMEOUT_MS " +
                                    "idleElapsedMs=$idleElapsedMs totalMs=$requestElapsedMs pendingRequests=${requests.size} " +
                                    "fileBytes=${tempFile.length()} audioChunks=${req?.audioChunkCount ?: -1} " +
                                    "audioBytes=${req?.audioByteCount ?: -1}"
                            )
                            throw IllegalStateException("Processing Timeout")
                        }

                        val waitMs = minOf(
                            PROCESS_IDLE_TIMEOUT_MS - idleElapsedMs,
                            PROCESS_MAX_TIMEOUT_MS - requestElapsedMs
                        ).coerceAtLeast(250L)
                        finalResult = withTimeoutOrNull(waitMs) {
                            resultDeferred.await()
                        }
                    }

                    val completedResult = finalResult ?: throw IllegalStateException("TTS Engine did not complete")
                    if (completedResult.first != null) {
                        Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                            "synthesize-success attempt=$attempt id=$utteranceId " +
                                "totalMs=${System.currentTimeMillis() - requestStartedAtMs} " +
                                "fileBytes=${completedResult.first?.length() ?: -1}"
                        )
                        result = completedResult
                        break
                    } else {
                        Timber.w("BaseTts: onError received during processing.")
                        throw IllegalStateException("TTS Engine reported onError")
                    }

                } catch (e: kotlinx.coroutines.CancellationException) {
                    Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                        e,
                        "synthesize-cancelled attempt=$attempt id=$utteranceId tempFileBytes=${tempFile.length()}"
                    )
                    tempFile.delete()
                    requests.remove(utteranceId)
                    throw e
                } catch (e: Exception) {
                    Timber.w("BaseTts: Failure on attempt $attempt. Reason: ${e.message}")
                    Timber.tag(TTS_LOCAL_DIAG_TAG).w(
                        e,
                        "synthesize-attempt-failed attempt=$attempt id=$utteranceId tempFileBytes=${tempFile.length()}"
                    )

                    tempFile.delete()
                    requests.remove(utteranceId)

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        stopEngineForRetryLocked()
                    }
                }
            }

            Timber.tag(TTS_LOCAL_DIAG_TAG).i(
                "synthesize-finish success=${result.first != null} fileBytes=${result.first?.length() ?: -1}"
            )
            result
        }
    }

    fun shutdown() {
        Timber.tag(TTS_LOCAL_DIAG_TAG).i("engine-shutdown pendingRequests=${requests.size}")
        requests.clear()
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        Timber.d("TextToSpeech engine shut down.")
    }

}
