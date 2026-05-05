package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
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
                    tags = listOf(tag)
                )
            ),
            shelfRecords = listOf(ShelfRecord(id = "shelf", name = "Shelf", isSmart = true, smartRulesJson = "{}")),
            shelfRefs = listOf(BookShelfRef(bookId = "book", shelfId = "shelf", addedAt = 11L)),
            tags = listOf(tag)
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
}
