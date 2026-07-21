package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfTtsSessionTest {
    @Test
    fun `page chunks retain exact source offsets`() {
        val source = "First sentence.  Second sentence!\nThird sentence?"
        val page = PdfTtsSessionPlanner.page(4, source)

        assertTrue(page.chunks.isNotEmpty())
        page.chunks.forEach { chunk ->
            assertEquals(chunk.text, source.substring(chunk.startOffset, chunk.endOffset))
            assertEquals(4, chunk.pageIndex)
        }
    }

    @Test
    fun `starting inside a chunk trims text and offset`() {
        val source = "Read the beginning and continue to the end."
        val start = source.indexOf("continue")
        val first = PdfTtsSessionPlanner.page(0, source, start).chunks.first()

        assertEquals(start, first.startOffset)
        assertTrue(first.text.startsWith("continue"))
    }

    @Test
    fun `page continuation stops at document end`() {
        assertEquals(3, PdfTtsSessionPlanner.nextPage(2, 5))
        assertNull(PdfTtsSessionPlanner.nextPage(4, 5))
    }

    @Test
    fun `highlight range is clamped to loaded text page`() {
        val chunk = PdfTtsSessionPlanner.page(0, "A complete sentence.").chunks.first()
        assertEquals(PdfTextSelectionRange(0, 5), PdfTtsSessionPlanner.highlightRange(chunk.copy(endOffset = 99), 5))
    }
}
