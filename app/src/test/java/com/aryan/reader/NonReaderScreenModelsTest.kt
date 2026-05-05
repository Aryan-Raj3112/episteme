package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonReaderScreenModelsTest {

    @Test
    fun `home model treats open tabs as non-empty content`() {
        val tab = recentFile("tab")

        val model = ReaderScreenState(
            isTabsEnabled = true,
            openTabs = listOf(tab),
            rawLibraryFiles = listOf(tab)
        ).toHomeScreenModel()

        assertFalse(model.isEmpty)
        assertTrue(model.isLibraryEmpty)
        assertEquals(listOf(tab), model.openTabs)
    }

    @Test
    fun `home model reports empty when there are no recents or open tabs`() {
        val archivedBook = recentFile("archived", isRecent = false)

        val model = ReaderScreenState(
            recentFiles = emptyList(),
            rawLibraryFiles = listOf(archivedBook)
        ).toHomeScreenModel()

        assertTrue(model.isEmpty)
        assertTrue(model.isLibraryEmpty)
    }

    @Test
    fun `home model ignores open tabs for empty state when tabs are disabled`() {
        val tab = recentFile("tab")

        val model = ReaderScreenState(
            isTabsEnabled = false,
            openTabs = listOf(tab),
            recentFiles = emptyList()
        ).toHomeScreenModel()

        assertTrue(model.isEmpty)
        assertEquals(listOf(tab), model.openTabs)
    }

    @Test
    fun `home model exposes contextual selection and device limit state`() {
        val selected = recentFile("selected")
        val deviceState = DeviceLimitReachedState(isLimitReached = true)

        val model = ReaderScreenState(
            recentFiles = listOf(selected),
            contextualActionItems = setOf(selected),
            deviceLimitState = deviceState
        ).toHomeScreenModel()

        assertTrue(model.isContextualModeActive)
        assertEquals(setOf(selected), model.selectedItems)
        assertEquals(deviceState, model.deviceLimitState)
        assertFalse(model.isEmpty)
        assertFalse(model.isLibraryEmpty)
    }

    @Test
    fun `library model exposes contextual and shelf selection state`() {
        val folderBook = recentFile("folder", sourceFolderUri = "content://folder")
        val shelf = Shelf(
            id = "manual",
            name = "Manual",
            type = ShelfType.MANUAL,
            books = listOf(folderBook)
        )

        val model = ReaderScreenState(
            contextualActionItems = setOf(folderBook),
            contextualActionShelfIds = setOf(shelf.id),
            sortOrder = SortOrder.TITLE_ASC,
            shelves = listOf(shelf),
            rawLibraryFiles = listOf(folderBook),
            searchQuery = "folder",
            isSearchActive = true
        ).toLibraryScreenModel()

        assertTrue(model.isContextualModeActive)
        assertTrue(model.isShelfContextualModeActive)
        assertTrue(model.containsFolderItemsInSelection)
        assertEquals(setOf(folderBook), model.selectedItems)
        assertEquals(setOf(shelf.id), model.selectedShelves)
        assertEquals(SortOrder.TITLE_ASC, model.sortOrder)
        assertEquals("folder", model.searchQuery)
        assertTrue(model.isSearchActive)
    }

    @Test
    fun `library model reports inactive contextual states for normal browsing`() {
        val book = recentFile("book")

        val model = ReaderScreenState(
            allRecentFiles = listOf(book),
            rawLibraryFiles = listOf(book),
            sortOrder = SortOrder.RECENT
        ).toLibraryScreenModel()

        assertFalse(model.isContextualModeActive)
        assertFalse(model.isShelfContextualModeActive)
        assertFalse(model.containsFolderItemsInSelection)
        assertTrue(model.selectedItems.isEmpty())
        assertTrue(model.selectedShelves.isEmpty())
        assertEquals(listOf(book), model.rawLibraryFiles)
        assertEquals(SortOrder.RECENT, model.sortOrder)
    }

    @Test
    fun `library model distinguishes folder and non-folder selections`() {
        val localBook = recentFile("local")

        val model = ReaderScreenState(
            contextualActionItems = setOf(localBook),
            rawLibraryFiles = listOf(localBook)
        ).toLibraryScreenModel()

        assertTrue(model.isContextualModeActive)
        assertFalse(model.containsFolderItemsInSelection)
        assertEquals(setOf(localBook), model.selectedItems)
    }

    private fun recentFile(
        id: String,
        isRecent: Boolean = true,
        sourceFolderUri: String? = null
    ) = RecentFileItem(
        bookId = id,
        uriString = "content://$id",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
        isRecent = isRecent,
        sourceFolderUri = sourceFolderUri
    )
}
