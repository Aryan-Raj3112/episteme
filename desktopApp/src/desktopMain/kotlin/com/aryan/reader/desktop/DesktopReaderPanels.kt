package com.aryan.reader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiModelOption
import com.aryan.reader.shared.ReaderAiModelOptions
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.maskedReaderAiKey
import com.aryan.reader.shared.ui.SharedMarkdownText
import com.aryan.reader.shared.ui.SharedReaderPopupLayer
import com.aryan.reader.shared.ui.SharedReaderTtsReplacementControls
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.sharedReaderPopupWidth

@Composable
internal fun DesktopReaderBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SharedReaderPopupLayer(onDismiss = onDismiss) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .zIndex(40f)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            val sheetHorizontalPadding = 24.dp
            val sheetAvailableWidth = (maxWidth - sheetHorizontalPadding - sheetHorizontalPadding).coerceAtLeast(0.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = sheetHorizontalPadding, vertical = 16.dp)
                    .width(sharedReaderPopupWidth(sheetAvailableWidth))
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = readerString("action_close", "Close"))
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopReaderAiResultSheet(
    result: ReaderAiResultState,
    onDismiss: () -> Unit
) {
    DesktopReaderBottomSheet(
        title = result.title ?: "AI",
        onDismiss = onDismiss
    ) {
        val errorMessage = result.errorMessage
        when {
            result.isLoading && result.text.isBlank() -> Text(readerString("desktop_working", "Working..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorMessage != null -> Text(errorMessage, color = MaterialTheme.colorScheme.error)
            else -> {
                if (result.isLoading) {
                    Text(readerString("desktop_working", "Working..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SharedMarkdownText(result.text)
            }
        }
    }
}

@Composable
internal fun DesktopAiByokSettingsDialog(
    settings: ReaderAiByokSettings,
    secureStorageAvailable: Boolean,
    onSettingsChange: (ReaderAiByokSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sanitized = settings.sanitized()
    var selectedProvider by remember { mutableStateOf("gemini") }
    var pendingKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("ai_settings_title", "AI keys and models")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!secureStorageAvailable) {
                    Text(
                        readerString(
                            "desktop_secure_key_storage_unavailable",
                            "Secure key storage is unavailable on this operating system. Keys entered here will be used for this session but will not be persisted."
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(readerString("ai_settings_saved_keys", "Saved keys"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DesktopSavedAiKeyRow(
                    label = readerString("provider_gemini", "Gemini"),
                    keyValue = sanitized.geminiKey,
                    onClear = { onSettingsChange(sanitized.copy(geminiKey = "")) }
                )
                DesktopSavedAiKeyRow(
                    label = readerString("provider_groq", "Groq"),
                    keyValue = sanitized.groqKey,
                    onClear = { onSettingsChange(sanitized.copy(groqKey = "")) }
                )

                HorizontalDivider()

                Text(readerString("ai_settings_add_or_replace_key", "Add or replace key"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        "gemini" to readerString("provider_gemini", "Gemini"),
                        "groq" to readerString("provider_groq", "Groq")
                    ).forEach { (provider, label) ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                            label = { Text(label) }
                        )
                    }
                }
                SharedStableOutlinedTextField(
                    value = pendingKey,
                    onValueChange = { pendingKey = it },
                    label = { Text(readerString("label_api_key", "API key")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = pendingKey.isNotBlank(),
                    onClick = {
                        val trimmed = pendingKey.trim()
                        val next = when (selectedProvider) {
                            "gemini" -> sanitized.copy(
                                geminiKey = trimmed,
                                ttsModel = sanitized.ttsModel.ifBlank { GEMINI_CLOUD_TTS_MODEL_ID }
                            )
                            "groq" -> sanitized.copy(groqKey = trimmed)
                            else -> sanitized
                        }
                        onSettingsChange(next)
                        pendingKey = ""
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(readerString("ai_settings_save_key", "Save key"))
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(readerString("ai_settings_use_one_model", "Use one model for all features"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            readerString("ai_settings_use_one_model_desc", "When off, each reader AI feature uses its own selected model."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sanitized.useOneModel,
                        onCheckedChange = { onSettingsChange(sanitized.copy(useOneModel = it)) }
                    )
                }

                if (sanitized.useOneModel) {
                    DesktopAiModelSelector(
                        title = readerString("ai_settings_all_features", "All AI features"),
                        description = readerString("ai_settings_all_features_desc", "Smart dictionary, summaries, and recaps all use this model."),
                        selectedId = sanitized.modelForAll,
                        onSelected = { onSettingsChange(sanitized.copy(modelForAll = it)) }
                    )
                } else {
                    DesktopAiModelSelector(
                        title = readerString("ai_settings_smart_dictionary", "Smart dictionary"),
                        description = readerString("ai_settings_smart_dictionary_desc", "Used when defining selected words or phrases."),
                        selectedId = sanitized.defineModel,
                        onSelected = { onSettingsChange(sanitized.copy(defineModel = it)) }
                    )
                    DesktopAiModelSelector(
                        title = readerString("ai_settings_summaries", "Summaries"),
                        description = readerString("desktop_ai_settings_summaries_desc", "Used for EPUB summaries and PDF page summaries."),
                        selectedId = sanitized.summarizeModel,
                        onSelected = { onSettingsChange(sanitized.copy(summarizeModel = it)) }
                    )
                    DesktopAiModelSelector(
                        title = readerString("ai_settings_recaps", "Recaps"),
                        description = readerString("ai_settings_recaps_desc", "Used for story recap generation."),
                        selectedId = sanitized.recapModel,
                        onSelected = { onSettingsChange(sanitized.copy(recapModel = it)) }
                    )
                }

                DesktopAiModelSelector(
                    title = readerString("credits_cloud_tts_title", "Cloud TTS"),
                    description = readerString("ai_settings_cloud_tts_desc", "Uses the saved Gemini key. Only %1\$s is supported for now.", GEMINI_CLOUD_TTS_MODEL),
                    selectedId = sanitized.ttsModel,
                    options = listOf(ReaderAiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL)),
                    onSelected = { onSettingsChange(sanitized.copy(ttsModel = it)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_done", "Done"))
            }
        }
    )
}

@Composable
private fun DesktopSavedAiKeyRow(
    label: String,
    keyValue: String,
    onClear: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                keyValue.takeIf { it.isNotBlank() }?.let(::maskedReaderAiKey) ?: readerString("ai_settings_no_key_saved", "No key saved"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(enabled = keyValue.isNotBlank(), onClick = onClear) {
            Text(readerString("action_clear", "Clear"))
        }
    }
}

@Composable
private fun DesktopAiModelSelector(
    title: String,
    description: String,
    selectedId: String,
    options: List<ReaderAiModelOption> = ReaderAiModelOptions,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = selectedId.isBlank(),
                onClick = { onSelected("") },
                label = { Text(readerString("ai_settings_no_model_selected", "No model selected")) }
            )
            options.forEach { option ->
                FilterChip(
                    selected = selectedId == option.id,
                    onClick = { onSelected(option.id) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
internal fun DesktopPdfTtsPanel(
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsFeatureAvailable: Boolean,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(readerString("menu_tts_settings", "TTS"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
        if (cloudTtsFeatureAvailable) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            extrasState.cloudTts.isLoading -> readerString("desktop_preparing_audio", "Preparing audio")
                            extrasState.cloudTts.isPaused -> readerString("desktop_paused", "Paused")
                            extrasState.cloudTts.isPlaying -> readerString("label_reading", "Reading")
                            settings.isCloudTtsAvailable -> readerString("desktop_cloud_tts_ready", "Cloud TTS ready")
                            settings.serverBackedReaderAiFeatures -> readerString("desktop_cloud_tts_needs_signed_in_credits", "Cloud TTS needs signed-in credits")
                            else -> readerString("desktop_cloud_tts_needs_gemini", "Cloud TTS needs Gemini")
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    extrasState.cloudTts.errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    val statusMessage = extrasState.cloudTts.progress.currentPositionLabel
                        ?: extrasState.cloudTts.statusMessage?.takeIf { it.isNotBlank() }
                    statusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(readerString("desktop_cloud_tts_voice", "Cloud TTS voice"), fontWeight = FontWeight.SemiBold)
                if (ttsBusy) {
                    Text(
                        readerString("desktop_stop_reading_change_voices", "Stop reading to change voices."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ReaderCloudTtsVoices.forEach { voice ->
                        FilterChip(
                            selected = settings.ttsSpeakerId == voice.id,
                            enabled = !ttsBusy,
                            onClick = { onCloudTtsVoiceChange(voice.id) },
                            label = {
                                Column {
                                    Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        voice.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        )
                    }
                }
            }
            val cacheSummary = extrasState.cloudTts.cacheSummary
            if (cacheSummary.hasCachedAudio) {
                Text(
                    readerString("desktop_cache_format", "Cache: %1\$s", cacheSummary.currentVoiceLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cacheSummary.hasCurrentVoiceCachedAudio) {
                    TextButton(onClick = onCloudTtsClearCache) {
                        Text(readerString("desktop_clear_voice_cache", "Clear voice cache"))
                    }
                }
            }
        }
        SharedReaderTtsReplacementControls(
            preferences = ttsReplacementPreferences,
            bookId = ttsReplacementBookId,
            onPreferencesChange = onTtsReplacementPreferencesChange
        )
    }
}
