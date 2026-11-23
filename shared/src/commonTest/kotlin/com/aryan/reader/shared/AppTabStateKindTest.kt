package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two tab sets are the contract that keeps a tab tap inside one reader engine, so the isolation
 * between them is what these cover: an action on one set must never move the other.
 */
class AppTabStateKindTest {

    private fun pdf(id: String) = BookItem(
        id = id,
        path = "/books/$id.pdf",
        type = FileType.PDF,
        displayName = "$id.pdf",
        timestamp = 1L,
    )

    private fun epub(id: String) = BookItem(
        id = id,
        path = "/books/$id.epub",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 2L,
    )

    @Test
    fun `opening a tab only touches that kind's set`() {
        val state = AppTabState()
            .reduce(AppAction.BookTabOpened(ReaderTabKind.FIXED_LAYOUT, "pdf-a"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-a"))

        assertEquals(listOf("pdf-a"), state.fixedLayout.openBookIds)
        assertEquals("pdf-a", state.fixedLayout.activeBookId)
        assertEquals(listOf("epub-a"), state.text.openBookIds)
        assertEquals("epub-a", state.text.activeBookId)
    }

    @Test
    fun `closing a tab only touches that kind's set`() {
        val state = AppTabState()
            .reduce(AppAction.BookTabOpened(ReaderTabKind.FIXED_LAYOUT, "pdf-a"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.FIXED_LAYOUT, "pdf-b"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-a"))
            .reduce(AppAction.BookTabClosed(ReaderTabKind.TEXT, "epub-a"))

        assertEquals(listOf("pdf-a", "pdf-b"), state.fixedLayout.openBookIds)
        assertEquals("pdf-b", state.fixedLayout.activeBookId)
        assertTrue(state.text.openBookIds.isEmpty())
        assertNull(state.text.activeBookId)
    }

    @Test
    fun `closing the active tab falls back to the previous tab of the same kind`() {
        val state = AppTabState()
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-a"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-b"))
            .reduce(AppAction.BookTabClosed(ReaderTabKind.TEXT, "epub-b"))

        assertEquals("epub-a", state.text.activeBookId)
        assertEquals(listOf("epub-a"), state.text.openBookIds)
    }

    @Test
    fun `closing all tabs of a kind leaves the other kind open`() {
        val state = AppTabState()
            .reduce(AppAction.BookTabOpened(ReaderTabKind.FIXED_LAYOUT, "pdf-a"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-a"))
            .reduce(AppAction.BookTabsClosed(ReaderTabKind.TEXT))

        assertEquals(listOf("pdf-a"), state.fixedLayout.openBookIds)
        assertTrue(state.text.openBookIds.isEmpty())
    }

    @Test
    fun `disabling tabs parks both sets on their active tab and re-enabling keeps them`() {
        val state = AppTabState()
            .reduce(AppAction.BookTabOpened(ReaderTabKind.FIXED_LAYOUT, "pdf-a"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.FIXED_LAYOUT, "pdf-b"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-a"))
            .reduce(AppAction.BookTabOpened(ReaderTabKind.TEXT, "epub-b"))

        val disabled = state.reduce(AppAction.TabsEnabledChanged(false))

        assertEquals(false, disabled.isEnabled)
        assertEquals(listOf("pdf-b"), disabled.fixedLayout.openBookIds)
        assertEquals(listOf("epub-b"), disabled.text.openBookIds)

        val reEnabled = disabled.reduce(AppAction.TabsEnabledChanged(true))
        assertEquals(listOf("pdf-b"), reEnabled.fixedLayout.openBookIds)
        assertEquals("pdf-b", reEnabled.fixedLayout.activeBookId)
        assertEquals(listOf("epub-b"), reEnabled.text.openBookIds)
        assertEquals("epub-b", reEnabled.text.activeBookId)
        assertTrue(reEnabled.isEnabled)
    }

    @Test
    fun `disabling a set without an active tab forgets it`() {
        val disabled = AppTabState()
            .reduce(AppAction.TabsEnabledChanged(false))

        assertTrue(disabled.fixedLayout.openBookIds.isEmpty())
        assertTrue(disabled.text.openBookIds.isEmpty())
    }

    @Test
    fun `reconciliation prunes and caps each set independently`() {
        val books = (1..(MAX_OPEN_TABS_PER_KIND + 3)).map { pdf("pdf-$it") } +
            (1..3).map { epub("epub-$it") }
        val state = AppTabState(
            fixedLayout = AppTabGroup(
                openBookIds = listOf("gone") + books.filter { it.type == FileType.PDF }.map { it.id },
                activeBookId = "gone",
            ),
            text = AppTabGroup(
                openBookIds = listOf("epub-1", "gone", "epub-2"),
                activeBookId = "epub-2",
            ),
        )

        val reconciled = state.reconcileAvailableBooks(books.mapTo(mutableSetOf()) { it.id })

        assertEquals(MAX_OPEN_TABS_PER_KIND, reconciled.fixedLayout.openBookIds.size)
        assertNull(reconciled.fixedLayout.activeBookId)
        assertEquals(listOf("epub-1", "epub-2"), reconciled.text.openBookIds)
        assertEquals("epub-2", reconciled.text.activeBookId)
    }

    @Test
    fun `mobile mutations route a book into its own kind and keep the reader book lists in step`() {
        val pdfBook = pdf("pdf-a")
        val epubBook = epub("epub-a")
        val library = listOf(pdfBook, epubBook)

        val openedText = SharedReaderScreenState(rawLibraryBooks = library).withMobileBookOpened(epubBook)
        val openedPdf = openedText.withMobileBookOpened(pdfBook)

        assertEquals(listOf("pdf-a"), openedPdf.openTabIds)
        assertEquals(listOf("epub-a"), openedPdf.textOpenTabIds)
        assertEquals(listOf("pdf-a"), openedPdf.openTabs.map { it.id })
        assertEquals(listOf("epub-a"), openedPdf.textOpenTabs.map { it.id })
        assertEquals("epub-a", openedPdf.activeTextTabBookId)

        val closedText = openedPdf.withMobileBookClosed(ReaderTabKind.TEXT, "epub-a")

        assertEquals(listOf("pdf-a"), closedText.openTabIds)
        assertTrue(closedText.textOpenTabIds.isEmpty())
        // Closing the text tab the reader was in moves it, it does not clear the PDF selection.
        assertEquals("pdf-a", closedText.selectedBookId)
    }

    @Test
    fun `removing books from a folder prunes them from both tab sets`() {
        val folder = "content://books"
        val pdfBook = pdf("pdf-a").copy(sourceFolder = folder)
        val epubBook = epub("epub-a").copy(sourceFolder = folder)
        val state = SharedReaderScreenState(rawLibraryBooks = listOf(pdfBook, epubBook))
            .withMobileBookOpened(pdfBook)
            .withMobileBookOpened(epubBook)

        val pruned = state.withMobileFolderFileTypes(
            folder = SyncedFolder(uriString = folder, name = "Books", lastScanTime = 0L),
            allowedFileTypes = emptySet(),
        ).state

        assertTrue(pruned.openTabIds.isEmpty())
        assertTrue(pruned.textOpenTabIds.isEmpty())
        assertNull(pruned.activeTabBookId)
        assertNull(pruned.activeTextTabBookId)
    }

    @Test
    fun `mobile tab cap counts each set on its own`() {
        val full = (1..MAX_OPEN_TABS_PER_KIND).map { "book-$it" }

        assertTrue(canOpenMobileTab(full, "book-3"))
        assertEquals(false, canOpenMobileTab(full, "new-book"))
        assertTrue(canOpenMobileTab(emptyList(), "new-book"))
    }
}
