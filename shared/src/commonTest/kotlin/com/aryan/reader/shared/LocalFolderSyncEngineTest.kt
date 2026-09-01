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
    fun `sidecar display name survives physical folder scan`() {
        val existing = book(
            id = "local_Book.pdf",
            timestamp = 500L,
            displayName = "Reader Name",
            title = "Local"
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(rawLibraryBooks = listOf(existing)),
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Book.pdf")),
            remoteMetadata = mapOf(
                "local_Book.pdf" to metadata(
                    id = "local_Book.pdf",
                    displayName = "Reader Name",
                    modified = 500L
                )
            ),
            nowMillis = 1_000L
        )

        assertEquals("Reader Name", result.state.rawLibraryBooks.single().displayName)
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
    fun `sync migrates legacy content hash folder identity without losing reading state`() {
        val oldId = "a".repeat(64)
        val existing = book(
            id = oldId,
            path = "C:/Library/Book.epub",
            displayName = "Book.epub",
            sourceFolder = "C:/Library",
            fileSize = 123L,
            title = "Embedded title",
        ).copy(
            progressPercentage = 42f,
            lastPageIndex = 7,
            fileContentModifiedTimestamp = 500L,
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(
                rawLibraryBooks = listOf(existing),
                selectedBookIds = setOf(oldId),
                pinnedLibraryBookIds = setOf(oldId),
            ),
            folder = syncedFolder(),
            files = listOf(
                scannedFile(
                    name = "Book.epub",
                    relativePath = "Book.epub",
                    size = 123L,
                    lastModified = 500L,
                )
            ),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L,
        )

        val migrated = result.state.rawLibraryBooks.single()
        assertEquals("local_Book.epub", migrated.id)
        assertEquals(42f, migrated.progressPercentage)
        assertEquals(7, migrated.lastPageIndex)
        assertEquals("Embedded title", migrated.title)
        assertEquals(setOf("local_Book.epub"), result.state.selectedBookIds)
        assertEquals(setOf("local_Book.epub"), result.state.pinnedLibraryBookIds)
        assertEquals(mapOf(oldId to "local_Book.epub"), result.idMigrations)
    }

    @Test
    fun `sync resolves legacy root id collision before migrating subfolder book`() {
        val oldId = "local_Book.pdf"
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(
                book(
                    id = oldId,
                    path = "C:/Library/Series/Book.pdf",
                    displayName = "Book.pdf",
                    sourceFolder = "C:/Library"
                )
            ),
            selectedBookIds = setOf(oldId)
        )

        val result = LocalFolderSyncEngine.syncFolder(
            state = state,
            folder = syncedFolder(),
            files = listOf(
                scannedFile("Book.pdf", "Book.pdf"),
                scannedFile("Book.pdf", "Series/Book.pdf")
            ),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L
        )
        val migratedId = "local_Book.pdf_488206341973"

        assertEquals(
            listOf("local_Book.pdf", migratedId),
            result.state.rawLibraryBooks.map { it.id }.sorted()
        )
        assertEquals(setOf(migratedId), result.state.selectedBookIds)
        assertEquals(mapOf(oldId to migratedId), result.idMigrations)
        assertEquals(1, result.stats.newBooks)
        assertEquals(1, result.stats.migratedBooks)
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
    fun `partial folder scan keeps missing books and scan watermark`() {
        val missing = book(id = "local_Missing.pdf", path = "C:/Library/Missing.pdf")
        val folder = syncedFolder().copy(lastScanTime = 800L)
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(missing),
            syncedFolders = listOf(folder),
            lastFolderScanTime = 800L
        )

        val result = LocalFolderSyncEngine.syncFolder(
            state = state,
            folder = folder,
            files = emptyList(),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L,
            scanStatus = LocalFolderScanStatus.PARTIAL
        )

        assertEquals(listOf(missing), result.state.rawLibraryBooks)
        assertTrue(result.removedBookIds.isEmpty())
        assertEquals(0, result.stats.removedBooks)
        assertEquals(800L, result.state.syncedFolders.single().lastScanTime)
        assertEquals(800L, result.state.lastFolderScanTime)
    }

    @Test
    fun `metadata-only pass does not advance physical scan watermark`() {
        val folder = syncedFolder().copy(lastScanTime = 800L)
        val state = SharedReaderScreenState(
            syncedFolders = listOf(folder),
            lastFolderScanTime = 800L
        )

        val result = LocalFolderSyncEngine.syncFolder(
            state = state,
            folder = folder,
            files = emptyList(),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L,
            metadataOnly = true,
            scanStatus = LocalFolderScanStatus.NOT_SCANNED
        )

        assertEquals(800L, result.state.syncedFolders.single().lastScanTime)
        assertEquals(800L, result.state.lastFolderScanTime)
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
    fun `disabled synced folder does not import files or metadata`() {
        val existing = book(
            id = "local_Book.pdf",
            timestamp = 100L,
            progress = 10f
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(
                rawLibraryBooks = listOf(existing),
                syncedFolders = listOf(syncedFolder().copy(localSyncEnabled = false))
            ),
            folder = syncedFolder().copy(localSyncEnabled = false),
            files = listOf(scannedFile("New.pdf", "New.pdf")),
            remoteMetadata = mapOf(
                "local_Book.pdf" to metadata(
                    id = "local_Book.pdf",
                    progress = 80f,
                    modified = 500L
                )
            ),
            nowMillis = 1_000L
        )

        assertEquals(listOf(existing), result.state.rawLibraryBooks)
        assertEquals(0, result.stats.newBooks)
        assertEquals(0, result.stats.remoteMetadataUpdates)
        assertTrue(result.removedBookIds.isEmpty())
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
    fun `metadata sidecar preserves android block reader position`() {
        val locator = ReaderLocator(
            chapterIndex = 1,
            pageIndex = 5,
            blockIndex = 44,
            charOffset = 120,
            cfi = "android-locator:1:44:120"
        )

        val metadata = book(
            id = "local_Book.epub",
            type = FileType.EPUB,
            progress = 37f,
            readerPosition = locator
        ).toSharedFolderBookMetadata() ?: error("Expected sidecar")
        val restored = metadata.toBookItem(
            file = scannedFile("Book.epub", "Book.epub"),
            existing = null,
            nowMillis = 2_000L
        )

        assertEquals(1, metadata.lastChapterIndex)
        assertEquals(5, metadata.lastPage)
        assertEquals("android-locator:1:44:120", metadata.lastPositionCfi)
        assertEquals(44, metadata.locatorBlockIndex)
        assertEquals(120, metadata.locatorCharOffset)
        assertEquals(locator, restored.readerPosition)
    }

    @Test
    fun `metadata sidecar carries editable metadata and keeps v1 compatibility`() {
        val local = book(id = "local_Book.pdf")
            .copy(
                isRecent = true,
                title = "Edited Title",
                author = "Edited Author",
                seriesName = "Edited Series",
                seriesIndex = 2.0,
                description = "<p>Edited summary</p>",
                originalTitle = "Original Title",
                originalAuthor = "Original Author",
                originalSeriesName = "Original Series",
                originalSeriesIndex = 1.0,
                originalDescription = "Original summary"
            )

        val metadata = local.toSharedFolderBookMetadata() ?: error("Expected sidecar")
        val legacyMetadata = metadata.copy(
            schemaVersion = 1,
            presentFields = emptySet(),
            title = null,
            author = null,
            seriesName = null,
            seriesIndex = null,
            description = null,
            originalTitle = null,
            originalAuthor = null,
            originalSeriesName = null,
            originalSeriesIndex = null,
            originalDescription = null,
        )
        val restored = metadata.toBookItem(
            file = scannedFile("Book.pdf", "Book.pdf"),
            existing = book(id = "local_Book.pdf", title = "Stale"),
            nowMillis = 2_000L
        )
        val restoredFromLegacy = legacyMetadata.toBookItem(
            file = scannedFile("Book.pdf", "Book.pdf"),
            existing = book(id = "local_Book.pdf", title = "Stale"),
            nowMillis = 2_000L
        )

        assertEquals("Edited Title", metadata.title)
        assertEquals("Edited Author", metadata.author)
        assertEquals("Edited Series", metadata.seriesName)
        assertEquals("<p>Edited summary</p>", metadata.description)
        assertEquals("Original Title", metadata.originalTitle)
        assertEquals("Edited Title", restored.title)
        assertEquals("Edited Author", restored.author)
        assertEquals("Edited Series", restored.seriesName)
        assertEquals("<p>Edited summary</p>", restored.description)
        assertEquals("Stale", restoredFromLegacy.title)
        assertNull(restoredFromLegacy.author)
    }

    @Test
    fun `v2 metadata sidecar preserves explicit editable clears`() {
        val existing = book(
            id = "local_Book.pdf",
            title = "Old title",
            progress = 65f,
        ).copy(
            author = "Old author",
            seriesName = "Old series",
            titleSortKey = "Custom name",
        )
        val metadata = SharedFolderBookMetadata(
            bookId = existing.id,
            title = null,
            author = null,
            displayName = existing.displayName,
            type = existing.type.name,
            lastChapterIndex = null,
            lastPage = null,
            lastPositionCfi = null,
            progressPercentage = 0f,
            isRecent = true,
            lastModifiedTimestamp = 99L,
            bookmarksJson = null,
            locatorBlockIndex = null,
            locatorCharOffset = null,
            customName = null,
            highlightsJson = null,
            seriesName = null,
        )
        val restored = SharedFolderBookMetadata.fromJsonString(metadata.toJsonString())
            ?.toBookItem(scannedFile("Book.pdf", "Book.pdf"), existing = existing, nowMillis = 100L)
            ?: error("Expected metadata")

        assertNull(restored.title)
        assertNull(restored.author)
        assertNull(restored.seriesName)
        assertNull(restored.titleSortKey)
        assertEquals(0f, restored.progressPercentage)
        assertNull(restored.readerPosition)
    }

    @Test
    fun `sync resets extracted metadata and cover when folder file modified time changes`() {
        val existing = book(
            id = "local_Book.pdf",
            fileSize = 123L,
            title = "Extracted title",
            coverImagePath = "C:/Covers/book.png",
            folderTextMetadataParsed = true
        ).copy(
            author = "Extracted author",
            description = "Extracted summary",
            seriesName = "Extracted series",
            seriesIndex = 1.0,
            originalTitle = "Extracted title",
            originalAuthor = "Extracted author",
            originalDescription = "Extracted summary",
            fileContentModifiedTimestamp = 100L
        )
        val result = LocalFolderSyncEngine.syncFolder(
            state = SharedReaderScreenState(rawLibraryBooks = listOf(existing)),
            folder = syncedFolder(),
            files = listOf(scannedFile("Book.pdf", "Book.pdf", size = 123L, lastModified = 500L)),
            remoteMetadata = emptyMap(),
            nowMillis = 1_000L
        )

        val updated = result.state.rawLibraryBooks.single()
        assertNull(updated.coverImagePath)
        assertFalse(updated.folderTextMetadataParsed)
        assertEquals(500L, updated.fileContentModifiedTimestamp)
        assertEquals("Book", updated.title)
        assertNull(updated.author)
        assertNull(updated.description)
        assertNull(updated.seriesName)
        assertNull(updated.originalTitle)
        assertEquals(existing.progressPercentage, updated.progressPercentage)
        assertEquals(existing.lastPageIndex, updated.lastPageIndex)
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
        type: FileType = FileType.PDF,
        lastModified: Long = 100L
    ): SharedFolderScannedFile {
        return SharedFolderScannedFile(
            name = name,
            path = "C:/Library/$relativePath",
            sourceFolder = "C:/Library",
            relativePath = relativePath,
            type = type,
            size = size,
            lastModified = lastModified
        )
    }

    private fun book(
        id: String,
        path: String = "C:/Library/Book.pdf",
        displayName: String = "Book.pdf",
        sourceFolder: String = "C:/Library",
        timestamp: Long = 100L,
        title: String = "Book",
        type: FileType = FileType.PDF,
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
            type = type,
            displayName = displayName,
            timestamp = timestamp,
            coverImagePath = coverImagePath,
            title = title,
            progressPercentage = progress,
            fileSize = fileSize,
            fileContentModifiedTimestamp = 100L,
            sourceFolder = sourceFolder,
            isRecent = isRecent,
            folderTextMetadataParsed = folderTextMetadataParsed,
            readerPosition = readerPosition
        )
    }

    private fun metadata(
        id: String,
        title: String = "Book",
        displayName: String = "Book.pdf",
        lastPage: Int? = null,
        progress: Float = 0f,
        modified: Long
    ): SharedFolderBookMetadata {
        return SharedFolderBookMetadata(
            bookId = id,
            title = title,
            author = null,
            displayName = displayName,
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
            highlightsJson = null,
            presentFields = sidecarPresentFields
        )
    }

    private val sidecarPresentFields = setOf(
        "bookId",
        "schemaVersion",
        "title",
        "author",
        "displayName",
        "type",
        "lastChapterIndex",
        "lastPage",
        "lastPositionCfi",
        "progressPercentage",
        "isRecent",
        "lastModifiedTimestamp",
        "bookmarksJson",
        "locatorBlockIndex",
        "locatorCharOffset",
        "customName",
        "highlightsJson",
        "seriesName",
        "seriesIndex",
        "description",
        "originalTitle",
        "originalAuthor",
        "originalSeriesName",
        "originalSeriesIndex",
        "originalDescription"
    )
}
