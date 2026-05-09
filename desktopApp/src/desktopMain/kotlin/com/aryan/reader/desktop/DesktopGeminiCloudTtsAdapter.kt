package com.aryan.reader.desktop

import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsFileCacheManager
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.TtsAdapter
import com.aryan.reader.shared.createReaderTtsWavHeaderUnknownLength
import com.aryan.reader.shared.patchReaderTtsWavHeader
import com.aryan.reader.shared.splitReaderTextIntoTtsChunks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private data class DesktopTtsSequenceChunk(
    val text: String,
    val chapterTitle: String?
)

class DesktopGeminiCloudTtsAdapter(
    private val settingsProvider: () -> ReaderAiByokSettings,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val cacheManager: ReaderTtsFileCacheManager = ReaderTtsFileCacheManager(defaultDesktopTtsCacheRoot())
) : TtsAdapter {
    @Volatile
    private var activeLine: SourceDataLine? = null

    @Volatile
    private var activeWebSocket: WebSocket? = null

    @Volatile
    private var activePlayer: DesktopStreamingPcmPlayer? = null

    override val isAvailable: Boolean
        get() = settingsProvider().sanitized().isCloudTtsAvailable

    override suspend fun speak(text: String) {
        val trimmed = text.trim()
        logDesktopTts("speak_start textChars=${trimmed.length}")
        if (trimmed.isBlank()) return
        speakSequence(splitReaderTextIntoTtsChunks(trimmed).ifEmpty { listOf(trimmed.take(5_000)) })
        logDesktopTts("speak_finished")
    }

    suspend fun speakSequence(
        texts: List<String>,
        onChunkStart: suspend (Int) -> Unit = {}
    ) {
        val normalizedChunks = texts
            .flatMap { text -> splitReaderTextIntoTtsChunks(text).ifEmpty { listOf(text.trim()) } }
            .map { text -> DesktopTtsSequenceChunk(text = text.trim().take(5_000), chapterTitle = null) }
            .filter { it.text.isNotBlank() }
        logDesktopTts(
            "sequence_speak_start chunks=${normalizedChunks.size} totalTextChars=${normalizedChunks.sumOf { it.text.length }}"
        )
        if (normalizedChunks.isEmpty()) return
        val callbackContext = coroutineContext
        stop()
        streamSequence("Desktop selection", normalizedChunks, callbackContext, onChunkStart)
        logDesktopTts("sequence_speak_finished chunks=${normalizedChunks.size}")
    }

    suspend fun speakChunks(
        bookTitle: String,
        readScope: ReaderTtsReadScope,
        chunks: List<ReaderTtsChunk>,
        onChunkStart: suspend (Int) -> Unit = {}
    ) {
        val sequenceChunks = chunks
            .map { chunk ->
                DesktopTtsSequenceChunk(
                    text = chunk.spokenText.trim().ifBlank { chunk.text.trim() }.take(5_000),
                    chapterTitle = chunk.chapterTitle.ifBlank { readScope.label }
                )
            }
            .filter { it.text.isNotBlank() }
        logDesktopTts(
            "chunk_sequence_speak_start book=\"${bookTitle.desktopTtsPreview()}\" scope=${readScope.name} " +
                "chunks=${sequenceChunks.size} totalTextChars=${sequenceChunks.sumOf { it.text.length }}"
        )
        if (sequenceChunks.isEmpty()) return
        val callbackContext = coroutineContext
        stop()
        streamSequence(bookTitle.ifBlank { "Untitled" }, sequenceChunks, callbackContext, onChunkStart)
        logDesktopTts("chunk_sequence_speak_finished chunks=${sequenceChunks.size}")
    }

    override suspend fun pause() {
        withContext(Dispatchers.IO) {
            activePlayer?.pause()
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.IO) {
            activePlayer?.resume()
        }
    }

    fun cacheSummary(bookTitle: String, speakerId: String? = settingsProvider().sanitized().ttsSpeakerId): ReaderTtsCacheSummary {
        return cacheManager.getCacheSummary(bookTitle.ifBlank { "Untitled" }, speakerId)
    }

    fun clearBookCacheForSpeaker(bookTitle: String, speakerId: String = settingsProvider().sanitized().ttsSpeakerId) {
        cacheManager.clearBookCacheForSpeaker(bookTitle.ifBlank { "Untitled" }, speakerId)
    }

    fun clearBookCache(bookTitle: String) {
        cacheManager.clearBookCache(bookTitle.ifBlank { "Untitled" })
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            logDesktopTts("stop_requested hasWebSocket=${activeWebSocket != null} hasLine=${activeLine != null}")
            runCatching { activeWebSocket?.abort() }
            activeWebSocket = null
            runCatching { activePlayer?.closeNow() }
            activePlayer = null
            runCatching { activeLine?.stop() }
            runCatching { activeLine?.flush() }
            runCatching { activeLine?.close() }
            activeLine = null
            logDesktopTts("stop_complete")
        }
    }

    private suspend fun streamSequence(
        bookTitle: String,
        chunks: List<DesktopTtsSequenceChunk>,
        callbackContext: CoroutineContext,
        onChunkStart: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val settings = settingsProvider().sanitized()
        val totalTextChars = chunks.sumOf { it.text.length }
        logDesktopTts(
            "stream_start book=\"${bookTitle.desktopTtsPreview()}\" chunks=${chunks.size} totalTextChars=$totalTextChars keyPresent=${settings.geminiKey.isNotBlank()} " +
                "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" speaker=\"${settings.ttsSpeakerId.desktopTtsPreview()}\" " +
                "available=${settings.isCloudTtsAvailable}"
        )
        if (!settings.isCloudTtsAvailable) {
            logDesktopTts("stream_blocked reason=not_available")
            throw IllegalStateException("Cloud TTS needs a saved Gemini key and the Gemini cloud TTS model selected.")
        }

        val audioBytesReceived = AtomicLong(0)
        val currentTurnAudioBytesReceived = AtomicLong(0)
        val player = DesktopStreamingPcmPlayer { activeLine = it }
        activePlayer = player
        val setupComplete = CompletableDeferred<Unit>()
        val currentTurnComplete = AtomicReference<CompletableDeferred<Unit>?>(null)
        val activeCacheOutput = AtomicReference<FileOutputStream?>(null)
        val failure = CompletableDeferred<Throwable>()
        val messageBuffer = StringBuilder()
        var webSocket: WebSocket? = null
        var activeTempCacheFile: File? = null

        fun handleMessage(message: String) {
            handleGeminiTtsMessage(
                message = message,
                setupComplete = setupComplete,
                turnComplete = currentTurnComplete.get(),
                failure = failure,
                onAudioPart = { bytes ->
                    audioBytesReceived.addAndGet(bytes.size.toLong())
                    currentTurnAudioBytesReceived.addAndGet(bytes.size.toLong())
                    activeCacheOutput.get()?.let { output ->
                        runCatching { output.write(bytes) }
                            .onFailure { error ->
                                logDesktopTts("cache_write_failed error=\"${error.desktopTtsSummary()}\"")
                                failure.complete(error)
                            }
                    }
                    runCatching { player.write(bytes) }
                        .onFailure { error ->
                            logDesktopTts("stream_audio_write_failed error=\"${error.desktopTtsSummary()}\"")
                            failure.complete(error)
                        }
                }
            )
        }

        val listener = object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                activeWebSocket = webSocket
                webSocket.request(1)
                logDesktopTts("ws_open send_setup model=\"$GEMINI_CLOUD_TTS_MODEL\" speaker=\"${settings.ttsSpeakerId.desktopTtsPreview()}\"")
                webSocket.sendText(buildGeminiTtsSetup(settings.ttsSpeakerId), true)
                    .whenComplete { _, error ->
                        if (error != null) {
                            logDesktopTts("ws_setup_send_failed error=\"${error.desktopTtsSummary()}\"")
                            failure.complete(error)
                        } else {
                            logDesktopTts("ws_setup_send_complete")
                        }
                    }
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                messageBuffer.append(data)
                logDesktopTts("ws_message_text chunkChars=${data.length} last=$last bufferChars=${messageBuffer.length}")
                if (last) {
                    val message = messageBuffer.toString()
                    messageBuffer.clear()
                    handleMessage(message)
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> {
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                messageBuffer.append(bytes.decodeToString())
                logDesktopTts("ws_message_binary chunkBytes=${bytes.size} last=$last bufferChars=${messageBuffer.length}")
                if (last) {
                    val message = messageBuffer.toString()
                    messageBuffer.clear()
                    handleMessage(message)
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                logDesktopTts("ws_error error=\"${error.desktopTtsSummary()}\"")
                failure.complete(error)
            }

            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                val activeTurn = currentTurnComplete.get()
                logDesktopTts(
                    "ws_close status=$statusCode reason=\"${reason.desktopTtsPreview()}\" " +
                        "setupComplete=${setupComplete.isCompleted} turnComplete=${activeTurn?.isCompleted}"
                )
                if (!setupComplete.isCompleted && !failure.isCompleted) {
                    failure.complete(IllegalStateException("Cloud TTS connection closed before setup: $reason"))
                } else if (activeTurn != null && !activeTurn.isCompleted && !failure.isCompleted) {
                    failure.complete(IllegalStateException("Cloud TTS connection closed: $reason"))
                }
                return CompletableFuture.completedFuture(null)
            }
        }

        suspend fun ensureWebSocket(): WebSocket {
            webSocket?.let { return it }
            val encodedKey = URLEncoder.encode(settings.geminiKey, Charsets.UTF_8.name())
            val uri = URI("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$encodedKey")
            logDesktopTts("ws_connect_start endpoint=GeminiLive keyChars=${settings.geminiKey.length}")
            val connectedWebSocket = runCatching {
                httpClient.newWebSocketBuilder()
                    .buildAsync(uri, listener)
                    .get(15, TimeUnit.SECONDS)
            }.getOrElse { error ->
                logDesktopTts("ws_connect_failed error=\"${error.desktopTtsSummary()}\"")
                throw error
            }
            activeWebSocket = connectedWebSocket
            webSocket = connectedWebSocket
            logDesktopTts("ws_connect_complete")

            logDesktopTts("setup_wait_start timeoutMs=15000")
            withTimeout(15_000) {
                select<Unit> {
                    setupComplete.onAwait { }
                    failure.onAwait { throw it }
                }
            }
            logDesktopTts("setup_wait_complete")
            return connectedWebSocket
        }

        try {
            val totalChunksByChapter = chunks.groupingBy { it.chapterTitle }.eachCount()
            chunks.forEach { chunk ->
                cacheManager.saveTotalChunks(
                    bookTitle = bookTitle,
                    chapterTitle = chunk.chapterTitle,
                    totalChunks = totalChunksByChapter[chunk.chapterTitle] ?: chunks.size
                )
            }
            chunks.forEachIndexed { index, chunk ->
                val text = chunk.text
                val turnComplete = CompletableDeferred<Unit>()
                currentTurnAudioBytesReceived.set(0)
                currentTurnComplete.set(turnComplete)
                logDesktopTts("sequence_turn_start index=${index + 1}/${chunks.size} textChars=${text.length}")
                withContext(callbackContext) {
                    onChunkStart(index)
                }

                val cacheFile = cacheManager.getCacheFile(bookTitle, chunk.chapterTitle, text, settings.ttsSpeakerId)
                if (cacheFile.exists() && cacheFile.length() > 44) {
                    logDesktopTts(
                        "cache_hit index=${index + 1}/${chunks.size} bytes=${cacheFile.length()} " +
                            "file=\"${cacheFile.absolutePath.desktopTtsPreview(220)}\""
                    )
                    val cachedBytes = playCachedWav(cacheFile, player)
                    currentTurnAudioBytesReceived.set(cachedBytes)
                    audioBytesReceived.addAndGet(cachedBytes)
                    logDesktopTts("cache_play_complete index=${index + 1}/${chunks.size} audioBytes=$cachedBytes")
                    currentTurnComplete.compareAndSet(turnComplete, null)
                    return@forEachIndexed
                }

                val socket = ensureWebSocket()
                val tempCacheFile = File(cacheFile.absolutePath + ".tmp")
                activeTempCacheFile = tempCacheFile
                runCatching {
                    tempCacheFile.parentFile?.mkdirs()
                    FileOutputStream(tempCacheFile).also { output ->
                        output.write(createReaderTtsWavHeaderUnknownLength(24_000))
                        activeCacheOutput.set(output)
                    }
                }.onFailure { error ->
                    activeCacheOutput.set(null)
                    tempCacheFile.delete()
                    logDesktopTts("cache_prepare_failed index=${index + 1}/${chunks.size} error=\"${error.desktopTtsSummary()}\"")
                }

                try {
                    logDesktopTts("text_send_start index=${index + 1}/${chunks.size} textChars=${text.length}")
                    runCatching { socket.sendText(buildGeminiTtsTextInput(text), true).join() }
                        .onFailure { error ->
                            logDesktopTts("text_send_failed index=${index + 1}/${chunks.size} error=\"${error.desktopTtsSummary()}\"")
                            throw error
                        }
                    logDesktopTts("text_send_complete index=${index + 1}/${chunks.size}")

                    val turnTimeoutMs = (30_000L + text.length * 80L).coerceIn(60_000L, 600_000L)
                    logDesktopTts("turn_wait_start index=${index + 1}/${chunks.size} timeoutMs=$turnTimeoutMs")
                    withTimeout(turnTimeoutMs) {
                        select<Unit> {
                            turnComplete.onAwait { }
                            failure.onAwait { throw it }
                        }
                    }
                    val turnAudioBytes = currentTurnAudioBytesReceived.get()
                    logDesktopTts(
                        "turn_wait_complete index=${index + 1}/${chunks.size} " +
                            "turnAudioBytes=$turnAudioBytes totalAudioBytes=${audioBytesReceived.get()}"
                    )
                    if (turnAudioBytes == 0L) {
                        logDesktopTts("stream_failed reason=empty_turn_audio index=${index + 1}/${chunks.size}")
                        throw IllegalStateException("Cloud TTS returned no audio for a text chunk.")
                    }
                    activeCacheOutput.getAndSet(null)?.close()
                    runCatching {
                        patchReaderTtsWavHeader(tempCacheFile, turnAudioBytes.toInt())
                        if (cacheFile.exists()) cacheFile.delete()
                        if (!tempCacheFile.renameTo(cacheFile)) {
                            throw IllegalStateException("Could not move temp cache file into place.")
                        }
                    }.onSuccess {
                        logDesktopTts(
                            "cache_store_complete index=${index + 1}/${chunks.size} bytes=${cacheFile.length()} " +
                                "file=\"${cacheFile.absolutePath.desktopTtsPreview(220)}\""
                        )
                    }.onFailure { error ->
                        tempCacheFile.delete()
                        logDesktopTts("cache_store_failed index=${index + 1}/${chunks.size} error=\"${error.desktopTtsSummary()}\"")
                    }
                    activeTempCacheFile = null
                } finally {
                    activeCacheOutput.getAndSet(null)?.let { output ->
                        runCatching { output.close() }
                    }
                }
                currentTurnComplete.compareAndSet(turnComplete, null)
            }

            if (audioBytesReceived.get() == 0L) {
                logDesktopTts("stream_failed reason=empty_audio")
                throw IllegalStateException("Cloud TTS returned no audio.")
            }
            player.drainAndClose()
            webSocket?.let { socket -> runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join() } }
            activeWebSocket = null
            activePlayer = null
            logDesktopTts("stream_complete chunks=${chunks.size} audioBytes=${audioBytesReceived.get()}")
        } catch (error: Throwable) {
            currentTurnComplete.set(null)
            activeCacheOutput.getAndSet(null)?.let { output -> runCatching { output.close() } }
            activeTempCacheFile?.delete()
            activeTempCacheFile = null
            runCatching { webSocket?.abort() }
            activeWebSocket = null
            activePlayer = null
            player.closeNow()
            throw error
        }
    }
}

private suspend fun playCachedWav(file: File, player: DesktopStreamingPcmPlayer): Long {
    var totalBytes = 0L
    file.inputStream().use { input ->
        var skipped = 0L
        while (skipped < 44L) {
            val next = input.skip(44L - skipped)
            if (next <= 0L) break
            skipped += next
        }
        val buffer = ByteArray(8192)
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read <= 0) break
            player.write(buffer.copyOf(read))
            totalBytes += read
        }
    }
    return totalBytes
}

private fun defaultDesktopTtsCacheRoot(): File {
    val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
    return File(baseDir, "Episteme/TTS_Cache")
}

private fun buildGeminiTtsSetup(speakerId: String): String {
    val systemPrompt = """
        You are a professional audiobook narrator.
        Read the exact text provided, word for word, with neutral emotion and good pacing.
        Do not add conversational filler, acknowledgments, extra words, summaries, or commentary.
        Skip non-verbal symbols or formatting noise that cannot be read naturally.
    """.trimIndent()
    return buildJsonObject {
        put(
            "setup",
            buildJsonObject {
                put("model", JsonPrimitive("models/$GEMINI_CLOUD_TTS_MODEL"))
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(systemPrompt)) })
                        })
                    }
                )
                put(
                    "generationConfig",
                    buildJsonObject {
                        put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                        put(
                            "speechConfig",
                            buildJsonObject {
                                put(
                                    "voiceConfig",
                                    buildJsonObject {
                                        put(
                                            "prebuiltVoiceConfig",
                                            buildJsonObject { put("voiceName", JsonPrimitive(speakerId)) }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }.toString()
}

private fun buildGeminiTtsTextInput(text: String): String {
    return buildJsonObject {
        put(
            "realtimeInput",
            buildJsonObject {
                put("text", JsonPrimitive(text))
            }
        )
    }.toString()
}

private fun handleGeminiTtsMessage(
    message: String,
    setupComplete: CompletableDeferred<Unit>,
    turnComplete: CompletableDeferred<Unit>?,
    failure: CompletableDeferred<Throwable>,
    onAudioPart: (ByteArray) -> Unit
) {
    logDesktopTts("message_handle chars=${message.length} preview=\"${message.desktopTtsPreview()}\"")
    val json = runCatching { DesktopGeminiTtsJson.parseToJsonElement(message).jsonObject }.getOrElse { error ->
        logDesktopTts("message_parse_failed error=\"${error.desktopTtsSummary()}\"")
        return
    }
    json["error"]?.let { error ->
        logDesktopTts("message_provider_error body=\"${error.toString().desktopTtsPreview(300)}\"")
        failure.complete(IllegalStateException(error.toString()))
        return
    }
    if (json.containsKey("setupComplete") || json.containsKey("setup_complete")) {
        logDesktopTts("message_setup_complete")
        setupComplete.complete(Unit)
    }

    val serverContent = json.jsonObjectValue("serverContent", "server_content") ?: return
    val modelTurn = serverContent.jsonObjectValue("modelTurn", "model_turn")
    val parts = modelTurn?.get("parts")?.jsonArray
    parts?.forEach { part ->
        val inlineData = part.jsonObjectOrNull()?.jsonObjectValue("inlineData", "inline_data")
        val encoded = inlineData?.get("data")?.jsonPrimitive?.contentOrNull
        if (!encoded.isNullOrBlank()) {
            val decoded = Base64.getMimeDecoder().decode(encoded)
            onAudioPart(decoded)
            logDesktopTts("message_audio_part bytes=${decoded.size}")
        }
    }
    if (serverContent.booleanValue("turnComplete", "turn_complete")) {
        logDesktopTts("message_turn_complete")
        turnComplete?.complete(Unit)
    }
}

private val DesktopGeminiTtsJson = Json { ignoreUnknownKeys = true }

private fun JsonObject.jsonObjectValue(vararg keys: String): JsonObject? {
    return keys.firstNotNullOfOrNull { key -> get(key) as? JsonObject }
}

private fun JsonObject.booleanValue(vararg keys: String): Boolean {
    return keys.any { key -> get(key)?.jsonPrimitive?.booleanOrNull == true }
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun ByteArray.upsample16BitMonoLe2x(): ByteArray {
    if (size < 2) return this
    val sampleCount = size / 2
    val output = ByteArray(sampleCount * 4)
    var outputIndex = 0
    fun sampleAt(index: Int): Int {
        val byteIndex = index * 2
        val lo = this[byteIndex].toInt() and 0xFF
        val hi = this[byteIndex + 1].toInt()
        return (hi shl 8) or lo
    }
    fun writeSample(sample: Int) {
        output[outputIndex] = (sample and 0xFF).toByte()
        output[outputIndex + 1] = ((sample shr 8) and 0xFF).toByte()
        outputIndex += 2
    }
    for (index in 0 until sampleCount) {
        val current = sampleAt(index)
        val next = sampleAt((index + 1).coerceAtMost(sampleCount - 1))
        writeSample(current)
        writeSample(((current + next) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
    }
    return output
}

private class DesktopStreamingPcmPlayer(
    private val onLineChanged: (SourceDataLine?) -> Unit
) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val stateLock = java.lang.Object()
    private var line: SourceDataLine? = null
    private var fallbackTo48Khz = true
    @Volatile
    private var closed = false
    @Volatile
    private var paused = false
    private var bytesWritten = 0L

    init {
        logDesktopTts("play_stream_start mixers=\"${availableAudioMixers().desktopTtsPreview(260)}\"")
    }

    fun pause() {
        synchronized(stateLock) {
            if (closed || paused) return
            paused = true
            runCatching { line?.stop() }
            logDesktopTts("play_stream_paused totalWritten=$bytesWritten")
        }
    }

    fun resume() {
        synchronized(stateLock) {
            if (closed || !paused) return
            paused = false
            runCatching { line?.start() }
            stateLock.notifyAll()
            logDesktopTts("play_stream_resumed totalWritten=$bytesWritten")
        }
    }

    fun write(pcm24Khz: ByteArray) {
        if (closed || pcm24Khz.isEmpty()) return
        waitIfPaused()
        val activeLine = synchronized(stateLock) {
            if (closed) return
            line ?: openBestLine()
        }
        val bytes = if (fallbackTo48Khz) pcm24Khz.upsample16BitMonoLe2x() else pcm24Khz
        var offset = 0
        var lineStarted = activeLine.isRunning
        val primeTargetBytes = (activeLine.bufferSize / 2).coerceAtLeast(8192)
        while (offset < bytes.size && !closed) {
            waitIfPaused()
            val maxWrite = if (lineStarted) 8192 else primeTargetBytes
            val written = activeLine.write(bytes, offset, (bytes.size - offset).coerceAtMost(maxWrite))
            if (written <= 0) break
            offset += written
            bytesWritten += written
            if (!lineStarted && (offset >= bytes.size || offset >= primeTargetBytes)) {
                activeLine.start()
                lineStarted = true
                logDesktopTts("play_line_started_after_prime primeBytes=$offset")
            }
        }
        if (!lineStarted && !closed) {
            activeLine.start()
            logDesktopTts("play_line_started_after_prime primeBytes=$offset")
        }
        logDesktopTts("play_stream_write inputBytes=${pcm24Khz.size} writtenBytes=$offset totalWritten=$bytesWritten")
    }

    fun drainAndClose() {
        val activeLine = line
        if (activeLine != null && !closed) {
            logDesktopTts("play_stream_drain totalWritten=$bytesWritten")
            runCatching { activeLine.drain() }
                .onFailure { error -> logDesktopTts("play_stream_drain_failed error=\"${error.desktopTtsSummary()}\"") }
        }
        closeNow()
    }

    fun closeNow() {
        val activeLine = synchronized(stateLock) {
            if (closed) return
            closed = true
            paused = false
            stateLock.notifyAll()
            line.also { line = null }
        }
        activeLine?.let {
            runCatching { it.stop() }
            runCatching { it.flush() }
            runCatching { it.close() }
        }
        onLineChanged(null)
        logDesktopTts("play_stream_closed totalWritten=$bytesWritten")
    }

    private fun waitIfPaused() {
        synchronized(stateLock) {
            while (paused && !closed) {
                stateLock.wait(100)
            }
        }
    }

    private fun openBestLine(): SourceDataLine {
        fallbackTo48Khz = true
        return runCatching {
            openLine(48_000f)
        }.getOrElse { firstError ->
            logDesktopTts("play_primary_failed sampleRate=48000 error=\"${firstError.desktopTtsSummary()}\"")
            fallbackTo48Khz = false
            runCatching {
                openLine(24_000f)
            }.onFailure { secondError ->
                logDesktopTts("play_fallback_failed sampleRate=24000 error=\"${secondError.desktopTtsSummary()}\"")
                secondError.printStackTrace()
            }.getOrElse {
                throw firstError
            }
        }
    }

    private fun openLine(sampleRate: Float): SourceDataLine {
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val bufferBytes = sampleRate.toInt().coerceAtLeast(16_384)
        logDesktopTts("play_line_request sampleRate=${sampleRate.toInt()} bufferBytes=$bufferBytes")
        val openedLine = AudioSystem.getSourceDataLine(format)
        openedLine.open(format, bufferBytes)
        line = openedLine
        onLineChanged(openedLine)
        logDesktopTts(
            "play_line_opened sampleRate=${sampleRate.toInt()} output48Khz=$fallbackTo48Khz " +
                "line=\"${openedLine.lineInfo.toString().desktopTtsPreview(160)}\""
        )
        return openedLine
    }
}

private fun availableAudioMixers(): String {
    return runCatching {
        AudioSystem.getMixerInfo()
            .joinToString(limit = 8, truncated = "...") { "${it.name}/${it.description}" }
            .ifBlank { "none" }
    }.getOrDefault("unavailable")
}
