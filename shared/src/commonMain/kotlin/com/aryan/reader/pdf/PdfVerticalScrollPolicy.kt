package com.aryan.reader.pdf

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Prefetch window for the PDF vertical reader, in document pixels measured from the viewport edges.
 *
 * The reader composes only the pages inside this window, so its size directly decides how much
 * render runway a fling has. The old window was a flat half screen in both directions, which a fast
 * fling outruns immediately: pages entered the composed set only as they became visible, so each
 * one paid for a `BoxWithConstraints` subcomposition, a pdfium base render through the single global
 * pdfium mutex, a half-size thumbnail scale, and (in reverse-colour mode) a full-page CPU pixel
 * transform, all while the fling was still animating.
 *
 * The window is asymmetric and grows with speed, which is what a `LazyList` does with
 * `beyondViewportPageCount` plus its scroll-ahead prefetch. `aheadPx` covers the distance the camera
 * is expected to travel before the next frame, and both directions keep a floor so a slow scroll
 * still gets runway.
 */
data class PdfVerticalPrefetchWindow(
    val aheadPx: Float,
    val behindPx: Float,
) {
    companion object {
        /** Fraction of the viewport kept in front of the camera when it is not moving. */
        const val IDLE_AHEAD_FRACTION: Float = 0.75f

        /** Fraction of the viewport kept behind the camera when it is not moving. */
        const val IDLE_BEHIND_FRACTION: Float = 0.5f

        /**
         * Seconds of camera travel the leading edge of the window covers, on top of the idle
         * fraction. Sized so a hard fling stays inside the window for roughly a third of a second,
         * which is enough for a page to compose and finish its base render.
         */
        const val FLING_LOOKAHEAD_SECONDS: Float = 0.3f

        /** Multiplier on the velocity term for the trailing edge. Always below 1: once a page is
         * behind the camera the user is unlikely to come back to it during this fling. */
        const val BEHIND_VELOCITY_SCALE: Float = 0.35f

        /** Speed at which the velocity term reaches one full viewport of extra runway. */
        const val FULL_RUNWAY_VELOCITY_PX_PER_SEC: Float = 12_000f

        /** Absolute cap so a violent fling cannot compose the whole document. */
        const val MAX_VIEWPORTS: Float = 2f
    }
}

/**
 * Resolves the prefetch window for the current camera speed.
 *
 * Non-finite or absent velocity is treated as idle, so a bad reading can only ever fall back to the
 * previous flat window rather than producing a degenerate one.
 */
fun pdfVerticalScrollPrefetchWindow(
    viewportHeightPx: Float,
    flingVelocityYPxPerSec: Float,
): PdfVerticalPrefetchWindow {
    val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    if (height <= 0f) return PdfVerticalPrefetchWindow(0f, 0f)

    val idleAhead = height * PdfVerticalPrefetchWindow.IDLE_AHEAD_FRACTION
    val idleBehind = height * PdfVerticalPrefetchWindow.IDLE_BEHIND_FRACTION
    val velocity = abs(flingVelocityYPxPerSec).takeIf { it.isFinite() } ?: 0f

    // Ramp the velocity term in linearly to one extra viewport, then clamp.
    val velocityShare = (velocity / PdfVerticalPrefetchWindow.FULL_RUNWAY_VELOCITY_PX_PER_SEC)
        .coerceIn(0f, 1f)
    val extraViewport = height * PdfVerticalPrefetchWindow.MAX_VIEWPORTS * velocityShare

    val ahead = idleAhead + velocity * PdfVerticalPrefetchWindow.FLING_LOOKAHEAD_SECONDS
    val behind = idleBehind + extraViewport * PdfVerticalPrefetchWindow.BEHIND_VELOCITY_SCALE

    val maxWindow = height * PdfVerticalPrefetchWindow.MAX_VIEWPORTS
    return PdfVerticalPrefetchWindow(
        aheadPx = ahead.coerceIn(idleAhead, maxWindow),
        behindPx = behind.coerceIn(idleBehind, maxWindow),
    )
}

/**
 * Whether a page should render its base bitmap now, or defer until the reader is idle.
 *
 * A page entering the window mid-fling should not immediately queue a 3000px pdfium render: those
 * renders all serialize on one global mutex, so a fling that renders eagerly turns into a queue of
 * renders that finish long after the fling has stopped and steal frames from the animation that is
 * still running. Deferring costs nothing visible, because the page is behind the camera or only just
 * entering it, and `PdfThumbnailCache` already supplies a half-resolution bitmap to show meanwhile.
 *
 * The tiles use the same rule already (`shouldPauseHighResTileRendering`); this extends it to the
 * base render so the two cannot disagree.
 */
fun shouldDeferPdfBaseRender(
    isScrolling: Boolean,
    hasBitmap: Boolean,
    isActivePage: Boolean,
): Boolean {
    // An already-rendered page is never deferred: there is nothing left to do.
    if (hasBitmap) return false
    // The page the user is looking at has to render even mid-gesture, or a tap-to-page / restore
    // would show an empty page.
    if (isActivePage) return false
    return isScrolling
}