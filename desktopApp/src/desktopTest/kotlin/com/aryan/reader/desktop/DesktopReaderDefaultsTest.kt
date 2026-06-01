package com.aryan.reader.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.pdf.PdfZoomSpec
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `desktop library defaults migrate untouched reader defaults to two page pagination`() {
        val migrated = SharedLibrarySnapshot().withDesktopDefaults()

        assertEquals(DesktopReaderDefaultsVersion, migrated.desktopReaderDefaultsVersion)
        assertEquals(ReaderReadingMode.PAGINATED, migrated.readerDefaultSettings.readingMode)
        assertEquals(ReaderPageSpreadMode.TWO_PAGE, migrated.readerDefaultSettings.pageSpreadMode)
        assertEquals(ReaderReadingMode.PAGINATED, migrated.pdfReaderDefaultSettings.readingMode)
        assertEquals(ReaderPageSpreadMode.TWO_PAGE, migrated.pdfReaderDefaultSettings.pageSpreadMode)
        assertEquals("no_theme", migrated.pdfReaderDefaultSettings.themeId)
    }

    @Test
    fun `desktop reader settings engines are separated by shared reader surface`() {
        assertEquals(DesktopReaderSettingsEngine.TEXT, FileType.EPUB.desktopReaderSettingsEngine())
        assertEquals(DesktopReaderSettingsEngine.TEXT, FileType.MOBI.desktopReaderSettingsEngine())
        assertEquals(DesktopReaderSettingsEngine.TEXT, FileType.DOCX.desktopReaderSettingsEngine())
        assertEquals(DesktopReaderSettingsEngine.PDF, FileType.PDF.desktopReaderSettingsEngine())
        assertEquals(DesktopReaderSettingsEngine.PDF, FileType.CBZ.desktopReaderSettingsEngine())
        assertEquals(DesktopReaderSettingsEngine.PDF, FileType.CBT.desktopReaderSettingsEngine())
        assertEquals(DesktopReaderSettingsEngine.PDF, FileType.PPTX.desktopReaderSettingsEngine())
    }

    @Test
    fun `desktop engine settings update only matching reader family books`() {
        val textSettings = ReaderSettings(themeId = "sepia", readingMode = ReaderReadingMode.PAGINATED)
        val pdfSettings = ReaderSettings(themeId = "reverse", readingMode = ReaderReadingMode.PAGINATED)
        val books = listOf(
            bookItem("epub"),
            bookItem("mobi").copy(path = "C:/Books/mobi.mobi", type = FileType.MOBI, displayName = "mobi.mobi"),
            bookItem("pdf").copy(path = "C:/Books/pdf.pdf", type = FileType.PDF, displayName = "pdf.pdf")
        )

        val withTextDefaults = books.withDesktopReaderEngineSettings(DesktopReaderSettingsEngine.TEXT, textSettings)
        assertEquals(textSettings, withTextDefaults[0].readerSettings)
        assertEquals(textSettings, withTextDefaults[1].readerSettings)
        assertEquals(null, withTextDefaults[2].readerSettings)

        val withPdfDefaults = withTextDefaults.withDesktopReaderEngineSettings(DesktopReaderSettingsEngine.PDF, pdfSettings)
        assertEquals(textSettings, withPdfDefaults[0].readerSettings)
        assertEquals(textSettings, withPdfDefaults[1].readerSettings)
        assertEquals(pdfSettings, withPdfDefaults[2].readerSettings)
    }

    @Test
    fun `desktop pdf display mode is carried by pdf reader settings`() {
        assertEquals(PdfDisplayMode.PAGINATION, DesktopDefaultPdfDisplayMode)
        assertEquals(PdfDisplayMode.PAGINATION, DesktopDefaultPdfReaderSettings.toDesktopPdfDisplayMode())
        assertEquals(
            PdfDisplayMode.VERTICAL_SCROLL,
            ReaderSettings(readingMode = ReaderReadingMode.VERTICAL).toDesktopPdfDisplayMode()
        )
    }

    @Test
    fun `desktop pdf initial page is normalized before paginated spread display`() {
        val spreadSettings = ReaderSettings(
            readingMode = ReaderReadingMode.PAGINATED,
            pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
        )

        assertEquals(
            2,
            desktopPdfInitialPageIndex(
                requestedPageIndex = 3,
                pageCount = 10,
                displayMode = PdfDisplayMode.PAGINATION,
                settings = spreadSettings
            )
        )
        assertEquals(
            3,
            desktopPdfInitialPageIndex(
                requestedPageIndex = 3,
                pageCount = 10,
                displayMode = PdfDisplayMode.VERTICAL_SCROLL,
                settings = spreadSettings
            )
        )
        assertEquals(
            3,
            desktopPdfInitialPageIndex(
                requestedPageIndex = 3,
                pageCount = 10,
                displayMode = PdfDisplayMode.PAGINATION,
                settings = spreadSettings.copy(pageSpreadMode = ReaderPageSpreadMode.SINGLE)
            )
        )
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
    fun `desktop pdf page navigation commits pending zoom preview position`() {
        val preview = DesktopPdfZoomPreview(
            baseZoom = 1f,
            zoom = 2f,
            anchor = Offset(100f, 80f),
            displayMode = PdfDisplayMode.PAGINATION,
            pageIndex = 0
        )
        val snapshot = desktopPdfNavigationZoomSnapshot(
            preview = preview,
            currentHorizontalScroll = 40,
            currentVerticalScroll = 20
        ) ?: error("Expected navigation zoom snapshot")

        assertEquals(2f, snapshot.zoom)
        assertEquals(180, snapshot.horizontalScroll)
        assertEquals(120, snapshot.verticalScroll)
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
    fun `desktop pdf only displays renders for the requested page`() {
        assertTrue(desktopPdfRenderBelongsToPage(renderedPageIndex = 0, requestedPageIndex = 0))
        assertFalse(desktopPdfRenderBelongsToPage(renderedPageIndex = null, requestedPageIndex = 0))
        assertFalse(desktopPdfRenderBelongsToPage(renderedPageIndex = 1, requestedPageIndex = 0))
    }

    @Test
    fun `desktop pdf render scale rerenders only for missing or lower quality renders`() {
        assertTrue(desktopPdfRenderScaleNeedsUpgrade(renderedScale = null, requestedScale = 1f))
        assertTrue(desktopPdfRenderScaleNeedsUpgrade(renderedScale = 1f, requestedScale = 1.02f))
        assertTrue(desktopPdfRenderScaleNeedsUpgrade(renderedScale = Float.NaN, requestedScale = 1f))
        assertFalse(desktopPdfRenderScaleNeedsUpgrade(renderedScale = 1f, requestedScale = 1.005f))
        assertFalse(desktopPdfRenderScaleNeedsUpgrade(renderedScale = 2f, requestedScale = 1f))
        assertFalse(desktopPdfRenderScaleNeedsUpgrade(renderedScale = 1f, requestedScale = Float.NaN))
    }

    @Test
    fun `desktop pdf spread zoom anchors to page under cursor`() {
        val visiblePages = listOf(199, 200)
        val pageRoots = mapOf(
            199 to Offset(424f, 30f),
            200 to Offset(972f, 30f)
        )
        val pageSizes = mapOf(
            199 to IntSize(525, 693),
            200 to IntSize(525, 693)
        )

        assertEquals(
            200,
            desktopPdfSpreadZoomAnchorPageIndex(
                viewportRootOffset = Offset.Zero,
                anchor = Offset(1048.75f, 465f),
                visiblePageIndices = visiblePages,
                pageRootOffsets = pageRoots,
                pageSizes = pageSizes,
                fallbackPageIndex = 199
            )
        )
        assertEquals(
            199,
            desktopPdfSpreadZoomAnchorPageIndex(
                viewportRootOffset = Offset.Zero,
                anchor = Offset(500f, 465f),
                visiblePageIndices = visiblePages,
                pageRootOffsets = pageRoots,
                pageSizes = pageSizes,
                fallbackPageIndex = 199
            )
        )
        assertEquals(
            199,
            desktopPdfSpreadZoomAnchorPageIndex(
                viewportRootOffset = Offset.Zero,
                anchor = null,
                visiblePageIndices = visiblePages,
                pageRootOffsets = pageRoots,
                pageSizes = pageSizes,
                fallbackPageIndex = 199
            )
        )
        val fittedSpread = desktopPdfSpreadLayoutPrediction(
            viewportRootOffset = Offset.Zero,
            viewportSize = IntSize(1920, 991),
            visiblePageIndices = visiblePages,
            pageCanvasSizes = mapOf(
                199 to IntSize(667, 881),
                200 to IntSize(667, 881)
            ),
            horizontalScroll = 0,
            verticalScroll = 112,
            paddingPx = 30f,
            pageGapPx = 22.5f
        ) ?: error("Expected fitted spread prediction")
        assertEquals(0, fittedSpread.maxHorizontalScroll)
        assertEquals(0, fittedSpread.maxVerticalScroll)
        assertEquals(282f, fittedSpread.pageRootOffsets[199]?.x ?: -1f, 0.5f)
        assertEquals(972f, fittedSpread.pageRootOffsets[200]?.x ?: -1f, 0.5f)
        assertEquals(30f, fittedSpread.pageRootOffsets[200]?.y ?: -1f, 0.0001f)

        val scrollableSpread = desktopPdfSpreadLayoutPrediction(
            viewportRootOffset = Offset.Zero,
            viewportSize = IntSize(1920, 991),
            visiblePageIndices = visiblePages,
            pageCanvasSizes = mapOf(
                199 to IntSize(1371, 1810),
                200 to IntSize(1371, 1810)
            ),
            horizontalScroll = 696,
            verticalScroll = 575,
            paddingPx = 30f,
            pageGapPx = 22.5f
        ) ?: error("Expected scrollable spread prediction")
        assertEquals(905, scrollableSpread.maxHorizontalScroll)
        assertEquals(879, scrollableSpread.maxVerticalScroll)
        assertEquals(-666f, scrollableSpread.pageRootOffsets[199]?.x ?: 0f, 0.5f)
        assertEquals(728f, scrollableSpread.pageRootOffsets[200]?.x ?: 0f, 0.5f)
        assertEquals(-545f, scrollableSpread.pageRootOffsets[200]?.y ?: 0f, 0.0001f)
    }

    @Test
    fun `desktop pdf zoom preview bridges committed anchored zoom`() {
        val preview = DesktopPdfZoomPreview(
            baseZoom = 1f,
            zoom = 2f,
            anchor = Offset(100f, 100f),
            displayMode = PdfDisplayMode.PAGINATION,
            pageIndex = 0,
            viewportRootOffset = Offset.Zero,
            pageRootOffset = Offset.Zero
        )

        assertTrue(desktopPdfZoomPreviewMatchesScale(preview, 1f))
        assertTrue(desktopPdfZoomPreviewMatchesScale(preview, 2f))
        assertFalse(desktopPdfZoomPreviewMatchesScale(preview, 1.5f))
        assertEquals(
            Offset(-100f, -100f),
            desktopPdfZoomCommitPreviewTranslation(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentAnchorPageRootOffset = Offset.Zero,
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f,
                currentZoom = 2f
            )
        )
        assertEquals(
            Offset.Zero,
            desktopPdfZoomCommitPreviewTranslation(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentAnchorPageRootOffset = Offset(-100f, -100f),
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f,
                currentZoom = 2f
            )
        )
        assertEquals(
            null,
            desktopPdfZoomCommitPreviewTranslation(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentAnchorPageRootOffset = Offset.Zero,
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f,
                currentZoom = 1f
            )
        )
        assertEquals(0, desktopPdfReachableScrollDelta(currentScroll = 0, maxScroll = 0, requestedDelta = 100))
        assertEquals(0, desktopPdfReachableScrollDelta(currentScroll = 0, maxScroll = 200, requestedDelta = -40))
        assertEquals(-40, desktopPdfReachableScrollDelta(currentScroll = 80, maxScroll = 200, requestedDelta = -40))
        assertEquals(
            Offset.Zero,
            desktopPdfZoomCommitPreviewTranslation(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentAnchorPageRootOffset = Offset.Zero,
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f,
                currentZoom = 2f,
                scrollBounds = DesktopPdfZoomScrollBounds(
                    currentHorizontalScroll = 0,
                    maxHorizontalScroll = 0,
                    currentVerticalScroll = 0,
                    maxVerticalScroll = 0
                )
            )
        )
        assertEquals(
            Offset(-50f, -25f),
            desktopPdfZoomCommitPreviewTranslation(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentAnchorPageRootOffset = Offset.Zero,
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f,
                currentZoom = 2f,
                scrollBounds = DesktopPdfZoomScrollBounds(
                    currentHorizontalScroll = 0,
                    maxHorizontalScroll = 50,
                    currentVerticalScroll = 0,
                    maxVerticalScroll = 25
                )
            )
        )
        val pendingCommitBounds = desktopPdfZoomScrollBoundsWithCommitTargets(
            preview = preview.copy(
                commitTargetHorizontalScroll = 300,
                commitTargetVerticalScroll = 300
            ),
            currentHorizontalScroll = 0,
            maxHorizontalScroll = 0,
            currentVerticalScroll = 0,
            maxVerticalScroll = 0
        )
        assertEquals(300, pendingCommitBounds.maxHorizontalScroll)
        assertEquals(300, pendingCommitBounds.maxVerticalScroll)
        assertEquals(
            Offset(-100f, -100f),
            desktopPdfZoomCommitPreviewTranslation(
                viewportRootOffset = Offset.Zero,
                oldPageRootOffset = Offset.Zero,
                currentAnchorPageRootOffset = Offset.Zero,
                anchor = Offset(100f, 100f),
                oldZoom = 1f,
                newZoom = 2f,
                currentZoom = 2f,
                scrollBounds = pendingCommitBounds
            )
        )
        val fittingPagePrediction = desktopPdfSinglePageLayoutPrediction(
            viewportRootOffset = Offset.Zero,
            viewportSize = IntSize(1920, 991),
            pageCanvasSize = IntSize(1216, 1605),
            horizontalScroll = 0,
            verticalScroll = 0,
            paddingPx = 30f
        ) ?: error("Expected fitting page prediction")
        assertEquals(Offset(352f, 30f), fittingPagePrediction.rootOffset)
        assertEquals(0, fittingPagePrediction.maxHorizontalScroll)
        assertEquals(674, fittingPagePrediction.maxVerticalScroll)

        val oversizedPagePrediction = desktopPdfSinglePageLayoutPrediction(
            viewportRootOffset = Offset.Zero,
            viewportSize = IntSize(1920, 991),
            pageCanvasSize = IntSize(2498, 3298),
            horizontalScroll = 409,
            verticalScroll = 1122,
            paddingPx = 30f
        ) ?: error("Expected oversized page prediction")
        assertEquals(Offset(-379f, -1092f), oversizedPagePrediction.rootOffset)
        assertEquals(638, oversizedPagePrediction.maxHorizontalScroll)
        assertEquals(2367, oversizedPagePrediction.maxVerticalScroll)
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
