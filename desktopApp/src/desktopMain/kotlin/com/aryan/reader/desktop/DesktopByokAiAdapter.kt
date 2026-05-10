package com.aryan.reader.desktop

import com.aryan.reader.shared.AiAdapter
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderByokTextRequest
import com.aryan.reader.shared.ReaderByokTextRequestResult
import com.aryan.reader.shared.ReaderByokTextRequests
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.SummarizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class DesktopByokAiAdapter(
    private val settingsProvider: () -> ReaderAiByokSettings,
    private val networkAccess: () -> Boolean = { true }
) : AiAdapter {
    override val isAvailable: Boolean
        get() = networkAccess() && settingsProvider().sanitized().areReaderAiFeaturesAvailable

    override suspend fun define(text: String, context: String?): AiDefinitionResult {
        val result = callTextAi(ReaderAiFeature.DEFINE, text, context)
        return AiDefinitionResult(definition = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    override suspend fun summarize(text: String): SummarizationResult {
        val result = callTextAi(ReaderAiFeature.SUMMARIZE, text)
        return SummarizationResult(summary = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    override suspend fun recap(textBeforeCurrentLocation: String): RecapResult {
        val result = callTextAi(ReaderAiFeature.RECAP, textBeforeCurrentLocation)
        return RecapResult(recap = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    suspend fun callTextAi(
        feature: ReaderAiFeature,
        text: String,
        context: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!networkAccess()) return@withContext Result.failure(IllegalStateException("AI features are unavailable in this desktop build."))
        if (text.isBlank()) return@withContext Result.failure(IllegalArgumentException("There is no text to send."))
        when (val requestResult = ReaderByokTextRequests.build(settingsProvider(), feature, text, context)) {
            ReaderByokTextRequestResult.Hidden -> Result.failure(IllegalStateException("Reader AI features are hidden."))
            is ReaderByokTextRequestResult.MissingKey -> {
                Result.failure(IllegalStateException("Add a ${requestResult.provider.replaceFirstChar { it.uppercaseChar() }} API key in AI keys and models."))
            }
            is ReaderByokTextRequestResult.MissingModel -> {
                Result.failure(IllegalStateException("Choose a model for ${requestResult.featureName} in AI keys and models."))
            }
            is ReaderByokTextRequestResult.Ready -> runCatching {
                requestResult.request.execute()
            }
        }
    }

    private fun ReaderByokTextRequest.execute(): String {
        var connection: HttpURLConnection? = null
        try {
            val url = if (model.provider == "groq") {
                URL("https://api.groq.com/openai/v1/chat/completions")
            } else {
                URL("https://generativelanguage.googleapis.com/v1beta/models/${model.name}:streamGenerateContent?key=$apiKey")
            }
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (model.provider == "groq") {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                doInput = true
            }
            val payload = if (model.provider == "groq") buildGroqPayload(this) else buildGeminiPayload(this)
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                throw IllegalStateException("AI provider error: $responseCode. ${errorBody.orEmpty().take(300)}")
            }
            val text = if (model.provider == "groq") {
                streamGroqResponse(connection)
            } else {
                streamGeminiResponse(connection)
            }.trim()
            if (text.isBlank()) throw IllegalStateException("The AI provider returned an empty response.")
            return text
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildGroqPayload(request: ReaderByokTextRequest): String {
        return buildJsonObject {
            put("model", JsonPrimitive(request.model.name))
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(request.systemInstruction))
                    })
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(request.userPrompt))
                    })
                }
            )
            put("temperature", JsonPrimitive(request.temperature))
            put("top_p", JsonPrimitive(0.95))
            put("max_tokens", JsonPrimitive(request.maxTokens))
            put("stream", JsonPrimitive(true))
            if (request.model.name.contains("qwen")) put("reasoning_effort", JsonPrimitive("none"))
        }.toString()
    }

    private fun buildGeminiPayload(request: ReaderByokTextRequest): String {
        return buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(request.userPrompt)) })
                        })
                    })
                }
            )
            put(
                "systemInstruction",
                buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(request.systemInstruction)) })
                    })
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", JsonPrimitive(request.temperature))
                    put("topP", JsonPrimitive(0.95))
                    put("topK", JsonPrimitive(40))
                    put("maxOutputTokens", JsonPrimitive(request.maxTokens))
                    put("response_mime_type", JsonPrimitive("text/plain"))
                    if (request.model.name.startsWith("gemini")) {
                        put(
                            "thinkingConfig",
                            buildJsonObject { put("thinkingBudget", JsonPrimitive(0)) }
                        )
                    }
                }
            )
        }.toString()
    }

    private fun streamGeminiResponse(connection: HttpURLConnection): String {
        val output = StringBuilder()
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var buffer = ""
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                buffer += line
                while (true) {
                    val start = buffer.indexOf('{')
                    if (start == -1) {
                        buffer = ""
                        break
                    }
                    var depth = 0
                    var end = -1
                    scan@ for (index in start until buffer.length) {
                        when (buffer[index]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    end = index
                                    break@scan
                                }
                            }
                        }
                    }
                    if (end == -1) break
                    val jsonObject = buffer.substring(start, end + 1)
                    buffer = buffer.substring(end + 1)
                    val parsed = runCatching { DesktopAiJson.parseToJsonElement(jsonObject).jsonObject }.getOrNull()
                    output.append(parsed.geminiTextChunk())
                    if (parsed.geminiFinishReason() == "SAFETY") {
                        throw IllegalStateException("Blocked for safety reasons.")
                    }
                }
            }
        }
        return output.toString()
    }

    private fun streamGroqResponse(connection: HttpURLConnection): String {
        val output = StringBuilder()
        var inThink = false
        var thinkBuffer = ""

        fun cleanChunk(text: String): String {
            thinkBuffer += text
            val cleaned = StringBuilder()
            while (true) {
                if (inThink) {
                    val end = thinkBuffer.indexOf("</think>")
                    if (end == -1) {
                        if (thinkBuffer.length > 7) thinkBuffer = thinkBuffer.takeLast(7)
                        break
                    }
                    inThink = false
                    thinkBuffer = thinkBuffer.substring(end + 8)
                } else {
                    val start = thinkBuffer.indexOf("<think>")
                    if (start == -1) {
                        if (thinkBuffer.length > 6) {
                            cleaned.append(thinkBuffer.dropLast(6))
                            thinkBuffer = thinkBuffer.takeLast(6)
                        }
                        break
                    }
                    cleaned.append(thinkBuffer.substring(0, start))
                    inThink = true
                    thinkBuffer = thinkBuffer.substring(start + 7)
                }
            }
            return cleaned.toString()
        }

        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (!trimmed.startsWith("data: ")) continue
                val data = trimmed.removePrefix("data: ").trim()
                if (data == "[DONE]") continue
                val chunk = runCatching {
                    DesktopAiJson.parseToJsonElement(data).jsonObject["choices"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("delta")
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull().orEmpty()
                output.append(cleanChunk(chunk))
            }
        }
        if (!inThink && thinkBuffer.isNotBlank()) output.append(thinkBuffer)
        return output.toString()
    }
}

private val DesktopAiJson = Json { ignoreUnknownKeys = true }

private fun JsonObject?.geminiTextChunk(): String {
    if (this == null) return ""
    return this["candidates"]
        ?.jsonArrayOrNull()
        ?.firstOrNull()
        ?.jsonObjectOrNull()
        ?.get("content")
        ?.jsonObjectOrNull()
        ?.get("parts")
        ?.jsonArrayOrNull()
        ?.firstOrNull()
        ?.jsonObjectOrNull()
        ?.get("text")
        ?.jsonPrimitiveOrNull()
        ?.contentOrNull
        .orEmpty()
}

private fun JsonObject?.geminiFinishReason(): String? {
    if (this == null) return null
    return this["candidates"]
        ?.jsonArrayOrNull()
        ?.firstOrNull()
        ?.jsonObjectOrNull()
        ?.get("finishReason")
        ?.jsonPrimitiveOrNull()
        ?.contentOrNull
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
