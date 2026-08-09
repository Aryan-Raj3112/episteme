package com.aryan.reader.shared.reader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPaginatedNavigationTest {
    @Test
    fun `reconfiguration prefers the visible page anchor before fallback`() {
        assertEquals("visible", resolveSharedPaginatedReconfigurationAnchor("visible", "saved"))
        assertEquals("saved", resolveSharedPaginatedReconfigurationAnchor(null, "saved"))
        assertEquals(null, resolveSharedPaginatedReconfigurationAnchor<String>(null, null))
    }

    @Test
    fun `open position is saved only after valid stable pagination`() {
        assertTrue(
            shouldSaveSharedPaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = false,
                pageCount = 8,
                pageToSave = 7,
            )
        )
        assertFalse(
            shouldSaveSharedPaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = true,
                pageCount = 8,
                pageToSave = 7,
            )
        )
        assertFalse(
            shouldSaveSharedPaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = false,
                pageCount = 8,
                pageToSave = 8,
            )
        )
    }

    @Test
    fun `chapter start finalizes inaccurate prefix chapters in order`() = runTest {
        val finalized = mutableSetOf(0)
        val requested = mutableListOf<Int>()

        val result = resolveSharedStableChapterStartPage(
            chapterIndex = 3,
            chapterCount = 5,
            pageCountsAreAccurate = false,
            chapterStartPage = { if (it == 3) 21 else null },
            isChapterFinalized = finalized::contains,
            ensureChapterPaginated = {
                requested += it
                finalized += it
                true
            },
        )

        assertEquals(21, result)
        assertEquals(listOf(1, 2), requested)
    }

    @Test
    fun `chapter start stops when a prefix cannot be finalized`() = runTest {
        val result = resolveSharedStableChapterStartPage(
            chapterIndex = 2,
            chapterCount = 3,
            pageCountsAreAccurate = false,
            chapterStartPage = { 12 },
            isChapterFinalized = { false },
            ensureChapterPaginated = { false },
        )

        assertEquals(null, result)
    }
}
