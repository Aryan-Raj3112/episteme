package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedReducersTest {

    @Test
    fun `pin selection adds mixed selections and removes fully pinned selections`() {
        val initial = AppPinState(homeBookIds = setOf("one"), libraryBookIds = setOf("one", "two"))

        val home = initial.toggleSelection(listOf("one", "two"), isHome = true)
        val library = initial.toggleSelection(listOf("one", "two"), isHome = false)

        assertEquals(setOf("one", "two"), home.homeBookIds)
        assertEquals(emptySet(), library.libraryBookIds)
    }

    @Test
    fun `book selection can be replaced in one reducer action`() {
        val state = SharedReaderScreenState(selectedBookIds = setOf("old"))

        val result = state.reduce(LibraryAction.BookSelectionReplaced(setOf("one", "two")))

        assertEquals(setOf("one", "two"), result.selectedBookIds)
    }

    @Test
    fun `visible selection helper selects visible books and clears when all are selected`() {
        val visibleBooks = listOf(
            BookItem("one", "/books/one.epub", FileType.EPUB, "one.epub", timestamp = 1L),
            BookItem("two", "/books/two.epub", FileType.EPUB, "two.epub", timestamp = 2L)
        )

        val selected = SharedReaderScreenState()
            .replaceBookSelectionWithVisibleBooks(visibleBooks)

        assertEquals(setOf("one", "two"), selected.selectedBookIds)
        assertEquals(
            emptySet(),
            selected.replaceBookSelectionWithVisibleBooks(visibleBooks).selectedBookIds
        )
    }

    @Test
    fun `custom font imports enter shared application state`() {
        val font = CustomFontItem(
            id = "font-1",
            displayName = "Reader Serif",
            fileName = "ReaderSerif.ttf",
            fileExtension = "ttf",
            path = "/fonts/ReaderSerif.ttf",
            timestamp = 10L
        )

        val result = SharedReaderScreenState().reduce(AppAction.CustomFontsChanged(listOf(font)))

        assertEquals(listOf(font), result.customFonts)
    }

    @Test
    fun `disabling or closing all tabs clears ids active tab and materialized tabs together`() {
        val tab = BookItem(
            id = "pdf",
            path = "/books/pdf.pdf",
            type = FileType.PDF,
            displayName = "pdf.pdf",
            timestamp = 1L,
        )
        val state = SharedReaderScreenState(
            openTabIds = listOf(tab.id),
            activeTabBookId = tab.id,
            openTabs = listOf(tab),
        )

        val disabled = state.reduce(AppAction.TabsEnabledChanged(false))
        val closedAll = state.reduce(AppAction.AllTabsClosed)

        assertEquals(false, disabled.isTabsEnabled)
        assertEquals(emptyList(), disabled.openTabIds)
        assertEquals(null, disabled.activeTabBookId)
        assertEquals(emptyList(), disabled.openTabs)
        assertEquals(emptyList(), closedAll.openTabIds)
        assertEquals(null, closedAll.activeTabBookId)
        assertEquals(emptyList(), closedAll.openTabs)
    }

    @Test
    fun `tab reconciliation removes unavailable duplicates and invalid active identity`() {
        val state = AppTabState(
            isEnabled = true,
            openBookIds = listOf("one", "missing", "one", "two"),
            activeBookId = "missing",
        )

        val reconciled = state.reconcileAvailableBooks(setOf("one", "two"))

        assertEquals(listOf("one", "two"), reconciled.openBookIds)
        assertEquals(null, reconciled.activeBookId)
        assertTrue(reconciled.isEnabled)
    }

    @Test
    fun `app reader session owns portable open ready failure and close transitions`() {
        val opening = AppReaderSessionState().reduce(
            AppReaderSessionAction.OpenStarted("book", FileType.EPUB),
        )
        val unrelatedReady = opening.reduce(AppReaderSessionAction.OpenReady("other"))
        val ready = opening.reduce(AppReaderSessionAction.OpenReady("book"))
        val retainedFailure = opening.reduce(AppReaderSessionAction.OpenFailed("book", "retry"))
        val failed = opening.reduce(AppReaderSessionAction.OpenFailed("book", "broken", closeReader = true))
        val seamlessFailure = ready.reduce(AppReaderSessionAction.SeamlessSwitchFailed)
        val closed = failed.reduce(AppReaderSessionAction.Closed)

        assertEquals(AppReaderSessionPhase.OPENING, opening.phase)
        assertTrue(opening.isLoading)
        assertFalse(opening.canRestorePersistedReader)
        assertEquals(opening, unrelatedReady)
        assertEquals(AppReaderSessionPhase.READY, ready.phase)
        assertEquals("book", retainedFailure.bookId)
        assertEquals("retry", retainedFailure.errorMessage)
        assertEquals(AppReaderSessionPhase.FAILED, failed.phase)
        assertEquals(null, failed.bookId)
        assertEquals("broken", failed.errorMessage)
        assertEquals("book", seamlessFailure.bookId)
        assertEquals(null, seamlessFailure.fileType)
        assertEquals(AppReaderSessionPhase.READY, seamlessFailure.phase)
        assertTrue(failed.canRestorePersistedReader)
        assertEquals(AppReaderSessionState(), closed)
        assertTrue(closed.canRestorePersistedReader)
    }

    @Test
    fun `app shelf state owns navigation dialogs and add-book workflow`() {
        val create = AppShelfState().reduce(
            AppShelfAction.CreateDialogShown(setOf(" book ", "", "other")),
        )
        assertTrue(create.showCreateDialog)
        assertEquals(setOf("book", "other"), create.createShelfBookIds)
        assertEquals(AppShelfState(), create.reduce(AppShelfAction.CreateDialogDismissed))

        val adding = AppShelfState()
            .reduce(AppShelfAction.ShelfOpened("shelf"))
            .reduce(AppShelfAction.AddBooksStarted(AddBooksSource.ALL_BOOKS))
            .reduce(AppShelfAction.BookForAddingToggled(" book "))
        assertEquals("shelf", adding.viewingShelfId)
        assertTrue(adding.isAddingBooks)
        assertEquals(AddBooksSource.ALL_BOOKS, adding.addBooksSource)
        assertEquals(setOf("book"), adding.selectedBookIdsForAdding)

        val completed = adding.reduce(AppShelfAction.AddBooksCompleted)
        assertFalse(completed.isAddingBooks)
        assertEquals(emptySet(), completed.selectedBookIdsForAdding)
        assertEquals(AddBooksSource.ALL_BOOKS, completed.addBooksSource)

        val deleted = adding
            .reduce(AppShelfAction.DeleteDialogChanged("shelf"))
            .reduce(AppShelfAction.ShelfDeleted)
        assertEquals(null, deleted.viewingShelfId)
        assertEquals(null, deleted.deleteDialogShelfId)
        assertFalse(deleted.isAddingBooks)
    }
}
