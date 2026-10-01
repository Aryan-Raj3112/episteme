package com.aryan.reader.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aryan.reader.readerModalMaxHeightDp
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfToolConfig
import com.aryan.reader.shared.pdf.SharedPdfInkToolMapping
import com.aryan.reader.shared.ui.SharedPdfAndroidToolSettingsPopup
import com.aryan.reader.shared.pdf.SharedPdfAnnotationHighlighterTools

/**
 * Pen / highlighter tool-settings popup.
 *
 * Rendering lives in the shared panel (`SharedPdfAndroidToolSettingsPopup`),
 * which is the Android-benchmark copy. This file only adapts Android's [InkType]
 * and Compose [Color] state to the shared `PdfInkTool` + ARGB contract and back.
 */
@Composable
fun ToolSettingsPopup(
    selectedTool: InkType,
    activeToolThickness: Float,
    fountainPenColor: Color,
    markerColor: Color,
    pencilColor: Color,
    highlighterColor: Color,
    highlighterRoundColor: Color,
    activePalette: List<Color>,
    onToolTypeChanged: (InkType) -> Unit,
    onColorChanged: (Color) -> Unit,
    onThicknessChanged: (Float) -> Unit,
    onPaletteChange: (List<Color>) -> Unit,
    modifier: Modifier = Modifier,
    isHighlighterSnapEnabled: Boolean = false,
    onSnapToggle: (Boolean) -> Unit = {}
) {
    val sharedTool = SharedPdfInkToolMapping.toSharedPdfInkTool(selectedTool.name)
    val isHighlighter = sharedTool in SharedPdfAnnotationHighlighterTools
    val isEraser = sharedTool == PdfInkTool.ERASER
    val activeColor = activeColorFor(
        tool = selectedTool,
        fountainPenColor = fountainPenColor,
        markerColor = markerColor,
        pencilColor = pencilColor,
        highlighterColor = highlighterColor,
        highlighterRoundColor = highlighterRoundColor
    )
    val currentAlpha = activeColor.alpha

    val toolColors = mapOf(
        PdfInkTool.FOUNTAIN_PEN to fountainPenColor,
        PdfInkTool.PEN to markerColor,
        PdfInkTool.PENCIL to pencilColor,
        PdfInkTool.HIGHLIGHTER to highlighterColor,
        PdfInkTool.HIGHLIGHTER_ROUND to highlighterRoundColor
    )
    val toolConfigs = toolColors.mapValues { (_, color) ->
        PdfToolConfig(colorArgb = color.toArgb(), strokeWidth = activeToolThickness)
    }
    val activeColorArgb = toolColors[sharedTool]?.toArgb()
        ?: if (isEraser) Color.White.toArgb()
        else markerColor.toArgb()

    val configuration = LocalConfiguration.current
    val maxPopupHeight = readerModalMaxHeightDp(
        screenHeightDp = configuration.screenHeightDp,
        fraction = 0.8f,
        verticalMarginDp = 64,
        preferredMinHeightDp = 240
    ).dp

    SharedPdfAndroidToolSettingsPopup(
        selectedTool = sharedTool,
        selectedColor = activeColorArgb,
        strokeWidth = activeToolThickness,
        actualToolConfigs = toolConfigs,
        penPalette = if (isHighlighter) emptyList() else activePalette.map { it.toArgb() },
        highlighterPalette = if (isHighlighter) activePalette.map { it.toArgb() } else emptyList(),
        onToolSelected = { tool ->
            onToolTypeChanged(InkType.valueOf(SharedPdfInkToolMapping.toAndroidInkTypeName(tool)))
        },
        onColorSelected = { argb ->
            // Highlighters keep their existing opacity: the shared panel drives
            // RGB through this callback and edits alpha on its own slider.
            val next = if (isHighlighter) Color(argb).copy(alpha = currentAlpha) else Color(argb)
            onColorChanged(next)
        },
        onStrokeWidthChange = onThicknessChanged,
        onPaletteChange = { argbPalette -> onPaletteChange(argbPalette.map { Color(it) }) },
        isHighlighterSnapEnabled = isHighlighterSnapEnabled,
        onHighlighterSnapChange = onSnapToggle,
        maxHeight = maxPopupHeight,
        modifier = modifier
    )
}

private fun activeColorFor(
    tool: InkType,
    fountainPenColor: Color,
    markerColor: Color,
    pencilColor: Color,
    highlighterColor: Color,
    highlighterRoundColor: Color
): Color = when (tool) {
    InkType.FOUNTAIN_PEN -> fountainPenColor
    InkType.PEN -> markerColor
    InkType.PENCIL -> pencilColor
    InkType.HIGHLIGHTER -> highlighterColor
    InkType.HIGHLIGHTER_ROUND -> highlighterRoundColor
    InkType.ERASER -> Color.White
    else -> markerColor
}
