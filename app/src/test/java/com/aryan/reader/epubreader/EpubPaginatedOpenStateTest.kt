package com.aryan.reader.epubreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPaginatedOpenStateTest {

    @Test
    fun `paginated position save waits for initialized stable pager`() {
        assertFalse(
            shouldSavePaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = false,
                isReconfigurationRestoring = false,
                pageCount = 10,
                pageToSave = 4
            )
        )

        assertFalse(
            shouldSavePaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = true,
                pageCount = 10,
                pageToSave = 4
            )
        )

        assertTrue(
            shouldSavePaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = false,
                pageCount = 10,
                pageToSave = 4
            )
        )
    }

    @Test
    fun `paginated position save rejects invalid pages and inactive mode`() {
        assertFalse(
            shouldSavePaginatedOpenPosition(
                isPaginatedMode = false,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = false,
                pageCount = 10,
                pageToSave = 4
            )
        )

        assertFalse(
            shouldSavePaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = false,
                pageCount = 0,
                pageToSave = 0
            )
        )

        assertFalse(
            shouldSavePaginatedOpenPosition(
                isPaginatedMode = true,
                hasPaginator = true,
                isPagerInitialized = true,
                isReconfigurationRestoring = false,
                pageCount = 10,
                pageToSave = 10
            )
        )
    }
}
