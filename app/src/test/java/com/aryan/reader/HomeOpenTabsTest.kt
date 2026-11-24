package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.AppTabGroup
import com.aryan.reader.shared.AppTabState
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderTabKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library's tab switcher lists both readers in one menu. These cover the projection from the
 * library rows, which is where the active highlight and the chapter/page labels come from.
 */
class HomeOpenTabsTest {

    @Test
    fun `lists text tabs before fixed layout tabs and keeps an active tab per reader`() {
        val state = ReaderScreenState(
            openTabs = listOf(
                pdf("pdf_1", displayName = "manual.pdf", lastPage = 11),
                pdf("pdf_2", displayName = "atlas.pdf", lastPage = 0),
            ),
            textOpenTabs = listOf(
                epub("epub_1", title = "Dune", lastChapter = 4, progress = 62.4f),
                epub("epub_2", title = "Emma", lastChapter = 0),
            ),
            tabState = AppTabState(
                fixedLayout = AppTabGroup(listOf("pdf_1", "pdf_2"), activeBookId = "pdf_1"),
                text = AppTabGroup(listOf("epub_1", "epub_2"), activeBookId = "epub_2"),
            ),
        )

        val tabs = state.homeOpenTabs()

        assertEquals(listOf("epub_1", "epub_2", "pdf_1", "pdf_2"), tabs.map { it.bookId })
        assertEquals(listOf(ReaderTabKind.TEXT, ReaderTabKind.TEXT, ReaderTabKind.FIXED_LAYOUT, ReaderTabKind.FIXED_LAYOUT), tabs.map { it.kind })
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
        val state = ReaderScreenState(
            textOpenTabs = listOf(epub("epub", title = "Dune", customName = "Book club pick")),
            openTabs = listOf(pdf("pdf", displayName = "manual.pdf", title = "Manual")),
            tabState = AppTabState(
                text = AppTabGroup(listOf("epub"), activeBookId = "epub"),
                fixedLayout = AppTabGroup(listOf("pdf"), activeBookId = "pdf"),
            ),
        )

        val tabs = state.homeOpenTabs()

        assertEquals("Book club pick", tabs[0].title)
        assertEquals("Manual", tabs[1].title)
        // The option swaps only PDF titles for their file name, as the cards do.
        assertEquals("manual.pdf", state.homeOpenTabs(usePdfFileNameAsDisplayName = true)[1].title)
        assertEquals("Book club pick", state.homeOpenTabs(usePdfFileNameAsDisplayName = true)[0].title)
    }

    @Test
    fun `skips rows no reader owns and states without tabs`() {
        val state = ReaderScreenState(
            openTabs = listOf(
                pdf("pdf", displayName = "manual.pdf"),
                pdf("audio", displayName = "talk.m4b", type = FileType.AUDIOBOOK),
            ),
            tabState = AppTabState(fixedLayout = AppTabGroup(listOf("pdf", "audio"), activeBookId = "pdf")),
        )

        assertEquals(listOf("pdf"), state.homeOpenTabs().map { it.bookId })
        assertTrue(ReaderScreenState().homeOpenTabs().isEmpty())
    }

    private fun epub(
        bookId: String,
        title: String? = null,
        customName: String? = null,
        lastChapter: Int? = null,
        progress: Float? = null,
    ) = RecentFileItem(
        bookId = bookId,
        uriString = "content://$bookId",
        type = FileType.EPUB,
        displayName = "$bookId.epub",
        timestamp = 1L,
        title = title,
        customName = customName,
        lastChapterIndex = lastChapter,
        progressPercentage = progress,
    )

    private fun pdf(
        bookId: String,
        displayName: String,
        title: String? = null,
        type: FileType = FileType.PDF,
        lastPage: Int? = null,
        progress: Float? = null,
    ) = RecentFileItem(
        bookId = bookId,
        uriString = "content://$bookId",
        type = type,
        displayName = displayName,
        timestamp = 1L,
        title = title,
        lastPage = lastPage,
        progressPercentage = progress,
    )
}
