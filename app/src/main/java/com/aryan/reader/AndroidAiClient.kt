// Common.kt
@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderAiFeature as AiFeature

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

suspend fun fetchAiDefinition(
    text: String,
    context: Context,
    authToken: String?,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (text.isBlank()) {
        onError(context.getString(R.string.error_text_empty))
        onFinish()
        return
    }
    Timber.d("Fetching AI definition for: '$text'")

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "oss") {
        if (BuildConfig.IS_OFFLINE) {
            onError(context.getString(R.string.error_network_check_connection))
            onFinish()
            return
        }
        val systemInstruction = "You are an AI-powered dictionary. Your goal is to provide a concise and easy-to-understand definition for the given word, phrase or paragraphs. Keep the explanation brief. Respond only with the definition text, without any preamble. Do not send your thoughts, only the final definition you arrived on. no emoji."
        callByokTextAi(
            context = context,
            feature = AiFeature.DEFINE,
            systemInstruction = systemInstruction,
            userPrompt = "Define: \"$text\"",
            temperature = 0.1,
            maxTokens = 256,
            onUpdate = onUpdate,
            onError = onError
        )
        onFinish()
        return
    }

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(aiDefinitionUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply { put("text", text) }
            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == 402) {
                onError("INSUFFICIENT_CREDITS")
                onFinish()
                return@withContext
            }
            Timber.d("Definition: Got response code $responseCode")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Timber.d("Definition: Received line: $line")
                        try {
                            val jsonResponse = JSONObject(line!!)
                            jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                Timber.d("Definition: Parsed chunk, calling onUpdate.")
                                onUpdate(it)
                                hasReceivedData = true
                            }
                            jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                onError(it)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not parse stream line: $line")
                        }
                    }
                }
                Timber.d("Definition: Finished reading stream.")
                if (!hasReceivedData) {
                    onError(context.getString(R.string.error_ai_empty_definition))
                }
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                val errorDetail = try { errorBody?.let { JSONObject(it).getString("detail") } } catch (_: Exception) { context.getString(R.string.error_could_not_get_definition) }
                onError(context.getString(R.string.error_response_code_with_detail, responseCode, errorDetail ?: context.getString(R.string.error_unknown_server)))
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error fetching AI definition: ${e.message}")
            onError(context.getString(R.string.error_network_check_connection))
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}

fun countWords(text: String): Int {
    var count = 0
    var inWord = false
    for (char in text) {
        if (char.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            count++
            inWord = true
        }
    }
    return count
}

internal fun streamGeminiAiResponse(
    connection: HttpURLConnection,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    safetyError: String
): Boolean {
    var hasReceivedData = false
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
                var braceCount = 0
                var end = -1
                charLoop@ for (i in start until buffer.length) {
                    when (buffer[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                end = i
                                break@charLoop
                            }
                        }
                    }
                }
                if (end == -1) break
                val jsonString = buffer.substring(start, end + 1)
                buffer = buffer.substring(end + 1)
                try {
                    val jsonResponse = JSONObject(jsonString)
                    jsonResponse.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            onUpdate(it)
                            hasReceivedData = true
                        }
                    if (jsonResponse.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason") == "SAFETY") {
                        onError(safetyError)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Could not parse Gemini BYOK stream object")
                }
            }
        }
    }
    return hasReceivedData
}

internal fun streamGroqAiResponse(
    connection: HttpURLConnection,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean {
    var hasReceivedData = false
    var inThink = false
    var thinkBuffer = ""

    fun cleanChunk(text: String): String {
        thinkBuffer += text
        val out = StringBuilder()
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
                        out.append(thinkBuffer.dropLast(6))
                        thinkBuffer = thinkBuffer.takeLast(6)
                    }
                    break
                }
                out.append(thinkBuffer.substring(0, start))
                inThink = true
                thinkBuffer = thinkBuffer.substring(start + 7)
            }
        }
        return out.toString()
    }

    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (!trimmed.startsWith("data: ")) continue
            val data = trimmed.removePrefix("data: ").trim()
            if (data == "[DONE]") continue
            try {
                val chunk = JSONObject(data)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()
                val cleaned = cleanChunk(chunk)
                if (cleaned.isNotEmpty()) {
                    onUpdate(cleaned)
                    hasReceivedData = true
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not parse Groq BYOK stream line")
            }
        }
    }
    if (!inThink && thinkBuffer.isNotBlank()) {
        onUpdate(thinkBuffer)
        hasReceivedData = true
    }
    return hasReceivedData
}

internal fun buildGroqPayload(
    model: String,
    systemInstruction: String,
    userPrompt: String,
    temperature: Double,
    maxTokens: Int
): JSONObject {
    return JSONObject().apply {
        put("model", model)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        })
        put("temperature", temperature)
        put("top_p", 0.95)
        put("max_tokens", maxTokens)
        put("stream", true)
        if (model.contains("qwen")) put("reasoning_effort", "none")
    }
}

suspend fun callByokTextAi(
    context: Context,
    feature: AiFeature,
    systemInstruction: String,
    userPrompt: String,
    temperature: Double,
    maxTokens: Int,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val settings = loadAiByokSettings(context)
    val model = aiModelById(settings.modelIdFor(feature))
    if (model == null) {
        onError(context.getString(R.string.ai_error_choose_model, feature.displayName(context)))
        return@withContext false
    }
    val apiKey = settings.apiKeyFor(model.provider)
    if (apiKey.isBlank()) {
        onError(context.getString(R.string.ai_error_add_provider_key, aiProviderDisplayName(context, model.provider)))
        return@withContext false
    }

    var connection: HttpURLConnection? = null
    try {
        val url = if (model.provider == "groq") {
            URL("https://api.groq.com/openai/v1/chat/completions")
        } else {
            URL("https://generativelanguage.googleapis.com/v1beta/models/${model.name}:streamGenerateContent?key=$apiKey")
        }
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        if (model.provider == "groq") {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.connectTimeout = 15000
        connection.readTimeout = 120000
        connection.doOutput = true
        connection.doInput = true

        val payload = if (model.provider == "groq") {
            buildGroqPayload(model.name, systemInstruction, userPrompt, temperature, maxTokens)
        } else {
            JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", userPrompt) }))
                }))
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                    put("topP", 0.95)
                    put("topK", 40)
                    put("maxOutputTokens", maxTokens)
                    put("response_mime_type", "text/plain")
                    if (feature == AiFeature.DEFINE && model.name.startsWith("gemini")) {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                    }
                })
            }
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val hasData = if (model.provider == "groq") {
                streamGroqAiResponse(connection, onUpdate, onError)
            } else {
                streamGeminiAiResponse(connection, onUpdate, onError, context.getString(R.string.ai_error_blocked_safety))
            }
            if (!hasData) onError(context.getString(R.string.ai_error_provider_empty_response))
            hasData
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
            onError(context.getString(R.string.ai_error_provider_error, responseCode, errorBody.orEmpty().take(300)))
            false
        }
    } catch (e: Exception) {
        Timber.e(e, "BYOK AI request failed")
        onError(context.getString(R.string.error_network_check_connection))
        false
    } finally {
        connection?.disconnect()
    }
}

suspend fun callByokGeminiInlineAi(
    context: Context,
    feature: AiFeature,
    mimeType: String,
    base64Data: String,
    systemInstruction: String,
    temperature: Double,
    maxTokens: Int,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val settings = loadAiByokSettings(context)
    val model = aiModelById(settings.modelIdFor(feature))
    if (model == null) {
        onError(context.getString(R.string.ai_error_choose_model, feature.displayName(context)))
        return@withContext false
    }
    if (model.provider != "gemini") {
        onError(context.getString(R.string.ai_error_gemini_required_for_image_summary))
        return@withContext false
    }
    val apiKey = settings.geminiKey.trim()
    if (apiKey.isBlank()) {
        onError(context.getString(R.string.ai_error_add_provider_key, context.getString(R.string.provider_gemini)))
        return@withContext false
    }

    var connection: HttpURLConnection? = null
    try {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.name}:streamGenerateContent?key=$apiKey")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 180000
        connection.doOutput = true
        connection.doInput = true

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mime_type", mimeType)
                        put("data", base64Data)
                    })
                }))
            }))
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("topP", 0.95)
                put("topK", 40)
                put("maxOutputTokens", maxTokens)
                put("response_mime_type", "text/plain")
            })
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val hasData = streamGeminiAiResponse(connection, onUpdate, onError, context.getString(R.string.ai_error_blocked_safety))
            if (!hasData) onError(context.getString(R.string.ai_error_provider_empty_response))
            hasData
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
            onError(context.getString(R.string.ai_error_provider_error, responseCode, errorBody.orEmpty().take(300)))
            false
        }
    } catch (e: Exception) {
        Timber.e(e, "BYOK inline AI request failed")
        onError(context.getString(R.string.error_network_check_connection))
        false
    } finally {
        connection?.disconnect()
    }
}

object MarkdownParser {
    fun parse(markdown: String): AnnotatedString {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val builder = AnnotatedString.Builder()

        val visitor = object : AbstractVisitor() {
            override fun visit(text: Text) {
                builder.append(text.literal)
            }

            override fun visit(emphasis: Emphasis) {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                visitChildren(emphasis)
                builder.pop()
            }

            override fun visit(strongEmphasis: StrongEmphasis) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                visitChildren(strongEmphasis)
                builder.pop()
            }

            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
                // Add newline if it's not the last node
                if (paragraph.next != null) {
                    builder.append("\n\n")
                }
            }

            override fun visit(heading: Heading) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                visitChildren(heading)
                builder.pop()
                builder.append("\n\n")
            }

            override fun visit(softLineBreak: SoftLineBreak) {
                builder.append(" ")
            }

            override fun visit(hardLineBreak: HardLineBreak) {
                builder.append("\n")
            }

            override fun visit(code: Code) {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888)))
                builder.append(code.literal)
                builder.pop()
            }

            override fun visit(listItem: ListItem) {
                builder.append("• ")
                visitChildren(listItem)
                if (listItem.next != null) {
                    builder.append("\n")
                }
            }
        }

        document.accept(visitor)
        return builder.toAnnotatedString()
    }
}

suspend fun fetchRecap(
    pastSummaries: List<String>,
    currentText: String,
    context: Context,
    authToken: String?,
    onUpdate: (String) -> Unit,
    onCostReceived: (Double) -> Unit = {},
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (pastSummaries.isEmpty() && currentText.isBlank()) {
        onError(context.getString(R.string.error_not_enough_context))
        onFinish()
        return
    }

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "oss") {
        if (BuildConfig.IS_OFFLINE) {
            onError(context.getString(R.string.error_network_recap))
            onFinish()
            return
        }
        val systemInstruction = "You are a sophisticated reading assistant. You have to create a recap. Synthesize the provided past context and current chapter text into a cohesive summary of the reading session so far. Conclude exactly where the user is positioned currently. Do not add a preamble. Also Avoid including or mentioning text from administrative or boilerplate sections such as the introduction, copyright pages, preface, or table of contents; focus strictly on the core story or informative content. If the the book has multiple different short stories that came before then summarize them too, its a recap of the whole book up to this point."
        val promptContext = buildString {
            append("--- PREVIOUS CONTEXT (Summaries of read chapters) ---\n")
            if (pastSummaries.isEmpty()) {
                append("(None - User is in the first chapter)\n")
            } else {
                pastSummaries.forEachIndexed { index, summary ->
                    append("Chapter ${index + 1}: $summary\n\n")
                }
            }
            append("\n--- CURRENT SESSION (Text read in current chapter) ---\n")
            append(currentText)
            append("\n\nBased strictly on the above, provide a recap of the content read so far.")
        }
        callByokTextAi(
            context = context,
            feature = AiFeature.RECAP,
            systemInstruction = systemInstruction,
            userPrompt = promptContext,
            temperature = 0.3,
            maxTokens = 4096,
            onUpdate = onUpdate,
            onError = onError
        )
        onFinish()
        return
    }

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(recapUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply {
                put("past_summaries", JSONArray(pastSummaries))
                put("current_text", currentText)
            }

            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == 402) {
                onError("INSUFFICIENT_CREDITS")
                onFinish()
                return@withContext
            }
            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        try {
                            val jsonResponse = JSONObject(line!!)

                            jsonResponse.optDouble("cost_deducted", -1.0).takeIf { it > -1.0 }?.let { onCostReceived(it) }

                            jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                onUpdate(it)
                                hasReceivedData = true
                            }
                            jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                onError(it)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not parse stream line: $line")
                        }
                    }
                }
                if (!hasReceivedData) onError(context.getString(R.string.error_parse_recap))
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                onError(context.getString(R.string.error_response_code_with_detail, responseCode, errorBody.orEmpty()))
            }
        } catch (e: Exception) {
            Timber.e(e, "Recap error: ${e.message}")
            onError(context.getString(R.string.error_network_recap))
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}
