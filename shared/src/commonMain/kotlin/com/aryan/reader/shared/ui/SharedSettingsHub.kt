package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsCategoryModel
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubModel
import com.aryan.reader.shared.SharedSettingsItemKind
import com.aryan.reader.shared.SharedSettingsItemModel
import com.aryan.reader.shared.SharedSettingsPageKind
import com.aryan.reader.shared.SharedSettingsPageModel
import com.aryan.reader.shared.SharedSettingsSearchResult
import com.aryan.reader.shared.parentDestination
import com.aryan.reader.shared.reader.ReaderSettings

@Composable
fun SharedSettingsHub(
    model: SharedSettingsHubModel,
    query: String,
    onQueryChange: (String) -> Unit,
    readerDefaultSettings: ReaderSettings,
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit,
    pdfReaderDefaultSettings: ReaderSettings = readerDefaultSettings,
    onPdfReaderDefaultSettingsChange: (ReaderSettings) -> Unit = onReaderDefaultSettingsChange,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onAction: (SharedSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    readerToolbarPreferences: ReaderToolbarPreferences? = null,
    onReaderToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onPickCustomFont: (() -> String?)? = null,
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    readerCustomTextureIds: List<String> = emptyList(),
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    showTopBar: Boolean = true,
    onBack: (() -> Unit)? = null,
    destination: SharedSettingsDestination = SharedSettingsDestination.ROOT,
    onDestinationChange: (SharedSettingsDestination) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val page = remember(model, destination) { model.page(destination) }
    val searchResults = remember(model, query) { model.searchResults(query) }

    fun navigateTo(next: SharedSettingsDestination) {
        onQueryChange("")
        onDestinationChange(next)
    }

    fun navigateUp() {
        if (query.isNotBlank()) {
            onQueryChange("")
            return
        }
        val parent = destination.parentDestination()
        if (parent != null) {
            onDestinationChange(parent)
        } else {
            onBack?.invoke()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        val contentWidth = if (maxWidth >= 960.dp) Modifier.width(860.dp) else Modifier.fillMaxWidth()
        Column(
            modifier = contentWidth
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showTopBar) {
                SharedSettingsHeader(
                    page = page,
                    canNavigateUp = query.isNotBlank() || destination != SharedSettingsDestination.ROOT || onBack != null,
                    onNavigateUp = ::navigateUp
                )
            }

            SharedStableOutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(readerString("desktop_search_settings", "Search settings")) }
            )

            when {
                query.isNotBlank() -> SharedSettingsSearchResults(
                    results = searchResults,
                    onNavigate = ::navigateTo,
                    onAction = onAction,
                    modifier = Modifier.weight(1f)
                )
                page.kind == SharedSettingsPageKind.ROOT -> SharedSettingsCategoryList(
                    categories = page.categories,
                    onNavigate = ::navigateTo,
                    modifier = Modifier.weight(1f)
                )
                page.kind == SharedSettingsPageKind.CATEGORY -> SharedSettingsItemList(
                    page = page,
                    onNavigate = ::navigateTo,
                    onAction = onAction,
                    modifier = Modifier.weight(1f)
                )
                else -> SharedSettingsDetailPage(
                    page = page,
                    settings = readerDefaultSettings,
                    onSettingsChange = onReaderDefaultSettingsChange,
                    pdfSettings = pdfReaderDefaultSettings,
                    onPdfSettingsChange = onPdfReaderDefaultSettingsChange,
                    toolbarPreferences = readerToolbarPreferences,
                    onToolbarPreferencesChange = onReaderToolbarPreferencesChange,
                    ttsReplacementPreferences = ttsReplacementPreferences,
                    onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
                    customFonts = customFonts,
                    onPickCustomFont = onPickCustomFont,
                    customReaderThemes = customReaderThemes,
                    onCustomReaderThemesChange = onCustomReaderThemesChange,
                    readerCustomTextureIds = readerCustomTextureIds,
                    onImportReaderTexture = onImportReaderTexture,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SharedSettingsHeader(
    page: SharedSettingsPageModel,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canNavigateUp) {
            TextButton(onClick = onNavigateUp) {
                Text(readerString("action_back", "Back"))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                page.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SharedSettingsCategoryList(
    categories: List<SharedSettingsCategoryModel>,
    onNavigate: (SharedSettingsDestination) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { category ->
            item(key = category.destination.name) {
                SharedSettingsCategoryRow(category = category, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun SharedSettingsCategoryRow(
    category: SharedSettingsCategoryModel,
    onNavigate: (SharedSettingsDestination) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        onClick = { onNavigate(category.destination) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                category.destination.iconForSettingsDestination(),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    category.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (category.itemCount == 1) "1 setting" else "${category.itemCount} settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SharedSettingsItemList(
    page: SharedSettingsPageModel,
    onNavigate: (SharedSettingsDestination) -> Unit,
    onAction: (SharedSettingsAction) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = page.destination.name) {
            SharedSettingsGroup {
                page.items.forEachIndexed { index, item ->
                    SharedSettingsRow(
                        item = item,
                        onNavigate = onNavigate,
                        onAction = onAction
                    )
                    if (index != page.items.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSettingsSearchResults(
    results: List<SharedSettingsSearchResult>,
    onNavigate: (SharedSettingsDestination) -> Unit,
    onAction: (SharedSettingsAction) -> Unit,
    modifier: Modifier
) {
    if (results.isEmpty()) {
        SharedSettingsEmptySearch(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SharedSettingsGroup {
                results.forEachIndexed { index, result ->
                    SharedSettingsSearchResultRow(
                        result = result,
                        onNavigate = onNavigate,
                        onAction = onAction
                    )
                    if (index != results.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSettingsEmptySearch(modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
            Text(readerString("desktop_no_settings_found", "No settings found"), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SharedSettingsSearchResultRow(
    result: SharedSettingsSearchResult,
    onNavigate: (SharedSettingsDestination) -> Unit,
    onAction: (SharedSettingsAction) -> Unit
) {
    val contentColor = if (result.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        enabled = result.enabled,
        onClick = {
            result.destination?.let(onNavigate) ?: result.action?.let(onAction)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                result.action?.iconForSettings() ?: result.destination?.iconForSettingsDestination() ?: Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (result.kind == SharedSettingsItemKind.DESTRUCTIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.title, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(
                    result.breadcrumb,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    result.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (result.kind) {
                SharedSettingsItemKind.TOGGLE -> {
                    Switch(
                        checked = result.checked == true,
                        enabled = result.enabled,
                        onCheckedChange = { result.action?.let(onAction) }
                    )
                }
                else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SharedSettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
private fun SharedSettingsRow(
    item: SharedSettingsItemModel,
    onNavigate: (SharedSettingsDestination) -> Unit,
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
        onClick = {
            item.destination?.let(onNavigate) ?: onAction(item.action)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
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
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                SharedSettingsItemKind.CONTROL -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SharedSettingsDetailPage(
    page: SharedSettingsPageModel,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    pdfSettings: ReaderSettings,
    onPdfSettingsChange: (ReaderSettings) -> Unit,
    toolbarPreferences: ReaderToolbarPreferences?,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    customFonts: List<CustomFontItem>,
    onPickCustomFont: (() -> String?)?,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        page.localOverrideNote?.let { note ->
            SharedSettingsLocalOverrideNote(note)
        }

        SharedSettingsDetailSurface {
            when (page.destination) {
                SharedSettingsDestination.EPUB_FORMAT -> {
                    SharedReaderFormatControls(
                        settings = settings,
                        onPickCustomFont = onPickCustomFont,
                        customFonts = customFonts,
                        onReaderAction = { action ->
                            if (action is ReaderAction.SettingsChanged) onSettingsChange(action.settings)
                        }
                    )
                }
                SharedSettingsDestination.EPUB_THEME_TEXTURE -> {
                    SharedReaderThemeControls(
                        settings = settings,
                        customThemes = customReaderThemes,
                        onCustomThemesChange = onCustomReaderThemesChange,
                        customTextureIds = readerCustomTextureIds,
                        onImportTexture = onImportReaderTexture,
                        onSettingsChange = onSettingsChange
                    )
                }
                SharedSettingsDestination.EPUB_VISUAL_DEFAULTS -> {
                    SharedReaderVisualOptionsControls(
                        settings = settings,
                        onReaderAction = { action ->
                            if (action is ReaderAction.SettingsChanged) onSettingsChange(action.settings)
                        }
                    )
                }
                SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(readerString("desktop_fixed_layout_appearance", "Fixed-layout appearance"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            readerString(
                                "desktop_pdf_appearance_defaults_desc",
                                "These defaults apply where the platform supports shared PDF appearance. Per-book PDF overrides stay in the PDF reader."
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SharedReaderThemeControls(
                            settings = pdfSettings,
                            builtInThemes = BuiltInPdfReaderThemes,
                            customThemes = customReaderThemes,
                            onCustomThemesChange = onCustomReaderThemesChange,
                            customTextureIds = readerCustomTextureIds,
                            onImportTexture = onImportReaderTexture,
                            onSettingsChange = onPdfSettingsChange
                        )
                        HorizontalDivider()
                        Text(readerString("visual_options_title", "Visual options"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SharedPdfVisualOptionDefaultsSwitch(
                            title = readerString("menu_right_to_left_pagination", "Paginated (right-to-left)"),
                            summary = readerString("visual_options_right_to_left_pagination_desc", "Uses right-to-left page order when PDF pagination mode is active."),
                            checked = pdfSettings.rightToLeftPagination,
                            onCheckedChange = { enabled ->
                                onPdfSettingsChange(pdfSettings.copy(rightToLeftPagination = enabled))
                            }
                        )
                        SharedPdfVisualOptionDefaultsSwitch(
                            title = readerString("visual_options_remove_page_gap", "Remove gap between pages"),
                            summary = readerString("desktop_remove_gap_between_pages_desc", "Applies to vertical reading and two-page spreads."),
                            checked = !pdfSettings.pdfVerticalPageGapVisible,
                            onCheckedChange = { removeGap ->
                                onPdfSettingsChange(pdfSettings.copy(pdfVerticalPageGapVisible = !removeGap))
                            }
                        )
                        SharedPdfVisualOptionDefaultsSwitch(
                            title = readerString("visual_options_hide_page_number_overlay", "Hide page number overlay"),
                            summary = readerString("visual_options_hide_page_number_overlay_desc", "Removes the small page count label from each page."),
                            checked = !pdfSettings.pdfPageNumberOverlayVisible,
                            onCheckedChange = { hideOverlay ->
                                onPdfSettingsChange(pdfSettings.copy(pdfPageNumberOverlayVisible = !hideOverlay))
                            }
                        )
                    }
                }
                SharedSettingsDestination.PDF_READER_TOOLS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(readerString("desktop_reader_managed_pdf_tools", "Reader-managed PDF tools"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            readerString(
                                "desktop_reader_managed_pdf_tools_desc",
                                "Auto-scroll, OCR, annotation defaults, and PDF-only tool visibility are managed inside the active PDF reader."
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SharedSettingsDestination.READER_TOOLBAR_DEFAULTS -> {
                    if (toolbarPreferences == null) {
                        Text(readerString("desktop_reader_toolbar_managed_in_reader", "Reader toolbar defaults are managed from the reader on this platform."))
                    } else {
                        SharedReaderToolbarControls(
                            toolbarPreferences = toolbarPreferences,
                            onToolbarPreferencesChange = onToolbarPreferencesChange
                        )
                    }
                }
                SharedSettingsDestination.EPUB_TTS_REPLACEMENTS,
                SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> {
                    SharedReaderTtsReplacementControls(
                        preferences = ttsReplacementPreferences,
                        bookId = "global",
                        onPreferencesChange = onTtsReplacementPreferencesChange,
                        allowBookScope = false
                    )
                }
                else -> Text(page.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedSettingsDetailSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SharedSettingsLocalOverrideNote(note: SharedSettingsItemModel) {
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

@Composable
private fun SharedPdfVisualOptionDefaultsSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun SharedSettingsDestination.iconForSettingsDestination(): ImageVector {
    return when (this) {
        SharedSettingsDestination.EPUB_TEXT,
        SharedSettingsDestination.EPUB_FORMAT,
        SharedSettingsDestination.EPUB_TTS_REPLACEMENTS,
        SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> Icons.Default.TextFields
        SharedSettingsDestination.PDF_COMICS,
        SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS,
        SharedSettingsDestination.PDF_READER_TOOLS,
        SharedSettingsDestination.EPUB_THEME_TEXTURE,
        SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
        SharedSettingsDestination.READER_TOOLBAR_DEFAULTS,
        SharedSettingsDestination.THEME_APPEARANCE -> Icons.Default.Palette
        SharedSettingsDestination.TTS_AI -> Icons.Default.Settings
        SharedSettingsDestination.LIBRARY_SYNC_STORAGE -> Icons.Default.Folder
        SharedSettingsDestination.SYNC_ACCOUNTS -> Icons.Default.Cloud
        SharedSettingsDestination.EXTRA -> Icons.Default.Settings
        SharedSettingsDestination.HELP_ABOUT -> Icons.Default.Info
        SharedSettingsDestination.ROOT -> Icons.Default.Settings
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
        SharedSettingsAction.CLEAR_REFLOW_CACHE,
        SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> Icons.Default.Delete
        SharedSettingsAction.HELP_FEEDBACK -> Icons.Default.Feedback
        SharedSettingsAction.SUPPORT -> Icons.Default.Favorite
        SharedSettingsAction.LOCAL_OVERRIDE_NOTE,
        SharedSettingsAction.ABOUT -> Icons.Default.Info
        SharedSettingsAction.EXPORT_LOGS,
        SharedSettingsAction.DEBUG_ACTIONS,
        SharedSettingsAction.TEST_PANEL_DETECTION,
        SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION,
        SharedSettingsAction.DEVICE_MANAGEMENT,
        SharedSettingsAction.AI_SETTINGS,
        SharedSettingsAction.LANGUAGE,
        SharedSettingsAction.TABS_TOGGLE,
        SharedSettingsAction.RECENT_LIMIT,
        SharedSettingsAction.STRICT_FILE_FILTER,
        SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME,
        SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR,
        SharedSettingsAction.SCREEN_CAPTURE_PROTECTION -> Icons.Default.Settings
    }
}
