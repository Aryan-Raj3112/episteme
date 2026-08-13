package com.aryan.reader

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aryan.reader.shared.ui.SharedAiSettingsScreen
import com.aryan.reader.shared.ui.SharedAiSettingsStrings

@Composable
fun AiSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadAiByokSettings(context)) }
    val geminiLabel = stringResource(R.string.provider_gemini)
    val groqLabel = stringResource(R.string.provider_groq)

    fun refresh() {
        settings = loadAiByokSettings(context)
    }

    SharedAiSettingsScreen(
        settings = settings,
        maskedKeys = mapOf(
            "gemini" to maskedAiByokKey(context, "gemini"),
            "groq" to maskedAiByokKey(context, "groq"),
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
            cloudTtsDescription = stringResource(R.string.ai_settings_cloud_tts_desc, GEMINI_CLOUD_TTS_MODEL),
            modelLabel = stringResource(R.string.label_model),
            noModelSelected = stringResource(R.string.ai_settings_no_model_selected),
            saveDialogDescription = stringResource(R.string.dialog_save_key_desc),
            deleteDialogDescription = stringResource(R.string.dialog_delete_key_desc),
            saveAction = stringResource(R.string.action_save),
            deleteAction = stringResource(R.string.action_delete),
            cancelAction = stringResource(R.string.action_cancel),
            providerLabels = mapOf("gemini" to geminiLabel, "groq" to groqLabel),
            saveDialogTitle = { context.getString(R.string.dialog_save_provider_key, it) },
            deleteDialogTitle = { context.getString(R.string.dialog_delete_provider_key, it) },
            deleteKeyDescription = { context.getString(R.string.content_desc_delete_provider_key, it) },
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
            refresh()
        },
        modifier = Modifier.statusBarsPadding(),
    )
}
