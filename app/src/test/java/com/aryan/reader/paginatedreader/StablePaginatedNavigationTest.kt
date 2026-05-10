package com.aryan.reader.paginatedreader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StablePaginatedNavigationTest {

    @Test
    fun `chapter zero needs no prefix stabilization`() = runTest {
        val requested = mutableListOf<Int>()

        val startPage = resolveStableChapterStartPage(
            chapterIndex = 0,
            chapterCount = 4,
            pageCountsAreAccurate = false,
            chapterStartPage = { chapterStarts[it] },
            isChapterFinalized = { false },
            ensureChapterPaginated = {
                requested += it
                true
            }
        )

        assertEquals(0, startPage)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `target skips finalized prefix chapters`() = runTest {
        val requested = mutableListOf<Int>()

        val startPage = resolveStableChapterStartPage(
            chapterIndex = 3,
            chapterCount = 5,
            pageCountsAreAccurate = false,
            chapterStartPage = { chapterStarts[it] },
            isChapterFinalized = { it == 0 || it == 2 },
            ensureChapterPaginated = {
                requested += it
                true
            }
        )

        assertEquals(45, startPage)
        assertEquals(listOf(1), requested)
    }

    @Test
    fun `accurate cached page counts skip prefix pagination`() = runTest {
        val requested = mutableListOf<Int>()

        val startPage = resolveStableChapterStartPage(
            chapterIndex = 4,
            chapterCount = 5,
            pageCountsAreAccurate = true,
            chapterStartPage = { chapterStarts[it] },
            isChapterFinalized = { false },
            ensureChapterPaginated = {
                requested += it
                true
            }
        )

        assertEquals(60, startPage)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `missing prefix chapters are requested in order`() = runTest {
        val requested = mutableListOf<Int>()

        val startPage = resolveStableChapterStartPage(
            chapterIndex = 4,
            chapterCount = 5,
            pageCountsAreAccurate = false,
            chapterStartPage = { chapterStarts[it] },
            isChapterFinalized = { false },
            ensureChapterPaginated = {
                requested += it
                true
            }
        )

        assertEquals(60, startPage)
        assertEquals(listOf(0, 1, 2, 3), requested)
    }

    private val chapterStarts = mapOf(
        0 to 0,
        1 to 10,
        2 to 25,
        3 to 45,
        4 to 60
    )
}
