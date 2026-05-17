package com.aryan.reader.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSummaryCacheStoreTest {
    @Test
    fun `stores lists and deletes cached summaries`() {
        val store = DesktopSummaryCacheStore(Files.createTempDirectory("reader-summary-cache").toFile())

        store.saveSummary("book-a", 2, "Chapter 3", "A cached summary.")
        store.saveSummary("book-a", 0, "Chapter 1", "The first cached summary.")

        assertEquals("A cached summary.", store.getSummary("book-a", 2))
        assertEquals(
            listOf(
                DesktopCachedSummaryItem(0, "Chapter 1", "The first cached summary."),
                DesktopCachedSummaryItem(2, "Chapter 3", "A cached summary.")
            ),
            store.getAllSummaries("book-a")
        )

        store.deleteSummary("book-a", 2)

        assertNull(store.getSummary("book-a", 2))
        assertEquals(1, store.getAllSummaries("book-a").size)
    }

    @Test
    fun `keeps books isolated and ignores blank summaries`() {
        val store = DesktopSummaryCacheStore(Files.createTempDirectory("reader-summary-cache-isolated").toFile())

        store.saveSummary("book-a", 0, "Chapter 1", "A summary.")
        store.saveSummary("book-b", 0, "Chapter 1", "Another summary.")
        store.saveSummary("book-a", 1, "Chapter 2", "   ")

        assertEquals("A summary.", store.getSummary("book-a", 0))
        assertEquals("Another summary.", store.getSummary("book-b", 0))
        assertNull(store.getSummary("book-a", 1))

        store.clearBookCache("book-a")

        assertNull(store.getSummary("book-a", 0))
        assertEquals("Another summary.", store.getSummary("book-b", 0))
    }
}
