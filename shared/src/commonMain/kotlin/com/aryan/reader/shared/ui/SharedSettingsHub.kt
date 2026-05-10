package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsHubModel
import com.aryan.reader.shared.SharedSettingsItemKind
import com.aryan.reader.shared.SharedSettingsItemModel
import com.aryan.reader.shared.SharedSettingsSection
import com.aryan.reader.shared.reader.ReaderSettings

private enum class SharedSettingsReaderPane(val title: String) {
    TEXT("Text"),
    THEME("Theme"),
    VISUAL("Visual"),
    PDF("PDF"),
    TOOLBAR("Toolbar"),
    TTS("TTS")
}

@Composable
fun SharedSettingsHub(
    model: SharedSettingsHubModel,
    query: String,
    onQueryChange: (String) -> Unit,
    readerDefaultSettings: ReaderSettings,
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onAction: (SharedSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    readerToolbarPreferences: ReaderToolbarPreferences? = null,
    onReaderToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onPickCustomFont: (() -> String?)? = null,
    readerCustomTextureIds: List<String> = emptyList(),
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    showTopBar: Boolean = true,
    onBack: (() -> Unit)? = null
) {
    val filteredModel = remember(model, query) { model.filtered(query) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showTopBar) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Global defaults live here. Per-book overrides stay inside the reader.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search settings") }
        )

        if (filteredModel.sections.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(36.dp))
                    Text("No settings found", fontWeight = FontWeight.SemiBold)
                    Text(query, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                filteredModel.sections.forEach { section ->
                    item(key = section.section.name) {
                        SharedSettingsSectionCard(
                            section = section.section,
                            items = section.items,
                            readerDefaultSettings = readerDefaultSettings,
                            onReaderDefaultSettingsChange = onReaderDefaultSettingsChange,
                            readerToolbarPreferences = readerToolbarPreferences,
                            onReaderToolbarPreferencesChange = onReaderToolbarPreferencesChange,
                            ttsReplacementPreferences = ttsReplacementPreferences,
                            onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
                            customFonts = customFonts,
                            onPickCustomFont = onPickCustomFont,
                            readerCustomTextureIds = readerCustomTextureIds,
                            onImportReaderTexture = onImportReaderTexture,
                            onAction = onAction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSettingsSectionCard(
    section: SharedSettingsSection,
    items: List<SharedSettingsItemModel>,
    readerDefaultSettings: ReaderSettings,
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit,
    readerToolbarPreferences: ReaderToolbarPreferences?,
    onReaderToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    customFonts: List<CustomFontItem>,
    onPickCustomFont: (() -> String?)?,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onAction: (SharedSettingsAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(section.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (section == SharedSettingsSection.READER) {
                SharedReaderDefaultsSettingsPanel(
                    items = items,
                    settings = readerDefaultSettings,
                    onSettingsChange = onReaderDefaultSettingsChange,
                    toolbarPreferences = readerToolbarPreferences,
                    onToolbarPreferencesChange = onReaderToolbarPreferencesChange,
                    ttsReplacementPreferences = ttsReplacementPreferences,
                    onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
                    customFonts = customFonts,
                    onPickCustomFont = onPickCustomFont,
                    readerCustomTextureIds = readerCustomTextureIds,
                    onImportReaderTexture = onImportReaderTexture,
                    onAction = onAction
                )
            } else {
                items.forEachIndexed { index, item ->
                    SharedSettingsRow(item = item, onAction = onAction)
                    if (index != items.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedReaderDefaultsSettingsPanel(
    items: List<SharedSettingsItemModel>,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    toolbarPreferences: ReaderToolbarPreferences?,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    customFonts: List<CustomFontItem>,
    onPickCustomFont: (() -> String?)?,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onAction: (SharedSettingsAction) -> Unit
) {
    val availableActions = items.mapTo(mutableSetOf()) { it.action }
    val availablePanes = buildList {
        if (SharedSettingsAction.TEXT_READER_DEFAULTS in availableActions) {
            add(SharedSettingsReaderPane.TEXT)
            add(SharedSettingsReaderPane.THEME)
            add(SharedSettingsReaderPane.VISUAL)
        }
        if (SharedSettingsAction.PDF_READER_DEFAULTS in availableActions) add(SharedSettingsReaderPane.PDF)
        if (SharedSettingsAction.READER_TOOLBAR in availableActions) add(SharedSettingsReaderPane.TOOLBAR)
        if (SharedSettingsAction.TTS_REPLACEMENTS in availableActions) add(SharedSettingsReaderPane.TTS)
    }
    var selectedPane by remember(availablePanes) { mutableStateOf(availablePanes.firstOrNull() ?: SharedSettingsReaderPane.TEXT) }
    if (selectedPane !in availablePanes && availablePanes.isNotEmpty()) {
        selectedPane = availablePanes.first()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.firstOrNull { it.action == SharedSettingsAction.LOCAL_OVERRIDE_NOTE }?.let { note ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(note.title, fontWeight = FontWeight.SemiBold)
                        Text(note.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (availablePanes.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                availablePanes.forEach { pane ->
                    FilterChip(
                        selected = selectedPane == pane,
                        onClick = { selectedPane = pane },
                        label = { Text(pane.title) }
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val panelWidth = if (maxWidth > 820.dp) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth()
            Surface(
                modifier = panelWidth,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedPane) {
                        SharedSettingsReaderPane.TEXT -> {
                            SharedReaderFormatControls(
                                settings = settings,
                                toolbarPreferences = ReaderToolbarPreferences(),
                                onPickCustomFont = onPickCustomFont,
                                customFonts = customFonts,
                                onReaderAction = { action ->
                                    if (action is ReaderAction.SettingsChanged) onSettingsChange(action.settings)
                                }
                            )
                        }

                        SharedSettingsReaderPane.THEME -> {
                            SharedReaderThemeControls(
                                settings = settings,
                                customTextureIds = readerCustomTextureIds,
                                onImportTexture = onImportReaderTexture,
                                onSettingsChange = onSettingsChange
                            )
                        }

                        SharedSettingsReaderPane.VISUAL -> {
                            SharedReaderVisualOptionsControls(
                                settings = settings,
                                onReaderAction = { action ->
                                    if (action is ReaderAction.SettingsChanged) onSettingsChange(action.settings)
                                }
                            )
                        }

                        SharedSettingsReaderPane.PDF -> {
                            Text("PDF and comic defaults", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "These defaults cover reader-level appearance where the platform supports it. PDF-specific OCR, annotation, and tool options remain available in the PDF reader too.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SharedReaderThemeControls(
                                settings = settings,
                                builtInThemes = BuiltInPdfReaderThemes,
                                customTextureIds = readerCustomTextureIds,
                                onImportTexture = onImportReaderTexture,
                                onSettingsChange = onSettingsChange
                            )
                            Button(onClick = { onAction(SharedSettingsAction.PDF_READER_DEFAULTS) }) {
                                Text("Open PDF-specific settings")
                            }
                        }

                        SharedSettingsReaderPane.TOOLBAR -> {
                            if (toolbarPreferences == null) {
                                Text("Reader toolbar defaults are managed from the reader on this platform.")
                            } else {
                                SharedReaderToolbarControls(
                                    toolbarPreferences = toolbarPreferences,
                                    onToolbarPreferencesChange = onToolbarPreferencesChange
                                )
                            }
                        }

                        SharedSettingsReaderPane.TTS -> {
                            SharedReaderTtsReplacementControls(
                                preferences = ttsReplacementPreferences,
                                bookId = "global",
                                onPreferencesChange = onTtsReplacementPreferencesChange,
                                allowBookScope = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSettingsRow(
    item: SharedSettingsItemModel,
    onAction: (SharedSettingsAction) -> Unit
) {
    val contentColor = if (item.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        enabled = item.enabled,
        onClick = { onAction(item.action) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                item.action.iconForSettings(),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (item.kind == SharedSettingsItemKind.DESTRUCTIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(
                    item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                )
            }
            when (item.kind) {
                SharedSettingsItemKind.TOGGLE -> {
                    Switch(
                        checked = item.checked == true,
                        enabled = item.enabled,
                        onCheckedChange = { onAction(item.action) }
                    )
                }

                SharedSettingsItemKind.INFO -> Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                SharedSettingsItemKind.DESTRUCTIVE,
                SharedSettingsItemKind.NAVIGATION,
                SharedSettingsItemKind.CONTROL -> {
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

private fun SharedSettingsAction.iconForSettings(): ImageVector {
    return when (this) {
        SharedSettingsAction.TEXT_READER_DEFAULTS,
        SharedSettingsAction.TTS_REPLACEMENTS,
        SharedSettingsAction.TTS_SETTINGS,
        SharedSettingsAction.HIDE_READER_AI -> Icons.Default.TextFields
        SharedSettingsAction.PDF_READER_DEFAULTS,
        SharedSettingsAction.READER_TOOLBAR,
        SharedSettingsAction.APP_THEME -> Icons.Default.Palette
        SharedSettingsAction.CUSTOM_FONTS -> Icons.Default.TextFields
        SharedSettingsAction.SIGN_IN,
        SharedSettingsAction.SIGN_OUT,
        SharedSettingsAction.CLOUD_SYNC -> Icons.Default.Cloud
        SharedSettingsAction.FOLDER_SYNC -> Icons.Default.Folder
        SharedSettingsAction.CLEAR_BOOK_CACHE,
        SharedSettingsAction.CLEAR_REFLOW_CACHE -> Icons.Default.Delete
        SharedSettingsAction.HELP_FEEDBACK -> Icons.Default.Feedback
        SharedSettingsAction.SUPPORT -> Icons.Default.Favorite
        SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
        SharedSettingsAction.ABOUT -> Icons.Default.Info
        SharedSettingsAction.EXPORT_LOGS,
        SharedSettingsAction.DEBUG_ACTIONS,
        SharedSettingsAction.DEVICE_MANAGEMENT,
        SharedSettingsAction.AI_SETTINGS,
        SharedSettingsAction.LANGUAGE,
        SharedSettingsAction.TABS_TOGGLE,
        SharedSettingsAction.RECENT_LIMIT,
        SharedSettingsAction.STRICT_FILE_FILTER,
        SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
        SharedSettingsAction.SCREEN_CAPTURE_PROTECTION -> Icons.Default.Settings
    }
}
