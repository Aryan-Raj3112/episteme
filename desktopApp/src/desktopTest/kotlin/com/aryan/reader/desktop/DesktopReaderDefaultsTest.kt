package com.aryan.reader.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopReaderDefaultsTest {

    @Test
    fun `desktop open book dialog accepts every shared desktop readable format`() {
        assertEquals(
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP),
            desktopBookFileTypesForDialog()
        )
        assertTrue(FileType.PDF in desktopBookFileTypesForDialog())
    }

    @Test
    fun `desktop uses global reader defaults when book has no local settings`() {
        val defaults = ReaderSettings(fontSize = 23, readingMode = ReaderReadingMode.VERTICAL)
        val book = bookItem("without-local")

        assertEquals(defaults, resolvedDesktopReaderSettings(book, defaults))
    }

    @Test
    fun `desktop keeps local book reader settings ahead of global defaults`() {
        val defaults = ReaderSettings(fontSize = 23, readingMode = ReaderReadingMode.VERTICAL)
        val local = ReaderSettings(fontSize = 17, readingMode = ReaderReadingMode.PAGINATED, themeId = "sepia")
        val book = bookItem("with-local").copy(readerSettings = local)

        assertEquals(local, resolvedDesktopReaderSettings(book, defaults))
    }

    @Test
    fun `desktop pdf zoom allows deeper page magnification`() {
        val sharedDefaultMax = PdfZoomSpec().max
        val letterPageScale = DesktopPdfZoomSpec.safeRenderScale(
            pageWidth = 612f,
            pageHeight = 792f,
            requestedScale = 6f
        )

        assertEquals(8f, DesktopPdfZoomSpec.max)
        assertTrue(letterPageScale > sharedDefaultMax)
    }

    @Test
    fun `desktop pdf touchpad zoom factors zoom in and out`() {
        val zoomSpec = PdfZoomSpec(min = 0.5f, max = 8f, default = 1f)

        assertTrue(desktopPdfScrollZoomFactor(-1f) > 1.1f)
        assertTrue(desktopPdfScrollZoomFactor(1f) < 0.9f)
        assertEquals(8f, desktopPdfZoomTarget(currentZoom = 7.8f, zoomSpec = zoomSpec, factor = 2f))
        assertEquals(0.5f, desktopPdfZoomTarget(currentZoom = 0.6f, zoomSpec = zoomSpec, factor = 0.1f))
    }

    @Test
    fun `desktop paginated pdf page changes avoid high resolution first render`() {
        assertEquals(
            DesktopPdfPaginationFastFirstRenderMaxScale,
            desktopPdfPaginationFirstRenderScale(requestedScale = 6f, hasPageRender = false)
        )
        assertEquals(
            6f,
            desktopPdfPaginationFirstRenderScale(
                requestedScale = 6f,
                hasPageRender = false,
                isOpeningRender = true
            )
        )
        assertEquals(
            6f,
            desktopPdfPaginationFirstRenderScale(requestedScale = 6f, hasPageRender = true)
        )
        assertEquals(
            1.25f,
            desktopPdfPaginationFirstRenderScale(requestedScale = 1.25f, hasPageRender = false)
        )
        assertEquals(
            0.75f,
            desktopPdfPaginationFirstRenderScale(requestedScale = 0.75f, hasPageRender = false)
        )
    }

    @Test
    fun `desktop pdf anchored zoom keeps cursor content stable`() {
        assertEquals(
            300,
            desktopPdfAnchoredScrollTarget(currentScroll = 100, anchor = 100f, oldZoom = 1f, newZoom = 2f)
        )
        assertEquals(
            25,
            desktopPdfAnchoredScrollTarget(currentScroll = 150, anchor = 100f, oldZoom = 2f, newZoom = 1f)
        )
        assertEquals(
            100,
            desktopPdfAnchoredLazyItemScrollOffset(itemOffset = 0, anchor = 100f, oldZoom = 1f, newZoom = 2f)
        )
        assertEquals(
            200,
            desktopPdfAnchoredLazyItemScrollOffset(itemOffset = -50, anchor = 100f, oldZoom = 1f, newZoom = 2f)
        )
        assertEquals(
            IntOffset(100, 100),
            desktopPdfAnchoredPageScrollDelta(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentPageRootOffset = Offset.Zero,
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f
            )
        )
        assertEquals(
            IntOffset(0, 0),
            desktopPdfAnchoredPageScrollDelta(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentPageRootOffset = Offset(-100f, -100f),
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f
            )
        )
        val offCenterPivot = desktopPdfZoomPreviewPivotFraction(
            viewportRootOffset = Offset(20f, 30f),
            pageRootOffset = Offset(120f, 230f),
            anchor = Offset(250f, 450f),
            pageCanvasSize = IntSize(500, 1000)
        ) ?: error("Expected off-center pivot")
        assertEquals(0.3f, offCenterPivot.x, 0.0001f)
        assertEquals(0.25f, offCenterPivot.y, 0.0001f)

        val clampedPivot = desktopPdfZoomPreviewPivotFraction(
            viewportRootOffset = Offset.Zero,
            pageRootOffset = Offset.Zero,
            anchor = Offset(900f, -20f),
            pageCanvasSize = IntSize(500, 1000)
        ) ?: error("Expected clamped pivot")
        assertEquals(1f, clampedPivot.x, 0.0001f)
        assertEquals(0f, clampedPivot.y, 0.0001f)

        val firstPageDocumentTranslation = desktopPdfDocumentZoomPreviewTranslation(
            viewportRootOffset = Offset.Zero,
            pageRootOffset = Offset(0f, 0f),
            anchor = Offset(100f, 200f),
            previewScale = 2f
        ) ?: error("Expected first page document translation")
        assertEquals(-100f, firstPageDocumentTranslation.x, 0.0001f)
        assertEquals(-200f, firstPageDocumentTranslation.y, 0.0001f)

        val secondPageDocumentTranslation = desktopPdfDocumentZoomPreviewTranslation(
            viewportRootOffset = Offset.Zero,
            pageRootOffset = Offset(0f, 900f),
            anchor = Offset(100f, 200f),
            previewScale = 2f
        ) ?: error("Expected second page document translation")
        assertEquals(-100f, secondPageDocumentTranslation.x, 0.0001f)
        assertEquals(700f, secondPageDocumentTranslation.y, 0.0001f)
    }

    private fun bookItem(id: String): BookItem {
        return BookItem(
            id = id,
            path = "C:/Books/$id.epub",
            type = FileType.EPUB,
            displayName = "$id.epub",
            timestamp = 1L
        )
    }
}
