package com.aryan.reader.epubreader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.aryan.reader.R
import com.aryan.reader.areReaderAiFeaturesEnabled

@Suppress("KotlinConstantConditions")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    isProUser: Boolean,
    useOnlineDictionary: Boolean,
    onToggleOnlineDictionary: (Boolean) -> Unit,
    selectedDictionaryPackageName: String?,
    onSelectDictionaryPackage: (String) -> Unit,
    selectedTranslatePackageName: String?,
    onSelectTranslatePackage: (String) -> Unit,
    selectedSearchPackageName: String?,
    onSelectSearchPackage: (String) -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    var dictionaryApps by remember { mutableStateOf<List<ExternalDictionaryApp>>(emptyList()) }
    var searchApps by remember { mutableStateOf<List<ExternalDictionaryApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        dictionaryApps = ExternalDictionaryHelper.getAvailableDictionaries(context)
        searchApps = ExternalDictionaryHelper.getAvailableSearchApps(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.dict_lookup_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // ── Dictionary ──
                if (areReaderAiFeaturesEnabled(context)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.dict_dictionary_engine),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                SegmentedButton(
                                    selected = useOnlineDictionary,
                                    onClick = { onToggleOnlineDictionary(true) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text(stringResource(R.string.dict_smart_ai))
                                }
                                SegmentedButton(
                                    selected = !useOnlineDictionary,
                                    onClick = { onToggleOnlineDictionary(false) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text(stringResource(R.string.dict_external_app))
                                }
                            }

                            Text(
                                text = if (useOnlineDictionary)
                                    stringResource(R.string.dict_ai_description)
                                else
                                    stringResource(R.string.dict_external_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Text(
                                text = if (useOnlineDictionary) stringResource(R.string.dict_fallback_app) else stringResource(R.string.dict_dictionary_app),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            AppSelectionDropdown(
                                apps = dictionaryApps,
                                selectedPackageName = selectedDictionaryPackageName,
                                onSelect = onSelectDictionaryPackage,
                                placeholder = stringResource(R.string.dict_select_app)
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.tooltip_dictionary),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AppSelectionDropdown(
                        apps = dictionaryApps,
                        selectedPackageName = selectedDictionaryPackageName,
                        onSelect = onSelectDictionaryPackage,
                        placeholder = stringResource(R.string.dict_select_app)
                    )
                }

                SectionDivider()

                // ── Translate ──
                Text(
                    text = stringResource(R.string.dict_translate),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.dict_translate_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                AppSelectionDropdown(
                    apps = dictionaryApps,
                    selectedPackageName = selectedTranslatePackageName,
                    onSelect = onSelectTranslatePackage,
                    placeholder = stringResource(R.string.dict_select_app)
                )

                SectionDivider()

                // ── Search ──
                Text(
                    text = stringResource(R.string.tooltip_search),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.dict_search_app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                AppSelectionDropdown(
                    apps = searchApps,
                    selectedPackageName = selectedSearchPackageName,
                    onSelect = onSelectSearchPackage,
                    placeholder = stringResource(R.string.dict_select_app)
                )
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSelectionDropdown(
    apps: List<ExternalDictionaryApp>,
    selectedPackageName: String?,
    onSelect: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedApp = apps.find { it.packageName == selectedPackageName }
    val hasSelection = !selectedPackageName.isNullOrEmpty() && selectedApp != null

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = if (hasSelection) selectedApp.label else "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = if (hasSelection && selectedApp.icon != null) {
                {
                    Image(
                        bitmap = selectedApp.icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // None option
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.dict_none),
                        color = if (!hasSelection) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingIcon = if (!hasSelection) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.content_desc_selected),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
                onClick = {
                    onSelect("")
                    expanded = false
                }
            )

            if (apps.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            apps.forEach { app ->
                val isSelected = app.packageName == selectedPackageName
                DropdownMenuItem(
                    text = { Text(app.label) },
                    leadingIcon = {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center,
                                content = {}
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.content_desc_selected),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        onSelect(app.packageName)
                        expanded = false
                    }
                )
            }
        }
    }
}
