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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aryan.reader.tts.TtsPlaybackManager.TtsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WordTimingInfo(val word: String, val startTime: Double)
data class TtsAudioData(
    val audioFile: File?,
    val serverText: String?,
    val wordTimings: List<WordTimingInfo>?,
    val error: String? = null
)

data class PageCharacterRange(
    val pageInChapter: Int,
    val cfi: String,
    val startOffset: Int,
    val endOffset: Int
)

@UnstableApi
class TtsService : MediaSessionService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var playbackManager: TtsPlaybackManager
    private lateinit var baseTtsSynthesizer: BaseTtsSynthesizer

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

            if (startInForegroundRequired) {
                stopSelf()
            }

            return
        }

        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private val okHttpClient = OkHttpClient.Builder().build()
    private val liveClient by lazy { GeminiLiveClient(okHttpClient) }

    class GeminiLiveClient(private val client: OkHttpClient) {
        private var webSocket: WebSocket? = null
        private val mutex = Mutex()
        private var audioChannel = Channel<GeminiWsEvent>(Channel.UNLIMITED)
        private var setupDeferred = CompletableDeferred<Boolean>().apply { complete(false) }

        sealed class GeminiWsEvent {
            data class Audio(val bytes: ByteArray) : GeminiWsEvent()
            object TurnComplete : GeminiWsEvent()
            data class Error(val message: String) : GeminiWsEvent()
        }

        suspend fun ensureConnected(serverUrl: String, speaker: String, authToken: String?) {
            val connectStartTime = System.currentTimeMillis()
            if (webSocket != null) {
                val isSetup = setupDeferred.await()
                if (isSetup) return
            }

            val sanitizedUrl = serverUrl.removeSuffix("/")
            val wsUrlStr = sanitizedUrl.replace("https://", "wss://").replace("http://", "ws://")
            val url = "$wsUrlStr/live?speaker=$speaker&token=${authToken ?: ""}"

            Timber.tag("TTS_CLOUD_DIAG").d("Connecting to WS: $url")
            val request = Request.Builder().url(url).build()
            val connectedDeferred = CompletableDeferred<Boolean>()

            setupDeferred = CompletableDeferred<Boolean>()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Timber.tag("TTS_CLOUD_DIAG").d("WS Opened. Sending Setup configuration to Gemini...")

                    val systemPrompt = """
                        You are a professional audiobook narrator. 
                        Your ONLY task is to read the exact text provided to you, word for word, with perfect pacing, natural emotion, and clarity. 
                        Do NOT add any conversational filler, acknowledgments, or extra words (e.g., do not say "Sure, here is the text"). 
                        Do NOT skip any parts or summarize. Output ONLY the audio reading of the provided text.
                    """.trimIndent()

                    val setupMsg = JSONObject().apply {
                        put("setup", JSONObject().apply {
                            put("model", "models/gemini-3.1-flash-live-preview")
                            put("systemInstruction", JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", systemPrompt)
                                    })
                                })
                            })
                            put("generationConfig", JSONObject().apply {
                                put("responseModalities", org.json.JSONArray().apply { put("AUDIO") })
                                put("speechConfig", JSONObject().apply {
                                    put("voiceConfig", JSONObject().apply {
                                        put("prebuiltVoiceConfig", JSONObject().apply {
                                            put("voiceName", speaker)
                                        })
                                    })
                                })
                            })
                        })
                    }.toString()

                    Timber.tag("TTS_CLOUD_DIAG").d("Sending Setup Configuration: $setupMsg")
                    webSocket.send(setupMsg)
                    connectedDeferred.complete(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        if (json.has("error")) {
                            Timber.tag("TTS_CLOUD_DIAG").e("API ERROR RETURNED: ${json.optJSONObject("error")}")
                            setupDeferred.complete(false)
                            return
                        }
                        if (json.has("setupComplete")) {
                            Timber.tag("TTS_CLOUD_DIAG").i("Received setupComplete from Gemini!")
                            setupDeferred.complete(true)
                        }

                        val serverContent = json.optJSONObject("serverContent")
                        if (serverContent != null) {

                            // Log if Gemini interrupted itself or stopped early
                            if (serverContent.optBoolean("interrupted", false)) {
                                Timber.tag("TTS_CLOUD_DIAG").w("Gemini interrupted the response!")
                            }

                            val turnComplete = serverContent.optBoolean("turnComplete", false)
                            val modelTurn = serverContent.optJSONObject("modelTurn")
                            val parts = modelTurn?.optJSONArray("parts")

                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)

                                    // Add log for text response so we know if it fell back to Text-Only
                                    if (part.has("text")) {
                                        Timber.tag("TTS_CLOUD_DIAG").i("Received Text Response: ${part.optString("text")}")
                                    }

                                    val inlineData = part.optJSONObject("inlineData")
                                    if (inlineData != null) {
                                        val b64 = inlineData.optString("data")
                                        if (b64.isNotEmpty()) {
                                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                            Timber.tag("TTS_CLOUD_DIAG").d("Decoded audio chunk: ${bytes.size} bytes")
                                            audioChannel.trySend(GeminiWsEvent.Audio(bytes))
                                        }
                                    }
                                }
                            }

                            if (turnComplete) {
                                Timber.tag("TTS_CLOUD_DIAG").d("Received TurnComplete signal")
                                audioChannel.trySend(GeminiWsEvent.TurnComplete)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("TTS_CLOUD_DIAG").e(e, "Error parsing WS message text")
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    val decodedText = bytes.utf8()
                    onMessage(webSocket, decodedText)
                }

                fun onResponse(webSocket: WebSocket, response: Response) {
                    Timber.tag("TTS_CLOUD_DIAG").d("WS HTTP Response Code: ${response.code}, Message: ${response.message}")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val errorMsg = "WS Failure: ${t.message} | Response: ${response?.code}"
                    Timber.tag("TTS_CLOUD_DIAG").e(t, errorMsg)
                    audioChannel.trySend(GeminiWsEvent.Error(errorMsg))
                    this@GeminiLiveClient.webSocket = null
                    connectedDeferred.complete(false)
                    setupDeferred.complete(false)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.tag("TTS_CLOUD_DIAG").w("WS Closed. Code: $code, Reason: $reason")
                    audioChannel.trySend(GeminiWsEvent.Error("Connection Closed: $reason"))
                    this@GeminiLiveClient.webSocket = null
                    setupDeferred.complete(false)
                }
            })

            val isConnected = connectedDeferred.await()
            if (!isConnected) {
                throw IllegalStateException("Failed to connect to proxy WebSocket")
            }

            val isSetup = try {
                kotlinx.coroutines.withTimeout(10000L) {
                    setupDeferred.await()
                }
            } catch (e: Exception) {
                false
            }

            if (!isSetup) {
                Timber.tag("TTS_CLOUD_DIAG").e("Setup failed or timed out (never received setupComplete)")
                webSocket?.close(1000, "Setup failed")
                webSocket = null
                throw IllegalStateException("Failed to complete Gemini setup")
            } else {
                Timber.tag("TTS_CLOUD_DIAG").i("WS Connection & Setup complete in ${System.currentTimeMillis() - connectStartTime}ms")
            }
        }

        suspend fun generateChunk(context: android.content.Context, text: String): TtsAudioData = mutex.withLock {
            if (text.isBlank()) return TtsAudioData(null, null, null, "Text is blank")

            audioChannel = Channel(Channel.UNLIMITED)
            val chunkGenStartTime = System.currentTimeMillis()
            var firstByteTime = -1L

            val payload = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("text", text)
                })
            }.toString()

            Timber.tag("TTS_CLOUD_DIAG").d("Sending Text Payload: $payload")
            val sent = webSocket?.send(payload) ?: false

            if (!sent) {
                Timber.tag("TTS_CLOUD_DIAG").e("Failed to send text payload over WS")
                return TtsAudioData(null, null, null, "WebSocket send failure")
            }

            val accumulatedBytes = java.io.ByteArrayOutputStream()
            var error: String? = null

            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    for (event in audioChannel) {
                        when (event) {
                            is GeminiWsEvent.Audio -> {
                                if (firstByteTime == -1L) {
                                    firstByteTime = System.currentTimeMillis()
                                    Timber.tag("TTS_CLOUD_DIAG").i("TTFB (Time to First Byte): ${firstByteTime - chunkGenStartTime}ms")
                                }
                                accumulatedBytes.write(event.bytes)
                            }
                            is GeminiWsEvent.TurnComplete -> {
                                Timber.tag("TTS_CLOUD_DIAG").i("Chunk generation complete. Total time: ${System.currentTimeMillis() - chunkGenStartTime}ms")
                                break
                            }
                            is GeminiWsEvent.Error -> {
                                error = event.message
                                break
                            }
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                error = "Timeout waiting for Gemini response"
                Timber.tag("TTS_CLOUD_DIAG").e("Timeout: No audio or TurnComplete received within 20s")
            }

            if (error != null) return TtsAudioData(null, null, null, error)
            if (accumulatedBytes.size() == 0) {
                Timber.tag("TTS_CLOUD_DIAG").w("Accumulated bytes is 0")
                return TtsAudioData(null, null, null, "No audio received")
            }

            Timber.tag("TTS_CLOUD_DIAG").d("Total Audio Bytes Accumulated: ${accumulatedBytes.size()}")
            val finalWavBytes = addWavHeader(accumulatedBytes.toByteArray(), 24000)
            val tempFile = File.createTempFile("tts_live_", ".wav", context.cacheDir).apply {
                writeBytes(finalWavBytes)
            }

            return TtsAudioData(tempFile, text, emptyList())
        }

        fun close() {
            webSocket?.close(1000, "Context Reset")
            webSocket = null
            setupDeferred = CompletableDeferred<Boolean>().apply { complete(false) }
        }
    }

    private val synthesizeBaseTtsChunk: suspend (String) -> TtsAudioData =
        { chunkToSpeak ->
            val (file, text) = baseTtsSynthesizer.synthesizeToFile(chunkToSpeak)
            TtsAudioData(file, text, null)
        }

    private val audioGenerator: suspend (bookTitle: String, text: String, speaker: String, mode: TtsMode, authToken: String?) -> TtsAudioData =
        { _, text, speaker, mode, authToken ->
            when (mode) {
                TtsMode.CLOUD -> {
                    try {
                        liveClient.ensureConnected(googleCloudWorkerTtsUrl, speaker, authToken)
                        liveClient.generateChunk(applicationContext, text)
                    } catch (e: Exception) {
                        Timber.e(e, "Cloud TTS generation failed")
                        TtsAudioData(audioFile = null, serverText = null, wordTimings = null, error = e.message ?: "Failed to connect to TTS service")
                    }
                }
                TtsMode.BASE -> synthesizeBaseTtsChunk(text)
            }
        }

    override fun onCreate() {
        super.onCreate()
        Timber.d("TtsService created.")

        baseTtsSynthesizer = BaseTtsSynthesizer(this)
        scope.launch {
            try {
                baseTtsSynthesizer.initialize()
            } catch (e: Exception) {
                Timber.e(e, "Base TTS synthesizer failed to initialize")
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        playbackManager = TtsPlaybackManager(
            player = player,
            generateAudioChunk = audioGenerator,
            onResetContext = { liveClient.close() }
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(playbackManager)
            .build()

        mediaSession?.let { playbackManager.setMediaSession(it) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) {
            stopSelf()
        }
        Timber.d("onTaskRemoved called, stopping service.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        Timber.d("TtsService is being destroyed.")
        baseTtsSynthesizer.shutdown()
        playbackManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}