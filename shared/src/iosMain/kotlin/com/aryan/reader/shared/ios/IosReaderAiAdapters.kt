@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.AiAdapter
import com.aryan.reader.shared.AiDefinitionResult
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderByokTextRequest
import com.aryan.reader.shared.ReaderByokTextRequestResult
import com.aryan.reader.shared.ReaderByokTextRequests
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.maskedReaderAiKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.HTTPMethod
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.dataWithLength
import platform.Foundation.setValue
import platform.Foundation.setHTTPBody
import platform.darwin.NSObject
import platform.posix.memcpy
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecClassGenericPassword

/**
 * iOS uses the same shared model IDs and prompt contract as Android. The
 * native boundary is kept here so the reader host can inject auth/billing
 * state without putting Keychain or NSURLSession details in shared UI.
 */
internal const val IOS_READER_AI_WORKER_URL = "https://reader-ai.aryanrajttps.workers.dev"

private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    if (bytes.isEmpty()) return NSMutableData.dataWithLength(0u) ?: NSMutableData()
    val data = NSMutableData.dataWithLength(bytes.size.toULong()) ?: NSMutableData()
    bytes.usePinned { pinned ->
        memcpy(data.mutableBytes, pinned.addressOf(0), bytes.size.toULong())
    }
    return data
}

internal data class IosReaderAiUsage(
    val cost: Double? = null,
    val freeRemaining: Int? = null,
)

internal data class IosReaderAiAccountState(
    val isSignedIn: Boolean = false,
    val isProUser: Boolean = false,
    val credits: Int = 0,
)

internal class IosReaderAiSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    fun load(): ReaderAiByokSettings {
        return ReaderAiByokSettings(
            geminiKey = IosReaderAiKeychain.read(IosReaderAiKeychain.GEMINI_ACCOUNT),
            groqKey = IosReaderAiKeychain.read(IosReaderAiKeychain.GROQ_ACCOUNT),
            useOneModel = defaults.objectForKey(KEY_USE_ONE_MODEL)?.let { defaults.boolForKey(KEY_USE_ONE_MODEL) } ?: true,
            modelForAll = defaults.stringForKey(KEY_MODEL_ALL).orEmpty(),
            defineModel = defaults.stringForKey(KEY_MODEL_DEFINE).orEmpty(),
            summarizeModel = defaults.stringForKey(KEY_MODEL_SUMMARIZE).orEmpty(),
            recapModel = defaults.stringForKey(KEY_MODEL_RECAP).orEmpty(),
            ttsModel = defaults.stringForKey(KEY_TTS_MODEL).orEmpty(),
            hideReaderAiFeatures = defaults.boolForKey(KEY_HIDE_READER_AI),
            ttsSpeakerId = defaults.stringForKey(KEY_TTS_SPEAKER).orEmpty(),
        ).sanitized()
    }

    fun save(settings: ReaderAiByokSettings) {
        val sanitized = settings.sanitized()
        IosReaderAiKeychain.write(IosReaderAiKeychain.GEMINI_ACCOUNT, sanitized.geminiKey)
        IosReaderAiKeychain.write(IosReaderAiKeychain.GROQ_ACCOUNT, sanitized.groqKey)
        defaults.setBool(sanitized.useOneModel, forKey = KEY_USE_ONE_MODEL)
        defaults.setObject(sanitized.modelForAll, forKey = KEY_MODEL_ALL)
        defaults.setObject(sanitized.defineModel, forKey = KEY_MODEL_DEFINE)
        defaults.setObject(sanitized.summarizeModel, forKey = KEY_MODEL_SUMMARIZE)
        defaults.setObject(sanitized.recapModel, forKey = KEY_MODEL_RECAP)
        defaults.setObject(sanitized.ttsModel, forKey = KEY_TTS_MODEL)
        defaults.setBool(sanitized.hideReaderAiFeatures, forKey = KEY_HIDE_READER_AI)
        defaults.setObject(sanitized.ttsSpeakerId, forKey = KEY_TTS_SPEAKER)
    }

    fun saveKey(provider: String, key: String) {
        IosReaderAiKeychain.write(provider.accountName(), key.trim())
    }

    fun deleteKey(provider: String) {
        IosReaderAiKeychain.delete(provider.accountName())
    }

    fun maskedKeys(): Map<String, String> {
        return mapOf(
            "gemini" to maskedReaderAiKey(IosReaderAiKeychain.read(IosReaderAiKeychain.GEMINI_ACCOUNT)),
            "groq" to maskedReaderAiKey(IosReaderAiKeychain.read(IosReaderAiKeychain.GROQ_ACCOUNT)),
        )
    }

    private fun String.accountName(): String = when (lowercase()) {
        "gemini" -> IosReaderAiKeychain.GEMINI_ACCOUNT
        "groq" -> IosReaderAiKeychain.GROQ_ACCOUNT
        else -> error("Unsupported AI provider: $this")
    }

    private companion object {
        const val KEY_USE_ONE_MODEL = "reader.ai.use_one_model.v1"
        const val KEY_MODEL_ALL = "reader.ai.model_all.v1"
        const val KEY_MODEL_DEFINE = "reader.ai.model_define.v1"
        const val KEY_MODEL_SUMMARIZE = "reader.ai.model_summarize.v1"
        const val KEY_MODEL_RECAP = "reader.ai.model_recap.v1"
        const val KEY_TTS_MODEL = "reader.ai.tts_model.v1"
        const val KEY_TTS_SPEAKER = "reader.ai.tts_speaker.v1"
        const val KEY_HIDE_READER_AI = "reader.ai.hide_features.v1"
    }
}

/** Small Keychain wrapper; values never enter NSUserDefaults or cloud snapshots. */
internal object IosReaderAiKeychain {
    const val GEMINI_ACCOUNT = "gemini"
    const val GROQ_ACCOUNT = "groq"
    private const val SERVICE = "com.aryan.reader.ai.byok.v1"

    fun read(account: String): String {
        val query = baseQuery(account).toMutableMap().apply {
            put("r_Data", true)
            put("m_Limit", "m_LimitOne")
        }
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.toNSDictionary() as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return@memScoped ""
            val dataPointer = result.value ?: return@memScoped ""
            val dataRef = dataPointer as CFDataRef
            val length = CFDataGetLength(dataRef).toInt()
            if (length <= 0) return@memScoped ""
            val bytes = CFDataGetBytePtr(dataRef) ?: return@memScoped ""
            val output = ByteArray(length)
            output.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length.toULong())
            }
            output.decodeToString()
        }
    }

    fun write(account: String, value: String) {
        if (value.isBlank()) {
            delete(account)
            return
        }
        val data = value.toNSData()
        val query = baseQuery(account)
        val attributes = mapOf(
            "v_Data" to data,
            "pdmn" to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        val addStatus = SecItemAdd((query + attributes).toNSDictionary() as CFDictionaryRef, null)
        if (addStatus == errSecDuplicateItem) {
            SecItemUpdate(query.toNSDictionary() as CFDictionaryRef, attributes.toNSDictionary() as CFDictionaryRef)
        }
    }

    fun delete(account: String) {
        SecItemDelete(baseQuery(account).toNSDictionary() as CFDictionaryRef)
    }

    private fun baseQuery(account: String): Map<Any?, Any?> = mapOf(
        "class" to kSecClassGenericPassword,
        "svce" to SERVICE,
        "acct" to account,
    )

    private fun Map<*, *>.toNSDictionary(): NSMutableDictionary = NSMutableDictionary().apply {
        for ((key, value) in this@toNSDictionary) {
            if (key != null && value != null) {
                setObject(value, forKey = NSString.create(key.toString()))
            }
        }
    }

}

/**
 * The worker and BYOK paths intentionally share one adapter. A signed-in
 * account uses the paid worker for managed features; otherwise configured
 * BYOK models are used exactly as on Android OSS.
 */
internal class IosReaderAiAdapter(
    private val settingsProvider: () -> ReaderAiByokSettings,
    private val accountStateProvider: () -> IosReaderAiAccountState,
    private val authTokenProvider: suspend () -> String?,
    private val networkAccess: () -> Boolean = { true },
    private val workerUrlProvider: () -> String = { IOS_READER_AI_WORKER_URL },
    private val onUsageReported: (IosReaderAiUsage) -> Unit = {},
) : AiAdapter {
    override val isAvailable: Boolean
        get() {
            val settings = settingsProvider().sanitized()
            return networkAccess() && !settings.hideReaderAiFeatures &&
                (settings.hasAnyAiKey || accountStateProvider().isSignedIn)
        }

    override suspend fun define(text: String, context: String?): AiDefinitionResult {
        return defineStreaming(text, context, {})
    }

    override suspend fun defineStreaming(
        text: String,
        context: String?,
        onUpdate: (String) -> Unit,
    ): AiDefinitionResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return AiDefinitionResult(error = "There is no text to define.")
        val account = accountStateProvider()
        val multiWord = trimmed.count { it.isWhitespace() } > 0
        if (multiWord && !account.isSignedIn && !settingsProvider().sanitized().hasAnyAiKey) {
            return AiDefinitionResult(error = "Sign in to use multi-word smart dictionary.")
        }
        if (multiWord && account.isSignedIn && !account.isProUser && account.credits <= 0 && !hasByokModel(ReaderAiFeature.DEFINE)) {
            return AiDefinitionResult(error = "Multi-word smart dictionary requires Pro or credits.")
        }
        return textRequest(ReaderAiFeature.DEFINE, trimmed.take(2400), context, onUpdate).let { result ->
            AiDefinitionResult(definition = result.text, error = result.error)
        }
    }

    override suspend fun summarize(text: String): SummarizationResult {
        return summarizeStreaming(text, { _, _ -> }, {})
    }

    override suspend fun summarizeStreaming(
        text: String,
        onUsageReceived: (cost: Double?, freeRemaining: Int?) -> Unit,
        onUpdate: (String) -> Unit,
    ): SummarizationResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return SummarizationResult(error = "There is no text to summarize.")
        val gate = paidGenerationGate(freeProSummaryAllowed = true, feature = ReaderAiFeature.SUMMARIZE)
        if (gate != null) return SummarizationResult(error = gate)
        val result = textRequest(ReaderAiFeature.SUMMARIZE, trimmed, null, onUpdate, onUsageReceived)
        return SummarizationResult(
            summary = result.text,
            error = result.error,
            cost = result.cost,
            freeRemaining = result.freeRemaining,
        )
    }

    override suspend fun recap(textBeforeCurrentLocation: String): RecapResult {
        val trimmed = textBeforeCurrentLocation.trim()
        if (trimmed.isBlank()) return RecapResult(error = "There is no reading context for a recap.")
        val gate = paidGenerationGate(freeProSummaryAllowed = false, feature = ReaderAiFeature.RECAP)
        if (gate != null) return RecapResult(error = gate)
        val result = textRequest(ReaderAiFeature.RECAP, trimmed, null, {}, { _, _ -> })
        return RecapResult(
            recap = result.text,
            error = result.error,
            cost = result.cost,
            freeRemaining = result.freeRemaining,
        )
    }

    private fun hasByokModel(feature: ReaderAiFeature): Boolean {
        val settings = settingsProvider().sanitized()
        val modelId = settings.modelIdFor(feature)
        val model = com.aryan.reader.shared.readerAiModelById(modelId) ?: return false
        return settings.apiKeyFor(model.provider).isNotBlank()
    }

    private fun paidGenerationGate(freeProSummaryAllowed: Boolean, feature: ReaderAiFeature): String? {
        val settings = settingsProvider().sanitized()
        val account = accountStateProvider()
        if (settings.hideReaderAiFeatures) return "Reader AI features are hidden."
        if (!networkAccess()) return "AI features are unavailable while offline."
        if (!account.isSignedIn && !hasByokModel(feature)) return "Sign in to use this AI feature."
        if (!hasByokModel(feature) && !(freeProSummaryAllowed && account.isProUser) && account.credits <= 0) {
            return "This action needs credits."
        }
        return null
    }

    private suspend fun textRequest(
        feature: ReaderAiFeature,
        text: String,
        context: String?,
        onUpdate: (String) -> Unit,
        onUsageReceived: (cost: Double?, freeRemaining: Int?) -> Unit = { _, _ -> },
    ): IosReaderAiTextResult {
        val settings = settingsProvider().sanitized()
        val account = accountStateProvider()
        val useWorker = account.isSignedIn && workerUrlProvider().isNotBlank() && !hasByokModel(feature)
        return if (useWorker) {
            callWorker(feature, text, context, onUpdate, onUsageReceived)
        } else {
            when (val request = ReaderByokTextRequests.build(settings, feature, text, context)) {
                ReaderByokTextRequestResult.Hidden -> IosReaderAiTextResult(error = "Reader AI features are hidden.")
                is ReaderByokTextRequestResult.MissingKey -> IosReaderAiTextResult(error = "Add a ${request.provider} API key in AI settings.")
                is ReaderByokTextRequestResult.MissingModel -> IosReaderAiTextResult(error = "Choose a model for ${request.featureName} in AI settings.")
                is ReaderByokTextRequestResult.Ready -> callByok(request.request, onUpdate)
            }
        }
    }

    private suspend fun callWorker(
        feature: ReaderAiFeature,
        text: String,
        context: String?,
        onUpdate: (String) -> Unit,
        onUsageReceived: (cost: Double?, freeRemaining: Int?) -> Unit,
    ): IosReaderAiTextResult {
        val token = authTokenProvider()
            ?: return IosReaderAiTextResult(error = "Sign in again to use this AI feature.")
        val body = when (feature) {
            ReaderAiFeature.DEFINE -> buildJsonObject { put("text", JsonPrimitive(text)) }
            ReaderAiFeature.SUMMARIZE -> buildJsonObject {
                put("content_type", JsonPrimitive("text"))
                put("data", JsonPrimitive(text))
            }
            ReaderAiFeature.RECAP -> buildJsonObject {
                put("past_summaries", buildJsonArray {})
                put("current_text", JsonPrimitive(text))
            }
        }.toString()
        val response = runCatching {
            IosReaderAiHttpClient.post(
                url = workerUrlProvider().removeSuffix("/") + when (feature) {
                    ReaderAiFeature.DEFINE -> "/define"
                    ReaderAiFeature.SUMMARIZE -> "/summarize"
                    ReaderAiFeature.RECAP -> "/recap"
                },
                body = body,
                headers = mapOf("Authorization" to "Bearer $token"),
            )
        }.getOrElse { error -> return IosReaderAiTextResult(error = error.message ?: "AI request failed.") }
        if (response.statusCode == 401) return IosReaderAiTextResult(error = "Sign in again to use this AI feature.")
        if (response.statusCode == 402 || response.body.contains("INSUFFICIENT_CREDITS", ignoreCase = true)) {
            onUsageReported(IosReaderAiUsage())
            return IosReaderAiTextResult(error = "Out of credits.")
        }
        if (response.statusCode !in 200..299) {
            return IosReaderAiTextResult(error = "AI request failed: HTTP ${response.statusCode}")
        }
        return parseWorkerStream(response.body, onUpdate, onUsageReceived)
    }

    private suspend fun callByok(
        request: ReaderByokTextRequest,
        onUpdate: (String) -> Unit,
    ): IosReaderAiTextResult {
        val (url, headers, body) = if (request.model.provider == "groq") {
            Triple(
                "https://api.groq.com/openai/v1/chat/completions",
                mapOf("Authorization" to "Bearer ${request.apiKey}"),
                buildGroqPayload(request),
            )
        } else {
            Triple(
                "https://generativelanguage.googleapis.com/v1beta/models/${request.model.name}:streamGenerateContent?key=${iosUrlEncode(request.apiKey)}",
                emptyMap(),
                buildGeminiPayload(request),
            )
        }
        val response = runCatching { IosReaderAiHttpClient.post(url, body, headers) }
            .getOrElse { error -> return IosReaderAiTextResult(error = error.message ?: "AI request failed.") }
        if (response.statusCode !in 200..299) return IosReaderAiTextResult(error = "AI provider error: HTTP ${response.statusCode}")
        return if (request.model.provider == "groq") {
            parseGroqStream(response.body, onUpdate)
        } else {
            parseGeminiStream(response.body, onUpdate)
        }
    }

    private fun buildGroqPayload(request: ReaderByokTextRequest): String = buildJsonObject {
        put("model", JsonPrimitive(request.model.name))
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("system"))
                put("content", JsonPrimitive(request.systemInstruction))
            })
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(request.userPrompt))
            })
        })
        put("temperature", JsonPrimitive(request.temperature))
        put("top_p", JsonPrimitive(0.95))
        put("max_tokens", JsonPrimitive(request.maxTokens))
        put("stream", JsonPrimitive(true))
        if (request.model.name.contains("qwen")) put("reasoning_effort", JsonPrimitive("none"))
    }.toString()

    private fun buildGeminiPayload(request: ReaderByokTextRequest): String = buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(request.userPrompt)) }) })
            })
        })
        put("systemInstruction", buildJsonObject {
            put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(request.systemInstruction)) }) })
        })
        put("generationConfig", buildJsonObject {
            put("temperature", JsonPrimitive(request.temperature))
            put("topP", JsonPrimitive(0.95))
            put("topK", JsonPrimitive(40))
            put("maxOutputTokens", JsonPrimitive(request.maxTokens))
            put("response_mime_type", JsonPrimitive("text/plain"))
            if (request.model.name.startsWith("gemini")) put("thinkingConfig", buildJsonObject { put("thinkingBudget", JsonPrimitive(0)) })
        })
    }.toString()

    private fun parseWorkerStream(
        body: String,
        onUpdate: (String) -> Unit,
        onUsageReceived: (cost: Double?, freeRemaining: Int?) -> Unit,
    ): IosReaderAiTextResult {
        val output = StringBuilder()
        var cost: Double? = null
        var freeRemaining: Int? = null
        body.lineSequence().forEach { line ->
            val json = line.trim().removePrefix("data:").trim()
            if (json.isBlank() || json == "[DONE]") return@forEach
            val obj = runCatching { IosReaderAiJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return@forEach
            val chunk = obj["chunk"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (chunk.isNotEmpty()) {
                output.append(chunk)
                onUpdate(chunk)
            }
            obj["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                return IosReaderAiTextResult(text = output.toString(), error = it, cost = cost, freeRemaining = freeRemaining)
            }
            obj["cost_deducted"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let { cost = it }
            obj["free_summaries_remaining"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.let { freeRemaining = it }
        }
        if (cost != null || freeRemaining != null) {
            onUsageReceived(cost, freeRemaining)
            onUsageReported(IosReaderAiUsage(cost, freeRemaining))
        }
        return if (output.isBlank()) IosReaderAiTextResult(error = "The AI service returned an empty response.", cost = cost, freeRemaining = freeRemaining)
        else IosReaderAiTextResult(text = output.toString(), cost = cost, freeRemaining = freeRemaining)
    }

    private fun parseGeminiStream(body: String, onUpdate: (String) -> Unit): IosReaderAiTextResult {
        val output = StringBuilder()
        for (obj in parseConcatenatedJsonObjects(body)) {
            val chunk = obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (chunk.isNotEmpty()) {
                output.append(chunk)
                onUpdate(chunk)
            }
            if (obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull == "SAFETY") {
                return IosReaderAiTextResult(text = output.toString(), error = "Blocked for safety reasons.")
            }
        }
        return if (output.isBlank()) IosReaderAiTextResult(error = "The AI provider returned an empty response.")
        else IosReaderAiTextResult(text = output.toString())
    }

    private fun parseGroqStream(body: String, onUpdate: (String) -> Unit): IosReaderAiTextResult {
        val output = StringBuilder()
        body.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("data:").trim()
            if (line.isBlank() || line == "[DONE]") return@forEach
            val chunk = runCatching {
                IosReaderAiJson.parseToJsonElement(line).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            if (chunk.isNotEmpty()) {
                output.append(chunk)
                onUpdate(chunk)
            }
        }
        return if (output.isBlank()) IosReaderAiTextResult(error = "The AI provider returned an empty response.")
        else IosReaderAiTextResult(text = output.toString())
    }

}

private data class IosReaderAiTextResult(
    val text: String = "",
    val error: String? = null,
    val cost: Double? = null,
    val freeRemaining: Int? = null,
)

private val IosReaderAiJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Gemini's stream endpoint may return adjacent JSON objects rather than NDJSON. */
private fun parseConcatenatedJsonObjects(body: String): List<JsonObject> {
    val objects = mutableListOf<JsonObject>()
    var start = -1
    var depth = 0
    var inString = false
    var escaped = false
    body.forEachIndexed { index, char ->
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            return@forEachIndexed
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) start = index
                depth++
            }
            '}' -> if (depth > 0 && --depth == 0 && start >= 0) {
                runCatching { IosReaderAiJson.parseToJsonElement(body.substring(start, index + 1)).jsonObject }
                    .onSuccess(objects::add)
                start = -1
            }
        }
    }
    return objects
}

private object IosReaderAiHttpClient {
    suspend fun post(url: String, body: String, headers: Map<String, String>): IosReaderAiHttpResponse {
        val nsUrl = NSURL.URLWithString(url) ?: error("Invalid AI URL")
        val request = NSMutableURLRequest.requestWithURL(
            URL = nsUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 120.0,
        ).apply {
            HTTPMethod = "POST"
            setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
            setValue("application/json", forHTTPHeaderField = "Accept")
            headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
            setHTTPBody(body.toNSData())
        }
        return suspendCancellableCoroutine { continuation ->
            val delegate = IosReaderAiHttpDelegate { result ->
                if (continuation.isActive) continuation.resumeWith(result)
            }
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration,
                delegate = delegate,
                delegateQueue = null,
            )
            val task = session.dataTaskWithRequest(request)
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }
}

private data class IosReaderAiHttpResponse(
    val statusCode: Int,
    val body: String,
)

private class IosReaderAiHttpDelegate(
    private val onComplete: (Result<IosReaderAiHttpResponse>) -> Unit,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private var response: NSURLResponse? = null
    private val data = NSMutableData.dataWithLength(0u) ?: NSMutableData()
    private var completed = false

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (Long) -> Unit,
    ) {
        response = didReceiveResponse
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        data.appendData(didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: platform.Foundation.NSError?) {
        if (completed) return
        completed = true
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
        val body = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
        if (didCompleteWithError != null && status == 0) {
            onComplete(Result.failure(IllegalStateException(didCompleteWithError.localizedDescription)))
        } else {
            onComplete(Result.success(IosReaderAiHttpResponse(status, body)))
        }
        session.finishTasksAndInvalidate()
    }
}

private fun iosUrlEncode(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val hexDigits = "0123456789ABCDEF"
    return buildString(value.length * 3) {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            val char = unsigned.toChar()
            if (char in unreserved) append(char) else {
                append('%')
                append(hexDigits[unsigned ushr 4])
                append(hexDigits[unsigned and 0x0F])
            }
        }
    }
}
