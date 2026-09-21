package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfSelectionHandle
import com.aryan.reader.shared.pdf.sharedPdfNormalizeRotationDisplay
import com.aryan.reader.shared.pdf.sharedPdfSelectionHandlePositions
import com.aryan.reader.shared.pdf.sharedPdfSelectionUnionBounds
import kotlin.math.roundToInt

internal val SharedPdfSelectionBoxColor = Color(0xFF64B5F6)

/**
 * Page-local selection canvas (shared mobile reader, benchmark: Android
 * `PdfInkSelectionCanvas`). Mount inside the same zoom/pan transform as the
 * page so page-px drawing lines up with the page content: the union box,
 * small handles and the open lasso polyline all divide by [zoom] to keep a
 * constant screen size. Selected strokes are NOT re-tinted: only the box
 * shows. While a rotation drag runs, the box + handles hide and a small
 * degree pill shows centered on the ink instead.
 *
 * @param selectedAnnotations currently selected strokes (box follows live data)
 * @param pageSizePx page size in page px for norm→px mapping
 * @param lassoPagePoints in-progress lasso in page px, or empty
 * @param zoom current zoom so handles keep constant screen size
 * @param activeRotationDegrees snapped angle of an in-progress rotate drag,
 *   or null. While set, the box + handles hide and a degree pill shows.
 */
@Composable
internal fun SharedPdfInkSelectionCanvas(
    selectedAnnotations: List<SharedPdfAnnotation>,
    pageSizePx: IntSize,
    lassoPagePoints: List<Offset>,
    zoom: Float,
    activeRotationDegrees: Float? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val safeZoom = zoom.coerceAtLeast(0.5f)
    val handleRadiusPx = remember(density, safeZoom) { with(density) { 5.dp.toPx() / safeZoom } }
    val pillTextSize = remember(safeZoom) { 13.sp / safeZoom }
    val pillPadH = remember(density, safeZoom) { with(density) { 10.dp.toPx() / safeZoom } }
    val pillPadV = remember(density, safeZoom) { with(density) { 6.dp.toPx() / safeZoom } }
    val textMeasurer = rememberTextMeasurer()
    val syncVector = remember {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(SharedPdfAndroidSyncPath).toNodes(),
                // Explicit white fill (tinted blue at draw): addPath defaults
                // to no fill, which paints nothing — same fill the dock
                // icons pass in SharedPdfAndroidPathIcon.
                fill = SolidColor(Color.White),
            )
        }.build()
    }
    val syncPainter = rememberVectorPainter(syncVector)
    val rotateOffsetNorm = 0.06f

    Canvas(modifier = modifier) {
        if (selectedAnnotations.isNotEmpty() && pageSizePx.width > 0 && pageSizePx.height > 0) {
            val union = sharedPdfSelectionUnionBounds(selectedAnnotations)
            if (union != null) {
                val box = Rect(
                    union.left * pageSizePx.width,
                    union.top * pageSizePx.height,
                    union.right * pageSizePx.width,
                    union.bottom * pageSizePx.height,
                )
                if (activeRotationDegrees != null) {
                    drawSharedRotationDegreePill(
                        center = box.center,
                        degrees = activeRotationDegrees,
                        textMeasurer = textMeasurer,
                        textSize = pillTextSize,
                        padHPx = pillPadH,
                        padVPx = pillPadV,
                    )
                } else {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f / safeZoom, 8f / safeZoom), 0f)
                    drawRect(
                        color = SharedPdfSelectionBoxColor,
                        topLeft = box.topLeft,
                        size = box.size,
                        style = Stroke(width = 2f / safeZoom, pathEffect = dash),
                    )
                    val normHandles = sharedPdfSelectionHandlePositions(union, rotateOffsetNorm = rotateOffsetNorm)
                    fun toPage(normX: Float, normY: Float): Offset = Offset(
                        normX * pageSizePx.width,
                        normY * pageSizePx.height,
                    )
                    // Rotate stem.
                    val rotatePos = normHandles.getValue(SharedPdfSelectionHandle.ROTATE)
                        .let { toPage(it.x, it.y) }
                    val boxTopCenter = Offset(box.center.x, box.top)
                    drawLine(
                        color = SharedPdfSelectionBoxColor,
                        start = boxTopCenter,
                        end = rotatePos,
                        strokeWidth = 2f / safeZoom,
                    )
                    for ((handle, norm) in normHandles) {
                        val center = toPage(norm.x, norm.y)
                        if (handle == SharedPdfSelectionHandle.ROTATE) {
                            drawCircle(color = Color.White, radius = handleRadiusPx, center = center)
                            drawCircle(
                                color = SharedPdfSelectionBoxColor,
                                radius = handleRadiusPx,
                                center = center,
                                style = Stroke(width = 2f / safeZoom),
                            )
                            // Sync glyph (shared pathData twin of sync.xml),
                            // tinted selection blue.
                            val glyphSize = handleRadiusPx * 1.6f
                            translate(
                                center.x - glyphSize / 2f,
                                center.y - glyphSize / 2f,
                            ) {
                                with(syncPainter) {
                                    draw(
                                        size = Size(glyphSize, glyphSize),
                                        colorFilter = ColorFilter.tint(SharedPdfSelectionBoxColor),
                                    )
                                }
                            }
                        } else {
                            drawCircle(color = Color.White, radius = handleRadiusPx, center = center)
                            drawCircle(
                                color = Color(0xFF616161),
                                radius = handleRadiusPx,
                                center = center,
                                style = Stroke(width = 2f / safeZoom),
                            )
                        }
                    }
                }
            }
        }
        // In-progress lasso: plain open polyline, never auto-closed and no
        // area fill.
        if (activeRotationDegrees == null && lassoPagePoints.size >= 2) {
            val path = Path().apply {
                moveTo(lassoPagePoints.first().x, lassoPagePoints.first().y)
                for (i in 1 until lassoPagePoints.size) {
                    lineTo(lassoPagePoints[i].x, lassoPagePoints[i].y)
                }
            }
            drawPath(
                path = path,
                color = SharedPdfSelectionBoxColor,
                style = Stroke(
                    width = 2.5f / safeZoom,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f / safeZoom, 10f / safeZoom), 0f),
                ),
            )
        }
    }
}

/** Small white-on-black degree pill, constant screen size at any zoom. */
private fun DrawScope.drawSharedRotationDegreePill(
    center: Offset,
    degrees: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textSize: androidx.compose.ui.unit.TextUnit,
    padHPx: Float,
    padVPx: Float,
) {
    val label = "${sharedPdfNormalizeRotationDisplay(degrees)}°"
    val layout = textMeasurer.measure(
        text = label,
        style = TextStyle(color = Color.White, fontSize = textSize, textAlign = TextAlign.Center),
    )
    val pillW = layout.size.width + padHPx * 2f
    val pillH = layout.size.height + padVPx * 2f
    val topLeft = Offset(center.x - pillW / 2f, center.y - pillH / 2f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.85f),
        topLeft = topLeft,
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
    )
    drawText(
        textMeasurer = textMeasurer,
        text = label,
        topLeft = Offset(topLeft.x + padHPx, topLeft.y + padVPx),
        style = TextStyle(color = Color.White, fontSize = textSize),
    )
}

/**
 * Floating edit bar anchored near the selection (benchmark: Android
 * `PdfInkSelectionEditBar`): HSV color entry (opens the spectrum picker
 * directly), a compact thickness slider with a numeric readout, and
 * copy (duplicate-in-place) / delete / clear actions. Rendered outside the
 * zoom transform; [selectionWindowRect] positions it below the selection
 * (above when there is no room), fully clamped into [containerSizePx].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedPdfInkSelectionEditBar(
    selectionWindowRect: Rect,
    containerSizePx: IntSize,
    selectedColor: Color?,
    onColorLive: (Color) -> Unit,
    onColorCommitted: (Color) -> Unit,
    onColorReverted: () -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onThicknessChangeFinished: () -> Unit,
    canDuplicate: Boolean,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    topInsetPx: Float = 0f,
) {
    var showSpectrum by remember { mutableStateOf(false) }
    // Dismissing the spectrum without saving reverts the live preview; the
    // flag tells dismiss apart from the save path (which hides first).
    var spectrumCommitted by remember { mutableStateOf(false) }
    var barSizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val marginPx = remember(density) { with(density) { 8.dp.toPx() } }
    val offset = remember(selectionWindowRect, containerSizePx, barSizePx, marginPx, topInsetPx) {
        val barW = barSizePx.width.toFloat()
        val barH = barSizePx.height.toFloat()
        val maxX = (containerSizePx.width - barW).coerceAtLeast(0f)
        val x = (selectionWindowRect.center.x - barW / 2f).coerceIn(0f, maxX)
        val belowY = selectionWindowRect.bottom + marginPx
        val y = if (belowY + barH <= containerSizePx.height || selectionWindowRect.top - marginPx - barH < topInsetPx) {
            belowY.coerceAtMost((containerSizePx.height - barH).coerceAtLeast(topInsetPx))
        } else {
            (selectionWindowRect.top - marginPx - barH).coerceAtLeast(topInsetPx)
        }
        IntOffset(x.roundToInt(), y.roundToInt())
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Color(0xFF1E1E1E),
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { offset }
                .onSizeChanged { barSizePx = it },
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
                        .clickable {
                            spectrumCommitted = false
                            showSpectrum = true
                        }
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
                SharedSelectionEditBarButton(
                    onClick = onDuplicate,
                    enabled = canDuplicate,
                    description = "Duplicate selection",
                ) {
                    SharedPdfAndroidPathIcon(
                        pathData = SharedPdfAndroidContentCopyPath,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                SharedSelectionEditBarButton(onClick = onDelete, description = "Delete selection") {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                SharedSelectionEditBarButton(onClick = onClose, description = "Clear selection") {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
    if (showSpectrum) {
        val initial = selectedColor ?: Color.Black
        SharedHsvColorPickerDialog(
            initialColor = initial,
            title = readerString("label_spectrum", "Spectrum"),
            onDismiss = {
                showSpectrum = false
                if (!spectrumCommitted) onColorReverted()
            },
            onSave = {
                onColorLive(it)
                onColorCommitted(it)
                spectrumCommitted = true
                showSpectrum = false
            },
            onLiveColorChange = onColorLive,
            stateKey = initial,
        )
    }
}

@Composable
private fun SharedSelectionEditBarButton(
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
