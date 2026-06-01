package com.aryan.reader.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.PdfToolConfig
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfEmbeddedAnnotation
import com.aryan.reader.shared.pdf.SharedPdfHighlighterPalette
import com.aryan.reader.shared.pdf.SharedPdfInkRenderData
import com.aryan.reader.shared.pdf.SharedPdfInkRenderer
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextFontPreset
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
        activePalette.indexOfFirst { paletteColor ->
            if (isHighlighter) {
                Color(paletteColor).copy(alpha = 1f) == Color(selectedColor).copy(alpha = 1f)
            } else {
                paletteColor == selectedColor
            }
        }
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
    val displayValue = remember(value, valueRange) {
        val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        (fraction * 100f).roundToInt().coerceIn(1, 100)
    }
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
                text = "-",
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
                track = { sliderState ->
                    val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                    val fraction = if (range == 0f) {
                        0f
                    } else {
                        ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                    }
                    val activeTrackColor = when {
                        isOpacity -> activeColor.copy(alpha = 1f)
                        activeColor.luminance() < 0.18f -> Color.White.copy(alpha = 0.88f)
                        else -> activeColor.copy(alpha = 0.95f)
                    }
                    val inactiveTrackColor = if (isOpacity) {
                        Color.White.copy(alpha = 0.24f)
                    } else {
                        trackColor.copy(alpha = 0.85f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(inactiveTrackColor, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .background(activeTrackColor, RoundedCornerShape(2.dp))
                        )
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
fun SharedPdfTextAnnotationDock(
    style: SharedPdfTextStyleConfig,
    onStyleChange: (SharedPdfTextStyleConfig) -> Unit,
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

    Box(modifier = modifier.fillMaxSize()) {
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
                fontFamily = sharedPdfFontFamily(style.fontName ?: style.fontPath),
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
                .pointerInput(id, canvasSize) {
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

@Suppress("UNUSED_PARAMETER")
@Composable
fun SharedPdfAnnotationOverlay(
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    canvasSize: IntSize,
    activeTool: PdfInkTool = PdfInkTool.PEN,
    activeStrokeColorArgb: Int = 0xFF1976D2.toInt(),
    activeStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.PEN).strokeWidth,
    selectedAnnotationId: String? = null,
    eraserPosition: Offset? = null,
    showEraserIndicator: Boolean = false,
    eraserStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            annotations.forEach { annotation ->
                when (annotation.kind) {
                    PdfAnnotationKind.HIGHLIGHT -> {
                        val highlightBounds = annotation.boundsList.ifEmpty { listOfNotNull(annotation.bounds) }
                        val style = sharedPdfHighlightAnnotationOverlayStyle(annotation)
                        highlightBounds.forEach { bounds ->
                            drawRect(
                                color = style.color,
                                topLeft = bounds.topLeft(canvasSize),
                                size = bounds.size(canvasSize),
                                blendMode = style.blendMode
                            )
                        }
                    }
                    PdfAnnotationKind.INK -> {
                        SharedPdfInkRenderer.createRenderData(annotation, canvasSize)?.let(::drawInkRenderData)
                    }
                    PdfAnnotationKind.TEXT -> {
                        val bounds = annotation.bounds ?: return@forEach
                        if (!annotation.backgroundArgb.isTransparentArgb()) {
                            drawRoundRect(
                                color = Color(annotation.backgroundArgb),
                                topLeft = bounds.topLeft(canvasSize),
                                size = bounds.size(canvasSize),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }
            }

            if (activeStroke.isNotEmpty()) {
                val activeAnnotation = SharedPdfAnnotation(
                    id = "active",
                    pageIndex = 0,
                    kind = PdfAnnotationKind.INK,
                    tool = activeTool,
                    points = activeStroke,
                    colorArgb = activeStrokeColorArgb,
                    strokeWidth = activeStrokeWidth
                )
                SharedPdfInkRenderer.createRenderData(activeAnnotation, canvasSize)?.let(::drawInkRenderData)
            }

            if (showEraserIndicator && eraserPosition != null) {
                val radius = SharedPdfInkRenderer.effectiveStrokeWidthPx(eraserStrokeWidth, canvasSize)
                    .coerceAtLeast(8.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = radius,
                    center = eraserPosition
                )
                drawCircle(
                    color = Color.Black,
                    radius = radius,
                    center = eraserPosition,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        annotations
            .filter { it.kind == PdfAnnotationKind.TEXT && it.text.isNotBlank() }
            .forEach { annotation ->
                val bounds = annotation.bounds ?: return@forEach
                val leftPx = bounds.left * canvasSize.width
                val topPx = bounds.top * canvasSize.height
                val widthPx = ((bounds.right - bounds.left) * canvasSize.width).coerceAtLeast(24f)
                val heightPx = ((bounds.bottom - bounds.top) * canvasSize.height).coerceAtLeast(18f)
                val fontSizePx = annotation.sharedPdfTextFontSizePx(canvasSize)
                Text(
                    text = annotation.text,
                    color = Color(annotation.colorArgb),
                    fontSize = with(density) { fontSizePx.toSp() },
                    lineHeight = with(density) { (fontSizePx * 1.25f).toSp() },
                    fontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = annotation.sharedPdfTextFontFamily(),
                    textDecoration = annotation.textDecoration,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = SharedPdfTextAnnotationDefaults.estimateLineCount(annotation.text, fontSizePx, widthPx),
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .width(with(density) { widthPx.toDp() })
                        .heightIn(
                            min = with(density) { heightPx.toDp() },
                            max = with(density) { heightPx.toDp() }
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
    }
}

internal data class SharedPdfHighlightAnnotationOverlayStyle(
    val color: Color,
    val blendMode: BlendMode
)

internal fun sharedPdfHighlightAnnotationOverlayStyle(
    annotation: SharedPdfAnnotation
): SharedPdfHighlightAnnotationOverlayStyle {
    val storedColor = Color(annotation.colorArgb)
    val renderAlpha = storedColor.alpha
        .takeIf { it > 0f }
        ?.coerceAtMost(SharedPdfAndroidHighlightColors.RenderAlpha)
        ?: SharedPdfAndroidHighlightColors.RenderAlpha
    return SharedPdfHighlightAnnotationOverlayStyle(
        color = storedColor.copy(alpha = renderAlpha),
        blendMode = BlendMode.Multiply
    )
}

@Composable
fun SharedPdfPageNumberOverlay(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    isDarkPage: Boolean = false
) {
    if (pageCount <= 0 || pageIndex !in 0 until pageCount) return
    val textColor = if (isDarkPage) Color.White else Color.Black
    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = "${pageIndex + 1}/$pageCount",
            color = textColor.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        )
    }
}

@Composable
fun SharedPdfEmbeddedAnnotationOverlay(
    annotations: List<SharedPdfEmbeddedAnnotation>,
    canvasSize: IntSize,
    selectedAnnotationId: String? = null
) {
    if (annotations.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    Canvas(Modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            val bounds = annotation.bounds
            val isSelected = annotation.id == selectedAnnotationId
            val color = if (isSelected) Color(0xFF1976D2) else Color(0xFFFF9800)
            drawRect(
                color = color.copy(alpha = if (isSelected) 0.12f else 0.07f),
                topLeft = bounds.topLeft(canvasSize),
                size = bounds.size(canvasSize)
            )
            drawRect(
                color = color,
                topLeft = bounds.topLeft(canvasSize),
                size = bounds.size(canvasSize),
                style = Stroke(width = if (isSelected) 2.5f else 1.25f)
            )
        }
    }
}

private const val SharedPdfAndroidPenPath = "M490,433L527,470L744,253L707,216L490,433ZM200,760L237,760L470,527L433,490L200,723L200,760ZM555,555L405,405L572,238L543,209Q543,209 543,209Q543,209 543,209L324,428L268,372L486,153Q510,129 542.5,129Q575,129 599,153L628,182L678,132Q690,120 706.5,120Q723,120 735,132L828,225Q840,237 840,253.5Q840,270 828,282L555,555ZM270,840L120,840L120,690L405,405L555,555L270,840Z"
private const val SharedPdfAndroidMarkerPath = "M272,856L234,818L192,860Q173,879 145.5,879.5Q118,880 100,860Q81,841 81,814Q81,787 100,768L142,726L104,686L658,132Q670,120 687,120Q704,120 716,132L828,244Q840,256 840,273Q840,290 828,302L272,856ZM444,460L216,686L274,744L500,516L444,460Z"
private const val SharedPdfAndroidKeyboardPath = "M160,760Q127,760 103.5,736.5Q80,713 80,680L80,280Q80,247 103.5,223.5Q127,200 160,200L800,200Q833,200 856.5,223.5Q880,247 880,280L880,680Q880,713 856.5,736.5Q833,760 800,760L160,760ZM160,680L800,680Q800,680 800,680Q800,680 800,680L800,280Q800,280 800,280Q800,280 800,280L160,280Q160,280 160,280Q160,280 160,280L160,680Q160,680 160,680Q160,680 160,680ZM320,640L640,640L640,560L320,560L320,640ZM200,520L280,520L280,440L200,440L200,520ZM320,520L400,520L400,440L320,440L320,520ZM440,520L520,520L520,440L440,440L440,520ZM560,520L640,520L640,440L560,440L560,520ZM680,520L760,520L760,440L680,440L680,520ZM200,400L280,400L280,320L200,320L200,400ZM320,400L400,400L400,320L320,320L320,400ZM440,400L520,400L520,320L440,320L440,400ZM560,400L640,400L640,320L560,320L560,400ZM680,400L760,400L760,320L680,320L680,400ZM160,680Q160,680 160,680Q160,680 160,680L160,280Q160,280 160,280Q160,280 160,280L160,280Q160,280 160,280Q160,280 160,280L160,680Q160,680 160,680Q160,680 160,680Z"
private const val SharedPdfAndroidTextSelectStartPath = "M440,840L440,760L520,760L520,840L440,840ZM440,200L440,120L520,120L520,200L440,200ZM600,840L600,760L680,760L680,840L600,840ZM600,200L600,120L680,120L680,200L600,200ZM760,840L760,760L840,760L840,840L760,840ZM760,680L760,600L840,600L840,680L760,680ZM760,520L760,440L840,440L840,520L760,520ZM760,360L760,280L840,280L840,360L760,360ZM760,200L760,120L840,120L840,200L760,200ZM120,840L120,760L200,760L200,200L120,200L120,120L360,120L360,200L280,200L280,760L360,760L360,840L120,840Z"
private const val SharedPdfAndroidEraserPath = "M690,720L880,720L880,800L610,800L690,720ZM190,800L105,715Q82,692 81.5,658Q81,624 104,600L544,144Q567,120 600.5,120Q634,120 657,143L856,342Q879,365 879,399Q879,433 856,456L520,800L190,800ZM486,720L800,398Q800,398 800,398Q800,398 800,398L602,200Q602,200 602,200Q602,200 602,200L160,656Q160,656 160,656Q160,656 160,656L224,720L486,720ZM480,480L480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480L480,480Q480,480 480,480Q480,480 480,480Z"
private const val SharedPdfAndroidDeletePath = "M280,840Q247,840 223.5,816.5Q200,793 200,760L200,240L160,240L160,160L360,160L360,120L600,120L600,160L800,160L800,240L760,240L760,760Q760,793 736.5,816.5Q713,840 680,840L280,840ZM680,240L280,240L280,760Q280,760 280,760Q280,760 280,760L680,760Q680,760 680,760Q680,760 680,760L680,240ZM360,680L440,680L440,320L360,320L360,680ZM520,680L600,680L600,320L520,320L520,680ZM280,240L280,240L280,760Q280,760 280,760Q280,760 280,760L280,760Q280,760 280,760Q280,760 280,760L280,240Z"
private const val SharedPdfAndroidTouchAppPath = "M419,880Q391,880 366.5,868Q342,856 325,834L107,557L126,537Q146,516 174,512Q202,508 226,523L300,568L300,240Q300,223 311.5,211.5Q323,200 340,200Q357,200 369,211.5Q381,223 381,240L381,712L284,652L388,785Q394,792 402,796Q410,800 419,800L640,800Q673,800 696.5,776.5Q720,753 720,720L720,560Q720,543 708.5,531.5Q697,520 680,520L461,520L461,440L680,440Q730,440 765,475Q800,510 800,560L800,720Q800,786 753,833Q706,880 640,880L419,880ZM167,340Q154,318 147,292.5Q140,267 140,240Q140,157 198.5,98.5Q257,40 340,40Q423,40 481.5,98.5Q540,157 540,240Q540,267 533,292.5Q526,318 513,340L444,300Q452,286 456,271.5Q460,257 460,240Q460,190 425,155Q390,120 340,120Q290,120 255,155Q220,190 220,240Q220,257 224,271.5Q228,286 236,300L167,340ZM502,620L502,620L502,620L502,620Q502,620 502,620Q502,620 502,620L502,620Q502,620 502,620Q502,620 502,620L502,620Q502,620 502,620Q502,620 502,620L502,620L502,620Z"
private const val SharedPdfAndroidUndoPath = "M280,760L280,680L564,680Q627,680 673.5,640Q720,600 720,540Q720,480 673.5,440Q627,400 564,400L312,400L416,504L360,560L160,360L360,160L416,216L312,320L564,320Q661,320 730.5,383Q800,446 800,540Q800,634 730.5,697Q661,760 564,760L280,760Z"
private const val SharedPdfAndroidRedoPath = "M396,760Q299,760 229.5,697Q160,634 160,540Q160,446 229.5,383Q299,320 396,320L648,320L544,216L600,160L800,360L600,560L544,504L648,400L396,400Q333,400 286.5,440Q240,480 240,540Q240,600 286.5,640Q333,680 396,680L680,680L680,760L396,760Z"

@Composable
private fun SharedPdfAndroidPathIcon(
    pathData: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val imageVector = remember(pathData) {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.White)
            )
        }.build()
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

@Composable
private fun SharedPdfModeDockButton(
    tooltip: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val background by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (selected) 0.15f else 0f),
        label = "shared_pdf_mode_background"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.76f),
        label = "shared_pdf_mode_tint"
    )

    ReaderTooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(background)
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center
        ) {
            icon(tint)
        }
    }
}

@Composable
private fun SharedPdfInteractionDivider() {
    Box(
        modifier = Modifier
            .height(20.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.2f))
    )
}

@Composable
private fun SharedPdfToolButton(
    tool: PdfInkTool,
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    onToolSelected: (PdfInkTool) -> Unit
) {
    val selected = tool == selectedTool
    val toolColor = if (selected) {
        selectedColor
    } else {
        SharedPdfAnnotationDefaults.configFor(tool).colorArgb
    }
    SharedPdfToolButton(
        tool = tool,
        selected = selected,
        color = toolColor,
        strokeWidth = strokeWidth,
        onClick = { onToolSelected(tool) }
    )
}

@Composable
private fun SharedPdfToolButton(
    tool: PdfInkTool,
    selected: Boolean,
    color: Int?,
    strokeWidth: Float,
    onClick: () -> Unit
) {
    val toolColor = color ?: SharedPdfAnnotationDefaults.configFor(tool).colorArgb
    val tooltip = sharedPdfToolLabel(tool)
    ReaderTooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (selected) 0.16f else 0f))
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center
        ) {
            when (tool) {
                PdfInkTool.TEXT -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidKeyboardPath,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.ERASER -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidEraserPath,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.HIGHLIGHTER,
                PdfInkTool.HIGHLIGHTER_ROUND -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidMarkerPath,
                    tint = Color(toolColor).copy(alpha = 1f),
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.PEN,
                PdfInkTool.FOUNTAIN_PEN,
                PdfInkTool.PENCIL -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidPenPath,
                    tint = Color(toolColor).copy(alpha = 1f),
                    modifier = Modifier.size(20.dp)
                )
                PdfInkTool.NONE -> SharedPdfAndroidPathIcon(
                    pathData = SharedPdfAndroidTouchAppPath,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun sharedPdfToolLabel(tool: PdfInkTool): String = when (tool) {
    PdfInkTool.PEN -> readerString("content_desc_pen", "Pen")
    PdfInkTool.FOUNTAIN_PEN -> readerString("desktop_fountain_pen", "Fountain pen")
    PdfInkTool.PENCIL -> readerString("desktop_pencil", "Pencil")
    PdfInkTool.HIGHLIGHTER -> readerString("content_desc_highlighter", "Highlighter")
    PdfInkTool.HIGHLIGHTER_ROUND -> readerString("desktop_round_highlighter", "Round highlighter")
    PdfInkTool.TEXT -> readerString("desktop_text_note", "Text note")
    PdfInkTool.ERASER -> readerString("content_desc_eraser", "Eraser")
    PdfInkTool.NONE -> readerString("label_none", "None")
}

@Composable
private fun SharedTextStyleChoiceButton(
    selected: Boolean,
    selectedBackground: Color,
    unselectedBackground: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) selectedBackground else unselectedBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SharedTextColorSwatches(
    palette: List<Int>,
    selectedArgb: Int,
    allowTransparent: Boolean,
    dark: Boolean,
    onColorSelected: (Int) -> Unit
) {
    val borderBase = if (dark) Color.White else Color.Black
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        palette
            .filter { allowTransparent || !it.isTransparentArgb() }
            .forEach { argb ->
                val selected = argb == selectedArgb || (argb.isTransparentArgb() && selectedArgb.isTransparentArgb())
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (argb.isTransparentArgb()) Color.Transparent else Color(argb).copy(alpha = 1f))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) borderBase.copy(alpha = 0.88f) else borderBase.copy(alpha = 0.22f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(argb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (argb.isTransparentArgb()) {
                        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                            drawCircle(color = borderBase.copy(alpha = 0.18f))
                            drawLine(
                                color = borderBase.copy(alpha = 0.68f),
                                start = Offset(size.width * 0.22f, size.height * 0.78f),
                                end = Offset(size.width * 0.78f, size.height * 0.22f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun DockCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    showBackground: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    ReaderTooltipIconButton(
        tooltip = contentDescription.orEmpty(),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (showBackground && enabled) 0.10f else 0f))
                .then(
                    if (contentDescription == null) {
                        Modifier
                    } else {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun SharedPdfPenIcon(
    tool: PdfInkTool,
    color: Color,
    inkColor: Color,
    isSelected: Boolean,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
    showHighlighterSnap: Boolean = false
) {
    val animatedBodyColor by animateColorAsState(targetValue = color, label = "shared_pen_color")
    val animatedInkColor by animateColorAsState(targetValue = inkColor, label = "shared_ink_color")
    val inkProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        label = "shared_ink_progress"
    )

    Canvas(modifier = modifier) {
        val penWidth = size.width * 0.65f
        val startX = (size.width - penWidth) / 2f
        val tipHeight = size.height * 0.45f
        val collarHeight = size.height * 0.15f
        val bodyHeight = size.height * 0.35f
        val topPadding = size.height * 0.05f
        val tipRect = Rect(Offset(startX, topPadding), Size(penWidth, tipHeight))
        val collarRect = Rect(Offset(startX, topPadding + tipHeight), Size(penWidth, collarHeight))
        val bodyRect = Rect(Offset(startX, topPadding + tipHeight + collarHeight), Size(penWidth, bodyHeight))

        drawMatteCylinder(Color(0xFF454545), bodyRect)
        when (tool) {
            PdfInkTool.FOUNTAIN_PEN -> {
                drawMatteCylinder(animatedBodyColor, collarRect)
                drawFountainNib(Color(0xFFCFD8DC), animatedBodyColor, tipRect)
            }
            PdfInkTool.PENCIL -> {
                drawMatteCylinder(animatedBodyColor, collarRect)
                drawPencilHead(animatedBodyColor, tipRect)
            }
            PdfInkTool.HIGHLIGHTER -> drawHighlighterChiselParts(animatedBodyColor, collarRect, tipRect)
            PdfInkTool.HIGHLIGHTER_ROUND -> drawHighlighterRoundParts(animatedBodyColor, collarRect, tipRect)
            PdfInkTool.PEN -> {
                drawMatteCylinder(animatedBodyColor, collarRect)
                drawMarkerHead(animatedBodyColor, tipRect)
            }
            PdfInkTool.NONE,
            PdfInkTool.TEXT,
            PdfInkTool.ERASER -> Unit
        }

        if (inkProgress > 0.01f) {
            drawInkPreview(
                tool = tool,
                color = animatedInkColor,
                progress = inkProgress,
                startPoint = Offset(size.width / 2f, topPadding - 1f),
                strokeWidth = strokeWidth
            )
        }
        if (showHighlighterSnap && tool.isDesktopHighlighter) {
            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(size.width * 0.18f, size.height * 0.9f),
                end = Offset(size.width * 0.82f, size.height * 0.9f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

fun Offset.toSharedPdfPoint(size: IntSize, timestamp: Long): PdfPagePoint {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    return PdfPagePoint(
        x = (x / width).coerceIn(0f, 1f),
        y = (y / height).coerceIn(0f, 1f),
        timestamp = timestamp
    )
}

fun pageBoundsFromSharedPdfPoint(point: Offset, size: IntSize): PdfPageBounds {
    val width = size.width.coerceAtLeast(1)
    val height = size.height.coerceAtLeast(1)
    val left = (point.x / width).coerceIn(0f, 0.92f)
    val top = (point.y / height).coerceIn(0f, 0.95f)
    return PdfPageBounds(
        left = left,
        top = top,
        right = (left + 0.32f).coerceAtMost(1f),
        bottom = (top + 0.08f).coerceAtMost(1f)
    )
}

fun SharedPdfAnnotation.sharedPdfHitTest(
    point: Offset,
    size: IntSize,
    lastPoint: Offset? = null,
    eraserStrokeWidth: Float = SharedPdfAnnotationDefaults.configFor(PdfInkTool.ERASER).strokeWidth
): Boolean {
    val pageWidthPx = size.width.coerceAtLeast(1).toFloat()
    val pageAspectRatio = size.width.toFloat() / size.height.coerceAtLeast(1).toFloat()
    return SharedPdfInkRenderer.isAnnotationHit(
        annotation = this,
        hitPoint = point.toSharedPdfPoint(size, timestamp = 0L),
        pageWidthPx = pageWidthPx,
        pageAspectRatio = pageAspectRatio,
        eraserStrokeWidth = eraserStrokeWidth,
        lastHitPoint = lastPoint?.toSharedPdfPoint(size, timestamp = 0L)
    )
}

fun SharedPdfEmbeddedAnnotation.sharedPdfEmbeddedHitTest(
    point: Offset,
    size: IntSize,
    tolerancePx: Float = 24f
): Boolean {
    val rect = bounds
    val left = (rect.left * size.width) - tolerancePx
    val top = (rect.top * size.height) - tolerancePx
    val right = (rect.right * size.width) + tolerancePx
    val bottom = (rect.bottom * size.height) + tolerancePx
    return point.x in left..right && point.y in top..bottom
}

private fun DrawScope.drawInkRenderData(
    renderData: SharedPdfInkRenderData,
    selectedOutline: Boolean = false
) {
    when (renderData) {
        is SharedPdfInkRenderData.Standard -> {
            drawPath(
                path = renderData.path,
                color = if (selectedOutline) Color(0xFF64B5F6).copy(alpha = 0.30f) else renderData.color,
                style = Stroke(
                    width = if (selectedOutline) renderData.strokeWidthPx + 7f else renderData.strokeWidthPx,
                    cap = renderData.cap,
                    join = StrokeJoin.Round
                ),
                blendMode = if (selectedOutline) BlendMode.SrcOver else renderData.blendMode
            )
        }
        is SharedPdfInkRenderData.Fountain -> {
            drawPath(
                path = renderData.path,
                color = if (selectedOutline) Color(0xFF64B5F6).copy(alpha = 0.30f) else renderData.color,
                style = Fill
            )
        }
        is SharedPdfInkRenderData.Pencil -> {
            val color = if (selectedOutline) {
                Color(0xFF64B5F6).copy(alpha = 0.28f)
            } else {
                renderData.color.copy(alpha = renderData.color.alpha * renderData.velocityAlpha)
            }
            val width = if (selectedOutline) renderData.strokeWidthPx + 7f else renderData.strokeWidthPx
            drawPath(
                path = renderData.path,
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            if (!selectedOutline) {
                translate(left = 0.7f, top = 0.4f) {
                    drawPath(
                        path = renderData.path,
                        color = renderData.color.copy(alpha = renderData.color.alpha * 0.18f),
                        style = Stroke(width = (width * 0.55f).coerceAtLeast(0.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMatteCylinder(color: Color, rect: Rect) {
    drawRect(
        brush = Brush.horizontalGradient(
            0.0f to color.darker(0.6f),
            0.3f to color.lighter(0.1f),
            0.5f to color,
            0.85f to color.darker(0.5f),
            1.0f to color.darker(0.7f),
            startX = rect.left,
            endX = rect.right
        ),
        topLeft = rect.topLeft,
        size = rect.size
    )
}

private fun DrawScope.drawFountainNib(metalColor: Color, inkColor: Color, rect: Rect) {
    val centerX = rect.left + rect.width / 2f
    val path = Path().apply {
        moveTo(rect.left + rect.width * 0.15f, rect.bottom)
        lineTo(rect.right - rect.width * 0.15f, rect.bottom)
        cubicTo(rect.right - rect.width * 0.1f, rect.bottom - rect.height * 0.6f, rect.right, rect.top + rect.height * 0.2f, centerX, rect.top)
        cubicTo(rect.left, rect.top + rect.height * 0.2f, rect.left + rect.width * 0.1f, rect.bottom - rect.height * 0.6f, rect.left + rect.width * 0.15f, rect.bottom)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            0.0f to metalColor.darker(0.6f),
            0.4f to Color.White,
            0.6f to metalColor,
            1.0f to metalColor.darker(0.6f),
            startX = rect.left,
            endX = rect.right
        )
    )
    drawCircle(Color.Black.copy(alpha = 0.7f), radius = rect.width * 0.06f, center = Offset(centerX, rect.bottom - rect.height * 0.5f))
    drawLine(Color.Black.copy(alpha = 0.6f), start = Offset(centerX, rect.top), end = Offset(centerX, rect.bottom - rect.height * 0.5f), strokeWidth = 1.2f)
    drawCircle(inkColor.copy(alpha = 0.5f), radius = rect.width * 0.04f, center = Offset(centerX, rect.bottom - rect.height * 0.5f))
}

private fun DrawScope.drawMarkerHead(inkColor: Color, rect: Rect) {
    val centerX = rect.left + rect.width / 2f
    val plasticColor = Color(0xFF616161)
    val coneHeight = rect.height * 0.8f
    val conePath = Path().apply {
        moveTo(rect.left, rect.bottom)
        lineTo(rect.right, rect.bottom)
        lineTo(centerX + rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        lineTo(centerX - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        close()
    }
    drawPath(
        path = conePath,
        brush = Brush.horizontalGradient(
            0.0f to plasticColor.darker(0.5f),
            0.5f to plasticColor,
            1.0f to plasticColor.darker(0.5f),
            startX = rect.left,
            endX = rect.right
        )
    )
    val tipPath = Path().apply {
        moveTo(centerX - rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        lineTo(centerX + rect.width * 0.15f, rect.top + (rect.height - coneHeight))
        quadraticTo(centerX, rect.top, centerX, rect.top)
        close()
    }
    drawPath(path = tipPath, color = inkColor)
}

private fun DrawScope.drawPencilHead(inkColor: Color, rect: Rect) {
    val centerX = rect.left + rect.width / 2f
    val woodColor = Color(0xFFFFCC80)
    val woodPath = Path().apply {
        moveTo(rect.left, rect.bottom)
        val scallops = 3
        val step = rect.width / scallops
        for (i in 0 until scallops) {
            quadraticTo(
                rect.left + i * step + step / 2f,
                rect.bottom - rect.width * 0.1f,
                rect.left + (i + 1) * step,
                rect.bottom
            )
        }
        lineTo(centerX + rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(centerX - rect.width * 0.12f, rect.top + rect.height * 0.25f)
        close()
    }
    drawPath(
        path = woodPath,
        brush = Brush.horizontalGradient(
            0.0f to woodColor.darker(0.3f),
            0.5f to woodColor.lighter(0.1f),
            1.0f to woodColor.darker(0.3f),
            startX = rect.left,
            endX = rect.right
        )
    )
    val leadPath = Path().apply {
        moveTo(centerX - rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(centerX + rect.width * 0.12f, rect.top + rect.height * 0.25f)
        lineTo(centerX, rect.top)
        close()
    }
    drawPath(path = leadPath, color = inkColor)
}

private fun DrawScope.drawHighlighterChiselParts(color: Color, collarRect: Rect, tipRect: Rect) {
    drawMatteCylinder(color, collarRect)
    val bodyColor = Color(0xFF454545)
    val neckHeight = tipRect.height * 0.65f
    val inkTipHeight = tipRect.height - neckHeight
    val neckTopY = tipRect.bottom - neckHeight
    val centerX = tipRect.center.x
    val neckTopHalfWidth = tipRect.width * 0.25f
    val neckPath = Path().apply {
        moveTo(tipRect.left, tipRect.bottom)
        lineTo(tipRect.right, tipRect.bottom)
        lineTo(centerX + neckTopHalfWidth, neckTopY)
        lineTo(centerX - neckTopHalfWidth, neckTopY)
        close()
    }
    drawPath(
        path = neckPath,
        brush = Brush.horizontalGradient(
            0.0f to bodyColor.darker(0.6f),
            0.3f to bodyColor.lighter(0.1f),
            0.5f to bodyColor,
            0.85f to bodyColor.darker(0.5f),
            1.0f to bodyColor.darker(0.7f),
            startX = tipRect.left,
            endX = tipRect.right
        )
    )

    val slantDrop = inkTipHeight * 0.4f
    val tipPath = Path().apply {
        moveTo(centerX - neckTopHalfWidth, neckTopY)
        lineTo(centerX + neckTopHalfWidth, neckTopY)
        lineTo(centerX + neckTopHalfWidth, tipRect.top + slantDrop)
        lineTo(centerX - neckTopHalfWidth, tipRect.top)
        close()
    }
    drawPath(
        path = tipPath,
        brush = Brush.horizontalGradient(
            0.0f to color.darker(0.8f),
            0.5f to color,
            1.0f to color.darker(0.8f),
            startX = centerX - neckTopHalfWidth,
            endX = centerX + neckTopHalfWidth
        )
    )
}

private fun DrawScope.drawHighlighterRoundParts(color: Color, collarRect: Rect, tipRect: Rect) {
    drawMatteCylinder(color, collarRect)
    val bodyColor = Color(0xFF454545)
    val neckHeight = tipRect.height * 0.65f
    val neckTopY = tipRect.bottom - neckHeight
    val centerX = tipRect.center.x
    val neckTopHalfWidth = tipRect.width * 0.25f
    val neckPath = Path().apply {
        moveTo(tipRect.left, tipRect.bottom)
        lineTo(tipRect.right, tipRect.bottom)
        lineTo(centerX + neckTopHalfWidth, neckTopY)
        lineTo(centerX - neckTopHalfWidth, neckTopY)
        close()
    }
    drawPath(
        path = neckPath,
        brush = Brush.horizontalGradient(
            0.0f to bodyColor.darker(0.6f),
            0.3f to bodyColor.lighter(0.1f),
            0.5f to bodyColor,
            0.85f to bodyColor.darker(0.5f),
            1.0f to bodyColor.darker(0.7f),
            startX = tipRect.left,
            endX = tipRect.right
        )
    )
    val tipHeight = tipRect.height - neckHeight
    val domeRect = Rect(
        left = centerX - neckTopHalfWidth,
        top = neckTopY - tipHeight,
        right = centerX + neckTopHalfWidth,
        bottom = neckTopY
    )
    val domePath = Path().apply {
        moveTo(domeRect.left, domeRect.bottom)
        lineTo(domeRect.right, domeRect.bottom)
        arcTo(domeRect, startAngleDegrees = 0f, sweepAngleDegrees = -180f, forceMoveTo = false)
        close()
    }
    drawPath(
        path = domePath,
        brush = Brush.radialGradient(
            colors = listOf(color.lighter(0.3f), color, color.darker(0.6f)),
            center = Offset(domeRect.center.x - domeRect.width * 0.2f, domeRect.top + domeRect.height * 0.4f),
            radius = domeRect.width
        )
    )
}

private fun DrawScope.drawInkPreview(
    tool: PdfInkTool,
    color: Color,
    progress: Float,
    startPoint: Offset,
    strokeWidth: Float
) {
    val path = Path().apply {
        moveTo(startPoint.x, startPoint.y)
        if (tool.isHighlighter) {
            val waveWidth = 46f
            cubicTo(startPoint.x + waveWidth * 0.35f, startPoint.y - 12f, startPoint.x + waveWidth * 0.65f, startPoint.y + 12f, startPoint.x + waveWidth, startPoint.y)
        } else {
            cubicTo(startPoint.x + 22f, startPoint.y - 24f, startPoint.x - 22f, startPoint.y - 52f, startPoint.x - 9f, startPoint.y - 28f)
            cubicTo(startPoint.x - 3f, startPoint.y - 8f, startPoint.x + 32f, startPoint.y - 16f, startPoint.x + 44f, startPoint.y - 34f)
        }
    }
    val width = SharedPdfInkRenderer.effectiveStrokeWidthPx(strokeWidth, pageWidthPx = 700f)
        .coerceIn(if (tool.isHighlighter) 5f else 1.2f, if (tool.isHighlighter) 16f else 5f)
    drawPath(
        path = path,
        color = color.copy(alpha = color.alpha * progress),
        style = Stroke(
            width = width,
            cap = if (tool == PdfInkTool.HIGHLIGHTER) StrokeCap.Butt else StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = if (tool.isHighlighter) BlendMode.SrcOver else BlendMode.SrcOver
    )
}

private val PdfInkTool.isDesktopPenTool: Boolean
    get() = this == PdfInkTool.FOUNTAIN_PEN || this == PdfInkTool.PEN || this == PdfInkTool.PENCIL

private val PdfInkTool.isDesktopHighlighter: Boolean
    get() = this == PdfInkTool.HIGHLIGHTER || this == PdfInkTool.HIGHLIGHTER_ROUND

private val PdfInkTool.isHighlighter: Boolean
    get() = isDesktopHighlighter

private fun Int.withSharedPdfAnnotationAlpha(alpha: Float): Int {
    return Color(this).copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
}

private val SharedPdfAnnotation.textDecoration: TextDecoration
    get() {
        val decorations = mutableListOf<TextDecoration>()
        if (isUnderline) decorations += TextDecoration.Underline
        if (isStrikeThrough) decorations += TextDecoration.LineThrough
        return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
    }

private val SharedPdfTextStyleConfig.textDecoration: TextDecoration
    get() {
        val decorations = mutableListOf<TextDecoration>()
        if (isUnderline) decorations += TextDecoration.Underline
        if (isStrikeThrough) decorations += TextDecoration.LineThrough
        return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
    }

private fun SharedPdfAnnotation.sharedPdfTextFontFamily(): FontFamily? {
    return sharedPdfFontFamily(fontName ?: fontPath)
}

private fun SharedPdfTextResizeHandle.centerOffset(
    leftPx: Float,
    topPx: Float,
    widthPx: Float,
    heightPx: Float
): Offset {
    return when (this) {
        SharedPdfTextResizeHandle.TOP_LEFT -> Offset(leftPx, topPx)
        SharedPdfTextResizeHandle.TOP_CENTER -> Offset(leftPx + widthPx / 2f, topPx)
        SharedPdfTextResizeHandle.TOP_RIGHT -> Offset(leftPx + widthPx, topPx)
        SharedPdfTextResizeHandle.RIGHT_CENTER -> Offset(leftPx + widthPx, topPx + heightPx / 2f)
        SharedPdfTextResizeHandle.BOTTOM_RIGHT -> Offset(leftPx + widthPx, topPx + heightPx)
        SharedPdfTextResizeHandle.BOTTOM_CENTER -> Offset(leftPx + widthPx / 2f, topPx + heightPx)
        SharedPdfTextResizeHandle.BOTTOM_LEFT -> Offset(leftPx, topPx + heightPx)
        SharedPdfTextResizeHandle.LEFT_CENTER -> Offset(leftPx, topPx + heightPx / 2f)
    }
}

private fun SharedPdfTextStyleConfig.withFontPreset(preset: SharedPdfTextFontPreset): SharedPdfTextStyleConfig {
    return copy(
        fontName = preset.name.takeUnless { it == "Default" },
        fontPath = preset.fontPath
    )
}

private fun SharedPdfTextStyleConfig.displayFontName(): String {
    return fontName
        ?: fontPath?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "Default"
}

private fun sharedPdfFontFamily(nameOrPath: String?): FontFamily? {
    return when (nameOrPath) {
        "Merriweather",
        "Lora",
        "asset:fonts/merriweather.ttf",
        "asset:fonts/lora.ttf" -> FontFamily.Serif
        "Roboto Mono",
        "asset:fonts/roboto_mono.ttf" -> FontFamily.Monospace
        "Lato",
        "Lexend",
        "asset:fonts/lato.ttf",
        "asset:fonts/lexend.ttf" -> FontFamily.SansSerif
        else -> null
    }
}

private fun Int.isTransparentArgb(): Boolean {
    return (this ushr 24) == 0
}

private fun PdfPageBounds.topLeft(canvasSize: IntSize): Offset {
    return Offset(left * canvasSize.width, top * canvasSize.height)
}

private fun PdfPageBounds.size(canvasSize: IntSize): Size {
    return Size((right - left) * canvasSize.width, (bottom - top) * canvasSize.height)
}

private fun Color.darker(factor: Float = 0.7f): Color {
    return Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha
    )
}

private fun Color.lighter(factor: Float = 0.3f): Color {
    return Color(
        red = red + (1 - red) * factor,
        green = green + (1 - green) * factor,
        blue = blue + (1 - blue) * factor,
        alpha = alpha
    )
}
