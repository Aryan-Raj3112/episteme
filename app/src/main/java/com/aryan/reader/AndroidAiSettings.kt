// Common.kt
@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import com.aryan.reader.shared.ReaderAiByokSettings as AiByokSettings

import com.aryan.reader.shared.ReaderAiModelOption as AiModelOption

import com.aryan.reader.shared.ReaderAiFeature as AiFeature

import androidx.compose.material3.ExperimentalMaterial3Api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.edit
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
        useOneModel = prefs.getBoolean(PREF_AI_USE_ONE_MODEL, true),
        modelForAll = prefs.getString(PREF_AI_MODEL_ALL, "") ?: "",
        defineModel = prefs.getString(PREF_AI_MODEL_DEFINE, "") ?: "",
        summarizeModel = prefs.getString(PREF_AI_MODEL_SUMMARIZE, "") ?: "",
        recapModel = prefs.getString(PREF_AI_MODEL_RECAP, "") ?: "",
        ttsModel = prefs.getString(PREF_AI_TTS_MODEL, "") ?: ""
    )
    val geminiStored = prefs.getString(PREF_AI_GEMINI_KEY, "").orEmpty()
    val groqStored = prefs.getString(PREF_AI_GROQ_KEY, "").orEmpty()
    if ((geminiStored.isNotBlank() && !geminiStored.startsWith(ENCRYPTION_PREFIX)) ||
        (groqStored.isNotBlank() && !groqStored.startsWith(ENCRYPTION_PREFIX))
    ) {
        saveAiByokSettings(context, settings)
    }
    return settings
}

fun saveAiByokSettings(context: Context, settings: AiByokSettings) {
    context.aiPrefs().edit {
        putString(PREF_AI_GEMINI_KEY, encryptAiSecret(settings.geminiKey.trim()))
        putString(PREF_AI_GROQ_KEY, encryptAiSecret(settings.groqKey.trim()))
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
    return settings.geminiKey.isNotBlank() || settings.groqKey.isNotBlank()
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
            settings.geminiKey.isNotBlank() &&
            settings.ttsModel == GEMINI_CLOUD_TTS_MODEL_ID
}

fun aiModelById(id: String): AiModelOption? {
    return aiByokModelOptions.firstOrNull { it.id == id }
}
