package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SharedExternalAppOption(val packageName: String, val label: String, val hasIcon: Boolean)

data class SharedDictionarySettingsLabels(
    val title: String,
    val dictionaryEngine: String,
    val smartAi: String,
    val externalApp: String,
    val aiDescription: String,
    val externalDescription: String,
    val fallbackApp: String,
    val dictionaryApp: String,
    val dictionary: String,
    val translate: String,
    val translateDescription: String,
    val search: String,
    val searchDescription: String,
    val selectApp: String,
    val none: String,
    val selected: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedDictionarySettingsSheet(
    isVisible: Boolean,
    aiFeaturesEnabled: Boolean,
    useOnlineDictionary: Boolean,
    onToggleOnlineDictionary: (Boolean) -> Unit,
    dictionaryApps: List<SharedExternalAppOption>,
    searchApps: List<SharedExternalAppOption>,
    selectedDictionaryPackageName: String?,
    onSelectDictionaryPackage: (String) -> Unit,
    selectedTranslatePackageName: String?,
    onSelectTranslatePackage: (String) -> Unit,
    selectedSearchPackageName: String?,
    onSelectSearchPackage: (String) -> Unit,
    maxSheetHeight: Dp,
    labels: SharedDictionarySettingsLabels,
    appIcon: @Composable (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text(labels.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp))
            if (aiFeaturesEnabled) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(labels.dictionaryEngine, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            SegmentedButton(
                                selected = useOnlineDictionary,
                                onClick = { onToggleOnlineDictionary(true) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text(labels.smartAi) }
                            SegmentedButton(
                                selected = !useOnlineDictionary,
                                onClick = { onToggleOnlineDictionary(false) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text(labels.externalApp) }
                        }
                        Text(
                            if (useOnlineDictionary) labels.aiDescription else labels.externalDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            if (useOnlineDictionary) labels.fallbackApp else labels.dictionaryApp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SharedAppSelectionDropdown(dictionaryApps, selectedDictionaryPackageName, onSelectDictionaryPackage, labels, appIcon)
                    }
                }
            } else {
                Text(labels.dictionary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                SharedAppSelectionDropdown(dictionaryApps, selectedDictionaryPackageName, onSelectDictionaryPackage, labels, appIcon)
            }
            SharedDictionarySectionDivider()
            Text(labels.translate, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            Text(labels.translateDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            SharedAppSelectionDropdown(dictionaryApps, selectedTranslatePackageName, onSelectTranslatePackage, labels, appIcon)
            SharedDictionarySectionDivider()
            Text(labels.search, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            Text(labels.searchDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            SharedAppSelectionDropdown(searchApps, selectedSearchPackageName, onSelectSearchPackage, labels, appIcon)
        }
    }
}

@Composable
private fun SharedDictionarySectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedAppSelectionDropdown(
    apps: List<SharedExternalAppOption>,
    selectedPackageName: String?,
    onSelect: (String) -> Unit,
    labels: SharedDictionarySettingsLabels,
    appIcon: @Composable (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedApp = apps.find { it.packageName == selectedPackageName }
    val hasSelection = !selectedPackageName.isNullOrEmpty() && selectedApp != null
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (hasSelection) selectedApp.label else "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            placeholder = { Text(labels.selectApp) },
            leadingIcon = if (hasSelection && selectedApp.hasIcon) ({ appIcon(selectedApp.packageName) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(labels.none, color = if (!hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                trailingIcon = if (!hasSelection) ({ Icon(Icons.Default.Check, contentDescription = labels.selected, tint = MaterialTheme.colorScheme.primary) }) else null,
                onClick = { onSelect(""); expanded = false }
            )
            if (apps.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            apps.forEach { app ->
                val selected = app.packageName == selectedPackageName
                DropdownMenuItem(
                    text = { Text(app.label) },
                    leadingIcon = {
                        if (app.hasIcon) {
                            appIcon(app.packageName)
                        } else {
                            Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)), Alignment.Center) {
                            }
                        }
                    },
                    trailingIcon = if (selected) ({ Icon(Icons.Default.Check, contentDescription = labels.selected, tint = MaterialTheme.colorScheme.primary) }) else null,
                    onClick = { onSelect(app.packageName); expanded = false }
                )
            }
        }
    }
}
