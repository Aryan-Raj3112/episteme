package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Test

class StablePaginatedNavigationTest {

    @Test
    fun `chapter zero needs no prefix stabilization`() {
        val startPage = resolveStableChapterStartPage(
            chapterIndex = 0,
            chapterCount = 4,
            chapterStartPage = { chapterStarts[it] },
        )

        assertEquals(0, startPage)
    }

    @Test
    fun `far target uses estimated start without requesting its prefix`() {
        val startPage = resolveStableChapterStartPage(
            chapterIndex = 1_000,
            chapterCount = 1_500,
            chapterStartPage = { if (it == 1_000) 12_345 else null },
        )

        assertEquals(12_345, startPage)
    }

    @Test
    fun `missing estimated start still fails safely`() {
        val startPage = resolveStableChapterStartPage(
            chapterIndex = 4,
            chapterCount = 5,
            chapterStartPage = { null },
        )

        assertEquals(null, startPage)
    }

    private val chapterStarts = mapOf(
        0 to 0,
        1 to 10,
        2 to 25,
        3 to 45,
        4 to 60
    )
}
