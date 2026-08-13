package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfPageLinkPolicyTest {
    @Test
    fun `render identity includes document and virtual source page`() {
        assertEquals("book:PDF_3", pdfRenderPageId("book", 3, null))
        assertEquals("book:PDF_12", pdfRenderPageId("book", 3, PdfPageIdentity.Pdf(12)))
        assertEquals("book:BLANK_note", pdfRenderPageId("book", 3, PdfPageIdentity.Blank("note")))
        assertTrue(pdfRenderPageId("one", 0, null) != pdfRenderPageId("two", 0, null))
    }

    @Test
    fun `link construction preserves Android target and tap-padding rules`() {
        val link = buildPdfPageLink(
            highlightBounds = PdfIntBounds(10, 20, 40, 50),
            verticalTapPaddingPx = 7,
            url = null,
            destPageIdx = 3,
            source = LinkSource.ANNOTATION,
        )

        assertEquals(PdfIntBounds(10, 20, 40, 50), link?.highlightBounds)
        assertEquals(PdfIntBounds(10, 13, 40, 57), link?.tapBounds)
        assertTrue(link!!.tapBounds.contains(10, 13))
        assertFalse(link.tapBounds.contains(40, 13))
    }

    @Test
    fun `invalid targets and empty mapped rectangles are skipped`() {
        assertFalse(isActionablePdfLinkTarget(null, -1))
        assertTrue(isActionablePdfLinkTarget("", null))
        assertNull(buildPdfPageLink(PdfIntBounds(0, 0, 0, 20), 4, "https://example.com", null, LinkSource.TEXT_CONTENT))
        assertNull(buildPdfPageLink(PdfIntBounds(0, 0, 20, 20), 4, null, -1, LinkSource.ANNOTATION))
    }
}
