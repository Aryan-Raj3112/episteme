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
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
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
        if (trimmed.isBlank()) return
        stop()
        val audio = synthesize(trimmed.take(5_000))
        playPcm(audio)
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching { activeWebSocket?.abort() }
            activeWebSocket = null
            runCatching { activeLine?.stop() }
            runCatching { activeLine?.flush() }
            runCatching { activeLine?.close() }
            activeLine = null
        }
    }

    private suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val settings = settingsProvider().sanitized()
        if (!settings.isCloudTtsAvailable) {
            throw IllegalStateException("Cloud TTS needs a saved Gemini key and the Gemini cloud TTS model selected.")
        }

        val audioBytes = ByteArrayOutputStream()
        val setupComplete = CompletableDeferred<Unit>()
        val turnComplete = CompletableDeferred<Unit>()
        val failure = CompletableDeferred<Throwable>()
        val messageBuffer = StringBuilder()

        val listener = object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                activeWebSocket = webSocket
                webSocket.request(1)
                webSocket.sendText(buildGeminiTtsSetup(settings.ttsSpeakerId), true)
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                messageBuffer.append(data)
                if (last) {
                    val message = messageBuffer.toString()
                    messageBuffer.clear()
                    handleGeminiTtsMessage(message, audioBytes, setupComplete, turnComplete, failure)
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> {
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                messageBuffer.append(bytes.decodeToString())
                if (last) {
                    val message = messageBuffer.toString()
                    messageBuffer.clear()
                    handleGeminiTtsMessage(message, audioBytes, setupComplete, turnComplete, failure)
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                failure.complete(error)
            }

            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                if (!turnComplete.isCompleted && !failure.isCompleted) {
                    failure.complete(IllegalStateException("Cloud TTS connection closed: $reason"))
                }
                return CompletableFuture.completedFuture(null)
            }
        }

        val encodedKey = URLEncoder.encode(settings.geminiKey, Charsets.UTF_8.name())
        val uri = URI("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$encodedKey")
        val webSocket = httpClient.newWebSocketBuilder()
            .buildAsync(uri, listener)
            .get(15, TimeUnit.SECONDS)
        activeWebSocket = webSocket

        withTimeout(15_000) {
            select<Unit> {
                setupComplete.onAwait { }
                failure.onAwait { throw it }
            }
        }

        webSocket.sendText(buildGeminiTtsTextInput(text), true).join()

        withTimeout(45_000) {
            select<Unit> {
                turnComplete.onAwait { }
                failure.onAwait { throw it }
            }
        }

        val bytes = audioBytes.toByteArray()
        if (bytes.isEmpty()) throw IllegalStateException("Cloud TTS returned no audio.")
        runCatching { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join() }
        activeWebSocket = null
        bytes
    }

    private fun playPcm(audioBytes: ByteArray) {
        runCatching {
            playPcmWithFormat(audioBytes, 24_000f)
        }.getOrElse { firstError ->
            runCatching {
                playPcmWithFormat(audioBytes.upsample16BitMonoLe2x(), 48_000f)
            }.getOrElse {
                throw firstError
            }
        }
    }

    private fun playPcmWithFormat(audioBytes: ByteArray, sampleRate: Float) {
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        activeLine = line
        line.open(format)
        line.start()
        try {
            var offset = 0
            while (offset < audioBytes.size && activeLine === line) {
                val written = line.write(audioBytes, offset, (audioBytes.size - offset).coerceAtMost(4096))
                if (written <= 0) break
                offset += written
            }
            line.drain()
        } finally {
            runCatching { line.stop() }
            runCatching { line.close() }
            if (activeLine === line) activeLine = null
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
    audioBytes: ByteArrayOutputStream,
    setupComplete: CompletableDeferred<Unit>,
    turnComplete: CompletableDeferred<Unit>,
    failure: CompletableDeferred<Throwable>
) {
    val json = runCatching { DesktopGeminiTtsJson.parseToJsonElement(message).jsonObject }.getOrElse { return }
    json["error"]?.let { error ->
        failure.complete(IllegalStateException(error.toString()))
        return
    }
    if (json.containsKey("setupComplete") || json.containsKey("setup_complete")) {
        setupComplete.complete(Unit)
    }

    val serverContent = json.jsonObjectValue("serverContent", "server_content") ?: return
    val modelTurn = serverContent.jsonObjectValue("modelTurn", "model_turn")
    val parts = modelTurn?.get("parts")?.jsonArray
    parts?.forEach { part ->
        val inlineData = part.jsonObjectOrNull()?.jsonObjectValue("inlineData", "inline_data")
        val encoded = inlineData?.get("data")?.jsonPrimitive?.contentOrNull
        if (!encoded.isNullOrBlank()) {
            audioBytes.write(Base64.getMimeDecoder().decode(encoded))
        }
    }
    if (serverContent.booleanValue("turnComplete", "turn_complete")) {
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
