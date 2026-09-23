package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Android auto-scroll contract the iOS readers now share: the same
 * 0.1 speed grid, the same one-decimal readout and the same chrome anchoring for
 * EPUB and PDF.
 */
class SharedMobileAutoScrollChromeTest {

    @Test
    fun `stepper increments stay on the 0-1 grid and never leak float noise`() {
        var speed = 3.9f
        repeat(10) { speed = snapSharedMobileAutoScrollSpeed(speed + 0.1f) }

        assertEquals(4.9f, speed)
        // The regression: the raw `speed + 0.1f` value rendered as "3.999999".
        assertEquals("4.0x", sharedMobileAutoScrollSpeedLabel(snapSharedMobileAutoScrollSpeed(3.9f + 0.1f)))
    }

    @Test
    fun `speed labels keep one decimal like the Android readout`() {
        assertEquals("0.1x", sharedMobileAutoScrollSpeedLabel(0.1f))
        assertEquals("0.5x", sharedMobileAutoScrollSpeedLabel(0.5f))
        assertEquals("1.0x", sharedMobileAutoScrollSpeedLabel(1f))
        assertEquals("4.0x", sharedMobileAutoScrollSpeedLabel(4f))
        assertEquals("10.0x", sharedMobileAutoScrollSpeedLabel(10f))
        // Out-of-range values clamp instead of printing nonsense.
        assertEquals("0.1x", sharedMobileAutoScrollSpeedLabel(-3f))
        assertEquals("10.0x", sharedMobileAutoScrollSpeedLabel(42f))
    }

    @Test
    fun `snapping clamps to the Android speed range`() {
        assertEquals(0.1f, snapSharedMobileAutoScrollSpeed(0f))
        assertEquals(10f, snapSharedMobileAutoScrollSpeed(99f))
        assertEquals(3.7f, snapSharedMobileAutoScrollSpeed(3.74f))
    }

    @Test
    fun `epub overlay clears the bottom bar plus the safe inset`() {
        // Android EpubReaderScreen: bottomPadding + 45.dp + 16.dp.
        assertEquals(
            SharedReaderEpubBottomBarHeight + 16.dp + 34.dp,
            sharedMobileEpubAutoScrollBottomPadding(chromeVisible = true, bottomInset = 34.dp)
        )
        // Hidden chrome drops to the Android constant and ignores the inset.
        assertEquals(32.dp, sharedMobileEpubAutoScrollBottomPadding(chromeVisible = false, bottomInset = 34.dp))
    }

    @Test
    fun `pdf overlay clears the bottom bar plus the navigation inset`() {
        // Android PdfViewerScreen: 56.dp + 16.dp + inset, or 16.dp + inset.
        assertEquals(
            SharedReaderPdfBottomBarHeight + 16.dp + 21.dp,
            sharedMobilePdfAutoScrollBottomPadding(chromeVisible = true, bottomInset = 21.dp)
        )
        assertEquals(16.dp + 21.dp, sharedMobilePdfAutoScrollBottomPadding(chromeVisible = false, bottomInset = 21.dp))
    }

    @Test
    fun `collapsed overlay hugs the edge and expanded centres`() {
        assertEquals(1f, sharedMobileAutoScrollAlignmentBias(isCollapsed = true))
        assertEquals(0f, sharedMobileAutoScrollAlignmentBias(isCollapsed = false))
    }
}
