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
private const val PROCESS_TIMEOUT_MS = 15000L
private const val MAX_RETRY_ATTEMPTS = 3

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

class BaseTtsSynthesizer(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mutex = Mutex()

    private data class RequestContext(
        val resultDeferred: CompletableDeferred<Pair<File?, String?>>,
        val startSignal: CompletableDeferred<Unit>,
        val file: File,
        val text: String
    )

    private val requests = ConcurrentHashMap<String, RequestContext>()

    private val sharedListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Timber.d("BaseTts: onStart $utteranceId [Thread: ${Thread.currentThread().name}]")
            utteranceId?.let { id ->
                requests[id]?.startSignal?.complete(Unit)
            }
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { id ->
                val req = requests.remove(id)
                if (req != null) {
                    Timber.d("BaseTts: onDone $id. [Thread: ${Thread.currentThread().name}]")
                    req.resultDeferred.complete(Pair(req.file, req.text))
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
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    Timber.d("TextToSpeech engine initialized successfully.")
                    tts?.setOnUtteranceProgressListener(sharedListener)
                    if (continuation.isActive) continuation.resume(Unit)
                } else {
                    Timber.e("Failed to initialize TextToSpeech engine. Status: $status")
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("TTS initialization failed"))
                }
            }
        }
    }

    private suspend fun shutdownEngineLocked() {
        Timber.w("BaseTts: Shutting down TTS engine for recovery.")
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

    private fun applyPreferredVoice() {
        if (tts == null) return

        try {
            val preferredVoiceName = loadNativeVoice(context)
            if (!shouldResolveNativeTtsVoice(preferredVoiceName, BuildConfig.IS_OFFLINE)) {
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
            tts?.voice = targetVoice
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "BaseTts: Skipping optional voice selection due to low memory")
        } catch (e: Exception) {
            Timber.e(e, "BaseTts: Failed to apply preferred voice")
        }
    }

    suspend fun synthesizeToFile(text: String): Pair<File?, String?> {
        if (text.isBlank()) {
            return Pair(null, text)
        }

        return mutex.withLock {
            var result: Pair<File?, String?> = Pair(null, null)

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
                            if (attempt == MAX_RETRY_ATTEMPTS) return@withLock Pair(null, null)
                            delay(200)
                            continue
                        }
                    }

                    applyPreferredVoice()

                    tts?.setSpeechRate(loadTtsSpeechRate(context))
                    tts?.setPitch(loadTtsPitch(context))

                    Timber.d("BaseTts: Requesting synthesis (Attempt $attempt). ID: $utteranceId")

                    requests[utteranceId] = RequestContext(resultDeferred, startSignal, tempFile, text)

                    val ttsResult = tts?.synthesizeToFile(text, Bundle.EMPTY, tempFile, utteranceId)

                    if (ttsResult == TextToSpeech.ERROR) {
                        Timber.e("synthesizeToFile returned immediate ERROR for $utteranceId.")
                        requests.remove(utteranceId)
                        throw IllegalStateException("TTS Engine returned ERROR")
                    }

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
                    }

                    try {
                        val finalResult = withTimeout(PROCESS_TIMEOUT_MS) {
                            resultDeferred.await()
                        }

                        if (finalResult.first != null) {
                            result = finalResult
                            break
                        } else {
                            Timber.w("BaseTts: onError received during processing.")
                            throw IllegalStateException("TTS Engine reported onError")
                        }

                    } catch (_: TimeoutCancellationException) {
                        Timber.w("BaseTts: PROCESSING STUCK. onDone not received within ${PROCESS_TIMEOUT_MS}ms.")
                        throw IllegalStateException("Processing Timeout")
                    }

                } catch (e: Exception) {
                    Timber.w("BaseTts: Failure on attempt $attempt. Reason: ${e.message}")

                    tempFile.delete()
                    requests.remove(utteranceId)

                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        shutdownEngineLocked()
                    }
                }
            }

            result
        }
    }

    fun shutdown() {
        requests.clear()
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
        Timber.d("TextToSpeech engine shut down.")
    }

}
