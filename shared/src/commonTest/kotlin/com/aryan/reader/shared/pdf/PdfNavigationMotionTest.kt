package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfNavigationMotionTest {
    @Test
    fun `pagination utility jumps are immediate like Android`() {
        assertFalse(PdfNavigationReason.INITIAL.animatesPagination())
        assertFalse(PdfNavigationReason.TABLE_OF_CONTENTS.animatesPagination())
        assertFalse(PdfNavigationReason.SEARCH_RESULT.animatesPagination())
        assertFalse(PdfNavigationReason.PAGE_SLIDER.animatesPagination())
    }

    @Test
    fun `pagination spatial reading transitions animate like Android`() {
        assertTrue(PdfNavigationReason.INTERNAL_LINK.animatesPagination())
        assertTrue(PdfNavigationReason.JUMP_HISTORY.animatesPagination())
        assertTrue(PdfNavigationReason.TTS.animatesPagination())
        assertTrue(PdfNavigationReason.PAGE_TURN.animatesPagination())
    }

    @Test
    fun `vertical destination offset centers requested point in one scroll`() {
        assertEquals(500, centeredPdfPageScrollOffset(viewportHeightPx = 1000, pageHeightPx = 2000))
        assertEquals(100, centeredPdfPageScrollOffset(viewportHeightPx = 1000, pageHeightPx = 2000, pageFraction = 0.3f))
        assertEquals(-500, centeredPdfPageScrollOffset(viewportHeightPx = 1000, pageHeightPx = 2000, pageFraction = -1f))
    }
}
