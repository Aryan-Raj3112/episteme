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
        assertEquals(PdfPageBounds(0.1f, 0.1f, 0.4f, 0.16f), page.characterBounds.first())
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
