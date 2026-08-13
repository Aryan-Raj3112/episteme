package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.toArgb

class EpubBookmarkPoliciesTest {
    @Test
    fun `highlight palette and legacy color tokens preserve Android compatibility`() {
        assertEquals(DefaultEpubHighlightPaletteArgb, sanitizeEpubHighlightPalette(listOf(1, 2)))
        assertEquals(listOf(1, 2, 3, 4), sanitizeEpubHighlightPalette(listOf(1, 2, 3, 4)))
        val yellow = HighlightColor.YELLOW.color.toArgb()
        assertEquals(HighlightColor.YELLOW, legacyEpubHighlightColorForArgb(yellow))
        assertEquals("yellow", epubHighlightColorTag(yellow))
        assertEquals("custom_12345678", epubHighlightColorTag(0x12345678))
        assertEquals(HighlightColor.RED to null, epubHighlightColorFromToken("red"))
        assertEquals(HighlightColor.YELLOW to 0xFF112233.toInt(), epubHighlightColorFromToken("#112233"))
        assertEquals(HighlightColor.YELLOW to null, epubHighlightColorFromToken("unknown"))
    }

    @Test
    fun `visible range then nearby locator then legacy page preserve Android precedence`() {
        val bookmarks = listOf("visible", "nearby", "page")
        val positions = mapOf(
            "visible" to EpubBlockPosition(1, 3, 120),
            "nearby" to EpubBlockPosition(2, 4, 340)
        )
        val pages = mapOf("page" to 8)

        assertEquals(
            "visible",
            findEpubBookmarkForLocation(
                bookmarks = bookmarks,
                visibleRanges = listOf(EpubVisibleTextRange(1, 3, 100, 130)),
                currentPosition = EpubBlockPosition(2, 4, 300),
                currentPage = 8,
                cfi = { it },
                positionForCfi = positions::get,
                pageForCfi = pages::get
            )
        )
        assertEquals(
            "nearby",
            findEpubBookmarkForLocation(
                bookmarks = bookmarks,
                visibleRanges = emptyList(),
                currentPosition = EpubBlockPosition(2, 4, 500),
                currentPage = 8,
                cfi = { it },
                positionForCfi = positions::get,
                pageForCfi = pages::get
            )
        )
        assertEquals(
            "page",
            findEpubBookmarkForLocation(
                bookmarks = bookmarks,
                visibleRanges = emptyList(),
                currentPosition = EpubBlockPosition(9, 9, 9),
                currentPage = 8,
                cfi = { it },
                positionForCfi = positions::get,
                pageForCfi = pages::get
            )
        )
    }
}
