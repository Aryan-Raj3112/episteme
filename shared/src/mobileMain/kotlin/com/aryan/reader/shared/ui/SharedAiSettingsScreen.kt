package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiModelOption
import com.aryan.reader.shared.ReaderAiModelOptions
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderTtsCacheSummary

data class SharedAiSettingsStrings(
    val title: String,
    val backDescription: String,
    val savedKeys: String,
    val noKeySaved: String,
    val addOrReplaceKey: String,
    val providerLabel: String,
    val apiKeyLabel: String,
    val saveKey: String,
    val useOneModel: String,
    val useOneModelDescription: String,
    val allFeatures: String,
    val allFeaturesDescription: String,
    val smartDictionary: String,
    val smartDictionaryDescription: String,
    val summaries: String,
    val summariesDescription: String,
    val recaps: String,
    val recapsDescription: String,
    val cloudTts: String,
    val cloudTtsDescription: String,
    val modelLabel: String,
    val noModelSelected: String,
    val saveDialogDescription: String,
    val deleteDialogDescription: String,
    val saveAction: String,
    val deleteAction: String,
    val cancelAction: String,
    val providerLabels: Map<String, String>,
    val saveDialogTitle: (String) -> String,
    val deleteDialogTitle: (String) -> String,
    val deleteKeyDescription: (String) -> String,
)

/** Android-parity AI/BYOK settings UI. Secure storage and persistence stay platform-owned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAiSettingsScreen(
    settings: ReaderAiByokSettings,
    maskedKeys: Map<String, String>,
    strings: SharedAiSettingsStrings,
    onBackClick: () -> Unit,
    onSaveKey: (provider: String, key: String) -> Unit,
    onDeleteKey: (provider: String) -> Unit,
    onSettingsChange: (ReaderAiByokSettings) -> Unit,
    cloudCacheSummary: ReaderTtsCacheSummary? = null,
    onClearCloudTtsCache: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var currentSettings by remember(settings) { mutableStateOf(settings) }
    var selectedProvider by remember { mutableStateOf("gemini") }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf("") }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<String?>(null) }
    var ttsVoiceMenuExpanded by remember { mutableStateOf(false) }

    fun updateSettings(updated: ReaderAiByokSettings) {
        currentSettings = updated
        onSettingsChange(updated)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SharedMobileTopAppBar(
                title = { Text(strings.title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backDescription)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.savedKeys, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOf("gemini", "groq").forEach { provider ->
                SharedSavedAiKeyRow(
                    label = strings.providerLabels.getValue(provider),
                    maskedKey = maskedKeys[provider].orEmpty(),
                    noKeySaved = strings.noKeySaved,
                    deleteDescription = strings.deleteKeyDescription(strings.providerLabels.getValue(provider)),
                    onDelete = { providerToDelete = provider },
                )
            }

            HorizontalDivider()
            Text(strings.addOrReplaceKey, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = strings.providerLabels[selectedProvider].orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.providerLabel) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                    listOf("gemini", "groq").forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(strings.providerLabels[provider].orEmpty()) },
                            onClick = {
                                selectedProvider = provider
                                providerMenuExpanded = false
                            },
                            trailingIcon = if (provider == selectedProvider) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = pendingKey,
                onValueChange = { pendingKey = it },
                label = { Text(strings.apiKeyLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { showSaveConfirm = true },
                enabled = pendingKey.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text(strings.saveKey) }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.useOneModel, style = MaterialTheme.typography.titleMedium)
                    Text(strings.useOneModelDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = currentSettings.useOneModel,
                    onCheckedChange = { updateSettings(currentSettings.copy(useOneModel = it)) },
                )
            }

            if (currentSettings.useOneModel) {
                SharedAiModelSelector(strings.allFeatures, strings.allFeaturesDescription, currentSettings.modelForAll, ReaderAiModelOptions, strings) {
                    updateSettings(currentSettings.copy(modelForAll = it))
                }
            } else {
                SharedAiModelSelector(strings.smartDictionary, strings.smartDictionaryDescription, currentSettings.defineModel, ReaderAiModelOptions, strings) {
                    updateSettings(currentSettings.copy(defineModel = it))
                }
                SharedAiModelSelector(strings.summaries, strings.summariesDescription, currentSettings.summarizeModel, ReaderAiModelOptions, strings) {
                    updateSettings(currentSettings.copy(summarizeModel = it))
                }
                SharedAiModelSelector(strings.recaps, strings.recapsDescription, currentSettings.recapModel, ReaderAiModelOptions, strings) {
                    updateSettings(currentSettings.copy(recapModel = it))
                }
            }
            SharedAiModelSelector(
                strings.cloudTts,
                strings.cloudTtsDescription,
                currentSettings.ttsModel,
                listOf(ReaderAiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL)),
                strings,
            ) { updateSettings(currentSettings.copy(ttsModel = it)) }
            if (currentSettings.ttsModel == GEMINI_CLOUD_TTS_MODEL_ID) {
                Text("Cloud TTS voice", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(
                    expanded = ttsVoiceMenuExpanded,
                    onExpandedChange = { ttsVoiceMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val selectedVoice = ReaderCloudTtsVoices.firstOrNull { it.id == currentSettings.ttsSpeakerId }
                        ?: ReaderCloudTtsVoices.first()
                    OutlinedTextField(
                        value = "${selectedVoice.name} · ${selectedVoice.description}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Voice") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ttsVoiceMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = ttsVoiceMenuExpanded,
                        onDismissRequest = { ttsVoiceMenuExpanded = false },
                    ) {
                        ReaderCloudTtsVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(voice.name)
                                        Text(voice.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    updateSettings(currentSettings.copy(ttsSpeakerId = voice.id))
                                    ttsVoiceMenuExpanded = false
                                },
                                trailingIcon = if (voice.id == currentSettings.ttsSpeakerId) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                }
                cloudCacheSummary?.let { cache ->
                    Text(
                        if (cache.hasCachedAudio) {
                            "Cached cloud audio: ${cache.cachedChunkCount} chunks · ${cache.currentVoiceLabel}"
                        } else {
                            "No cached cloud audio"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cache.hasCachedAudio) {
                        TextButton(onClick = onClearCloudTtsCache) { Text("Clear cached cloud audio") }
                    }
                }
            }
        }
    }

    if (showSaveConfirm) {
        val providerLabel = strings.providerLabels[selectedProvider].orEmpty()
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text(strings.saveDialogTitle(providerLabel)) },
            text = { Text(strings.saveDialogDescription) },
            confirmButton = {
                TextButton(onClick = {
                    onSaveKey(selectedProvider, pendingKey)
                    pendingKey = ""
                    showSaveConfirm = false
                }) { Text(strings.saveAction) }
            },
            dismissButton = { TextButton(onClick = { showSaveConfirm = false }) { Text(strings.cancelAction) } },
        )
    }
    providerToDelete?.let { provider ->
        val providerLabel = strings.providerLabels[provider].orEmpty()
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text(strings.deleteDialogTitle(providerLabel)) },
            text = { Text(strings.deleteDialogDescription) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteKey(provider)
                    providerToDelete = null
                }) { Text(strings.deleteAction) }
            },
            dismissButton = { TextButton(onClick = { providerToDelete = null }) { Text(strings.cancelAction) } },
        )
    }
}

@Composable
private fun SharedSavedAiKeyRow(
    label: String,
    maskedKey: String,
    noKeySaved: String,
    deleteDescription: String,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(maskedKey.ifBlank { noKeySaved }) },
        trailingContent = {
            IconButton(onClick = onDelete, enabled = maskedKey.isNotBlank()) {
                Icon(Icons.Default.Delete, contentDescription = deleteDescription)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedAiModelSelector(
    title: String,
    description: String,
    selectedId: String,
    options: List<ReaderAiModelOption>,
    strings: SharedAiSettingsStrings,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selected?.label ?: strings.noModelSelected,
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.modelLabel) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(strings.noModelSelected) },
                    onClick = { onSelected(""); expanded = false },
                    trailingIcon = if (selectedId.isBlank()) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSelected(option.id); expanded = false },
                        trailingIcon = if (option.id == selected?.id) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    )
                }
            }
        }
    }
}
