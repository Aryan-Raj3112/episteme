package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSerializer
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfReaderViewport
import com.aryan.reader.shared.pdf.SharedPdfRichDocument
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.reader.ReaderBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `metadata only upload can preserve remote content timestamp`() {
        val book = BookItem(
            id = "book-1",
            path = null,
            type = FileType.PDF,
            displayName = "Book.pdf",
            timestamp = 1_000L,
            fileContentModifiedTimestamp = 111L
        )

        val metadata = book.toDesktopCloudBookMetadata(
            hasAnnotations = false,
            timestamp = 2_000L,
            contentTimestampOverride = 999L
        )

        assertEquals(999L, metadata.fileContentModifiedTimestamp)
    }

    @Test
    fun `empty epub annotations upload as empty arrays`() {
        val book = BookItem(
            id = "book-1",
            path = "C:/books/Book.epub",
            type = FileType.EPUB,
            displayName = "Book.epub",
            timestamp = 1_000L
        )

        val metadata = book.toDesktopCloudBookMetadata(hasAnnotations = false)

        assertEquals("[]", metadata.bookmarksJson)
        assertEquals("[]", metadata.highlightsJson)
    }

    @Test
    fun `remote pdf metadata moves stale desktop viewport to remote page`() {
        val existing = BookItem(
            id = "book-1",
            path = "C:/books/Book.pdf",
            type = FileType.PDF,
            displayName = "Book.pdf",
            timestamp = 1_000L,
            lastPageIndex = 264,
            progressPercentage = 33.125f,
            readerPosition = ReaderLocator(pageIndex = 264),
            pdfReaderViewport = SharedPdfReaderViewport(
                pageIndex = 264,
                verticalFirstPageIndex = 264,
                verticalFirstPageScrollOffset = 120
            )
        )
        val remote = DesktopCloudBookMetadata(
            bookId = "book-1",
            displayName = "Book.pdf",
            type = FileType.PDF.name,
            lastModifiedTimestamp = 2_000L,
            lastPage = 69,
            progressPercentage = 8.75f
        )

        val restored = remote.toDesktopBookItem(existing = existing)

        assertEquals(69, restored.lastPageIndex)
        assertEquals(8.75f, restored.progressPercentage)
        assertNull(restored.readerPosition)
        assertEquals(69, restored.pdfReaderViewport?.pageIndex)
        assertEquals(69, restored.pdfReaderViewport?.verticalFirstPageIndex)
        assertEquals(0, restored.pdfReaderViewport?.verticalFirstPageScrollOffset)
    }

    @Test
    fun `pdf metadata upload ignores stale text locator page`() {
        val book = BookItem(
            id = "book-1",
            path = "C:/books/Book.pdf",
            type = FileType.PDF,
            displayName = "Book.pdf",
            timestamp = 1_000L,
            lastPageIndex = 264,
            progressPercentage = 33.125f,
            readerPosition = ReaderLocator(pageIndex = 69)
        )

        val metadata = book.toDesktopCloudBookMetadata(hasAnnotations = false)

        assertEquals(264, metadata.lastPage)
        assertNull(metadata.lastPositionCfi)
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

    @Test
    fun `desktop pdf bookmarks map to android metadata json`() {
        val metadataJson = desktopPdfBookmarksMetadataJson(
            bookmarks = listOf(
                SharedPdfBookmark(
                    pageIndex = 3,
                    label = "Important page",
                    createdAt = 1_234L
                )
            ),
            lastPageIndex = 9
        )

        val restored = desktopPdfBookmarksFromMetadataJson(metadataJson)

        assertTrue(metadataJson.contains("\"pageIndex\""))
        assertTrue(metadataJson.contains("\"title\""))
        assertTrue(metadataJson.contains("\"totalPages\""))
        assertEquals(1, restored.size)
        assertEquals(3, restored.single().pageIndex)
        assertEquals("Important page", restored.single().label)
    }

    @Test
    fun `android pdf bookmark metadata keeps titles on desktop`() {
        val restored = desktopPdfBookmarksFromMetadataJson(
            """[{"pageIndex":2,"title":"Android bookmark","totalPages":8}]"""
        )

        assertEquals(1, restored.size)
        assertEquals(2, restored.single().pageIndex)
        assertEquals("Android bookmark", restored.single().label)
    }

    @Test
    fun `empty desktop pdf annotations are not exported as cloud annotation data`() {
        val emptyAnnotationsJson = SharedPdfAnnotationSerializer.encode(emptyList())

        assertNull(desktopPdfAnnotationElementForSync(emptyAnnotationsJson))
    }

    @Test
    fun `empty desktop pdf rich text is not exported as cloud annotation data`() {
        val emptyRichTextJson = SharedPdfRichTextSerializer.encode(SharedPdfRichDocument())

        assertNull(desktopPdfRichTextElementForSync(emptyRichTextJson))
    }
}
