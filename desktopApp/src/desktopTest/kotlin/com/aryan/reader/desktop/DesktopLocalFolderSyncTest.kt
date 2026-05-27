package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.LOCAL_FOLDER_SYNC_DATA_DIR
import com.aryan.reader.shared.SharedFolderBookMetadata
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SyncedFolder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopLocalFolderSyncTest {
    @Test
    fun `target folder sync imports files before desktop metadata extraction`() {
        val root = Files.createTempDirectory("reader-desktop-folder-sync").toFile()
        try {
            val bookFile = File(root, "Notes.txt").apply { writeText("Notes") }

            val result = DesktopLocalFolderSync.sync(
                state = SharedReaderScreenState(),
                shelfRefs = emptyList(),
                targetFolder = root,
                nowMillis = 3_000L,
                extractMetadata = false
            )

            val syncedBook = result.state.rawLibraryBooks.single()
            assertEquals("local_Notes.txt", syncedBook.id)
            assertEquals(bookFile.absolutePath, syncedBook.path)
            assertEquals(root.absolutePath, syncedBook.sourceFolder)
            assertEquals(listOf(root.absolutePath), result.processedFolderUris)
            assertEquals(1, result.state.syncedFolders.size)
            assertEquals(1, result.stats.newBooks)
            assertEquals(0, result.metadataStats.updatedBooks)
            assertNull(syncedBook.coverImagePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `metadata-only sync imports sidecar metadata without scanning physical files`() {
        val root = Files.createTempDirectory("reader-desktop-folder-sync").toFile()
        try {
            File(root, "New.pdf").writeText("%PDF")
            val existingFile = File(root, "Existing.pdf")
            val existingId = "local_Existing.pdf"
            writeMetadataSidecar(
                root = root,
                metadata = metadata(
                    id = existingId,
                    title = "Remote Title",
                    progress = 72f,
                    modified = 2_000L
                )
            )

            val existingBook = BookItem(
                id = existingId,
                path = existingFile.absolutePath,
                type = FileType.PDF,
                displayName = existingFile.name,
                timestamp = 100L,
                title = "Local Title",
                progressPercentage = 5f,
                sourceFolder = root.absolutePath
            )

            val result = DesktopLocalFolderSync.sync(
                state = SharedReaderScreenState(
                    rawLibraryBooks = listOf(existingBook),
                    syncedFolders = listOf(syncedFolder(root))
                ),
                shelfRefs = emptyList(),
                nowMillis = 3_000L,
                metadataOnly = true
            )

            assertEquals(1, result.state.rawLibraryBooks.size)
            val syncedBook = result.state.rawLibraryBooks.single()
            assertEquals(existingId, syncedBook.id)
            assertEquals("Local Title", syncedBook.title)
            assertEquals(72f, syncedBook.progressPercentage)
            assertNull(result.state.rawLibraryBooks.firstOrNull { it.id == "local_New.pdf" })
            assertEquals(0, result.stats.scannedFiles)
            assertEquals(0, result.stats.newBooks)
            assertEquals(0, result.stats.removedBooks)
            assertEquals(1, result.stats.remoteMetadataUpdates)
            assertTrue(result.processedFolderUris.contains(root.absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `disabled folder is not scanned or written`() {
        val root = Files.createTempDirectory("reader-desktop-folder-sync").toFile()
        try {
            File(root, "Notes.txt").writeText("Notes")
            val existingBook = BookItem(
                id = "local_Existing.pdf",
                path = File(root, "Existing.pdf").absolutePath,
                type = FileType.PDF,
                displayName = "Existing.pdf",
                timestamp = 100L,
                progressPercentage = 50f,
                sourceFolder = root.absolutePath
            )

            val result = DesktopLocalFolderSync.sync(
                state = SharedReaderScreenState(
                    rawLibraryBooks = listOf(existingBook),
                    syncedFolders = listOf(syncedFolder(root).copy(localSyncEnabled = false))
                ),
                shelfRefs = emptyList(),
                nowMillis = 3_000L
            )

            assertEquals(listOf(existingBook), result.state.rawLibraryBooks)
            assertTrue(result.processedFolderUris.isEmpty())
            assertEquals(0, result.stats.newBooks)
            assertTrue(!File(root, LOCAL_FOLDER_SYNC_DATA_DIR).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun syncedFolder(root: File): SyncedFolder {
        return SyncedFolder(
            uriString = root.absolutePath,
            name = root.name,
            lastScanTime = 0L,
            allowedFileTypes = setOf(FileType.PDF)
        )
    }

    private fun writeMetadataSidecar(root: File, metadata: SharedFolderBookMetadata) {
        val syncDir = File(root, LOCAL_FOLDER_SYNC_DATA_DIR).apply { mkdirs() }
        File(syncDir, ".${metadata.bookId}.json").writeText(metadata.toJsonString())
    }

    private fun metadata(
        id: String,
        title: String,
        progress: Float,
        modified: Long
    ): SharedFolderBookMetadata {
        return SharedFolderBookMetadata(
            bookId = id,
            title = title,
            author = null,
            displayName = "Existing.pdf",
            type = FileType.PDF.name,
            lastChapterIndex = null,
            lastPage = null,
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
