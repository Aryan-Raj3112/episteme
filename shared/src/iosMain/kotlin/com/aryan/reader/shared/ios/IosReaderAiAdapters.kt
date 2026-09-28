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
import com.aryan.reader.shared.mapSpendGuardHttpError
import com.aryan.reader.shared.mapSpendGuardStreamError
import com.aryan.reader.shared.hasSpendableBalance
import com.aryan.reader.shared.isFishVoiceListCacheFresh
import com.aryan.reader.shared.maskedReaderAiKey
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
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
import platform.CoreFoundation.CFRelease
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

/**
 * iOS uses the same shared model IDs and prompt contract as Android. The
 * native boundary is kept here so the reader host can inject auth/billing
 * state without putting Keychain or NSURLSession details in shared UI.
 */
internal const val IOS_READER_AI_WORKER_URL = "https://reader-ai.aryanrajttps.workers.dev"
// Fish Cloud TTS worker (same deployment the Android app uses as TTS_WORKER_URL).
internal const val IOS_TTS_WORKER_URL = "https://reader-tts-proxy.aryanrajivyms.workers.dev"

private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    if (bytes.isEmpty()) return NSMutableData.dataWithLength(0u) ?: NSMutableData()
    val data = NSMutableData.dataWithLength(bytes.size.toULong()) ?: NSMutableData()
    bytes.usePinned { pinned ->
        memcpy(data.mutableBytes, pinned.addressOf(0), bytes.size.toULong())
    }
    return data
}

internal fun NSData.iosToByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    return ByteArray(size).also { output ->
        output.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, size.toULong())
        }
    }
}

internal data class IosReaderAiUsage(
    val cost: Double? = null,
    val freeRemaining: Int? = null,
)

internal data class IosReaderAiAccountState(
    val isSignedIn: Boolean = false,
    val isProUser: Boolean = false,
    val credits: Int = 0,
    val walletMicros: Long = 0L,
    val walletMigrated: Boolean = false,
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

/**
 * Starred TTS voice ids (device + cloud reference ids share one set, same
 * as Android's TTS prefs). Favorites are not secrets: NSUserDefaults, not
 * the Keychain.
 */
private const val IOS_TTS_FAVORITE_VOICES_KEY = "reader.tts.favoriteVoices"

internal fun iosLoadTtsFavoriteVoices(): Set<String> {
    val stored = NSUserDefaults.standardUserDefaults.arrayForKey(IOS_TTS_FAVORITE_VOICES_KEY) as? List<*>
    return stored?.mapNotNull { it as? String }?.toSet().orEmpty()
}

internal fun iosSaveTtsFavoriteVoices(favorites: Set<String>) {
    NSUserDefaults.standardUserDefaults.setObject(favorites.toList(), forKey = IOS_TTS_FAVORITE_VOICES_KEY)
}

/** Persisted Fish voice language filter (raw selection, validated by the UI). */
private const val IOS_FISH_LANGUAGE_FILTER_KEY = "reader.tts.fish_language_filter"

internal fun iosLoadFishLanguageFilter(): String? {
    return NSUserDefaults.standardUserDefaults.stringForKey(IOS_FISH_LANGUAGE_FILTER_KEY)?.takeIf { it.isNotBlank() }
}

internal fun iosSaveFishLanguageFilter(selection: String) {
    NSUserDefaults.standardUserDefaults.setObject(selection, forKey = IOS_FISH_LANGUAGE_FILTER_KEY)
}

/**
 * Process-lifetime Fish voice-list cache (TTL is shared). Keyed by
 * credential for BYOK and by base URL for the worker (whose response
 * carries no per-user data); empty results are never cached.
 */
private data class IosCachedFishVoices(
    val voices: List<com.aryan.reader.shared.ReaderFishVoice>,
    val fetchedAtMs: Long
)

private val iosFishVoicesCache = mutableMapOf<String, IosCachedFishVoices>()

private fun iosCachedFishVoices(cacheKey: String): List<com.aryan.reader.shared.ReaderFishVoice>? {
    val cached = iosFishVoicesCache[cacheKey] ?: return null
    if (!isFishVoiceListCacheFresh(cached.fetchedAtMs, Clock.System.now().toEpochMilliseconds())) {
        iosFishVoicesCache.remove(cacheKey)
        return null
    }
    return cached.voices
}

private fun iosStoreFishVoices(cacheKey: String, voices: List<com.aryan.reader.shared.ReaderFishVoice>) {
    iosFishVoicesCache[cacheKey] = IosCachedFishVoices(voices, Clock.System.now().toEpochMilliseconds())
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
            val queryDictionary = query.toNSDictionary()
            val status = SecItemCopyMatching(queryDictionary.toCFDictionary(), result.ptr)
            if (status != errSecSuccess) return@memScoped ""
            val dataPointer = result.value ?: return@memScoped ""
            val dataRef: CFDataRef = dataPointer.reinterpret()
            try {
                val length = CFDataGetLength(dataRef).toInt()
                if (length <= 0) return@memScoped ""
                val bytes = CFDataGetBytePtr(dataRef) ?: return@memScoped ""
                val output = ByteArray(length)
                output.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), bytes, length.toULong())
                }
                output.decodeToString()
            } finally {
                CFRelease(dataPointer)
            }
        }
    }

    /**
     * Returns true when the value is stored (or updated). False means the Security
     * framework rejected the write (e.g. errSecMissingEntitlement on an unsigned
     * test host) — callers must not assume the previous secret was replaced.
     */
    fun write(account: String, value: String): Boolean {
        if (value.isBlank()) {
            delete(account)
            return true
        }
        val data = value.toNSData()
        val query = baseQuery(account)
        val attributes = mapOf(
            "v_Data" to data,
            "pdmn" to "cku",
        )
        val addDictionary = (query + attributes).toNSDictionary()
        val addStatus = SecItemAdd(addDictionary.toCFDictionary(), null)
        if (addStatus == errSecSuccess) return true
        if (addStatus == errSecDuplicateItem) {
            val queryDictionary = query.toNSDictionary()
            val attributesDictionary = attributes.toNSDictionary()
            return SecItemUpdate(queryDictionary.toCFDictionary(), attributesDictionary.toCFDictionary()) == errSecSuccess
        }
        return false
    }

    fun delete(account: String) {
        val queryDictionary = baseQuery(account).toNSDictionary()
        SecItemDelete(queryDictionary.toCFDictionary())
    }

    private fun baseQuery(account: String): Map<Any?, Any?> = mapOf(
        "class" to "genp",
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

    private fun NSMutableDictionary.toCFDictionary(): CFDictionaryRef =
        interpretCPointer(objcPtr())!!

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
            // Android parity (areReaderAiFeaturesEnabled): the managed worker
            // path is enabled for non-OSS builds regardless of sign-in, so
            // single-word AI define works signed-out just like Android. Only
            // the hidden toggle (and offline) hides the entry points.
            return networkAccess() && !settings.hideReaderAiFeatures &&
                (workerUrlProvider().isNotBlank() || settings.hasAnyAiKey)
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
        // Android parity (worker /define): smart dictionary is Pro-only.
        // BYOK bypasses the worker gate exactly like Android, so a
        // configured define model allows definitions without Pro.
        val account = accountStateProvider()
        if (!account.isProUser && !hasByokModel(ReaderAiFeature.DEFINE)) {
            return AiDefinitionResult(error = "Smart dictionary requires Pro.")
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
        return recapWithContext(emptyList(), textBeforeCurrentLocation)
    }

    internal suspend fun recapWithContext(
        pastSummaries: List<String>,
        currentText: String,
        onUpdate: (String) -> Unit = {},
    ): RecapResult {
        val trimmed = currentText.trim()
        if (trimmed.isBlank()) return RecapResult(error = "There is no reading context for a recap.")
        val gate = paidGenerationGate(freeProSummaryAllowed = false, feature = ReaderAiFeature.RECAP)
        if (gate != null) return RecapResult(error = gate)
        val result = textRequest(
            feature = ReaderAiFeature.RECAP,
            text = trimmed,
            context = null,
            onUpdate = onUpdate,
            onUsageReceived = { _, _ -> },
            pastSummaries = pastSummaries.filter { it.isNotBlank() },
        )
        return RecapResult(
            recap = result.text,
            error = result.error,
            cost = result.cost,
            freeRemaining = result.freeRemaining,
        )
    }

    /**
     * Android `executeRecapLogic` parity: past sections resolve through the
     * summary cache (misses summarize on the fly and backfill the cache),
     * then the final recap streams with progress callbacks. A failed past
     * chapter aborts with its error so spend-gate tokens still route.
     */
    internal suspend fun recapChained(
        request: com.aryan.reader.shared.ReaderRecapRequest,
        onProgress: (String) -> Unit = {},
        onUpdate: (String) -> Unit = {},
    ): RecapResult {
        if (request.currentText.trim().isBlank() && request.pastSections.all { it.text.isBlank() }) {
            return RecapResult(error = "There is no reading context for a recap.")
        }
        val pastSummaries = mutableListOf<String>()
        if (request.pastSections.isNotEmpty()) {
            onProgress("CHECKING_PAST")
            request.pastSections.forEachIndexed { offset, section ->
                val sectionIndex = request.sectionIndex - request.pastSections.size + offset
                onProgress("ANALYZING:${offset + 1}")
                val cached = request.summaryCache?.getSummary(request.bookTitle, sectionIndex)?.summary
                if (!cached.isNullOrBlank()) {
                    pastSummaries.add(cached)
                } else if (section.text.length > 100) {
                    val chapterSummary = StringBuilder()
                    val chapterResult = summarizeStreaming(
                        section.text,
                        onUsageReceived = { _, _ -> },
                        onUpdate = { chapterSummary.append(it) },
                    )
                    if (chapterResult.error != null) return RecapResult(
                        recap = null,
                        error = chapterResult.error,
                        cost = chapterResult.cost,
                        freeRemaining = chapterResult.freeRemaining,
                    )
                    val summary = chapterSummary.toString().trim().ifBlank { chapterResult.summary.orEmpty() }
                    if (summary.isNotBlank()) {
                        request.summaryCache?.saveSummary(request.bookTitle, sectionIndex, section.title, summary)
                        pastSummaries.add(summary)
                    }
                }
            }
        }
        onProgress("READING_POSITION")
        val currentText = request.currentText.trim().take(24_000)
        if (currentText.isBlank() && pastSummaries.isEmpty()) {
            return RecapResult(error = "There is no reading context for a recap.")
        }
        onProgress("GENERATING")
        return recapWithContext(pastSummaries, currentText.ifBlank { pastSummaries.joinToString("\n\n") }, onUpdate)
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
        // Android benchmark parity: migrated users spend the USD wallet,
        // legacy users spend credits.
        if (!hasByokModel(feature) && !(freeProSummaryAllowed && account.isProUser) && !hasSpendableBalance(account.credits, account.walletMicros)) {
            return if (account.walletMigrated) "This action needs balance. Top up your wallet to continue."
            else "This action needs credits."
        }
        return null
    }

    private suspend fun textRequest(
        feature: ReaderAiFeature,
        text: String,
        context: String?,
        onUpdate: (String) -> Unit,
        onUsageReceived: (cost: Double?, freeRemaining: Int?) -> Unit = { _, _ -> },
        pastSummaries: List<String> = emptyList(),
    ): IosReaderAiTextResult {
        val settings = settingsProvider().sanitized()
        val account = accountStateProvider()
        val useWorker = workerUrlProvider().isNotBlank() && !hasByokModel(feature) &&
            (feature == ReaderAiFeature.DEFINE || account.isSignedIn)
        return if (useWorker) {
            callWorker(feature, text, context, onUpdate, onUsageReceived, pastSummaries)
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
        pastSummaries: List<String>,
    ): IosReaderAiTextResult {
        // Every worker feature (including /define) requires auth now; the
        // adapter pro gate above keeps this a friendly client-side error.
        val authRequired = true
        val token = authTokenProvider()
        if (authRequired && token.isNullOrBlank()) {
            return IosReaderAiTextResult(error = "Sign in again to use this AI feature.")
        }
        val body = when (feature) {
            ReaderAiFeature.DEFINE -> buildJsonObject { put("text", JsonPrimitive(text)) }
            ReaderAiFeature.SUMMARIZE -> buildJsonObject {
                put("content_type", JsonPrimitive("text"))
                put("data", JsonPrimitive(text))
            }
            ReaderAiFeature.RECAP -> buildJsonObject {
                put("past_summaries", buildJsonArray {
                    pastSummaries.forEach { add(JsonPrimitive(it)) }
                })
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
                headers = token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty(),
            )
        }.getOrElse { error -> return IosReaderAiTextResult(error = error.message ?: "AI request failed.") }
        if (response.statusCode == 401) return IosReaderAiTextResult(error = "Sign in again to use this AI feature.")
        // Android parity (mapAiHttpError): HTTP errors become routing tokens
        // ("RATE_LIMITED:<s>", "DAILY_SPEND_LIMIT:<s>", "INSUFFICIENT_CREDITS")
        // that the host turns into notices/dialogs. Tokens are never shown.
        if (response.statusCode == 429) {
            return IosReaderAiTextResult(error = mapSpendGuardHttpError(429, response.body))
        }
        val workerError = workerErrorMessage(response.body)
        if (response.statusCode == 402 || response.body.contains("INSUFFICIENT_CREDITS", ignoreCase = true)) {
            onUsageReported(IosReaderAiUsage())
            val token = mapSpendGuardHttpError(response.statusCode, response.body)
                ?: if (response.body.contains("INSUFFICIENT_CREDITS", ignoreCase = true)) "INSUFFICIENT_CREDITS" else null
            return IosReaderAiTextResult(
                error = token ?: workerError ?: "AI request failed."
            )
        }
        if (response.statusCode !in 200..299) {
            if (response.statusCode == 401) return IosReaderAiTextResult(error = "Sign in again to use this AI feature.")
            return IosReaderAiTextResult(error = workerError ?: "AI request failed: HTTP ${response.statusCode}")
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
            obj["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { streamError ->
                // Concurrency-slot throttle arrives as a stream payload
                // ({error, retry_after_seconds}); Android parity
                // (mapAiStreamError): keep the routing token so the host can
                // turn it into a notice/dialog. Tokens are never shown.
                val retry = obj["retry_after_seconds"]?.jsonPrimitive?.intOrNull ?: 0
                val text = mapSpendGuardStreamError(streamError, retry)
                return IosReaderAiTextResult(text = output.toString(), error = text, cost = cost, freeRemaining = freeRemaining)
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
        var inThink = false
        var thinkBuffer = ""

        fun cleanChunk(text: String): String {
            thinkBuffer += text
            val visible = StringBuilder()
            while (true) {
                if (inThink) {
                    val end = thinkBuffer.indexOf("</think>")
                    if (end == -1) {
                        if (thinkBuffer.length > 7) thinkBuffer = thinkBuffer.takeLast(7)
                        break
                    }
                    inThink = false
                    thinkBuffer = thinkBuffer.substring(end + "</think>".length)
                } else {
                    val start = thinkBuffer.indexOf("<think>")
                    if (start == -1) {
                        if (thinkBuffer.length > 6) {
                            visible.append(thinkBuffer.dropLast(6))
                            thinkBuffer = thinkBuffer.takeLast(6)
                        }
                        break
                    }
                    visible.append(thinkBuffer.substring(0, start))
                    inThink = true
                    thinkBuffer = thinkBuffer.substring(start + "<think>".length)
                }
            }
            return visible.toString()
        }

        body.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("data:").trim()
            if (line.isBlank() || line == "[DONE]") return@forEach
            val chunk = runCatching {
                IosReaderAiJson.parseToJsonElement(line).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            val visible = cleanChunk(chunk)
            if (visible.isNotEmpty()) {
                output.append(visible)
                onUpdate(visible)
            }
        }
        if (!inThink && thinkBuffer.isNotBlank()) {
            output.append(thinkBuffer)
            onUpdate(thinkBuffer)
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

private fun workerErrorMessage(body: String): String? {
    val normalized = body.lowercase()
    return when {
        "insufficient_credits" in normalized -> "Out of credits."
        "summary_limit" in normalized || ("free summar" in normalized && "limit" in normalized) ->
            "Free summaries are used up for today."
        "define_requires_pro" in normalized -> "Smart dictionary requires Pro."
        "authentication required" in normalized || "unauthorized" in normalized ->
            "Sign in again to use this AI feature."
        else -> runCatching {
            val json = IosReaderAiJson.parseToJsonElement(body).jsonObject
            json["error"]?.jsonPrimitive?.contentOrNull
                ?: json["detail"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}

/**
 * Fish Official's author id: the professional voices Fish themselves post
 * (the `licensed=true` pool is only 7 voices and a subset of this catalog).
 */
private const val FISH_OFFICIAL_AUTHOR_ID = "d8b0991f96b44e489422ca2ddf0bd31d"

/**
 * Localized copy for a chained-recap progress token (`recapChained`
 * emits stable tokens; the host resolves them so progress localizes like
 * every other reader string). Unknown tokens pass through as-is.
 */
internal data class RecapProgressCopy(
    val key: String,
    val fallback: String,
    val chapterNumber: Int? = null
)

internal fun recapProgressCopy(token: String): RecapProgressCopy {
    if (token.startsWith("ANALYZING:")) {
        val number = token.substringAfter(":").toIntOrNull() ?: 0
        return RecapProgressCopy(
            key = "ai_recap_analyzing_chapter",
            fallback = "Analyzing Chapter %1\$d...",
            chapterNumber = number,
        )
    }
    return when (token) {
        "CHECKING_PAST" -> RecapProgressCopy("ai_recap_checking_past", "Checking past chapters...")
        "READING_POSITION" -> RecapProgressCopy("ai_recap_reading_position", "Reading current position...")
        "GENERATING" -> RecapProgressCopy("ai_recap_generating", "Generating Recap...")
        else -> RecapProgressCopy("ai_thinking", token)
    }
}

/**
 * Fish voice catalog for the iOS voice picker (Android `fetchFishVoices` +
 * `fetchCloudFishVoices` parity). BYOK Fish key lists the user's own voice
 * library plus Fish Official's own catalog; otherwise the credited worker
 * catalog (`GET /v2/voices`). Empty when neither credential is available or
 * the call fails.
 */
internal suspend fun iosFetchFishVoices(
    fishKey: String,
    workerBaseUrl: String,
    authToken: String?,
): List<com.aryan.reader.shared.ReaderFishVoice> {
    if (fishKey.isNotBlank()) {
        val cacheKey = "byok:$fishKey"
        iosCachedFishVoices(cacheKey)?.let { return it }
        // Own + public fetch concurrently; each side degrades independently
        // so one slow/failed listing still leaves the other instead of an
        // empty picker.
        val (own, public) = coroutineScope {
            awaitAll(
                async { runCatching { iosFetchFishModelPages(fishKey, selfOnly = true) }.getOrDefault(emptyList()) },
                async { runCatching { iosFetchFishModelPages(fishKey, selfOnly = false) }.getOrDefault(emptyList()) },
            )
        }
        val seen = own.mapTo(mutableSetOf()) { it.referenceId }
        val merged = own + public.filter { it.referenceId.isNotBlank() && seen.add(it.referenceId) }
        if (merged.isNotEmpty()) {
            iosStoreFishVoices(cacheKey, merged)
            return merged
        }
    }
    val base = workerBaseUrl.trim().removeSuffix("/")
    val token = authToken
    if (base.isBlank() || token.isNullOrBlank()) return emptyList()
    // The worker response carries no per-user data (auth only gates access),
    // so it is cached by base URL, not by token.
    val workerCacheKey = "worker:$base"
    iosCachedFishVoices(workerCacheKey)?.let { return it }
    val response = runCatching {
        IosReaderAiHttpClient.get(
            "$base/v2/voices",
            mapOf("Authorization" to "Bearer $token"),
        )
    }.getOrNull() ?: return emptyList()
    if (response.statusCode !in 200..299) return emptyList()
    val voices = runCatching {
        IosReaderAiJson.parseToJsonElement(response.body).jsonObject["voices"]?.jsonArray
    }.getOrNull() ?: return emptyList()
    return voices.mapNotNull { element ->
        val voice = element.jsonObject
        val referenceId = voice["reference_id"]?.jsonPrimitive?.contentOrNull
            ?.ifBlank { voice["id"]?.jsonPrimitive?.contentOrNull }
            .orEmpty()
        if (referenceId.isBlank()) return@mapNotNull null
        com.aryan.reader.shared.ReaderFishVoice(
            id = voice["id"]?.jsonPrimitive?.contentOrNull?.ifBlank { referenceId } ?: referenceId,
            referenceId = referenceId,
            title = voice["name"]?.jsonPrimitive?.contentOrNull
                ?.ifBlank { voice["title"]?.jsonPrimitive?.contentOrNull }
                ?: referenceId,
            description = voice["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            languages = voice["languages"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
            }.orEmpty(),
            sampleAudioUrl = voice["sample_audio"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.also { if (it.isNotEmpty()) iosStoreFishVoices(workerCacheKey, it) }
}

private suspend fun iosFetchFishModelPages(
    fishKey: String,
    selfOnly: Boolean,
    maxPages: Int = 4,
    pageSize: Int = 100,
): List<com.aryan.reader.shared.ReaderFishVoice> {
    val voices = mutableListOf<com.aryan.reader.shared.ReaderFishVoice>()
    repeat(maxPages) { index ->
        // NOTE: the listing endpoint is NOT under /v1 (/v1/model 404s).
        // Fish Official's catalog for the public side: the professional
        // voices Fish themselves post, no random user clones.
        val scopeParam = if (selfOnly) "&self=true" else "&author_id=$FISH_OFFICIAL_AUTHOR_ID"
        val response = runCatching {
            IosReaderAiHttpClient.get(
                "https://api.fish.audio/model?page_size=$pageSize&page_number=${index + 1}$scopeParam",
                mapOf("Authorization" to "Bearer $fishKey"),
            )
        }.getOrNull() ?: return voices
        if (response.statusCode !in 200..299) return voices
        val body = runCatching {
            IosReaderAiJson.parseToJsonElement(response.body).jsonObject
        }.getOrNull() ?: return voices
        val items = body["items"]?.jsonArray ?: return voices
        items.mapNotNullTo(voices) { iosFishVoiceFromModel(it) }
        val hasMore = body["has_more"]?.jsonPrimitive?.booleanOrNull
            ?: (items.size >= pageSize)
        if (!hasMore) return voices
    }
    return voices
}

private fun iosFishVoiceFromModel(element: JsonElement): com.aryan.reader.shared.ReaderFishVoice? {
    val model = runCatching { element.jsonObject }.getOrNull() ?: return null
    // Skip voices that cannot synthesize; `state` is absent on older entries.
    val state = model["state"]?.jsonPrimitive?.contentOrNull
    if (!state.isNullOrBlank() && state != "trained") return null
    // Only TTS-capable models belong in a TTS picker (`type` is absent on
    // older entries — those are kept).
    val type = model["type"]?.jsonPrimitive?.contentOrNull
    if (!type.isNullOrBlank() && type != "tts") return null
    val rawRef = model["_id"]?.jsonPrimitive?.contentOrNull
        ?: model["id"]?.jsonPrimitive?.contentOrNull
        ?: model["reference_id"]?.jsonPrimitive?.contentOrNull
        ?: return null
    val referenceId = rawRef.replace("-", "")
    if (referenceId.isBlank()) return null
    val languages = model["languages"]?.jsonArray?.mapNotNull {
        it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
    }.orEmpty()
    return com.aryan.reader.shared.ReaderFishVoice(
        id = referenceId,
        referenceId = referenceId,
        title = model["title"]?.jsonPrimitive?.contentOrNull
            ?: model["name"]?.jsonPrimitive?.contentOrNull
            ?: "Fish voice",
        description = model["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        languages = languages,
        sampleAudioUrl = model["samples"]?.jsonArray
            ?.mapNotNull { it.jsonObject["audio"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
            ?.firstOrNull().orEmpty(),
    )
}

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

/**
 * App Check token source, a plain (non-suspend) provider so the declaration
 * stays representable to Swift. Swift pushes fresh tokens into the bridge
 * (see ReaderIosBridge.updateAppCheckToken) and ReaderIosApp wires the
 * cached value here. Null until wired — requests then simply omit the header
 * and the server logs the miss while enforcement is off.
 */
internal var iosAppCheckTokenProvider: (() -> String?)? = null

internal object IosReaderAiHttpClient {
    suspend fun post(url: String, body: String, headers: Map<String, String>): IosReaderAiHttpResponse {
        return request(url = url, method = "POST", body = body.toNSData(), headers = headers)
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): IosReaderAiHttpResponse {
        return request(url = url, method = "GET", body = null, headers = headers)
    }

    suspend fun postBytes(url: String, body: String, headers: Map<String, String>): IosReaderAiHttpResponse {
        return request(url = url, method = "POST", body = body.toNSData(), headers = headers)
    }

    private suspend fun request(
        url: String,
        method: String,
        body: platform.Foundation.NSData?,
        headers: Map<String, String>,
    ): IosReaderAiHttpResponse {
        val nsUrl = NSURL.URLWithString(url) ?: error("Invalid AI URL")
        val request = NSMutableURLRequest.requestWithURL(
            URL = nsUrl,
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = 120.0,
        ).apply {
            HTTPMethod = method
            setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
            setValue("application/json", forHTTPHeaderField = "Accept")
            headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
            // Attestation (omitted when unavailable; the server logs the miss).
            iosAppCheckTokenProvider?.invoke()?.let { setValue(it, forHTTPHeaderField = "X-Firebase-AppCheck") }
            if (body != null) setHTTPBody(body)
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

internal data class IosReaderAiHttpResponse(
    val statusCode: Int,
    val body: String,
    // Raw bytes (TTS audio) + lowercased response headers (cost metadata).
    // Text callers keep using `body`; binary callers use `bodyBytes`.
    val bodyBytes: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
)

internal class IosReaderAiHttpDelegate(
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
        val httpResponse = response as? NSHTTPURLResponse
        val status = httpResponse?.statusCode?.toInt() ?: 0
        val body = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
        // Header names are case-insensitive; lowercase once for lookups.
        val headers = (httpResponse?.allHeaderFields as? Map<Any?, Any?>).orEmpty()
            .mapNotNull { (key, value) ->
                val name = (key as? String)?.lowercase()
                val stringValue = value as? String
                if (name != null && stringValue != null) name to stringValue else null
            }.toMap()
        if (didCompleteWithError != null && status == 0) {
            onComplete(Result.failure(IllegalStateException(didCompleteWithError.localizedDescription)))
        } else {
            onComplete(Result.success(IosReaderAiHttpResponse(status, body, data.iosToByteArray(), headers)))
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
