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
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.currentSharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.updateCurrentSharedPdfTextStyle
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import com.aryan.reader.shared.ui.SharedPdfAnnotationToolDock
import com.aryan.reader.shared.ui.SharedPdfHighlighterPaletteEditor
import com.aryan.reader.shared.ui.SharedPdfTextAnnotationDock
import com.aryan.reader.shared.ui.SharedReaderThemeControls
import com.aryan.reader.shared.ui.SharedReaderVerticalScrollbar
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
    recapText: () -> String,
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
    onAiAction: (ReaderAiFeature, String) -> Unit,
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
                recapText = recapText,
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
                onAiAction = onAiAction,
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
        Text("PDF tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            tab.title,
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
    recapText: () -> String,
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
    onAiAction: (ReaderAiFeature, String) -> Unit,
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
                        DesktopPdfInspectorSection("Reading") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = displayMode == PdfDisplayMode.PAGINATION,
                                    onClick = { onDisplayModeSelected(PdfDisplayMode.PAGINATION) },
                                    label = { Text("Page") }
                                )
                                FilterChip(
                                    selected = displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                                    onClick = { onDisplayModeSelected(PdfDisplayMode.VERTICAL_SCROLL) },
                                    label = { Text("Scroll") }
                                )
                            }
                        }
                    }
                    item {
                        DesktopPdfInspectorSection("Position") {
                            Text(
                                "Page ${pageIndex + 1} of ${document.pageCount}",
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
                        DesktopPdfInspectorSection("Appearance") {
                            SharedReaderThemeControls(
                                settings = pdfReaderSettings,
                                builtInThemes = BuiltInPdfReaderThemes,
                                customTextureIds = customTextureIds,
                                onImportTexture = onImportTexture,
                                onSettingsChange = onReaderSettingsChange
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "Visual options",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            DesktopPdfVisualOptionSwitch(
                                title = "Remove gap between pages",
                                description = "Applies to vertical reading mode.",
                                checked = !pdfReaderSettings.pdfVerticalPageGapVisible,
                                onCheckedChange = { removeGap ->
                                    onReaderSettingsChange(
                                        pdfReaderSettings.copy(pdfVerticalPageGapVisible = !removeGap)
                                    )
                                }
                            )
                            DesktopPdfVisualOptionSwitch(
                                title = "Hide page number overlay",
                                description = "Removes the small page count label from each page.",
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
                        DesktopPdfInspectorSection("Zoom") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onZoomOut) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                                }
                                Text(
                                    "${(zoomControlScale * 100).toInt()}%",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(onClick = onZoomIn) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
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
                        DesktopPdfInspectorSection("Interaction") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = !isTextSelectionMode && selectedTool == PdfInkTool.NONE && !isRichTextMode,
                                    onClick = onSelectPanMode,
                                    label = { Text("Pan") }
                                )
                                FilterChip(
                                    selected = isTextSelectionMode,
                                    onClick = onTextSelectionModeToggle,
                                    label = { Text("Select text") }
                                )
                                FilterChip(
                                    selected = isRichTextMode,
                                    onClick = onRichTextModeToggle,
                                    label = { Text("Document text") }
                                )
                            }
                        }
                    }
                    item {
                        DesktopPdfInspectorSection("Annotation tools") {
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
                        DesktopPdfInspectorSection("Highlighter palette") {
                            SharedPdfHighlighterPaletteEditor(
                                palette = pdfHighlighterPalette,
                                onPaletteChange = onHighlighterPaletteChange
                            )
                        }
                    }
                    if (isRichTextMode || selectedTool == PdfInkTool.TEXT) {
                        item {
                            DesktopPdfInspectorSection("Text style") {
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
                            recapText = recapText(),
                            extrasState = pdfExtrasState,
                            aiByokSettings = aiByokSettings,
                            externalLookupAvailable = externalLookupAvailable,
                            cloudTtsFeatureAvailable = cloudTtsFeatureAvailable,
                            onExternalLookup = onExternalLookup,
                            onAiAction = onAiAction,
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
