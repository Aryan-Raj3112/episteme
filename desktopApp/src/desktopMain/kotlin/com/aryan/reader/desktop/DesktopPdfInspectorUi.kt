package com.aryan.reader.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.currentSharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.updateCurrentSharedPdfTextStyle
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.ui.ReaderMinimalSlider
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
    pageIndex: Int,
    displayMode: PdfDisplayMode,
    pdfReaderSettings: ReaderSettings,
    customTextureIds: List<String>,
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    zoomControlScale: Float,
    zoomSpec: PdfZoomSpec,
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
    externalLookupAvailable: Boolean,
    cloudTtsFeatureAvailable: Boolean,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    pageText: () -> String,
    onDisplayModeSelected: (PdfDisplayMode) -> Unit,
    onPageScrub: (Float) -> Unit,
    onPageScrubFinished: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomChange: (Float) -> Unit,
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
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onOpenAiHub: (() -> Unit)? = null,
    onCloudTtsStart: (ReaderTtsReadScope) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    var selectedPdfInspectorTab by remember(document.handleId) { mutableStateOf(DesktopPdfInspectorTab.VIEW) }
    val viewInspectorListState = rememberLazyListState()
    val markupInspectorListState = rememberLazyListState()
    val assistInspectorListState = rememberLazyListState()
    val pdfInspectorListState = when (selectedPdfInspectorTab) {
        DesktopPdfInspectorTab.VIEW -> viewInspectorListState
        DesktopPdfInspectorTab.MARKUP -> markupInspectorListState
        DesktopPdfInspectorTab.ASSIST -> assistInspectorListState
    }

    Surface(
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopPdfInspectorHeader(
                selectedTab = selectedPdfInspectorTab,
                onTabSelected = { selectedPdfInspectorTab = it }
            )
            HorizontalDivider()
            DesktopPdfInspectorContent(
                document = document,
                pageIndex = pageIndex,
                displayMode = displayMode,
                pdfReaderSettings = pdfReaderSettings,
                customTextureIds = customTextureIds,
                onImportTexture = onImportTexture,
                onReaderSettingsChange = onReaderSettingsChange,
                zoomControlScale = zoomControlScale,
                zoomSpec = zoomSpec,
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
                externalLookupAvailable = externalLookupAvailable,
                cloudTtsFeatureAvailable = cloudTtsFeatureAvailable,
                ttsReplacementPreferences = ttsReplacementPreferences,
                pageText = pageText,
                selectedTab = selectedPdfInspectorTab,
                listState = pdfInspectorListState,
                onDisplayModeSelected = onDisplayModeSelected,
                onPageScrub = onPageScrub,
                onPageScrubFinished = onPageScrubFinished,
                onZoomOut = onZoomOut,
                onZoomIn = onZoomIn,
                onZoomChange = onZoomChange,
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
                onExternalLookup = onExternalLookup,
                onOpenAiHub = onOpenAiHub,
                onCloudTtsStart = onCloudTtsStart,
                onCloudTtsPauseResume = onCloudTtsPauseResume,
                onCloudTtsStop = onCloudTtsStop,
                onCloudTtsClearCache = onCloudTtsClearCache,
                onAutoScrollChange = onAutoScrollChange,
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
    Column(
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(readerString("desktop_pdf_tools", "PDF tools"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            DesktopPdfInspectorTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
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
}

@Composable
private fun ColumnScope.DesktopPdfInspectorContent(
    document: DesktopPdfDocument,
    pageIndex: Int,
    displayMode: PdfDisplayMode,
    pdfReaderSettings: ReaderSettings,
    customTextureIds: List<String>,
    onImportTexture: ((ReaderSettings) -> ReaderSettings?)?,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    zoomControlScale: Float,
    zoomSpec: PdfZoomSpec,
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
    externalLookupAvailable: Boolean,
    cloudTtsFeatureAvailable: Boolean,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    pageText: () -> String,
    selectedTab: DesktopPdfInspectorTab,
    listState: LazyListState,
    onDisplayModeSelected: (PdfDisplayMode) -> Unit,
    onPageScrub: (Float) -> Unit,
    onPageScrubFinished: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomChange: (Float) -> Unit,
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
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onOpenAiHub: (() -> Unit)?,
    onCloudTtsStart: (ReaderTtsReadScope) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
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
                DesktopPdfInspectorTab.VIEW -> {
                    item {
                        DesktopPdfInspectorSection(readerString("label_reading", "Reading")) {
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
                        }
                    }
                    item {
                        DesktopPdfInspectorSection(readerString("visual_options_progress_bar_position", "Position")) {
                            val pageRange = if (displayMode == PdfDisplayMode.PAGINATION) {
                                PdfSpreadLayout.pageRangeLabel(pageIndex, document.pageCount, pdfReaderSettings)
                            } else {
                                "${pageIndex + 1}"
                            }
                            Text(
                                if ('-' in pageRange) {
                                    readerString("desktop_pdf_pages_of_count", "Pages %1\$s of %2\$d", pageRange, document.pageCount)
                                } else {
                                    readerString("desktop_pdf_page_of_count", "Page %1\$s of %2\$d", pageRange, document.pageCount)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (document.pageCount > 1) {
                                ReaderMinimalSlider(
                                    value = pageIndex.toFloat(),
                                    onValueChange = onPageScrub,
                                    onValueChangeFinished = onPageScrubFinished,
                                    valueRange = 0f..(document.pageCount - 1).toFloat()
                                )
                            }
                        }
                    }
                    item {
                        DesktopPdfInspectorSection(readerString("app_theme_appearance", "Appearance")) {
                            SharedReaderThemeControls(
                                settings = pdfReaderSettings,
                                builtInThemes = BuiltInPdfReaderThemes,
                                customTextureIds = customTextureIds,
                                onImportTexture = onImportTexture,
                                onSettingsChange = onReaderSettingsChange
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                readerString("visual_options_title", "Visual options"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
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
                    item {
                        DesktopPdfInspectorSection(readerString("desktop_zoom", "Zoom")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onZoomOut) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = readerString("desktop_zoom_out", "Zoom out"))
                                }
                                Text(
                                    "${(zoomControlScale * 100).toInt()}%",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(onClick = onZoomIn) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = readerString("desktop_zoom_in", "Zoom in"))
                                }
                            }
                            Slider(
                                value = zoomControlScale,
                                onValueChange = onZoomChange,
                                valueRange = zoomSpec.min..zoomSpec.max
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
                DesktopPdfInspectorTab.ASSIST -> {
                    item {
                        DesktopPdfExtrasPanel(
                            pageText = pageText(),
                            extrasState = pdfExtrasState,
                            aiByokSettings = aiByokSettings,
                            externalLookupAvailable = externalLookupAvailable,
                            cloudTtsFeatureAvailable = cloudTtsFeatureAvailable,
                            onExternalLookup = onExternalLookup,
                            onOpenAiHub = onOpenAiHub,
                            onCloudTtsStart = onCloudTtsStart,
                            onCloudTtsPauseResume = onCloudTtsPauseResume,
                            onCloudTtsStop = onCloudTtsStop,
                            onCloudTtsClearCache = onCloudTtsClearCache,
                            onAutoScrollChange = onAutoScrollChange,
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
        DesktopPdfInspectorTab.VIEW -> readerString("desktop_view", "View")
        DesktopPdfInspectorTab.MARKUP -> readerString("desktop_markup", "Markup")
        DesktopPdfInspectorTab.ASSIST -> readerString("desktop_assist", "Assist")
    }
}
