package com.aryan.reader.desktop

import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.layoutSignature
import kotlin.test.Test
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

    private fun readerPage(text: String): ReaderPage {
        return ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "Chapter",
            text = text,
            startOffset = 0,
            endOffset = text.length
        )
    }
}
