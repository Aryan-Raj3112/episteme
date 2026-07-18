package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfTextSelectionEngineTest {

    private class StubBackend(
        private val text: String,
        override val pageCharCount: Int = text.length
    ) : PdfTextSelectionBackend {
        override fun charAt(index: Int): Char =
            text.getOrNull(index) ?: '\u0000'

        override fun charIndexAtNormalized(
            normX: Float,
            normY: Float,
            xTolerance: Double,
            yTolerance: Double
        ): Int = -1
    }

    @Test
    fun `wordBoundaries expands around an interior alphanumeric character`() {
        val backend = StubBackend("  Hello, world!  ")
        // Index 9 is 'w'; the run "world" spans indices [9, 14).
        val range = PdfTextSelectionEngine.wordBoundaries(backend, 9)
        assertEquals(PdfTextSelectionRange(9, 14), range)
    }

    @Test
    fun `wordBoundaries returns null when the touched character is not selectable`() {
        val backend = StubBackend("  Hello, world!  ")
        assertNull(PdfTextSelectionEngine.wordBoundaries(backend, 0))
        assertNull(PdfTextSelectionEngine.wordBoundaries(backend, 7))
    }

    @Test
    fun `wordBoundaries handles run at the start of the page`() {
        val backend = StubBackend("Word")
        assertEquals(
            PdfTextSelectionRange(0, 4),
            PdfTextSelectionEngine.wordBoundaries(backend, 2)
        )
    }

    @Test
    fun `wordBoundaries handles run at the end of the page`() {
        val backend = StubBackend("Word")
        assertEquals(
            PdfTextSelectionRange(0, 4),
            PdfTextSelectionEngine.wordBoundaries(backend, 3)
        )
    }

    @Test
    fun `extendRange moves the start handle without crossing the end`() {
        val backend = StubBackend("The quick brown fox")
        val initial = PdfTextSelectionRange(4, 9)
        val update = PdfTextSelectionEngine.extendRange(
            backend = backend,
            current = initial,
            activeHandle = PdfSelectionHandle.START,
            newCharIndex = 0
        )
        assertEquals(PdfSelectionHandle.START, update.activeHandle)
        assertEquals(PdfTextSelectionRange(0, 9), update.range)
    }

    @Test
    fun `extendRange swaps to the end handle when start overtakes end`() {
        val backend = StubBackend("The quick brown fox")
        val initial = PdfTextSelectionRange(4, 9)
        val update = PdfTextSelectionEngine.extendRange(
            backend = backend,
            current = initial,
            activeHandle = PdfSelectionHandle.START,
            newCharIndex = 9
        )
        assertEquals(PdfSelectionHandle.END, update.activeHandle)
        assertEquals(PdfTextSelectionRange(8, 10), update.range)
    }

    @Test
    fun `extendRange moves the end handle without crossing the start`() {
        val backend = StubBackend("The quick brown fox")
        val initial = PdfTextSelectionRange(4, 9)
        val update = PdfTextSelectionEngine.extendRange(
            backend = backend,
            current = initial,
            activeHandle = PdfSelectionHandle.END,
            newCharIndex = 14
        )
        assertEquals(PdfSelectionHandle.END, update.activeHandle)
        assertEquals(PdfTextSelectionRange(4, 15), update.range)
    }

    @Test
    fun `extendRange swaps to the start handle when end overtakes start`() {
        val backend = StubBackend("The quick brown fox")
        val initial = PdfTextSelectionRange(4, 9)
        val update = PdfTextSelectionEngine.extendRange(
            backend = backend,
            current = initial,
            activeHandle = PdfSelectionHandle.END,
            newCharIndex = 3
        )
        assertEquals(PdfSelectionHandle.START, update.activeHandle)
        assertEquals(PdfTextSelectionRange(3, 5), update.range)
    }

    @Test
    fun `range coerced within page bounds`() {
        val backend = StubBackend("Hello")
        assertEquals(
            PdfTextSelectionRange(0, 1),
            PdfTextSelectionRange(-5, -3).coerced(backend.pageCharCount)
        )
        // (3, 99) clamps to (3, 5) as 'Hello' has 5 characters.
        assertEquals(
            PdfTextSelectionRange(3, 5),
            PdfTextSelectionRange(3, 99).coerced(backend.pageCharCount)
        )
        // (7, 2) collapses to (1, 2) on an empty page.
        assertEquals(
            PdfTextSelectionRange(0, 0),
            PdfTextSelectionRange(7, 2).coerced(0)
        )
    }
}
