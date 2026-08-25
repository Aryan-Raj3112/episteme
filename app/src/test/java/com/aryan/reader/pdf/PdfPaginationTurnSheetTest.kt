package com.aryan.reader.pdf

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PdfPaginationTurnSheetTest {

    @Test
    fun `portrait page in tall slot fills the width`() {
        val size = pdfPaginationTurnSheetSize(availableWidth = 400.dp, availableHeight = 800.dp, aspectRatio = 0.5f)
        assertEquals(400f, size.width.value, 0.001f)
        assertEquals(800f, size.height.value, 0.001f)
    }

    @Test
    fun `wide page in tall slot is limited by the slot width`() {
        val size = pdfPaginationTurnSheetSize(availableWidth = 400.dp, availableHeight = 800.dp, aspectRatio = 2f)
        assertEquals(400f, size.width.value, 0.001f)
        assertEquals(200f, size.height.value, 0.001f)
    }

    @Test
    fun `portrait page in wide slot is limited by the slot height`() {
        val size = pdfPaginationTurnSheetSize(availableWidth = 800.dp, availableHeight = 400.dp, aspectRatio = 0.5f)
        assertEquals(200f, size.width.value, 0.001f)
        assertEquals(400f, size.height.value, 0.001f)
    }

    @Test
    fun `square page in square slot fills exactly`() {
        val size = pdfPaginationTurnSheetSize(availableWidth = 600.dp, availableHeight = 600.dp, aspectRatio = 1f)
        assertEquals(600f, size.width.value, 0.001f)
        assertEquals(600f, size.height.value, 0.001f)
    }

    @Test
    fun `mixed page sizes produce per page sheets`() {
        val slotWidth = 400.dp
        val slotHeight = 800.dp
        val portrait = pdfPaginationTurnSheetSize(slotWidth, slotHeight, aspectRatio = 0.5f)
        val landscape = pdfPaginationTurnSheetSize(slotWidth, slotHeight, aspectRatio = 1.414f)

        assertEquals(400f, portrait.width.value, 0.001f)
        assertEquals(800f, portrait.height.value, 0.001f)
        assertEquals(400f, landscape.width.value, 0.001f)
        assertEquals(282.885f, landscape.height.value, 0.01f)
        assertNotEquals(portrait, landscape)
    }

    @Test
    fun `invalid aspect ratio falls back to square`() {
        val size = pdfPaginationTurnSheetSize(availableWidth = 300.dp, availableHeight = 500.dp, aspectRatio = 0f)
        assertEquals(300f, size.width.value, 0.001f)
        assertEquals(300f, size.height.value, 0.001f)
    }
}
