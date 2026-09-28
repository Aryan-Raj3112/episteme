package com.aryan.reader

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aryan.reader.shared.ReaderFishVoice
import com.aryan.reader.shared.ui.SharedAiSettingsScreen
import com.aryan.reader.shared.ui.SharedAiSettingsStrings
import com.aryan.reader.shared.ui.toggleSharedMobileTtsVoiceFavorite
import com.aryan.reader.tts.loadCloudVoiceLanguage
import com.aryan.reader.tts.saveCloudVoiceLanguage
import com.aryan.reader.tts.loadTtsFavoriteVoices
import com.aryan.reader.tts.saveTtsFavoriteVoices
import com.aryan.reader.tts.saveTtsSpeaker

@Composable
fun AiSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadAiByokSettings(context)) }
    val geminiLabel = stringResource(R.string.provider_gemini)
    val groqLabel = stringResource(R.string.provider_groq)
    val fishLabel = stringResource(R.string.provider_fish)

    var geminiTtsOptions by remember { mutableStateOf(aiByokTtsModelFallback) }
    var fishVoices by remember { mutableStateOf<List<ReaderFishVoice>>(emptyList()) }
    var fishVoicesLoading by remember { mutableStateOf(false) }
    var favoriteFishVoices by remember { mutableStateOf(loadTtsFavoriteVoices(context)) }
    // Shared persisted cloud-voice language filter (same key as the TTS
    // settings cloud tab, so both surfaces agree).
    var fishLanguageFilter by remember { mutableStateOf(loadCloudVoiceLanguage(context)) }

    fun refresh() {
        settings = loadAiByokSettings(context)
    }

    // Live TTS model list (ListModels filtered to TTS) when a Gemini key is
    // saved; manual fallback otherwise. Prices only if the API provides them.
    LaunchedEffect(settings.geminiKey) {
        geminiTtsOptions = if (settings.geminiKey.isNotBlank()) {
            fetchGeminiTtsModels(settings.geminiKey)
        } else {
            aiByokTtsModelFallback
        }
    }

    // All voices the Fish API exposes for the saved Fish key.
    LaunchedEffect(settings.fishKey) {
        if (settings.fishKey.isBlank()) {
            fishVoices = emptyList()
            fishVoicesLoading = false
        } else {
            fishVoicesLoading = true
            fishVoices = fetchFishVoices(settings.fishKey)
            fishVoicesLoading = false
        }
    }

    SharedAiSettingsScreen(
        settings = settings,
        maskedKeys = mapOf(
            "gemini" to maskedAiByokKey(context, "gemini"),
            "groq" to maskedAiByokKey(context, "groq"),
            "fish" to maskedAiByokKey(context, "fish"),
        ),
        strings = SharedAiSettingsStrings(
            title = stringResource(R.string.ai_settings_title),
            backDescription = stringResource(R.string.action_back),
            savedKeys = stringResource(R.string.ai_settings_saved_keys),
            noKeySaved = stringResource(R.string.ai_settings_no_key_saved),
            addOrReplaceKey = stringResource(R.string.ai_settings_add_or_replace_key),
            providerLabel = stringResource(R.string.label_provider),
            apiKeyLabel = stringResource(R.string.label_api_key),
            saveKey = stringResource(R.string.ai_settings_save_key),
            useOneModel = stringResource(R.string.ai_settings_use_one_model),
            useOneModelDescription = stringResource(R.string.ai_settings_use_one_model_desc),
            allFeatures = stringResource(R.string.ai_settings_all_features),
            allFeaturesDescription = stringResource(R.string.ai_settings_all_features_desc),
            smartDictionary = stringResource(R.string.ai_settings_smart_dictionary),
            smartDictionaryDescription = stringResource(R.string.ai_settings_smart_dictionary_desc),
            summaries = stringResource(R.string.ai_settings_summaries),
            summariesDescription = stringResource(R.string.ai_settings_summaries_desc),
            recaps = stringResource(R.string.ai_settings_recaps),
            recapsDescription = stringResource(R.string.ai_settings_recaps_desc),
            cloudTts = stringResource(R.string.credits_cloud_tts_title),
            cloudTtsDescription = stringResource(R.string.ai_settings_cloud_tts_desc),
            modelLabel = stringResource(R.string.label_model),
            noModelSelected = stringResource(R.string.ai_settings_no_model_selected),
            saveDialogDescription = stringResource(R.string.dialog_save_key_desc),
            deleteDialogDescription = stringResource(R.string.dialog_delete_key_desc),
            saveAction = stringResource(R.string.action_save),
            deleteAction = stringResource(R.string.action_delete),
            cancelAction = stringResource(R.string.action_cancel),
            providerLabels = mapOf("gemini" to geminiLabel, "groq" to groqLabel, "fish" to fishLabel),
            saveDialogTitle = { context.getString(R.string.dialog_save_provider_key, it) },
            deleteDialogTitle = { context.getString(R.string.dialog_delete_provider_key, it) },
            deleteKeyDescription = { context.getString(R.string.content_desc_delete_provider_key, it) },
            languageFilterLabel = stringResource(R.string.tts_language_filter),
            favoritesLabel = stringResource(R.string.tts_favorites),
            allLanguagesLabel = stringResource(R.string.filter_all),
            noFishVoicesFound = stringResource(R.string.ai_settings_no_fish_voices),
            noFavoriteVoices = stringResource(R.string.tts_no_favorite_voices),
            noVoicesForLanguage = { context.getString(R.string.tts_no_voices_for_language) },
            addFavoriteDescription = stringResource(R.string.tts_add_favorite),
            removeFavoriteDescription = stringResource(R.string.tts_remove_favorite),
        ),
        onBackClick = onBackClick,
        onSaveKey = { provider, key ->
            saveAiByokKey(context, provider, key)
            refresh()
        },
        onDeleteKey = { provider ->
            deleteAiByokKey(context, provider)
            refresh()
        },
        onSettingsChange = { updated ->
            saveAiByokSettings(context, updated)
            // Keep the reader's active cloud voice in sync with the BYOK
            // voice pick so both settings surfaces agree.
            if (updated.ttsSpeakerId != settings.ttsSpeakerId) {
                saveTtsSpeaker(context, updated.ttsSpeakerId)
            }
            refresh()
        },
        ttsModelOptions = geminiTtsOptions + aiByokTtsModelFallback.filter { fallback ->
            geminiTtsOptions.none { it.id == fallback.id }
        } + com.aryan.reader.shared.ReaderAiModelOption("fish", FISH_TTS_MODEL),
        fishVoices = fishVoices,
        fishVoicesLoading = fishVoicesLoading,
        favoriteFishVoiceIds = favoriteFishVoices,
        onToggleFavoriteFishVoice = { referenceId ->
            favoriteFishVoices = toggleSharedMobileTtsVoiceFavorite(favoriteFishVoices, referenceId)
            saveTtsFavoriteVoices(context, favoriteFishVoices)
        },
        fishLanguageSelection = fishLanguageFilter,
        onFishLanguageSelectionChange = { selection ->
            fishLanguageFilter = selection
            saveCloudVoiceLanguage(context, selection)
        },
        modifier = Modifier.statusBarsPadding(),
    )
}
