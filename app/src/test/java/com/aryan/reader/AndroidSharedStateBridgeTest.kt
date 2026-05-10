package com.aryan.reader

import com.aryan.reader.data.BookTagCrossRef
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.TagEntity
import com.aryan.reader.shared.AppAction as SharedAppAction
import com.aryan.reader.shared.AppThemeMode as SharedAppThemeMode
import com.aryan.reader.shared.LibraryAction as SharedLibraryAction
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

        val selected = AndroidSharedStateBridge.reduceLibraryAction(
            current = ReaderScreenState(),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(book)),
            action = SharedLibraryAction.BookSelectionToggled(book.bookId)
        )
        val filtered = AndroidSharedStateBridge.reduceLibraryAction(
            current = selected,
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(book)),
            action = SharedLibraryAction.FiltersChanged(filters.toSharedLibraryFilters())
        )

        assertEquals(setOf(book), selected.contextualActionItems)
        assertEquals(filters, filtered.libraryFilters)
    }

    @Test
    fun `reduceLibraryAction drops selection ids that are not in projected Android books`() {
        val result = AndroidSharedStateBridge.reduceLibraryAction(
            current = ReaderScreenState(),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(recentFile("book"))),
            action = SharedLibraryAction.BookSelectionToggled("missing")
        )

        assertTrue(result.contextualActionItems.isEmpty())
    }

    @Test
    fun `reduceAppAction applies shared app state back to Android fields`() {
        val result = AndroidSharedStateBridge.reduceAppAction(
            current = ReaderScreenState(appThemeMode = AppThemeMode.LIGHT),
            projectedState = ReaderScreenState(),
            action = SharedAppAction.AppThemeChanged(SharedAppThemeMode.DARK)
        )

        assertEquals(AppThemeMode.DARK, result.appThemeMode)
    }

    @Test
    fun `setTabsEnabled disables shared tabs but preserves Android active reader session`() {
        val result = AndroidSharedStateBridge.setTabsEnabled(
            current = ReaderScreenState(
                isTabsEnabled = true,
                openTabIds = listOf("one", "two"),
                activeTabBookId = "two"
            ),
            projectedState = ReaderScreenState(),
            enabled = false
        )

        assertEquals(false, result.isTabsEnabled)
        assertEquals(listOf("two"), result.openTabIds)
        assertEquals("two", result.activeTabBookId)
    }

    @Test
    fun `openBookTab delegates tab ordering and activation to shared reducer`() {
        val result = AndroidSharedStateBridge.openBookTab(
            current = ReaderScreenState(
                isTabsEnabled = false,
                openTabIds = listOf("old"),
                activeTabBookId = "old"
            ),
            projectedState = ReaderScreenState(),
            bookId = "new"
        )

        assertEquals(true, result.isTabsEnabled)
        assertEquals(listOf("old", "new"), result.openTabIds)
        assertEquals("new", result.activeTabBookId)
    }

    @Test
    fun `closeBookTab selects the previous tab when the active tab closes`() {
        val result = AndroidSharedStateBridge.closeBookTab(
            current = ReaderScreenState(
                isTabsEnabled = true,
                openTabIds = listOf("one", "two", "three"),
                activeTabBookId = "three"
            ),
            projectedState = ReaderScreenState(),
            bookId = "three"
        )

        assertEquals(true, result.isTabsEnabled)
        assertEquals(listOf("one", "two"), result.openTabIds)
        assertEquals("two", result.activeTabBookId)
    }

    @Test
    fun `closeAllTabs clears Android tab ids through shared reducer`() {
        val result = AndroidSharedStateBridge.closeAllTabs(
            current = ReaderScreenState(
                isTabsEnabled = true,
                openTabIds = listOf("one", "two"),
                activeTabBookId = "two"
            ),
            projectedState = ReaderScreenState()
        )

        assertEquals(true, result.isTabsEnabled)
        assertTrue(result.openTabIds.isEmpty())
        assertEquals(null, result.activeTabBookId)
    }

    @Test
    fun `togglePinsForSelectedBooks pins mixed home selection and clears selection`() {
        val pinned = recentFile("pinned")
        val unpinned = recentFile("unpinned")

        val result = AndroidSharedStateBridge.togglePinsForSelectedBooks(
            current = ReaderScreenState(
                rawLibraryFiles = listOf(pinned, unpinned),
                contextualActionItems = setOf(pinned, unpinned),
                pinnedHomeBookIds = setOf(pinned.bookId)
            ),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(pinned, unpinned)),
            isHome = true
        )

        assertEquals(setOf("pinned", "unpinned"), result.pinnedHomeBookIds)
        assertTrue(result.contextualActionItems.isEmpty())
    }

    @Test
    fun `togglePinsForSelectedBooks unpins when all selected library books are pinned`() {
        val first = recentFile("first")
        val second = recentFile("second")

        val result = AndroidSharedStateBridge.togglePinsForSelectedBooks(
            current = ReaderScreenState(
                rawLibraryFiles = listOf(first, second),
                contextualActionItems = setOf(first, second),
                pinnedLibraryBookIds = setOf(first.bookId, second.bookId)
            ),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(first, second)),
            isHome = false
        )

        assertTrue(result.pinnedLibraryBookIds.isEmpty())
        assertTrue(result.contextualActionItems.isEmpty())
    }

    @Test
    fun `replaceBookSelectionWithVisibleBooks selects visible books through shared reducer`() {
        val visible = recentFile("visible")
        val hidden = recentFile("hidden")

        val result = AndroidSharedStateBridge.replaceBookSelectionWithVisibleBooks(
            current = ReaderScreenState(),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(visible, hidden)),
            visibleBooks = listOf(visible)
        )

        assertEquals(setOf(visible), result.contextualActionItems)
    }

    @Test
    fun `replaceBookSelectionWithVisibleBooks clears when visible books are already selected`() {
        val visible = recentFile("visible")

        val result = AndroidSharedStateBridge.replaceBookSelectionWithVisibleBooks(
            current = ReaderScreenState(contextualActionItems = setOf(visible)),
            projectedState = ReaderScreenState(rawLibraryFiles = listOf(visible)),
            visibleBooks = listOf(visible)
        )

        assertTrue(result.contextualActionItems.isEmpty())
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
