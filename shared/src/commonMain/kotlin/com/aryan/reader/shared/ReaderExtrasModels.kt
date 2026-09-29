package com.aryan.reader.shared

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.logSharedReaderDiagnostic
import kotlin.math.floor
import kotlin.math.roundToInt

// LEGACY pre-Fish live model. Kept so older clients (and their stored
// tts_model values) keep working against the legacy worker routes.
const val GEMINI_CLOUD_TTS_MODEL = "gemini-3.1-flash-live-preview"
const val GEMINI_CLOUD_TTS_MODEL_ID = "gemini:$GEMINI_CLOUD_TTS_MODEL"
// Proper (non-Live) Gemini TTS models, selectable via AI Keys & Models (BYOK).
// Listed dynamically from the Gemini ListModels API when possible; these are
// the manual fallback (prices are never hardcoded — shown only if an API
// provides them).
const val GEMINI_TTS_MODEL_LITE = "gemini-3.8-flash-lite-tts"
const val GEMINI_TTS_MODEL_PREVIEW = "gemini-3.1-flash-tts-preview"
const val GEMINI_TTS_MODEL_LITE_ID = "gemini:$GEMINI_TTS_MODEL_LITE"
const val GEMINI_TTS_MODEL_PREVIEW_ID = "gemini:$GEMINI_TTS_MODEL_PREVIEW"
// Fish TTS via BYOK (user's Fish key, direct api.fish.audio calls, no credits).
const val FISH_TTS_MODEL = "s2.1-pro"
const val FISH_TTS_MODEL_ID = "fish:$FISH_TTS_MODEL"

/**
 * Cloud TTS master switch: enabled when the stored TTS model names a cloud
 * backend (legacy Gemini Live or Fish), disabled for device speech ("").
 * Single source of truth for the reader toggle and the speech engine — they
 * must never disagree about whether cloud mode is on.
 */
fun isCloudTtsModelEnabled(ttsModel: String): Boolean =
    ttsModel == GEMINI_CLOUD_TTS_MODEL_ID || ttsModel == FISH_TTS_MODEL_ID
const val DEFAULT_CLOUD_TTS_SPEAKER_ID = "Aoede"
const val READER_TTS_CHUNK_MAX_LENGTH = 250
private const val ReaderTtsStartTraceLogTag = "EpistemeDesktopTtsStartTrace"

data class ReaderCloudTtsVoice(
    val id: String,
    val name: String,
    val description: String
)

enum class ReaderAiFeature(val displayName: String) {
    DEFINE("Smart dictionary"),
    SUMMARIZE("Summaries"),
    RECAP("Recaps")
}

data class ReaderAiModelOption(
    val provider: String,
    val name: String,
    val label: String = "${provider.replaceFirstChar { it.uppercaseChar() }} - $name",
    // Displayed next to the label only when a listing API provides pricing.
    // Never hardcoded: null means "no price info".
    val priceLabel: String? = null
) {
    val id: String = "$provider:$name"
}

// A Fish voice as exposed by the Fish API (GET /model) or the worker catalog
// (GET /v2/voices). [id] is the alias in the worker catalog or the Fish
// model _id for BYOK voices; [referenceId] is what TTS requests send.
data class ReaderFishVoice(
    val id: String,
    val referenceId: String,
    val title: String,
    val description: String = "",
    // Language codes from the Fish model catalog (e.g. ["en"]). Empty when
    // the source carries no language info (static catalog, Gemini rows).
    val languages: List<String> = emptyList(),
    // Free pre-generated preview audio (Fish-hosted MP3). Playing it costs
    // nobody anything; empty when the voice exposes no sample.
    val sampleAudioUrl: String = ""
)

data class ReaderAiByokSettings(
    val geminiKey: String = "",
    val groqKey: String = "",
    val fishKey: String = "",
    val useOneModel: Boolean = true,
    val modelForAll: String = "",
    val defineModel: String = "",
    val summarizeModel: String = "",
    val recapModel: String = "",
    val ttsModel: String = "",
    val hideReaderAiFeatures: Boolean = false,
    // Provider-specific: Gemini prebuilt voice name, or Fish alias/reference_id.
    val ttsSpeakerId: String = DEFAULT_CLOUD_TTS_SPEAKER_ID,
    val serverBackedReaderAiFeatures: Boolean = false,
    val serverBackedCloudTts: Boolean = false
) {
    fun sanitized(): ReaderAiByokSettings {
        val knownTextModelIds = ReaderAiModelOptions.mapTo(mutableSetOf()) { it.id }
        val knownTtsModelIds = ReaderTtsByokOptions.mapTo(mutableSetOf()) { it.id } + GEMINI_CLOUD_TTS_MODEL_ID
        return copy(
            geminiKey = geminiKey.trim(),
            groqKey = groqKey.trim(),
            fishKey = fishKey.trim(),
            modelForAll = modelForAll.takeIf { it in knownTextModelIds }.orEmpty(),
            defineModel = defineModel.takeIf { it in knownTextModelIds }.orEmpty(),
            summarizeModel = summarizeModel.takeIf { it in knownTextModelIds }.orEmpty(),
            recapModel = recapModel.takeIf { it in knownTextModelIds }.orEmpty(),
            ttsModel = ttsModel.takeIf { it in knownTtsModelIds }.orEmpty(),
            ttsSpeakerId = ttsSpeakerId.ifBlank { DEFAULT_CLOUD_TTS_SPEAKER_ID }
        )
    }

    fun modelIdFor(feature: ReaderAiFeature): String {
        return if (useOneModel) {
            modelForAll
        } else {
            when (feature) {
                ReaderAiFeature.DEFINE -> defineModel
                ReaderAiFeature.SUMMARIZE -> summarizeModel
                ReaderAiFeature.RECAP -> recapModel
            }
        }
    }

    fun apiKeyFor(provider: String): String {
        return when (provider) {
            "gemini" -> geminiKey
            "groq" -> groqKey
            "fish" -> fishKey
            else -> ""
        }.trim()
    }

    // Provider selected for BYOK TTS ("gemini", "fish", or "" when unset).
    val ttsProvider: String get() = ttsModel.substringBefore(':', "").takeIf { ttsModel.contains(':') }.orEmpty()

    val hasAnyAiKey: Boolean get() = geminiKey.isNotBlank() || groqKey.isNotBlank() || fishKey.isNotBlank()
    val areReaderAiFeaturesAvailable: Boolean get() = !hideReaderAiFeatures && (serverBackedReaderAiFeatures || hasAnyAiKey)
    // LEGACY: pre-Fish live-model check. Kept for older clients (notably iOS,
    // which still gates on it); Android uses isAnyByokTtsAvailable.
    val isByokCloudTtsAvailable: Boolean get() = geminiKey.isNotBlank() && ttsModel == GEMINI_CLOUD_TTS_MODEL_ID
    val isGeminiRestByokTtsAvailable: Boolean get() =
        geminiKey.isNotBlank() && (ttsModel == GEMINI_TTS_MODEL_LITE_ID || ttsModel == GEMINI_TTS_MODEL_PREVIEW_ID)
    val isFishByokTtsAvailable: Boolean get() = fishKey.isNotBlank() && ttsModel == FISH_TTS_MODEL_ID
    val isAnyByokTtsAvailable: Boolean get() = isByokCloudTtsAvailable || isGeminiRestByokTtsAvailable || isFishByokTtsAvailable
    val isCloudTtsAvailable: Boolean get() = serverBackedCloudTts || isAnyByokTtsAvailable
}

/**
 * Manual fallback for the BYOK TTS model picker. Clients prefer a live list:
 * Gemini ListModels filtered to TTS-capable models, Fish voices from the Fish
 * API — both with prices only when the API provides them.
 */
val ReaderTtsByokOptions = listOf(
    ReaderAiModelOption("gemini", GEMINI_TTS_MODEL_LITE),
    ReaderAiModelOption("gemini", GEMINI_TTS_MODEL_PREVIEW),
    ReaderAiModelOption("fish", FISH_TTS_MODEL)
)

val ReaderAiModelOptions = listOf(
    ReaderAiModelOption("groq", "qwen/qwen3-32b"),
    ReaderAiModelOption("groq", "llama-3.3-70b-versatile"),
    ReaderAiModelOption("groq", "llama-3.1-8b-instant"),
    ReaderAiModelOption("gemini", "gemma-4-26b-a4b-it"),
    ReaderAiModelOption("gemini", "gemma-4-31b-it"),
    ReaderAiModelOption("gemini", "gemini-flash-lite-latest"),
    ReaderAiModelOption("gemini", "gemini-2.5-flash-lite"),
    ReaderAiModelOption("gemini", "gemini-3.1-flash-lite-preview")
)

val ReaderCloudTtsVoices = listOf(
    ReaderCloudTtsVoice("Zephyr", "Zephyr", "Bright, Higher pitch"),
    ReaderCloudTtsVoice("Puck", "Puck", "Upbeat, Middle pitch"),
    ReaderCloudTtsVoice("Charon", "Charon", "Informative, Lower pitch"),
    ReaderCloudTtsVoice("Kore", "Kore", "Firm, Middle pitch"),
    ReaderCloudTtsVoice("Fenrir", "Fenrir", "Excitable, Lower middle pitch"),
    ReaderCloudTtsVoice("Leda", "Leda", "Youthful, Higher pitch"),
    ReaderCloudTtsVoice("Orus", "Orus", "Firm, Lower middle pitch"),
    ReaderCloudTtsVoice("Aoede", "Aoede", "Breezy, Middle pitch"),
    ReaderCloudTtsVoice("Callirrhoe", "Callirrhoe", "Easy-going, Middle pitch"),
    ReaderCloudTtsVoice("Autonoe", "Autonoe", "Bright, Middle pitch"),
    ReaderCloudTtsVoice("Enceladus", "Enceladus", "Breathy, Lower pitch"),
    ReaderCloudTtsVoice("Iapetus", "Iapetus", "Clear, Lower middle pitch"),
    ReaderCloudTtsVoice("Umbriel", "Umbriel", "Easy-going, Lower middle pitch"),
    ReaderCloudTtsVoice("Algieba", "Algieba", "Smooth, Lower pitch"),
    ReaderCloudTtsVoice("Despina", "Despina", "Smooth, Middle pitch"),
    ReaderCloudTtsVoice("Erinome", "Erinome", "Clear, Middle pitch"),
    ReaderCloudTtsVoice("Algenib", "Algenib", "Gravelly, Lower pitch"),
    ReaderCloudTtsVoice("Rasalgethi", "Rasalgethi", "Informative, Middle pitch"),
    ReaderCloudTtsVoice("Laomedeia", "Laomedeia", "Upbeat, Higher pitch"),
    ReaderCloudTtsVoice("Achernar", "Achernar", "Soft, Higher pitch"),
    ReaderCloudTtsVoice("Alnilam", "Alnilam", "Firm, Lower middle pitch"),
    ReaderCloudTtsVoice("Schedar", "Schedar", "Even, Lower middle pitch"),
    ReaderCloudTtsVoice("Gacrux", "Gacrux", "Mature, Middle pitch"),
    ReaderCloudTtsVoice("Pulcherrima", "Pulcherrima", "Forward, Middle pitch"),
    ReaderCloudTtsVoice("Achird", "Achird", "Friendly, Lower middle pitch"),
    ReaderCloudTtsVoice("Zubenelgenubi", "Zubenelgenubi", "Casual, Lower middle pitch"),
    ReaderCloudTtsVoice("Vindemiatrix", "Vindemiatrix", "Gentle, Middle pitch"),
    ReaderCloudTtsVoice("Sadachbia", "Sadachbia", "Lively, Lower pitch"),
    ReaderCloudTtsVoice("Sadaltager", "Sadaltager", "Lively, Lower pitch"),
    ReaderCloudTtsVoice("Sulafat", "Sulafat", "Warm, Middle pitch")
)

val ReaderCloudTtsSpeakers = ReaderCloudTtsVoices.map { it.id }

fun readerCloudTtsVoiceById(id: String): ReaderCloudTtsVoice? {
    return ReaderCloudTtsVoices.firstOrNull { it.id == id }
}

/**
 * Whether the user can spend on metered features: either legacy credits
 * (older balances, older app versions) or the USD wallet (micro-dollars).
 */
fun hasSpendableBalance(credits: Int, walletMicros: Long): Boolean {
    return credits > 0 || walletMicros > 0
}

/**
 * Balance chip text: USD wallet once migrated, legacy "⭐ N" otherwise.
 */
fun spendableDisplayText(credits: Int, walletMicros: Long, walletMigrated: Boolean): String {
    if (walletMigrated) return formatMicrosUsd(walletMicros)
    return "⭐ $credits"
}

/**
 * Formats an integer micro-dollar wallet balance as USD ("$10.25").
 * Sub-cent fractions truncate in display only; the ledger keeps micros.
 */
fun formatMicrosUsd(micros: Long): String {
    val negative = micros < 0
    val abs = if (negative) -micros else micros
    val dollars = abs / 1_000_000L
    val cents = (abs % 1_000_000L) / 10_000L
    return (if (negative) "-$" else "$") + dollars.toString() + "." + cents.toString().padStart(2, '0')
}

/**
 * Parses worker spend-guard error bodies:
 * {"error":"RATE_LIMITED"|"DAILY_SPEND_LIMIT","retry_after_seconds":N}.
 * Returns (kind, retrySeconds) or null when the body is anything else.
 */
fun parseSpendGuardError(body: String?): Pair<String, Int>? {
    if (body.isNullOrBlank()) return null
    val kind = Regex("\"error\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.getOrNull(1)
        ?: return null
    if (kind != "RATE_LIMITED" && kind != "DAILY_SPEND_LIMIT") return null
    val retry = Regex("\"retry_after_seconds\"\\s*:\\s*(\\d+)").find(body)
        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    return kind to retry
}

/**
 * Parses the client-side sentinel "RATE_LIMITED:<s>" / "DAILY_SPEND_LIMIT:<s>"
 * carried through TtsAudioData.error / AI onError strings.
 */
fun parseSpendGuardSentinel(sentinel: String?): Pair<String, Int>? {
    if (sentinel.isNullOrBlank()) return null
    val head = sentinel.substringBefore(":")
    if (head != "RATE_LIMITED" && head != "DAILY_SPEND_LIMIT") return null
    val retry = sentinel.substringAfter(":", "").toIntOrNull() ?: 0
    return head to retry
}

fun spendGuardSentinel(kind: String, retryAfterSeconds: Int): String {
    return "$kind:${retryAfterSeconds.coerceAtLeast(0)}"
}

/**
 * Maps worker HTTP errors to client error tokens (Android benchmark parity):
 * 402 with a DAILY_SPEND_LIMIT body -> "DAILY_SPEND_LIMIT:<s>" sentinel,
 * other 402 / INSUFFICIENT_CREDITS -> "INSUFFICIENT_CREDITS" (callers route it
 * to the out-of-balance dialog), 429 -> "RATE_LIMITED:<s>" sentinel, anything
 * else -> null (caller falls back to generic handling). Callers render
 * user-facing copy from the token; the token itself is never shown.
 */
fun mapSpendGuardHttpError(responseCode: Int, errorBody: String?): String? {
    if (responseCode == 402) {
        val parsed = parseSpendGuardError(errorBody)
        return if (parsed?.first == "DAILY_SPEND_LIMIT") {
            spendGuardSentinel(parsed.first, parsed.second)
        } else {
            "INSUFFICIENT_CREDITS"
        }
    }
    if (responseCode == 429) {
        val parsed = parseSpendGuardError(errorBody)
        return spendGuardSentinel("RATE_LIMITED", parsed?.second ?: 30)
    }
    return null
}

/**
 * Maps a worker stream error payload to a client token, preserving the retry
 * window for the concurrency-slot path ({error, retry_after_seconds}).
 */
fun mapSpendGuardStreamError(error: String, retryAfterSeconds: Int): String {
    if ((error == "RATE_LIMITED" || error == "DAILY_SPEND_LIMIT")) {
        return spendGuardSentinel(error, retryAfterSeconds)
    }
    return error
}

/**
 * cost_deducted unit depends on ledger: migrated users pay dollars, legacy
 * users pay credits. Never show a raw dollar number as "credits" or vice versa.
 */
fun formatAiCostDeducted(cost: Double, walletMigrated: Boolean): String {
    if (!walletMigrated) {
        val text = if (cost == floor(cost)) cost.toLong().toString() else cost.toString()
        return "$text credits"
    }
    val cents = (cost * 100).roundToInt().coerceAtLeast(1)
    return "$" + (cents / 100).toString() + "." + (cents % 100).toString().padStart(2, '0')
}

/**
 * Short countdown for rate-limit / spend-cap notices: "45s", "3m 20s", "11h 05m".
 */
fun formatSpendGuardCountdown(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    if (s < 60) return "${s}s"
    val m = s / 60
    if (m < 60) return "${m}m ${(s % 60).toString().padStart(2, '0')}s"
    return "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}

fun formatReaderTtsBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "${(value * 10).toInt() / 10.0} ${units[unitIndex]}"
}

fun splitReaderTextIntoTtsChunks(
    text: String,
    maxLength: Int = READER_TTS_CHUNK_MAX_LENGTH
): List<String> {
    if (text.isBlank()) return emptyList()
    val sentenceBoundaryRegex = Regex("""(?<!\w\.\w.)(?<![A-Z][a-z]\.)(?<=[.?!\n])\s+""")
    val sentences = text.trim()
        .split(sentenceBoundaryRegex)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (sentences.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    val currentChunk = StringBuilder()
    fun flush() {
        if (currentChunk.isNotEmpty()) {
            chunks += currentChunk.toString()
            currentChunk.clear()
        }
    }

    sentences.forEach { sentence ->
        if (sentence.length > maxLength) {
            flush()
            chunks += sentence
            return@forEach
        }
        if (currentChunk.isNotEmpty() && currentChunk.length + sentence.length + 1 > maxLength) {
            flush()
        }
        if (currentChunk.isNotEmpty()) currentChunk.append(' ')
        currentChunk.append(sentence)
    }
    flush()
    return chunks
}

fun readerAiModelById(id: String): ReaderAiModelOption? {
    return ReaderAiModelOptions.firstOrNull { it.id == id }
}

/**
 * Client-side TTL for Fish voice-list caches (the worker additionally
 * caches 5 minutes server-side). Voice catalogs change rarely; an hour
 * avoids refetching on every settings visit without going stale.
 */
const val FISH_VOICE_LIST_CACHE_TTL_MS = 60L * 60L * 1000L

/** Pure TTL check for timestamped voice-list cache entries. */
fun isFishVoiceListCacheFresh(fetchedAtMs: Long, nowMs: Long): Boolean {
    return nowMs - fetchedAtMs <= FISH_VOICE_LIST_CACHE_TTL_MS
}

/**
 * True when the user picked a known model for [feature] and saved that
 * provider's key, so BYOK overrides the credited worker for the feature
 * (Pro parity with iOS, whose adapter gates on the same condition).
 */
fun ReaderAiByokSettings.hasByokModel(feature: ReaderAiFeature): Boolean {
    val sanitized = sanitized()
    val model = readerAiModelById(sanitized.modelIdFor(feature)) ?: return false
    return sanitized.apiKeyFor(model.provider).isNotBlank()
}

fun maskedReaderAiKey(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.length <= 6 -> "***"
        else -> "${trimmed.take(3)}...${trimmed.takeLast(3)}"
    }
}

enum class ReaderExternalLookupAction(val title: String) {
    DICTIONARY("Dictionary"),
    TRANSLATE("Translate"),
    SEARCH("Search")
}

enum class ReaderExternalLookupService(val id: String, val title: String) {
    SYSTEM("system", "System Dictionary"),
    /**
     * In-app Safari (SFSafariViewController on iOS): renders the same web URL
     * as the matching engine but stays inside the reader with a Done button
     * instead of leaving the app to the default browser. Always available, so
     * it is the default where a web page is the answer (translate/search).
     * Shown to users as plain "Browser".
     */
    SAFARI("safari", "Browser"),
    GOOGLE("google", "Google"),
    GOOGLE_TRANSLATE("google_translate", "Google Translate"),
    /**
     * Installed-app targets, probed by URL scheme. iOS cannot enumerate
     * installed apps (sandbox), so each entry carries the scheme used to
     * detect it; entries whose scheme is absent never appear in settings.
     * Android keeps its own installed-app dropdowns and ignores these.
     */
    GOOGLE_TRANSLATE_APP("google_translate_app", "Google Translate App"),
    ITRANSLATE_APP("itranslate_app", "iTranslate"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo"),
    BING("bing", "Bing"),

    /**
     * Android parity (ExternalDictionaryHelper): hand the selection to the user's
     * installed apps instead of a fixed engine. iOS shows the system share/choose
     * sheet so the reader can pick any app that accepts text; Android keeps its
     * per-action installed-app dropdowns.
     */
    ANY_APP("any_app", "Any App"),

    /**
     * Routes the selection to the in-app AI definition flow (iOS prompt is
     * "Smart AI" in the Android dictionary sheet).
     */
    AI("ai", "Smart AI");

    companion object {
        fun fromId(id: String?): ReaderExternalLookupService {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
        }
    }
}

// Android parity (no-selection default): with nothing persisted the Dict
// action opens the app chooser instead of assuming an engine. ANY_APP is the
// explicit "choose each time" choice and the default; Smart AI stays first so
// the in-app route is prominent once selected.
val ReaderDictionaryServiceOptions = listOf(
    ReaderExternalLookupService.AI,
    ReaderExternalLookupService.ANY_APP,
    ReaderExternalLookupService.SAFARI,
)

/**
 * Whether the selection-menu "Dict" action routes to the in-app AI definition
 * (Smart AI engine) instead of the external/system lookup. Android reads its
 * persisted engine choice; hosts set this from the same preference.
 */
expect var readerLookupUsesAiDictionary: Boolean

val ReaderTranslateServiceOptions = listOf(
    ReaderExternalLookupService.SAFARI,
)

val ReaderSearchServiceOptions = listOf(
    ReaderExternalLookupService.SAFARI,
)

/**
 * URL scheme used to detect an installed-app service (`googletranslate`,
 * `itranslate`). Null for services that need no app (web, system, AI,
 * share sheet) — those are always listed.
 */
val ReaderExternalLookupService.appScheme: String?
    get() = when (this) {
        ReaderExternalLookupService.GOOGLE_TRANSLATE_APP -> "googletranslate"
        ReaderExternalLookupService.ITRANSLATE_APP -> "itranslate"
        else -> null
    }

/**
 * Deep link into the installed app for [action] (`googletranslate://…`,
 * `itranslate://…`). Null when the service is not an installed app or the
 * action has no app mapping — callers fall back to the web URL / chooser.
 */
fun readerExternalLookupAppUrl(
    service: ReaderExternalLookupService,
    action: ReaderExternalLookupAction,
    text: String,
): String? {
    val encoded = text.trim().urlEncoded()
    if (encoded.isEmpty()) return null
    return when (service) {
        ReaderExternalLookupService.GOOGLE_TRANSLATE_APP -> when (action) {
            // Community-documented scheme (sl=auto detects the source).
            ReaderExternalLookupAction.TRANSLATE -> "googletranslate://?sl=auto&tl=en&text=$encoded"
            else -> null
        }
        ReaderExternalLookupService.ITRANSLATE_APP ->
            "itranslate://translate?from=auto&to=en&text=$encoded"
        else -> null
    }
}

/**
 * Settings-visible subset of [options]: scheme-gated apps appear only when
 * their scheme was probed in [installedSchemes]. The current [selected]
 * service always stays visible so a stale pick (app since uninstalled) can
 * still be changed away instead of vanishing.
 */
fun visibleReaderLookupOptions(
    options: List<ReaderExternalLookupService>,
    selected: ReaderExternalLookupService,
    installedSchemes: Set<String>,
): List<ReaderExternalLookupService> {
    return options.filter { option ->
        option == selected || option.appScheme == null ||
            installedSchemes.any { it.equals(option.appScheme, ignoreCase = true) }
    }
}

const val ReaderExternalLookupSelectionLimit = 2_000

fun readerExternalLookupActionsAvailable(selectionLength: Int): Boolean {
    return selectionLength in 0..ReaderExternalLookupSelectionLimit
}

fun readerExternalLookupActionForSelectionId(id: String): ReaderExternalLookupAction? {
    return when (id.trim().lowercase()) {
        "dictionary" -> ReaderExternalLookupAction.DICTIONARY
        "translate" -> ReaderExternalLookupAction.TRANSLATE
        "web-search", "search" -> ReaderExternalLookupAction.SEARCH
        else -> null
    }
}

fun externalLookupUrl(
    action: ReaderExternalLookupAction,
    text: String,
    service: ReaderExternalLookupService = ReaderExternalLookupService.GOOGLE,
): String {
    val encoded = text.trim().urlEncoded()
    return when (service) {
        ReaderExternalLookupService.GOOGLE_TRANSLATE ->
            "https://translate.google.com/?sl=auto&tl=en&text=$encoded&op=translate"
        ReaderExternalLookupService.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
        ReaderExternalLookupService.BING -> when (action) {
            ReaderExternalLookupAction.TRANSLATE -> "https://www.bing.com/translator/?text=$encoded"
            else -> "https://www.bing.com/search?q=$encoded"
        }
        ReaderExternalLookupService.SYSTEM, ReaderExternalLookupService.GOOGLE -> when (action) {
            ReaderExternalLookupAction.DICTIONARY -> "https://www.google.com/search?q=define+$encoded"
            ReaderExternalLookupAction.TRANSLATE -> "https://translate.google.com/?sl=auto&tl=en&text=$encoded&op=translate"
            ReaderExternalLookupAction.SEARCH -> "https://www.google.com/search?q=$encoded"
        }
        // Safari renders the same page as the default engine for the action,
        // only the presenter differs (in-app Safari vs default browser).
        ReaderExternalLookupService.SAFARI -> when (action) {
            ReaderExternalLookupAction.DICTIONARY -> "https://www.google.com/search?q=define+$encoded"
            ReaderExternalLookupAction.TRANSLATE -> "https://translate.google.com/?sl=auto&tl=en&text=$encoded&op=translate"
            ReaderExternalLookupAction.SEARCH -> "https://www.google.com/search?q=$encoded"
        }
        // Android parity (ExternalDictionaryHelper "Any App"): the text is handed
        // to the user's installed apps via the platform chooser/share sheet, so
        // there is no web URL to open. Installed-app entries are opened from
        // their deep link (readerExternalLookupAppUrl) before reaching here.
        // The caller (openSharedMobileEpubLookup) handles ANY_APP before
        // reaching this URL builder.
        ReaderExternalLookupService.ANY_APP,
        ReaderExternalLookupService.AI,
        ReaderExternalLookupService.GOOGLE_TRANSLATE_APP,
        ReaderExternalLookupService.ITRANSLATE_APP -> ""
    }
}

data class ReaderAutoScrollState(
    val enabled: Boolean = false,
    val speed: Float = 36f
) {
    fun sanitized(): ReaderAutoScrollState {
        return copy(speed = speed.coerceIn(12f, 160f))
    }
}

data class ReaderAutoScrollProfile(
    val speed: Float = 0.8f,
    val minSpeed: Float = 0.1f,
    val maxSpeed: Float = 10f,
) {
    fun sanitized(): ReaderAutoScrollProfile {
        val min = minSpeed.coerceIn(0.1f, 10f)
        val max = maxSpeed.coerceIn(min, 10f)
        return copy(
            speed = speed.coerceIn(min, max),
            minSpeed = min,
            maxSpeed = max,
        )
    }

    fun withMinSpeed(value: Float): ReaderAutoScrollProfile {
        val min = value.coerceIn(0.1f, 10f)
        val max = maxSpeed.coerceAtLeast(min)
        return copy(speed = speed.coerceIn(min, max), minSpeed = min, maxSpeed = max).sanitized()
    }

    fun withMaxSpeed(value: Float): ReaderAutoScrollProfile {
        val max = value.coerceIn(0.1f, 10f)
        val min = minSpeed.coerceAtMost(max)
        return copy(speed = speed.coerceIn(min, max), minSpeed = min, maxSpeed = max).sanitized()
    }
}

/**
 * Android advances by `speed * 0.5` pixels per display frame. The iOS web
 * reader uses a time-based timer, so normalize that contract to 60 Hz.
 */
fun readerAutoScrollPixelsPerSecond(speedMultiplier: Float): Float =
    speedMultiplier.coerceIn(0.1f, 10f) * 0.5f * 60f

fun migrateLegacyIosReaderAutoScrollSpeed(value: Float): Float =
    if (value > 10f) (value / 60f).coerceIn(0.1f, 10f) else value.coerceIn(0.1f, 10f)

const val ReaderSearchDebounceMillis = 350L
const val ReaderSearchFocusDelayMillis = 100L

fun readerSearchDelayMillis(requestId: Long, immediateRequestId: Long): Long =
    if (requestId == immediateRequestId) 0L else ReaderSearchDebounceMillis

enum class ReaderAutoScrollBoundaryAction { NEXT_CHAPTER, STOP }

fun readerAutoScrollBoundaryAction(
    currentChapterIndex: Int,
    chapterCount: Int,
): ReaderAutoScrollBoundaryAction =
    if (currentChapterIndex in 0 until (chapterCount - 1)) {
        ReaderAutoScrollBoundaryAction.NEXT_CHAPTER
    } else {
        ReaderAutoScrollBoundaryAction.STOP
    }

const val ReaderMusicianHoldDurationMillis = 1_000L
const val ReaderMusicianTapPauseMillis = 600L
const val ReaderMusicianHoldPauseMillis = 1_000L
const val ReaderMusicianViewportJumpFraction = 0.75f

enum class ReaderMusicianNavigationTarget { RELATIVE, START, END }

data class ReaderMusicianGesturePlan(
    val target: ReaderMusicianNavigationTarget,
    val relativeViewportDelta: Float,
    val pauseMillis: Long,
)

fun planReaderMusicianGesture(
    isRightRegion: Boolean,
    isLongPress: Boolean,
): ReaderMusicianGesturePlan {
    if (isLongPress) {
        return ReaderMusicianGesturePlan(
            target = if (isRightRegion) ReaderMusicianNavigationTarget.END else ReaderMusicianNavigationTarget.START,
            relativeViewportDelta = 0f,
            pauseMillis = ReaderMusicianHoldPauseMillis,
        )
    }
    return ReaderMusicianGesturePlan(
        target = ReaderMusicianNavigationTarget.RELATIVE,
        relativeViewportDelta = ReaderMusicianViewportJumpFraction * if (isRightRegion) 1f else -1f,
        pauseMillis = ReaderMusicianTapPauseMillis,
    )
}

enum class ReaderTtsReadScope(val label: String) {
    PAGE("Page"),
    CHAPTER("Chapter"),
    BOOK("From here")
}

data class ReaderTtsChunk(
    val index: Int,
    val pageIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val sourceCfi: String? = null,
    val spokenText: String = text
) {
    fun toLocator(): ReaderLocator {
        val boundedEnd = endOffset.coerceAtLeast(startOffset)
        return ReaderLocator(
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            startOffset = startOffset,
            endOffset = boundedEnd,
            textQuote = text,
            cfi = sourceCfi ?: "desktop:$chapterIndex:$startOffset:$boundedEnd"
        )
    }

    fun toHighlight(sessionId: Long): UserHighlight {
        val locator = toLocator()
        return UserHighlight(
            id = "tts_${sessionId}_$index",
            cfi = locator.cfi.orEmpty(),
            text = text,
            color = HighlightColor.YELLOW,
            chapterIndex = chapterIndex,
            locator = locator
        )
    }
}

data class ReaderTtsProgress(
    val sessionId: Long = 0L,
    val scope: ReaderTtsReadScope = ReaderTtsReadScope.PAGE,
    val chunks: List<ReaderTtsChunk> = emptyList(),
    val currentChunkIndex: Int = -1
) {
    val currentChunk: ReaderTtsChunk?
        get() = chunks.getOrNull(currentChunkIndex)

    val isActive: Boolean
        get() = currentChunk != null

    val currentPositionLabel: String?
        get() = currentChunk?.let { chunk ->
            "Part ${currentChunkIndex + 1}/${chunks.size} - ${chunk.chapterTitle.ifBlank { scope.label }}"
        }
}

data class ReaderTtsCacheSummary(
    val cachedChapterCount: Int = 0,
    val cachedChunkCount: Int = 0,
    val currentVoiceChunkCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val currentVoiceSizeBytes: Long = 0L
) {
    val hasCachedAudio: Boolean get() = cachedChunkCount > 0
    val hasCurrentVoiceCachedAudio: Boolean get() = currentVoiceChunkCount > 0

    val currentVoiceLabel: String
        get() = if (hasCurrentVoiceCachedAudio) {
            "$currentVoiceChunkCount chunks, ${formatReaderTtsBytes(currentVoiceSizeBytes)}"
        } else {
            "No cached chunks for this voice"
        }
}

/**
 * One cached-audio chapter for one cloud voice (Android `TtsCacheTab`
 * parity). [entryKey] is the opaque platform storage key the matching
 * delete call consumes; [chapterTitle]/[bookTitle] are display labels.
 */
data class ReaderTtsCacheChapter(
    val bookTitle: String = "",
    val chapterTitle: String,
    val voiceId: String,
    val chunkCount: Int = 0,
    val sizeBytes: Long = 0L,
    val entryKey: String = "",
)

/** Voice-sample preview state (Android `SpeakerSamplePlayer` parity). */
data class ReaderVoiceSampleState(
    val loadingVoiceId: String? = null,
    val playingVoiceId: String? = null,
    val cachedVoiceIds: Set<String> = emptySet(),
)

/**
 * Android `TtsCacheManager` file-name parity:
 * `cached_chunk_<speaker>_<digest>.<ext>` (ext is `wav` for legacy
 * Gemini-Live chunks, `mp3` for Fish REST chunks). Returns the speaker id or null.
 */
fun readerTtsCacheSpeakerId(fileName: String): String? {
    if (!fileName.startsWith("cached_chunk_")) return null
    val ext = fileName.substringAfterLast('.', "")
    if (ext != "wav" && ext != "mp3") return null
    return fileName.removePrefix("cached_chunk_").removeSuffix(".$ext")
        .substringBeforeLast('_').takeIf { it.isNotBlank() }
}

/**
 * Display label for a sanitized cache segment dir (`<clean>_<digest>`):
 * drops the digest and restores spaces.
 */
fun readerTtsCacheDisplayLabel(segment: String): String {
    val withoutDigest = if (segment.length > 17 && segment[segment.length - 17] == '_') {
        segment.dropLast(17)
    } else {
        segment
    }
    return withoutDigest.replace('_', ' ').trim().ifBlank { segment }
}

object ReaderTtsPlanner {
    fun chunksForCurrentPage(session: ReaderSessionState): List<ReaderTtsChunk> {
        val page = session.reader.currentPage ?: return emptyList()
        return chunksForPages(session.reader.book, listOf(page))
    }

    fun chunksForCurrentChapter(session: ReaderSessionState): List<ReaderTtsChunk> {
        val page = session.reader.currentPage ?: return emptyList()
        return chunksForPages(
            session.reader.book,
            session.reader.pages
                .asSequence()
                .filter { it.pageIndex >= page.pageIndex && it.chapterIndex == page.chapterIndex }
                .toList()
        )
    }

    /**
     * Android benchmark (chapter chaining): read-aloud plans one chapter at
     * a time instead of the rest of the book, so starting TTS stays fast on
     * long books and the engine chains chapters on natural completion. When
     * the session anchor sits inside [chapterIndex] the head is sliced at
     * the anchor exactly like [chunksFromCurrentLocation]; otherwise the
     * whole chapter is returned. Empty when the chapter has no pages or no
     * speakable text (callers skip to the next chapter).
     */
    fun chunksForChapterFromLocation(session: ReaderSessionState, chapterIndex: Int): List<ReaderTtsChunk> {
        val pages = session.reader.pages.filter { it.chapterIndex == chapterIndex }
        if (pages.isEmpty()) return emptyList()
        val chunks = chunksForPages(session.reader.book, pages)
        if (chunks.isEmpty()) return emptyList()
        val anchor = session.navigationLocator
        if (anchor?.chapterIndex != chapterIndex) {
            return chunks
                .filter { it.text.isNotBlank() }
                .mapIndexed { index, chunk -> chunk.copy(index = index) }
        }
        val target = anchor.toTtsChunkTarget()
        val startChunkIndex = findReaderTtsChunkStartIndex(chunks, target)
            ?: chunks.indexOfFirst { it.isOnOrAfterLocator(chapterIndex, anchor.startOffset) }.takeIf { it >= 0 }
            ?: return emptyList()
        val initialChunk = chunks[startChunkIndex].sliceFromLocator(anchor)
        val sessionChunks = if (initialChunk == null) {
            chunks.drop(startChunkIndex + 1)
        } else {
            chunks.withInitialChunkOverride(startChunkIndex, initialChunk).drop(startChunkIndex)
        }
        return sessionChunks
            .filter { it.text.isNotBlank() }
            .mergeTinyLeadingChunk()
            .mapIndexed { index, chunk -> chunk.copy(index = index) }
    }

    fun chunksFromCurrentLocation(session: ReaderSessionState): List<ReaderTtsChunk> {
        val anchor = session.navigationLocator
        val pageIndex = anchor?.pageIndex ?: session.reader.currentPageIndex
        val pages = session.reader.pages.dropWhile { it.pageIndex < pageIndex.coerceAtLeast(0) }
        val syntheticDesktopAnchor = anchor?.isSyntheticDesktopTtsAnchor() == true
        val chunks = chunksForPages(session.reader.book, pages)
        val chapterIndex = anchor?.chapterIndex
        val startOffset = anchor?.startOffset
        logReaderTtsStartTrace {
            "event=planner_from_here_start pageIndex=$pageIndex pages=${pages.size} chunks=${chunks.size} " +
                "syntheticDesktop=$syntheticDesktopAnchor " +
                "anchor=${anchor.readerTtsLocatorSummary()} first=${chunks.firstOrNull().readerTtsChunkSummary()} " +
                "second=${chunks.getOrNull(1).readerTtsChunkSummary()}"
        }
        if (chapterIndex == null && startOffset == null) return chunks
        val target = anchor?.toTtsChunkTarget()
        val startChunkIndex = findReaderTtsChunkStartIndex(chunks, target)
            ?: chunks.indexOfFirst { it.isOnOrAfterLocator(chapterIndex, startOffset) }.takeIf { it >= 0 }
            ?: run {
                logReaderTtsStartTrace {
                    "event=planner_from_here_empty reason=no_start_chunk target=${target.readerTtsTargetSummary()} " +
                        "anchor=${anchor.readerTtsLocatorSummary()} chunks=${chunks.size}"
                }
                return emptyList()
            }
        val initialChunk = anchor?.let { chunks[startChunkIndex].sliceFromLocator(it) }
        val sessionChunks = if (initialChunk == null) {
            chunks.drop(startChunkIndex + 1)
        } else {
            chunks.withInitialChunkOverride(startChunkIndex, initialChunk).drop(startChunkIndex)
        }
        logReaderTtsStartTrace {
            "event=planner_from_here_result target=${target.readerTtsTargetSummary()} startChunkIndex=$startChunkIndex " +
                "sourceChunk=${chunks.getOrNull(startChunkIndex).readerTtsChunkSummary()} " +
                "initialChunk=${initialChunk.readerTtsChunkSummary()} resultChunks=${sessionChunks.size} " +
                "resultFirst=${sessionChunks.firstOrNull().readerTtsChunkSummary()}"
        }
        return sessionChunks
            .filter { it.text.isNotBlank() }
            .mergeTinyLeadingChunk()
            .mapIndexed { index, chunk -> chunk.copy(index = index) }
    }

    fun chunksForText(
        text: String,
        pageIndex: Int,
        chapterIndex: Int,
        chapterTitle: String,
        sourceStartOffset: Int = 0
    ): List<ReaderTtsChunk> {
        return splitTextIntoRanges(text).mapIndexed { index, range ->
            ReaderTtsChunk(
                index = index,
                pageIndex = pageIndex,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                text = range.text,
                startOffset = sourceStartOffset + range.start,
                endOffset = sourceStartOffset + range.end
            )
        }
    }

    private fun chunksForPages(book: SharedEpubBook, pages: List<ReaderPage>): List<ReaderTtsChunk> {
        var nextIndex = 0
        return pages
            .groupBy { it.chapterIndex }
            .entries
            .sortedBy { (chapterIndex, _) ->
                pages.indexOfFirst { it.chapterIndex == chapterIndex }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            .flatMap { chapterPages ->
                val chapter = book.chapters.getOrNull(chapterPages.key)
                val semanticChunks = chapter
                    ?.let { chunksForSemanticPages(it, chapterPages.value) }
                    .orEmpty()
                if (semanticChunks.isNotEmpty()) {
                    semanticChunks
                } else {
                    chunksForPlainPages(book, chapterPages.value)
                }
            }
            .distinctBy { "${it.sourceCfi}:${it.startOffset}:${it.endOffset}:${it.text}" }
            .map { it.copy(index = nextIndex++) }
            .toList()
    }

    private fun chunksForPlainPages(book: SharedEpubBook, pages: List<ReaderPage>): List<ReaderTtsChunk> {
        return pages.flatMap { page ->
            val chapterText = book.chapters
                .getOrNull(page.chapterIndex)
                ?.normalizedTtsSourceText()
                .orEmpty()
            val sourceStartOffset = page.sourceTextStartOffset(chapterText)
            splitTextIntoRanges(page.text).map { range ->
                ReaderTtsChunk(
                    index = 0,
                    pageIndex = page.pageIndex,
                    chapterIndex = page.chapterIndex,
                    chapterTitle = page.chapterTitle,
                    text = range.text,
                    startOffset = sourceStartOffset + range.start,
                    endOffset = sourceStartOffset + range.end
                )
            }
        }
    }

    private fun ReaderTtsChunk.isOnOrAfterLocator(chapterIndex: Int?, startOffset: Int?): Boolean {
        if (chapterIndex != null) {
            if (this.chapterIndex < chapterIndex) return false
            if (this.chapterIndex > chapterIndex) return true
        }
        val anchorOffset = startOffset ?: return true
        return endOffset > anchorOffset
    }

    private fun ReaderTtsChunk.sliceFromLocator(locator: ReaderLocator): ReaderTtsChunk? {
        if (locator.chapterIndex != null && locator.chapterIndex != chapterIndex) return this
        val sourceOffset = locator.startOffset ?: return this
        val rawDrop = (sourceOffset - startOffset).coerceIn(0, text.length)
        val drop = rawDrop
        if (drop <= 0) {
            logReaderTtsStartTrace {
                "event=planner_slice_keep reason=drop_at_start rawDrop=$rawDrop " +
                    "locator=${locator.readerTtsLocatorSummary()} chunk=${readerTtsChunkSummary()}"
            }
            return this
        }
        if (drop >= text.length) {
            logReaderTtsStartTrace {
                "event=planner_slice_skip reason=drop_past_end rawDrop=$rawDrop " +
                    "chosenDrop=$drop locator=${locator.readerTtsLocatorSummary()} chunk=${readerTtsChunkSummary()}"
            }
            return null
        }
        val remaining = text.drop(drop)
        val leadingWhitespace = remaining.indexOfFirst { !it.isWhitespace() }
        if (leadingWhitespace < 0) {
            logReaderTtsStartTrace {
                "event=planner_slice_skip reason=blank_after_drop rawDrop=$rawDrop " +
                    "chosenDrop=$drop locator=${locator.readerTtsLocatorSummary()} chunk=${readerTtsChunkSummary()}"
            }
            return null
        }
        val nextText = remaining.drop(leadingWhitespace)
        if (nextText.isBlank()) return null
        val nextStartOffset = (sourceOffset + leadingWhitespace).coerceAtMost(endOffset)
        logReaderTtsStartTrace {
            "event=planner_slice_result rawDrop=$rawDrop chosenDrop=$drop " +
                "leadingWhitespace=$leadingWhitespace nextStart=$nextStartOffset locator=${locator.readerTtsLocatorSummary()} " +
                "chunk=${readerTtsChunkSummary()} nextText=\"${nextText.readerTtsLogPreview()}\""
        }
        return copy(
            text = nextText,
            spokenText = nextText,
            startOffset = nextStartOffset
        )
    }

    private fun chunksForSemanticPages(
        chapter: SharedEpubChapter,
        pages: List<ReaderPage>
    ): List<ReaderTtsChunk> {
        if (chapter.semanticBlocks.isEmpty() || pages.isEmpty()) return emptyList()
        val ranges = pages.map { it.startOffset to it.endOffset }
        val textBlocks = chapter.semanticBlocks.semanticTextBlocks()
            .filter { block ->
                block.cfi != null &&
                    block.text.isNotBlank() &&
                    ranges.any { (start, end) -> block.intersects(start, end) }
            }
        return textBlocks.flatMap { block ->
            val blockStart = block.startCharOffsetInSource.coerceAtLeast(0)
            splitTextIntoRanges(block.text).mapNotNull { range ->
                val chunkStart = blockStart + range.start
                val chunkEnd = blockStart + range.end
                if (ranges.none { (start, end) -> chunkStart < end && chunkEnd > start }) return@mapNotNull null
                val page = pages.firstOrNull { it.intersects(chunkStart, chunkEnd) }
                    ?: pages.minByOrNull { kotlin.math.abs(it.startOffset - chunkStart) }
                    ?: return@mapNotNull null
                ReaderTtsChunk(
                    index = 0,
                    pageIndex = page.pageIndex,
                    chapterIndex = page.chapterIndex,
                    chapterTitle = page.chapterTitle,
                    text = range.text,
                    startOffset = chunkStart,
                    endOffset = chunkEnd,
                    sourceCfi = block.cfi
                )
            }
        }
    }

    private fun List<SemanticBlock>.semanticTextBlocks(): List<SemanticTextBlock> {
        val blocks = mutableListOf<SemanticTextBlock>()
        fun visit(block: SemanticBlock) {
            when (block) {
                is SemanticTextBlock -> blocks += block
                is SemanticFlexContainer -> block.children.forEach(::visit)
                is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> cell.content.forEach(::visit) } }
                is SemanticList -> block.items.forEach(::visit)
                is SemanticWrappingBlock -> block.paragraphsToWrap.forEach(::visit)
                else -> Unit
            }
        }
        forEach(::visit)
        return blocks
    }

    private fun SemanticTextBlock.intersects(startOffset: Int, endOffset: Int): Boolean {
        val start = startCharOffsetInSource
        val end = start + text.length
        return start < endOffset && end > startOffset
    }

    private fun ReaderPage.intersects(startOffset: Int, endOffset: Int): Boolean {
        return startOffset < endOffset && startOffset < this.endOffset && endOffset > this.startOffset
    }

    private fun ReaderPage.sourceTextStartOffset(chapterText: String): Int {
        if (chapterText.isBlank()) return startOffset
        val boundedStart = startOffset.coerceIn(0, chapterText.length)
        val boundedEnd = endOffset.coerceIn(boundedStart, chapterText.length)
        val pageSlice = chapterText.substring(boundedStart, boundedEnd)
        val trimAdjustedStart = boundedStart + pageSlice.leadingWhitespaceLength()
        val exactTextStart = text
            .takeIf { it.isNotBlank() }
            ?.let { needle ->
                chapterText.indexOf(needle, startIndex = boundedStart)
                    .takeIf { found -> found >= boundedStart && found + needle.length <= boundedEnd }
            }
        if (exactTextStart != null) return exactTextStart
        val trimmedTextStart = text
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { needle ->
                chapterText.indexOf(needle, startIndex = boundedStart)
                    .takeIf { found -> found >= boundedStart && found + needle.length <= boundedEnd }
            }
        return trimmedTextStart ?: trimAdjustedStart
    }

    private fun String.leadingWhitespaceLength(): Int {
        return length - trimStart().length
    }

    private fun SharedEpubChapter.normalizedTtsSourceText(): String {
        return plainText
            .replace("\r\n", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun splitTextIntoRanges(
        text: String,
        maxLength: Int = READER_TTS_CHUNK_MAX_LENGTH
    ): List<ReaderTtsTextRange> {
        val sourceStart = text.indexOfFirst { !it.isWhitespace() }
        if (sourceStart < 0) return emptyList()
        val sourceEnd = text.indexOfLast { !it.isWhitespace() } + 1
        val source = text.substring(sourceStart, sourceEnd)
        val sentenceRanges = androidStyleSentenceRanges(source, sourceStart)
        if (sentenceRanges.isEmpty()) return emptyList()

        val chunks = mutableListOf<ReaderTtsTextRange>()
        var currentText = StringBuilder()
        var currentStart = -1
        var currentEnd = -1
        fun flushCurrent() {
            if (currentText.isNotEmpty() && currentStart >= 0 && currentEnd >= currentStart) {
                chunks += ReaderTtsTextRange(
                    text = currentText.toString(),
                    start = currentStart,
                    end = currentEnd
                )
            }
            currentText = StringBuilder()
            currentStart = -1
            currentEnd = -1
        }

        for (sentence in sentenceRanges) {
            val appendText = if (currentText.isEmpty()) {
                sentence.text
            } else {
                val gapStart = (currentEnd - sourceStart).coerceIn(0, source.length)
                val gapEnd = (sentence.end - sourceStart).coerceIn(gapStart, source.length)
                source.substring(gapStart, gapEnd)
            }
            if (sentence.text.length > maxLength) {
                flushCurrent()
                chunks += sentence
                continue
            }
            if (currentText.isNotEmpty() && currentText.length + appendText.length > maxLength) {
                flushCurrent()
                currentText.append(sentence.text)
                currentStart = sentence.start
                currentEnd = sentence.end
            } else {
                currentText.append(appendText)
                if (currentStart < 0) currentStart = sentence.start
                currentEnd = sentence.end
            }
        }
        flushCurrent()
        return chunks
    }

    private fun androidStyleSentenceRanges(source: String, sourceOffset: Int): List<ReaderTtsTextRange> {
        val sentenceBoundaryRegex = Regex("""(?<!\w\.\w.)(?<![A-Z][a-z]\.)(?<=[.?!\n])\s+""")
        val ranges = mutableListOf<ReaderTtsTextRange>()
        var start = 0
        sentenceBoundaryRegex.findAll(source).forEach { match ->
            val end = match.range.first
            if (end > start) {
                source.substring(start, end)
                    .takeIf { it.isNotBlank() }
                    ?.let { sentence ->
                        ranges += ReaderTtsTextRange(
                            text = sentence,
                            start = sourceOffset + start,
                            end = sourceOffset + end
                        )
                    }
            }
            start = match.range.last + 1
        }
        if (start < source.length) {
            val sentence = source.substring(start)
            if (sentence.isNotBlank()) {
                ranges += ReaderTtsTextRange(
                    text = sentence,
                    start = sourceOffset + start,
                    end = sourceOffset + source.length
                )
            }
        }
        return ranges
    }

    private data class ReaderTtsTextRange(
        val text: String,
        val start: Int,
        val end: Int
    )

    private data class ReaderTtsChunkTarget(
        val text: String,
        val sourceCfi: String?,
        val startOffset: Int
    )

    private fun ReaderLocator.toTtsChunkTarget(): ReaderTtsChunkTarget? {
        val offset = startOffset ?: return null
        val sourceCfi = cfi
            ?.readerTtsSourceCfiBase()
            ?.takeIf { it.startsWith("/") }
        return ReaderTtsChunkTarget(
            text = textQuote.orEmpty(),
            sourceCfi = sourceCfi,
            startOffset = offset
        )
    }

    private fun ReaderLocator.isSyntheticDesktopTtsAnchor(): Boolean {
        val value = cfi.orEmpty()
        return value.startsWith("desktop:") ||
            value.startsWith("desktop-scroll:") ||
            value.startsWith("desktop-scroll-page:")
    }

    private fun findReaderTtsChunkStartIndex(
        chunks: List<ReaderTtsChunk>,
        target: ReaderTtsChunkTarget?
    ): Int? {
        if (target == null) return null

        val exactIndex = chunks.indexOfFirst {
            readerSameTtsChunkSource(it.sourceCfi, target.sourceCfi) &&
                it.startOffset == target.startOffset &&
                it.text.normalizedReaderTtsText() == target.text.normalizedReaderTtsText()
        }
        if (exactIndex >= 0) return exactIndex

        val sourceAndOffsetIndex = chunks.indexOfFirst {
            readerSameTtsChunkSource(it.sourceCfi, target.sourceCfi) &&
                target.startOffset >= it.startOffset &&
                target.startOffset < it.endOffset
        }
        if (sourceAndOffsetIndex >= 0) return sourceAndOffsetIndex

        val sourceAndTextIndex = chunks.indexOfFirst {
            readerSameTtsChunkSource(it.sourceCfi, target.sourceCfi) &&
                readerTtsTextMatches(it.text, target.text)
        }
        if (sourceAndTextIndex >= 0) return sourceAndTextIndex

        val sourceNearestOffsetIndex = chunks
            .mapIndexedNotNull { index, chunk ->
                if (readerSameTtsChunkSource(chunk.sourceCfi, target.sourceCfi)) {
                    index to kotlin.math.abs(chunk.startOffset - target.startOffset)
                } else {
                    null
                }
            }
            .minByOrNull { it.second }
            ?.first
        if (sourceNearestOffsetIndex != null) return sourceNearestOffsetIndex

        return findUniqueReaderTtsTextMatch(chunks, target.text)
    }

    private fun List<ReaderTtsChunk>.withInitialChunkOverride(
        startChunkIndex: Int,
        initialChunk: ReaderTtsChunk?
    ): List<ReaderTtsChunk> {
        if (initialChunk == null || startChunkIndex !in indices) return this
        val existing = this[startChunkIndex]
        if (
            existing.text == initialChunk.text &&
            existing.sourceCfi == initialChunk.sourceCfi &&
            existing.startOffset == initialChunk.startOffset
        ) {
            return this
        }
        return toMutableList().also { it[startChunkIndex] = initialChunk }
    }

    private fun readerSameTtsChunkSource(first: String?, second: String?): Boolean {
        val firstSource = first.orEmpty()
        val secondSource = second.orEmpty()
        if (firstSource.isBlank() || secondSource.isBlank()) return firstSource == secondSource
        val firstPath = firstSource.readerTtsSourceCfiBase()
        val secondPath = secondSource.readerTtsSourceCfiBase()
        return firstPath == secondPath ||
            readerTtsCfiPathContains(firstPath, secondPath) ||
            readerTtsCfiPathContains(secondPath, firstPath)
    }

    private fun readerTtsCfiPathContains(parentPath: String, childPath: String): Boolean {
        if (parentPath.isBlank() || childPath.isBlank() || parentPath == childPath) return false
        val parentParts = parentPath.split('/').filter { it.isNotEmpty() }
        val childParts = childPath.split('/').filter { it.isNotEmpty() }
        return parentParts.size < childParts.size && childParts.take(parentParts.size) == parentParts
    }

    private fun readerTtsTextMatches(first: String, second: String): Boolean {
        val firstNormalized = first.normalizedReaderTtsText()
        val secondNormalized = second.normalizedReaderTtsText()
        if (firstNormalized.isBlank() || secondNormalized.isBlank()) return false
        return firstNormalized == secondNormalized ||
            firstNormalized.startsWith(secondNormalized) ||
            secondNormalized.startsWith(firstNormalized)
    }

    private fun findUniqueReaderTtsTextMatch(chunks: List<ReaderTtsChunk>, text: String): Int? {
        val matches = chunks.mapIndexedNotNull { index, chunk ->
            index.takeIf { readerTtsTextMatches(chunk.text, text) }
        }
        return matches.singleOrNull()
    }

    private fun String.readerTtsSourceCfiBase(): String {
        return substringBefore('|').substringBefore(':')
    }

    private fun String.normalizedReaderTtsText(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    private inline fun logReaderTtsStartTrace(message: () -> String) {
        logSharedReaderDiagnostic(ReaderTtsStartTraceLogTag, message)
    }

    private fun ReaderLocator?.readerTtsLocatorSummary(maxTextLength: Int = 120): String {
        if (this == null) return "null"
        return "chapter=${chapterIndex ?: "null"} page=${pageIndex ?: "null"} " +
            "offsets=${startOffset ?: "null"}..${endOffset ?: "null"} " +
            "block=${blockIndex ?: "null"} char=${charOffset ?: "null"} " +
            "cfi=\"${cfi.orEmpty().readerTtsLogPreview(180)}\" text=\"${textQuote.orEmpty().readerTtsLogPreview(maxTextLength)}\""
    }

    private fun ReaderTtsChunk?.readerTtsChunkSummary(maxTextLength: Int = 120): String {
        if (this == null) return "null"
        return "index=$index page=$pageIndex chapter=$chapterIndex offsets=$startOffset..$endOffset " +
            "sourceCfi=\"${sourceCfi.orEmpty().readerTtsLogPreview(160)}\" textChars=${text.length} " +
            "text=\"${text.readerTtsLogPreview(maxTextLength)}\" spoken=\"${spokenText.readerTtsLogPreview(maxTextLength)}\""
    }

    private fun ReaderTtsChunkTarget?.readerTtsTargetSummary(maxTextLength: Int = 120): String {
        if (this == null) return "null"
        return "offset=$startOffset sourceCfi=\"${sourceCfi.orEmpty().readerTtsLogPreview(160)}\" " +
            "text=\"${text.readerTtsLogPreview(maxTextLength)}\""
    }

    private fun String.readerTtsLogPreview(maxLength: Int = 120): String {
        return replace(Regex("\\s+"), " ")
            .trim()
            .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
            .replace("\"", "\\\"")
    }
}

data class ReaderCloudTtsState(
    val isAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isPaused: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val progress: ReaderTtsProgress = ReaderTtsProgress(),
    val cacheSummary: ReaderTtsCacheSummary = ReaderTtsCacheSummary(),
    // USD session spend in micro-dollars (credited Fish path only; the worker
    // reports it per chunk via X-Tts-Cost-Micros). Android benchmark parity.
    val cloudSessionSpendMicros: Long = 0L,
    // Increments only when every chunk finishes naturally (chapter chaining);
    // explicit stop does not increment it. Lets readers chain the next
    // chapter like the local completionCount.
    val completionCount: Long = 0L
)

data class ReaderCloudTtsControlsModel(
    val isVisible: Boolean,
    val canPauseResume: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val canLocateCurrentChunk: Boolean
)

fun readerCloudTtsControlsModel(cloudTts: ReaderCloudTtsState): ReaderCloudTtsControlsModel {
    val progress = cloudTts.progress
    val visible = cloudTts.isLoading || cloudTts.isPlaying || cloudTts.isPaused
    val hasCurrentChunk = progress.currentChunk != null
    return ReaderCloudTtsControlsModel(
        isVisible = visible,
        canPauseResume = cloudTts.isPlaying || cloudTts.isPaused,
        canSkipPrevious = !cloudTts.isLoading &&
            progress.currentChunkIndex > 0 &&
            progress.chunks.isNotEmpty(),
        canSkipNext = !cloudTts.isLoading &&
            progress.currentChunkIndex >= 0 &&
            progress.currentChunkIndex < progress.chunks.lastIndex,
        canLocateCurrentChunk = hasCurrentChunk
    )
}

data class ReaderAiResultState(
    val title: String? = null,
    val text: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Android parity (AiDefinitionPopup word / AiHubBottomSheet chapter title):
     * the selected word (DEFINE) or section title the result was generated
     * for. Shown as the headline; [title] stays the feature display name.
     */
    val queryText: String? = null,
    /**
     * Android parity (AiResultContentView usage badge): worker-reported cost
     * and free-remaining balance, plus whether the text came from cache.
     */
    val cost: Double? = null,
    val freeRemaining: Int? = null,
    val isCacheHit: Boolean = false,
    /**
     * Android parity (executeRecapLogic progress): "Checking past
     * chapters..." / "Analyzing Chapter N..." / "Reading current
     * position..." / "Generating Recap...". Shown while [isLoading] with
     * blank [text]; cleared on first streamed chunk.
     */
    val progressMessage: String? = null
) {
    val hasContent: Boolean get() = text.isNotBlank() || errorMessage != null || isLoading
}

/**
 * Android parity (executeRecapLogic): a story-recap request carries past
 * sections (summarized with cache read-through) plus the current section
 * text, instead of one head-truncated blob. [summaryCache] is the host's
 * instance so chained summaries land in the same store the hub reads.
 */
data class ReaderRecapSection(
    val title: String,
    val text: String
)

data class ReaderRecapRequest(
    val bookTitle: String,
    val sectionIndex: Int,
    val pastSections: List<ReaderRecapSection>,
    val currentText: String,
    val currentTitle: String = "",
    val summaryCache: SharedSummaryCache? = null
)

data class ReaderExtrasState(
    val autoScroll: ReaderAutoScrollState = ReaderAutoScrollState(),
    val cloudTts: ReaderCloudTtsState = ReaderCloudTtsState(),
    val aiResult: ReaderAiResultState = ReaderAiResultState()
)

data class ReaderByokTextRequest(
    val model: ReaderAiModelOption,
    val apiKey: String,
    val systemInstruction: String,
    val userPrompt: String,
    val temperature: Double,
    val maxTokens: Int
)

sealed interface ReaderByokTextRequestResult {
    data class Ready(val request: ReaderByokTextRequest) : ReaderByokTextRequestResult
    data class MissingModel(val featureName: String) : ReaderByokTextRequestResult
    data class MissingKey(val provider: String) : ReaderByokTextRequestResult
    data object Hidden : ReaderByokTextRequestResult
}

object ReaderByokTextRequests {
    fun build(
        settings: ReaderAiByokSettings,
        feature: ReaderAiFeature,
        text: String,
        context: String? = null
    ): ReaderByokTextRequestResult {
        val sanitized = settings.sanitized()
        if (sanitized.hideReaderAiFeatures) return ReaderByokTextRequestResult.Hidden
        val model = readerAiModelById(sanitized.modelIdFor(feature))
            ?: return ReaderByokTextRequestResult.MissingModel(feature.displayName)
        val apiKey = sanitized.apiKeyFor(model.provider)
        if (apiKey.isBlank()) return ReaderByokTextRequestResult.MissingKey(model.provider)
        val prompt = promptFor(feature, text, context)
        return ReaderByokTextRequestResult.Ready(
            ReaderByokTextRequest(
                model = model,
                apiKey = apiKey,
                systemInstruction = prompt.systemInstruction,
                userPrompt = prompt.userPrompt,
                temperature = prompt.temperature,
                maxTokens = prompt.maxTokens
            )
        )
    }

    private fun promptFor(feature: ReaderAiFeature, text: String, context: String?): ReaderPrompt {
        return when (feature) {
            ReaderAiFeature.DEFINE -> ReaderPrompt(
                systemInstruction = "You are a concise reading dictionary. Define the selected word or passage, explain nuance in context, and avoid unrelated commentary.",
                userPrompt = buildString {
                    context?.takeIf { it.isNotBlank() }?.let {
                        append("Context:\n")
                        append(it.trim().take(3000))
                        append("\n\n")
                    }
                    append("Selection:\n")
                    append(text.trim())
                },
                temperature = 0.15,
                maxTokens = 1024
            )

            ReaderAiFeature.SUMMARIZE -> ReaderPrompt(
                systemInstruction = "You are an expert reading assistant. Summarize the provided passage clearly and concisely. Focus on the main ideas, plot points, and useful context. Do not add a preamble.",
                userPrompt = text.trim(),
                temperature = 0.2,
                maxTokens = 4096
            )

            ReaderAiFeature.RECAP -> ReaderPrompt(
                systemInstruction = "You are a reading assistant creating a recap up to the reader's current position. Synthesize prior context and current text into a cohesive recap. Conclude exactly where the reader is positioned. Do not add a preamble.",
                userPrompt = text.trim(),
                temperature = 0.3,
                maxTokens = 4096
            )
        }
    }
}

data class ReaderPrompt(
    val systemInstruction: String,
    val userPrompt: String,
    val temperature: Double,
    val maxTokens: Int
)

object ReaderContextExtractor {
    fun currentPageText(session: ReaderSessionState, maxChars: Int = 6000): String {
        return session.reader.currentPage?.text.orEmpty().trim().take(maxChars)
    }

    fun currentChapterText(session: ReaderSessionState, maxChars: Int = 20_000): String {
        val chapterIndex = session.reader.currentPage?.chapterIndex ?: return currentPageText(session, maxChars)
        return session.reader.book.chapters
            .getOrNull(chapterIndex)
            ?.plainText
            .orEmpty()
            .trim()
            .take(maxChars)
    }

    fun textBeforeCurrentLocation(session: ReaderSessionState, maxChars: Int = 24_000): String {
        val page = session.reader.currentPage ?: return ""
        val builder = StringBuilder()
        session.reader.book.chapters.forEachIndexed { chapterIndex, chapter ->
            when {
                chapterIndex < page.chapterIndex -> {
                    builder.append(chapter.title).append('\n')
                    builder.append(chapter.plainText.trim()).append("\n\n")
                }
                chapterIndex == page.chapterIndex -> {
                    builder.append(chapter.title).append('\n')
                    builder.append(chapter.plainText.take(page.endOffset.coerceAtMost(chapter.plainText.length)).trim())
                }
            }
        }
        return builder.toString().trim().takeLast(maxChars)
    }
}

private fun String.urlEncoded(): String {
    val bytes = encodeToByteArray()
    val builder = StringBuilder()
    bytes.forEach { raw ->
        val value = raw.toInt() and 0xFF
        val char = value.toChar()
        when {
            value in 'A'.code..'Z'.code ||
                value in 'a'.code..'z'.code ||
                value in '0'.code..'9'.code ||
                char in "-_.~" -> builder.append(char)
            char == ' ' -> builder.append('+')
            else -> builder.append('%').append(value.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
