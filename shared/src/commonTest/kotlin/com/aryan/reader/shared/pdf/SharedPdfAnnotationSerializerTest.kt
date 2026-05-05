package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfAnnotationSerializerTest {

    @Test
    fun `serializer round trips text highlight annotations`() {
        val annotation = SharedPdfAnnotation(
            id = "highlight",
            pageIndex = 3,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            bounds = PdfPageBounds(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.24f),
            text = "Selected text",
            colorArgb = 0x8CFFEB3B.toInt(),
            createdAt = 42L
        )

        val decoded = SharedPdfAnnotationSerializer.decode(
            SharedPdfAnnotationSerializer.encode(listOf(annotation))
        )

        assertEquals(listOf(annotation), decoded)
    }
}
