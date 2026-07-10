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
    fun deleteSelectedBooks() {}
    fun createShelfFromSelectedBooks(name: String) {}
    fun openSettings()
    fun openMoreActions()
}
