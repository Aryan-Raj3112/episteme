package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioural tests for the fixes behind issue #484 (PDF vertical scrolling micro-hops).
 * See `docs/pdf-vertical-scroll-and-highlight-audit.md` §2.
 */
class PdfVerticalScrollPolicyTest {

    // --- prefetch window ---------------------------------------------------------------------

    /**
     * The window must never be smaller than the old flat half screen, or a slow scroll would
     * regress. It has to be at least as large, in both directions, at rest.
     */
    @Test
    fun idleWindowIsAtLeastTheOldHalfScreenBuffer() {
        val height = 2400f
        val window = pdfVerticalScrollPrefetchWindow(height, flingVelocityYPxPerSec = 0f)
        assertTrue(
            window.behindPx >= height * 0.5f,
            "behind must not regress below the old 0.5 screen buffer: ${window.behindPx}",
        )
        assertTrue(
            window.aheadPx >= height * 0.5f,
            "ahead must not regress below the old 0.5 screen buffer: ${window.aheadPx}",
        )
    }

    /**
     * The point of the change: a fling must get more runway ahead of it than a resting camera,
     * otherwise pages still enter the composed set only as they become visible.
     */
    @Test
    fun fastFlingGetsMoreRunwayAheadThanIdle() {
        val height = 2400f
        val idle = pdfVerticalScrollPrefetchWindow(height, 0f)
        val slow = pdfVerticalScrollPrefetchWindow(height, 1500f)
        val fast = pdfVerticalScrollPrefetchWindow(height, 9000f)
        assertTrue(slow.aheadPx > idle.aheadPx, "1500px/s must widen the window")
        assertTrue(fast.aheadPx > slow.aheadPx, "9000px/s must widen it further")
        assertTrue(fast.behindPx > idle.behindPx, "behind must widen too")
    }

    /** Direction must not matter: the camera speed is a magnitude. */
    @Test
    fun prefetchWindowIgnoresFlingDirection() {
        val height = 2400f
        assertEquals(
            pdfVerticalScrollPrefetchWindow(height, 8000f),
            pdfVerticalScrollPrefetchWindow(height, -8000f),
        )
    }

    /** A violent fling must not be able to compose the whole document. */
    @Test
    fun prefetchWindowIsCapped() {
        val height = 2400f
        val maxWindow = height * PdfVerticalPrefetchWindow.MAX_VIEWPORTS
        listOf(1f, 5_000f, 50_000f, 500_000f, Float.MAX_VALUE).forEach { velocity ->
            val window = pdfVerticalScrollPrefetchWindow(height, velocity)
            assertTrue(
                window.aheadPx <= maxWindow + 0.01f,
                "velocity=$velocity ahead=${window.aheadPx} exceeded cap $maxWindow",
            )
            assertTrue(
                window.behindPx <= maxWindow + 0.01f,
                "velocity=$velocity behind=${window.behindPx} exceeded cap $maxWindow",
            )
        }
    }

    /** Behind must stay well under ahead: a page the camera has passed is unlikely to be revisited. */
    @Test
    fun trailingRunwayStaysBelowLeadingRunway() {
        listOf(0f, 2_000f, 10_000f, 40_000f).forEach { velocity ->
            val window = pdfVerticalScrollPrefetchWindow(2400f, velocity)
            assertTrue(
                window.aheadPx >= window.behindPx,
                "velocity=$velocity ahead=${window.aheadPx} behind=${window.behindPx}",
            )
        }
    }

    /** A garbage reading must fall back to the idle window, never produce a degenerate one. */
    @Test
    fun prefetchWindowSurvivesInvalidInput() {
        val idle = pdfVerticalScrollPrefetchWindow(2400f, 0f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { velocity ->
            assertEquals(
                idle,
                pdfVerticalScrollPrefetchWindow(2400f, velocity),
                "velocity=$velocity must fall back to idle",
            )
        }
        val zeroHeight = pdfVerticalScrollPrefetchWindow(0f, 5_000f)
        assertEquals(0f, zeroHeight.aheadPx, 0.001f)
        assertEquals(0f, zeroHeight.behindPx, 0.001f)
        val negativeHeight = pdfVerticalScrollPrefetchWindow(-100f, 5_000f)
        assertEquals(0f, negativeHeight.aheadPx, 0.001f)
    }

    // --- base-render deferral ---------------------------------------------------------------

    /**
     * A page entering the window mid-fling must not start a 3000px pdfium render. Those renders all
     * serialize on one global mutex, so a fling that renders eagerly becomes a queue that keeps
     * stealing frames long after the fling ended.
     */
    @Test
    fun baseRenderDefersWhileScrollingForPagesWithoutABitmap() {
        assertTrue(
            shouldDeferPdfBaseRender(
                isScrolling = true,
                hasBitmap = false,
                isActivePage = false,
            )
        )
    }

    /** Nothing left to do if the page already has a bitmap; never defer. */
    @Test
    fun baseRenderNeverDefersAPageThatAlreadyHasABitmap() {
        assertFalse(
            shouldDeferPdfBaseRender(
                isScrolling = true,
                hasBitmap = true,
                isActivePage = false,
            )
        )
    }

    /** The page under the user's finger must render immediately or a tap-to-page shows an empty page. */
    @Test
    fun baseRenderNeverDefersTheActivePage() {
        assertFalse(
            shouldDeferPdfBaseRender(
                isScrolling = true,
                hasBitmap = false,
                isActivePage = true,
            )
        )
    }

    @Test
    fun baseRenderNeverDefersWhenIdle() {
        assertFalse(
            shouldDeferPdfBaseRender(
                isScrolling = false,
                hasBitmap = false,
                isActivePage = false,
            )
        )
    }
}