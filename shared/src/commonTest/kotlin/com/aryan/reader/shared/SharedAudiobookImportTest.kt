package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedAudiobookImportTest {
    @Test
    fun `a successful audiobook import projects into Listen and library recents`() {
        val request = SharedAudiobookImportRequest(
            bookId = "audio-1",
            filePath = "/imports/story.m4b",
            displayName = "story.m4b",
            format = "M4B",
            metadata = SharedAudiobookImportMetadata(
                title = "The Story",
                author = "Author",
                album = "Series",
                narrator = "Narrator",
                durationMs = 120_000L,
            ),
            addedAt = 42L,
            fileSize = 99L,
        )

        val result = SharedReaderScreenState().withAudiobookImportedToLibrary(request)

        assertEquals(SharedAudiobookImportStatus.ADDED, result.status)
        assertTrue(result.wasAdded)
        assertEquals(listOf("audio-1"), result.state.audiobooks.map { it.bookId })
        assertEquals(listOf("audio-1"), result.state.rawLibraryBooks.map { it.id })
        assertEquals(listOf("audio-1"), result.state.recentBooks.map { it.id })
        assertEquals(FileType.AUDIOBOOK, result.libraryBook?.type)
        assertEquals("The Story", result.libraryBook?.title)
        assertEquals("Series", result.libraryBook?.seriesName)
        assertEquals(99L, result.libraryBook?.fileSize)
        assertEquals(0f, result.libraryBook?.progressPercentage)
    }

    @Test
    fun `same audiobook id or path is a duplicate and does not mutate state`() {
        val first = SharedAudiobookImportRequest(
            bookId = "audio-1",
            filePath = "/imports/story.m4b",
            displayName = "story.m4b",
            format = "M4B",
            addedAt = 1L,
        )
        val imported = SharedReaderScreenState().withAudiobookImportedToLibrary(first)
        val duplicateById = imported.state.withAudiobookImportedToLibrary(
            first.copy(filePath = "/imports/renamed.m4b", displayName = "renamed.m4b", addedAt = 2L)
        )
        val duplicateByPath = imported.state.withAudiobookImportedToLibrary(
            first.copy(bookId = "audio-2", addedAt = 3L)
        )

        assertEquals(SharedAudiobookImportStatus.DUPLICATE, duplicateById.status)
        assertEquals(SharedAudiobookImportStatus.DUPLICATE, duplicateByPath.status)
        assertEquals(imported.state, duplicateById.state)
        assertEquals(imported.state, duplicateByPath.state)
        assertFalse(duplicateById.wasAdded)
    }

    @Test
    fun `an existing library projection also blocks audiobook reimport`() {
        val existing = BookItem(
            id = "audio-1",
            path = "/imports/story.m4b",
            type = FileType.AUDIOBOOK,
            displayName = "story.m4b",
            timestamp = 1L,
        )
        val result = SharedReaderScreenState(
            rawLibraryBooks = listOf(existing),
            libraryBooks = listOf(existing),
            recentBooks = listOf(existing),
        ).withAudiobookImportedToLibrary(
            SharedAudiobookImportRequest(
                bookId = "audio-1",
                filePath = "/imports/renamed.m4b",
                displayName = "renamed.m4b",
                format = "M4B",
                addedAt = 2L,
            )
        )

        assertEquals(SharedAudiobookImportStatus.DUPLICATE, result.status)
        assertEquals(listOf(existing), result.state.rawLibraryBooks)
        assertEquals(existing, result.libraryBook)
    }

    @Test
    fun `invalid import does not create audiobook or library item`() {
        val result = SharedReaderScreenState().withAudiobookImportedToLibrary(
            SharedAudiobookImportRequest(
                bookId = "",
                filePath = "",
                displayName = "",
                format = "M4B",
                addedAt = 1L,
            )
        )

        assertEquals(SharedAudiobookImportStatus.INVALID, result.status)
        assertTrue(result.state.audiobooks.isEmpty())
        assertTrue(result.state.rawLibraryBooks.isEmpty())
        assertNotNull(result.message)
    }
}
