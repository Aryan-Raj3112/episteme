package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPdfInkAppearanceTest {

    @Test
    fun `appearance stream strokes the path with color and width`() {
        val content = sharedPdfInkAppearanceContent(
            pagePoints = listOf(PdfPagePoint(10f, 20f), PdfPagePoint(30f, 40f)),
            strokeWidthPdfUnits = 2.5f,
            colorArgb = 0xFFFF0000.toInt(),
        )

        assertTrue(content.startsWith("q\n"))
        assertTrue(content.contains("1 0 0 RG"))
        assertTrue(content.contains("2.5 w"))
        assertTrue(content.contains("10 20 m"))
        assertTrue(content.contains("30 40 l"))
        assertTrue(content.endsWith("S\nQ\n"))
        assertTrue(content.contains("1 J\n1 j\n"))
    }

    @Test
    fun `appearance stream is empty for degenerate input`() {
        assertEquals("", sharedPdfInkAppearanceContent(emptyList(), 1f, 0xFF000000.toInt()))
        assertEquals(
            "",
            sharedPdfInkAppearanceContent(listOf(PdfPagePoint(1f, 1f)), 1f, 0xFF000000.toInt()),
        )
        assertEquals(
            "",
            sharedPdfInkAppearanceContent(
                listOf(PdfPagePoint(1f, 1f), PdfPagePoint(2f, 2f)),
                0f,
                0xFF000000.toInt(),
            ),
        )
    }

    @Test
    fun `appearance stream formats non-integral coordinates without locale commas`() {
        val content = sharedPdfInkAppearanceContent(
            pagePoints = listOf(PdfPagePoint(1.25f, 3.5f), PdfPagePoint(4.75f, 5.125f)),
            strokeWidthPdfUnits = 0.25f,
            colorArgb = 0xFF00FF00.toInt(),
        )

        assertTrue(content.contains("1.25 3.5 m"))
        assertTrue(content.contains("4.75 5.125 l"))
        assertTrue(content.contains("0.25 w"))
        assertTrue(content.contains("0 1 0 RG"))
    }
}
