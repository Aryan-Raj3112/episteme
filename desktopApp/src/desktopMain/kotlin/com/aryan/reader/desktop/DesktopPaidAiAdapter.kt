package com.aryan.reader.desktop

import com.aryan.reader.shared.AiAdapter
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.SummarizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

internal class DesktopPaidAiAdapter(
    private val config: DesktopCloudConfig,
    private val networkAccess: () -> Boolean,
    private val hideReaderAiFeatures: () -> Boolean,
    private val currentAuthToken: suspend () -> String?,
    private val currentSignedIn: () -> Boolean,
    private val currentIsProUser: () -> Boolean,
    private val currentCredits: () -> Int,
    private val onUsageCompleted: suspend () -> Unit = {}
) : AiAdapter {
    override val isAvailable: Boolean
        get() = networkAccess() &&
            config.isAiWorkerConfigured &&
            !hideReaderAiFeatures()

    override suspend fun define(text: String, context: String?): AiDefinitionResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return AiDefinitionResult(error = "There is no text to define.")
        val multiWord = wordCount(trimmed) > 1
        if (multiWord && !currentSignedIn()) {
            return AiDefinitionResult(error = "Sign in with Google to use multi-word smart dictionary.")
        }
        if (multiWord && !currentIsProUser()) {
            return AiDefinitionResult(error = "Multi-word smart dictionary requires Pro. Pro can only be purchased from the Android app.")
        }
        val result = callWorker(
            path = "/define",
            body = buildJsonObject { put("text", JsonPrimitive(trimmed.take(2400))) }.toString(),
            authRequired = multiWord
        )
        return AiDefinitionResult(definition = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    override suspend fun summarize(text: String): SummarizationResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return SummarizationResult(error = "There is no text to summarize.")
        val gate = paidGenerationGate(freeProSummaryAllowed = true)
        if (gate != null) return SummarizationResult(error = gate)
        val result = callWorker(
            path = "/summarize",
            body = buildJsonObject {
                put("content_type", JsonPrimitive("text"))
                put("data", JsonPrimitive(trimmed))
            }.toString(),
            authRequired = true
        )
        return SummarizationResult(summary = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    override suspend fun recap(textBeforeCurrentLocation: String): RecapResult {
        val trimmed = textBeforeCurrentLocation.trim()
        if (trimmed.isBlank()) return RecapResult(error = "There is no reading context for a recap.")
        val gate = paidGenerationGate(freeProSummaryAllowed = false)
        if (gate != null) return RecapResult(error = gate)
        val result = callWorker(
            path = "/recap",
            body = buildJsonObject {
                put("past_summaries", buildJsonArray {})
                put("current_text", JsonPrimitive(trimmed))
            }.toString(),
            authRequired = true
        )
        return RecapResult(recap = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    private fun paidGenerationGate(freeProSummaryAllowed: Boolean): String? {
        if (!config.isAiWorkerConfigured) return "Desktop AI is not configured."
        if (!networkAccess()) return "AI features are unavailable in this desktop build."
        if (hideReaderAiFeatures()) return "Reader AI features are hidden."
        if (!currentSignedIn()) return "Sign in with Google to use this AI feature."
        if (!(freeProSummaryAllowed && currentIsProUser()) && currentCredits() <= 0) {
            return "This action needs credits. Pro and credits can only be purchased from the Android app."
        }
        return null
    }

    private suspend fun callWorker(
        path: String,
        body: String,
        authRequired: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IllegalStateException("AI features are unavailable."))
        val token = currentAuthToken()
        if (authRequired && token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Sign in with Google to use this AI feature."))
        }
        runCatching {
            val url = URL(config.aiWorkerUrl.removeSuffix("/") + path)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                doInput = true
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (connection.responseCode == 402 || responseText.contains("INSUFFICIENT_CREDITS")) {
                    throw IllegalStateException("Out of credits. Pro and credits can only be purchased from the Android app.")
                }
                if (connection.responseCode == 401) {
                    throw IllegalStateException("Sign in again to use this AI feature.")
                }
                if (connection.responseCode == 403 && responseText.contains("MULTI_WORD_REQUIRES_PRO")) {
                    throw IllegalStateException("Multi-word smart dictionary requires Pro. Pro can only be purchased from the Android app.")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException(workerErrorMessage(responseText) ?: "AI request failed: HTTP ${connection.responseCode}")
                }
                val text = parseWorkerStream(responseText).trim()
                if (text.isBlank()) throw IllegalStateException("The AI service returned an empty response.")
                onUsageCompleted()
                text
            } finally {
                connection.disconnect()
            }
        }
    }
}

private val DesktopPaidAiJson = Json { ignoreUnknownKeys = true }

private fun parseWorkerStream(responseText: String): String {
    val output = StringBuilder()
    responseText.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach
        val parsed = runCatching { DesktopPaidAiJson.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        parsed?.get("chunk")?.jsonPrimitive?.contentOrNull?.let(output::append)
        parsed?.get("error")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { error ->
            throw IllegalStateException(workerErrorMessage(error) ?: error)
        }
    }
    return output.toString()
}

private fun workerErrorMessage(errorBody: String): String? {
    return when {
        errorBody.contains("INSUFFICIENT_CREDITS") -> "Out of credits. Pro and credits can only be purchased from the Android app."
        errorBody.contains("SUMMARY_LIMIT") ||
            (errorBody.contains("free summar", ignoreCase = true) && errorBody.contains("limit", ignoreCase = true)) ->
            "Free summaries are used up for today. More summaries need credits, and credits can only be purchased from the Android app."
        errorBody.contains("MULTI_WORD_REQUIRES_PRO") -> "Multi-word smart dictionary requires Pro. Pro can only be purchased from the Android app."
        errorBody.contains("Authentication required") -> "Sign in with Google to use this AI feature."
        else -> runCatching {
            DesktopPaidAiJson.parseToJsonElement(errorBody)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
    }
}

private fun wordCount(text: String): Int {
    return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}
