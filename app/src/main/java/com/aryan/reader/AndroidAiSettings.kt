@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderAiByokSettings as AiByokSettings

import com.aryan.reader.shared.hasByokModel
import com.aryan.reader.shared.isFishVoiceListCacheFresh

import com.aryan.reader.shared.ReaderAiModelOption as AiModelOption

import com.aryan.reader.shared.ReaderAiFeature as AiFeature

import com.aryan.reader.shared.ReaderFishVoice as FishVoice

import androidx.compose.material3.ExperimentalMaterial3Api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.async
import kotlin.time.Clock
import timber.log.Timber
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


const val aiServerBasePath = BuildConfig.AI_WORKER_URL
const val summarizeEndpoint = "/summarize"
const val summarizationUrl = aiServerBasePath + summarizeEndpoint
const val defineEndpoint = "/define"
const val aiDefinitionUrl = aiServerBasePath + defineEndpoint
const val recapEndpoint = "/recap"
const val recapUrl = aiServerBasePath + recapEndpoint

const val PREF_NATIVE_TTS_VOICE = "native_tts_voice_name"
internal const val AI_PREFS_NAME = "ai_byok_prefs"
internal const val PREF_AI_HIDE_READER_FEATURES = "hide_reader_ai_features"
internal const val PREF_AI_GEMINI_KEY = "gemini_key"
internal const val PREF_AI_GROQ_KEY = "groq_key"
internal const val PREF_AI_FISH_KEY = "fish_key"
internal const val PREF_AI_USE_ONE_MODEL = "use_one_model"
internal const val PREF_AI_MODEL_ALL = "model_all"
internal const val PREF_AI_MODEL_DEFINE = "model_define"
internal const val PREF_AI_MODEL_SUMMARIZE = "model_summarize"
internal const val PREF_AI_MODEL_RECAP = "model_recap"
internal const val PREF_AI_TTS_MODEL = "tts_model"
internal const val PREF_AI_MODEL_EMPTY_MIGRATION_DONE = "model_empty_migration_done"
internal const val AI_KEYSTORE_ALIAS = "reader_ai_byok_key_v1"
internal const val ENCRYPTION_PREFIX = "v1:"
const val GEMINI_CLOUD_TTS_MODEL = com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
const val GEMINI_CLOUD_TTS_MODEL_ID = com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
const val GEMINI_TTS_MODEL_LITE = com.aryan.reader.shared.GEMINI_TTS_MODEL_LITE
const val GEMINI_TTS_MODEL_PREVIEW = com.aryan.reader.shared.GEMINI_TTS_MODEL_PREVIEW
const val GEMINI_TTS_MODEL_LITE_ID = com.aryan.reader.shared.GEMINI_TTS_MODEL_LITE_ID
const val GEMINI_TTS_MODEL_PREVIEW_ID = com.aryan.reader.shared.GEMINI_TTS_MODEL_PREVIEW_ID
const val FISH_TTS_MODEL = com.aryan.reader.shared.FISH_TTS_MODEL
const val FISH_TTS_MODEL_ID = com.aryan.reader.shared.FISH_TTS_MODEL_ID

/** Manual fallback for the BYOK TTS picker (no prices — never hardcoded). */
val aiByokTtsModelFallback: List<AiModelOption> = com.aryan.reader.shared.ReaderTtsByokOptions


internal fun AiFeature.displayName(context: Context): String {
    return when (this) {
        AiFeature.DEFINE -> context.getString(R.string.ai_settings_smart_dictionary)
        AiFeature.SUMMARIZE -> context.getString(R.string.ai_settings_summaries)
        AiFeature.RECAP -> context.getString(R.string.ai_settings_recaps)
    }
}

internal fun aiProviderDisplayName(context: Context, provider: String): String {
    return when (provider) {
        "gemini" -> context.getString(R.string.provider_gemini)
        "groq" -> context.getString(R.string.provider_groq)
        "fish" -> context.getString(R.string.provider_fish)
        else -> provider.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}

val aiByokModelOptions: List<AiModelOption> = com.aryan.reader.shared.ReaderAiModelOptions

internal fun Context.aiPrefs() = getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)

internal fun getAiSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(AI_KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val keySpec = KeyGenParameterSpec.Builder(
        AI_KEYSTORE_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()
    keyGenerator.init(keySpec)
    return keyGenerator.generateKey()
}

internal fun encryptAiSecret(value: String): String {
    if (value.isBlank()) return ""
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getAiSecretKey())
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val combined = cipher.iv + encrypted
    return ENCRYPTION_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
}

internal fun decryptAiSecret(value: String?): String {
    if (value.isNullOrBlank()) return ""
    if (!value.startsWith(ENCRYPTION_PREFIX)) return value
    return try {
        val combined = Base64.decode(value.removePrefix(ENCRYPTION_PREFIX), Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getAiSecretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (e: Exception) {
        Timber.e(e, "Failed to decrypt AI key")
        ""
    }
}

internal fun maskedAiSecret(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.length <= 6 -> "***"
        else -> "${trimmed.take(3)}...${trimmed.takeLast(3)}"
    }
}

fun loadAiByokSettings(context: Context): AiByokSettings {
    val prefs = context.aiPrefs()
    if (!prefs.getBoolean(PREF_AI_MODEL_EMPTY_MIGRATION_DONE, false)) {
        prefs.edit {
            if (prefs.getString(PREF_AI_MODEL_ALL, "") == "gemini:gemini-flash-lite-latest") putString(PREF_AI_MODEL_ALL, "")
            if (prefs.getString(PREF_AI_MODEL_DEFINE, "") == "groq:qwen/qwen3-32b") putString(PREF_AI_MODEL_DEFINE, "")
            if (prefs.getString(PREF_AI_MODEL_SUMMARIZE, "") == "gemini:gemini-flash-lite-latest") putString(PREF_AI_MODEL_SUMMARIZE, "")
            if (prefs.getString(PREF_AI_MODEL_RECAP, "") == "gemini:gemini-flash-lite-latest") putString(PREF_AI_MODEL_RECAP, "")
            putBoolean(PREF_AI_MODEL_EMPTY_MIGRATION_DONE, true)
        }
    }
    val settings = AiByokSettings(
        geminiKey = decryptAiSecret(prefs.getString(PREF_AI_GEMINI_KEY, "")),
        groqKey = decryptAiSecret(prefs.getString(PREF_AI_GROQ_KEY, "")),
        fishKey = decryptAiSecret(prefs.getString(PREF_AI_FISH_KEY, "")),
        useOneModel = prefs.getBoolean(PREF_AI_USE_ONE_MODEL, true),
        modelForAll = prefs.getString(PREF_AI_MODEL_ALL, "") ?: "",
        defineModel = prefs.getString(PREF_AI_MODEL_DEFINE, "") ?: "",
        summarizeModel = prefs.getString(PREF_AI_MODEL_SUMMARIZE, "") ?: "",
        recapModel = prefs.getString(PREF_AI_MODEL_RECAP, "") ?: "",
        // Migrate the legacy Live TTS selection to the proper (non-Live)
        // preview TTS model; the Live WebSocket path no longer exists in-app.
        ttsModel = prefs.getString(PREF_AI_TTS_MODEL, "")?.takeIf { it.isNotBlank() }?.let { stored ->
            if (stored == GEMINI_CLOUD_TTS_MODEL_ID) GEMINI_TTS_MODEL_PREVIEW_ID else stored
        } ?: ""
    )
    val geminiStored = prefs.getString(PREF_AI_GEMINI_KEY, "").orEmpty()
    val groqStored = prefs.getString(PREF_AI_GROQ_KEY, "").orEmpty()
    val fishStored = prefs.getString(PREF_AI_FISH_KEY, "").orEmpty()
    if ((geminiStored.isNotBlank() && !geminiStored.startsWith(ENCRYPTION_PREFIX)) ||
        (groqStored.isNotBlank() && !groqStored.startsWith(ENCRYPTION_PREFIX)) ||
        (fishStored.isNotBlank() && !fishStored.startsWith(ENCRYPTION_PREFIX))
    ) {
        saveAiByokSettings(context, settings)
    }
    // Persist the Live -> preview TTS migration for upgraders.
    if (prefs.getString(PREF_AI_TTS_MODEL, "") == GEMINI_CLOUD_TTS_MODEL_ID) {
        saveAiByokSettings(context, settings)
    }
    return settings
}

fun saveAiByokSettings(context: Context, settings: AiByokSettings) {
    context.aiPrefs().edit {
        putString(PREF_AI_GEMINI_KEY, encryptAiSecret(settings.geminiKey.trim()))
        putString(PREF_AI_GROQ_KEY, encryptAiSecret(settings.groqKey.trim()))
        putString(PREF_AI_FISH_KEY, encryptAiSecret(settings.fishKey.trim()))
        putBoolean(PREF_AI_USE_ONE_MODEL, settings.useOneModel)
        putString(PREF_AI_MODEL_ALL, settings.modelForAll)
        putString(PREF_AI_MODEL_DEFINE, settings.defineModel)
        putString(PREF_AI_MODEL_SUMMARIZE, settings.summarizeModel)
        putString(PREF_AI_MODEL_RECAP, settings.recapModel)
        putString(PREF_AI_TTS_MODEL, settings.ttsModel)
    }
}

fun saveAiByokKey(context: Context, provider: String, key: String) {
    val current = loadAiByokSettings(context)
    val updated = when (provider) {
        "gemini" -> current.copy(geminiKey = key)
        "groq" -> current.copy(groqKey = key)
        "fish" -> current.copy(fishKey = key)
        else -> current
    }
    saveAiByokSettings(context, updated)
}

fun deleteAiByokKey(context: Context, provider: String) {
    saveAiByokKey(context, provider, "")
}

fun maskedAiByokKey(context: Context, provider: String): String {
    val settings = loadAiByokSettings(context)
    return maskedAiSecret(
        when (provider) {
            "gemini" -> settings.geminiKey
            "groq" -> settings.groqKey
            "fish" -> settings.fishKey
            else -> ""
        }
    )
}

fun loadHideReaderAiFeatures(context: Context): Boolean {
    return context.aiPrefs().getBoolean(PREF_AI_HIDE_READER_FEATURES, false)
}

fun saveHideReaderAiFeatures(context: Context, hidden: Boolean) {
    context.aiPrefs().edit { putBoolean(PREF_AI_HIDE_READER_FEATURES, hidden) }
}

fun hasAiByokKey(context: Context): Boolean {
    val settings = loadAiByokSettings(context)
    return settings.geminiKey.isNotBlank() || settings.groqKey.isNotBlank() || settings.fishKey.isNotBlank()
}

/**
 * iOS parity: a configured BYOK model+key overrides the credited worker
 * for that feature, even on Pro builds.
 */
internal fun isByokModelReady(context: Context, feature: AiFeature): Boolean {
    return loadAiByokSettings(context).hasByokModel(feature)
}

@Suppress("KotlinConstantConditions")
fun areReaderAiFeaturesEnabled(context: Context): Boolean {
    if (loadHideReaderAiFeatures(context)) return false
    if (BuildConfig.FLAVOR != "oss") return true
    return !BuildConfig.IS_OFFLINE && hasAiByokKey(context)
}

@Suppress("KotlinConstantConditions")
fun isByokCloudTtsAvailable(context: Context): Boolean {
    val settings = loadAiByokSettings(context)
    return BuildConfig.FLAVOR == "oss" &&
            !BuildConfig.IS_OFFLINE &&
            settings.isAnyByokTtsAvailable
}

/**
 * True when the user's own key covers cloud TTS (key + TTS model picked),
 * on any flavor. TtsService already prefers BYOK over the credited worker,
 * so callers use this to skip credit upsells the user will never hit.
 */
internal fun isByokTtsReady(context: Context): Boolean {
    return loadAiByokSettings(context).isAnyByokTtsAvailable
}

fun aiModelById(id: String): AiModelOption? {
    return aiByokModelOptions.firstOrNull { it.id == id }
}

/**
 * Lists Gemini TTS-capable models via the Gemini ListModels API, filtered to
 * TTS models. Falls back to the manual list (no hardcoded prices; the
 * ListModels API provides no pricing, so priceLabel is always null here).
 */
suspend fun fetchGeminiTtsModels(apiKey: String): List<AiModelOption> {
    val key = apiKey.trim()
    if (key.isBlank()) return aiByokTtsModelFallback
    return try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("generativelanguage.googleapis.com")
                .addPathSegments("v1beta/models")
                .addQueryParameter("key", key)
                .addQueryParameter("pageSize", "100")
                .build()
            val response = client.newCall(okhttp3.Request.Builder().url(url).get().build()).execute()
            response.use {
                if (!it.isSuccessful) return@withContext aiByokTtsModelFallback
                val models = org.json.JSONObject(it.body?.string().orEmpty()).optJSONArray("models")
                    ?: return@withContext aiByokTtsModelFallback
                val ttsModels = mutableListOf<AiModelOption>()
                for (i in 0 until models.length()) {
                    val fullName = models.optJSONObject(i)?.optString("name").orEmpty()
                    val shortName = fullName.removePrefix("models/")
                    // The API has no TTS-only filter; TTS models carry "tts"
                    // in the model id (e.g. gemini-3.1-flash-tts-preview).
                    if (shortName.contains("tts", ignoreCase = true)) {
                        ttsModels += AiModelOption(provider = "gemini", name = shortName)
                    }
                }
                // Prefer the known-good models first, then any others found.
                val preferred = aiByokTtsModelFallback.filter { fallback ->
                    ttsModels.any { found -> found.id == fallback.id }
                }
                val rest = ttsModels.filter { found -> preferred.none { it.id == found.id } }
                (preferred + rest).ifEmpty { aiByokTtsModelFallback }
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to list Gemini TTS models, using fallback")
        aiByokTtsModelFallback
    }
}

/**
 * Fish Official's author id: the professional voices Fish themselves post
 * (the `licensed=true` pool is only 7 voices and a subset of this catalog).
 */
internal const val FISH_OFFICIAL_AUTHOR_ID = "d8b0991f96b44e489422ca2ddf0bd31d"

/**
 * Lists voices for BYOK Fish TTS: the user's own voice library plus Fish
 * Official's own catalog (docs: GET /model paginated; `self=true` scopes to
 * the workspace, `author_id` lists one author's public voices). Own voices
 * first, deduped. Used for BYOK Fish TTS voice selection.
 */
suspend fun fetchFishVoices(apiKey: String): List<FishVoice> {
    val key = apiKey.trim()
    if (key.isBlank()) return emptyList()
    // Process-lifetime cache so settings revisits don't refetch; keyed by
    // credential so key rotation refetches. Empty results are never cached.
    val cacheKey = "byok:$key"
    cachedFishVoices(cacheKey)?.let { return it }
    return try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            // Own + public fetch concurrently; each side degrades
            // independently so one slow/failed listing still leaves the other
            // instead of an empty picker.
            val ownDeferred = async { fetchFishModelPages(client, key, selfOnly = true) }
            val publicDeferred = async { fetchFishModelPages(client, key, selfOnly = false) }
            val own = runCatching { ownDeferred.await() }.getOrDefault(emptyList())
            val seen = own.mapTo(mutableSetOf()) { it.referenceId }
            val public = runCatching { publicDeferred.await() }.getOrDefault(emptyList())
                .filter { it.referenceId.isNotBlank() && seen.add(it.referenceId) }
            (own + public).also { if (it.isNotEmpty()) storeFishVoices(cacheKey, it) }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to list Fish voices")
        emptyList()
    }
}

private data class CachedFishVoices(val voices: List<FishVoice>, val fetchedAtMs: Long)

private val fishVoicesCache = mutableMapOf<String, CachedFishVoices>()

private fun cachedFishVoices(cacheKey: String): List<FishVoice>? {
    val cached = synchronized(fishVoicesCache) { fishVoicesCache[cacheKey] } ?: return null
    if (!isFishVoiceListCacheFresh(cached.fetchedAtMs, Clock.System.now().toEpochMilliseconds())) {
        synchronized(fishVoicesCache) { fishVoicesCache.remove(cacheKey) }
        return null
    }
    return cached.voices
}

private fun storeFishVoices(cacheKey: String, voices: List<FishVoice>) {
    synchronized(fishVoicesCache) {
        fishVoicesCache[cacheKey] = CachedFishVoices(voices, Clock.System.now().toEpochMilliseconds())
    }
}

private fun fetchFishModelPages(
    client: okhttp3.OkHttpClient,
    apiKey: String,
    selfOnly: Boolean,
    maxPages: Int = 4,
    pageSize: Int = 100
): List<FishVoice> {
    val voices = mutableListOf<FishVoice>()
    repeat(maxPages) { index ->
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.fish.audio")
            .addPathSegment("model")
            .addQueryParameter("page_size", pageSize.toString())
            .addQueryParameter("page_number", (index + 1).toString())
            // Fish Official's catalog for the public side: the professional
            // voices Fish themselves post, no random user clones.
            // `author_id` is ignored server-side for self.
            .apply {
                if (selfOnly) {
                    addQueryParameter("self", "true")
                } else {
                    addQueryParameter("author_id", FISH_OFFICIAL_AUTHOR_ID)
                }
            }
            .build()
        val response = client.newCall(
            okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
        ).execute()
        response.use {
            if (!it.isSuccessful) return voices
            val body = org.json.JSONObject(it.body?.string().orEmpty())
            val items = body.optJSONArray("items") ?: return voices
            for (i in 0 until items.length()) {
                fishVoiceFromModel(items.optJSONObject(i))?.let(voices::add)
            }
            val hasMore = if (body.has("has_more")) {
                body.optBoolean("has_more")
            } else {
                items.length() >= pageSize
            }
            if (!hasMore) return voices
        }
    }
    return voices
}

internal fun fishVoiceFromModel(item: org.json.JSONObject?): FishVoice? {
    if (item == null) return null
    // Skip voices that cannot synthesize (training failed / still
    // training). `state` is absent on older entries — those are kept.
    val state = item.optString("state")
    if (state.isNotBlank() && state != "trained") return null
    // Only TTS-capable models belong in a TTS picker (`type` is absent on
    // older entries — those are kept).
    val type = item.optString("type")
    if (type.isNotBlank() && type != "tts") return null
    val id = item.optString("_id").ifBlank { item.optString("id") }
    if (id.isBlank()) return null
    return FishVoice(
        id = id,
        referenceId = id,
        title = item.optString("title").ifBlank { item.optString("name").ifBlank { id } },
        description = item.optString("description"),
        languages = fishLanguages(item.optJSONArray("languages")),
        sampleAudioUrl = fishSampleAudioUrl(item.optJSONArray("samples"))
    )
}

/**
 * First pre-generated sample audio URL from a Fish model listing (free to
 * play; empty when the voice exposes no sample).
 */
internal fun fishSampleAudioUrl(samples: org.json.JSONArray?): String {
    if (samples == null) return ""
    for (i in 0 until samples.length()) {
        val url = samples.optJSONObject(i)?.optString("audio").orEmpty()
        if (url.isNotBlank()) return url
    }
    return ""
}

internal fun fishLanguages(array: org.json.JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

/**
 * Fetches the server-pinned Fish voice catalog for credited Cloud TTS
 * (worker GET /v2/voices). Empty when the worker URL is unset or the call fails.
 */
suspend fun fetchCloudFishVoices(workerBaseUrl: String, firebaseToken: String?): List<FishVoice> {
    val base = workerBaseUrl.trim().removeSuffix("/")
    if (base.isBlank() || firebaseToken.isNullOrBlank()) return emptyList()
    // The worker response carries no per-user data (auth only gates access),
    // so it is cached by base URL, not by token. Empty results are never cached.
    val cacheKey = "worker:$base"
    cachedFishVoices(cacheKey)?.let { return it }
    return try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url("$base/v2/voices")
                    .header("Authorization", "Bearer $firebaseToken")
                    .get()
                    .build()
            ).execute()
            response.use {
                if (!it.isSuccessful) return@withContext emptyList()
                val voices = org.json.JSONObject(it.body?.string().orEmpty()).optJSONArray("voices")
                    ?: return@withContext emptyList()
                val out = mutableListOf<FishVoice>()
                for (i in 0 until voices.length()) {
                    val item = voices.optJSONObject(i) ?: continue
                    val referenceId = item.optString("reference_id").ifBlank { item.optString("id") }
                    if (referenceId.isBlank()) continue
                    out += FishVoice(
                        id = item.optString("id").ifBlank { referenceId },
                        referenceId = referenceId,
                        title = item.optString("name").ifBlank { item.optString("title").ifBlank { referenceId } },
                        description = item.optString("description"),
                        languages = fishLanguages(item.optJSONArray("languages")),
                        sampleAudioUrl = item.optString("sample_audio")
                    )
                }
                out.also { if (it.isNotEmpty()) storeFishVoices(cacheKey, it) }
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to fetch cloud Fish voices")
        emptyList()
    }
}
