package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.text.font.FontWeight
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
 * degree pill shows pinned to the rotation-start center.
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
    // Frozen pill anchor: the live union box breathes as the ink turns
    // (axis-aligned bounds of a rotating shape), so centering the pill on
    // it every frame makes it fly around. Capture the pre-rotation center
    // on the first frame and hold it until the drag ends.
    var rotationAnchorNorm by remember { mutableStateOf<Offset?>(null) }
    if (activeRotationDegrees == null) {
        if (rotationAnchorNorm != null) rotationAnchorNorm = null
    } else if (rotationAnchorNorm == null) {
        sharedPdfSelectionUnionBounds(selectedAnnotations)?.let { union ->
            rotationAnchorNorm = Offset(
                (union.left + union.right) / 2f,
                (union.top + union.bottom) / 2f,
            )
        }
    }

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
                    val anchor = rotationAnchorNorm?.let { anchorNorm ->
                        Offset(
                            anchorNorm.x * pageSizePx.width,
                            anchorNorm.y * pageSizePx.height,
                        )
                    } ?: box.center
                    drawSharedRotationDegreePill(
                        center = anchor,
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
 * Floating selection menu anchored near the selection: Duplicate / Delete /
 * Change style, no icons. "Change style" swaps the menu for an inline style
 * panel (same anchor): ink-settings slider + palette with live preview and
 * slim Cancel | Done. The spectrum edits the selected palette slot, like the
 * ink settings popup — no new circles are created. Rendered outside the zoom
 * transform; [selectionWindowRect] positions it below the selection (above
 * when there is no room), fully clamped into [containerSizePx].
 */
@Composable
internal fun SharedPdfInkSelectionEditBar(
    selectionWindowRect: Rect,
    containerSizePx: IntSize,
    selectedColor: Color?,
    selectionPalette: List<Int>,
    onPaletteChange: (List<Int>) -> Unit,
    onColorLive: (Color) -> Unit,
    onColorReverted: () -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onThicknessChangeFinished: () -> Unit,
    canDuplicate: Boolean,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    topInsetPx: Float = 0f,
) {
    var showStylePanel by remember { mutableStateOf(false) }
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
            if (!showStylePanel) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionMenuTextButton(label = "Duplicate", onClick = onDuplicate, enabled = canDuplicate)
                    SelectionMenuTextButton(label = "Delete", onClick = onDelete)
                    SelectionMenuTextButton(label = "Change style", onClick = { showStylePanel = true })
                }
            } else {
                SharedPdfSelectionStylePanel(
                    selectedColor = selectedColor,
                    selectionPalette = selectionPalette,
                    onPaletteChange = onPaletteChange,
                    thickness = thickness,
                    thicknessRange = thicknessRange,
                    onThicknessChange = onThicknessChange,
                    onPaletteColorPicked = onColorLive,
                    onCancel = {
                        onColorReverted()
                        showStylePanel = false
                    },
                    onDone = {
                        // One commit for the whole panel: thickness + color
                        // share the live snapshot/preview, so a single commit
                        // covers both.
                        onThicknessChangeFinished()
                        showStylePanel = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionMenuTextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Inline change-style panel replacing the text menu at the same anchor:
 * ink-settings thickness slider (steppers + value-bubble thumb), palette
 * row with checkmark + rainbow custom entry, slim Cancel | Done footer. All
 * edits preview live; [onCancel] reverts, [onDone] commits once. The
 * spectrum edits the selected palette slot via [onPaletteChange] (like the
 * ink settings popup); with no circle selected it applies a custom color
 * live without touching the palette.
 */
@Composable
private fun SharedPdfSelectionStylePanel(
    selectedColor: Color?,
    selectionPalette: List<Int>,
    onPaletteChange: (List<Int>) -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onPaletteColorPicked: (Color) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    var showSpectrum by remember { mutableStateOf(false) }
    val selectedArgb = selectedColor?.toArgb()
    val selectedIndex = remember(selectionPalette, selectedArgb) {
        if (selectedArgb == null) {
            -1
        } else {
            selectionPalette.take(6).indexOfFirst { (it and 0x00FFFFFF) == (selectedArgb and 0x00FFFFFF) }
        }
    }
    Column(modifier = Modifier.width(300.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    SharedPdfStyledPropertySlider(
                        value = thickness,
                        onValueChange = onThicknessChange,
                        valueRange = thicknessRange,
                        isOpacity = false,
                        trackColor = Color(0xFF424242),
                        thumbColor = Color(0xFF757575),
                        activeColor = selectedColor ?: Color.White,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            selectionPalette.take(6).forEachIndexed { index, argb ->
                                val dotColor = Color(argb).copy(alpha = 1f)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .clickable { onPaletteColorPicked(dotColor) }
                                        .semantics { contentDescription = "Selection color $index" },
                                ) {
                                    if (index == selectedIndex) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (dotColor.luminance() > 0.5f) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
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
                            contentAlignment = Alignment.Center,
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
                                            Color.Red,
                                        )
                                    )
                                )
                                .clickable { showSpectrum = true }
                                .semantics { contentDescription = "Custom selection color" },
                            content = {},
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onCancel)
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Cancel style change" },
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDone)
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Apply style change" },
                        ) {
                            Text(
                                text = "Done",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
    if (showSpectrum) {
        SharedHsvColorPickerDialog(
            initialColor = selectedColor ?: Color.Black,
            title = readerString("label_spectrum", "Spectrum"),
            // Slot edit like the ink settings popup: saving rewrites the
            // selected palette slot (persisted via onPaletteChange) and
            // applies it; with no circle selected it applies a custom color
            // live without touching the palette.
            onDismiss = { showSpectrum = false },
            onSave = {
                if (selectedIndex in selectionPalette.indices) {
                    onPaletteChange(
                        selectionPalette.toMutableList().also { next -> next[selectedIndex] = it.toArgb() }
                    )
                }
                onPaletteColorPicked(it)
                showSpectrum = false
            },
            stateKey = selectedIndex,
        )
    }
}
