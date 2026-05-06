package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import kotlin.math.abs

@Composable
fun SharedPdfAnnotationToolDock(
    selectedTool: PdfInkTool,
    selectedColor: Int,
    strokeWidth: Float,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearPage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SharedPdfToolButton(PdfInkTool.PEN, selectedTool, onToolSelected)
            SharedPdfToolButton(PdfInkTool.HIGHLIGHTER, selectedTool, onToolSelected)
            SharedPdfToolButton(PdfInkTool.PENCIL, selectedTool, onToolSelected)
            SharedPdfToolButton(PdfInkTool.FOUNTAIN_PEN, selectedTool, onToolSelected)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SharedPdfToolButton(PdfInkTool.HIGHLIGHTER_ROUND, selectedTool, onToolSelected)
            SharedPdfToolButton(PdfInkTool.TEXT, selectedTool, onToolSelected)
            SharedPdfToolButton(PdfInkTool.ERASER, selectedTool, onToolSelected)
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Undo annotation")
            }
            IconButton(onClick = onClearPage) {
                Icon(Icons.Default.Delete, contentDescription = "Clear page annotations")
            }
        }
        Text("Color", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val palette = if (selectedTool == PdfInkTool.HIGHLIGHTER || selectedTool == PdfInkTool.HIGHLIGHTER_ROUND) {
                SharedPdfAnnotationDefaults.highlighterPalette
            } else {
                SharedPdfAnnotationDefaults.penPalette
            }
            palette.forEach { argb ->
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .border(
                            width = if (argb == selectedColor) 3.dp else 1.dp,
                            color = if (argb == selectedColor) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onColorSelected(argb) },
                    color = Color(argb),
                    shape = RoundedCornerShape(14.dp),
                    content = {}
                )
            }
        }
        Text("Thickness ${strokeWidth.formatOneDecimal()}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = strokeWidth,
            onValueChange = onStrokeWidthChange,
            valueRange = 1f..28f
        )
    }
}

@Composable
fun SharedPdfAnnotationOverlay(
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    canvasSize: IntSize,
    activeStrokeColorArgb: Int = 0xFF1976D2.toInt(),
    activeStrokeWidth: Float = 2.5f
) {
    Canvas(Modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            when (annotation.kind) {
                PdfAnnotationKind.HIGHLIGHT -> {
                    val bounds = annotation.bounds ?: return@forEach
                    drawRect(
                        color = Color(annotation.colorArgb),
                        topLeft = Offset(bounds.left * canvasSize.width, bounds.top * canvasSize.height),
                        size = androidx.compose.ui.geometry.Size(
                            (bounds.right - bounds.left) * canvasSize.width,
                            (bounds.bottom - bounds.top) * canvasSize.height
                        )
                    )
                }
                PdfAnnotationKind.INK -> {
                    if (annotation.points.size > 1) {
                        drawPath(
                            path = annotation.points.toSharedPdfPath(canvasSize),
                            color = Color(annotation.colorArgb),
                            style = Stroke(
                                width = annotation.strokeWidth,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
                PdfAnnotationKind.TEXT -> {
                    val bounds = annotation.bounds ?: return@forEach
                    drawRect(
                        color = Color(annotation.backgroundArgb).copy(alpha = 0.18f),
                        topLeft = Offset(bounds.left * canvasSize.width, bounds.top * canvasSize.height),
                        size = androidx.compose.ui.geometry.Size(
                            (bounds.right - bounds.left) * canvasSize.width,
                            (bounds.bottom - bounds.top) * canvasSize.height
                        )
                    )
                }
            }
        }
        if (activeStroke.size > 1) {
            drawPath(
                path = activeStroke.toSharedPdfPath(canvasSize),
                color = Color(activeStrokeColorArgb),
                style = Stroke(width = activeStrokeWidth, cap = StrokeCap.Round)
            )
        }
    }
    annotations.filter { it.kind == PdfAnnotationKind.TEXT && it.text.isNotBlank() }.forEach { annotation ->
        val bounds = annotation.bounds ?: return@forEach
        Text(
            text = annotation.text,
            color = Color(annotation.colorArgb),
            fontSize = annotation.fontSize.sp,
            fontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .padding(
                    start = (bounds.left * canvasSize.width).dp,
                    top = (bounds.top * canvasSize.height).dp
                )
                .background(Color(annotation.backgroundArgb).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SharedPdfToolButton(
    tool: PdfInkTool,
    selectedTool: PdfInkTool,
    onToolSelected: (PdfInkTool) -> Unit
) {
    val selected = tool == selectedTool
    val icon = when (tool) {
        PdfInkTool.PEN -> Icons.Default.Draw
        PdfInkTool.HIGHLIGHTER -> Icons.Default.Brush
        PdfInkTool.HIGHLIGHTER_ROUND -> Icons.Default.FormatColorText
        PdfInkTool.ERASER -> Icons.Default.Remove
        PdfInkTool.FOUNTAIN_PEN -> Icons.Default.EditNote
        PdfInkTool.PENCIL -> Icons.Default.Brush
        PdfInkTool.TEXT -> Icons.Default.TextFields
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        IconButton(onClick = { onToolSelected(tool) }) {
            Icon(icon, contentDescription = tool.name.lowercase().replace('_', ' '))
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

fun SharedPdfAnnotation.sharedPdfHitTest(point: Offset, size: IntSize): Boolean {
    return when (kind) {
        PdfAnnotationKind.HIGHLIGHT,
        PdfAnnotationKind.TEXT -> {
            val bounds = bounds ?: return false
            val rect = Rect(
                bounds.left * size.width,
                bounds.top * size.height,
                bounds.right * size.width,
                bounds.bottom * size.height
            )
            rect.contains(point)
        }
        PdfAnnotationKind.INK -> {
            points.any {
                abs((it.x * size.width) - point.x) <= strokeWidth + 8f &&
                    abs((it.y * size.height) - point.y) <= strokeWidth + 8f
            }
        }
    }
}

private fun List<PdfPagePoint>.toSharedPdfPath(size: IntSize): Path {
    val path = Path()
    forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun Float.formatOneDecimal(): String {
    val scaled = (this * 10).toInt()
    return "${scaled / 10}.${scaled % 10}"
}
