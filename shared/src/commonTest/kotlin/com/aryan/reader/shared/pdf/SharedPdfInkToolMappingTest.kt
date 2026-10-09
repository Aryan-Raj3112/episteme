package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The annotation dock resolves a tap through [SharedPdfInkToolMapping] before it reaches the
 * tool-settings branch, so a lossy entry here does not fail loudly — it silently routes the tap to
 * the wrong tool. `SELECT` did exactly that: it mapped to `PEN` in both directions, so tapping the
 * dock's lasso button arrived as pen and, when a pen was already active, took the
 * "already-active tool toggles its settings" branch and opened the ink popup.
 */
class SharedPdfInkToolMappingTest {

    @Test
    fun `every ink tool round-trips through the mapping`() {
        val tools = listOf(
            PdfInkTool.PEN,
            PdfInkTool.FOUNTAIN_PEN,
            PdfInkTool.PENCIL,
            PdfInkTool.HIGHLIGHTER,
            PdfInkTool.HIGHLIGHTER_ROUND,
            PdfInkTool.ERASER,
            PdfInkTool.TEXT,
            PdfInkTool.SELECT,
        )
        for (tool in tools) {
            val name = SharedPdfInkToolMapping.toAndroidInkTypeName(tool)
            assertEquals(
                tool,
                SharedPdfInkToolMapping.toSharedPdfInkTool(name),
                "$tool did not survive the round-trip through \"$name\"",
            )
        }
    }

    @Test
    fun `select mode survives the round-trip`() {
        assertEquals("SELECT", SharedPdfInkToolMapping.toAndroidInkTypeName(PdfInkTool.SELECT))
        assertEquals(
            PdfInkTool.SELECT,
            SharedPdfInkToolMapping.toSharedPdfInkTool("SELECT"),
        )
    }

    @Test
    fun `select is distinct from pen so a lasso tap cannot resolve to pen`() {
        assertEquals(
            PdfInkTool.PEN,
            SharedPdfInkToolMapping.toSharedPdfInkTool("PEN"),
        )
        // The dock's SELECT cell must not collapse onto the pen group, or tapping it recalls
        // "the last pen tool" instead of entering selection mode.
        assertEquals(false, PdfInkTool.SELECT in SharedPdfAnnotationPenTools)
        assertEquals(false, PdfInkTool.SELECT in SharedPdfAnnotationHighlighterTools)
        assertEquals(
            PdfInkTool.SELECT,
            resolveSharedPdfAnnotationDockToolClick(
                selectedTool = PdfInkTool.PEN,
                clickedGroup = PdfInkTool.SELECT,
                lastPenTool = PdfInkTool.FOUNTAIN_PEN,
                lastHighlighterTool = PdfInkTool.HIGHLIGHTER,
            ),
        )
    }

    @Test
    fun `none has no ink-type counterpart and falls back to pen`() {
        assertEquals("PEN", SharedPdfInkToolMapping.toAndroidInkTypeName(PdfInkTool.NONE))
    }

    @Test
    fun `an unknown persisted name falls back to pen rather than throwing`() {
        assertEquals(PdfInkTool.PEN, SharedPdfInkToolMapping.toSharedPdfInkTool("SOMETHING_NEW"))
        assertEquals(PdfInkTool.PEN, SharedPdfInkToolMapping.toSharedPdfInkTool(""))
    }
}
