package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderBookmark
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLibrarySnapshotJsonTest {

    @Test
    fun `snapshot json round trips library records used by desktop persistence`() {
        val tag = Tag(id = "favorite", name = "Favorite", color = 7)
        val snapshot = SharedLibrarySnapshot(
            books = listOf(
                BookItem(
                    id = "book",
                    path = "C:/Books/book.epub",
                    type = FileType.EPUB,
                    displayName = "book.epub",
                    timestamp = 10L,
                    title = "Book",
                    author = "Ada",
                    progressPercentage = 42f,
                    fileSize = 99L,
                    sourceFolder = "C:/Books",
                    seriesName = "Series",
                    seriesIndex = 2.0,
                    tags = listOf(tag),
                    lastPageIndex = 4,
                    readerSettings = ReaderSettings(
                        fontSize = 22,
                        lineSpacing = 1.7f,
                        margin = 64,
                        darkMode = true,
                        readingMode = ReaderReadingMode.VERTICAL,
                        textAlign = SharedReaderTextAlign.JUSTIFY,
                        pageWidth = 840,
                        fontFamily = "Serif"
                    ),
                    readerBookmarks = listOf(
                        ReaderBookmark(
                            id = "book_4",
                            pageIndex = 4,
                            chapterTitle = "Chapter",
                            preview = "A useful paragraph"
                        )
                    )
                )
            ),
            shelfRecords = listOf(ShelfRecord(id = "shelf", name = "Shelf", isSmart = true, smartRulesJson = "{}")),
            shelfRefs = listOf(BookShelfRef(bookId = "book", shelfId = "shelf", addedAt = 11L)),
            tags = listOf(tag),
            syncedFolders = listOf(SyncedFolder("C:/Books", "Books", lastScanTime = 12L, allowedFileTypes = setOf(FileType.EPUB, FileType.PDF))),
            recentFilesLimit = 20,
            isTabsEnabled = true,
            openTabIds = listOf("book"),
            activeTabBookId = "book",
            pinnedHomeBookIds = setOf("book"),
            pinnedLibraryBookIds = setOf("book"),
            useStrictFileFilter = true,
            appThemeMode = AppThemeMode.DARK
        )

        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(SharedLibrarySnapshotJson.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `snapshot json tolerates malformed or missing data`() {
        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty("""{"books":[{"id":"missingName"}]}""")

        assertTrue(SharedLibrarySnapshotJson.decodeOrEmpty("not json").books.isEmpty())
        assertTrue(decoded.books.isEmpty())
    }

    @Test
    fun `legacy snapshot hides imported only books from recent home`() {
        val decoded = SharedLibrarySnapshotJson.decodeOrEmpty(
            """
            {
              "schemaVersion": 2,
              "books": [
                {
                  "id": "imported",
                  "path": "C:/Books/imported.epub",
                  "type": "EPUB",
                  "displayName": "imported.epub",
                  "timestamp": 10,
                  "isRecent": true
                },
                {
                  "id": "opened",
                  "path": "C:/Books/opened.epub",
                  "type": "EPUB",
                  "displayName": "opened.epub",
                  "timestamp": 11,
                  "isRecent": true
                }
              ],
              "openTabIds": ["opened"]
            }
            """.trimIndent()
        )

        assertFalse(decoded.books.first { it.id == "imported" }.isRecent)
        assertTrue(decoded.books.first { it.id == "opened" }.isRecent)
    }
}
