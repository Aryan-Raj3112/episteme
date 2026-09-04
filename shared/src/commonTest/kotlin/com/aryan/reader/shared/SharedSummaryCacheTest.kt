package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSummaryCacheStorage : SharedSummaryCacheStorage {
    val files = mutableMapOf<String, String>()

    override fun read(fileName: String): String? = files[fileName]

    override fun write(fileName: String, content: String): Boolean {
        files[fileName] = content
        return true
    }

    override fun delete(fileName: String): Boolean = files.remove(fileName) != null

    override fun listFileNames(): List<String> = files.keys.toList()
}

class SharedSummaryCacheTest {
    @Test
    fun `file naming matches android summary cache layout`() {
        assertEquals(
            "summary_Pride_and_Prejudice_3.txt",
            SharedSummaryCacheFiles.fileName("Pride and Prejudice", 3)
        )
        assertEquals(
            "summary_54___________The_Hobbit.epub_0.txt",
            SharedSummaryCacheFiles.fileName("54!@#\$%^&*() The Hobbit.epub", 0)
        )
    }

    @Test
    fun `content round-trips title and summary`() {
        val decoded = SharedSummaryCacheFiles.decodeContent(
            bookTitle = "Book",
            sectionIndex = 1,
            raw = SharedSummaryCacheFiles.encodeContent("Chapter One", "It was a good book."),
        )
        assertEquals("Chapter One", decoded?.sectionTitle)
        assertEquals("It was a good book.", decoded?.summary)
    }

    @Test
    fun `blank summaries never persist`() {
        val cache = SharedSummaryCache(FakeSummaryCacheStorage())
        assertFalse(cache.saveSummary("Book", 0, "Title", "   "))
        assertNull(cache.getSummary("Book", 0))
    }

    @Test
    fun `save get list delete clear round-trip`() {
        val cache = SharedSummaryCache(FakeSummaryCacheStorage())
        cache.saveSummary("Book", 2, "Two", "second")
        cache.saveSummary("Book", 0, "Zero", "first")
        cache.saveSummary("Other", 0, "Zero", "other book")

        assertEquals("second", cache.getSummary("Book", 2)?.summary)
        assertTrue(cache.hasSummary("Book", 0))
        assertEquals(listOf(0, 2), cache.getAllSummaries("Book").map { it.sectionIndex })
        // Other books are isolated by filename prefix.
        assertEquals(listOf(0), cache.getAllSummaries("Other").map { it.sectionIndex })

        assertTrue(cache.deleteSummary("Book", 0))
        assertNull(cache.getSummary("Book", 0))

        cache.clearBookCache("Book")
        assertTrue(cache.getAllSummaries("Book").isEmpty())
        assertEquals("other book", cache.getSummary("Other", 0)?.summary)
    }
}
