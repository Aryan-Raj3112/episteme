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
import androidx.compose.material3.Slider
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
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAiModelOption
import com.aryan.reader.shared.ReaderAiModelOptions
import com.aryan.reader.shared.ReaderAiResultState
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.maskedReaderAiKey
import com.aryan.reader.shared.ui.SharedMarkdownText
import com.aryan.reader.shared.ui.SharedReaderPopupLayer
import com.aryan.reader.shared.ui.SharedReaderTtsReplacementControls
import com.aryan.reader.shared.ui.SharedStableOutlinedTextField
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
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
            result.isLoading -> Text("Working...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorMessage != null -> Text(errorMessage, color = MaterialTheme.colorScheme.error)
            else -> SharedMarkdownText(result.text)
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
        title = { Text("AI keys and models") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!secureStorageAvailable) {
                    Text(
                        "Secure key storage is unavailable on this operating system. Keys entered here will be used for this session but will not be persisted.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Saved keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DesktopSavedAiKeyRow(
                    label = "Gemini",
                    keyValue = sanitized.geminiKey,
                    onClear = { onSettingsChange(sanitized.copy(geminiKey = "", ttsModel = "")) }
                )
                DesktopSavedAiKeyRow(
                    label = "Groq",
                    keyValue = sanitized.groqKey,
                    onClear = { onSettingsChange(sanitized.copy(groqKey = "")) }
                )

                HorizontalDivider()

                Text("Add or replace key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("gemini" to "Gemini", "groq" to "Groq").forEach { (provider, label) ->
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
                    label = { Text("API key") },
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
                    Text("Save key")
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show AI in reader", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Matches the Android hide toggle for smart dictionary, summaries, and recaps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !sanitized.hideReaderAiFeatures,
                        onCheckedChange = { enabled ->
                            onSettingsChange(sanitized.copy(hideReaderAiFeatures = !enabled))
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use one model for all features", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Turn this off to choose separate models per reader AI feature.",
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
                        title = "All AI features",
                        description = "Smart dictionary, summaries, and recaps all use this model.",
                        selectedId = sanitized.modelForAll,
                        onSelected = { onSettingsChange(sanitized.copy(modelForAll = it)) }
                    )
                } else {
                    DesktopAiModelSelector(
                        title = "Smart dictionary",
                        description = "Used when defining selected words or phrases.",
                        selectedId = sanitized.defineModel,
                        onSelected = { onSettingsChange(sanitized.copy(defineModel = it)) }
                    )
                    DesktopAiModelSelector(
                        title = "Summaries",
                        description = "Used for EPUB summaries and PDF page summaries.",
                        selectedId = sanitized.summarizeModel,
                        onSelected = { onSettingsChange(sanitized.copy(summarizeModel = it)) }
                    )
                    DesktopAiModelSelector(
                        title = "Recaps",
                        description = "Used for story recap generation.",
                        selectedId = sanitized.recapModel,
                        onSelected = { onSettingsChange(sanitized.copy(recapModel = it)) }
                    )
                }

                DesktopAiModelSelector(
                    title = "Cloud TTS",
                    description = "Uses the saved Gemini key. Only $GEMINI_CLOUD_TTS_MODEL is supported for now.",
                    selectedId = sanitized.ttsModel,
                    options = listOf(ReaderAiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL)),
                    onSelected = { onSettingsChange(sanitized.copy(ttsModel = it)) }
                )
                Text("Cloud TTS voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderCloudTtsVoices.chunked(3).forEach { rowVoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            rowVoices.forEach { voice ->
                                FilterChip(
                                    selected = sanitized.ttsSpeakerId == voice.id,
                                    onClick = { onSettingsChange(sanitized.copy(ttsSpeakerId = voice.id)) },
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
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
                keyValue.takeIf { it.isNotBlank() }?.let(::maskedReaderAiKey) ?: "No key saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(enabled = keyValue.isNotBlank(), onClick = onClear) {
            Text("Clear")
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
                label = { Text("No model") }
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
internal fun DesktopPdfExtrasPanel(
    pageText: String,
    recapText: String,
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    externalLookupAvailable: Boolean,
    cloudTtsFeatureAvailable: Boolean,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()
    val autoScroll = extrasState.autoScroll.sanitized()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Extras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (externalLookupAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ReaderExternalLookupAction.entries.forEach { action ->
                    FilterChip(
                        selected = false,
                        enabled = pageText.isNotBlank(),
                        onClick = { onExternalLookup(action, pageText) },
                        label = { Text(action.title) }
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Auto scroll", modifier = Modifier.weight(1f))
            Switch(
                checked = autoScroll.enabled,
                onCheckedChange = { onAutoScrollChange(autoScroll.copy(enabled = it)) }
            )
        }
        Slider(
            value = autoScroll.speed,
            onValueChange = { onAutoScrollChange(autoScroll.copy(speed = it).sanitized()) },
            valueRange = 12f..160f
        )
        val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
        if (cloudTtsFeatureAvailable) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            extrasState.cloudTts.isLoading -> "Preparing audio"
                            extrasState.cloudTts.isPaused -> "Paused"
                            extrasState.cloudTts.isPlaying -> "Reading"
                            settings.isCloudTtsAvailable -> "Cloud TTS ready"
                            else -> "Cloud TTS needs Gemini"
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
                TextButton(
                    enabled = settings.isCloudTtsAvailable || ttsBusy,
                    onClick = {
                        if (ttsBusy) {
                            onCloudTtsStop()
                        } else {
                            onCloudTtsStart(ReaderTtsReadScope.BOOK)
                        }
                    }
                ) {
                    Text(if (ttsBusy) "Stop" else "Read")
                }
            }
            if (extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = onCloudTtsPauseResume) {
                        Text(if (extrasState.cloudTts.isPaused) "Resume" else "Pause")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(
                    enabled = settings.isCloudTtsAvailable && !ttsBusy && pageText.isNotBlank(),
                    onClick = { onCloudTtsStart(ReaderTtsReadScope.PAGE) }
                ) {
                    Text("Page")
                }
                TextButton(
                    enabled = settings.isCloudTtsAvailable && !ttsBusy && pageText.isNotBlank(),
                    onClick = { onCloudTtsStart(ReaderTtsReadScope.BOOK) }
                ) {
                    Text("From here")
                }
            }
            val cacheSummary = extrasState.cloudTts.cacheSummary
            if (cacheSummary.hasCachedAudio) {
                Text(
                    "Cache: ${cacheSummary.currentVoiceLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cacheSummary.hasCurrentVoiceCachedAudio) {
                    TextButton(onClick = onCloudTtsClearCache) {
                        Text("Clear voice cache")
                    }
                }
            }
        }
        SharedReaderTtsReplacementControls(
            preferences = ttsReplacementPreferences,
            bookId = ttsReplacementBookId,
            onPreferencesChange = onTtsReplacementPreferencesChange
        )
        if (settings.areReaderAiFeaturesAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(
                    enabled = pageText.isNotBlank() && !extrasState.aiResult.isLoading,
                    onClick = { onAiAction(ReaderAiFeature.SUMMARIZE, pageText) }
                ) {
                    Text("Summarize page")
                }
                TextButton(
                    enabled = recapText.isNotBlank() && !extrasState.aiResult.isLoading,
                    onClick = { onAiAction(ReaderAiFeature.RECAP, recapText) }
                ) {
                    Text("Recap")
                }
            }
        }
    }
}
