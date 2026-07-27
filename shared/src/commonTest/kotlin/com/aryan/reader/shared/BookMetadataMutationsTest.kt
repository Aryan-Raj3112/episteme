package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BookMetadataMutationsTest {
    @Test
    fun `metadata edit preserves recency and original values while advancing metadata clock`() {
        val original = book(
            id = "book",
            timestamp = 10L,
            title = "Original",
            author = "Original Author",
        )

        val edited = original.withUserEditedMetadata(
            original.copy(title = "Edited", author = "Edited Author"),
            modifiedAt = 50L,
        )

        assertEquals(10L, edited.timestamp)
        assertEquals(50L, edited.metadataModifiedTimestamp)
        assertEquals("Original", edited.originalTitle)
        assertEquals("Original Author", edited.originalAuthor)
    }

    @Test
    fun `display rename sets Android compatible title sort override`() {
        val original = book(id = "book", displayName = "original.pdf")
        val edited = original.withUserEditedMetadata(
            original.copy(displayName = "My document"),
            modifiedAt = 20L,
        )

        assertEquals("My document", edited.titleSortKey)
        assertEquals(20L, edited.metadataModifiedTimestamp)
    }

    @Test
    fun `no-op edit does not create a newer metadata version`() {
        val original = book(id = "book", metadataModifiedTimestamp = 12L)
        assertSame(original, original.withUserEditedMetadata(original, modifiedAt = 20L))
    }

    @Test
    fun `loaded metadata fills blanks without overwriting edits or reading state`() {
        val edited = book(
            id = "book",
            title = "My title",
            author = null,
            metadataModifiedTimestamp = 50L,
        ).copy(
            progressPercentage = 60f,
            readingPositionModifiedTimestamp = 70L,
        )

        val merged = edited.withLoadedMetadata("Embedded title", "Embedded author")

        assertEquals("My title", merged.title)
        assertEquals("Embedded author", merged.author)
        assertEquals(60f, merged.progressPercentage)
        assertEquals(70L, merged.readingPositionModifiedTimestamp)
        assertEquals(50L, merged.metadataModifiedTimestamp)
    }

    private fun book(
        id: String,
        displayName: String = "book.epub",
        timestamp: Long = 1L,
        title: String? = "Book",
        author: String? = null,
        metadataModifiedTimestamp: Long = 0L,
    ) = BookItem(
        id = id,
        path = "/books/$displayName",
        type = FileType.EPUB,
        displayName = displayName,
        timestamp = timestamp,
        title = title,
        author = author,
        metadataModifiedTimestamp = metadataModifiedTimestamp,
    )
}
