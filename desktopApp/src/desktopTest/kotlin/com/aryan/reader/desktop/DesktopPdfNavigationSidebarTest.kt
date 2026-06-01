package com.aryan.reader.desktop

import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopPdfNavigationSidebarTest {
    @Test
    fun `sidebar highlights exclude ink and text annotations`() {
        val result = desktopPdfSidebarHighlights(
            listOf(
                annotation(id = "ink", pageIndex = 0, kind = PdfAnnotationKind.INK, createdAt = 1L),
                annotation(id = "later-highlight", pageIndex = 2, kind = PdfAnnotationKind.HIGHLIGHT, createdAt = 4L),
                annotation(id = "text", pageIndex = 1, kind = PdfAnnotationKind.TEXT, createdAt = 1L),
                annotation(
                    id = "first-same-page-highlight",
                    pageIndex = 1,
                    kind = PdfAnnotationKind.HIGHLIGHT,
                    createdAt = 3L
                ),
                annotation(
                    id = "second-same-page-highlight",
                    pageIndex = 1,
                    kind = PdfAnnotationKind.HIGHLIGHT,
                    createdAt = 2L
                )
            )
        )

        assertEquals(
            listOf("first-same-page-highlight", "second-same-page-highlight", "later-highlight"),
            result.map { it.id }
        )
        assertTrue(result.all { it.kind == PdfAnnotationKind.HIGHLIGHT })
    }

    private fun annotation(
        id: String,
        pageIndex: Int,
        kind: PdfAnnotationKind,
        createdAt: Long
    ): SharedPdfAnnotation {
        return SharedPdfAnnotation(
            id = id,
            pageIndex = pageIndex,
            kind = kind,
            tool = if (kind == PdfAnnotationKind.TEXT) PdfInkTool.TEXT else PdfInkTool.HIGHLIGHTER,
            text = "selected text",
            colorArgb = 0x55FFEB3B,
            createdAt = createdAt
        )
    }
}
