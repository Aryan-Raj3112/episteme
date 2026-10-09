package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.DockLocation
import kotlin.math.roundToInt

/**
 * Android-parity policy for the PDF annotation dock.
 *
 * Benchmark: `app/.../pdf/AnnotationDock.kt` + `PdfViewerScreen.kt` dock chrome.
 * - Full bar when sticky (TOP/BOTTOM, not dragging) or when not minimized;
 *   only a floating + minimized dock collapses to the 48dp circle.
 * - Minimized dims/disables tools and stops drawing
 *   (`isDrawingActive = isEditMode && !isDockMinimized`).
 * - Tool-settings popup opens on the opposite side of the dock (dock in bottom
 *   half -> popup above, else below).
 * - Pen/highlighter groups recall the last tool in that group, matching
 *   AnnotationDock's onClick resolution.
 */

val SharedPdfAnnotationPenTools: Set<PdfInkTool> = setOf(
    PdfInkTool.PEN,
    PdfInkTool.FOUNTAIN_PEN,
    PdfInkTool.PENCIL,
)

val SharedPdfAnnotationHighlighterTools: Set<PdfInkTool> = setOf(
    PdfInkTool.HIGHLIGHTER,
    PdfInkTool.HIGHLIGHTER_ROUND,
)

fun isSharedPdfAnnotationDockFullBar(
    isSticky: Boolean,
    isMinimized: Boolean,
): Boolean = isSticky || !isMinimized

fun isSharedPdfAnnotationDockSticky(
    dockLocation: DockLocation,
    isDragging: Boolean,
): Boolean = (dockLocation == DockLocation.TOP || dockLocation == DockLocation.BOTTOM ||
    dockLocation == DockLocation.LEFT || dockLocation == DockLocation.RIGHT) && !isDragging

/**
 * Whether the dock hugs a vertical screen edge. Side docks render as a
 * vertical bar with semi-circle caps (half-pill flush to the edge) instead
 * of the horizontal top/bottom bar.
 */
fun isSharedPdfAnnotationDockSide(dockLocation: DockLocation): Boolean =
    dockLocation == DockLocation.LEFT || dockLocation == DockLocation.RIGHT

fun isSharedPdfAnnotationDockVertical(dockLocation: DockLocation): Boolean =
    isSharedPdfAnnotationDockSide(dockLocation)

fun isSharedPdfAnnotationDrawingActive(
    selectedTool: PdfInkTool,
    isDockMinimized: Boolean,
): Boolean = selectedTool != PdfInkTool.NONE && selectedTool != PdfInkTool.TEXT &&
    selectedTool != PdfInkTool.SELECT && !isDockMinimized

fun isSharedPdfTextDockDrawingActive(
    selectedTool: PdfInkTool,
): Boolean = selectedTool == PdfInkTool.TEXT

fun isSharedPdfAnnotationPenActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool in SharedPdfAnnotationPenTools

fun isSharedPdfAnnotationHighlighterActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool in SharedPdfAnnotationHighlighterTools

fun isSharedPdfAnnotationTextActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool == PdfInkTool.TEXT

fun isSharedPdfAnnotationEraserActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool == PdfInkTool.ERASER

fun isSharedPdfAnnotationSelectActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool == PdfInkTool.SELECT

/**
 * Resolves which tool a dock tap should activate, mirroring
 * `AnnotationDock.kt` pen/highlighter group recall:
 * tapping the pen icon when another pen is active keeps it (caller toggles
 * settings); tapping it from another group recalls the last pen tool.
 */
fun resolveSharedPdfAnnotationDockToolClick(
    selectedTool: PdfInkTool,
    clickedGroup: PdfInkTool,
    lastPenTool: PdfInkTool,
    lastHighlighterTool: PdfInkTool,
): PdfInkTool = when (clickedGroup) {
    PdfInkTool.TEXT, PdfInkTool.ERASER -> clickedGroup
    in SharedPdfAnnotationPenTools -> if (selectedTool in SharedPdfAnnotationPenTools) {
        selectedTool
    } else {
        lastPenTool.takeIf { it in SharedPdfAnnotationPenTools } ?: PdfInkTool.PEN
    }
    in SharedPdfAnnotationHighlighterTools -> if (selectedTool in SharedPdfAnnotationHighlighterTools) {
        selectedTool
    } else {
        lastHighlighterTool.takeIf { it in SharedPdfAnnotationHighlighterTools } ?: PdfInkTool.HIGHLIGHTER
    }
    else -> clickedGroup
}

/**
 * Mirrors `PdfViewerScreen.kt` popup placement: dock in the bottom half ->
 * popup above the dock, else below it.
 */
fun isSharedPdfAnnotationDockInBottomHalf(
    dockTopYPx: Float,
    dockHeightPx: Float,
    boxHeightPx: Float,
): Boolean {
    if (boxHeightPx <= 0f) return true
    val dockCenterY = dockTopYPx + (dockHeightPx / 2f)
    return dockCenterY > (boxHeightPx / 2f)
}

fun sharedPdfAnnotationDockTopYPx(
    dockLocation: DockLocation,
    dockOffsetYPx: Float,
    boxHeightPx: Float,
    dockHeightPx: Float,
): Float = when (dockLocation) {
    DockLocation.TOP -> 0f
    DockLocation.BOTTOM -> boxHeightPx - dockHeightPx
    DockLocation.LEFT, DockLocation.RIGHT -> dockOffsetYPx
    DockLocation.FLOATING -> dockOffsetYPx
}

/**
 * Vertical padding (px, never negative) that keeps the tool-settings popup clear
 * of a side-docked wheel: the popup is anchored to the wheel's half, so it has
 * to start past the wheel's far edge — below its top edge when the wheel sits in
 * the top half, above its bottom edge when it sits in the bottom half.
 *
 * Clamped at 0 because a fixed-height wheel (192dp) can be taller than the space
 * it lives in — IME open, split screen, small window — and the raw difference
 * then goes negative, which `Modifier.padding` rejects with
 * "Padding must be non-negative". A non-finite input (unbounded constraints)
 * yields 0 as well.
 */
fun sharedPdfSideWheelPopupSidePadPx(
    wheelYPx: Float,
    wheelHeightPx: Float,
    boxHeightPx: Float,
    wheelInBottomHalf: Boolean,
): Float {
    val pad = if (wheelInBottomHalf) {
        boxHeightPx - wheelYPx - wheelHeightPx
    } else {
        wheelYPx
    }
    return if (pad.isFinite()) pad.coerceAtLeast(0f) else 0f
}

/**
 * Vertical padding (px, never negative) that keeps the tool-settings popup clear
 * of a top/bottom/floating bar: above it when the bar is in the bottom half,
 * below it otherwise. Same clamping contract as
 * [sharedPdfSideWheelPopupSidePadPx] — a floating bar dragged past an edge
 * would otherwise push the popup's padding negative.
 */
fun sharedPdfPopupClearanceAboveBarPx(
    dockTopYPx: Float,
    boxHeightPx: Float,
): Float {
    val pad = boxHeightPx - dockTopYPx
    return if (pad.isFinite()) pad.coerceAtLeast(0f) else 0f
}

/**
 * Left-edge X for a side-docked bar. TOP/BOTTOM are full-width so X is 0;
 * FLOATING uses the drag offset.
 */
fun sharedPdfAnnotationDockLeftXPx(
    dockLocation: DockLocation,
    dockOffsetXPx: Float,
    boxWidthPx: Float,
    dockWidthPx: Float,
): Float = when (dockLocation) {
    DockLocation.LEFT -> 0f
    DockLocation.RIGHT -> boxWidthPx - dockWidthPx
    DockLocation.FLOATING -> dockOffsetXPx
    DockLocation.TOP, DockLocation.BOTTOM -> dockOffsetXPx
}

fun isSharedPdfAnnotationDockInLeftHalf(
    dockLeftXPx: Float,
    dockWidthPx: Float,
    boxWidthPx: Float,
): Boolean {
    if (boxWidthPx <= 0f) return true
    val dockCenterX = dockLeftXPx + (dockWidthPx / 2f)
    return dockCenterX <= (boxWidthPx / 2f)
}

/**
 * Shared snap resolver for draggable docks (annotation + text).
 *
 * Snaps to whichever edge the dock touches *most*: the contact length with
 * each edge (visible dock height for LEFT/RIGHT, visible dock width for
 * TOP/BOTTOM) decides, so a wide horizontal bar in the bottom-left corner
 * snaps BOTTOM (long bottom contact) rather than LEFT (short side contact),
 * and a tall side wheel in the same corner snaps LEFT. Returns null when no
 * edge is touched and the drop point should stay floating.
 *
 * When the dock size is unknown ([dockWidthPx]/[dockHeightPx] <= 0) it falls
 * back to the drag-point rule (sides first), preserving the old behavior.
 */
fun resolveSharedPdfDockSnapLocation(
    dockOffsetX: Float,
    dockOffsetY: Float,
    boxWidthPx: Float,
    boxHeightPx: Float,
    sideEdgeThresholdPx: Float = 150f,
    topSnapThresholdPx: Float = 150f,
    bottomSnapThresholdOffsetPx: Float = 250f,
    dockWidthPx: Float = 0f,
    dockHeightPx: Float = 0f,
): DockLocation? {
    if (dockWidthPx <= 0f || dockHeightPx <= 0f) {
        return when {
            dockOffsetX < sideEdgeThresholdPx -> DockLocation.LEFT
            dockOffsetX > boxWidthPx - sideEdgeThresholdPx -> DockLocation.RIGHT
            dockOffsetY < topSnapThresholdPx -> DockLocation.TOP
            dockOffsetY > boxHeightPx - bottomSnapThresholdOffsetPx -> DockLocation.BOTTOM
            else -> null
        }
    }
    val dockRight = dockOffsetX + dockWidthPx
    val dockBottom = dockOffsetY + dockHeightPx
    val touchesLeft = dockOffsetX <= sideEdgeThresholdPx
    val touchesRight = dockRight >= boxWidthPx - sideEdgeThresholdPx
    val touchesTop = dockOffsetY <= topSnapThresholdPx
    val touchesBottom = dockBottom >= boxHeightPx - bottomSnapThresholdOffsetPx
    if (!touchesLeft && !touchesRight && !touchesTop && !touchesBottom) return null
    // Visible span along each axis = contact length with that edge.
    val visibleWidth = (minOf(dockRight, boxWidthPx) - maxOf(dockOffsetX, 0f)).coerceAtLeast(0f)
    val visibleHeight = (minOf(dockBottom, boxHeightPx) - maxOf(dockOffsetY, 0f)).coerceAtLeast(0f)
    // Candidates in tie-break order (TOP first): maxByOrNull keeps the first
    // maximum, so exact ties favor the top/bottom bars.
    val candidates = listOfNotNull(
        DockLocation.TOP.takeIf { touchesTop },
        DockLocation.BOTTOM.takeIf { touchesBottom },
        DockLocation.LEFT.takeIf { touchesLeft },
        DockLocation.RIGHT.takeIf { touchesRight },
    )
    return candidates.maxByOrNull { location ->
        when (location) {
            DockLocation.TOP, DockLocation.BOTTOM -> visibleWidth
            DockLocation.LEFT, DockLocation.RIGHT -> visibleHeight
            DockLocation.FLOATING -> 0f
        }
    }
}

/**
 * Android parity (`ReaderPopupSizing.readerModalMaxHeightDp` as called from
 * `ToolSettingsPopup.kt`): caps the tool-settings popup height to a fraction
 * of the available height so it scrolls instead of overflowing on small
 * screens. Pure math, dp-in/dp-out.
 */
fun sharedPdfPopupMaxHeightDp(
    availableHeightDp: Int,
    fraction: Float = 0.8f,
    verticalMarginDp: Int = 64,
    preferredMinHeightDp: Int = 240,
): Int {
    val usableHeight = (availableHeightDp - verticalMarginDp).coerceAtLeast(1)
    val proportionalHeight = (availableHeightDp * fraction).roundToInt().coerceAtLeast(1)
    val cappedHeight = minOf(usableHeight, proportionalHeight)
    return if (usableHeight >= preferredMinHeightDp) {
        cappedHeight.coerceAtLeast(preferredMinHeightDp)
    } else {
        usableHeight
    }
}
