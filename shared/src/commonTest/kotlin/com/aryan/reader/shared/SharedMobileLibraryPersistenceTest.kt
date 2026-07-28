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
            directBookAddedAt = mapOf(first.id to 1234L),
        )
        val smartRules = SmartCollectionEngine.toJson(
            SmartCollectionDefinition(
                rules = listOf(SmartRule(SmartField.TITLE, SmartOperator.CONTAINS, "first"))
            )
        )
        val smart = Shelf(
            id = "smart",
            name = "First books",
            type = ShelfType.SMART,
            books = listOf(first),
            smartRulesJson = smartRules,
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
            shelves = listOf(manual, smart, generatedFolder),
            syncedFolders = listOf(folder),
            recentFilesLimit = 20,
            isTabsEnabled = true,
            isFolderSyncEnabled = true,
            sortOrder = SortOrder.AUTHOR_ASC,
            libraryFilters = LibraryFilters(
                fileTypes = setOf(FileType.EPUB),
                sourceFolders = setOf("Local"),
                readStatus = ReadStatusFilter.UNREAD,
                tagIds = setOf("favorite"),
            ),
            mainScreenStartPage = 1,
            libraryScreenStartPage = 2,
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
        assertEquals(20, restored.recentFilesLimit)
        assertTrue(restored.isFolderSyncEnabled)
        assertEquals(SortOrder.AUTHOR_ASC, restored.sortOrder)
        assertEquals(setOf(FileType.EPUB), restored.libraryFilters.fileTypes)
        assertEquals(setOf("Local"), restored.libraryFilters.sourceFolders)
        assertEquals(ReadStatusFilter.UNREAD, restored.libraryFilters.readStatus)
        assertEquals(setOf("favorite"), restored.libraryFilters.tagIds)
        assertEquals(1, restored.mainScreenStartPage)
        assertEquals(2, restored.libraryScreenStartPage)
        assertTrue(restored.useStrictFileFilter)
        assertEquals("TEMPORARY", restored.externalFileBehavior)
        assertTrue(restored.usePdfFileNameAsDisplayName)
        assertEquals("fr", restored.appLanguageTag)
        assertEquals(AppThemeMode.DARK, restored.appThemeMode)
        assertTrue(restored.shelves.any { it.id == manual.id && it.books.map(BookItem::id) == listOf(first.id) })
        assertTrue(restored.shelves.any { it.id == smart.id && it.smartRulesJson == smartRules })
        assertTrue(snapshot.shelfRecords.any { it.id == manual.id })
        assertEquals(
            1234L,
            snapshot.shelfRefs.single { it.shelfId == manual.id && it.bookId == first.id }.addedAt,
        )
        assertEquals(smartRules, snapshot.shelfRecords.single { it.id == smart.id }.smartRulesJson)
        assertFalse(snapshot.shelfRefs.any { it.shelfId == smart.id })
        assertFalse(snapshot.shelfRecords.any { it.id == generatedFolder.id })
    }

    @Test
    fun unlimitedRecentFilesSurvivesMobileSnapshotRoundTrip() {
        val snapshot = SharedReaderScreenState(recentFilesLimit = 0)
            .toSharedMobileLibrarySnapshot()
        val restored = SharedLibrarySnapshotJson
            .decodeOrEmpty(SharedLibrarySnapshotJson.encode(snapshot))
            .toSharedMobileReaderState()

        assertEquals(0, snapshot.recentFilesLimit)
        assertEquals(0, restored.recentFilesLimit)
    }

    @Test
    fun invalidMobileRecentLimitIsNormalizedBeforePersistence() {
        val snapshot = SharedReaderScreenState(recentFilesLimit = 24)
            .toSharedMobileLibrarySnapshot()

        assertEquals(20, snapshot.recentFilesLimit)
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

    @Test
    fun olderSnapshotDefaultsFolderSyncToDisabled() {
        val restored = SharedLibrarySnapshotJson
            .decodeOrEmpty("""{"schemaVersion":26}""")
            .toSharedMobileReaderState()

        assertFalse(restored.isFolderSyncEnabled)
        assertEquals(SortOrder.RECENT, restored.sortOrder)
        assertEquals(LibraryFilters(), restored.libraryFilters)
        assertEquals(0, restored.mainScreenStartPage)
        assertEquals(0, restored.libraryScreenStartPage)
    }

    @Test
    fun landingPagesAreClampedBeforePersistence() {
        val snapshot = SharedReaderScreenState(
            mainScreenStartPage = 8,
            libraryScreenStartPage = -4,
        ).toSharedMobileLibrarySnapshot()

        assertEquals(1, snapshot.mainScreenStartPage)
        assertEquals(0, snapshot.libraryScreenStartPage)
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
