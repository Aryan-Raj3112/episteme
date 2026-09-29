package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            hideReaderAi = true,
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
        assertTrue(restored.hideReaderAi)
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

    @Test
    fun audiobooksSurviveMobileSnapshotRoundTrip() {
        val audiobook = SharedAudiobook(
            bookId = "ab-1",
            filePath = "/tmp/audiobooks/left-hand.epub.m4b",
            format = "m4b",
            title = "The Left Hand of Darkness",
            author = "Ursula K. Le Guin",
            album = "Hainish Cycle",
            durationMs = 2_500_000L,
            positionMs = 60_000L,
            playbackSpeed = 1.5f,
            coverPath = null,
            addedAt = 42L,
        )
        val state = SharedReaderScreenState(
            audiobooks = listOf(audiobook),
        )

        val snapshot = state.toSharedMobileLibrarySnapshot()
        val restored = SharedLibrarySnapshotJson
            .decodeOrEmpty(SharedLibrarySnapshotJson.encode(snapshot))
            .toSharedMobileReaderState()

        assertEquals(listOf(audiobook), restored.audiobooks)
    }

    @Test
    fun olderSnapshotDefaultsAudiobooksToEmpty() {
        val restored = SharedLibrarySnapshotJson
            .decodeOrEmpty("""{"schemaVersion":28}""")
            .toSharedMobileReaderState()

        assertTrue(restored.audiobooks.isEmpty())
    }

    @Test
    fun audiobookImportDedupesByBookIdAndPath() {
        val book1 = SharedAudiobook(
            bookId = "ab-1", filePath = "/a.m4b", format = "m4b", title = "A", addedAt = 1L,
        )
        val book1Again = SharedAudiobook(
            bookId = "ab-1", filePath = "/a.m4b", format = "m4b", title = "A", addedAt = 2L,
        )
        val book2 = SharedAudiobook(
            bookId = "ab-2", filePath = "/b.m4b", format = "m4b", title = "B", addedAt = 3L,
        )
        val state = SharedReaderScreenState()

        val imported = state
            .withAudiobookImported(book1)
            .withAudiobookImported(book1Again)
            .withAudiobookImported(book2)

        assertEquals(listOf("ab-2", "ab-1"), imported.audiobooks.map { it.bookId })
        assertTrue(imported.audiobooks.single { it.bookId == "ab-1" }.addedAt == 1L)
    }

    @Test
    fun audiobookPositionUpdateKeepsDurationAndSetsLastListened() {
        val book = SharedAudiobook(
            bookId = "ab-1", filePath = "/a.m4b", format = "m4b", title = "A",
            durationMs = 100L, addedAt = 1L,
        )
        val state = SharedReaderScreenState(audiobooks = listOf(book))

        val updated = state.withAudiobookPosition("ab-1", 50L, 100L, 1.5f, 77L)

        assertEquals(50L, updated.audiobooks.single().positionMs)
        assertEquals(100L, updated.audiobooks.single().durationMs)
        assertEquals(1.5f, updated.audiobooks.single().playbackSpeed)
        assertEquals(77L, updated.audiobooks.single().lastListenedAt)
    }

    @Test
    fun audiobookPositionUnknownBookIsIgnored() {
        val book = SharedAudiobook(bookId = "ab-1", filePath = "/a.m4b", format = "m4b", title = "A", addedAt = 1L)
        val state = SharedReaderScreenState(audiobooks = listOf(book))

        assertEquals(state, state.withAudiobookPosition("missing", 50L, 100L, 1f, 1L))
    }

    @Test
    fun audiobookRemovalDropsOnlyThatBook() {
        val a = SharedAudiobook(bookId = "ab-1", filePath = "/a.m4b", format = "m4b", title = "A", addedAt = 1L)
        val b = SharedAudiobook(bookId = "ab-2", filePath = "/b.m4b", format = "m4b", title = "B", addedAt = 2L)
        val state = SharedReaderScreenState(audiobooks = listOf(a, b))

        assertEquals(listOf(b), state.withAudiobookRemoved("ab-1").audiobooks)
    }

    @Test
    fun snapshotRebuildKeepsTheSignedInSession() {
        // Regression: a completed sync rebuilt the whole state from the cloud
        // snapshot, which does not carry the account, so currentUser came back
        // null and the app rendered as signed out while Firebase was still
        // authenticated. Pro, wallet, and the sync toggle were reset too.
        val user = UserData(
            uid = "uid-1",
            displayName = "Aryan",
            photoUrl = null,
            email = null,
        )
        val signedIn = SharedReaderScreenState(
            currentUser = user,
            isProUser = true,
            credits = 42,
            walletMicros = 7_000L,
            walletMigrated = true,
            isSyncEnabled = true,
            audiobooks = listOf(
                SharedAudiobook(bookId = "ab-1", filePath = "/a.m4b", format = "m4b", title = "A", addedAt = 1L)
            ),
        )

        // What a snapshot merge produces: a fresh projection, which defaults
        // every account field.
        val projected = SharedLibrarySnapshot().toSharedMobileReaderState()
        assertNull(projected.currentUser)
        assertFalse(projected.isProUser)
        assertFalse(projected.isSyncEnabled)

        val merged = projected.preservingSessionFrom(signedIn)
        assertEquals(user, merged.currentUser)
        assertTrue(merged.isProUser)
        assertEquals(42, merged.credits)
        assertEquals(7_000L, merged.walletMicros)
        assertTrue(merged.walletMigrated)
        assertTrue(merged.isSyncEnabled)
    }

    @Test
    fun snapshotRebuildKeepsTheOpenBookSoSyncDoesNotEjectTheReader() {
        val reading = SharedReaderScreenState(
            selectedBookId = "b7",
            selectedUriString = "/b7.epub",
            selectedFileType = FileType.EPUB,
            renderMode = RenderMode.PAGINATED,
            viewingShelfId = "shelf-1",
        )

        val merged = SharedLibrarySnapshot().toSharedMobileReaderState().preservingSessionFrom(reading)

        assertEquals("b7", merged.selectedBookId)
        assertEquals("/b7.epub", merged.selectedUriString)
        assertEquals(FileType.EPUB, merged.selectedFileType)
        assertEquals(RenderMode.PAGINATED, merged.renderMode)
        assertEquals("shelf-1", merged.viewingShelfId)
    }

    @Test
    fun snapshotRebuildStillAppliesLibraryContent() {
        // The fix must not stop the snapshot from doing its actual job.
        val snapshot = SharedLibrarySnapshot(
            books = listOf(book("remote", 5L)),
            sortOrder = SortOrder.TITLE_ASC,
            hideReaderAi = true,
        )
        val previous = SharedReaderScreenState(
            currentUser = UserData(uid = "uid-1", displayName = "Aryan", photoUrl = null, email = null),
            isSyncEnabled = true,
        )

        val merged = snapshot.toSharedMobileReaderState().preservingSessionFrom(previous)

        assertEquals("remote", merged.libraryBooks.single().id)
        assertEquals(SortOrder.TITLE_ASC, merged.sortOrder)
        assertTrue(merged.hideReaderAi)
        // and the session still survives
        assertEquals("uid-1", merged.currentUser?.uid)
        assertTrue(merged.isSyncEnabled)
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
