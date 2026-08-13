package com.aryan.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfPageIndexMappingTest {
    @Test
    fun `insertions remap source pages by identity`() {
        val current = listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Pdf(1), PdfPageIdentity.Pdf(2))
        val updated = listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Blank("note"), PdfPageIdentity.Pdf(1), PdfPageIdentity.Pdf(2))
        assertEquals(mapOf(0 to 0, 1 to 2, 2 to 3), buildSharedPdfPageIndexMapping(current, updated, 0..2))
    }

    @Test
    fun `duplicate identities preserve occurrence order`() {
        val current = listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Blank("same"), PdfPageIdentity.Blank("same"), PdfPageIdentity.Pdf(1))
        val updated = listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Blank("same"), PdfPageIdentity.Pdf(1), PdfPageIdentity.Blank("same"))
        assertEquals(mapOf(1 to 1, 2 to 3, 3 to 2), buildSharedPdfPageIndexMapping(current, updated, listOf(1, 2, 3)))
    }

    @Test
    fun `missing legacy layout assumes identity pdf pages`() {
        assertEquals(
            mapOf(2 to 3),
            buildSharedPdfPageIndexMapping(
                currentLayout = emptyList(),
                updatedLayout = listOf(PdfPageIdentity.Pdf(0), PdfPageIdentity.Blank("x"), PdfPageIdentity.Pdf(1), PdfPageIdentity.Pdf(2)),
                sourcePageIndices = listOf(2),
            ),
        )
    }
}
