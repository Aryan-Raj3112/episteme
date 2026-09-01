package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem

/**
 * Platform adapter required by the shared Home UI.
 *
 * The screen owns presentation; Android and iOS retain ownership of native
 * file pickers, navigation, persistence, and system integrations.
 */
interface SharedMobileHomeActions {
    fun importBooks()
    fun openBook(book: BookItem)
    fun longPressBook(book: BookItem)
    fun openDrawer()
    fun openSearch()
    fun navigateToFolderSync()
    fun refresh()
    fun clearSelection()
    fun selectAll()
    fun closeTab(book: BookItem)
    fun closeAllTabs()
    fun togglePinned(book: BookItem)
    fun toggleSelectedPins() {}
    fun removeSelectedBooksFromRecents() {}
    fun addSelectedBooksToShelves(shelfIds: Set<String>) {}
    fun createShelfFromSelectedBooks(name: String) {}
    fun showBookInfo(book: BookItem) {}
    fun updateBook(book: BookItem) {}
    fun saveBook(book: BookItem) {}
    fun shareBook(book: BookItem) {}
    fun exportAnnotations(book: BookItem) {}
    fun importCover() {}
    fun createAndAssignTag(name: String) {}
    fun toggleTagForSelectedBooks(tagId: String, assign: Boolean) {}
    fun deleteTag(tagId: String) {}
    fun openSettings()
    fun openAppTheme()
    fun openRecentLimit()
    fun openAbout()
    fun openLanguage()
    fun toggleTabs()
    fun openExternalFileBehavior()
    fun toggleStrictFileFilter()
    fun togglePdfFileNameDisplay()
    fun toggleReaderAi() {}
    fun clearReflowCache() {}
    fun exportLogs() {}
}
