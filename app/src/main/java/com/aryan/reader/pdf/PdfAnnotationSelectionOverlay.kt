// PdfAnnotationSelectionOverlay.kt
//
// Ink selection rendering: dashed bounding box + small corner/mid-side/sync
// handles, lasso polyline, and the floating style/action edit bar. Handles
// are drawn very small; the generous (28dp) touch slop in the gesture
// detector keeps them easy to grab. Selected strokes are NOT re-tinted: only
// the box shows. While a rotation drag runs, the box + handles hide and a
// small degree pill shows pinned to the rotation-start center. Lasso loops draw as
// plain open polylines with no area fill and no auto-closing segment.
package com.aryan.reader.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.R
import com.aryan.reader.pdf.data.PdfAnnotation
import kotlin.math.roundToInt

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
    val syncPainter = painterResource(id = R.drawable.sync)
    val rotateOffsetNorm = 0.06f
    // Frozen pill anchor: the live union box breathes as the ink turns
    // (axis-aligned bounds of a rotating shape), so centering the pill on
    // it every frame makes it fly around. Capture the pre-rotation center
    // on the first frame and hold it until the drag ends.
    var rotationAnchorNorm by remember { mutableStateOf<Offset?>(null) }
    if (activeRotationDegrees == null) {
        if (rotationAnchorNorm != null) rotationAnchorNorm = null
    } else if (rotationAnchorNorm == null) {
        pdfSelectionUnionBounds(selectedAnnotations)?.let { rotationAnchorNorm = it.center }
    }

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
                    // pinned to the rotation-start center. Strokes themselves
                    // keep drawing live from the page layer underneath.
                    val anchor = rotationAnchorNorm?.let { anchorNorm ->
                        Offset(
                            anchorNorm.x * pageWidthDoc,
                            anchorNorm.y * pageHeightDoc,
                        )
                    } ?: box.center
                    drawRotationDegreePill(
                        anchor,
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
                            // Sync glyph from sync.xml, tinted selection blue.
                            val glyphSize = handleRadiusDoc * 1.6f
                            translate(
                                center.x - glyphSize / 2f,
                                center.y - glyphSize / 2f,
                            ) {
                                with(syncPainter) {
                                    draw(
                                        size = Size(glyphSize, glyphSize),
                                        colorFilter = ColorFilter.tint(PdfSelectionBoxColor),
                                    )
                                }
                            }
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

/** Small white-on-black degree pill, constant screen size at any zoom. */
private fun DrawScope.drawRotationDegreePill(
    center: Offset,
    degrees: Float,
    textPx: Float,
    padHPx: Float,
    padVPx: Float,
) {
    val label = "${pdfNormalizeRotationDisplay(degrees)}°"
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
 * Floating selection menu anchored near the selection: Duplicate / Delete /
 * Change style, no icons. "Change style" swaps the menu for an inline style
 * panel (same anchor): ink-settings slider + palette with live preview and
 * slim Cancel | Done. The spectrum edits the selected palette slot, like the
 * ink settings popup — no new circles are created. Rendered outside the zoom
 * transform.
 */
@Composable
internal fun PdfInkSelectionEditBar(
    selectedColor: Color?,
    selectionPalette: List<Color>,
    onPaletteChange: (List<Color>) -> Unit,
    onColorLive: (Color) -> Unit,
    onColorReverted: () -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onThicknessChangeFinished: () -> Unit,
    canDuplicate: Boolean,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStylePanel by remember { mutableStateOf(false) }
        Surface(
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        modifier = modifier,
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
            PdfSelectionStylePanel(
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
                    // One commit for the whole panel: thickness + color share
                    // the live snapshot, so a single commit covers both.
                    onThicknessChangeFinished()
                    showStylePanel = false
                },
            )
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
private fun PdfSelectionStylePanel(
    selectedColor: Color?,
    selectionPalette: List<Color>,
    onPaletteChange: (List<Color>) -> Unit,
    thickness: Float,
    thicknessRange: ClosedFloatingPointRange<Float>,
    onThicknessChange: (Float) -> Unit,
    onPaletteColorPicked: (Color) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    var showSpectrum by remember { mutableStateOf(false) }
    val selectedIndex = remember(selectionPalette, selectedColor) {
        if (selectedColor == null) {
            -1
        } else {
            selectionPalette.take(6).indexOfFirst {
                it.copy(alpha = 1f) == selectedColor.copy(alpha = 1f)
            }
        }
    }
    Column(modifier = Modifier.width(300.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        StyledPropertySlider(
            value = thickness,
            onValueChange = onThicknessChange,
            valueRange = thicknessRange,
            isOpacity = false,
            trackColor = Color(0xFF424242),
            thumbColor = Color(0xFF757575),
            activeColor = selectedColor ?: Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                selectionPalette.take(6).forEachIndexed { index, paletteColor ->
                    val dotColor = paletteColor.copy(alpha = 1f)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable { onPaletteColorPicked(dotColor) },
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
                    .clickable { showSpectrum = true },
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
                    .padding(vertical = 8.dp),
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
                    .padding(vertical = 8.dp),
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
        ColorPickerDialog(
            initialColor = selectedColor ?: Color.Black,
            // Slot edit like the ink settings popup: saving rewrites the
            // selected palette slot (persisted via onPaletteChange) and
            // applies it; with no circle selected it applies a custom color
            // live without touching the palette.
            onDismiss = { showSpectrum = false },
            onColorSelected = {
                if (selectedIndex in selectionPalette.indices) {
                    onPaletteChange(
                        selectionPalette.toMutableList().also { next -> next[selectedIndex] = it }
                    )
                }
                onPaletteColorPicked(it)
                showSpectrum = false
            },
        )
    }
}
