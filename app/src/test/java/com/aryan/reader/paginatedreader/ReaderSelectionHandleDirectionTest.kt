package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionHandleDirectionTest {

    @Test
    fun `selection handle overlay uses a physical left origin in rtl app locales`() {
        assertEquals(
            IntOffset.Zero,
            ReaderSelectionHandleOverlayAlignment.align(
                size = IntSize(36, 36),
                space = IntSize(1080, 1920),
                layoutDirection = LayoutDirection.Rtl
            )
        )
    }
}
