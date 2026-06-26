package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTocSearchTest {
    @Test
    fun `blank query returns all entries in order`() {
        val result = filterReaderTocEntries(
            entries = sampleToc,
            query = " ",
            labelOf = { it.title },
            depthOf = { it.depth }
        )

        assertEquals(listOf(0, 1, 2, 3), result.map { it.originalIndex })
        assertEquals(listOf(true, true, true, true), result.map { it.matchesQuery })
    }

    @Test
    fun `matching nested entry includes ancestors for context`() {
        val result = filterReaderTocEntries(
            entries = sampleToc,
            query = "meters",
            labelOf = { it.title },
            depthOf = { it.depth }
        )

        assertEquals(listOf(0, 1, 2), result.map { it.originalIndex })
        assertEquals(listOf(false, false, true), result.map { it.matchesQuery })
    }

    @Test
    fun `query matches case insensitively`() {
        val result = filterReaderTocEntries(
            entries = sampleToc,
            query = "PROLOGUE",
            labelOf = { it.title },
            depthOf = { it.depth }
        )

        assertEquals(listOf(3), result.map { it.originalIndex })
        assertEquals(listOf(true), result.map { it.matchesQuery })
    }

    private data class TocItem(
        val title: String,
        val depth: Int
    )

    private val sampleToc = listOf(
        TocItem("Part One", 0),
        TocItem("Chapter One", 1),
        TocItem("The Meters", 2),
        TocItem("Prologue", 0)
    )
}
