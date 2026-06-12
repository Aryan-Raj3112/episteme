package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPopupSizingTest {

    @Test
    fun `modal max height leaves edge margin on landscape-height screens`() {
        assertEquals(306, readerModalMaxHeightDp(screenHeightDp = 360))
    }

    @Test
    fun `modal max height uses preferred minimum when there is room`() {
        assertEquals(220, readerModalMaxHeightDp(screenHeightDp = 252))
    }

    @Test
    fun `modal max height stays within tiny screens`() {
        assertEquals(168, readerModalMaxHeightDp(screenHeightDp = 200))
    }
}
