package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderJumpPositionCaptureTest {
    @Test
    fun currentPdfCaptureUsesRendererSnapshotAndNormalizesIt() {
        assertEquals(
            21,
            captureCurrentPdfHistoryPage(
                renderedCurrentPage = 21,
                fallbackCurrentPage = 19,
                pageCount = 30,
            ),
        )
        assertEquals(
            20,
            captureCurrentPdfHistoryPage(
                renderedCurrentPage = 21,
                fallbackCurrentPage = 19,
                pageCount = 30,
                normalizePage = { it - (it % 2) },
            ),
        )
    }

    @Test
    fun pdfCapturePrefersFreshRendererPageOverStaleScreenPage() {
        var renderedPage: Int? = 0
        val first = capturePdfJumpHistoryOrigin(
            renderedCurrentPage = renderedPage,
            fallbackCurrentPage = 0,
            targetPage = 19,
            pageCount = 30,
        ) ?: error("expected a valid origin")

        var history = SharedPdfJumpHistory().record(
            first.currentPageIndex,
            first.targetPageIndex,
            pageCount = 30,
        )

        // A manual scroll changed the renderer, but the screen callback has
        // not recomposed yet. The next jump must use that immediate snapshot.
        renderedPage = 21
        val second = capturePdfJumpHistoryOrigin(
            renderedCurrentPage = renderedPage,
            fallbackCurrentPage = 19,
            targetPage = 0,
            pageCount = 30,
        ) ?: error("expected a valid origin")
        history = history.record(second.currentPageIndex, second.targetPageIndex, 30)

        assertEquals(listOf(0, 19, 21, 0), history.pages)
        assertEquals(21, history.backPage)
    }

    @Test
    fun pdfCaptureNormalizesSpreadTarget() {
        val origin = capturePdfJumpHistoryOrigin(
            renderedCurrentPage = 5,
            fallbackCurrentPage = 0,
            targetPage = 7,
            pageCount = 20,
            normalizeCurrent = { it - (it % 2) },
            normalizeTarget = { it - (it % 2) },
        )

        assertEquals(PdfJumpHistoryOrigin(4, 6), origin)
    }

    @Test
    fun epubCapturePrefersFreshRendererLocatorAndKeepsMetadata() {
        val fallback = ReaderLocator(chapterIndex = 0, pageIndex = 19, cfi = "desktop:0:190")
        val rendered = ReaderLocator(chapterIndex = 0, pageIndex = 21, cfi = "desktop:0:210")

        val captured = captureReaderJumpHistoryOrigin(
            renderedCurrentLocator = rendered,
            fallbackCurrentLocator = fallback,
            chapterCount = 2,
        )

        assertEquals(rendered, captured)
    }

    @Test
    fun captureRejectsInvalidRendererAndFallbackLocations() {
        assertEquals(
            ReaderLocator(chapterIndex = 1, pageIndex = 2),
            captureReaderJumpHistoryOrigin(
                renderedCurrentLocator = ReaderLocator(chapterIndex = 5),
                fallbackCurrentLocator = ReaderLocator(chapterIndex = 1, pageIndex = 2),
                chapterCount = 2,
            ),
        )
        assertNull(
            captureReaderJumpHistoryOrigin(
                renderedCurrentLocator = ReaderLocator(chapterIndex = 5),
                fallbackCurrentLocator = ReaderLocator(chapterIndex = 4),
                chapterCount = 2,
            ),
        )
    }
}
