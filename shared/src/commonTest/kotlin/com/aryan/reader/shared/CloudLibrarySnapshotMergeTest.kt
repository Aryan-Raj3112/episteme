package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CloudLibrarySnapshotMergeTest {
    @Test
    fun `newer remote reading state applies while local file identity is preserved`() {
        val localBook = BookItem(
            id = "book",
            path = "/local/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 1L,
            progressPercentage = 10f,
            lastPageIndex = 1,
            readingPositionModifiedTimestamp = 100L,
        )
        val remoteBook = localBook.copy(
            path = "/remote/device/book.epub",
            progressPercentage = 70f,
            lastPageIndex = 7,
            readingPositionModifiedTimestamp = 200L,
        )

        val merged = mergeCloudReadingState(
            local = SharedLibrarySnapshot(books = listOf(localBook)),
            remote = SharedLibrarySnapshot(books = listOf(remoteBook)),
        ).books.single()

        assertEquals("/local/book.epub", merged.path)
        assertEquals(70f, merged.progressPercentage)
        assertEquals(7, merged.lastPageIndex)
        assertEquals(200L, merged.readingPositionModifiedTimestamp)
    }

    @Test
    fun `older remote state is ignored and remote-only books stay hidden without content`() {
        val localBook = BookItem(
            id = "local",
            path = "/local/book.pdf",
            type = FileType.PDF,
            displayName = "book.pdf",
            timestamp = 1L,
            progressPercentage = 80f,
            readingPositionModifiedTimestamp = 300L,
        )
        val staleRemote = localBook.copy(
            path = null,
            progressPercentage = 20f,
            readingPositionModifiedTimestamp = 200L,
        )
        val remoteOnly = staleRemote.copy(id = "remote-only", displayName = "missing.pdf")

        val merged = mergeCloudReadingState(
            local = SharedLibrarySnapshot(books = listOf(localBook)),
            remote = SharedLibrarySnapshot(books = listOf(staleRemote, remoteOnly)),
        )

        assertEquals(listOf("local"), merged.books.map(BookItem::id))
        assertEquals(80f, merged.books.single().progressPercentage)
        assertNull(merged.books.firstOrNull { it.id == "remote-only" })
    }

    @Test
    fun `newer remote metadata applies without replacing local recency or path`() {
        val local = BookItem(
            id = "book",
            path = "/local/book.epub",
            type = FileType.EPUB,
            displayName = "Local name",
            timestamp = 500L,
            title = "Local title",
            metadataModifiedTimestamp = 100L,
            readingPositionModifiedTimestamp = 300L,
            progressPercentage = 60f,
        )
        val remote = local.copy(
            path = "/remote/book.epub",
            displayName = "Remote name",
            timestamp = 900L,
            title = "Remote title",
            metadataModifiedTimestamp = 200L,
            readingPositionModifiedTimestamp = 200L,
            progressPercentage = 10f,
        )

        val merged = mergeCloudReadingState(
            local = SharedLibrarySnapshot(books = listOf(local)),
            remote = SharedLibrarySnapshot(books = listOf(remote)),
        ).books.single()

        assertEquals("/local/book.epub", merged.path)
        assertEquals(500L, merged.timestamp)
        assertEquals("Remote name", merged.displayName)
        assertEquals("Remote title", merged.title)
        assertEquals(200L, merged.metadataModifiedTimestamp)
        assertEquals(60f, merged.progressPercentage)
    }

    @Test
    fun `remote-only book appears only after its content was downloaded`() {
        val remoteBook = BookItem(
            id = "remote",
            path = "/other-device/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 10L,
            readingPositionModifiedTimestamp = 20L,
        )

        val unavailable = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(),
            remote = SharedLibrarySnapshot(books = listOf(remoteBook)),
            downloadedBookPaths = emptyMap(),
        )
        val available = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(),
            remote = SharedLibrarySnapshot(books = listOf(remoteBook)),
            downloadedBookPaths = mapOf("remote" to "/local/cloud/remote.epub"),
        )

        assertEquals(emptyList(), unavailable.books)
        assertEquals("/local/cloud/remote.epub", available.books.single().path)
        assertEquals(true, available.books.single().isAvailable)
        assertNull(available.books.single().sourceFolder)
    }

    @Test
    fun `downloaded newer content replaces the existing local path`() {
        val local = BookItem(
            id = "book",
            path = "/local/stale.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 1L,
            fileContentModifiedTimestamp = 100L,
        )
        val remote = local.copy(
            path = null,
            timestamp = 2L,
            fileContentModifiedTimestamp = 200L,
        )

        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(books = listOf(local)),
            remote = SharedLibrarySnapshot(books = listOf(remote)),
            downloadedBookPaths = mapOf("book" to "/local/cloud/book.epub"),
        )

        assertEquals("/local/cloud/book.epub", merged.books.single().path)
        assertEquals(200L, merged.books.single().fileContentModifiedTimestamp)
    }

    @Test
    fun `newer remote deletion removes local book and is retained as a tombstone`() {
        val local = BookItem(
            id = "book",
            path = "/local/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 100L,
        )
        val tombstone = CloudBookTombstone(
            bookId = local.id,
            type = local.type.name,
            deletedAt = 200L,
        )

        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(books = listOf(local)),
            remote = SharedLibrarySnapshot(bookTombstones = listOf(tombstone)),
            downloadedBookPaths = emptyMap(),
        )

        assertEquals(emptyList(), merged.books)
        assertEquals(listOf(tombstone), merged.bookTombstones)
    }

    @Test
    fun `newer local book resurrects over stale remote deletion`() {
        val local = BookItem(
            id = "book",
            path = "/local/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 300L,
        )

        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(books = listOf(local)),
            remote = SharedLibrarySnapshot(
                bookTombstones = listOf(
                    CloudBookTombstone(
                        bookId = local.id,
                        type = local.type.name,
                        deletedAt = 200L,
                    )
                )
            ),
            downloadedBookPaths = emptyMap(),
        )

        assertEquals(listOf(local), merged.books)
        assertEquals(emptyList(), merged.bookTombstones)
    }

    @Test
    fun `equal deletion and book timestamps preserve the local book like android`() {
        val local = BookItem(
            id = "book",
            path = "/local/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 200L,
        )

        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(books = listOf(local)),
            remote = SharedLibrarySnapshot(
                bookTombstones = listOf(
                    CloudBookTombstone(
                        bookId = local.id,
                        type = local.type.name,
                        deletedAt = 200L,
                    )
                )
            ),
            downloadedBookPaths = emptyMap(),
        )

        assertEquals(listOf(local), merged.books)
        assertEquals(emptyList(), merged.bookTombstones)
    }

    @Test
    fun `cloud merge keeps newer shelf tombstones and newest membership clocks`() {
        val localBook = BookItem(
            id = "book",
            path = "/local/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 1L,
        )
        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(
                books = listOf(localBook),
                shelfRecords = listOf(
                    ShelfRecord("keep", "Local", modifiedAt = 10L),
                    ShelfRecord("delete", "Local delete", modifiedAt = 30L, isDeleted = true),
                ),
                shelfRefs = listOf(BookShelfRef("book", "keep", addedAt = 10L)),
            ),
            remote = SharedLibrarySnapshot(
                shelfRecords = listOf(
                    ShelfRecord("keep", "Remote", modifiedAt = 20L),
                    ShelfRecord("delete", "Remote delete", modifiedAt = 20L),
                ),
                shelfRefs = listOf(
                    BookShelfRef("book", "keep", addedAt = 40L),
                    BookShelfRef("missing", "keep", addedAt = 50L),
                ),
            ),
            downloadedBookPaths = emptyMap(),
        )

        assertEquals(listOf("keep"), merged.shelfRecords.map(ShelfRecord::id))
        assertEquals("Remote", merged.shelfRecords.single().name)
        assertEquals(listOf(40L), merged.shelfRefs.map(BookShelfRef::addedAt))
    }

    @Test
    fun `cloud merge selects newest custom font and preserves existing folder grants`() {
        val localFolder = SyncedFolder("file:///local", "Local", lastScanTime = 20L)
        val merged = mergeCloudLibrarySnapshotWithDownloadedBooks(
            local = SharedLibrarySnapshot(
                syncedFolders = listOf(localFolder),
                customFonts = listOf(
                    CustomFontItem("font", "Local", "local.ttf", "ttf", "/local.ttf", 10L),
                ),
            ),
            remote = SharedLibrarySnapshot(
                syncedFolders = listOf(
                    SyncedFolder("file:///local", "Remote", lastScanTime = 30L),
                    SyncedFolder("file:///remote", "Remote-only", lastScanTime = 40L),
                ),
                customFonts = listOf(
                    CustomFontItem("font", "Remote", "remote.ttf", "ttf", "/remote.ttf", 20L),
                    CustomFontItem("font2", "Remote 2", "remote2.ttf", "ttf", "/remote2.ttf", 30L),
                ),
            ),
            downloadedBookPaths = emptyMap(),
            downloadedFontPaths = mapOf("font" to "/remote.ttf", "font2" to "/remote2.ttf"),
        )

        assertEquals("Remote", merged.customFonts.first { it.id == "font" }.displayName)
        assertEquals(setOf("font", "font2"), merged.customFonts.mapTo(mutableSetOf(), CustomFontItem::id))
        assertEquals(listOf(localFolder.uriString), merged.syncedFolders.map(SyncedFolder::uriString))
    }
}
