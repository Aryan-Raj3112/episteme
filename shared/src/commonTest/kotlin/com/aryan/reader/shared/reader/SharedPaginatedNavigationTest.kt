package com.aryan.reader.shared.reader

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
    fun `chapter start immediately uses the current estimated prefix`() {
        val result = resolveSharedStableChapterStartPage(
            chapterIndex = 3,
            chapterCount = 5,
            chapterStartPage = { if (it == 3) 21 else null },
        )

        assertEquals(21, result)
    }

    @Test
    fun `chapter start does not require preceding chapter pagination`() {
        val result = resolveSharedStableChapterStartPage(
            chapterIndex = 1_000,
            chapterCount = 1_500,
            chapterStartPage = { if (it == 1_000) 8_400 else null },
        )

        assertEquals(8_400, result)
    }
}
