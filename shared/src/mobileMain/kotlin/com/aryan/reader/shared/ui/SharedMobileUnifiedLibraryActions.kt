package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem

/**
 * Platform effects for Library Beta contextual book selection.
 *
 * Shared UI owns the selection dialogs and invokes these callbacks; hosts retain
 * ownership of persistence, native sharing/export, and deleting managed files.
 */
interface SharedMobileUnifiedLibraryActions {
    fun clearSelection()
    fun selectAll(visibleBookIds: Set<String>)
    fun toggleSelectedPins()
    fun addSelectedBooksToShelves(shelfIds: Set<String>)
    fun createShelfFromSelectedBooks(name: String)
    fun createAndAssignTag(name: String)
    fun toggleTagForSelectedBooks(tagId: String, assign: Boolean)
    fun deleteTag(tagId: String)
    fun saveBook(book: BookItem)
    fun shareBook(book: BookItem)
    fun exportAnnotations(book: BookItem)
    fun deleteBooks(bookIds: Set<String>)
}
