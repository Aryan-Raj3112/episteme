package com.aryan.reader.shared.ui

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderTabKind
import com.aryan.reader.shared.SharedReaderScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library's top-bar tab switcher lists both readers, so iOS and Android show the same menu.
 * These cover the shared projection: EPUB first, the active tab per reader, and 1-based labels.
 */
class SharedOpenTabsSwitcherTest {

    @Test
    fun `lists text tabs before fixed layout tabs and flags one active tab per reader`() {
        val state = SharedReaderScreenState(
            openTabs = listOf(
                pdf("pdf_1", lastPageIndex = 11),
                pdf("pdf_2", lastPageIndex = 0),
            ),
            textOpenTabs = listOf(
                epub("epub_1", chapterIndex = 4, progress = 62.4f),
                epub("epub_2", chapterIndex = 0),
            ),
            openTabIds = listOf("pdf_1", "pdf_2"),
            activeTabBookId = "pdf_1",
            textOpenTabIds = listOf("epub_1", "epub_2"),
            activeTextTabBookId = "epub_2",
        )

        val tabs = state.openTabsForSwitcher()

        assertEquals(listOf("epub_1", "epub_2", "pdf_1", "pdf_2"), tabs.map { it.bookId })
        assertEquals(
            listOf(
                ReaderTabKind.TEXT,
                ReaderTabKind.TEXT,
                ReaderTabKind.FIXED_LAYOUT,
                ReaderTabKind.FIXED_LAYOUT,
            ),
            tabs.map { it.kind },
        )
        assertEquals(listOf(false, true, true, false), tabs.map { it.isActive })
        // Stored indices are zero-based; the panel says "Chapter 5", not "Chapter 4".
        assertEquals(5, tabs[0].chapterNumber)
        assertEquals(1, tabs[1].chapterNumber)
        assertEquals(12, tabs[2].pageNumber)
        assertEquals(1, tabs[3].pageNumber)
        assertNull(tabs[0].pageNumber)
        assertNull(tabs[2].chapterNumber)
        assertEquals(62, tabs[0].progressPercent)
        assertNull(tabs[1].progressPercent)
    }

    @Test
    fun `titles follow the library card precedence`() {
        val state = SharedReaderScreenState(
            textOpenTabs = listOf(epub("epub", title = "Dune")),
            openTabs = listOf(pdf("pdf", title = "Manual")),
            openTabIds = listOf("pdf"),
            activeTabBookId = "pdf",
            textOpenTabIds = listOf("epub"),
            activeTextTabBookId = "epub",
        )

        val tabs = state.openTabsForSwitcher()

        assertEquals("Dune", tabs[0].title)
        assertEquals("Manual", tabs[1].title)
        // The setting swaps only PDF titles for their file name, as the cards do.
        assertEquals("pdf.pdf", state.openTabsForSwitcher(usePdfFileNameAsDisplayName = true)[1].title)
        assertEquals("Dune", state.openTabsForSwitcher(usePdfFileNameAsDisplayName = true)[0].title)
    }

    @Test
    fun `skips rows no reader owns and states without tabs`() {
        val state = SharedReaderScreenState(
            openTabs = listOf(
                pdf("pdf"),
                pdf("audio", type = FileType.AUDIOBOOK),
            ),
            openTabIds = listOf("pdf", "audio"),
            activeTabBookId = "pdf",
        )

        assertEquals(listOf("pdf"), state.openTabsForSwitcher().map { it.bookId })
        assertTrue(SharedReaderScreenState().openTabsForSwitcher().isEmpty())
    }

    private fun epub(
        id: String,
        title: String? = null,
        chapterIndex: Int? = null,
        progress: Float? = null,
    ) = BookItem(
        id = id,
        path = "/books/$id.epub",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
        title = title,
        progressPercentage = progress,
        readerPosition = chapterIndex?.let { ReaderLocator(chapterIndex = it) },
    )

    private fun pdf(
        id: String,
        type: FileType = FileType.PDF,
        title: String? = null,
        lastPageIndex: Int? = null,
        progress: Float? = null,
    ) = BookItem(
        id = id,
        path = "/books/$id.pdf",
        type = type,
        displayName = "$id.pdf",
        timestamp = 1L,
        title = title,
        progressPercentage = progress,
        lastPageIndex = lastPageIndex,
    )
}
