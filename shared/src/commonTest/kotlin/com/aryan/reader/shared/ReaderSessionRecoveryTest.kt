package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderBookmark
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSessionRecoveryTest {
    @Test
    fun `newer recovery restores reading state without rolling metadata back`() {
        val library = book(clock = 10L).copy(title = "Edited title", author = "Edited author")
        val recovery = book(clock = 20L).copy(
            title = "Old title",
            author = "Old author",
            progressPercentage = 72f,
            lastPageIndex = 8,
            readerBookmarks = listOf(
                ReaderBookmark(
                    id = "bookmark",
                    pageIndex = 8,
                    chapterTitle = "Chapter",
                    preview = "Preview",
                )
            ),
        )

        val merged = library.withNewerReaderSession(recovery)

        assertEquals("Edited title", merged.title)
        assertEquals("Edited author", merged.author)
        assertEquals(72f, merged.progressPercentage)
        assertEquals(8, merged.lastPageIndex)
        assertEquals(recovery.readerBookmarks, merged.readerBookmarks)
        assertEquals(20L, merged.readingPositionModifiedTimestamp)
    }

    @Test
    fun `stale recovery cannot overwrite newer library reading state`() {
        val library = book(clock = 30L).copy(progressPercentage = 80f)
        val stale = book(clock = 20L).copy(progressPercentage = 10f)

        assertEquals(library, library.withNewerReaderSession(stale))
    }

    @Test
    fun `legacy unclocked recovery is accepted only when library has no reading state`() {
        val emptyLibrary = book(clock = 0L)
        val legacyRecovery = book(clock = 0L).copy(lastPageIndex = 4, progressPercentage = 50f)

        assertEquals(4, emptyLibrary.withNewerReaderSession(legacyRecovery).lastPageIndex)
        assertEquals(
            7,
            emptyLibrary.copy(lastPageIndex = 7)
                .withNewerReaderSession(legacyRecovery)
                .lastPageIndex,
        )
    }

    private fun book(clock: Long) = BookItem(
        id = "book",
        path = "/books/book.epub",
        type = FileType.EPUB,
        displayName = "book.epub",
        timestamp = 1L,
        readingPositionModifiedTimestamp = clock,
    )
}
