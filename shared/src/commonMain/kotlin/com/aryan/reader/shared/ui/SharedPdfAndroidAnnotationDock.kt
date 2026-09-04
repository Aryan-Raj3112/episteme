package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAnnotationHighlighterTools
import com.aryan.reader.shared.pdf.SharedPdfAnnotationPenTools
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationDockFullBar
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationEraserActive
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationHighlighterActive
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationPenActive
import com.aryan.reader.shared.pdf.isSharedPdfAnnotationTextActive
import com.aryan.reader.shared.pdf.resolveSharedPdfAnnotationDockToolClick

/**
 * Android-parity PDF annotation dock.
 *
 * Benchmark: `app/.../pdf/AnnotationDock.kt` (behavior + layout).
 * Shared-first so iOS renders the same toolbar as Android:
 * close, minimize, stylus-only, pen / highlighter / text / eraser,
 * undo + redo. Sticky (TOP/BOTTOM) renders full-width rectangular with no
 * shadow; floating renders as a pill; floating + minimized collapses to the
 * 48dp circle. Minimized dims tools to 30% and disables them, matching
 * Android's `toolsAlpha` + `enabled = canX && !isMinimized` + drawing gate.
 */
@Composable
fun SharedPdfAndroidAnnotationDock(
    selectedTool: PdfInkTool,
    activePenColor: Color,
    activeHighlighterColor: Color,
    onToolClick: (PdfInkTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    lastPenTool: PdfInkTool,
    modifier: Modifier = Modifier,
    lastHighlighterTool: PdfInkTool = PdfInkTool.HIGHLIGHTER,
    isSticky: Boolean = false,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    isStylusOnlyMode: Boolean,
    onToggleStylusOnlyMode: () -> Unit,
) {
    val showFullDock = isSharedPdfAnnotationDockFullBar(isSticky, isMinimized)

    val dockHeight = 56.dp
    val buttonSize = 36.dp
    val iconSize = 20.dp
    val spacing = 8.dp
    val horizontalPadding = 12.dp

    if (showFullDock) {
        val shape = if (isSticky) RectangleShape else RoundedCornerShape(percent = 50)
        Surface(
            color = Color(0xFF1E1E1E),
            shape = shape,
            shadowElevation = if (isSticky) 0.dp else 8.dp,
            modifier = modifier.height(dockHeight),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = readerString("content_desc_close_edit_mode", "Close edit mode"),
                        tint = Color.White,
                        modifier = Modifier.size(iconSize),
                    )
                }

                val visIcon = if (isMinimized) Icons.Default.VisibilityOff else Icons.Default.Visibility
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleMinimize),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = visIcon,
                        contentDescription = readerString("content_desc_toggle_visibility", "Toggle visibility"),
                        tint = Color.White,
                        modifier = Modifier.size(iconSize),
                    )
                }

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.2f)),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.graphicsLayer { alpha = if (isMinimized) 0.3f else 1f },
                ) {
                    if (!isMinimized) {
                        val iconVector = if (isStylusOnlyMode) Icons.Default.DoNotTouch else Icons.Default.TouchApp
                        val iconTint = if (isStylusOnlyMode) Color(0xFFE57373) else Color.White
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isStylusOnlyMode) 0.15f else 0f))
                                .clickable(onClick = onToggleStylusOnlyMode),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = readerString("content_desc_stylus_only_mode", "Stylus-only mode"),
                                tint = iconTint,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }

                    val isPenActive = isSharedPdfAnnotationPenActive(selectedTool, isMinimized)
                    SharedPdfDockIcon(
                        tool = PdfInkTool.PEN,
                        isActive = isPenActive,
                        tintColor = if (isMinimized) Color.Gray else activePenColor,
                        description = readerString("content_desc_pen", "Pen"),
                        sizeDp = buttonSize,
                        iconSizeDp = iconSize,
                        onClick = {
                            if (!isMinimized) {
                                onToolClick(
                                    resolveSharedPdfAnnotationDockToolClick(
                                        selectedTool = selectedTool,
                                        clickedGroup = PdfInkTool.PEN,
                                        lastPenTool = lastPenTool,
                                        lastHighlighterTool = lastHighlighterTool,
                                    ),
                                )
                            }
                        },
                    )

                    val isHighlighterActive = isSharedPdfAnnotationHighlighterActive(selectedTool, isMinimized)
                    SharedPdfDockIcon(
                        tool = PdfInkTool.HIGHLIGHTER,
                        isActive = isHighlighterActive,
                        tintColor = if (isMinimized) Color.Gray else activeHighlighterColor.copy(alpha = 1f),
                        description = readerString("content_desc_highlighter", "Highlighter"),
                        sizeDp = buttonSize,
                        iconSizeDp = iconSize,
                        onClick = {
                            if (!isMinimized) {
                                onToolClick(
                                    resolveSharedPdfAnnotationDockToolClick(
                                        selectedTool = selectedTool,
                                        clickedGroup = PdfInkTool.HIGHLIGHTER,
                                        lastPenTool = lastPenTool,
                                        lastHighlighterTool = lastHighlighterTool,
                                    ),
                                )
                            }
                        },
                    )

                    SharedPdfDockIcon(
                        tool = PdfInkTool.TEXT,
                        isActive = isSharedPdfAnnotationTextActive(selectedTool, isMinimized),
                        tintColor = if (isMinimized) Color.Gray else Color.White,
                        description = readerString("content_desc_text", "Text"),
                        sizeDp = buttonSize,
                        iconSizeDp = iconSize,
                        onClick = { if (!isMinimized) onToolClick(PdfInkTool.TEXT) },
                    )

                    SharedPdfDockIcon(
                        tool = PdfInkTool.ERASER,
                        isActive = isSharedPdfAnnotationEraserActive(selectedTool, isMinimized),
                        tintColor = if (isMinimized) Color.Gray else Color.White,
                        description = readerString("content_desc_eraser", "Eraser"),
                        sizeDp = buttonSize,
                        iconSizeDp = iconSize,
                        onClick = { if (!isMinimized) onToolClick(PdfInkTool.ERASER) },
                    )
                }

                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(enabled = canUndo && !isMinimized, onClick = onUndo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = readerString("content_desc_undo", "Undo"),
                        tint = if (canUndo && !isMinimized) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(iconSize),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(enabled = canRedo && !isMinimized, onClick = onRedo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = readerString("content_desc_redo", "Redo"),
                        tint = if (canRedo && !isMinimized) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    } else {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = modifier.size(48.dp),
        ) {
            Box(
                modifier = Modifier.clickable(onClick = onToggleMinimize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = readerString("content_desc_show_dock", "Show dock"),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SharedPdfDockIcon(
    tool: PdfInkTool,
    isActive: Boolean,
    tintColor: Color,
    description: String,
    sizeDp: androidx.compose.ui.unit.Dp,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val backgroundAlpha = if (isActive) 0.15f else 0f
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .semantics { selected = isActive }
            .testTag("DockItem_$description")
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (tool) {
            PdfInkTool.TEXT -> SharedPdfAndroidPathIcon(
                pathData = SharedPdfAndroidKeyboardPath,
                tint = tintColor,
                modifier = Modifier.size(iconSizeDp),
            )
            PdfInkTool.ERASER -> SharedPdfAndroidPathIcon(
                pathData = SharedPdfAndroidEraserPath,
                tint = tintColor,
                modifier = Modifier.size(iconSizeDp),
            )
            PdfInkTool.HIGHLIGHTER,
            PdfInkTool.HIGHLIGHTER_ROUND -> SharedPdfAndroidPathIcon(
                pathData = SharedPdfAndroidMarkerPath,
                tint = tintColor,
                modifier = Modifier.size(iconSizeDp),
            )
            PdfInkTool.PEN,
            PdfInkTool.FOUNTAIN_PEN,
            PdfInkTool.PENCIL -> SharedPdfAndroidPathIcon(
                pathData = SharedPdfAndroidPenPath,
                tint = tintColor,
                modifier = Modifier.size(iconSizeDp),
            )
            PdfInkTool.NONE -> SharedPdfAndroidPathIcon(
                pathData = SharedPdfAndroidTouchAppPath,
                tint = tintColor,
                modifier = Modifier.size(iconSizeDp),
            )
        }
    }
}

internal val SharedPdfAnnotationPenToolsForDock: Set<PdfInkTool>
    get() = SharedPdfAnnotationPenTools

internal val SharedPdfAnnotationHighlighterToolsForDock: Set<PdfInkTool>
    get() = SharedPdfAnnotationHighlighterTools

/**
 * Android-parity tool-settings popup content.
 *
 * Benchmark: `ToolSettingsPopup.kt` — pen / highlighter type selector,
 * thickness + opacity sliders, 6-color palette with long-press spectrum,
 * straight-line switch for highlighters, eraser size preview.
 * Shared rendering lives in [SharedPdfAnnotationToolSettingsPanel]; this
 * wrapper picks the panel from the selected tool like Android does
 * (`isHighlighter` / `isEraser`) so callers don't need the private enum.
 */
@Composable
fun SharedPdfAndroidToolSettingsPopup(
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    actualToolConfigs: Map<PdfInkTool, com.aryan.reader.shared.pdf.PdfToolConfig> = emptyMap(),
    penPalette: List<Int>,
    highlighterPalette: List<Int>,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onPaletteChange: (List<Int>) -> Unit,
    isHighlighterSnapEnabled: Boolean,
    onHighlighterSnapChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEraser = selectedTool == PdfInkTool.ERASER
    val isHighlighter = selectedTool in SharedPdfAnnotationHighlighterTools
    val panel = when {
        isEraser -> SharedPdfAnnotationSettingsPanel.ERASER
        isHighlighter -> SharedPdfAnnotationSettingsPanel.HIGHLIGHTER
        else -> SharedPdfAnnotationSettingsPanel.PEN
    }
    val panelTools = when (panel) {
        SharedPdfAnnotationSettingsPanel.PEN -> listOf(
            PdfInkTool.FOUNTAIN_PEN,
            PdfInkTool.PEN,
            PdfInkTool.PENCIL,
        )
        SharedPdfAnnotationSettingsPanel.HIGHLIGHTER -> listOf(
            PdfInkTool.HIGHLIGHTER,
            PdfInkTool.HIGHLIGHTER_ROUND,
        )
        SharedPdfAnnotationSettingsPanel.ERASER -> listOf(PdfInkTool.ERASER)
    }
    Box(modifier = modifier) {
        SharedPdfAnnotationToolSettingsPanel(
            panel = panel,
            tools = panelTools,
            selectedTool = selectedTool,
            selectedColor = selectedColor,
            strokeWidth = strokeWidth,
            toolConfigs = actualToolConfigs,
            penPalette = penPalette,
            highlighterPalette = highlighterPalette,
            onToolSelected = onToolSelected,
            onColorSelected = onColorSelected,
            onStrokeWidthChange = onStrokeWidthChange,
            onPaletteChange = onPaletteChange,
            isHighlighterSnapEnabled = isHighlighterSnapEnabled,
            onHighlighterSnapChange = onHighlighterSnapChange,
        )
    }
}
