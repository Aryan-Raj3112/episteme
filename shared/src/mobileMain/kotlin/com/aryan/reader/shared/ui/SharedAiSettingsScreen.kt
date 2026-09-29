package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.aryan.reader.shared.AiKeySaveResult
import com.aryan.reader.shared.CloudTtsBackend
import com.aryan.reader.shared.resolveCloudTtsBackend
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL
import com.aryan.reader.shared.GEMINI_CLOUD_TTS_MODEL_ID
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiModelOption
import com.aryan.reader.shared.ReaderAiModelOptions
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderFishVoice
import com.aryan.reader.shared.ReaderTtsByokOptions
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.aiKeySaveErrorMessage
import com.aryan.reader.shared.normalizeAiKeyEntry

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
    // Fish voice list filter + favorites (mirrors the native TTS voice tabs).
    // Hosts that pass no favorites still get the language filter.
    val languageFilterLabel: String = "Language",
    val favoritesLabel: String = "Favorites",
    val allLanguagesLabel: String = "All",
    val noFishVoicesFound: String = "No Fish voices found. Save a Fish API key and create voices at fish.audio.",
    val noFavoriteVoices: String = "No favorite voices yet. Tap the star on any voice to add it here.",
    val noVoicesForLanguage: (String) -> String = { "No voices for $it yet." },
    val addFavoriteDescription: String = "Add to favorites",
    val removeFavoriteDescription: String = "Remove from favorites",
    // Key save/delete outcomes. The host reports what actually happened so a
    // failed write is visible instead of looking like a no-op.
    val onSaveKeyResult: (AiKeySaveResult) -> Unit = { },    // Section titles. Voice selection is its own section, separate from keys
    // and models (Android keeps them on different screens).
    val voicesSectionTitle: String = "Read aloud voice",
    val voicesSectionDescription: String = "Pick the voice used for read aloud.",
    val keySavedMessage: String = "Key saved.",
    // Account reachability, so the backend line can say whether the wallet is
    // actually available. Hosts pass the live values.
    val backendStatusSignedIn: Boolean = false,
    val backendStatusHasToken: Boolean = false,
    val backendStatusHasWorkerUrl: Boolean = false,
)

/** Android-parity AI/BYOK settings UI. Secure storage and persistence stay platform-owned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAiSettingsScreen(
    settings: ReaderAiByokSettings,
    maskedKeys: Map<String, String>,
    strings: SharedAiSettingsStrings,
    onBackClick: () -> Unit,
    /** Persists the key and reports what actually happened. */
    onSaveKey: (provider: String, key: String) -> AiKeySaveResult,    onDeleteKey: (provider: String) -> Unit,    onSettingsChange: (ReaderAiByokSettings) -> Unit,
    cloudCacheSummary: ReaderTtsCacheSummary? = null,
    onClearCloudTtsCache: () -> Unit = {},
    // BYOK TTS model picker options. Hosts pass a live list when available
    // (Gemini ListModels filtered to TTS models); defaults to the manual
    // fallback. Prices render only when an option carries a priceLabel.
    ttsModelOptions: List<ReaderAiModelOption> = ReaderTtsByokOptions,
    // Fish voices for BYOK Fish TTS, as exposed by the Fish API (hosts fetch
    // with the user's Fish key). Empty = key missing or fetch failed.
    fishVoices: List<ReaderFishVoice> = emptyList(),
    fishVoicesLoading: Boolean = false,
    // Starred Fish voice reference ids + toggle. Null = no favorites UI.
    favoriteFishVoiceIds: Set<String> = emptySet(),
    onToggleFavoriteFishVoice: ((String) -> Unit)? = null,
    // Persisted Fish language filter selection. Null = manage internally
    // (not persisted); hosts pass their stored value + saver to persist.
    fishLanguageSelection: String? = null,
    onFishLanguageSelectionChange: ((String) -> Unit)? = null,
    // Temporary iOS launch scope (see IosFeatureGating): iOS passes false to
    // hide cloud TTS model/voice/cache controls while the TTS logic stays.
    // Defaults stay true so Android behavior remains the benchmark.
    showCloudTts: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var currentSettings by remember(settings) { mutableStateOf(settings) }
    var selectedProvider by remember { mutableStateOf("gemini") }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf("") }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<String?>(null) }
    var ttsVoiceMenuExpanded by remember { mutableStateOf(false) }
    // Key persistence outcome. Previously the save button cleared the field and
    // the list simply still read "No key saved" when the write failed, which
    // looked identical to the user never having saved anything.
    var keyBanner by remember { mutableStateOf<SharedAiSettingsBanner?>(null) }
    // Snapshot of the field taken when the confirm dialog opens, so validation
    // sees the value the user actually confirmed (the field is cleared below).
    var pendingKeySnapshot by remember { mutableStateOf("") }

    fun reportKeySave(result: AiKeySaveResult) {
        strings.onSaveKeyResult(result)
        keyBanner = when (result) {
            is AiKeySaveResult.Saved ->
                SharedAiSettingsBanner.Success("${strings.providerLabels[selectedProvider].orEmpty()} ${strings.keySavedMessage}")
            is AiKeySaveResult.Invalid ->
                SharedAiSettingsBanner.Error(aiKeySaveErrorMessage(result.reason))
            is AiKeySaveResult.Failed ->
                SharedAiSettingsBanner.Error(aiKeySaveErrorMessage(result.reason))
        }
    }

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
            // Providers come from the host's labels so new key types (e.g. Fish)
            // appear without screen changes.
            strings.providerLabels.keys.toList().forEach { provider ->
                SharedSavedAiKeyRow(
                    label = strings.providerLabels.getValue(provider),
                    maskedKey = maskedKeys[provider].orEmpty(),
                    noKeySaved = strings.noKeySaved,
                    deleteDescription = strings.deleteKeyDescription(strings.providerLabels.getValue(provider)),
                    onDelete = { providerToDelete = provider },
                )
            }

            // Save/delete outcome banner. Persistent (not a transient snackbar)
            // so a failure stays readable while the user fixes the input.
            keyBanner?.let { banner ->
                SharedAiSettingsBannerRow(banner) { keyBanner = null }
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
                    strings.providerLabels.keys.toList().forEach { provider ->
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
                onClick = {
                    pendingKeySnapshot = pendingKey
                    showSaveConfirm = true
                },
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
            // Intentional temporary iOS scope: cloud TTS controls are hidden
            // while the cloud TTS logic and cache handling are kept for later.
            if (showCloudTts) {
                SharedAiModelSelector(
                    strings.cloudTts,
                    strings.cloudTtsDescription,
                    currentSettings.ttsModel,
                    // Keep a stale/legacy selection visible so it can be changed
                    // away instead of vanishing.
                    (ttsModelOptions + currentSettings.ttsModel.toTtsOptionIfKnown()).distinctBy { it.id },
                    strings,
                ) { updateSettings(currentSettings.copy(ttsModel = it)) }
                // Backend line: which key (if any) will actually pay for the
                // audio, per the Android priority (a saved key beats credits).
                CloudTtsBackendStatusLine(
                    backend = resolveCloudTtsBackend(
                        settings = currentSettings,
                        isSignedIn = strings.backendStatusSignedIn,
                        hasAuthToken = strings.backendStatusHasToken,
                        hasWorkerUrl = strings.backendStatusHasWorkerUrl,
                    ),
                    modelLabel = currentSettings.ttsModel,
                )

                // Voice selection is its own section, separate from keys and
                // models: the model says which engine speaks, the voice says who.
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        strings.voicesSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        strings.voicesSectionDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (currentSettings.ttsProvider == "gemini") {
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
                }
                if (currentSettings.ttsProvider == "fish") {
                    var fishVoiceMenuExpanded by remember { mutableStateOf(false) }
                    var fishLanguageMenuExpanded by remember { mutableStateOf(false) }
                    var internalFishLanguage by remember { mutableStateOf(strings.allLanguagesLabel) }
                    // Language filter + favorites mirror the native TTS voice
                    // tabs. Voices without language info are hidden under a
                    // specific language filter.
                    val fishFilterLanguages = remember(fishVoices) {
                        listOf(strings.favoritesLabel, strings.allLanguagesLabel) +
                            fishVoices.flatMap { it.languages }.filter { it.isNotBlank() }.distinct().sorted()
                    }
                    val effectiveFishLanguage =
                        (fishLanguageSelection ?: internalFishLanguage).takeIf { it in fishFilterLanguages }
                            ?: strings.allLanguagesLabel
                    val showingFishFavorites = effectiveFishLanguage == strings.favoritesLabel
                    val visibleFishVoices = remember(fishVoices, effectiveFishLanguage, showingFishFavorites, favoriteFishVoiceIds) {
                        val base = when {
                            showingFishFavorites || effectiveFishLanguage == strings.allLanguagesLabel -> fishVoices
                            else -> fishVoices.filter { effectiveFishLanguage in it.languages }
                        }
                        if (showingFishFavorites) {
                            base.filter { it.referenceId in favoriteFishVoiceIds }
                        } else base
                    }
                    if (fishFilterLanguages.size > 2 || onToggleFavoriteFishVoice != null) {
                        ExposedDropdownMenuBox(
                            expanded = fishLanguageMenuExpanded,
                            onExpandedChange = { fishLanguageMenuExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = effectiveFishLanguage,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(strings.languageFilterLabel) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fishLanguageMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = fishLanguageMenuExpanded,
                                onDismissRequest = { fishLanguageMenuExpanded = false },
                            ) {
                                fishFilterLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang) },
                                        leadingIcon = if (lang == strings.favoritesLabel) {
                                            {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        } else null,
                                        trailingIcon = if (lang == effectiveFishLanguage) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            internalFishLanguage = lang
                                            onFishLanguageSelectionChange?.invoke(lang)
                                            fishLanguageMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (fishVoicesLoading) {
                        Text(
                            "Loading voices…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (visibleFishVoices.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = fishVoiceMenuExpanded,
                            onExpandedChange = { fishVoiceMenuExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val selectedFishVoice = fishVoices.firstOrNull {
                                it.referenceId == currentSettings.ttsSpeakerId || it.id == currentSettings.ttsSpeakerId
                            }
                            OutlinedTextField(
                                value = selectedFishVoice?.let { "${it.title} · ${it.description}".trimEnd(' ', '·') }
                                    ?: currentSettings.ttsSpeakerId.ifBlank { "Select a voice" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Voice") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fishVoiceMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = fishVoiceMenuExpanded,
                                onDismissRequest = { fishVoiceMenuExpanded = false },
                            ) {
                                visibleFishVoices.forEach { voice ->
                                    val isFavorite = voice.referenceId in favoriteFishVoiceIds
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(voice.title)
                                                if (voice.description.isNotBlank()) {
                                                    Text(voice.description, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        },
                                        leadingIcon = if (onToggleFavoriteFishVoice != null) {
                                            {
                                                IconButton(onClick = { onToggleFavoriteFishVoice.invoke(voice.referenceId) }) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = if (isFavorite) {
                                                            strings.removeFavoriteDescription
                                                        } else {
                                                            strings.addFavoriteDescription
                                                        },
                                                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        } else null,
                                        onClick = {
                                            updateSettings(currentSettings.copy(ttsSpeakerId = voice.referenceId))
                                            fishVoiceMenuExpanded = false
                                        },
                                        trailingIcon = if (voice.referenceId == currentSettings.ttsSpeakerId || voice.id == currentSettings.ttsSpeakerId) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null,
                                    )
                                }
                            }
                        }
                    } else if (!fishVoicesLoading) {
                        Text(
                            when {
                                fishVoices.isEmpty() -> strings.noFishVoicesFound
                                showingFishFavorites -> strings.noFavoriteVoices
                                else -> strings.noVoicesForLanguage(effectiveFishLanguage)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = currentSettings.ttsSpeakerId,
                        onValueChange = { updateSettings(currentSettings.copy(ttsSpeakerId = it)) },
                        label = { Text("Voice reference ID (advanced)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (currentSettings.ttsModel.isNotBlank()) {
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
    }

    if (showSaveConfirm) {
        val providerLabel = strings.providerLabels[selectedProvider].orEmpty()
        AlertDialog(            onDismissRequest = { showSaveConfirm = false },
            title = { Text(strings.saveDialogTitle(providerLabel)) },
            text = { Text(strings.saveDialogDescription) },
            confirmButton = {
                TextButton(onClick = {
                    val entry = pendingKeySnapshot
                    pendingKey = ""
                    pendingKeySnapshot = ""
                    showSaveConfirm = false
                    // Validate before persisting so a pasted key with spaces or
                    // a stray "Bearer " prefix is reported instead of silently
                    // becoming an unusable credential.
                    val validated = normalizeAiKeyEntry(entry)
                    if (validated is AiKeySaveResult.Invalid) {
                        reportKeySave(validated)
                        return@TextButton
                    }
                    // The host owns persistence and is authoritative about the
                    // outcome (a keychain write can fail after validation).
                    reportKeySave(onSaveKey(selectedProvider, entry))
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
                    keyBanner = SharedAiSettingsBanner.Success("${strings.providerLabels[provider].orEmpty()} key removed.")
                }) { Text(strings.deleteAction) }
            },
            dismissButton = { TextButton(onClick = { providerToDelete = null }) { Text(strings.cancelAction) } },
        )
    }
}

/** Maps a stored TTS model id back to a displayable option, including the
 * legacy Live model so old selections stay visible until changed. */
private fun String.toTtsOptionIfKnown(): List<ReaderAiModelOption> {
    if (isBlank()) return emptyList()
    if (this == GEMINI_CLOUD_TTS_MODEL_ID) {
        return listOf(ReaderAiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL))
    }
    val provider = substringBefore(':', "")
    val name = substringAfter(':', "")
    if (provider.isBlank() || name.isBlank()) return emptyList()
    return listOf(ReaderAiModelOption(provider, name))
}

/**
 * One-line readout of which backend the current model + keys will actually use.
 *
 * Android parity: a BYOK key always wins over spending credits, Fish before
 * Gemini (`TtsService.audioGenerator`). The previous screen said nothing about
 * this, so a misconfigured setup (e.g. a Fish model selected with only a Gemini
 * key saved) looked configured while quietly spending credits — or, with no
 * credits, silently doing nothing.
 */
@Composable
private fun CloudTtsBackendStatusLine(
    backend: CloudTtsBackend,
    modelLabel: String,
) {
    val (text, isError) = when (backend) {
        CloudTtsBackend.FISH_BYOK -> "Uses your Fish Audio key — no credits spent." to false
        CloudTtsBackend.GEMINI_BYOK -> "Uses your Gemini key — no credits spent." to false
        CloudTtsBackend.WORKER -> "No key for this model, so your wallet pays per chunk." to false
        CloudTtsBackend.UNAVAILABLE ->
            "Not ready: this model has no matching saved key. Save a ${if (modelLabel.startsWith("fish")) "Fish Audio" else "Gemini"} key, or sign in to use the wallet." to true
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Outcome of a key save/delete, rendered as a persistent banner. */
internal sealed interface SharedAiSettingsBanner {
    val message: String

    data class Success(override val message: String) : SharedAiSettingsBanner
    data class Error(override val message: String) : SharedAiSettingsBanner
}

@Composable
private fun SharedAiSettingsBannerRow(
    banner: SharedAiSettingsBanner,
    onDismiss: () -> Unit,
) {
    val isError = banner is SharedAiSettingsBanner.Error
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = banner.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
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
                value = selected?.let { option ->
                    option.priceLabel?.let { "${option.label} · $it" } ?: option.label
                } ?: strings.noModelSelected,
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
                        text = { Text(option.priceLabel?.let { "${option.label} · $it" } ?: option.label) },
                        onClick = { onSelected(option.id); expanded = false },
                        trailingIcon = if (option.id == selected?.id) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    )
                }
            }
        }
    }
}
