package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HighlightPageScopingTest {

    @Test
    fun `overlapping range touches page`() {
        assertTrue(highlightTextRangeOverlapsPage(100, 140, 0, 200))
        assertTrue(highlightTextRangeOverlapsPage(0, 200, 100, 140))
        assertTrue(highlightTextRangeOverlapsPage(150, 250, 100, 200))
    }

    @Test
    fun `adjacent range does not touch page`() {
        assertFalse(highlightTextRangeOverlapsPage(200, 260, 100, 200))
        assertFalse(highlightTextRangeOverlapsPage(0, 100, 100, 200))
        assertFalse(highlightTextRangeOverlapsPage(300, 360, 100, 200))
    }

    @Test
    fun `collapsed range uses closed-open containment`() {
        assertTrue(highlightTextRangeOverlapsPage(100, 100, 100, 200))
        assertTrue(highlightTextRangeOverlapsPage(150, 150, 100, 200))
        assertFalse(highlightTextRangeOverlapsPage(200, 200, 100, 200))
        assertFalse(highlightTextRangeOverlapsPage(50, 50, 100, 200))
    }

    @Test
    fun `missing range stays visible for per-block mapping`() {
        assertTrue(highlightTextRangeOverlapsPage(null, 140, 100, 200))
        assertTrue(highlightTextRangeOverlapsPage(100, null, 100, 200))
        assertTrue(highlightTextRangeOverlapsPage(null, null, 100, 200))
    }

    @Test
    fun `degenerate page range keeps highlight`() {
        assertTrue(highlightTextRangeOverlapsPage(500, 560, 100, 100))
    }

    @Test
    fun `structural scope needs block index or rooted cfi`() {
        assertTrue(highlightHasStructuralScope(blockIndex = 3, sourceCfi = null))
        assertTrue(highlightHasStructuralScope(blockIndex = null, sourceCfi = "/4/2:5"))
        assertTrue(highlightHasStructuralScope(blockIndex = 0, sourceCfi = "desktop:1:2:3"))
        assertFalse(highlightHasStructuralScope(blockIndex = null, sourceCfi = null))
        assertFalse(highlightHasStructuralScope(blockIndex = null, sourceCfi = ""))
        assertFalse(highlightHasStructuralScope(blockIndex = null, sourceCfi = "desktop:1:2:3"))
    }
}
