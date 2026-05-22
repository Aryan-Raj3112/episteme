package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.currentSharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.updateCurrentSharedPdfTextStyle
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.ui.SharedPdfAnnotationToolDock
import com.aryan.reader.shared.ui.SharedPdfHighlighterPaletteEditor
import com.aryan.reader.shared.ui.SharedPdfTextAnnotationDock
import com.aryan.reader.shared.ui.SharedReaderThemeControls
import com.aryan.reader.shared.ui.SharedReaderVerticalScrollbar
import com.aryan.reader.shared.ui.readerString
import com.aryan.reader.shared.ui.sharedAcceleratedLazyWheelScroll

@Composable
internal fun DesktopPdfInspectorPanel(
    document: DesktopPdfDocument,
    displayMode: PdfDisplayMode,
    pdfReaderSettings: ReaderSettings,
    customTextureIds: List<String>,
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    isTextSelectionMode: Boolean,
    selectedTool: PdfInkTool,
    isRichTextMode: Boolean,
    selectedColor: Int,
    strokeWidth: Float,
    pdfHighlighterColors: List<Int>,
    pdfHighlighterPalette: SharedPdfHighlighterPalette,
    isHighlighterSnapEnabled: Boolean,
    effectiveTextStyleConfig: SharedPdfTextStyleConfig,
    richTextController: SharedPdfRichTextController,
    pdfExtrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsFeatureAvailable: Boolean,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onDisplayModeSelected: (PdfDisplayMode) -> Unit,
    onSelectPanMode: () -> Unit,
    onTextSelectionModeToggle: () -> Unit,
    onRichTextModeToggle: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndoPage: () -> Unit,
    onClearPage: () -> Unit,
    onHighlighterSnapChange: (Boolean) -> Unit,
    onHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit,
    onTextStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    var selectedPdfInspectorTab by remember(document.handleId) { mutableStateOf(DesktopPdfInspectorTab.VISUAL) }
    val appearanceInspectorListState = rememberLazyListState()
    val visualInspectorListState = rememberLazyListState()
    val markupInspectorListState = rememberLazyListState()
    val ttsInspectorListState = rememberLazyListState()
    val pdfInspectorListState = when (selectedPdfInspectorTab) {
        DesktopPdfInspectorTab.APPEARANCE -> appearanceInspectorListState
        DesktopPdfInspectorTab.VISUAL -> visualInspectorListState
        DesktopPdfInspectorTab.MARKUP -> markupInspectorListState
        DesktopPdfInspectorTab.TTS -> ttsInspectorListState
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopPdfInspectorHeader(
                selectedTab = selectedPdfInspectorTab,
                onTabSelected = { selectedPdfInspectorTab = it }
            )
            HorizontalDivider()
            DesktopPdfInspectorContent(
                document = document,
                displayMode = displayMode,
                pdfReaderSettings = pdfReaderSettings,
                customTextureIds = customTextureIds,
                onImportTexture = onImportTexture,
                onReaderSettingsChange = onReaderSettingsChange,
                isTextSelectionMode = isTextSelectionMode,
                selectedTool = selectedTool,
                isRichTextMode = isRichTextMode,
                selectedColor = selectedColor,
                strokeWidth = strokeWidth,
                pdfHighlighterColors = pdfHighlighterColors,
                pdfHighlighterPalette = pdfHighlighterPalette,
                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                effectiveTextStyleConfig = effectiveTextStyleConfig,
                richTextController = richTextController,
                pdfExtrasState = pdfExtrasState,
                aiByokSettings = aiByokSettings,
                cloudTtsFeatureAvailable = cloudTtsFeatureAvailable,
                ttsReplacementPreferences = ttsReplacementPreferences,
                selectedTab = selectedPdfInspectorTab,
                listState = pdfInspectorListState,
                onDisplayModeSelected = onDisplayModeSelected,
                onSelectPanMode = onSelectPanMode,
                onTextSelectionModeToggle = onTextSelectionModeToggle,
                onRichTextModeToggle = onRichTextModeToggle,
                onToolSelected = onToolSelected,
                onColorSelected = onColorSelected,
                onStrokeWidthChange = onStrokeWidthChange,
                onUndoPage = onUndoPage,
                onClearPage = onClearPage,
                onHighlighterSnapChange = onHighlighterSnapChange,
                onHighlighterPaletteChange = onHighlighterPaletteChange,
                onTextStyleChange = onTextStyleChange,
                onCloudTtsClearCache = onCloudTtsClearCache,
                onCloudTtsVoiceChange = onCloudTtsVoiceChange,
                onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
            )
        }
    }
}

@Composable
private fun DesktopPdfInspectorHeader(
    selectedTab: DesktopPdfInspectorTab,
    onTabSelected: (DesktopPdfInspectorTab) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        DesktopPdfInspectorTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        tab.icon(),
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        tab.localizedTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.DesktopPdfInspectorContent(
    document: DesktopPdfDocument,
    displayMode: PdfDisplayMode,
    pdfReaderSettings: ReaderSettings,
    customTextureIds: List<String>,
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    isTextSelectionMode: Boolean,
    selectedTool: PdfInkTool,
    isRichTextMode: Boolean,
    selectedColor: Int,
    strokeWidth: Float,
    pdfHighlighterColors: List<Int>,
    pdfHighlighterPalette: SharedPdfHighlighterPalette,
    isHighlighterSnapEnabled: Boolean,
    effectiveTextStyleConfig: SharedPdfTextStyleConfig,
    richTextController: SharedPdfRichTextController,
    pdfExtrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    cloudTtsFeatureAvailable: Boolean,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    selectedTab: DesktopPdfInspectorTab,
    listState: LazyListState,
    onDisplayModeSelected: (PdfDisplayMode) -> Unit,
    onSelectPanMode: () -> Unit,
    onTextSelectionModeToggle: () -> Unit,
    onRichTextModeToggle: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndoPage: () -> Unit,
    onClearPage: () -> Unit,
    onHighlighterSnapChange: (Boolean) -> Unit,
    onHighlighterPaletteChange: (SharedPdfHighlighterPalette) -> Unit,
    onTextStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .sharedAcceleratedLazyWheelScroll(listState, multiplier = 2.8f)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                DesktopPdfInspectorTab.APPEARANCE -> {
                    item {
                        DesktopPdfInspectorSection(readerString("app_theme_appearance", "Appearance")) {
                            SharedReaderThemeControls(
                                settings = pdfReaderSettings,
                                builtInThemes = BuiltInPdfReaderThemes,
                                customTextureIds = customTextureIds,
                                onImportTexture = onImportTexture,
                                texturePreviewContent = { textureId, previewModifier ->
                                    DesktopReaderTexturePreview(textureId = textureId, modifier = previewModifier)
                                },
                                onSettingsChange = onReaderSettingsChange
                            )
                        }
                    }
                }
                DesktopPdfInspectorTab.VISUAL -> {
                    item {
                        DesktopPdfInspectorSection(readerString("visual_options_title", "Visual options")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = displayMode == PdfDisplayMode.PAGINATION,
                                    onClick = { onDisplayModeSelected(PdfDisplayMode.PAGINATION) },
                                    label = { Text(readerString("desktop_page", "Page")) }
                                )
                                FilterChip(
                                    selected = displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                                    onClick = { onDisplayModeSelected(PdfDisplayMode.VERTICAL_SCROLL) },
                                    label = { Text(readerString("desktop_scroll", "Scroll")) }
                                )
                            }
                            if (displayMode == PdfDisplayMode.PAGINATION) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(
                                        selected = pdfReaderSettings.pageSpreadMode == ReaderPageSpreadMode.SINGLE,
                                        onClick = {
                                            onReaderSettingsChange(
                                                pdfReaderSettings.copy(pageSpreadMode = ReaderPageSpreadMode.SINGLE)
                                            )
                                        },
                                        label = { Text(readerString("visual_options_pdf_spread_single", "Single page")) }
                                    )
                                    FilterChip(
                                        selected = pdfReaderSettings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE,
                                        onClick = {
                                            onReaderSettingsChange(
                                                pdfReaderSettings.copy(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)
                                            )
                                        },
                                        label = { Text(readerString("visual_options_pdf_spread_two", "Two pages")) }
                                    )
                                }
                                if (pdfReaderSettings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE) {
                                    DesktopPdfVisualOptionSwitch(
                                        title = readerString("visual_options_pdf_first_page_alone", "First page alone"),
                                        description = readerString(
                                            "visual_options_pdf_first_page_alone_desc",
                                            "Starts facing-page spreads after the cover page."
                                        ),
                                        checked = pdfReaderSettings.pdfFirstPageStandaloneInSpread,
                                        onCheckedChange = { enabled ->
                                            onReaderSettingsChange(
                                                pdfReaderSettings.copy(pdfFirstPageStandaloneInSpread = enabled)
                                            )
                                        }
                                    )
                                }
                            }
                            DesktopPdfVisualOptionSwitch(
                                title = readerString("visual_options_remove_page_gap", "Remove gap between pages"),
                                description = readerString(
                                    "desktop_remove_gap_between_pages_desc",
                                    "Applies to vertical reading mode."
                                ),
                                checked = !pdfReaderSettings.pdfVerticalPageGapVisible,
                                onCheckedChange = { removeGap ->
                                    onReaderSettingsChange(
                                        pdfReaderSettings.copy(pdfVerticalPageGapVisible = !removeGap)
                                    )
                                }
                            )
                            DesktopPdfVisualOptionSwitch(
                                title = readerString("visual_options_hide_page_number_overlay", "Hide page number overlay"),
                                description = readerString(
                                    "visual_options_hide_page_number_overlay_desc",
                                    "Removes the small page count label from each page."
                                ),
                                checked = !pdfReaderSettings.pdfPageNumberOverlayVisible,
                                onCheckedChange = { hideOverlay ->
                                    onReaderSettingsChange(
                                        pdfReaderSettings.copy(pdfPageNumberOverlayVisible = !hideOverlay)
                                    )
                                }
                            )
                        }
                    }
                }
                DesktopPdfInspectorTab.MARKUP -> {
                    item {
                        DesktopPdfInspectorSection(readerString("desktop_interaction", "Interaction")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = !isTextSelectionMode && selectedTool == PdfInkTool.NONE && !isRichTextMode,
                                    onClick = onSelectPanMode,
                                    label = { Text(readerString("desktop_pan", "Pan")) }
                                )
                                FilterChip(
                                    selected = isTextSelectionMode,
                                    onClick = onTextSelectionModeToggle,
                                    label = { Text(readerString("desktop_select_text", "Select text")) }
                                )
                                FilterChip(
                                    selected = isRichTextMode,
                                    onClick = onRichTextModeToggle,
                                    label = { Text(readerString("desktop_document_text", "Document text")) }
                                )
                            }
                        }
                    }
                    item {
                        DesktopPdfInspectorSection(readerString("desktop_annotation_tools", "Annotation tools")) {
                            SharedPdfAnnotationToolDock(
                                selectedTool = selectedTool,
                                selectedColor = selectedColor,
                                strokeWidth = strokeWidth,
                                tools = DesktopPdfAnnotationTools,
                                highlighterPalette = pdfHighlighterColors,
                                onToolSelected = onToolSelected,
                                onColorSelected = onColorSelected,
                                onStrokeWidthChange = onStrokeWidthChange,
                                onUndo = onUndoPage,
                                onClearPage = onClearPage,
                                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                onHighlighterSnapChange = onHighlighterSnapChange
                            )
                        }
                    }
                    item {
                        DesktopPdfInspectorSection(readerString("desktop_highlighter_palette", "Highlighter palette")) {
                            SharedPdfHighlighterPaletteEditor(
                                palette = pdfHighlighterPalette,
                                onPaletteChange = onHighlighterPaletteChange
                            )
                        }
                    }
                    if (isRichTextMode || selectedTool == PdfInkTool.TEXT) {
                        item {
                            DesktopPdfInspectorSection(readerString("desktop_text_style", "Text style")) {
                                SharedPdfTextAnnotationDock(
                                    style = if (isRichTextMode) {
                                        richTextController.currentSharedPdfTextStyleConfig()
                                    } else {
                                        effectiveTextStyleConfig
                                    },
                                    onStyleChange = { style ->
                                        if (isRichTextMode) {
                                            richTextController.updateCurrentSharedPdfTextStyle(style)
                                        } else {
                                            onTextStyleChange(style)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                DesktopPdfInspectorTab.TTS -> {
                    item {
                        DesktopPdfTtsPanel(
                            extrasState = pdfExtrasState,
                            aiByokSettings = aiByokSettings,
                            cloudTtsFeatureAvailable = cloudTtsFeatureAvailable,
                            onCloudTtsClearCache = onCloudTtsClearCache,
                            onCloudTtsVoiceChange = onCloudTtsVoiceChange,
                            ttsReplacementPreferences = ttsReplacementPreferences,
                            ttsReplacementBookId = document.path,
                            onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange
                        )
                    }
                }
            }
        }
        SharedReaderVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun DesktopPdfInspectorTab.localizedTitle(): String {
    return when (this) {
        DesktopPdfInspectorTab.APPEARANCE -> readerString("app_theme_appearance", "Appearance")
        DesktopPdfInspectorTab.VISUAL -> readerString("visual_options_title", "Visual")
        DesktopPdfInspectorTab.MARKUP -> readerString("desktop_markup", "Markup")
        DesktopPdfInspectorTab.TTS -> readerString("menu_tts_settings", "TTS")
    }
}

private fun DesktopPdfInspectorTab.icon(): ImageVector {
    return when (this) {
        DesktopPdfInspectorTab.APPEARANCE -> Icons.Default.Palette
        DesktopPdfInspectorTab.VISUAL -> Icons.Default.Tune
        DesktopPdfInspectorTab.MARKUP -> Icons.Default.Edit
        DesktopPdfInspectorTab.TTS -> Icons.AutoMirrored.Filled.VolumeUp
    }
}
