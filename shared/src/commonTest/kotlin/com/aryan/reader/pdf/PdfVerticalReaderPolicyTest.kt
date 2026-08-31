package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfVerticalReaderPolicyTest {
    @Test
    fun `Android benchmark themes resolve exact vertical page backgrounds`() {
        assertEquals(Color.White, resolvePdfVerticalPageBackgroundColor(theme("no_theme", Color.Black)))
        assertEquals(Color.White, resolvePdfVerticalPageBackgroundColor(theme("system", Color.Black)))
        assertEquals(Color.Black, resolvePdfVerticalPageBackgroundColor(theme("reverse", Color.White)))
        assertEquals(Color(0xFFEEE8D5), resolvePdfVerticalPageBackgroundColor(theme("sepia", Color(0xFFEEE8D5))))
        assertEquals(Color.White, resolvePdfVerticalPageBackgroundColor(theme("custom", Color.Unspecified)))
    }

    @Test
    fun `locked reset keeps target page at header using fit zoom`() {
        assertEquals(
            PdfLockedOrientationResetCamera(zoom = 1f, panX = 0f, panY = -960f),
            calculateLockedOrientationResetCamera(
                pageTopY = 1_000f,
                totalDocHeight = 3_000f,
                screenWidth = 800f,
                screenHeight = 1_200f,
                headerHeightPx = 40f,
                footerHeightPx = 60f,
                fitZoom = 1f,
            ),
        )
    }

    @Test
    fun `locked reset centers narrow short document and clamps it below header`() {
        assertEquals(
            PdfLockedOrientationResetCamera(zoom = 0.5f, panX = 250f, panY = 40f),
            calculateLockedOrientationResetCamera(
                pageTopY = 120f,
                totalDocHeight = 500f,
                screenWidth = 1_000f,
                screenHeight = 900f,
                headerHeightPx = 40f,
                footerHeightPx = 60f,
                fitZoom = 0.5f,
            ),
        )
    }

    @Test
    fun `geometry refinement keeps the same point under the viewport anchor`() {
        assertEquals(
            -1_200f,
            preservedPdfVerticalPanY(
                oldPanY = -900f,
                oldZoom = 1f,
                newZoom = 1f,
                viewportAnchorY = 600f,
                oldPageTopY = 1_000f,
                oldPageHeight = 1_000f,
                newPageTopY = 1_200f,
                newPageHeight = 1_200f,
            ),
        )
    }

    @Test
    fun `viewport resize centers under zoomed width and clamps preserved pan`() {
        // Split divider drag shrinks the pane: a pan from the wider viewport
        // must land inside the new bounds or the next pinch jumps to the edge.
        assertEquals(
            -400f,
            preservedPdfVerticalPanXAfterViewportResize(
                panX = -900f,
                zoom = 2f,
                viewportWidth = 400f,
            ),
        )
        // Narrower-than-viewport content stays centered.
        assertEquals(
            100f,
            preservedPdfVerticalPanXAfterViewportResize(
                panX = 0f,
                zoom = 0.5f,
                viewportWidth = 400f,
            ),
        )
        // Valid pans from a same-width viewport are untouched.
        assertEquals(
            -160f,
            preservedPdfVerticalPanXAfterViewportResize(
                panX = -160f,
                zoom = 2f,
                viewportWidth = 400f,
            ),
        )
        // Degenerate inputs fall back to a safe centered camera.
        assertEquals(
            0f,
            preservedPdfVerticalPanXAfterViewportResize(panX = -50f, zoom = 0f, viewportWidth = 400f),
        )
        assertEquals(
            0f,
            preservedPdfVerticalPanXAfterViewportResize(panX = -50f, zoom = 2f, viewportWidth = 0f),
        )
    }

    @Test
    fun `geometry refinement only resets zoom close to landscape fit scale`() {
        assertTrue(isPdfVerticalZoomNearFit(currentZoom = 0.54f, fitZoom = 0.5f))
        assertFalse(isPdfVerticalZoomNearFit(currentZoom = 0.88f, fitZoom = 0.5f))
        assertFalse(isPdfVerticalZoomNearFit(currentZoom = 1.1f, fitZoom = 0.5f))
    }

    @Test
    fun `geometry refinement keeps portrait fit tolerance`() {
        assertTrue(isPdfVerticalZoomNearFit(currentZoom = 1.1f, fitZoom = 1f))
        assertFalse(isPdfVerticalZoomNearFit(currentZoom = 1.11f, fitZoom = 1f))
    }

    @Test
    fun `camera epoch wraps without reusing the active maximum value`() {
        assertEquals(0L, nextPdfVerticalCameraEpoch(Long.MAX_VALUE))
    }

    @Test
    fun `selection menu hidden by motion does not flash back when motion settles`() {
        assertFalse(
            shouldShowPdfSelectionMenu(
                hasMenu = true,
                isPageMoving = true,
                suppressedForCurrentSelection = false,
            )
        )
        assertFalse(
            shouldShowPdfSelectionMenu(
                hasMenu = true,
                isPageMoving = false,
                suppressedForCurrentSelection = true,
            )
        )
        assertTrue(
            shouldShowPdfSelectionMenu(
                hasMenu = true,
                isPageMoving = false,
                suppressedForCurrentSelection = false,
            )
        )
    }

    @Test
    fun `pan acquisition preserves only movement beyond touch slop`() {
        assertEquals(
            Offset(0f, -2f),
            pdfPanAfterTouchSlop(Offset(0f, -12f), touchSlop = 10f),
        )
        assertEquals(
            Offset.Zero,
            pdfPanAfterTouchSlop(Offset(3f, 4f), touchSlop = 10f),
        )
    }

    @Test
    fun `fling axes remain independent when horizontal motion is clamped`() {
        assertEquals(
            PdfFlingVelocity(x = 0f, y = -2400f),
            resolvePdfFlingVelocity(
                rawX = 1800f,
                rawY = -2400f,
                displacementX = 80f,
                displacementY = -160f,
                minimumVelocity = 131f,
                maximumVelocity = 8000f,
                allowHorizontal = false,
            ),
        )
        assertEquals(
            PdfFlingVelocity(x = 1800f, y = -2400f),
            resolvePdfFlingVelocity(
                rawX = 1800f,
                rawY = -2400f,
                displacementX = 80f,
                displacementY = -160f,
                minimumVelocity = 131f,
                maximumVelocity = 8000f,
                allowHorizontal = true,
            ),
        )
    }

    @Test
    fun `release jitter cannot fling opposite the completed gesture`() {
        assertEquals(
            PdfFlingVelocity(x = 0f, y = 0f),
            resolvePdfFlingVelocity(
                rawX = -65f,
                rawY = 559f,
                displacementX = 56f,
                displacementY = -174f,
                minimumVelocity = 131f,
                maximumVelocity = 8000f,
                allowHorizontal = true,
            ),
        )
    }


    private fun theme(id: String, background: Color): ReaderTheme = ReaderTheme(
        id = id,
        name = id,
        backgroundColor = background,
        textColor = Color.Black,
        isDark = id == "reverse",
    )
}
