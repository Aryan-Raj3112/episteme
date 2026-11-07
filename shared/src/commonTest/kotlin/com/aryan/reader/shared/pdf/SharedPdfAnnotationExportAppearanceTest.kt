package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.HighlightStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPdfAnnotationExportAppearanceTest {

    @Test
    fun `highlighter keeps its stored render alpha`() {
        // The palette stores SharedPdfHighlighterPalette.DefaultAlpha and the reader draws
        // the stroke at that alpha with Multiply, so the export must not change it.
        val stored = 0x8CFFEB3B.toInt()

        assertEquals(stored, sharedPdfInkExportColorArgb(PdfInkTool.HIGHLIGHTER, stored))
        assertEquals(stored, sharedPdfInkExportColorArgb(PdfInkTool.HIGHLIGHTER_ROUND, stored))
    }

    @Test
    fun `highlighter alpha is capped and never fully transparent`() {
        // Legacy strokes can be stored opaque; exporting them solid is what smeared text.
        assertEquals(
            0x8CFFEB3B.toInt(),
            sharedPdfInkExportColorArgb(PdfInkTool.HIGHLIGHTER, 0xFFFFEB3B.toInt()),
        )
        assertEquals(
            0x8CFFEB3B.toInt(),
            sharedPdfInkExportColorArgb(PdfInkTool.HIGHLIGHTER, 0x00FFEB3B),
        )
    }

    @Test
    fun `pen keeps whatever alpha it was stored with`() {
        assertEquals(0xFFFF0000.toInt(), sharedPdfInkExportColorArgb(PdfInkTool.PEN, 0xFFFF0000.toInt()))
        assertEquals(0x80FF0000.toInt(), sharedPdfInkExportColorArgb(PdfInkTool.PEN, 0x80FF0000.toInt()))
        assertEquals(0x00FF0000, sharedPdfInkExportColorArgb(PdfInkTool.PENCIL, 0x00FF0000))
    }

    @Test
    fun `ink dict colour keeps the alpha strictly below one`() {
        // FPDFAnnot_SetAP only writes the appearance /Resources while /CA < 1, and Apple's
        // PDFKit paints a resource-less appearance stream black, so an opaque stroke exports
        // one alpha step down while keeping its RGB.
        assertEquals(0xFEFF0000.toInt(), sharedPdfInkAnnotationDictColorArgb(0xFFFF0000.toInt()))
        assertEquals(0xFE336699.toInt(), sharedPdfInkAnnotationDictColorArgb(0xFF336699.toInt()))
    }

    @Test
    fun `ink dict colour preserves translucent and transparent alphas`() {
        assertEquals(0x8CFFEB3B.toInt(), sharedPdfInkAnnotationDictColorArgb(0x8CFFEB3B.toInt()))
        assertEquals(0x00FF0000, sharedPdfInkAnnotationDictColorArgb(0x00FF0000))
    }

    @Test
    fun `stroke width scales by page width with a 0_25pt floor`() {
        assertEquals(21.42f, sharedPdfInkStrokeWidthPdfUnits(0.035f, 612f), 0.0001f)
        assertEquals(0.25f, sharedPdfInkStrokeWidthPdfUnits(0.0001f, 100f), 0.0001f)
        assertEquals(0.25f, sharedPdfInkStrokeWidthPdfUnits(0f, 612f), 0.0001f)
    }

    @Test
    fun `background highlight burns down to the on-screen overlay alpha`() {
        // sharedPdfHighlightAnnotationOverlayStyle caps the stored alpha at RenderAlpha.
        assertEquals(0x66FFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.BACKGROUND, 0xFFFFEB3B.toInt()))
        assertEquals(0x66FFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.BACKGROUND, 0x8CFFEB3B.toInt()))
        assertEquals(0x66FFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.BACKGROUND, 0x00FFEB3B))
    }

    @Test
    fun `background highlight keeps a fainter custom alpha`() {
        assertEquals(0x50FFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.BACKGROUND, 0x50FFEB3B.toInt()))
    }

    @Test
    fun `markup highlight burns opaque colours to 235 without lightening stored alpha`() {
        assertEquals(0xEBFFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.UNDERLINE, 0xFFFFEB3B.toInt()))
        assertEquals(0xEBFFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.WAVY_UNDERLINE, 0xFFFFEB3B.toInt()))
        assertEquals(0xEBFFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.STRIKETHROUGH, 0xFFFFEB3B.toInt()))
        assertEquals(0x8CFFEB3B.toInt(), sharedPdfHighlightExportColorArgb(HighlightStyle.UNDERLINE, 0x8CFFEB3B.toInt()))
    }
}
