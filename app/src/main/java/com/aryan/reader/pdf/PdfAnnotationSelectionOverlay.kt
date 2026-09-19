// PdfAnnotationSelectionOverlay.kt
//
// Ink selection rendering: dashed bounding box + small corner/mid-side/sync
// handles, lasso polyline, and the floating style/action edit bar. Handles
// are drawn very small; the generous (28dp) touch slop in the gesture
// detector keeps them easy to grab. Selected strokes are NOT re-tinted: only
// the box shows. While a rotation drag runs, the box + handles hide and a
// small degree pill shows centered on the ink instead. Lasso loops draw as
// plain open polylines with no area fill and no auto-closing segment.
package com.aryan.reader.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.R
import com.aryan.reader.pdf.data.PdfAnnotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal val PdfSelectionBoxColor = Color(0xFF64B5F6)

/**
 * Page-local selection canvas. Must be placed inside the same zoom/pan
 * transform as the pages, positioned at the page's doc origin, so drawing
 * in page px lines up with the page content. Lasso points arrive in doc
 * coords and are shifted by the page top; drawing may overflow onto
 * neighboring pages (Canvas does not clip), which keeps cross-page
 * lassos visible.
 *
 * @param selectedAnnotations currently selected strokes (box follows live data)
 * @param selectionPage page layout (doc px) the selection lives on, or null
 * @param pageWidthDoc page width in doc px for path building
 * @param lassoDocPoints in-progress lasso in doc coords, or empty
 * @param cameraZoom current zoom so handles keep constant screen size
 * @param activeRotationDegrees snapped angle of an in-progress rotate drag,
 *   or null. While set, the box + handles hide and a degree pill shows.
 */
@Composable
internal fun PdfInkSelectionCanvas(
    selectedAnnotations: List<PdfAnnotation>,
    selectionPage: PdfSelectionPageLayout?,
    pageWidthDoc: Int,
    pageHeightDoc: Int,
    lassoDocPoints: List<Offset>,
    cameraZoom: Float,
    activeRotationDegrees: Float? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val zoom = cameraZoom.coerceAtLeast(0.5f)
    val handleRadiusDoc = remember(density, zoom) { with(density) { 5.dp.toPx() / zoom } }
    val pillTextPx = remember(density, zoom) { with(density) { 13.sp.toPx() / zoom } }
    val pillPadHPx = remember(density, zoom) { with(density) { 10.dp.toPx() / zoom } }
    val pillPadVPx = remember(density, zoom) { with(density) { 6.dp.toPx() / zoom } }
    val rotateOffsetNorm = 0.06f

    Canvas(modifier = modifier) {
        if (selectionPage != null && selectedAnnotations.isNotEmpty()) {
            // Union bounds in normalized coords for the box + handles.
            val union = pdfSelectionUnionBounds(selectedAnnotations)
            if (union != null) {
                val box = Rect(
                    union.left * pageWidthDoc,
                    union.top * pageHeightDoc,
                    union.right * pageWidthDoc,
                    union.bottom * pageHeightDoc,
                )
                if (activeRotationDegrees != null) {
                    // Rotation in progress: chrome hides, degree pill shows
                    // centered on the ink. Strokes themselves keep drawing
                    // live from the page layer underneath.
                    drawRotationDegreePill(
                        box.center,
                        activeRotationDegrees,
                        pillTextPx,
                        pillPadHPx,
                        pillPadVPx,
                    )
                } else {
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
                                style = Stroke(width = 2f / zoom),
                            )
                            drawSyncGlyph(center, handleRadiusDoc, zoom)
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
        }
        // In-progress lasso: plain open polyline, never auto-closed and no
        // area fill (doc coords shifted to page-local).
        if (activeRotationDegrees == null && lassoDocPoints.size >= 2) {
            val pageTop = selectionPage?.topDoc ?: 0f
            val path = Path().apply {
                moveTo(lassoDocPoints.first().x, lassoDocPoints.first().y - pageTop)
                for (i in 1 until lassoDocPoints.size) {
                    lineTo(lassoDocPoints[i].x, lassoDocPoints[i].y - pageTop)
                }
            }
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

/**
 * Sync-style circular-arrows glyph for the rotate handle: an almost-closed
 * arc with an arrowhead at each end of the gap.
 */
private fun DrawScope.drawSyncGlyph(center: Offset, radius: Float, zoom: Float) {
    val arcR = radius * 0.55f
    val stroke = 2f / zoom
    val startAngle = 40f
    val sweep = 280f
    drawArc(
        color = PdfSelectionBoxColor,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - arcR, center.y - arcR),
        size = Size(arcR * 2f, arcR * 2f),
        style = Stroke(width = stroke),
    )
    fun arrowHead(angleDeg: Float, forward: Boolean) {
        val rad = angleDeg * PI.toFloat() / 180f
        val px = center.x + arcR * cos(rad)
        val py = center.y + arcR * sin(rad)
        var tx = -sin(rad)
        var ty = cos(rad)
        if (!forward) {
            tx = -tx
            ty = -ty
        }
        val nx = cos(rad)
        val ny = sin(rad)
        val s = 4.5f / zoom
        val tipX = px + tx * s * 0.9f
        val tipY = py + ty * s * 0.9f
        val backX = px - tx * s * 0.15f
        val backY = py - ty * s * 0.15f
        val w = s * 0.55f
        drawPath(
            Path().apply {
                moveTo(tipX, tipY)
                lineTo(backX + nx * w, backY + ny * w)
                lineTo(backX - nx * w, backY - ny * w)
                close()
            },
            PdfSelectionBoxColor,
        )
    }
    arrowHead(startAngle + sweep, forward = true)
    arrowHead(startAngle, forward = false)
}

/** Small white-on-black degree pill, constant screen size at any zoom. */
private fun DrawScope.drawRotationDegreePill(
    center: Offset,
    degrees: Float,
    textPx: Float,
    padHPx: Float,
    padVPx: Float,
) {
    val label = "${degrees.roundToInt()}°"
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = textPx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val metrics = textPaint.fontMetrics
        val pillW = textPaint.measureText(label) + padHPx * 2f
        val pillH = (metrics.descent - metrics.ascent) + padVPx * 2f
        val left = center.x - pillW / 2f
        val top = center.y - pillH / 2f
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(217, 0, 0, 0)
            isAntiAlias = true
        }
        native.drawRoundRect(
            left, top, left + pillW, top + pillH, pillH / 2f, pillH / 2f, bgPaint
        )
        native.drawText(label, center.x, center.y - (metrics.ascent + metrics.descent) / 2f, textPaint)
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
 * Floating edit bar anchored near the selection: HSV color entry (opens the
 * spectrum picker directly), a compact thickness slider with a numeric
 * readout, and copy (duplicate-in-place) / delete actions. Rendered outside
 * the zoom transform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfInkSelectionEditBar(
    selectedColor: Color?,
    onColorSelected: (Color) -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onThicknessChangeFinished: () -> Unit,
    canDuplicate: Boolean,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpectrum by remember { mutableStateOf(false) }
    Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Single HSV entry: rainbow ring around the current color.
            // Opens the spectrum picker directly, no palette dots.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { showSpectrum = true }
                    .semantics { contentDescription = "Pick selection color" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
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
                                    Color.Red,
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawCircle(color = selectedColor ?: Color.Black)
                    }
                }
            }
            val sizeNumber = remember(thickness, thicknessRange) {
                val span = thicknessRange.endInclusive - thicknessRange.start
                val fraction = if (span > 0f) {
                    (thickness - thicknessRange.start) / span
                } else {
                    0f
                }
                (fraction * 100).roundToInt().coerceIn(1, 100)
            }
            Text(
                text = sizeNumber.toString(),
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(26.dp),
            )
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
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                },
                modifier = Modifier.width(84.dp),
            )
            EditBarButton(
                onClick = onDuplicate,
                enabled = canDuplicate,
                description = "Duplicate selection",
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.content_copy),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            EditBarButton(onClick = onDelete, description = "Delete selection") {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            EditBarButton(onClick = onClose, description = "Clear selection") {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    if (showSpectrum) {
        ColorPickerDialog(
            initialColor = selectedColor ?: Color.Black,
            onDismiss = { showSpectrum = false },
            onColorSelected = {
                onColorSelected(it)
                showSpectrum = false
            },
        )
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
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
