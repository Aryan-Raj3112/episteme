// PdfAnnotationSelectionOverlay.kt
//
// Ink selection rendering: glow + dashed bounding box + corner/rotate handles,
// lasso path, and the floating style/action edit bar. Mobile-first sizing:
// 48dp touch targets, 28dp visible handles.
package com.aryan.reader.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aryan.reader.R
import com.aryan.reader.pdf.data.PdfAnnotation

internal val PdfSelectionGlowColor = Color(0xFF64B5F6)
internal val PdfSelectionBoxColor = Color(0xFF64B5F6)

/**
 * Page-local selection canvas. Must be placed inside the same zoom/pan
 * transform as the pages, positioned at the page's doc origin, so drawing
 * in page px lines up with the page content. Lasso points arrive in doc
 * coords and are shifted by the page top; drawing may overflow onto
 * neighbouring pages (Canvas does not clip), which keeps cross-page
 * lassos visible.
 *
 * @param selectedAnnotations currently selected strokes (glow follows live data)
 * @param selectionPage page layout (doc px) the selection lives on, or null
 * @param pageWidthDoc page width in doc px for path building
 * @param lassoDocPoints in-progress lasso in doc coords, or empty
 * @param cameraZoom current zoom so handles keep constant screen size
 */
@Composable
internal fun PdfInkSelectionCanvas(
    selectedAnnotations: List<PdfAnnotation>,
    selectionPage: PdfSelectionPageLayout?,
    pageWidthDoc: Int,
    pageHeightDoc: Int,
    lassoDocPoints: List<Offset>,
    cameraZoom: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val zoom = cameraZoom.coerceAtLeast(0.5f)
    val handleRadiusDoc = remember(density, zoom) { with(density) { 11.dp.toPx() / zoom } }
    val glowExtraDoc = remember(density, zoom) { with(density) { 7.dp.toPx() / zoom } }
    val rotateOffsetNorm = 0.06f

    Canvas(modifier = modifier) {
        if (selectionPage != null && selectedAnnotations.isNotEmpty()) {
            // Union bounds in normalized coords for the box + handles.
            val union = pdfSelectionUnionBounds(selectedAnnotations)
            // 1. Glow: wide translucent stroke over each selected path.
            // Paths are built page-relative, matching the overlay origin.
            for (annotation in selectedAnnotations) {
                val renderData = PdfAnnotationRenderHelper.createRenderData(
                    annotation, pageWidthDoc, pageHeightDoc
                ) ?: continue
                drawPdfSelectionGlow(renderData, glowExtraDoc)
            }
            // 2. Dashed bounding box + handles.
            if (union != null) {
                val box = Rect(
                    union.left * pageWidthDoc,
                    union.top * pageHeightDoc,
                    union.right * pageWidthDoc,
                    union.bottom * pageHeightDoc,
                )
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f / zoom, 8f / zoom), 0f)
                drawRect(
                    color = PdfSelectionBoxColor,
                    topLeft = box.topLeft,
                    size = box.size,
                    style = Stroke(width = 2f / zoom, pathEffect = dash),
                )
                val normHandles = pdfSelectionHandlePositions(
                    Rect(union.left, union.top, union.right, union.bottom),
                    rotateOffsetNorm = rotateOffsetNorm,
                )
                fun toDoc(norm: PdfPoint): Offset = Offset(
                    norm.x * pageWidthDoc,
                    norm.y * pageHeightDoc,
                )
                // Rotate stem.
                val rotatePos = toDoc(normHandles.getValue(PdfSelectionHandle.ROTATE))
                val boxTopCenter = Offset(box.center.x, box.top)
                drawLine(
                    color = PdfSelectionBoxColor,
                    start = boxTopCenter,
                    end = rotatePos,
                    strokeWidth = 2f / zoom,
                )
                for ((handle, norm) in normHandles) {
                    if (handle == PdfSelectionHandle.ROTATE) {
                        val center = toDoc(norm)
                        drawCircle(color = Color.White, radius = handleRadiusDoc, center = center)
                        drawCircle(
                            color = PdfSelectionBoxColor,
                            radius = handleRadiusDoc,
                            center = center,
                            style = Stroke(width = 2.5f / zoom),
                        )
                        // Rotate glyph: small arc arrow approximated by inner dot ring.
                        drawCircle(
                            color = PdfSelectionBoxColor,
                            radius = handleRadiusDoc * 0.45f,
                            center = center,
                            style = Stroke(width = 2f / zoom),
                        )
                    } else {
                        val center = toDoc(norm)
                        drawCircle(color = Color.White, radius = handleRadiusDoc, center = center)
                        drawCircle(
                            color = Color(0xFF616161),
                            radius = handleRadiusDoc,
                            center = center,
                            style = Stroke(width = 2f / zoom),
                        )
                    }
                }
            }
        }
        // 3. In-progress lasso loop (doc coords shifted to page-local).
        if (lassoDocPoints.size >= 2) {
            val pageTop = selectionPage?.topDoc ?: 0f
            val path = Path().apply {
                moveTo(lassoDocPoints.first().x, lassoDocPoints.first().y - pageTop)
                for (i in 1 until lassoDocPoints.size) {
                    lineTo(lassoDocPoints[i].x, lassoDocPoints[i].y - pageTop)
                }
                close()
            }
            drawPath(
                path = path,
                color = PdfSelectionBoxColor.copy(alpha = 0.12f),
            )
            drawPath(
                path = path,
                color = PdfSelectionBoxColor,
                style = Stroke(
                    width = 2.5f / zoom,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f / zoom, 10f / zoom), 0f),
                ),
            )
        }
    }
}

private fun DrawScope.drawPdfSelectionGlow(
    renderData: AnnotationRenderData,
    glowExtraPx: Float,
) {
    when (renderData) {
        is AnnotationRenderData.Standard -> {
            drawPath(
                path = renderData.path,
                color = PdfSelectionGlowColor.copy(alpha = 0.35f),
                style = Stroke(
                    width = renderData.strokeWidth + glowExtraPx,
                    cap = renderData.cap,
                    join = StrokeJoin.Round,
                ),
            )
        }
        is AnnotationRenderData.Fountain -> {
            drawPath(
                path = renderData.path,
                color = PdfSelectionGlowColor.copy(alpha = 0.35f),
            )
        }
        is AnnotationRenderData.Pencil -> {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        90,
                        (PdfSelectionGlowColor.red * 255).toInt(),
                        (PdfSelectionGlowColor.green * 255).toInt(),
                        (PdfSelectionGlowColor.blue * 255).toInt(),
                    )
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = renderData.strokeWidth + glowExtraPx
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawPath(renderData.path, paint)
            }
        }
    }
}

/** Minimal page geometry the overlay needs (doc px). */
internal data class PdfSelectionPageLayout(
    val index: Int,
    val topDoc: Float,
    val widthDoc: Float,
    val heightDoc: Float,
)

/**
 * Floating edit bar anchored near the selection: color dots, thickness slider,
 * duplicate / copy / delete actions. Rendered outside the zoom transform.
 */
@Composable
internal fun PdfInkSelectionEditBar(
    colors: List<Color>,
    selectedColor: Color?,
    onColorSelected: (Color) -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onThicknessChangeFinished: () -> Unit,
    canDuplicate: Boolean,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.take(6).forEach { color ->
                val isSelected = selectedColor != null &&
                    (color.toArgb() and 0x00FFFFFF) == (selectedColor.toArgb() and 0x00FFFFFF)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onColorSelected(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(26.dp)) {
                        drawCircle(color = color)
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = size.minDimension / 2f - 1.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                }
            }
            Slider(
                value = thickness.coerceIn(thicknessRange.start, thicknessRange.endInclusive),
                onValueChange = onThicknessChange,
                onValueChangeFinished = onThicknessChangeFinished,
                valueRange = thicknessRange,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.85f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.size(width = 90.dp, height = 40.dp),
            )
            EditBarButton(
                onClick = onDuplicate,
                enabled = canDuplicate,
                description = "Duplicate selection",
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.copy),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            EditBarButton(onClick = onCopy, description = "Copy selection") {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            EditBarButton(
                onClick = onPaste,
                enabled = canPaste,
                description = "Paste selection",
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.paste),
                    contentDescription = null,
                    tint = if (canPaste) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp),
                )
            }
            EditBarButton(onClick = onDelete, description = "Delete selection") {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            EditBarButton(onClick = onClose, description = "Clear selection") {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun EditBarButton(
    onClick: () -> Unit,
    description: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
