package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCloudSyncMappingTest {
    @Test
    fun `book metadata encodes desktop reader state for cloud sync`() {
        val bookmarkLocator = ReaderLocator(
            chapterIndex = 2,
            pageIndex = 4,
            startOffset = 30,
            endOffset = 44,
            textQuote = "marked passage"
        )
        val highlightLocator = ReaderLocator(
            chapterIndex = 2,
            startOffset = 50,
            endOffset = 64,
            textQuote = "highlighted text"
        )
        val book = BookItem(
            id = "book-1",
            path = null,
            type = FileType.EPUB,
            displayName = "Book.epub",
            timestamp = 1_000L,
            title = "Book",
            author = "Author",
            progressPercentage = 42f,
            lastPageIndex = 4,
            readerPosition = ReaderLocator(
                chapterIndex = 2,
                pageIndex = 4,
                startOffset = 10,
                endOffset = 20
            ),
            readerBookmarks = listOf(
                ReaderBookmark(
                    id = "bookmark-1",
                    pageIndex = 4,
                    chapterTitle = "Chapter",
                    preview = "marked passage",
                    locator = bookmarkLocator
                )
            ),
            readerHighlights = listOf(
                UserHighlight(
                    id = "highlight-1",
                    cfi = "desktop:2:50:64",
                    text = "highlighted text",
                    color = HighlightColor.YELLOW,
                    chapterIndex = 2,
                    locator = highlightLocator
                )
            )
        )

        val metadata = book.toDesktopCloudBookMetadata(hasAnnotations = false, timestamp = 2_000L)
        val restored = metadata.toDesktopBookItem()

        assertEquals("desktop:2:10:20", metadata.lastPositionCfi)
        assertEquals(2, metadata.lastChapterIndex)
        assertEquals(4, metadata.lastPage)
        assertEquals(42f, metadata.progressPercentage)
        assertTrue(assertNotNull(metadata.bookmarksJson).contains("desktop:2:30:44"))
        assertTrue(assertNotNull(metadata.highlightsJson).contains("highlighted text"))
        assertEquals(book.id, restored.id)
        assertEquals(2, restored.readerPosition?.chapterIndex)
        assertEquals(10, restored.readerPosition?.startOffset)
        assertEquals(1, restored.readerBookmarks.size)
        assertEquals(2, restored.readerBookmarks.single().locator.chapterIndex)
        assertEquals(30, restored.readerBookmarks.single().locator.startOffset)
        assertEquals(44, restored.readerBookmarks.single().locator.endOffset)
        assertEquals("desktop:2:30:44", restored.readerBookmarks.single().locator.cfi)
        assertEquals(1, restored.readerHighlights.size)
        assertEquals(2, restored.readerHighlights.single().locator.chapterIndex)
        assertEquals(50, restored.readerHighlights.single().locator.startOffset)
        assertEquals(64, restored.readerHighlights.single().locator.endOffset)
        assertEquals("desktop:2:50:64", restored.readerHighlights.single().locator.cfi)
    }

    @Test
    fun `remote metadata without annotation json preserves existing desktop annotations`() {
        val existingBookmark = ReaderBookmark(
            id = "bookmark-1",
            pageIndex = 1,
            chapterTitle = "Chapter",
            preview = "local bookmark"
        )
        val existingHighlight = UserHighlight(
            id = "highlight-1",
            cfi = "desktop:0:12:18",
            text = "local highlight",
            color = HighlightColor.BLUE,
            chapterIndex = 0
        )
        val existing = BookItem(
            id = "book-1",
            path = "C:/books/Book.epub",
            type = FileType.EPUB,
            displayName = "Book.epub",
            timestamp = 1_000L,
            readerBookmarks = listOf(existingBookmark),
            readerHighlights = listOf(existingHighlight)
        )
        val remote = DesktopCloudBookMetadata(
            bookId = existing.id,
            displayName = existing.displayName,
            type = FileType.EPUB.name,
            lastModifiedTimestamp = 2_000L,
            bookmarksJson = null,
            highlightsJson = null
        )

        val restored = remote.toDesktopBookItem(existing = existing)

        assertEquals(listOf(existingBookmark), restored.readerBookmarks)
        assertEquals(listOf(existingHighlight), restored.readerHighlights)
        assertEquals(existing.path, restored.path)
    }
}
