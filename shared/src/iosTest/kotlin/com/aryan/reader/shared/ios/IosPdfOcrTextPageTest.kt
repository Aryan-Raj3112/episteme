package com.aryan.reader.shared.ios

import com.aryan.reader.shared.pdf.IosPdfOcrWord
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.buildIosPdfOcrTextPage
import com.aryan.reader.shared.pdf.boundsForRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfOcrTextPageTest {
    @Test
    fun wordsBecomeSelectableUtf16TextWithLineBreaks() {
        val page = buildIosPdfOcrTextPage(
            listOf(
                IosPdfOcrWord("Scanned", PdfPageBounds(0.1f, 0.1f, 0.4f, 0.16f)),
                IosPdfOcrWord("PDF", PdfPageBounds(0.42f, 0.1f, 0.55f, 0.16f)),
                IosPdfOcrWord("page", PdfPageBounds(0.1f, 0.25f, 0.28f, 0.31f)),
            ),
        )

        assertEquals("Scanned PDF\npage", page.text)
        assertEquals(page.text.length, page.characterBounds.size)
        // characterBounds are per-character slices of each Vision word, not whole-word rects.
        val firstCharWidth = (0.4f - 0.1f) / "Scanned".length
        val first = page.characterBounds.first()
        assertEquals(0.1f, first.left, 1e-5f)
        assertEquals(0.1f, first.top, 1e-5f)
        assertEquals(0.1f + firstCharWidth, first.right, 1e-5f)
        assertEquals(0.16f, first.bottom, 1e-5f)
        // The first word's characters reassemble to the original word bounds.
        val firstWord = page.characterBounds.take("Scanned".length)
        assertEquals(0.1f, firstWord.minOf { it.left }, 1e-5f)
        assertEquals(0.4f, firstWord.maxOf { it.right }, 1e-5f)
    }

    @Test
    fun ocrPageSupportsWordSelectionAndRangeGeometry() {
        val page = buildIosPdfOcrTextPage(
            listOf(IosPdfOcrWord("Select", PdfPageBounds(0.1f, 0.2f, 0.5f, 0.27f))),
        )
        assertTrue(page.text.isNotBlank())
        assertEquals(1, page.boundsForRange(0, page.text.length).size)
        assertEquals(0.1f, page.boundsForRange(0, page.text.length).single().left)
    }
}
