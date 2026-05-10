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
