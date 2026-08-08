package com.aryan.reader

import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AppAction as SharedAppAction
import com.aryan.reader.shared.AppFontPreference as SharedAppFontPreference
import com.aryan.reader.shared.AppThemeMode as SharedAppThemeMode
import com.aryan.reader.shared.LibraryAction as SharedLibraryAction
import com.aryan.reader.shared.AppReaderSessionPhase
import com.aryan.reader.shared.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSharedStateBridgeTest {

    @Test
    fun `prepareLibraryProjection builds shared input and Android lookup context`() {
        val tag = tag("tag", "Favorite")
        val book = recentFile("book", sourceFolderUri = "content://folder")
        val reflowCopy = recentFile("book_reflow", sourceFolderUri = "content://folder")

        val context = AndroidSharedStateBridge.prepareLibraryProjection(
            input = LibraryProjectionInput(
                state = ReaderScreenState(),
                recentFilesFromDb = listOf(book, reflowCopy),
                dbShelves = emptyList(),
                shelfRefs = emptyList(),
                dbTags = listOf(tag),
                tagRefs = listOf(BookTagCrossRef(bookId = book.bookId, tagId = tag.id))
            ),
            folderPathResolver = EmptyFolderPathResolver
        )

        assertEquals(listOf("book"), context.androidBooksById.keys.toList())
        assertEquals(listOf("book"), context.sharedInput.booksFromStore.map { it.id })
        assertEquals(listOf("tag"), context.sharedInput.booksFromStore.single().tags.map { it.id })
        assertEquals(listOf(AndroidSharedFolderProjectionKey("content://folder", "Local Folder")), context.folderKeys)
    }

    @Test
    fun `reduceLibraryAction applies shared library state back to Android fields`() {
        val book = recentFile("book")
        val filters = LibraryFilters(readStatus = ReadStatusFilter.COMPLETED)

        val selected = reduceLibraryAction(
            current = ReaderScreenState(),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(book)),
            action = SharedLibraryAction.BookSelectionToggled(book.bookId)
        )
        val filtered = reduceLibraryAction(
            current = selected,
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(book)),
            action = SharedLibraryAction.FiltersChanged(filters.toSharedLibraryFilters())
        )

        assertEquals(setOf(book), selected.contextualActionItems)
        assertEquals(filters, filtered.libraryFilters)
    }

    @Test
    fun `reduceLibraryAction drops selection ids that are not in projected Android books`() {
        val result = reduceLibraryAction(
            current = ReaderScreenState(),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(recentFile("book"))),
            action = SharedLibraryAction.BookSelectionToggled("missing")
        )

        assertTrue(result.contextualActionItems.isEmpty())
    }

    @Test
    fun `shared appearance reducer applies app theme without bridge projection`() {
        val result = AppAppearanceState(themeMode = AppThemeMode.LIGHT)
            .reduce(SharedAppAction.AppThemeChanged(SharedAppThemeMode.DARK))

        assertEquals(AppThemeMode.DARK, result.themeMode)
    }

    @Test
    fun `shared appearance reducer applies app font preference without bridge projection`() {
        val preference = SharedAppFontPreference.custom("font")

        val result = AppAppearanceState()
            .reduce(SharedAppAction.AppFontPreferenceChanged(preference))

        assertEquals(preference, result.fontPreference)
    }

    @Test
    fun `setTabsEnabled disables shared tabs but preserves Android active reader session`() {
        val result = setTabsEnabled(
            current = ReaderScreenState(
                tabState = AppTabState(true, listOf("one", "two"), "two")
            ),
            enabled = false
        )

        assertEquals(false, result.isTabsEnabled)
        assertEquals(listOf("two"), result.openTabIds)
        assertEquals("two", result.activeTabBookId)
    }

    @Test
    fun `openBookTab delegates tab ordering and activation to shared reducer`() {
        val result = openBookTab(
            current = ReaderScreenState(
                tabState = AppTabState(false, listOf("old"), "old")
            ),
            availableBookIds = setOf("old", "new"),
            bookId = "new"
        )

        assertEquals(true, result.isTabsEnabled)
        assertEquals(listOf("old", "new"), result.openTabIds)
        assertEquals("new", result.activeTabBookId)
    }

    @Test
    fun `openBookTab ignores stale persisted tab ids missing from projection`() {
        val staleIds = (1..20).map { "missing_$it" }

        val result = openBookTab(
            current = ReaderScreenState(
                tabState = AppTabState(true, staleIds, staleIds.last())
            ),
            availableBookIds = setOf("new"),
            bookId = "new"
        )

        assertEquals(true, result.isTabsEnabled)
        assertEquals(listOf("new"), result.openTabIds)
        assertEquals("new", result.activeTabBookId)
    }

    @Test
    fun `closeBookTab selects the previous tab when the active tab closes`() {
        val result = closeBookTab(
            current = ReaderScreenState(
                tabState = AppTabState(true, listOf("one", "two", "three"), "three")
            ),
            availableBookIds = setOf("one", "two", "three"),
            bookId = "three"
        )

        assertEquals(true, result.isTabsEnabled)
        assertEquals(listOf("one", "two"), result.openTabIds)
        assertEquals("two", result.activeTabBookId)
    }

    @Test
    fun `closeAllTabs clears Android tab ids through shared reducer`() {
        val result = closeAllTabs(
            current = ReaderScreenState(
                tabState = AppTabState(true, listOf("one", "two"), "two")
            )
        )

        assertEquals(true, result.isTabsEnabled)
        assertTrue(result.openTabIds.isEmpty())
        assertEquals(null, result.activeTabBookId)
    }

    @Test
    fun `togglePinsForSelectedBooks pins mixed home selection and clears selection`() {
        val pinned = recentFile("pinned")
        val unpinned = recentFile("unpinned")

        val result = togglePinsForSelectedBooks(
            current = ReaderScreenState(
                rawLibraryFiles = listOf(pinned, unpinned),
                contextualActionItems = setOf(pinned, unpinned),
                pinState = AppPinState(homeBookIds = setOf(pinned.bookId)),
                libraryState = LibraryState(selectedBookIds = setOf(pinned.bookId, unpinned.bookId)),
            ),
            isHome = true
        )

        assertEquals(setOf("pinned", "unpinned"), result.pinnedHomeBookIds)
        assertTrue(result.contextualActionItems.isEmpty())
        assertTrue(result.libraryState.selectedBookIds.isEmpty())
    }

    @Test
    fun `togglePinsForSelectedBooks unpins when all selected library books are pinned`() {
        val first = recentFile("first")
        val second = recentFile("second")

        val result = togglePinsForSelectedBooks(
            current = ReaderScreenState(
                rawLibraryFiles = listOf(first, second),
                contextualActionItems = setOf(first, second),
                pinState = AppPinState(libraryBookIds = setOf(first.bookId, second.bookId)),
                libraryState = LibraryState(selectedBookIds = setOf(first.bookId, second.bookId)),
            ),
            isHome = false
        )

        assertTrue(result.pinnedLibraryBookIds.isEmpty())
        assertTrue(result.contextualActionItems.isEmpty())
        assertTrue(result.libraryState.selectedBookIds.isEmpty())
    }

    @Test
    fun `replaceBookSelectionWithVisibleBooks selects visible books through shared reducer`() {
        val visible = recentFile("visible")
        val hidden = recentFile("hidden")

        val result = replaceBookSelectionWithVisibleBooks(
            current = ReaderScreenState(),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(visible, hidden)),
            visibleBooks = listOf(visible)
        )

        assertEquals(setOf(visible), result.contextualActionItems)
    }

    @Test
    fun `replaceBookSelectionWithVisibleBooks clears when visible books are already selected`() {
        val visible = recentFile("visible")

        val result = replaceBookSelectionWithVisibleBooks(
            current = ReaderScreenState(contextualActionItems = setOf(visible)),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(visible)),
            visibleBooks = listOf(visible)
        )

        assertTrue(result.contextualActionItems.isEmpty())
    }

    @Test
    fun `reader session reducer preserves Android fields outside portable lifecycle on close`() {
        val current = ReaderScreenState(
            readerSession = AppReaderSessionState(
                bookId = "book",
                fileType = FileType.EPUB,
                phase = AppReaderSessionPhase.OPENING,
            ),
            isLoading = true,
            isTemporaryExternalOpen = true,
            initialCfi = "epubcfi(/6/2)",
        )

        val projected = readerSessionState(current)
        val closed = closeReaderSession(current)

        assertEquals(AppReaderSessionPhase.OPENING, projected.phase)
        assertEquals(null, closed.selectedBookId)
        assertEquals(null, closed.selectedFileType)
        assertEquals(false, closed.isLoading)
        assertEquals(null, closed.errorMessage)
        assertTrue(closed.isTemporaryExternalOpen)
        assertEquals("epubcfi(/6/2)", closed.initialCfi)
    }

    @Test
    fun `reader session reducer ignores stale completion and closes identity on matching failure`() {
        val platformState = ReaderScreenState(initialCfi = "keep")
        val opening = startReaderSession(platformState, "book", FileType.EPUB)
        val staleReady = markReaderSessionReady(opening, "other")
        val ready = markReaderSessionReady(opening, "book")
        val failed = markReaderSessionFailed(opening, "book", "broken", closeReader = true)

        assertTrue(opening.isLoading)
        assertEquals(opening, staleReady)
        assertEquals("book", ready.selectedBookId)
        assertEquals(false, ready.isLoading)
        assertEquals(null, failed.selectedBookId)
        assertEquals(null, failed.selectedFileType)
        assertEquals("broken", failed.errorMessage)
        assertEquals("keep", failed.initialCfi)
    }

    private fun recentFile(
        id: String,
        sourceFolderUri: String? = null
    ) = RecentFileItem(
        bookId = id,
        uriString = "content://$id",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
        sourceFolderUri = sourceFolderUri
    )

    private fun tag(id: String, name: String) = TagEntity(
        id = id,
        name = name,
        createdAt = 1L
    )
}
