package com.aryan.reader.data

import com.aryan.reader.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBookMetadataTest {

    @Test
    fun `metadata JSON round trips nullable reader progress fields`() {
        val metadata = FolderBookMetadata(
            bookId = "book-1",
            title = "Title",
            author = null,
            displayName = "Title.epub",
            type = "EPUB",
            lastChapterIndex = 4,
            lastPage = null,
            lastPositionCfi = "/4/2:10",
            progressPercentage = 42.5f,
            isRecent = false,
            lastModifiedTimestamp = 1234L,
            bookmarksJson = """[{"chapter":4}]""",
            locatorBlockIndex = 99,
            locatorCharOffset = null,
            customName = "Custom",
            highlightsJson = """[{"id":"h1"}]"""
        )

        val decoded = FolderBookMetadata.fromJsonString(metadata.toJsonString())

        assertEquals(metadata.copy(author = null, lastPage = null, locatorCharOffset = null), decoded)
    }

    @Test
    fun `fromJsonString applies legacy defaults for missing optional fields`() {
        val decoded = FolderBookMetadata.fromJsonString("""{"bookId":"legacy"}""")

        assertEquals("legacy", decoded.bookId)
        assertEquals("Unknown", decoded.displayName)
        assertEquals("PDF", decoded.type)
        assertEquals(0f, decoded.progressPercentage)
        assertTrue(decoded.isRecent)
        assertEquals(0L, decoded.lastModifiedTimestamp)
        assertNull(decoded.title)
        assertNull(decoded.lastChapterIndex)
        assertNull(decoded.locatorBlockIndex)
    }

    @Test
    fun `toRecentFileItem maps metadata and falls back to EPUB for unknown type`() {
        val metadata = FolderBookMetadata(
            bookId = "book-2",
            title = "Remote Title",
            author = "Author",
            displayName = "Remote.bin",
            type = "NOT_A_TYPE",
            lastChapterIndex = 2,
            lastPage = 12,
            lastPositionCfi = "/6",
            progressPercentage = 75f,
            isRecent = true,
            lastModifiedTimestamp = 500L,
            bookmarksJson = "bookmarks",
            locatorBlockIndex = 7,
            locatorCharOffset = 8,
            customName = "Shelf Name",
            highlightsJson = "highlights"
        )

        val item = metadata.toRecentFileItem(
            uriString = "content://book",
            coverPath = "/covers/book.png",
            sourceFolderUri = "content://folder"
        )

        assertEquals("book-2", item.bookId)
        assertEquals(FileType.EPUB, item.type)
        assertEquals("Remote Title", item.title)
        assertEquals("Author", item.author)
        assertEquals(12, item.lastPage)
        assertEquals(7, item.locatorBlockIndex)
        assertEquals(8, item.locatorCharOffset)
        assertEquals("content://folder", item.sourceFolderUri)
        assertEquals("Shelf Name", item.customName)
        assertEquals("highlights", item.highlightsJson)
    }
}
