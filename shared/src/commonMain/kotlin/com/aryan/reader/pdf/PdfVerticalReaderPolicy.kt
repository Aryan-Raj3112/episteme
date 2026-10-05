package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.aryan.reader.shared.ReaderTheme
import kotlin.math.abs
import kotlin.math.sign

fun resolvePdfVerticalPageBackgroundColor(activeTheme: ReaderTheme): Color {
    val resolved = when (activeTheme.id) {
        "no_theme", "system" -> Color.White
        "reverse" -> Color.Black
        else -> activeTheme.backgroundColor
    }
    return if (resolved.isSpecified) resolved else Color.White
}

data class PdfLockedOrientationResetCamera(
    val zoom: Float,
    val panX: Float,
    val panY: Float,
)

data class PdfFlingVelocity(
    val x: Float,
    val y: Float,
)

/** Resolves independent axis velocities without projecting one axis through the other. */
fun resolvePdfFlingVelocity(
    rawX: Float,
    rawY: Float,
    displacementX: Float,
    displacementY: Float,
    minimumVelocity: Float,
    maximumVelocity: Float,
    allowHorizontal: Boolean,
): PdfFlingVelocity {
    val safeMaximum = maximumVelocity.coerceAtLeast(0f)
    val safeMinimum = minimumVelocity.coerceIn(0f, safeMaximum)
    val x = rawX.coerceIn(-safeMaximum, safeMaximum)
    val y = rawY.coerceIn(-safeMaximum, safeMaximum)
    val xMatchesGesture = displacementX == 0f || x * displacementX > 0f
    val yMatchesGesture = displacementY == 0f || y * displacementY > 0f
    return PdfFlingVelocity(
        x = if (
            allowHorizontal && xMatchesGesture && kotlin.math.abs(x) > safeMinimum
        ) x else 0f,
        y = if (yMatchesGesture && kotlin.math.abs(y) > safeMinimum) y else 0f,
    )
}

/** Returns only motion beyond touch slop, matching Android drag acquisition semantics. */
fun pdfPanAfterTouchSlop(accumulatedPan: Offset, touchSlop: Float): Offset {
    val distance = accumulatedPan.getDistance()
    val safeSlop = touchSlop.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    if (!distance.isFinite() || distance <= safeSlop || distance == 0f) return Offset.Zero
    val retainedDistance = distance - safeSlop
    return accumulatedPan * (retainedDistance / distance)
}

/** Overscroll budget as a fraction of the viewport. */
private const val PDF_PAN_OVERSCROLL_MAX_STRETCH = 0.22f

/** Fraction of the requested distance honoured once the stretch budget is used up. */
private const val PDF_PAN_OVERSCROLL_RESISTANCE = 0.10f

/**
 * The overscroll budget, in pixels: how far content may travel past a document edge.
 *
 * Every other Android scrolling surface lets content move a little past the edge and springs back,
 * and Compose exposes that as `Modifier.scrollable(overscrollEffect = ...)`. A hand-rolled camera
 * with a bare `coerceIn` has no equivalent, so the document ends at a dead wall: the fling simply
 * stops mid-curve and the reader gives no signal that there is nothing further.
 */
fun pdfVerticalOverscrollStretchPx(viewportHeightPx: Float): Float {
    val viewport = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    if (viewport <= 0f) return 0f
    return viewport * PDF_PAN_OVERSCROLL_MAX_STRETCH
}

/**
 * Resists motion that goes *outward* past [hardLimitPx].
 *
 * [outwardSign] is `+1` when increasing pan moves past the edge and `-1` when decreasing pan does;
 * motion in the other direction is inside the document and passes through untouched, which is what
 * keeps ordinary scrolling exactly as it was.
 *
 * Past the limit the camera keeps following the finger, but the remaining distance is discounted, so
 * the surface feels elastic rather than locked and stays bounded however far the gesture goes.
 *
 * Android 12+ gets the same feel from a stretch effect driven by an overscroll distance; doing it
 * here keeps the reader's own camera instead of trading it for a `Scrollable`.
 */
fun pdfVerticalOverscrollResist(
    requestedPanPx: Float,
    hardLimitPx: Float,
    viewportHeightPx: Float,
    outwardSign: Int,
): Float {
    if (!requestedPanPx.isFinite()) return hardLimitPx.takeIf { it.isFinite() } ?: 0f
    val safeHard = hardLimitPx.takeIf { it.isFinite() } ?: 0f
    if (outwardSign == 0) return requestedPanPx
    val budget = pdfVerticalOverscrollStretchPx(viewportHeightPx)
    if (budget <= 0f) return safeHard

    val overshoot = (requestedPanPx - safeHard) * outwardSign
    // Not past the edge in the outward direction: pass through.
    if (overshoot <= 0f) return requestedPanPx

    val resisted = if (overshoot <= budget) {
        overshoot
    } else {
        // Past the budget, keep a little give so the surface never feels locked, but only a
        // fraction of the distance actually requested.
        budget + (overshoot - budget) * PDF_PAN_OVERSCROLL_RESISTANCE
    }
    return safeHard + outwardSign * resisted
}

/**
 * Vertical pan bound: hard document limits with a bounded elastic stretch beyond them.
 *
 * The hard limits define where the document actually ends. Past them the camera follows the finger
 * with increasing resistance, up to [pdfVerticalOverscrollStretchPx]. Every path resolves through
 * here - drag, fling decay and programmatic moves alike - so a fling launched at an overscrolled
 * position decays back to exactly the edge the drag would have settled on.
 */
fun resolveVerticalPanYWithOverscroll(
    requestedPanY: Float,
    hardMinPanY: Float,
    hardMaxPanY: Float,
    viewportHeightPx: Float,
): Float {
    val safeMin = hardMinPanY.takeIf { it.isFinite() } ?: 0f
    val safeMax = hardMaxPanY.takeIf { it.isFinite() } ?: 0f
    if (!requestedPanY.isFinite()) return safeMin.coerceIn(minOf(safeMin, safeMax), maxOf(safeMin, safeMax))
    return when {
        // panY decreases toward the end of the document, so the bottom edge is the outward -1 side.
        requestedPanY < safeMin -> pdfVerticalOverscrollResist(
            requestedPanPx = requestedPanY,
            hardLimitPx = safeMin,
            viewportHeightPx = viewportHeightPx,
            outwardSign = -1,
        )
        requestedPanY > safeMax -> pdfVerticalOverscrollResist(
            requestedPanPx = requestedPanY,
            hardLimitPx = safeMax,
            viewportHeightPx = viewportHeightPx,
            outwardSign = 1,
        )
        else -> requestedPanY
    }
}

/** The outermost pan value reachable past [hardLimitPx], for use as an `Animatable` bound. */
fun pdfVerticalOverscrollLimitPx(
    hardLimitPx: Float,
    viewportHeightPx: Float,
    outwardSign: Int = -1,
): Float {
    val safeHard = hardLimitPx.takeIf { it.isFinite() } ?: 0f
    return safeHard + outwardSign * pdfVerticalOverscrollStretchPx(viewportHeightPx)
}

/** Advancing ownership prevents a canceled animation from finishing as the current owner. */
fun nextPdfVerticalCameraEpoch(currentEpoch: Long): Long =
    if (currentEpoch == Long.MAX_VALUE) 0L else currentEpoch + 1L

/**
 * Once page motion has hidden a selection menu, keep that same menu hidden after motion settles.
 * A new selection resets [suppressedForCurrentSelection] when it installs its new menu state.
 */
fun shouldShowPdfSelectionMenu(
    hasMenu: Boolean,
    isPageMoving: Boolean,
    suppressedForCurrentSelection: Boolean,
): Boolean = hasMenu && !isPageMoving && !suppressedForCurrentSelection

/**
 * Geometry refinement may follow the initial placeholder layout. Only keep treating the camera
 * as fitted while it is still close to the actual fit scale; an absolute threshold breaks
 * landscape documents whose fit scale is below 1.
 */
fun isPdfVerticalZoomNearFit(
    currentZoom: Float,
    fitZoom: Float,
    tolerance: Float = 0.1f,
): Boolean {
    val safeFitZoom = fitZoom.takeIf { it.isFinite() && it > 0f } ?: return false
    val safeCurrentZoom = currentZoom.takeIf { it.isFinite() && it > 0f } ?: return false
    val safeTolerance = tolerance.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    return safeCurrentZoom <= safeFitZoom * (1f + safeTolerance)
}

/**
 * Whether a viewport/layout change should snap the camera back to the fit scale instead of
 * preserving the user's zoom. The camera starts on a placeholder fit (empty page layout),
 * so the first real layout must fit before the placeholder looks like a deliberate zoom.
 */
fun pdfVerticalResizeShouldRefit(
    currentZoom: Float,
    fitZoom: Float,
    isFirstRealLayout: Boolean,
): Boolean {
    if (isFirstRealLayout) return true
    return isPdfVerticalZoomNearFit(currentZoom, fitZoom)
}

/**
 * Base zoom of the vertical reader: the largest zoom where a single page is fully visible.
 * Standard portrait viewports return 1f (page width fills the viewport); short or narrow
 * panes (split view) shrink below 1f so a single page fits without scrolling. Without the
 * whole-page fit, portrait split panes were pinned at 100% and could not zoom out below it.
 */
fun pdfVerticalFitZoomScale(
    pageAspectRatios: List<Float>,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
): Float {
    if (pageAspectRatios.isEmpty() || viewportWidthPx <= 0f || viewportHeightPx <= 0f) return 1f
    val firstRatio = pageAspectRatios.firstOrNull { it > 0f } ?: 1f
    val pageHeightAtFullWidth = viewportWidthPx / firstRatio
    if (pageHeightAtFullWidth <= 0f) return 1f
    return ((viewportHeightPx - 32f) / pageHeightAtFullWidth).coerceAtMost(1f)
}

fun preservedPdfVerticalPanY(
    oldPanY: Float,
    oldZoom: Float,
    newZoom: Float,
    viewportAnchorY: Float,
    oldPageTopY: Float,
    oldPageHeight: Float,
    newPageTopY: Float,
    newPageHeight: Float,
): Float {
    val oldDocumentAnchor = (viewportAnchorY - oldPanY) / oldZoom.coerceAtLeast(0.01f)
    val pageFraction = ((oldDocumentAnchor - oldPageTopY) / oldPageHeight.coerceAtLeast(1f))
        .coerceIn(0f, 1f)
    val newDocumentAnchor = newPageTopY + newPageHeight * pageFraction
    return viewportAnchorY - newDocumentAnchor * newZoom
}

/**
 * Horizontal camera for a viewport whose width changed (split divider drag,
 * rotation, chrome resize). The vertical re-anchor already clamps panY, but a
 * pan preserved from the old width can sit outside the new horizontal range;
 * the next pinch or fling then clamps abruptly and the view jumps to the left
 * edge. Centers under-zoomed content and clamps the preserved pan into the
 * zoomed document's new range.
 */
fun preservedPdfVerticalPanXAfterViewportResize(
    panX: Float,
    zoom: Float,
    viewportWidth: Float,
): Float {
    val safeZoom = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: return 0f
    val zoomedDocWidth = safeViewportWidth * safeZoom
    return if (zoomedDocWidth <= safeViewportWidth) {
        (safeViewportWidth - zoomedDocWidth) / 2f
    } else {
        panX.takeIf { it.isFinite() }?.coerceIn(-(zoomedDocWidth - safeViewportWidth), 0f) ?: 0f
    }
}

fun calculateLockedOrientationResetCamera(
    pageTopY: Float,
    totalDocHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    headerHeightPx: Float,
    footerHeightPx: Float,
    fitZoom: Float,
): PdfLockedOrientationResetCamera {
    val targetPanY = headerHeightPx - (pageTopY * fitZoom)
    val zoomedDocHeight = totalDocHeight * fitZoom
    val minPanY = if (zoomedDocHeight < (screenHeight - headerHeightPx - footerHeightPx)) {
        headerHeightPx
    } else {
        (screenHeight - footerHeightPx - zoomedDocHeight).coerceAtMost(headerHeightPx)
    }
    val finalPanY = targetPanY.coerceIn(minPanY, headerHeightPx)

    val zoomedDocWidth = screenWidth * fitZoom
    val targetPanX = if (zoomedDocWidth < screenWidth) {
        (screenWidth - zoomedDocWidth) / 2f
    } else {
        0f
    }

    return PdfLockedOrientationResetCamera(
        zoom = fitZoom,
        panX = targetPanX,
        panY = finalPanY,
    )
}
