package com.aryan.reader.shared.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedLibrarySearchDeepLinkTest {

    @Test
    fun `exact quote matches first overlapping snippet`() {
        val index = bestReaderResultForLibraryHit(
            "the history of rome",
            listOf("unrelated chapter text here", "a history of rome and more"),
        )
        assertEquals(1, index)
    }

    @Test
    fun `matching ignores case ellipsis and whitespace`() {
        val index = bestReaderResultForLibraryHit(
            "…History   of ROME…",
            listOf("the HISTORY of rome begins"),
        )
        assertEquals(0, index)
    }

    @Test
    fun `no overlap returns null`() {
        assertNull(
            bestReaderResultForLibraryHit(
                "history of rome",
                listOf("unrelated chapter text here", "another passage entirely"),
            ),
        )
    }

    @Test
    fun `blank quote returns null`() {
        assertNull(bestReaderResultForLibraryHit("   …  ", listOf("history of rome")))
        assertNull(bestReaderResultForLibraryHit(null, listOf("history of rome")))
    }

    @Test
    fun `best overlap wins among several snippets`() {
        val index = bestReaderResultForLibraryHit(
            "magic kingdom harvest festival",
            listOf(
                "a magic tale",
                "the magic kingdom harvest festival begins at dawn",
                "harvest time",
            ),
        )
        assertEquals(1, index)
    }
}
