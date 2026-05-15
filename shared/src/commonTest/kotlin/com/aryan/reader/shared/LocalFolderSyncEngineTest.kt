package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFolderSyncEngineTest {
    @Test
    fun `stable ids match android folder-relative scheme`() {
        assertEquals(
            "local_Book.pdf",
            LocalFolderSyncEngine.buildStableBookId("Book.pdf", "Book.pdf")
        )
        assertEquals(
            "local_Book.pdf_488206341973",
            LocalFolderSyncEngine.buildStableBookId("Book.pdf", "Series/Book.pdf")
        )
    }

    @Test
    fun `local folder sidecar filenames stay short for long book ids`() {
        val bookId = "local_" + "Very Long Book Name ".repeat(20) + ".pdf"

        assertEquals(".book_37739e3be68f.json", localFolderSyncMetadataFileName(bookId))
        assertEquals(".book_37739e3be68f.tmp", localFolderSyncMetadataTempFileName(bookId))
        assertEquals(".book_37739e3be68f_annotations.json", localFolderSyncAnnotationFileName(bookId))
        assertEquals(".book_37739e3be68f_annotations.tmp", localFolderSyncAnnotationTempFileName(bookId))
        assertTrue(localFolderSyncAnnotationFileName(bookId).length < 80)
    }

    @Test
    fun `sync imports scanned folder books with remote metadata`() {
        val state = SharedReaderScreenState()
        val folder = syncedFolder()
        val result = LocalFolderSyncEngine.syncFolder(
            state = state,
            folder = folder,
            files = listOf(scannedFile("Book.pdf", "Book.pdf")),
            remoteMetadata = mapOf(
                "local_Book.pdf" to metadata(
                    id = "local_Book.pdf",
                    title = "Remote Title",
                    lastPage = 4,
                    progress = 25f,
                    modified = 2_000L
                )
            ),
            nowMillis = 3_000L
        )

        val book = result.state.rawLibraryBooks.single()
        assertEquals("local_Book.pdf", book.id)
        assertEquals("Remote Title", book.title)
        assertEquals(4, book.lastPageIndex)
        assertEquals(25f, book.progressPercentage)
        assertEquals("C:/Library", book.sourceFolder)
        assertEquals(1, result.stats.newBooks)
    }

    @Test
    fun `newer remote metadata updates existing folder book`() {
        val existing = book(
            id = "local_Book.pdf",
            timestamp = 100L,
            title = "Local",
            progress = 10f
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(rawLibraryBooks = listOf(existing)),
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Book.pdf")),
            remoteMetadata = mapOf(
                "local_Book.pdf" to metadata(
                    id = "local_Book.pdf",
                    title = "Remote",
                    progress = 80f,
                    modified = 500L
                )
            ),
            nowMillis = 1_000L
        )

        val book = result.state.rawLibraryBooks.single()
        assertEquals("Remote", book.title)
        assertEquals(80f, book.progressPercentage)
        assertEquals(1, result.stats.remoteMetadataUpdates)
    }

    @Test
    fun `older remote metadata does not clobber local book state`() {
        val existing = book(
            id = "local_Book.pdf",
            timestamp = 500L,
            title = "Local",
            progress = 60f
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(rawLibraryBooks = listOf(existing)),
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Book.pdf")),
            remoteMetadata = mapOf(
                "local_Book.pdf" to metadata(
                    id = "local_Book.pdf",
                    title = "Remote",
                    progress = 5f,
                    modified = 100L
                )
            ),
            nowMillis = 1_000L
        )

        val book = result.state.rawLibraryBooks.single()
        assertEquals("Local", book.title)
        assertEquals(60f, book.progressPercentage)
        assertEquals(0, result.stats.remoteMetadataUpdates)
    }

    @Test
    fun `sync migrates desktop path ids and preserves references`() {
        val oldId = "C:/Library/Series/Book.pdf"
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(
                book(
                    id = oldId,
                    path = oldId,
                    displayName = "Book.pdf",
                    sourceFolder = "C:/Library"
                )
            ),
            selectedBookIds = setOf(oldId),
            pinnedHomeBookIds = setOf(oldId),
            openTabIds = listOf(oldId),
            activeTabBookId = oldId
        )

        val result = LocalFolderSyncEngine.syncFolder(
            state = state,
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Series/Book.pdf")),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L
        )
        val newId = "local_Book.pdf_488206341973"

        assertEquals(newId, result.state.rawLibraryBooks.single().id)
        assertEquals(setOf(newId), result.state.selectedBookIds)
        assertEquals(setOf(newId), result.state.pinnedHomeBookIds)
        assertEquals(listOf(newId), result.state.openTabIds)
        assertEquals(newId, result.state.activeTabBookId)
        assertEquals(mapOf(oldId to newId), result.idMigrations)
    }

    @Test
    fun `sync removes missing books from linked folder only`() {
        val missing = book(id = "local_Missing.pdf", path = "C:/Library/Missing.pdf")
        val keptExternal = book(
            id = "external",
            path = "C:/Other/External.pdf",
            sourceFolder = "C:/Other"
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(
                rawLibraryBooks = listOf(missing, keptExternal),
                selectedBookIds = setOf(missing.id),
                pinnedHomeBookIds = setOf(missing.id),
                openTabIds = listOf(missing.id),
                activeTabBookId = missing.id
            ),
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Book.pdf")),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L
        )

        assertNull(result.state.rawLibraryBooks.firstOrNull { it.id == "local_Missing.pdf" })
        assertTrue(result.state.rawLibraryBooks.any { it.id == "external" })
        assertTrue(result.state.selectedBookIds.isEmpty())
        assertTrue(result.state.openTabIds.isEmpty())
        assertNull(result.state.activeTabBookId)
        assertEquals(setOf("local_Missing.pdf"), result.removedBookIds)
        assertEquals(1, result.stats.removedBooks)
    }

    @Test
    fun `sync ignores unknown scanned files even with default allowed types`() {
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(),
            folder = SyncedFolder(
                uriString = "C:/Library",
                name = "Library",
                lastScanTime = 0L
            ),
            files = listOf(scannedFile("archive.zip", "archive.zip", type = FileType.UNKNOWN)),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L
        )

        assertTrue(result.state.rawLibraryBooks.isEmpty())
        assertEquals(0, result.stats.supportedFiles)
        assertEquals(0, result.stats.newBooks)
    }

    @Test
    fun `default synced folder allowed types exclude unknown`() {
        assertFalse(FileType.UNKNOWN in SyncedFolder("C:/Library", "Library", lastScanTime = 0L).allowedFileTypes)
    }

    @Test
    fun `metadata sidecar is skipped for clean unread folder books`() {
        assertNull(book(id = "local_Book.pdf", isRecent = false, progress = null).toSharedFolderBookMetadata())
        assertNotNull(book(id = "local_Book.pdf", isRecent = true).toSharedFolderBookMetadata())
    }

    @Test
    fun `metadata sidecar preserves precise reader position`() {
        val locator = ReaderLocator(
            chapterIndex = 2,
            pageIndex = 7,
            startOffset = 320,
            endOffset = 320,
            cfi = "desktop:2:320:320"
        )

        val metadata = book(
            id = "local_Book.pdf",
            progress = 45f,
            readerPosition = locator
        ).toSharedFolderBookMetadata() ?: error("Expected sidecar")
        val restored = metadata.toBookItem(
            file = scannedFile("Book.pdf", "Book.pdf"),
            existing = null,
            nowMillis = 2_000L
        )

        assertEquals(2, metadata.lastChapterIndex)
        assertEquals(7, metadata.lastPage)
        assertEquals("desktop:2:320:320", metadata.lastPositionCfi)
        assertNull(metadata.locatorBlockIndex)
        assertNull(metadata.locatorCharOffset)
        assertEquals(locator, restored.readerPosition)
    }

    @Test
    fun `sync resets extracted metadata and cover when folder file size changes`() {
        val existing = book(
            id = "local_Book.pdf",
            fileSize = 123L,
            coverImagePath = "C:/Covers/book.png",
            folderTextMetadataParsed = true
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(rawLibraryBooks = listOf(existing)),
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Book.pdf", size = 456L)),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L
        )

        val book = result.state.rawLibraryBooks.single()
        assertEquals(456L, book.fileSize)
        assertNull(book.coverImagePath)
        assertFalse(book.folderTextMetadataParsed)
        assertEquals(1, result.stats.updatedBooks)
    }

    private fun syncedFolder(): SyncedFolder {
        return SyncedFolder(
            uriString = "C:/Library",
            name = "Library",
            lastScanTime = 0L,
            allowedFileTypes = setOf(FileType.PDF, FileType.EPUB)
        )
    }

    private fun scannedFile(
        name: String,
        relativePath: String,
        size: Long = 123L,
        type: FileType = FileType.PDF
    ): SharedFolderScannedFile {
        return SharedFolderScannedFile(
            name = name,
            path = "C:/Library/$relativePath",
            sourceFolder = "C:/Library",
            relativePath = relativePath,
            type = type,
            size = size,
            lastModified = 100L
        )
    }

    private fun book(
        id: String,
        path: String = "C:/Library/Book.pdf",
        displayName: String = "Book.pdf",
        sourceFolder: String = "C:/Library",
        timestamp: Long = 100L,
        title: String = "Book",
        progress: Float? = null,
        isRecent: Boolean = false,
        fileSize: Long = 0L,
        coverImagePath: String? = null,
        folderTextMetadataParsed: Boolean = false,
        readerPosition: ReaderLocator? = null
    ): BookItem {
        return BookItem(
            id = id,
            path = path,
            type = FileType.PDF,
            displayName = displayName,
            timestamp = timestamp,
            coverImagePath = coverImagePath,
            title = title,
            progressPercentage = progress,
            fileSize = fileSize,
            sourceFolder = sourceFolder,
            isRecent = isRecent,
            folderTextMetadataParsed = folderTextMetadataParsed,
            readerPosition = readerPosition
        )
    }

    private fun metadata(
        id: String,
        title: String = "Book",
        lastPage: Int? = null,
        progress: Float = 0f,
        modified: Long
    ): SharedFolderBookMetadata {
        return SharedFolderBookMetadata(
            bookId = id,
            title = title,
            author = null,
            displayName = "Book.pdf",
            type = FileType.PDF.name,
            lastChapterIndex = null,
            lastPage = lastPage,
            lastPositionCfi = null,
            progressPercentage = progress,
            isRecent = true,
            lastModifiedTimestamp = modified,
            bookmarksJson = null,
            locatorBlockIndex = null,
            locatorCharOffset = null,
            customName = null,
            highlightsJson = null
        )
    }
}
