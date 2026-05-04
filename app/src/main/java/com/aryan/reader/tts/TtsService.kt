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
import com.aryan.reader.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.loadAiByokSettings
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WordTimingInfo(val word: String, val startTime: Double)

data class TtsAudioData(
    val audioFile: File?,
    val serverText: String?,
    val wordTimings: List<WordTimingInfo>?,
    val error: String? = null,
    val streamUri: String? = null
)

data class PageCharacterRange(
    val pageInChapter: Int,
    val cfi: String,
    val startOffset: Int,
    val endOffset: Int
)

class ConcurrentInputStream : java.io.InputStream() {
    private val queue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
    private var currentBuffer: ByteArray? = null
    private var bufferPos = 0
    private var eofReached = false

    var isFinished = false
        private set

    var isClosed = false
        private set

    fun write(data: ByteArray) {
        if (!isClosed) queue.offer(data)
    }

    override fun read(): Int {
        val b = ByteArray(1)
        val readCount = read(b, 0, 1)
        return if (readCount == -1) -1 else b[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (eofReached) {
            isFinished = true
            return -1
        }
        if (len == 0) return 0

        if (currentBuffer == null || bufferPos >= currentBuffer!!.size) {
            try {
                // Blocks here safely until data arrives
                currentBuffer = queue.take()
                bufferPos = 0
                if (currentBuffer!!.isEmpty()) {
                    eofReached = true
                    isFinished = true
                    return -1
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return -1
            }
        }

        val available = currentBuffer!!.size - bufferPos
        val toCopy = len.coerceAtMost(available)
        System.arraycopy(currentBuffer!!, bufferPos, b, off, toCopy)
        bufferPos += toCopy
        return toCopy
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            queue.offer(ByteArray(0)) // Send EOF marker
        }
    }
}

object StreamRegistry {
    private val streams = java.util.concurrent.ConcurrentHashMap<String, java.io.InputStream>()
    private val totalBytesMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val finishedMap = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun register(id: String, stream: java.io.InputStream) {
        streams[id] = stream
        totalBytesMap[id] = 0L
        finishedMap[id] = false
    }
    fun get(id: String): java.io.InputStream? = streams[id]

    fun markFinished(id: String, totalBytes: Long) {
        totalBytesMap[id] = totalBytes
        finishedMap[id] = true
    }
    fun getStreamMetadata(id: String): Pair<Boolean, Long> {
        return (finishedMap[id] ?: false) to (totalBytesMap[id] ?: 0L)
    }

    fun remove(id: String) {
        streams.remove(id)?.let { try { it.close() } catch (_: Exception) {} }
        totalBytesMap.remove(id)
        finishedMap.remove(id)
    }
    fun clear() {
        streams.values.forEach { try { it.close() } catch (_: Exception) {} }
        streams.clear()
    }
}

@UnstableApi
class InputStreamDataSource : androidx.media3.datasource.BaseDataSource(true) {
    private var inputStream: java.io.InputStream? = null
    private var opened = false
    private var uri: android.net.Uri? = null
    private var bytesReadTotal: Long = 0

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        uri = dataSpec.uri
        Timber.tag("TTS_CLOUD_DIAG").d("InputStreamDataSource.open called for $uri, position=${dataSpec.position}")

        val streamId = uri?.host ?: uri?.lastPathSegment ?: throw java.io.IOException("No stream ID")
        val stream = StreamRegistry.get(streamId) ?: throw java.io.IOException("Stream not found")

        if (stream is ConcurrentInputStream && stream.isFinished) {
            Timber.tag("TTS_CLOUD_DIAG").d("InputStreamDataSource.open returning 0 bytes for finished stream to prevent retry.")
            opened = true
            transferInitializing(dataSpec)
            transferStarted(dataSpec)
            return 0
        }

        inputStream = stream
        opened = true
        transferInitializing(dataSpec)

        if (dataSpec.position > bytesReadTotal) {
            val toSkip = dataSpec.position - bytesReadTotal
            var skipped = 0L
            while (skipped < toSkip) {
                val s = inputStream?.skip(toSkip - skipped) ?: 0L
                if (s <= 0L) break
                skipped += s
            }
            bytesReadTotal += skipped
        }

        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        return try {
            val bytesRead = inputStream?.read(buffer, offset, length) ?: -1
            if (bytesRead == -1) {
                Timber.tag("TTS_CLOUD_DIAG").d("InputStreamDataSource EOF reached for $uri")
                return C.RESULT_END_OF_INPUT
            }
            bytesReadTotal += bytesRead
            bytesTransferred(bytesRead)
            bytesRead
        } catch (e: java.io.IOException) {
            Timber.tag("TTS_CLOUD_DIAG").e(e, "Stream read interrupted/broken for $uri")
            C.RESULT_END_OF_INPUT
        }
    }

    override fun getUri(): android.net.Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

@UnstableApi
class TtsService : MediaSessionService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var playbackManager: TtsPlaybackManager
    private lateinit var baseTtsSynthesizer: BaseTtsSynthesizer
    private lateinit var cacheManager: TtsCacheManager

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val playerState = if (::player.isInitialized) {
            "playbackState=${player.playbackState}, isPlaying=${player.isPlaying}, playWhenReady=${player.playWhenReady}, mediaItems=${player.mediaItemCount}, currentIndex=${player.currentMediaItemIndex}"
        } else {
            "player=uninitialized"
        }
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "onUpdateNotification called. startInForegroundRequired=$startInForegroundRequired, hasPostNotifications=$hasNotificationPermission, $playerState"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

            if (startInForegroundRequired) {
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("Notification permission missing while foreground is required. Calling stopSelf().")
                stopSelf()
            } else {
                Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("Notification permission missing. Skipping notification update.")
            }

            return
        }

        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("Delegating notification update to MediaSessionService.")
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private val okHttpClient = OkHttpClient.Builder().build()
    private val liveClient by lazy {
        GeminiLiveClient(okHttpClient) { errorMsg ->
            if (::playbackManager.isInitialized) {
                playbackManager.forceStopWithError(errorMsg)
            }
        }
    }

    class GeminiLiveClient(
        private val client: OkHttpClient,
        private val onAsyncError: (String) -> Unit = {}
    ) {
        private var webSocket: WebSocket? = null

        private val connectionMutex = Mutex()
        private val generationMutex = Mutex()
        private var clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private var audioChannel = Channel<GeminiWsEvent>(Channel.UNLIMITED)
        private var setupDeferred = CompletableDeferred<Boolean>().apply { complete(false) }

        var connectedSpeaker: String? = null

        sealed class GeminiWsEvent {
            data class Audio(val bytes: ByteArray) : GeminiWsEvent()
            object TurnComplete : GeminiWsEvent()
            data class Error(val message: String) : GeminiWsEvent()
        }

        suspend fun ensureConnected(
            serverUrl: String,
            speaker: String,
            authToken: String?,
            directGeminiApiKey: String? = null
        ) = connectionMutex.withLock {
            if (webSocket != null) {
                if (connectedSpeaker == speaker) {
                    val isSetup = try { setupDeferred.await() } catch(_: Exception) { false }
                    if (isSetup) return@withLock
                }
                Timber.tag("TTS_CLOUD_DIAG").d("Closing existing WS. Speaker changed or setup failed.")
                webSocket?.close(1000, "Reconnecting")
                webSocket = null
            }

            val url = if (!directGeminiApiKey.isNullOrBlank()) {
                "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$directGeminiApiKey"
            } else {
                val sanitizedUrl = serverUrl.removeSuffix("/")
                val wsUrlStr = sanitizedUrl.replace("https://", "wss://").replace("http://", "ws://")
                "$wsUrlStr/live?speaker=$speaker&token=${authToken ?: ""}"
            }

            Timber.tag("TTS_CLOUD_DIAG").d("Connecting to WS: ${if (!directGeminiApiKey.isNullOrBlank()) "Gemini BYOK" else url}")
            val request = Request.Builder().url(url).build()
            val connectedDeferred = CompletableDeferred<Boolean>()

            var connectionError: String? = null

            setupDeferred = CompletableDeferred()
            connectedSpeaker = speaker

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Timber.tag("TTS_CLOUD_DIAG").d("WS Opened. Sending Setup configuration to Gemini...")

                    val systemPrompt = """
                        You are a professional audiobook narrator. 
                        Your ONLY task is to read the exact text provided to you, word for word, neutral emotion, and with good pacing. 
                        Do NOT add any conversational filler, acknowledgments, or extra words (e.g., do not say "Sure, here is the text"). 
                        Do NOT skip any parts or summarize. Output ONLY the audio reading of the provided text. If you encounter unreadable, non-verbal, or non-linguistic content (e.g., symbols like "※▼◆", raw formatting markers, broken characters, or pure punctuation clusters with no readable words), silently skip it and continue reading.
                    """.trimIndent()

                    val setupMsg = JSONObject().apply {
                        put("setup", JSONObject().apply {
                            put("model", "models/$GEMINI_CLOUD_TTS_MODEL")
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

                    webSocket.send(setupMsg)
                    connectedDeferred.complete(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        if (json.has("error")) {
                            val errObj = json.opt("error")
                            val errMsg = if (errObj is JSONObject) errObj.toString() else errObj?.toString() ?: "Unknown API Error"
                            Timber.tag("TTS_CLOUD_DIAG").e("API ERROR RETURNED: $errMsg")
                            audioChannel.trySend(GeminiWsEvent.Error(errMsg))
                            setupDeferred.complete(false)
                            return
                        }
                        if (json.has("setupComplete")) {
                            setupDeferred.complete(true)
                        }

                        val serverContent = json.optJSONObject("serverContent")
                        if (serverContent != null) {
                            val turnComplete = serverContent.optBoolean("turnComplete", false)
                            val modelTurn = serverContent.optJSONObject("modelTurn")
                            val parts = modelTurn?.optJSONArray("parts")

                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val inlineData = part.optJSONObject("inlineData")
                                    if (inlineData != null) {
                                        val b64 = inlineData.optString("data")
                                        if (b64.isNotEmpty()) {
                                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                            audioChannel.trySend(GeminiWsEvent.Audio(bytes))
                                        }
                                    }
                                }
                            }

                            if (turnComplete) {
                                audioChannel.trySend(GeminiWsEvent.TurnComplete)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("TTS_CLOUD_DIAG").e(e, "Error parsing WS message text")
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    onMessage(webSocket, bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    connectionError = if (response?.code == 402) {
                        "INSUFFICIENT_CREDITS"
                    } else {
                        "WS Failure: ${t.message} | Response: ${response?.code}"
                    }
                    Timber.tag("TTS_CLOUD_DIAG").e(t)
                    audioChannel.trySend(GeminiWsEvent.Error(connectionError))
                    this@GeminiLiveClient.webSocket = null
                    connectedDeferred.complete(false)
                    setupDeferred.complete(false)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    audioChannel.trySend(GeminiWsEvent.Error("Connection Closed: $reason"))
                    this@GeminiLiveClient.webSocket = null
                    setupDeferred.complete(false)
                }
            })

            val isConnected = connectedDeferred.await()
            if (!isConnected) throw IllegalStateException(connectionError ?: "Failed to connect to proxy WebSocket")

            val isSetup = try {
                kotlinx.coroutines.withTimeout(10000L) { setupDeferred.await() }
            } catch (_: Exception) { false }

            if (!isSetup) {
                webSocket?.close(1000, "Setup failed")
                webSocket = null
                connectedSpeaker = null
                throw IllegalStateException("Failed to complete Gemini setup")
            } else {
                Timber.tag("TTS_CLOUD_DIAG").d("Gemini setup complete")
            }
        }

        fun generateChunk(text: String, cacheFile: File?): TtsAudioData {
            if (text.isBlank()) return TtsAudioData(null, null, null, "Text is blank")

            val streamId = java.util.UUID.randomUUID().toString()
            val concurrentStream = ConcurrentInputStream()
            StreamRegistry.register(streamId, concurrentStream)
            val header = createWavHeaderUnknownLength(24000)
            concurrentStream.write(header)

            clientScope.launch {
                generationMutex.withLock {
                    var fileOutputStream: java.io.FileOutputStream? = null
                    var tempFile: File? = null

                    try {
                        if (!isActive) return@launch

                        // Prepare cache temp file
                        if (cacheFile != null) {
                            tempFile = File(cacheFile.absolutePath + ".tmp")
                            fileOutputStream = java.io.FileOutputStream(tempFile)
                            fileOutputStream.write(header)
                        }

                        Timber.tag("TTS_CLOUD_DIAG").d("Starting API generation task for chunk: ${text.take(15)}...")

                        audioChannel = Channel(Channel.UNLIMITED)
                        val chunkGenStartTime = System.currentTimeMillis()
                        var firstByteTime = -1L

                        val payload = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("text", text)
                            })
                        }.toString()

                        val sent = webSocket?.send(payload) ?: false
                        if (!sent) {
                            Timber.tag("TTS_CLOUD_DIAG").e("Failed to send text payload over WS")
                            return@launch
                        }

                        var receivedAudioBytes = 0
                        kotlinx.coroutines.withTimeout(30000L) {
                            for (event in audioChannel) {
                                when (event) {
                                    is GeminiWsEvent.Audio -> {
                                        if (firstByteTime == -1L) {
                                            firstByteTime = System.currentTimeMillis()
                                            Timber.tag("TTS_CLOUD_DIAG").i("TTFB: ${firstByteTime - chunkGenStartTime}ms")
                                        }
                                        concurrentStream.write(event.bytes)
                                        fileOutputStream?.write(event.bytes)
                                        receivedAudioBytes += event.bytes.size
                                    }
                                    is GeminiWsEvent.TurnComplete -> {
                                        Timber.tag("TTS_CLOUD_DIAG").i("Chunk generation complete. Bytes: $receivedAudioBytes")
                                        StreamRegistry.markFinished(streamId, receivedAudioBytes.toLong() + 44)

                                        fileOutputStream?.close()
                                        fileOutputStream = null
                                        if (tempFile != null && cacheFile != null && receivedAudioBytes > 0) {
                                            patchWavHeader(tempFile, receivedAudioBytes)
                                            tempFile.renameTo(cacheFile)
                                            Timber.tag("TTS_CLOUD_DIAG").d("Successfully cached chunk to ${cacheFile.name}")
                                        }
                                        break
                                    }
                                    is GeminiWsEvent.Error -> {
                                        Timber.tag("TTS_CLOUD_DIAG").e("WS Error received: ${event.message}")
                                        onAsyncError(event.message)
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        Timber.tag("TTS_CLOUD_DIAG").e(e, "Timeout waiting for audio/TurnComplete")
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Timber.tag("TTS_CLOUD_DIAG").i(e, "Streaming job cancelled due to user skip/flush")
                    } catch (e: Exception) {
                        Timber.tag("TTS_CLOUD_DIAG").e(e, "Exception piping audio")
                    } finally {
                        Timber.tag("TTS_CLOUD_DIAG").d("Closing stream for ${text.take(15)}")
                        concurrentStream.close()
                        fileOutputStream?.close()
                        if (cacheFile != null && !cacheFile.exists()) {
                            tempFile?.delete()
                        }
                    }
                }
            }

            return TtsAudioData(null, text, emptyList(), streamUri = "ttsstream://$streamId")
        }

        fun close() {
            clientScope.cancel()
            clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            webSocket?.close(1000, "Context Reset")
            webSocket = null
            connectedSpeaker = null
            setupDeferred = CompletableDeferred<Boolean>().apply { complete(false) }
            StreamRegistry.clear()
        }
    }

    private val synthesizeBaseTtsChunk: suspend (String) -> TtsAudioData =
        { chunkToSpeak ->
            val (file, text) = baseTtsSynthesizer.synthesizeToFile(chunkToSpeak)
            TtsAudioData(file, text, null)
        }

    val audioGenerator: suspend (bookTitle: String, chapterTitle: String?, chunkIndex: Int, totalChunks: Int, text: String, speaker: String, mode: TtsMode, authToken: String?) -> TtsAudioData =
        { bookTitle, chapterTitle, chunkIndex, totalChunks, text, speaker, mode, authToken ->
            cacheManager.saveTotalChunks(bookTitle, chapterTitle, totalChunks)
            when (mode) {
                TtsMode.CLOUD -> {
                    val cachedFile = cacheManager.getCacheFile(bookTitle, chapterTitle, text, speaker, mode)

                    if (cachedFile.exists() && cachedFile.length() > 44) {
                        Timber.tag("TTS_CLOUD_DIAG").i("Using cached audio for chunk $chunkIndex")
                        TtsAudioData(audioFile = cachedFile, serverText = text, wordTimings = emptyList(), error = null, streamUri = null)
                    } else {
                        try {
                            val directGeminiApiKey = if (isByokCloudTtsAvailable(this@TtsService)) {
                                loadAiByokSettings(this@TtsService).geminiKey
                            } else {
                                null
                            }
                            if (directGeminiApiKey.isNullOrBlank() && googleCloudWorkerTtsUrl.isBlank()) {
                                TtsAudioData(audioFile = null, serverText = null, wordTimings = null, error = "Cloud TTS is not configured.")
                            } else {
                                liveClient.ensureConnected(googleCloudWorkerTtsUrl, speaker, authToken, directGeminiApiKey)
                                liveClient.generateChunk(text, cachedFile)
                            }
                        } catch (e: Exception) {
                            Timber.tag("TTS_CLOUD_DIAG").e(e, "Cloud TTS generation failed")
                            TtsAudioData(audioFile = null, serverText = null, wordTimings = null, error = e.message ?: "Failed to connect to TTS service")
                        }
                    }
                }
                TtsMode.BASE -> synthesizeBaseTtsChunk(text)
            }
        }

    override fun onCreate() {
        super.onCreate()
        Timber.d("TtsService created.")
        val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "TtsService onCreate. sdk=${Build.VERSION.SDK_INT}, hasPostNotifications=$hasNotificationPermission"
        )

        cacheManager = TtsCacheManager(this)

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

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this)
        val dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            object : androidx.media3.datasource.DataSource {
                private var dataSource: androidx.media3.datasource.DataSource? = null
                private val defaultDataSource = defaultDataSourceFactory.createDataSource()
                private val streamDataSource = InputStreamDataSource()

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    defaultDataSource.addTransferListener(transferListener)
                    streamDataSource.addTransferListener(transferListener)
                }

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    dataSource = if (dataSpec.uri.scheme == "ttsstream") {
                        streamDataSource
                    } else {
                        defaultDataSource
                    }
                    return dataSource!!.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return dataSource!!.read(buffer, offset, length)
                }

                override fun getUri(): android.net.Uri? = dataSource?.uri

                override fun close() {
                    dataSource?.close()
                }
            }
        }

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .build()
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("ExoPlayer created for TTS service.")

        playbackManager = TtsPlaybackManager(
            player = player,
            generateAudioChunk = audioGenerator,
            onResetContext = { liveClient.close() }
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(playbackManager)
            .build()

        mediaSession?.let { playbackManager.setMediaSession(it) }
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i("MediaSession created and attached to playback manager. sessionAvailable=${mediaSession != null}")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "onTaskRemoved. playWhenReady=${if (::player.isInitialized) player.playWhenReady else null}, isPlaying=${if (::player.isInitialized) player.isPlaying else null}"
        )
        if (!player.playWhenReady) {
            Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("Task removed while player is not playWhenReady. Calling stopSelf().")
            stopSelf()
        }
        Timber.d("onTaskRemoved called, stopping service.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).i(
            "onGetSession. package=${controllerInfo.packageName}, sessionAvailable=${mediaSession != null}"
        )
        return mediaSession
    }

    override fun onDestroy() {
        Timber.d("TtsService is being destroyed.")
        Timber.tag(TTS_NOTIFICATION_DIAG_TAG).w("TtsService onDestroy.")
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
