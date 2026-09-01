package com.aryan.reader.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfToolConfig
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextResizeHandle
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.movedBy
import com.aryan.reader.shared.pdf.resizedBy
import com.aryan.reader.shared.pdf.sharedPdfTextFontSizePx
import com.aryan.reader.shared.pdf.sharedPdfStrokeWidthRange
import com.aryan.reader.shared.pdf.withSharedPdfTextFontSize
import kotlin.math.roundToInt

val SharedPdfAnnotationDefaultTools: List<PdfInkTool> = listOf(
    PdfInkTool.PEN,
    PdfInkTool.FOUNTAIN_PEN,
    PdfInkTool.PENCIL,
    PdfInkTool.HIGHLIGHTER,
    PdfInkTool.HIGHLIGHTER_ROUND,
    PdfInkTool.TEXT,
    PdfInkTool.ERASER
)

private enum class SharedPdfAnnotationSettingsPanel {
    PEN,
    HIGHLIGHTER,
    ERASER
}

internal enum class SharedPdfInteractionDockItem {
    PAN,
    SELECT_TEXT,
    PEN,
    HIGHLIGHTER,
    TEXT_NOTE,
    ERASER,
    UNDO,
    REDO,
    CLEAR_PAGE
}

internal fun sharedPdfInteractionDockItems(
    tools: List<PdfInkTool> = SharedPdfAnnotationDefaultTools
): List<SharedPdfInteractionDockItem> = buildList {
    add(SharedPdfInteractionDockItem.PAN)
    add(SharedPdfInteractionDockItem.SELECT_TEXT)

    val availableTools = tools.toSet()
    if (availableTools.any(PdfInkTool::isDesktopPenTool)) {
        add(SharedPdfInteractionDockItem.PEN)
    }
    if (availableTools.any(PdfInkTool::isDesktopHighlighter)) {
        add(SharedPdfInteractionDockItem.HIGHLIGHTER)
    }
    if (PdfInkTool.TEXT in availableTools) {
        add(SharedPdfInteractionDockItem.TEXT_NOTE)
    }
    if (PdfInkTool.ERASER in availableTools) {
        add(SharedPdfInteractionDockItem.ERASER)
    }

    add(SharedPdfInteractionDockItem.UNDO)
    add(SharedPdfInteractionDockItem.REDO)
    add(SharedPdfInteractionDockItem.CLEAR_PAGE)
}

@Composable
fun SharedPdfInteractionDock(
    isTextSelectionMode: Boolean,
    isStylusOnlyMode: Boolean = false,
    onToggleStylusOnlyMode: (() -> Unit)? = null,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    tools: List<PdfInkTool> = SharedPdfAnnotationDefaultTools,
    toolConfigs: Map<PdfInkTool, PdfToolConfig> = emptyMap(),
    penPalette: List<Int> = SharedPdfAnnotationDefaults.penPalette,
    highlighterPalette: List<Int> = SharedPdfHighlighterPalette.defaultColors,
    lastActivePenTool: PdfInkTool = PdfInkTool.PEN,
    lastActiveHighlighterTool: PdfInkTool = PdfInkTool.HIGHLIGHTER,
    onPanSelected: () -> Unit,
    onTextSelectionSelected: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    modifier: Modifier = Modifier,
    allowExpandedSettings: Boolean = true,
    canUndo: Boolean = true,
    canRedo: Boolean = false,
    canClearPage: Boolean = true,
    isHighlighterSnapEnabled: Boolean = false,
    onHighlighterSnapChange: (Boolean) -> Unit = {},
    onHighlighterPaletteChange: (List<Int>) -> Unit = {},
    onPenPaletteChange: (List<Int>) -> Unit = {}
) {
    val availableTools = remember(tools) { tools.distinct() }
    val dockItems = remember(availableTools) { sharedPdfInteractionDockItems(availableTools) }
    val penTools = remember(availableTools) { availableTools.filter(PdfInkTool::isDesktopPenTool) }
    val highlighterTools = remember(availableTools) { availableTools.filter(PdfInkTool::isDesktopHighlighter) }

    fun toolConfig(tool: PdfInkTool): PdfToolConfig {
        return toolConfigs[tool] ?: SharedPdfAnnotationDefaults.configFor(tool)
    }

    fun toolColor(tool: PdfInkTool): Int {
        return if (tool == selectedTool) selectedColor else toolConfig(tool).colorArgb
    }

    var lastPenTool by remember(penTools) {
        mutableStateOf(
            selectedTool.takeIf { it in penTools }
                ?: lastActivePenTool.takeIf { it in penTools }
                ?: penTools.firstOrNull()
                ?: PdfInkTool.PEN
        )
    }
    var lastHighlighterTool by remember(highlighterTools) {
        mutableStateOf(
            selectedTool.takeIf { it in highlighterTools }
                ?: lastActiveHighlighterTool.takeIf { it in highlighterTools }
                ?: highlighterTools.firstOrNull()
                ?: PdfInkTool.HIGHLIGHTER
        )
    }
    var activeSettingsPanel by remember { mutableStateOf<SharedPdfAnnotationSettingsPanel?>(null) }
    var showClearPageConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(
        isTextSelectionMode,
        selectedTool,
        lastActivePenTool,
        lastActiveHighlighterTool,
        penTools,
        highlighterTools
    ) {
        when {
            selectedTool in penTools -> lastPenTool = selectedTool
            selectedTool in highlighterTools -> lastHighlighterTool = selectedTool
            selectedTool != PdfInkTool.ERASER -> {
                lastActivePenTool.takeIf { it in penTools }?.let { lastPenTool = it }
                lastActiveHighlighterTool.takeIf { it in highlighterTools }?.let { lastHighlighterTool = it }
                activeSettingsPanel = null
            }
        }
        if (isTextSelectionMode || selectedTool == PdfInkTool.NONE || selectedTool == PdfInkTool.TEXT) {
            activeSettingsPanel = null
        }
    }

    LaunchedEffect(allowExpandedSettings) {
        if (!allowExpandedSettings) {
            activeSettingsPanel = null
        }
    }

    fun selectToolWithSettings(tool: PdfInkTool, panel: SharedPdfAnnotationSettingsPanel) {
        val shouldCollapse = activeSettingsPanel == panel && selectedTool == tool
        onToolSelected(tool)
        activeSettingsPanel = if (shouldCollapse || !allowExpandedSettings) null else panel
    }

    Column(
        modifier = modifier.widthIn(max = 720.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (allowExpandedSettings) {
            activeSettingsPanel?.let { panel ->
                val panelTools = when (panel) {
                    SharedPdfAnnotationSettingsPanel.PEN -> penTools
                    SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> highlighterTools
                    SharedPdfAnnotationSettingsPanel.ERASER -> listOf(PdfInkTool.ERASER)
                }
                val panelTool = when (panel) {
                    SharedPdfAnnotationSettingsPanel.PEN -> selectedTool.takeIf { it in penTools } ?: lastPenTool
                    SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> selectedTool.takeIf { it in highlighterTools } ?: lastHighlighterTool
                    SharedPdfAnnotationSettingsPanel.ERASER -> PdfInkTool.ERASER
                }
                SharedPdfAnnotationToolSettingsPanel(
                    panel = panel,
                    tools = panelTools,
                    selectedTool = panelTool,
                    selectedColor = selectedColor,
                    strokeWidth = strokeWidth,
                    toolConfigs = toolConfigs,
                    penPalette = penPalette,
                    highlighterPalette = highlighterPalette,
                    onToolSelected = { tool ->
                        when (panel) {
                            SharedPdfAnnotationSettingsPanel.PEN -> lastPenTool = tool
                            SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> lastHighlighterTool = tool
                            SharedPdfAnnotationSettingsPanel.ERASER -> Unit
                        }
                        onToolSelected(tool)
                    },
                    onColorSelected = onColorSelected,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onPaletteChange = { nextPalette ->
                        when (panel) {
                            SharedPdfAnnotationSettingsPanel.PEN -> onPenPaletteChange(nextPalette)
                            SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> onHighlighterPaletteChange(nextPalette)
                            SharedPdfAnnotationSettingsPanel.ERASER -> Unit
                        }
                    },
                    isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                    onHighlighterSnapChange = onHighlighterSnapChange
                )
            }
        }

        Surface(
            color = Color(0xFF1E1E1E),
            contentColor = Color.White,
            shape = RoundedCornerShape(percent = 50),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.height(56.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (SharedPdfInteractionDockItem.PAN in dockItems) {
                    SharedPdfModeDockButton(
                        tooltip = readerString("pdf_pan_mode", "Pan"),
                        selected = !isTextSelectionMode && selectedTool == PdfInkTool.NONE,
                        onClick = {
                            activeSettingsPanel = null
                            onPanSelected()
                        }
                    ) { tint ->
                        SharedPdfAndroidPathIcon(
                            pathData = SharedPdfAndroidTouchAppPath,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (SharedPdfInteractionDockItem.SELECT_TEXT in dockItems) {
                    SharedPdfModeDockButton(
                        tooltip = readerString("pdf_text_select_mode", "Select text"),
                        selected = isTextSelectionMode,
                        onClick = {
                            activeSettingsPanel = null
                            onTextSelectionSelected()
                        }
                    ) { tint ->
                        SharedPdfAndroidPathIcon(
                            pathData = SharedPdfAndroidTextSelectStartPath,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                SharedPdfInteractionDivider()

                if (onToggleStylusOnlyMode != null) {
                    val stylusOnlyLabel = readerString("pdf_stylus_only_mode", "Stylus-only mode")
                    val stylusOnlyState = readerString(
                        if (isStylusOnlyMode) "common_enabled" else "common_disabled",
                        if (isStylusOnlyMode) "On" else "Off"
                    )
                    ReaderTooltipIconButton(
                        tooltip = stylusOnlyLabel,
                        onClick = { onToggleStylusOnlyMode() },
                        // Keep the compact 36dp visual while preserving the
                        // platform-recommended minimum target for Pencil and
                        // VoiceOver users.
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isStylusOnlyMode) Color.White.copy(alpha = 0.15f) else Color.Transparent
                                )
                                .semantics {
                                    contentDescription = stylusOnlyLabel
                                    stateDescription = stylusOnlyState
                                    selected = isStylusOnlyMode
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            SharedPdfAndroidPathIcon(
                                pathData = if (isStylusOnlyMode) SharedPdfAndroidDoNotTouchPath else SharedPdfAndroidTouchAppPath,
                                tint = if (isStylusOnlyMode) Color(0xFFE57373) else Color.White.copy(alpha = 0.76f),
                                viewportWidth = 24f,
                                viewportHeight = 24f,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (SharedPdfInteractionDockItem.PEN in dockItems) {
                    val tool = selectedTool.takeIf { it in penTools } ?: lastPenTool
                    SharedPdfToolButton(
                        tool = tool,
                        selected = !isTextSelectionMode && selectedTool in penTools,
                        color = toolColor(tool),
                        strokeWidth = strokeWidth,
                        onClick = { selectToolWithSettings(tool, SharedPdfAnnotationSettingsPanel.PEN) }
                    )
                }

                if (SharedPdfInteractionDockItem.HIGHLIGHTER in dockItems) {
                    val tool = selectedTool.takeIf { it in highlighterTools } ?: lastHighlighterTool
                    SharedPdfToolButton(
                        tool = tool,
                        selected = !isTextSelectionMode && selectedTool in highlighterTools,
                        color = toolColor(tool),
                        strokeWidth = strokeWidth,
                        onClick = { selectToolWithSettings(tool, SharedPdfAnnotationSettingsPanel.HIGHLIGHTER) }
                    )
                }

                if (SharedPdfInteractionDockItem.TEXT_NOTE in dockItems) {
                    SharedPdfToolButton(
                        tool = PdfInkTool.TEXT,
                        selected = !isTextSelectionMode && selectedTool == PdfInkTool.TEXT,
                        color = toolColor(PdfInkTool.TEXT),
                        strokeWidth = strokeWidth,
                        onClick = {
                            activeSettingsPanel = null
                            onToolSelected(PdfInkTool.TEXT)
                        }
                    )
                }

                if (SharedPdfInteractionDockItem.ERASER in dockItems) {
                    SharedPdfToolButton(
                        tool = PdfInkTool.ERASER,
                        selected = !isTextSelectionMode && selectedTool == PdfInkTool.ERASER,
                        color = null,
                        strokeWidth = strokeWidth,
                        onClick = { selectToolWithSettings(PdfInkTool.ERASER, SharedPdfAnnotationSettingsPanel.ERASER) }
                    )
                }

                SharedPdfInteractionDivider()

                if (SharedPdfInteractionDockItem.UNDO in dockItems) {
                    DockCircleButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        showBackground = false,
                        contentDescription = readerString("content_desc_undo", "Undo")
                    ) {
                        SharedPdfAndroidPathIcon(
                            pathData = SharedPdfAndroidUndoPath,
                            tint = Color.White.copy(alpha = if (canUndo) 0.88f else 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (SharedPdfInteractionDockItem.REDO in dockItems) {
                    DockCircleButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        showBackground = false,
                        contentDescription = readerString("content_desc_redo", "Redo")
                    ) {
                        SharedPdfAndroidPathIcon(
                            pathData = SharedPdfAndroidRedoPath,
                            tint = Color.White.copy(alpha = if (canRedo) 0.88f else 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (SharedPdfInteractionDockItem.CLEAR_PAGE in dockItems) {
                    DockCircleButton(
                        onClick = { showClearPageConfirmation = true },
                        enabled = canClearPage,
                        contentDescription = readerString("pdf_clear_page_annotations", "Clear page annotations")
                    ) {
                        SharedPdfAndroidPathIcon(
                            pathData = SharedPdfAndroidDeletePath,
                            tint = Color.White.copy(alpha = if (canClearPage) 0.88f else 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearPageConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearPageConfirmation = false },
            title = { Text(readerString("pdf_clear_page_annotations", "Clear page annotations")) },
            text = {
                Text(
                    readerString(
                        "pdf_clear_page_annotations_confirm",
                        "Delete all annotations on this page?"
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearPageConfirmation = false
                        onClearPage()
                    }
                ) {
                    Text(readerString("action_delete", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPageConfirmation = false }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun SharedPdfAnnotationToolDock(
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    tools: List<PdfInkTool> = SharedPdfAnnotationDefaultTools,
    toolConfigs: Map<PdfInkTool, PdfToolConfig> = emptyMap(),
    penPalette: List<Int> = SharedPdfAnnotationDefaults.penPalette,
    highlighterPalette: List<Int> = SharedPdfHighlighterPalette.defaultColors,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    isHighlighterSnapEnabled: Boolean = false,
    onHighlighterSnapChange: (Boolean) -> Unit = {}
) {
    val availableTools = tools.distinct()
    val penTools = listOf(PdfInkTool.FOUNTAIN_PEN, PdfInkTool.PEN, PdfInkTool.PENCIL)
        .filter { it in availableTools }
    val highlighterTools = listOf(PdfInkTool.HIGHLIGHTER, PdfInkTool.HIGHLIGHTER_ROUND)
        .filter { it in availableTools }
    var lastPenTool by remember { mutableStateOf(PdfInkTool.PEN) }
    var lastHighlighterTool by remember { mutableStateOf(PdfInkTool.HIGHLIGHTER) }
    var activeSettingsPanel by remember { mutableStateOf<SharedPdfAnnotationSettingsPanel?>(null) }

    LaunchedEffect(selectedTool) {
        when {
            selectedTool in penTools -> lastPenTool = selectedTool
            selectedTool in highlighterTools -> lastHighlighterTool = selectedTool
            selectedTool != PdfInkTool.ERASER -> activeSettingsPanel = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF1E1E1E),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (penTools.isNotEmpty()) {
                    val tool = selectedTool.takeIf { it in penTools } ?: lastPenTool.takeIf { it in penTools } ?: penTools.first()
                    SharedPdfToolButton(
                        tool = tool,
                        selectedTool = selectedTool,
                        selectedColor = selectedColor,
                        strokeWidth = strokeWidth,
                        onToolSelected = {
                            onToolSelected(tool)
                            activeSettingsPanel = SharedPdfAnnotationSettingsPanel.PEN
                        }
                    )
                }

                if (highlighterTools.isNotEmpty()) {
                    val tool = selectedTool.takeIf { it in highlighterTools }
                        ?: lastHighlighterTool.takeIf { it in highlighterTools }
                        ?: highlighterTools.first()
                    SharedPdfToolButton(
                        tool = tool,
                        selectedTool = selectedTool,
                        selectedColor = selectedColor,
                        strokeWidth = strokeWidth,
                        onToolSelected = {
                            onToolSelected(tool)
                            activeSettingsPanel = SharedPdfAnnotationSettingsPanel.HIGHLIGHTER
                        }
                    )
                }

                if (PdfInkTool.TEXT in availableTools) {
                    SharedPdfToolButton(
                        tool = PdfInkTool.TEXT,
                        selectedTool = selectedTool,
                        selectedColor = selectedColor,
                        strokeWidth = strokeWidth,
                        onToolSelected = {
                            activeSettingsPanel = null
                            onToolSelected(PdfInkTool.TEXT)
                        }
                    )
                }

                if (PdfInkTool.ERASER in availableTools) {
                    SharedPdfToolButton(
                        tool = PdfInkTool.ERASER,
                        selectedTool = selectedTool,
                        selectedColor = selectedColor,
                        strokeWidth = strokeWidth,
                        onToolSelected = {
                            onToolSelected(PdfInkTool.ERASER)
                            activeSettingsPanel = SharedPdfAnnotationSettingsPanel.ERASER
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )

                DockCircleButton(onClick = onUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = readerString("desktop_undo_annotation", "Undo annotation"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DockCircleButton(onClick = onClearPage) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = readerString("desktop_clear_page_annotations", "Clear page annotations"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        activeSettingsPanel?.let { panel ->
            val toolsForPanel = when (panel) {
                SharedPdfAnnotationSettingsPanel.PEN -> penTools
                SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> highlighterTools
                SharedPdfAnnotationSettingsPanel.ERASER -> listOf(PdfInkTool.ERASER).filter { it in availableTools }
            }
            if (toolsForPanel.isNotEmpty()) {
                val panelTool = when (panel) {
                    SharedPdfAnnotationSettingsPanel.PEN -> selectedTool.takeIf { it in penTools } ?: lastPenTool
                    SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> selectedTool.takeIf { it in highlighterTools } ?: lastHighlighterTool
                    SharedPdfAnnotationSettingsPanel.ERASER -> PdfInkTool.ERASER
                }
                SharedPdfAnnotationToolSettingsPanel(
                    panel = panel,
                    tools = toolsForPanel,
                    selectedTool = panelTool,
                    selectedColor = selectedColor,
                    strokeWidth = strokeWidth,
                    toolConfigs = toolConfigs,
                    penPalette = penPalette,
                    highlighterPalette = highlighterPalette,
                    onToolSelected = { tool ->
                        when (panel) {
                            SharedPdfAnnotationSettingsPanel.PEN -> lastPenTool = tool
                            SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> lastHighlighterTool = tool
                            SharedPdfAnnotationSettingsPanel.ERASER -> Unit
                        }
                        onToolSelected(tool)
                    },
                    onColorSelected = onColorSelected,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onPaletteChange = {},
                    isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                    onHighlighterSnapChange = onHighlighterSnapChange
                )
            }
        }
    }
}

@Composable
private fun SharedPdfAnnotationToolSettingsPanel(
    panel: SharedPdfAnnotationSettingsPanel,
    tools: List<PdfInkTool>,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    toolConfigs: Map<PdfInkTool, PdfToolConfig>,
    penPalette: List<Int>,
    highlighterPalette: List<Int>,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onPaletteChange: (List<Int>) -> Unit,
    isHighlighterSnapEnabled: Boolean,
    onHighlighterSnapChange: (Boolean) -> Unit
) {
    val isEraser = panel == SharedPdfAnnotationSettingsPanel.ERASER
    val isHighlighter = panel == SharedPdfAnnotationSettingsPanel.HIGHLIGHTER
    val effectiveTool = if (isEraser) PdfInkTool.ERASER else selectedTool
    val strokeRange = effectiveTool.sharedPdfStrokeWidthRange()
    val sliderValue = strokeWidth.coerceIn(strokeRange.start, strokeRange.endInclusive)
    val activeColor = if (isEraser) Color.White else Color(selectedColor)
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerSlotIndex by remember { mutableStateOf<Int?>(null) }
    var colorPickerDraftPalette by remember { mutableStateOf<List<Int>>(emptyList()) }
    val activePalette = if (isHighlighter) {
        SharedPdfHighlighterPalette(
            highlighterPalette.ifEmpty { SharedPdfHighlighterPalette.defaultColors }
        ).sanitized().colors
    } else {
        penPalette.ifEmpty { SharedPdfAnnotationDefaults.penPalette }
    }
    val selectedPaletteIndex = remember(activePalette, selectedColor, isHighlighter) {
        sharedPdfSettingsSelectedPaletteIndex(
            activePalette = activePalette,
            selectedColor = selectedColor,
            matchRgbOnly = isHighlighter
        )
    }

    fun colorPickerPalette(): List<Int> {
        return colorPickerDraftPalette.ifEmpty { activePalette }
    }

    fun updateColorPickerDraft(slotIndex: Int, color: Color): List<Int> {
        val nextPalette = colorPickerPalette().toMutableList()
        if (slotIndex in nextPalette.indices) {
            nextPalette[slotIndex] = color.copy(alpha = 1f).toArgb()
            colorPickerDraftPalette = nextPalette
        }
        return nextPalette
    }

    fun openColorPicker(slotIndex: Int) {
        if (!showColorPicker) {
            colorPickerDraftPalette = activePalette
        }
        colorPickerSlotIndex = slotIndex
        showColorPicker = true
    }

    fun selectPaletteColor(argb: Int) {
        onColorSelected(
            if (isHighlighter) {
                argb.withSharedPdfAnnotationAlpha(Color(selectedColor).alpha)
            } else {
                argb
            }
        )
    }

    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .width(360.dp)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEraser) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val diameter = (sliderValue * 800f).coerceIn(4f, 150f).dp
                    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.3f),
                                radius = size.minDimension / 2f
                            )
                            drawCircle(
                                color = Color.White,
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        tools.forEach { tool ->
                            val toolColor = Color(
                                if (tool == selectedTool) {
                                    selectedColor
                                } else {
                                    toolConfigs[tool]?.colorArgb
                                        ?: SharedPdfAnnotationDefaults.configFor(tool).colorArgb
                                }
                            )
                            SharedPdfSettingsToolItem(
                                tool = tool,
                                color = toolColor.copy(alpha = 1f),
                                inkColor = toolColor,
                                isSelected = tool == selectedTool,
                                strokeWidth = sliderValue,
                                isHighlighterSnapEnabled = isHighlighterSnapEnabled,
                                onClick = { onToolSelected(tool) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isHighlighter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = readerString("label_straight_line", "Straight line"),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isHighlighterSnapEnabled,
                        onCheckedChange = onHighlighterSnapChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = activeColor.copy(alpha = 1f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF424242)
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            SharedPdfStyledPropertySlider(
                value = sliderValue,
                onValueChange = onStrokeWidthChange,
                valueRange = strokeRange,
                isOpacity = false,
                trackColor = Color(0xFF424242),
                thumbColor = Color(0xFF757575),
                activeColor = if (isEraser) Color.White else activeColor
            )

            if (isHighlighter) {
                Spacer(Modifier.height(16.dp))
                val alpha = Color(selectedColor).alpha.coerceIn(0.1f, 1f)
                SharedPdfStyledPropertySlider(
                    value = alpha,
                    onValueChange = { nextAlpha ->
                        onColorSelected(selectedColor.withSharedPdfAnnotationAlpha(nextAlpha))
                    },
                    valueRange = 0.1f..1f,
                    isOpacity = true,
                    trackColor = activeColor.copy(alpha = 1f),
                    thumbColor = activeColor.copy(alpha = 1f),
                    activeColor = activeColor
                )
            }

            if (!isEraser) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        activePalette
                            .take(if (isHighlighter) SharedPdfHighlighterPalette.MaxColors else 6)
                            .forEachIndexed { index, argb ->
                                val isSelected = index == selectedPaletteIndex
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .pointerInput(argb, index) {
                                            detectTapGestures(
                                                onTap = { selectPaletteColor(argb) },
                                                onLongPress = {
                                                    openColorPicker(index)
                                                }
                                            )
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(color = Color(argb).copy(alpha = 1f))
                                        if (isSelected) {
                                            drawCircle(
                                                color = Color.White,
                                                radius = size.minDimension / 2f,
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }
                    }
                    Spacer(Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Red,
                                        Color.Magenta,
                                        Color.Blue,
                                        Color.Cyan,
                                        Color.Green,
                                        Color.Yellow,
                                        Color.Red
                                    )
                                )
                            )
                            .clickable {
                                if (selectedPaletteIndex != -1) {
                                    openColorPicker(selectedPaletteIndex)
                                }
                            }
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        val pickerPalette = colorPickerPalette()
        val slot = (colorPickerSlotIndex ?: 0).coerceIn(0, pickerPalette.lastIndex.coerceAtLeast(0))
        val initialColor = remember(slot, showColorPicker) {
            Color(pickerPalette.getOrElse(slot) { pickerPalette.firstOrNull() ?: selectedColor }).copy(alpha = 1f)
        }
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = readerString("label_spectrum", "Spectrum"),
            onDismiss = { showColorPicker = false },
            onSave = { color ->
                val nextPalette = updateColorPickerDraft(slot, color)
                if (slot in nextPalette.indices) {
                    onPaletteChange(nextPalette)
                    if (isHighlighter) {
                        onColorSelected(color.toArgb().withSharedPdfAnnotationAlpha(Color(selectedColor).alpha))
                    } else {
                        onColorSelected(color.toArgb())
                    }
                }
                showColorPicker = false
            },
            resetColor = initialColor,
            stateKey = slot,
            onLiveColorChange = { color ->
                updateColorPickerDraft(slot, color)
            }
        ) { liveColor ->
            SharedPdfHighlighterPalettePreview(
                colors = colorPickerPalette(),
                activeSlot = slot,
                activeColor = liveColor,
                onSlotSelected = { index ->
                    colorPickerSlotIndex = index
                }
            )
        }
    }
}

internal fun sharedPdfSettingsSelectedPaletteIndex(
    activePalette: List<Int>,
    selectedColor: Int,
    matchRgbOnly: Boolean
): Int {
    return activePalette.indexOfFirst { paletteColor ->
        if (matchRgbOnly) {
            (paletteColor and 0x00FFFFFF) == (selectedColor and 0x00FFFFFF)
        } else {
            paletteColor == selectedColor
        }
    }
}

@Composable
private fun SharedPdfHighlighterPalettePreview(
    colors: List<Int>,
    activeSlot: Int,
    activeColor: Color,
    onSlotSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEachIndexed { index, argb ->
            val color = if (index == activeSlot) activeColor else Color(argb).copy(alpha = 1f)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (index == activeSlot) 3.dp else 1.dp,
                        color = if (index == activeSlot) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                        shape = CircleShape
                    )
                    .clickable { onSlotSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                if (index == activeSlot) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedPdfSettingsToolItem(
    tool: PdfInkTool,
    color: Color,
    inkColor: Color?,
    isSelected: Boolean,
    strokeWidth: Float,
    isHighlighterSnapEnabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 0.9f,
        label = "shared_pdf_settings_tool_scale"
    )

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(100.dp)
            .scale(scale)
            .semantics { selected = isSelected }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        SharedPdfPenIcon(
            tool = tool,
            color = color,
            inkColor = inkColor ?: color,
            isSelected = isSelected,
            strokeWidth = strokeWidth,
            modifier = Modifier.fillMaxSize(),
            showHighlighterSnap = isHighlighterSnapEnabled
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SharedPdfStyledPropertySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isOpacity: Boolean,
    trackColor: Color,
    thumbColor: Color,
    activeColor: Color
) {
    val displayValue = remember(value, valueRange) { sharedPdfSettingsDisplayPercent(value, valueRange) }
    val onePercentDelta = (valueRange.endInclusive - valueRange.start) / 100f
    val canDecrease = value > valueRange.start + 0.0001f
    val canIncrease = value < valueRange.endInclusive - 0.0001f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = canDecrease) {
                    onValueChange((value - onePercentDelta).coerceAtLeast(valueRange.start))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2014",
                color = if (canDecrease) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(4.dp))

        Box(modifier = Modifier.weight(1f)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.height(32.dp),
                thumb = {
                    Surface(
                        shape = CircleShape,
                        color = thumbColor,
                        modifier = Modifier
                            .size(26.dp)
                            .padding(2.dp),
                        shadowElevation = 4.dp,
                        border = if (isOpacity) null else BorderStroke(1.dp, Color.Gray)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayValue.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                track = { _ ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                    ) {
                        val trackHeight = size.height
                        val cornerRadius = CornerRadius(trackHeight / 2f)
                        if (isOpacity) {
                            drawRoundRect(
                                color = Color.Gray,
                                size = size,
                                cornerRadius = cornerRadius
                            )
                            val roundedClipPath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(Offset.Zero, size),
                                        cornerRadius = cornerRadius
                                    )
                                )
                            }
                            clipPath(roundedClipPath) {
                                val boxSize = 12f
                                val columns = (size.width / boxSize).toInt() + 1
                                val rows = (size.height / boxSize).toInt() + 1
                                for (column in 0 until columns) {
                                    for (row in 0 until rows) {
                                        drawRect(
                                            color = if ((column + row) % 2 == 0) {
                                                Color(0xFF555555)
                                            } else {
                                                Color(0xFF333333)
                                            },
                                            topLeft = Offset(column * boxSize, row * boxSize),
                                            size = Size(boxSize, boxSize)
                                        )
                                    }
                                }
                                drawRect(color = activeColor)
                            }
                        } else {
                            drawRoundRect(
                                color = trackColor.copy(alpha = 0.5f),
                                size = size,
                                cornerRadius = cornerRadius
                            )
                            val dotRadius = 1.5.dp.toPx()
                            val padding = trackHeight / 2f
                            val availableWidth = size.width - (padding * 2f)
                            val dotCount = 8
                            val spacing = availableWidth / (dotCount - 1)
                            for (index in 0 until dotCount) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.2f),
                                    radius = dotRadius,
                                    center = Offset(padding + (index * spacing), size.height / 2f)
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = canIncrease) {
                    onValueChange((value + onePercentDelta).coerceAtMost(valueRange.endInclusive))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (canIncrease) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

internal fun sharedPdfSettingsDisplayPercent(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>
): Int {
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range == 0f) 0f else (value - valueRange.start) / range
    return (fraction * 100f).roundToInt().coerceIn(1, 100)
}

@Composable
private fun SharedPdfInkColorPalette(
    colors: List<Int>,
    selectedColor: Int,
    matchRgbOnly: Boolean,
    onColorSelected: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { argb ->
            val selected = if (matchRgbOnly) {
                (argb and 0x00FFFFFF) == (selectedColor and 0x00FFFFFF)
            } else {
                argb == selectedColor
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb).copy(alpha = 1f))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(argb) }
            )
        }
    }
}

@Composable
fun SharedPdfHighlighterPaletteEditor(
    palette: SharedPdfHighlighterPalette,
    onPaletteChange: (SharedPdfHighlighterPalette) -> Unit,
    modifier: Modifier = Modifier
) {
    val sanitized = palette.sanitized()
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Highlight colors",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Tap a color to customize it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            sanitized.colors.forEachIndexed { index, argb ->
                val color = Color(argb)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 1f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .clickable { editingSlot = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    editingSlot?.let { slot ->
        val initialColor = Color(sanitized.colors.getOrElse(slot) { SharedPdfHighlighterPalette.defaultColors.first() }).copy(alpha = 1f)
        SharedHsvColorPickerDialog(
            initialColor = initialColor,
            title = readerString("desktop_highlight_color_format", "Highlight color %1\$d", slot + 1),
            onDismiss = { editingSlot = null },
            onSave = { color ->
                onPaletteChange(
                    sanitized.withColorAt(
                        slotIndex = slot,
                        colorArgb = color.copy(alpha = SharedPdfHighlighterPalette.DefaultAlpha / 255f).toArgb()
                    )
                )
                editingSlot = null
            },
            resetColor = Color(SharedPdfHighlighterPalette.defaultColors.getOrElse(slot) {
                SharedPdfHighlighterPalette.defaultColors.first()
            }).copy(alpha = 1f),
            stateKey = slot
        ) { color ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(readerString("desktop_pdf_highlighter", "PDF highlighter"), fontWeight = FontWeight.SemiBold)
                    Text(
                        readerString("desktop_pdf_highlighter_alpha_desc", "Saved with reader highlight transparency."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SharedDesktopPdfTextAnnotationDock(
    style: SharedPdfTextStyleConfig,
    onStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    onInsertTextBox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = readerString("text_annotation_dock_title", "Text box"),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelLarge
                )
                Button(
                    onClick = onInsertTextBox,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF64B5F6),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = readerString("content_desc_insert_text_box", "Insert text box"),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = readerString("action_insert_text_box", "Insert"),
                        fontSize = 12.sp
                    )
                }
            }
            SharedPdfTextStyleControls(
                style = style,
                onStyleChange = onStyleChange,
                dark = true
            )
        }
    }
}

@Composable
fun SharedPdfInlineTextEditorOverlay(
    draft: SharedPdfTextDraft?,
    canvasSize: IntSize,
    onTextChange: (String) -> Unit,
    onBoundsChange: (PdfPageBounds) -> Unit,
    modifier: Modifier = Modifier
) {
    if (draft == null || canvasSize.width <= 0 || canvasSize.height <= 0) return

    SharedPdfTextBoxEditorOverlay(
        id = draft.id,
        text = draft.text,
        style = draft.style,
        bounds = draft.bounds,
        canvasSize = canvasSize,
        onTextChange = onTextChange,
        onBoundsChange = onBoundsChange,
        modifier = modifier
    )
}

@Composable
fun SharedPdfTextBoxEditorOverlay(
    id: String,
    text: String,
    style: SharedPdfTextStyleConfig,
    bounds: PdfPageBounds,
    canvasSize: IntSize,
    onTextChange: (String) -> Unit,
    onBoundsChange: (PdfPageBounds) -> Unit,
    customFontFamilies: Map<String, FontFamily> = emptyMap(),
    onGlobalDragStart: (() -> Unit)? = null,
    onGlobalDrag: ((Offset) -> Unit)? = null,
    onGlobalDragEnd: (() -> Unit)? = null,
    onGlobalDragCancel: (() -> Unit)? = null,
    isDraggingGlobally: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return

    val density = LocalDensity.current
    val focusRequester = remember(id) { FocusRequester() }
    var liveBounds by remember(id) { mutableStateOf(bounds) }
    var isResizing by remember(id) { mutableStateOf(false) }

    LaunchedEffect(bounds) {
        if (!isResizing) {
            liveBounds = bounds
        }
    }

    val leftPx = liveBounds.left * canvasSize.width
    val topPx = liveBounds.top * canvasSize.height
    val widthPx = ((liveBounds.right - liveBounds.left) * canvasSize.width).coerceAtLeast(50f)
    val heightPx = ((liveBounds.bottom - liveBounds.top) * canvasSize.height).coerceAtLeast(50f)
    val textColor = Color(style.colorArgb)
    val backgroundColor = Color(style.backgroundColorArgb)
    val handleSize = 10.dp
    val handleTouchSize = 38.dp
    val handleTouchSizePx = with(density) { handleTouchSize.toPx() }
    val moveHandleWidth = 54.dp
    val moveHandleHeight = 24.dp
    val moveHandleWidthPx = with(density) { moveHandleWidth.toPx() }
    val moveHandleHeightPx = with(density) { moveHandleHeight.toPx() }
    val moveHandleBelow = topPx + heightPx + moveHandleHeightPx + 10f <= canvasSize.height
    var textFieldValue by remember(id) {
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }

    LaunchedEffect(id, style) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(id, text) {
        if (text != textFieldValue.text) {
            textFieldValue = TextFieldValue(text, TextRange(text.length))
        }
    }

    val fontSizePx = style.sharedPdfTextFontSizePx(canvasSize)

    Box(modifier = modifier.fillMaxSize().alpha(if (isDraggingGlobally) 0f else 1f)) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { nextValue ->
                textFieldValue = nextValue
                if (nextValue.text != text) {
                    onTextChange(nextValue.text)
                }
            },
            textStyle = TextStyle(
                color = textColor,
                fontSize = with(density) { fontSizePx.toSp() },
                lineHeight = with(density) { (fontSizePx * 1.25f).toSp() },
                fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = sharedPdfFontFamily(style.fontPath, customFontFamilies)
                    ?: sharedPdfFontFamily(style.fontName, customFontFamilies),
                textDecoration = style.textDecoration
            ),
            cursorBrush = SolidColor(textColor),
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { heightPx.toDp() })
                .background(
                    color = if (style.backgroundColorArgb.isTransparentArgb()) {
                        Color.Transparent
                    } else {
                        backgroundColor
                    },
                    shape = RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF64B5F6),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(focusRequester)
        )

        SharedPdfTextResizeHandle.entries.forEach { handle ->
            val center = handle.centerOffset(
                leftPx = leftPx,
                topPx = topPx,
                widthPx = widthPx,
                heightPx = heightPx
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - handleTouchSizePx / 2f).roundToInt(),
                            (center.y - handleTouchSizePx / 2f).roundToInt()
                        )
                    }
                    .size(handleTouchSize)
                    .pointerInput(id, handle, canvasSize) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                            },
                            onDragEnd = {
                                isResizing = false
                                onBoundsChange(liveBounds)
                            },
                            onDragCancel = {
                                isResizing = false
                                liveBounds = bounds
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                liveBounds = liveBounds.resizedBy(
                                    handle = handle,
                                    deltaXPx = dragAmount.x,
                                    deltaYPx = dragAmount.y,
                                    canvasSize = canvasSize
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .background(Color(0xFF64B5F6), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (leftPx + (widthPx / 2f) - (moveHandleWidthPx / 2f)).roundToInt(),
                        if (moveHandleBelow) {
                            (topPx + heightPx + 8f).roundToInt()
                        } else {
                            (topPx - moveHandleHeightPx - 8f).roundToInt()
                        }
                    )
                }
                .size(width = moveHandleWidth, height = moveHandleHeight)
                .clip(CircleShape)
                .background(Color(0xFF64B5F6))
                .border(1.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                .pointerInput(id, canvasSize, onGlobalDrag != null) {
                    detectDragGestures(
                        onDragStart = {
                            isResizing = true
                            if (onGlobalDrag == null) return@detectDragGestures
                            onGlobalDragStart?.invoke()
                        },
                        onDragEnd = {
                            isResizing = false
                            if (onGlobalDrag != null) {
                                onGlobalDragEnd?.invoke()
                                return@detectDragGestures
                            }
                            onBoundsChange(liveBounds)
                        },
                        onDragCancel = {
                            isResizing = false
                            if (onGlobalDrag != null) {
                                onGlobalDragCancel?.invoke()
                                return@detectDragGestures
                            }
                            liveBounds = bounds
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (onGlobalDrag != null) {
                                onGlobalDrag(dragAmount)
                                return@detectDragGestures
                            }
                            liveBounds = liveBounds.movedBy(
                                deltaXPx = dragAmount.x,
                                deltaYPx = dragAmount.y,
                                canvasSize = canvasSize
                            )
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(width = 24.dp, height = 10.dp)) {
                val lineColor = Color.White.copy(alpha = 0.92f)
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.2f, size.height * 0.25f),
                    end = Offset(size.width * 0.8f, size.height * 0.25f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.2f, size.height * 0.75f),
                    end = Offset(size.width * 0.8f, size.height * 0.75f),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
fun SharedPdfTextStyleControls(
    style: SharedPdfTextStyleConfig,
    onStyleChange: (SharedPdfTextStyleConfig) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false
) {
    val labelColor = if (dark) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant
    val buttonTextColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val selectedBackground = if (dark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val unselectedBackground = if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    var fontMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(readerString("select_font", "Font"), color = labelColor, style = MaterialTheme.typography.labelMedium)
            Box {
                TextButton(onClick = { fontMenuExpanded = true }) {
                    Text(
                        text = style.displayFontName(),
                        color = buttonTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = fontMenuExpanded,
                    onDismissRequest = { fontMenuExpanded = false }
                ) {
                    SharedPdfTextAnnotationDefaults.fontPresets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name) },
                            onClick = {
                                onStyleChange(style.withFontPreset(preset))
                                fontMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SharedPdfTextAnnotationDefaults.fontSizes.chunked(4).forEach { rowSizes ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    rowSizes.forEach { size ->
                        SharedTextStyleChoiceButton(
                            selected = style.fontSize.toInt() == size.toInt(),
                            selectedBackground = selectedBackground,
                            unselectedBackground = unselectedBackground,
                            onClick = { onStyleChange(style.withSharedPdfTextFontSize(size)) }
                        ) {
                            Text(
                                text = size.toInt().toString(),
                                color = buttonTextColor,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SharedTextStyleChoiceButton(
                selected = style.isBold,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isBold = !style.isBold)) }
            ) {
                Text("B", color = buttonTextColor, fontWeight = FontWeight.Bold)
            }
            SharedTextStyleChoiceButton(
                selected = style.isItalic,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isItalic = !style.isItalic)) }
            ) {
                Text("I", color = buttonTextColor, fontStyle = FontStyle.Italic)
            }
            SharedTextStyleChoiceButton(
                selected = style.isUnderline,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isUnderline = !style.isUnderline)) }
            ) {
                Text("U", color = buttonTextColor, textDecoration = TextDecoration.Underline)
            }
            SharedTextStyleChoiceButton(
                selected = style.isStrikeThrough,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                onClick = { onStyleChange(style.copy(isStrikeThrough = !style.isStrikeThrough)) }
            ) {
                Text("S", color = buttonTextColor, textDecoration = TextDecoration.LineThrough)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(readerString("content_desc_text", "Text"), color = labelColor, style = MaterialTheme.typography.labelMedium)
            SharedTextColorSwatches(
                palette = SharedPdfTextAnnotationDefaults.textColorPalette,
                selectedArgb = style.colorArgb,
                allowTransparent = false,
                dark = dark,
                onColorSelected = { onStyleChange(style.copy(colorArgb = it)) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(readerString("desktop_fill", "Fill"), color = labelColor, style = MaterialTheme.typography.labelMedium)
            SharedTextColorSwatches(
                palette = SharedPdfTextAnnotationDefaults.backgroundColorPalette,
                selectedArgb = style.backgroundColorArgb,
                allowTransparent = true,
                dark = dark,
                onColorSelected = { onStyleChange(style.copy(backgroundColorArgb = it)) }
            )
        }
    }
}
