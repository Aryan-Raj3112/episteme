package com.aryan.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadAiByokSettings(context)) }
    var selectedProvider by remember { mutableStateOf("gemini") }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf("") }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<String?>(null) }
    val providerLabels = mapOf(
        "gemini" to stringResource(R.string.provider_gemini),
        "groq" to stringResource(R.string.provider_groq),
    )

    fun refresh() {
        settings = loadAiByokSettings(context)
    }

    fun updateModels(newSettings: AiByokSettings) {
        saveAiByokSettings(context, newSettings)
        settings = loadAiByokSettings(context)
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            CustomTopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.ai_settings_saved_keys), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SavedKeyRow(providerLabels.getValue("gemini"), maskedAiByokKey(context, "gemini"), onDelete = { providerToDelete = "gemini" })
            SavedKeyRow(providerLabels.getValue("groq"), maskedAiByokKey(context, "groq"), onDelete = { providerToDelete = "groq" })

            HorizontalDivider()

            Text(stringResource(R.string.ai_settings_add_or_replace_key), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = providerLabels[selectedProvider].orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_provider)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    listOf("gemini", "groq").forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(providerLabels[provider].orEmpty()) },
                            onClick = {
                                selectedProvider = provider
                                providerMenuExpanded = false
                            },
                            trailingIcon = if (provider == selectedProvider) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
            OutlinedTextField(
                value = pendingKey,
                onValueChange = { pendingKey = it },
                label = { Text(stringResource(R.string.label_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { showSaveConfirm = true },
                enabled = pendingKey.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.ai_settings_save_key))
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_settings_use_one_model), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.ai_settings_use_one_model_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.useOneModel,
                    onCheckedChange = { updateModels(settings.copy(useOneModel = it)) }
                )
            }

            if (settings.useOneModel) {
                ModelSelector(
                    title = stringResource(R.string.ai_settings_all_features),
                    description = stringResource(R.string.ai_settings_all_features_desc),
                    selectedId = settings.modelForAll,
                    onSelected = { updateModels(settings.copy(modelForAll = it)) }
                )
            } else {
                ModelSelector(
                    title = stringResource(R.string.ai_settings_smart_dictionary),
                    description = stringResource(R.string.ai_settings_smart_dictionary_desc),
                    selectedId = settings.defineModel,
                    onSelected = { updateModels(settings.copy(defineModel = it)) }
                )
                ModelSelector(
                    title = stringResource(R.string.ai_settings_summaries),
                    description = stringResource(R.string.ai_settings_summaries_desc),
                    selectedId = settings.summarizeModel,
                    onSelected = { updateModels(settings.copy(summarizeModel = it)) }
                )
                ModelSelector(
                    title = stringResource(R.string.ai_settings_recaps),
                    description = stringResource(R.string.ai_settings_recaps_desc),
                    selectedId = settings.recapModel,
                    onSelected = { updateModels(settings.copy(recapModel = it)) }
                )
            }

            ModelSelector(
                title = stringResource(R.string.credits_cloud_tts_title),
                description = stringResource(R.string.ai_settings_cloud_tts_desc, GEMINI_CLOUD_TTS_MODEL),
                selectedId = settings.ttsModel,
                options = listOf(AiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL)),
                onSelected = { updateModels(settings.copy(ttsModel = it)) }
            )
        }
    }

    if (showSaveConfirm) {
        val providerLabel = providerLabels[selectedProvider].orEmpty()
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text(stringResource(R.string.dialog_save_provider_key, providerLabel)) },
            text = { Text(stringResource(R.string.dialog_save_key_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    saveAiByokKey(context, selectedProvider, pendingKey)
                    pendingKey = ""
                    showSaveConfirm = false
                    refresh()
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    providerToDelete?.let { provider ->
        val providerLabel = providerLabels[provider].orEmpty()
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_provider_key, providerLabel)) },
            text = { Text(stringResource(R.string.dialog_delete_key_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteAiByokKey(context, provider)
                    providerToDelete = null
                    refresh()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun SavedKeyRow(
    label: String,
    maskedKey: String,
    onDelete: () -> Unit
) {
    val noKeySaved = stringResource(R.string.ai_settings_no_key_saved)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(maskedKey.ifBlank { noKeySaved })
        },
        trailingContent = {
            IconButton(onClick = onDelete, enabled = maskedKey.isNotBlank()) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.content_desc_delete_provider_key, label))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    title: String,
    description: String,
    selectedId: String,
    options: List<AiModelOption> = aiByokModelOptions,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selected?.label ?: stringResource(R.string.ai_settings_no_model_selected),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.label_model)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_settings_no_model_selected)) },
                    onClick = {
                        onSelected("")
                        expanded = false
                    },
                    trailingIcon = if (selectedId.isBlank()) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option.id)
                            expanded = false
                        },
                        trailingIcon = if (option.id == selected?.id) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }
        }
    }
}
