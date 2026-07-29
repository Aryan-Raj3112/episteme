package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfTtsSessionTest {
    @Test
    fun `manual pagination stops active pdf speech but automatic and vertical movement do not`() {
        assertTrue(shouldStopPdfTtsForManualPageTurn(true, true, true))
        assertFalse(shouldStopPdfTtsForManualPageTurn(true, false, true))
        assertFalse(shouldStopPdfTtsForManualPageTurn(false, true, true))
        assertFalse(shouldStopPdfTtsForManualPageTurn(true, true, false))
    }

    @Test
    fun `pdf auto scroll uses android base speed and bounds`() {
        assertEquals(4f, pdfAutoScrollPixelsPerSecond(0f))
        assertEquals(40f, pdfAutoScrollPixelsPerSecond(1f))
        assertEquals(120f, pdfAutoScrollPixelsPerSecond(3f))
        assertEquals(400f, pdfAutoScrollPixelsPerSecond(20f))
    }

    @Test
    fun `pdf auto scroll profile enforces android min max correction rules`() {
        val profile = PdfAutoScrollProfile(speed = 3f, minSpeed = 1f, maxSpeed = 8f)

        assertEquals(
            PdfAutoScrollProfile(speed = 9f, minSpeed = 9f, maxSpeed = 9f),
            profile.withMinSpeed(9f),
        )
        assertEquals(
            PdfAutoScrollProfile(speed = 0.5f, minSpeed = 0.5f, maxSpeed = 0.5f),
            profile.withMaxSpeed(0.5f),
        )
        assertEquals(
            PdfAutoScrollProfile(speed = 3f, minSpeed = 1f, maxSpeed = 8f),
            PdfAutoScrollProfile(speed = 3f, minSpeed = 1f, maxSpeed = 8f).sanitized(),
        )
    }

    @Test
    fun `pdf musician gestures match android jump targets and pauses`() {
        assertEquals(
            PdfMusicianGesturePlan(PdfMusicianNavigationTarget.RELATIVE, -0.75f, 600L),
            planPdfMusicianGesture(isRightRegion = false, isLongPress = false),
        )
        assertEquals(
            PdfMusicianGesturePlan(PdfMusicianNavigationTarget.RELATIVE, 0.75f, 600L),
            planPdfMusicianGesture(isRightRegion = true, isLongPress = false),
        )
        assertEquals(PdfMusicianNavigationTarget.START, planPdfMusicianGesture(false, true).target)
        assertEquals(PdfMusicianNavigationTarget.END, planPdfMusicianGesture(true, true).target)
        assertEquals(1_000L, PdfMusicianHoldDurationMillis)
    }

    @Test
    fun `page chunks retain exact source offsets`() {
        val source = "First sentence.  Second sentence!\nThird sentence?"
        val page = PdfTtsSessionPlanner.page(4, source)

        assertTrue(page.chunks.isNotEmpty())
        page.chunks.forEach { chunk ->
            assertEquals(chunk.text, source.substring(chunk.startOffset, chunk.endOffset))
            assertEquals(4, chunk.pageIndex)
        }
    }

    @Test
    fun `starting inside a chunk trims text and offset`() {
        val source = "Read the beginning and continue to the end."
        val start = source.indexOf("continue")
        val first = PdfTtsSessionPlanner.page(0, source, start).chunks.first()

        assertEquals(start, first.startOffset)
        assertTrue(first.text.startsWith("continue"))
    }

    @Test
    fun `page continuation stops at document end`() {
        assertEquals(3, PdfTtsSessionPlanner.nextPage(2, 5))
        assertNull(PdfTtsSessionPlanner.nextPage(4, 5))
    }

    @Test
    fun `highlight range is clamped to loaded text page`() {
        val chunk = PdfTtsSessionPlanner.page(0, "A complete sentence.").chunks.first()
        assertEquals(PdfTextSelectionRange(0, 5), PdfTtsSessionPlanner.highlightRange(chunk.copy(endOffset = 99), 5))
    }
}
