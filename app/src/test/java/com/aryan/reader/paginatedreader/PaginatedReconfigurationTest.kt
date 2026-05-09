package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaginatedReconfigurationTest {

    @Test
    fun `visible page locator is preferred for reconfiguration restore`() {
        val visiblePageLocator = Locator(chapterIndex = 2, blockIndex = 40, charOffset = 12)
        val fallbackLocator = Locator(chapterIndex = 1, blockIndex = 10, charOffset = 3)

        val anchor = resolvePaginatedReconfigurationAnchor(
            currentPageLocator = visiblePageLocator,
            fallbackLocator = fallbackLocator
        )

        assertEquals(visiblePageLocator, anchor)
    }

    @Test
    fun `last known locator is used when current page is temporarily unavailable`() {
        val fallbackLocator = Locator(chapterIndex = 3, blockIndex = 90, charOffset = 24)

        val anchor = resolvePaginatedReconfigurationAnchor(
            currentPageLocator = null,
            fallbackLocator = fallbackLocator
        )

        assertEquals(fallbackLocator, anchor)
    }

    @Test
    fun `missing page and fallback locators leave restore unset`() {
        val anchor = resolvePaginatedReconfigurationAnchor(
            currentPageLocator = null,
            fallbackLocator = null
        )

        assertNull(anchor)
    }
}
