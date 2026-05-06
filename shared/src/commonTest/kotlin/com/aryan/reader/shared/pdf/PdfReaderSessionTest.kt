package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.SearchHighlightMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfReaderSessionTest {

    @Test
    fun `initial state clamps page and reports progress`() {
        val state = SharedPdfReaderState.initial(pageCount = 5, initialPageIndex = 99)

        assertEquals(4, state.pageIndex)
        assertEquals(5, state.pageCount)
        assertEquals(100f, state.progressPercent)
        assertTrue(state.canGoPrevious)
    }

    @Test
    fun `page navigation clamps to document bounds`() {
        val state = SharedPdfReaderState.initial(pageCount = 3, initialPageIndex = 1)
            .reduce(SharedPdfReaderAction.NextPage)
            .reduce(SharedPdfReaderAction.NextPage)
            .reduce(SharedPdfReaderAction.PreviousPage)
            .reduce(SharedPdfReaderAction.GoToPage(-20))

        assertEquals(0, state.pageIndex)
    }

    @Test
    fun `first last and display mode actions are shared`() {
        val vertical = SharedPdfReaderState.initial(pageCount = 4, initialPageIndex = 1)
            .reduce(SharedPdfReaderAction.LastPage)
            .reduce(SharedPdfReaderAction.FirstPage)
            .reduce(SharedPdfReaderAction.DisplayModeToggled)
        val state = vertical.reduce(SharedPdfReaderAction.DisplayModeChanged(PdfDisplayMode.PAGINATION))

        assertEquals(0, state.pageIndex)
        assertEquals(PdfDisplayMode.VERTICAL_SCROLL, vertical.displayMode)
        assertEquals(PdfDisplayMode.PAGINATION, state.displayMode)
    }

    @Test
    fun `zoom changes use provided zoom spec`() {
        val zoomSpec = PdfZoomSpec(min = 0.5f, max = 4f, default = 1f)
        val state = SharedPdfReaderState.initial(pageCount = 1, zoomSpec = zoomSpec)
            .reduce(SharedPdfReaderAction.ZoomChanged(10f), zoomSpec)
            .reduce(SharedPdfReaderAction.ZoomBy(-10f), zoomSpec)

        assertEquals(0.5f, state.zoom)
    }

    @Test
    fun `initial zoom is clamped to provided zoom spec`() {
        val zoomSpec = PdfZoomSpec(min = 0.5f, max = 4f, default = 10f)

        val state = SharedPdfReaderState.initial(pageCount = 1, zoomSpec = zoomSpec)

        assertEquals(4f, state.zoom)
    }

    @Test
    fun `search query resets active result and result navigation wraps`() {
        val results = listOf(
            SharedPdfSearchResult(pageIndex = 1, preview = "first", matchIndex = 5),
            SharedPdfSearchResult(pageIndex = 3, preview = "second", matchIndex = 7)
        )

        val state = SharedPdfReaderState.initial(pageCount = 5)
            .reduce(SharedPdfReaderAction.GoToSearchResult(0, results))
            .reduce(SharedPdfReaderAction.SearchChanged("needle"))
            .reduce(SharedPdfReaderAction.GoToSearchResult(-1, results))

        assertEquals("needle", state.searchQuery)
        assertEquals(1, state.activeSearchResultIndex)
        assertEquals(3, state.pageIndex)
    }

    @Test
    fun `search highlight mode toggles between all and focused`() {
        val focused = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.SearchHighlightModeToggled)
        val all = focused.reduce(SharedPdfReaderAction.SearchHighlightModeToggled)
        val explicit = all.reduce(SharedPdfReaderAction.SearchHighlightModeChanged(SearchHighlightMode.FOCUSED))

        assertEquals(SearchHighlightMode.FOCUSED, focused.searchHighlightMode)
        assertEquals(SearchHighlightMode.ALL, all.searchHighlightMode)
        assertEquals(SearchHighlightMode.FOCUSED, explicit.searchHighlightMode)
    }

    @Test
    fun `tool selection applies shared defaults`() {
        val state = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.HIGHLIGHTER))

        val config = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER)
        assertEquals(PdfInkTool.HIGHLIGHTER, state.selectedTool)
        assertEquals(config.colorArgb, state.selectedColorArgb)
        assertEquals(config.strokeWidth, state.strokeWidth)
    }

    @Test
    fun `annotation actions mutate immutable annotation list`() {
        val first = annotation("first", pageIndex = 0)
        val second = annotation("second", pageIndex = 0)
        val third = annotation("third", pageIndex = 1)

        val state = SharedPdfReaderState.initial(pageCount = 2)
            .reduce(SharedPdfReaderAction.AnnotationsLoaded(listOf(first)))
            .reduce(SharedPdfReaderAction.AnnotationAdded(second))
            .reduce(SharedPdfReaderAction.AnnotationAdded(third))
            .reduce(SharedPdfReaderAction.UndoLastAnnotationOnPage(0))
            .reduce(SharedPdfReaderAction.ClearPageAnnotations(1))

        assertEquals(listOf(first), state.annotations)
    }

    @Test
    fun `search engine finds all case-insensitive matches with previews`() {
        val results = SharedPdfSearchEngine.search(
            pageTexts = listOf("Alpha beta alpha", "nothing", "ALPHA at the end"),
            query = "alpha"
        )

        assertEquals(listOf(0, 0, 2), results.map { it.pageIndex })
        assertEquals(listOf(0, 11, 0), results.map { it.matchIndex })
        assertTrue(results.first().preview.contains("Alpha"))
    }

    @Test
    fun `search highlights return all page matches or only focused match`() {
        val results = listOf(
            SharedPdfSearchResult(pageIndex = 0, preview = "first", matchIndex = 0),
            SharedPdfSearchResult(pageIndex = 0, preview = "second", matchIndex = 12),
            SharedPdfSearchResult(pageIndex = 1, preview = "third", matchIndex = 3)
        )

        assertEquals(
            listOf(results[0], results[1]),
            SharedPdfSearchEngine.highlightsForPage(
                results = results,
                pageIndex = 0,
                activeResultIndex = 2,
                mode = SearchHighlightMode.ALL
            )
        )
        assertEquals(
            listOf(results[1]),
            SharedPdfSearchEngine.highlightsForPage(
                results = results,
                pageIndex = 0,
                activeResultIndex = 1,
                mode = SearchHighlightMode.FOCUSED
            )
        )
    }

    @Test
    fun `most visible page follows largest viewport overlap`() {
        val visiblePages = listOf(
            PdfVisiblePageLayout(pageIndex = 2, top = -120f, bottom = 320f),
            PdfVisiblePageLayout(pageIndex = 3, top = 320f, bottom = 920f),
            PdfVisiblePageLayout(pageIndex = 4, top = 920f, bottom = 1300f)
        )

        val pageIndex = mostVisiblePdfPageIndex(
            visiblePages = visiblePages,
            viewportTop = 0f,
            viewportBottom = 800f,
            fallbackPageIndex = 2
        )

        assertEquals(3, pageIndex)
    }

    @Test
    fun `most visible page falls back when no measured page overlaps`() {
        val pageIndex = mostVisiblePdfPageIndex(
            visiblePages = listOf(PdfVisiblePageLayout(pageIndex = 8, top = 900f, bottom = 1200f)),
            viewportTop = 0f,
            viewportBottom = 800f,
            fallbackPageIndex = 5
        )

        assertEquals(5, pageIndex)
    }

    private fun annotation(id: String, pageIndex: Int): SharedPdfAnnotation {
        return SharedPdfAnnotation(
            id = id,
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.INK,
            points = listOf(PdfPagePoint(0.1f, 0.2f)),
            colorArgb = 0xFF111111.toInt()
        )
    }
}
