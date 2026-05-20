package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Offset
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfZoomLockStateTest {

    @Test
    fun `paginated locked page waits to report camera until saved lock is applied`() {
        val lockedState = Triple(2.25f, -12f, 32f)

        assertFalse(
            shouldReportPdfPageCamera(
                isZoomEnabled = true,
                isVerticalScroll = false,
                isScrollLocked = true,
                lockedState = lockedState,
                hasAppliedLockedState = false
            )
        )

        assertTrue(
            shouldReportPdfPageCamera(
                isZoomEnabled = true,
                isVerticalScroll = false,
                isScrollLocked = true,
                lockedState = lockedState,
                hasAppliedLockedState = true
            )
        )
    }

    @Test
    fun `paginated locked page initializes from saved camera`() {
        val camera = initialPdfPageCamera(
            isZoomEnabled = true,
            isVerticalScroll = false,
            isScrollLocked = true,
            lockedState = Triple(2.25f, -12f, 32f)
        )

        assertEquals(2.25f, camera.first, 0.0001f)
        assertEquals(-12f, camera.second.x, 0.0001f)
        assertEquals(32f, camera.second.y, 0.0001f)
    }

    @Test
    fun `loading locked preferences primes active camera from saved state`() {
        val camera = activePdfCameraAfterLockPreferenceLoad(
            isScrollLocked = true,
            lockedState = Triple(2.25f, -12f, 32f)
        )

        assertEquals(2.25f, camera.first, 0.0001f)
        assertEquals(-12f, camera.second.x, 0.0001f)
        assertEquals(32f, camera.second.y, 0.0001f)
    }

    @Test
    fun `paginated locked page can report camera when no saved lock exists yet`() {
        assertTrue(
            shouldReportPdfPageCamera(
                isZoomEnabled = true,
                isVerticalScroll = false,
                isScrollLocked = true,
                lockedState = null,
                hasAppliedLockedState = false
            )
        )
    }

    @Test
    fun `bubble zoom cleanup does not reset zoom while scroll lock is on`() {
        assertFalse(
            shouldResetPdfZoomAfterBubbleZoomCleanup(
                isBubbleZoomModeActive = false,
                scale = 1.8f,
                isVerticalScroll = false,
                isZoomEnabled = true,
                isScrollLocked = true
            )
        )
        assertTrue(
            shouldResetPdfZoomAfterBubbleZoomCleanup(
                isBubbleZoomModeActive = false,
                scale = 1.8f,
                isVerticalScroll = false,
                isZoomEnabled = true,
                isScrollLocked = false
            )
        )
    }

    @Test
    fun `page change preserves locked zoom scale only in paginated lock mode`() {
        val lockedState = Triple(2.25f, -12f, 32f)

        assertEquals(
            2.25f,
            currentPageScaleAfterPdfPageChange(
                displayMode = DisplayMode.PAGINATION,
                isScrollLocked = true,
                lockedState = lockedState,
                currentActiveScale = 1f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            currentPageScaleAfterPdfPageChange(
                displayMode = DisplayMode.PAGINATION,
                isScrollLocked = false,
                lockedState = lockedState,
                currentActiveScale = 2.25f
            ),
            0.0001f
        )
        assertEquals(
            1f,
            currentPageScaleAfterPdfPageChange(
                displayMode = DisplayMode.VERTICAL_SCROLL,
                isScrollLocked = true,
                lockedState = lockedState,
                currentActiveScale = 2.25f
            ),
            0.0001f
        )
    }

    @Test
    fun `pdf page range labels preserve single page and spread wording`() {
        val singlePageSettings = ReaderSettings()
        val spreadSettings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)

        assertEquals(
            "5 / 5",
            pdfPageRangeText(
                pageIndex = 99,
                pageCount = 5,
                displayMode = DisplayMode.VERTICAL_SCROLL,
                settings = singlePageSettings
            )
        )
        assertEquals(
            "Page 5 of 5",
            pdfPageRangeLabel(
                pageIndex = 99,
                pageCount = 5,
                displayMode = DisplayMode.VERTICAL_SCROLL,
                settings = singlePageSettings
            )
        )
        assertEquals(
            "3-4 / 10",
            pdfPageRangeText(
                pageIndex = 2,
                pageCount = 10,
                displayMode = DisplayMode.PAGINATION,
                settings = spreadSettings
            )
        )
        assertEquals(
            "Pages 3-4 of 10",
            pdfPageRangeLabel(
                pageIndex = 2,
                pageCount = 10,
                displayMode = DisplayMode.PAGINATION,
                settings = spreadSettings
            )
        )
    }

    @Test
    fun `spread camera clamp keeps offset inside scaled viewport bounds`() {
        val clamped = clampPdfSpreadCameraOffset(
            scale = 2f,
            offset = Offset(100f, -100f),
            viewportWidth = 100f,
            viewportHeight = 80f
        )

        assertEquals(50f, clamped.x, 0.0001f)
        assertEquals(-40f, clamped.y, 0.0001f)
        assertEquals(
            Offset.Zero,
            clampPdfSpreadCameraOffset(
                scale = 1f,
                offset = Offset(20f, 20f),
                viewportWidth = 100f,
                viewportHeight = 80f
            )
        )
    }
}
