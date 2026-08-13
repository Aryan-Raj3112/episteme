package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfInkPointCoalescingTest {
    @Test
    fun `nearby highlighter sample replaces endpoint instead of creating a loop`() {
        val points = listOf(point(0.1f, 0.2f), point(0.5f, 0.2f))
        val next = point(0.502f, 0.201f)

        assertTrue(shouldReplaceLastPdfInkPoint(points, next, PdfInkTool.HIGHLIGHTER, 0.035f))
    }

    @Test
    fun `meaningful highlighter curve samples are appended`() {
        val points = listOf(point(0.1f, 0.2f), point(0.5f, 0.2f))
        val next = point(0.51f, 0.21f)

        assertFalse(shouldReplaceLastPdfInkPoint(points, next, PdfInkTool.HIGHLIGHTER_ROUND, 0.035f))
    }

    @Test
    fun `replacement always follows the newest finger position`() {
        val start = point(0.1f, 0.2f)
        val firstEnd = point(0.5f, 0.2f)
        val newestEnd = point(0.503f, 0.202f)
        assertTrue(
            shouldReplaceLastPdfInkPoint(
                listOf(start, firstEnd), newestEnd, PdfInkTool.HIGHLIGHTER, 0.035f
            )
        )
    }

    @Test
    fun `pen samples remain untouched`() {
        val points = listOf(point(0.1f, 0.2f), point(0.5f, 0.2f))
        val next = point(0.5001f, 0.2001f)
        assertFalse(shouldReplaceLastPdfInkPoint(points, next, PdfInkTool.PEN, 0.035f))
    }

    private fun point(x: Float, y: Float) = PdfPagePoint(x, y, 1L)
}
