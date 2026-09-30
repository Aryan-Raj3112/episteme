package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubPositionSaveLogTest {
    @Test
    fun `null locator summarizes as null`() {
        val locator: ReaderLocator? = null
        assertEquals("null", locator.epubPositionSummary())
    }

    @Test
    fun `summary carries position fields without dumping text`() {
        val locator = ReaderLocator(
            chapterIndex = 2,
            pageIndex = 15,
            startOffset = 1234,
            endOffset = 1250,
            blockIndex = 7,
            charOffset = 1234,
            textQuote = "a long quoted passage that must never land in logs",
            cfi = "/4/2/4:12"
        )
        val summary = locator.epubPositionSummary()
        assertTrue(summary.contains("chapter=2"))
        assertTrue(summary.contains("page=15"))
        assertTrue(summary.contains("1234..1250"))
        assertTrue(summary.contains("/4/2/4:12"))
        assertFalse(summary.contains("a long quoted passage"))
    }

    @Test
    fun `pre restore positions in the restored chapter are dropped`() {
        val anchor = ReaderLocator(chapterIndex = 3, startOffset = 1946)
        val stale = ReaderLocator(chapterIndex = 3, startOffset = 0)
        assertTrue(shouldDropPreRestoreBridgePosition(anchor, stale))
    }

    @Test
    fun `positions at or past the anchor resolve the guard`() {
        val anchor = ReaderLocator(chapterIndex = 3, startOffset = 1946)
        assertFalse(
            shouldDropPreRestoreBridgePosition(anchor, ReaderLocator(chapterIndex = 3, startOffset = 1946))
        )
        assertFalse(
            shouldDropPreRestoreBridgePosition(anchor, ReaderLocator(chapterIndex = 3, startOffset = 2000))
        )
    }

    @Test
    fun `cross chapter reports and nulls are never dropped`() {
        val anchor = ReaderLocator(chapterIndex = 3, startOffset = 1946)
        assertFalse(
            shouldDropPreRestoreBridgePosition(anchor, ReaderLocator(chapterIndex = 4, startOffset = 0))
        )
        assertFalse(shouldDropPreRestoreBridgePosition(null, ReaderLocator(chapterIndex = 3, startOffset = 0)))
        assertFalse(
            shouldDropPreRestoreBridgePosition(
                ReaderLocator(chapterIndex = 3),
                ReaderLocator(chapterIndex = 3, startOffset = 0)
            )
        )
        assertFalse(
            shouldDropPreRestoreBridgePosition(anchor, ReaderLocator(chapterIndex = 3))
        )
    }
}
