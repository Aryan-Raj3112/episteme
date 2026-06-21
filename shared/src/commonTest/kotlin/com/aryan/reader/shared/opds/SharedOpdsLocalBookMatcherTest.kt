package com.aryan.reader.shared.opds

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedOpdsLocalBookMatcherTest {
    @Test
    fun `local matcher normalizes opds download prefixes without regex allocation`() {
        val entry = opdsEntry(
            title = "OPDS DL Example Book",
            acquisitions = listOf(OpdsAcquisition("https://example.org/opds_dl_example-book.epub", "application/epub+zip"))
        )
        val localBooks = listOf(
            LocalBook(title = null, displayName = "Example Book.epub", path = "/books/Example Book.epub")
        )

        val match = SharedOpdsLocalBookMatcher.find(
            entry = entry,
            books = localBooks,
            title = { it.title },
            displayName = { it.displayName },
            path = { it.path }
        )

        assertEquals(localBooks.first(), match)
    }

    private data class LocalBook(
        val title: String?,
        val displayName: String?,
        val path: String?
    )

    private fun opdsEntry(
        title: String,
        acquisitions: List<OpdsAcquisition> = emptyList()
    ): OpdsEntry {
        return OpdsEntry(
            id = title,
            title = title,
            summary = null,
            coverUrl = null,
            acquisitions = acquisitions,
            navigationUrl = null
        )
    }
}