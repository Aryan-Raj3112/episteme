package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfAnnotationExportPolicyTest {
    @Test
    fun `export content requires at least one ink text or highlight item`() {
        assertFalse(hasExportablePdfAnnotationContent(listOf(0, 0), 0, 0))
        assertTrue(hasExportablePdfAnnotationContent(listOf(0, 1), 0, 0))
        assertTrue(hasExportablePdfAnnotationContent(emptyList(), 1, 0))
        assertTrue(hasExportablePdfAnnotationContent(emptyList(), 0, 1))
    }

    @Test
    fun `export choice remains available until sidecars finish loading`() {
        assertTrue(shouldShowPdfAnnotationExportChoice(false, emptyList(), 0, 0))
        assertFalse(shouldShowPdfAnnotationExportChoice(true, emptyList(), 0, 0))
        assertTrue(shouldShowPdfAnnotationExportChoice(true, listOf(1), 0, 0))
    }

    @Test
    fun `annotation export accepts only unchanged original pdf page order`() {
        assertTrue(supportsOriginalPdfPageOrder(null))
        assertTrue(
            supportsOriginalPdfPageOrder(
                listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Pdf(1), PdfPageIdentity.Pdf(2)),
            ),
        )
        assertFalse(supportsOriginalPdfPageOrder(listOf(PdfPageIdentity.Pdf(1))))
        assertFalse(
            supportsOriginalPdfPageOrder(
                listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Blank("blank"), PdfPageIdentity.Pdf(1)),
            ),
        )
    }
}
