package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSearchMatchingTest {
    @Test
    fun `matches Android case-insensitive word-start behavior`() {
        assertEquals(
            listOf(0, 11, 18, 27),
            readerWordStartMatchOffsets("Reader pre-reader READERLY reader", "reader"),
        )
    }

    @Test
    fun `blank query has no matches`() {
        assertEquals(emptyList(), readerWordStartMatchOffsets("Reader", "  "))
    }
}
