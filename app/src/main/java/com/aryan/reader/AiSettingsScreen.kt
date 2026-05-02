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
                title = { Text("AI keys and models") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("Saved keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SavedKeyRow("Gemini", maskedAiByokKey(context, "gemini"), onDelete = { providerToDelete = "gemini" })
            SavedKeyRow("Groq", maskedAiByokKey(context, "groq"), onDelete = { providerToDelete = "groq" })

            HorizontalDivider()

            Text("Add or replace key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedProvider.replaceFirstChar { it.titlecase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    listOf("gemini", "groq").forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.replaceFirstChar { it.titlecase() }) },
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
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { showSaveConfirm = true },
                enabled = pendingKey.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save key")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use one model for all features", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "When off, each reader AI feature uses its own selected model.",
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
                    title = "All AI features",
                    description = "Smart dictionary, summaries, and recaps all use this model.",
                    selectedId = settings.modelForAll,
                    onSelected = { updateModels(settings.copy(modelForAll = it)) }
                )
            } else {
                ModelSelector(
                    title = "Smart dictionary",
                    description = "Used when defining selected words or phrases.",
                    selectedId = settings.defineModel,
                    onSelected = { updateModels(settings.copy(defineModel = it)) }
                )
                ModelSelector(
                    title = "Summaries",
                    description = "Used for EPUB summaries and PDF page summaries. PDF/image summaries need Gemini.",
                    selectedId = settings.summarizeModel,
                    onSelected = { updateModels(settings.copy(summarizeModel = it)) }
                )
                ModelSelector(
                    title = "Recaps",
                    description = "Used for story recap generation.",
                    selectedId = settings.recapModel,
                    onSelected = { updateModels(settings.copy(recapModel = it)) }
                )
            }

            ModelSelector(
                title = "Cloud TTS",
                description = "Uses the saved Gemini key. Only $GEMINI_CLOUD_TTS_MODEL is supported for now.",
                selectedId = settings.ttsModel,
                options = listOf(AiModelOption("gemini", GEMINI_CLOUD_TTS_MODEL)),
                onSelected = { updateModels(settings.copy(ttsModel = it)) }
            )
        }
    }

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Save ${selectedProvider.replaceFirstChar { it.titlecase() }} key?") },
            text = { Text("After saving, only the first 3 and last 3 characters will be visible. To change it later, replace or delete it.") },
            confirmButton = {
                TextButton(onClick = {
                    saveAiByokKey(context, selectedProvider, pendingKey)
                    pendingKey = ""
                    showSaveConfirm = false
                    refresh()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    providerToDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text("Delete ${provider.replaceFirstChar { it.titlecase() }} key?") },
            text = { Text("Features using this provider will stop working until a new key is saved.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteAiByokKey(context, provider)
                    providerToDelete = null
                    refresh()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) { Text("Cancel") }
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
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(maskedKey.ifBlank { "No key saved" })
        },
        trailingContent = {
            IconButton(onClick = onDelete, enabled = maskedKey.isNotBlank()) {
                Icon(Icons.Default.Delete, contentDescription = "Delete $label key")
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
                value = selected?.label ?: "No model selected",
                onValueChange = {},
                readOnly = true,
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("No model selected") },
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
