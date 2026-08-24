package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubJumpHistoryCaptureTest {

    @Test
    fun `settled page is the current jump origin after a completed scroll`() {
        assertEquals(
            21,
            authoritativePaginatedPageIndex(
                currentPageIndex = 21,
                settledPageIndex = 21,
                isScrollInProgress = false
            )
        )
    }

    @Test
    fun `current page is used while pager scroll is still in progress`() {
        assertEquals(
            21,
            authoritativePaginatedPageIndex(
                currentPageIndex = 21,
                settledPageIndex = 19,
                isScrollInProgress = true
            )
        )
    }

    @Test
    fun `paginated jump locator resolves the authoritative page before fallback`() {
        val page22 = Locator(chapterIndex = 0, blockIndex = 22, charOffset = 0)
        val locator = paginatedEpubJumpLocator(
            currentPageIndex = 21,
            settledPageIndex = 21,
            isScrollInProgress = false,
            locatorForPage = { pageIndex -> page22.takeIf { pageIndex == 21 } },
            fallbackLocator = Locator(chapterIndex = 0, blockIndex = 20, charOffset = 0)
        )

        assertEquals(21, locator?.pageIndex)
        assertEquals(22, locator?.blockIndex)
        assertNull(
            paginatedEpubJumpLocator(
                currentPageIndex = -1,
                settledPageIndex = -1,
                isScrollInProgress = false,
                locatorForPage = { null },
                fallbackLocator = Locator(chapterIndex = 0, blockIndex = 0, charOffset = 0)
            )
        )
    }
}

