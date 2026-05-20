package com.aryan.reader.desktop

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfRichPageLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPdfFileActionsTest {

    @Test
    fun `desktop pdf suggested filename follows android suffix format`() {
        assertEquals(
            "My_PDF_annotated_1234.pdf",
            desktopSuggestedPdfFilename("My PDF.pdf", isAnnotated = true, shortId = "1234")
        )
        assertEquals(
            "My_PDF_1234.pdf",
            desktopSuggestedPdfFilename("My PDF.pdf", isAnnotated = false, shortId = "1234")
        )
    }

    @Test
    fun `desktop pdf export choice waits for sidecars before defaulting to original`() {
        assertTrue(
            shouldShowDesktopPdfAnnotationExportChoice(
                sidecarsReady = false,
                annotations = emptyList(),
                richTextPageLayouts = emptyList()
            )
        )
        assertFalse(
            shouldShowDesktopPdfAnnotationExportChoice(
                sidecarsReady = true,
                annotations = emptyList(),
                richTextPageLayouts = emptyList()
            )
        )
    }

    @Test
    fun `desktop pdf export choice appears for exportable annotations`() {
        val ink = SharedPdfAnnotation(
            id = "ink",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            tool = PdfInkTool.PEN,
            points = listOf(PdfPagePoint(0.1f, 0.1f), PdfPagePoint(0.2f, 0.2f)),
            colorArgb = 0xFF000000.toInt()
        )
        val highlight = SharedPdfAnnotation(
            id = "highlight",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            bounds = PdfPageBounds(0.1f, 0.1f, 0.3f, 0.2f),
            colorArgb = 0x66FFFF00
        )

        assertTrue(
            shouldShowDesktopPdfAnnotationExportChoice(
                sidecarsReady = true,
                annotations = listOf(ink, highlight),
                richTextPageLayouts = emptyList()
            )
        )
    }

    @Test
    fun `desktop pdf export choice ignores non-exportable ink by itself`() {
        val eraser = SharedPdfAnnotation(
            id = "eraser",
            pageIndex = 0,
            kind = PdfAnnotationKind.INK,
            tool = PdfInkTool.ERASER,
            points = listOf(PdfPagePoint(0.1f, 0.1f), PdfPagePoint(0.2f, 0.2f)),
            colorArgb = 0x00000000
        )

        assertFalse(
            shouldShowDesktopPdfAnnotationExportChoice(
                sidecarsReady = true,
                annotations = listOf(eraser),
                richTextPageLayouts = emptyList()
            )
        )
    }

    @Test
    fun `desktop pdf export choice appears for recomputable highlight ranges`() {
        val highlight = SharedPdfAnnotation(
            id = "highlight-range",
            pageIndex = 0,
            kind = PdfAnnotationKind.HIGHLIGHT,
            colorArgb = 0x66FFFF00,
            rangeStartIndex = 4,
            rangeEndIndex = 12
        )

        assertTrue(
            shouldShowDesktopPdfAnnotationExportChoice(
                sidecarsReady = true,
                annotations = listOf(highlight),
                richTextPageLayouts = emptyList()
            )
        )
    }

    @Test
    fun `desktop pdf export choice appears for rich text layouts`() {
        val richLayout = SharedPdfRichPageLayout(
            pageIndex = 0,
            visibleText = AnnotatedString("Margin note"),
            globalStartIndex = 0,
            globalEndIndex = 11,
            pageHeightPx = 1200f
        )

        assertTrue(
            shouldShowDesktopPdfAnnotationExportChoice(
                sidecarsReady = true,
                annotations = emptyList(),
                richTextPageLayouts = listOf(richLayout)
            )
        )
    }

    @Test
    fun `desktop pdf password errors can be detected through wrappers`() {
        assertTrue(DesktopPdfPasswordException("locked.pdf").isDesktopPdfPasswordException())
        assertTrue(RuntimeException(DesktopPdfPasswordException("locked.pdf")).isDesktopPdfPasswordException())
    }
}
