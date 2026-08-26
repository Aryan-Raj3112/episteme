package com.aryan.reader

import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.DriveFile
import com.aryan.reader.shared.CloudBookTombstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBookDeletePlannerTest {
    @Test
    fun `payload planning removes duplicate content and annotation objects`() {
        val tombstone = CloudBookTombstone("book-1", FileType.PDF.name, 100L)
        val payloads = cloudBookDeletionPayloadIds(
            tombstone = tombstone,
            remote = null,
            remoteFilesByName = mapOf(
                "book-1.pdf" to listOf(DriveFile("content-a", "book-1.pdf"), DriveFile("content-b", "book-1.pdf")),
                "annotation_book-1.json" to listOf(DriveFile("annotation", "annotation_book-1.json")),
            ),
        )

        assertEquals(listOf("content-a", "content-b", "annotation"), payloads)
    }

    @Test
    fun `payload planning uses remote type when the outbox type is missing`() {
        val tombstone = CloudBookTombstone("book-2", null, 100L)
        val payloads = cloudBookDeletionPayloadIds(
            tombstone = tombstone,
            remote = BookMetadata(bookId = "book-2", type = FileType.EPUB.name),
            remoteFilesByName = mapOf(
                "book-2.epub" to listOf(DriveFile("content", "book-2.epub")),
            ),
        )

        assertEquals(listOf("content"), payloads)
    }

    @Test
    fun `deletion metadata preserves remote fields and wins over its timestamp`() {
        val metadata = cloudBookDeletionMetadata(
            tombstone = CloudBookTombstone("book-3", FileType.EPUB.name, 200L),
            remote = BookMetadata(
                bookId = "book-3",
                displayName = "A book",
                type = FileType.EPUB.name,
                lastModifiedTimestamp = 150L,
                isDeleted = false,
            ),
            nowMillis = 175L,
        )

        assertEquals("A book", metadata.displayName)
        assertTrue(metadata.isDeleted)
        assertEquals(200L, metadata.lastModifiedTimestamp)
    }

    @Test
    fun `deletion metadata falls back to the durable tombstone`() {
        val metadata = cloudBookDeletionMetadata(
            tombstone = CloudBookTombstone("book-4", FileType.PDF.name, 20L),
            remote = null,
            nowMillis = 30L,
        )

        assertEquals("book-4", metadata.bookId)
        assertEquals(FileType.PDF.name, metadata.type)
        assertTrue(metadata.isDeleted)
        assertEquals(30L, metadata.lastModifiedTimestamp)
    }

    @Test
    fun `unknown type is quarantinable instead of being guessed`() {
        val tombstone = CloudBookTombstone("book-5", "legacy-format", 20L)
        assertEquals(null, cloudBookDeletionType(tombstone, null))
        assertTrue(
            cloudBookDeletionPayloadIds(
                tombstone = tombstone,
                remote = null,
                remoteFilesByName = mapOf("book-5.pdf" to listOf(DriveFile("wrong", "book-5.pdf"))),
            ).isEmpty(),
        )
        assertEquals(FileType.UNKNOWN.name, cloudBookDeletionMetadata(tombstone, null, 30L).type)
    }
}
