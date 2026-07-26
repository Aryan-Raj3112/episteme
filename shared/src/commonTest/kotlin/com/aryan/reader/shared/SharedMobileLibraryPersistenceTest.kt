package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileLibraryPersistenceTest {
    @Test
    fun snapshotRoundTripPreservesDurableMobileLibraryState() {
        val first = book("first", 20L)
        val second = book("second", 10L)
        val manual = Shelf(
            id = "manual",
            name = "Reading",
            type = ShelfType.MANUAL,
            books = listOf(first),
        )
        val generatedFolder = Shelf(
            id = "folder",
            name = "Local",
            type = ShelfType.FOLDER,
            books = listOf(first, second),
        )
        val folder = SyncedFolder(
            uriString = "ios-local-folder://local",
            name = "Local",
            lastScanTime = 123L,
        )
        val state = SharedReaderScreenState(
            rawLibraryBooks = listOf(first, second),
            recentBooks = listOf(first, second),
            libraryBooks = listOf(first, second),
            shelves = listOf(manual, generatedFolder),
            syncedFolders = listOf(folder),
            recentFilesLimit = 7,
            isTabsEnabled = true,
            openTabIds = listOf(first.id),
            activeTabBookId = first.id,
            pinnedHomeBookIds = setOf(first.id),
            pinnedLibraryBookIds = setOf(second.id),
            useStrictFileFilter = true,
            externalFileBehavior = "TEMPORARY",
            usePdfFileNameAsDisplayName = true,
            appLanguageTag = "fr",
            appThemeMode = AppThemeMode.DARK,
        )

        val snapshot = state.toSharedMobileLibrarySnapshot()
        val restored = SharedLibrarySnapshotJson
            .decodeOrEmpty(SharedLibrarySnapshotJson.encode(snapshot))
            .toSharedMobileReaderState()

        assertEquals(listOf(first.id, second.id), restored.rawLibraryBooks.map { it.id })
        assertEquals(listOf(first.id), restored.openTabIds)
        assertEquals(first.id, restored.activeTabBookId)
        assertEquals(setOf(first.id), restored.pinnedHomeBookIds)
        assertEquals(setOf(second.id), restored.pinnedLibraryBookIds)
        assertEquals(listOf(folder), restored.syncedFolders)
        assertEquals(7, restored.recentFilesLimit)
        assertTrue(restored.useStrictFileFilter)
        assertEquals("TEMPORARY", restored.externalFileBehavior)
        assertTrue(restored.usePdfFileNameAsDisplayName)
        assertEquals("fr", restored.appLanguageTag)
        assertEquals(AppThemeMode.DARK, restored.appThemeMode)
        assertTrue(restored.shelves.any { it.id == manual.id && it.books.map(BookItem::id) == listOf(first.id) })
        assertTrue(snapshot.shelfRecords.any { it.id == manual.id })
        assertFalse(snapshot.shelfRecords.any { it.id == generatedFolder.id })
    }

    @Test
    fun restoreDropsTabsThatReferenceMissingBooks() {
        val restored = SharedLibrarySnapshot(
            openTabIds = listOf("missing"),
            activeTabBookId = "missing",
        ).toSharedMobileReaderState()

        assertTrue(restored.openTabIds.isEmpty())
        assertEquals(null, restored.activeTabBookId)
    }

    private fun book(id: String, timestamp: Long): BookItem {
        return BookItem(
            id = id,
            path = "/$id.epub",
            type = FileType.EPUB,
            displayName = "$id.epub",
            timestamp = timestamp,
            title = id,
            sourceFolder = "Local",
        )
    }
}
