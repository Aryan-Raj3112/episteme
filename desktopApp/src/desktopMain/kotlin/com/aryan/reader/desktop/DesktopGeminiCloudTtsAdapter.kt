package com.aryan.reader.desktop

import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.TtsAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

class DesktopGeminiCloudTtsAdapter(
    private val settingsProvider: () -> ReaderAiByokSettings,
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : TtsAdapter {
    @Volatile
    private var activeLine: SourceDataLine? = null

    @Volatile
    private var activeWebSocket: WebSocket? = null

    override val isAvailable: Boolean
        get() = settingsProvider().sanitized().isCloudTtsAvailable

    override suspend fun speak(text: String) {
        val trimmed = text.trim()
        logDesktopTts("speak_start textChars=${trimmed.length}")
        if (trimmed.isBlank()) return
        stop()
        stream(trimmed.take(5_000))
        logDesktopTts("speak_finished")
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            logDesktopTts("stop_requested hasWebSocket=${activeWebSocket != null} hasLine=${activeLine != null}")
            runCatching { activeWebSocket?.abort() }
            activeWebSocket = null
            runCatching { activeLine?.stop() }
            runCatching { activeLine?.flush() }
            runCatching { activeLine?.close() }
            activeLine = null
            logDesktopTts("stop_complete")
        }
    }

    private suspend fun stream(text: String) = withContext(Dispatchers.IO) {
        val settings = settingsProvider().sanitized()
        logDesktopTts(
            "stream_start textChars=${text.length} keyPresent=${settings.geminiKey.isNotBlank()} " +
                "ttsModel=\"${settings.ttsModel.desktopTtsPreview()}\" speaker=\"${settings.ttsSpeakerId.desktopTtsPreview()}\" " +
                "available=${settings.isCloudTtsAvailable}"
        )
        if (!settings.isCloudTtsAvailable) {
            logDesktopTts("stream_blocked reason=not_available")
            throw IllegalStateException("Cloud TTS needs a saved Gemini key and the Gemini cloud TTS model selected.")
        }

        val audioBytesReceived = AtomicLong(0)
        val player = DesktopStreamingPcmPlayer { activeLine = it }
        val setupComplete = CompletableDeferred<Unit>()
        val turnComplete = CompletableDeferred<Unit>()
        val failure = CompletableDeferred<Throwable>()
        val messageBuffer = StringBuilder()

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
                    handleGeminiTtsMessage(
                        message = message,
                        setupComplete = setupComplete,
                        turnComplete = turnComplete,
                        failure = failure,
                        onAudioPart = { bytes ->
                            audioBytesReceived.addAndGet(bytes.size.toLong())
                            runCatching { player.write(bytes) }
                                .onFailure { error ->
                                    logDesktopTts("stream_audio_write_failed error=\"${error.desktopTtsSummary()}\"")
                                    failure.complete(error)
                                }
                        }
                    )
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
                    handleGeminiTtsMessage(
                        message = message,
                        setupComplete = setupComplete,
                        turnComplete = turnComplete,
                        failure = failure,
                        onAudioPart = { bytes ->
                            audioBytesReceived.addAndGet(bytes.size.toLong())
                            runCatching { player.write(bytes) }
                                .onFailure { error ->
                                    logDesktopTts("stream_audio_write_failed error=\"${error.desktopTtsSummary()}\"")
                                    failure.complete(error)
                                }
                        }
                    )
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                logDesktopTts("ws_error error=\"${error.desktopTtsSummary()}\"")
                failure.complete(error)
            }

            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                logDesktopTts("ws_close status=$statusCode reason=\"${reason.desktopTtsPreview()}\" turnComplete=${turnComplete.isCompleted}")
                if (!turnComplete.isCompleted && !failure.isCompleted) {
                    failure.complete(IllegalStateException("Cloud TTS connection closed: $reason"))
                }
                return CompletableFuture.completedFuture(null)
            }
        }

        val encodedKey = URLEncoder.encode(settings.geminiKey, Charsets.UTF_8.name())
        val uri = URI("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$encodedKey")
        logDesktopTts("ws_connect_start endpoint=GeminiLive keyChars=${settings.geminiKey.length}")
        val webSocket = runCatching {
            httpClient.newWebSocketBuilder()
                .buildAsync(uri, listener)
                .get(15, TimeUnit.SECONDS)
        }.getOrElse { error ->
            logDesktopTts("ws_connect_failed error=\"${error.desktopTtsSummary()}\"")
            throw error
        }
        activeWebSocket = webSocket
        logDesktopTts("ws_connect_complete")

        logDesktopTts("setup_wait_start timeoutMs=15000")
        withTimeout(15_000) {
            select<Unit> {
                setupComplete.onAwait { }
                failure.onAwait { throw it }
            }
        }
        logDesktopTts("setup_wait_complete")

        logDesktopTts("text_send_start textChars=${text.length}")
        runCatching { webSocket.sendText(buildGeminiTtsTextInput(text), true).join() }
            .onFailure { error ->
                logDesktopTts("text_send_failed error=\"${error.desktopTtsSummary()}\"")
                throw error
            }
        logDesktopTts("text_send_complete")

        val turnTimeoutMs = (30_000L + text.length * 80L).coerceIn(60_000L, 600_000L)
        logDesktopTts("turn_wait_start timeoutMs=$turnTimeoutMs")
        try {
            withTimeout(turnTimeoutMs) {
                select<Unit> {
                    turnComplete.onAwait { }
                    failure.onAwait { throw it }
                }
            }
            logDesktopTts("turn_wait_complete audioBytes=${audioBytesReceived.get()}")

            if (audioBytesReceived.get() == 0L) {
                logDesktopTts("stream_failed reason=empty_audio")
                throw IllegalStateException("Cloud TTS returned no audio.")
            }
            player.drainAndClose()
            runCatching { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join() }
            activeWebSocket = null
            logDesktopTts("stream_complete audioBytes=${audioBytesReceived.get()}")
        } catch (error: Throwable) {
            runCatching { webSocket.abort() }
            activeWebSocket = null
            player.closeNow()
            throw error
        }
    }
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
    turnComplete: CompletableDeferred<Unit>,
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
        turnComplete.complete(Unit)
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
    var inputIndex = 0
    var outputIndex = 0
    repeat(sampleCount) {
        val lo = this[inputIndex]
        val hi = this[inputIndex + 1]
        output[outputIndex] = lo
        output[outputIndex + 1] = hi
        output[outputIndex + 2] = lo
        output[outputIndex + 3] = hi
        inputIndex += 2
        outputIndex += 4
    }
    return output
}

private class DesktopStreamingPcmPlayer(
    private val onLineChanged: (SourceDataLine?) -> Unit
) {
    private var line: SourceDataLine? = null
    private var fallbackTo48Khz = false
    private var closed = false
    private var bytesWritten = 0L

    init {
        logDesktopTts("play_stream_start mixers=\"${availableAudioMixers().desktopTtsPreview(260)}\"")
    }

    @Synchronized
    fun write(pcm24Khz: ByteArray) {
        if (closed || pcm24Khz.isEmpty()) return
        val activeLine = line ?: openBestLine()
        val bytes = if (fallbackTo48Khz) pcm24Khz.upsample16BitMonoLe2x() else pcm24Khz
        var offset = 0
        while (offset < bytes.size && !closed) {
            val written = activeLine.write(bytes, offset, (bytes.size - offset).coerceAtMost(4096))
            if (written <= 0) break
            offset += written
            bytesWritten += written
        }
        logDesktopTts("play_stream_write inputBytes=${pcm24Khz.size} writtenBytes=$offset totalWritten=$bytesWritten")
    }

    @Synchronized
    fun drainAndClose() {
        val activeLine = line
        if (activeLine != null && !closed) {
            logDesktopTts("play_stream_drain totalWritten=$bytesWritten")
            runCatching { activeLine.drain() }
                .onFailure { error -> logDesktopTts("play_stream_drain_failed error=\"${error.desktopTtsSummary()}\"") }
        }
        closeNow()
    }

    @Synchronized
    fun closeNow() {
        if (closed) return
        closed = true
        line?.let { activeLine ->
            runCatching { activeLine.stop() }
            runCatching { activeLine.flush() }
            runCatching { activeLine.close() }
        }
        line = null
        onLineChanged(null)
        logDesktopTts("play_stream_closed totalWritten=$bytesWritten")
    }

    private fun openBestLine(): SourceDataLine {
        return runCatching {
            openLine(24_000f)
        }.getOrElse { firstError ->
            logDesktopTts("play_primary_failed sampleRate=24000 error=\"${firstError.desktopTtsSummary()}\"")
            fallbackTo48Khz = true
            runCatching {
                openLine(48_000f)
            }.onFailure { secondError ->
                logDesktopTts("play_fallback_failed sampleRate=48000 error=\"${secondError.desktopTtsSummary()}\"")
                secondError.printStackTrace()
            }.getOrElse {
                throw firstError
            }
        }
    }

    private fun openLine(sampleRate: Float): SourceDataLine {
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        logDesktopTts("play_line_request sampleRate=${sampleRate.toInt()}")
        val openedLine = AudioSystem.getSourceDataLine(format)
        openedLine.open(format)
        openedLine.start()
        line = openedLine
        onLineChanged(openedLine)
        logDesktopTts("play_line_started sampleRate=${sampleRate.toInt()} line=\"${openedLine.lineInfo.toString().desktopTtsPreview(160)}\"")
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
