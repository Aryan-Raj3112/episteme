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
                    try {
                        val result = tts?.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Timber.e("Default language not supported/missing data")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error setting language")
                    }

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

            if (preferredVoiceName.isNullOrBlank()) {
                val defaultLocale = Locale.getDefault()
                try {
                    tts?.language = defaultLocale
                } catch (e: Exception) {
                    Timber.e(e, "BaseTts: Failed to restore default language")
                }

                val defaultVoice = try {
                    tts?.defaultVoice ?: tts?.voices?.firstOrNull { voice ->
                        voice.locale == defaultLocale && !voice.isNetworkConnectionRequired
                    } ?: tts?.voices?.firstOrNull { voice ->
                        voice.locale == defaultLocale
                    }
                } catch (e: Exception) {
                    Timber.e(e, "BaseTts: Failed to query default voice")
                    null
                }

                if (defaultVoice != null && tts?.voice?.name != defaultVoice.name) {
                    Timber.d("BaseTts: Restoring system default voice to ${defaultVoice.name} (${defaultVoice.locale})")
                    tts?.voice = defaultVoice
                } else {
                    Timber.d("BaseTts: Using engine default voice for locale $defaultLocale")
                }
                return
            }

            if (tts?.voice?.name == preferredVoiceName) return

            val availableVoices = tts?.voices
            if (availableVoices != null) {
                val targetVoice = availableVoices.find { it.name == preferredVoiceName }
                if (targetVoice != null) {
                    Timber.d("BaseTts: Setting preferred voice to ${targetVoice.name} (${targetVoice.locale})")
                    try {
                        tts?.language = targetVoice.locale
                    } catch (e: Exception) {
                        Timber.e(e, "BaseTts: Failed to set language for voice")
                    }
                    tts?.voice = targetVoice
                } else {
                    Timber.w("BaseTts: Preferred voice '$preferredVoiceName' not found in current engine.")
                }
            }
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
