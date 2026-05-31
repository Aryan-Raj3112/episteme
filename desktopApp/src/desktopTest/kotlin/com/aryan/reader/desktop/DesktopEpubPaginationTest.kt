package com.aryan.reader.desktop

import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.layoutSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopEpubPaginationTest {
    @Test
    fun `measured pagination is not ready until measured pages are applied`() {
        val request = desktopPaginationRequest()
        val currentPages = listOf(readerPage(text = "old page"))
        val measuredPages = listOf(readerPage(text = "measured page"))

        assertFalse(
            desktopMeasuredPaginationReady(
                request = request,
                completedRequest = request,
                currentPages = currentPages,
                measuredPages = measuredPages
            )
        )
    }

    @Test
    fun `measured pagination is ready when current pages match measured pages`() {
        val request = desktopPaginationRequest()
        val measuredPages = listOf(readerPage(text = "measured page"))

        assertTrue(
            desktopMeasuredPaginationReady(
                request = request,
                completedRequest = request,
                currentPages = measuredPages,
                measuredPages = measuredPages
            )
        )
    }

    @Test
    fun `paginated display waits for completed measured pages`() {
        assertFalse(
            desktopPaginatedLayoutReadyForDisplay(
                readingMode = ReaderReadingMode.PAGINATED,
                measuredPagesApplied = false
            )
        )
        assertTrue(
            desktopPaginatedLayoutReadyForDisplay(
                readingMode = ReaderReadingMode.PAGINATED,
                measuredPagesApplied = true
            )
        )
        assertTrue(
            desktopPaginatedLayoutReadyForDisplay(
                readingMode = ReaderReadingMode.VERTICAL,
                measuredPagesApplied = false
            )
        )
    }

    @Test
    fun `measured chapter warm start replaces only that chapter and renumbers pages`() {
        val currentPages = listOf(
            readerPage(text = "chapter 0 page", chapterIndex = 0, pageIndex = 0),
            readerPage(text = "chapter 1 old a", chapterIndex = 1, pageIndex = 1),
            readerPage(text = "chapter 1 old b", chapterIndex = 1, pageIndex = 2),
            readerPage(text = "chapter 2 page", chapterIndex = 2, pageIndex = 3)
        )
        val measuredChapter = listOf(
            readerPage(text = "chapter 1 measured", chapterIndex = 1, pageIndex = 1)
        )

        val pages = desktopPagesWithMeasuredChapter(
            currentPages = currentPages,
            chapterIndex = 1,
            measuredChapterPages = measuredChapter
        )

        assertEquals(listOf(0, 1, 2), pages.map { it.pageIndex })
        assertEquals(listOf(0, 1, 2), pages.map { it.chapterIndex })
        assertEquals("chapter 1 measured", pages[1].text)
    }

    private fun desktopPaginationRequest(): DesktopEpubPaginationRequest {
        return DesktopEpubPaginationRequest(
            bookId = "book",
            chapterSignature = 1,
            layoutSignature = ReaderSettings(
                readingMode = ReaderReadingMode.PAGINATED,
                pageSpreadMode = ReaderPageSpreadMode.SINGLE
            ).layoutSignature(),
            viewport = ReaderViewportSpec(widthPx = 1200, heightPx = 900),
            density = DesktopEpubPaginationDensity(density = 1f, fontScale = 1f),
            cacheGeneration = 0
        )
    }

    private fun readerPage(
        text: String,
        chapterIndex: Int = 0,
        pageIndex: Int = 0
    ): ReaderPage {
        return ReaderPage(
            pageIndex = pageIndex,
            chapterIndex = chapterIndex,
            chapterTitle = "Chapter",
            text = text,
            startOffset = 0,
            endOffset = text.length
        )
    }
}
