package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.ui.sharedPdfThumbnailRowFor
import com.aryan.reader.shared.ui.sharedPdfThumbnailRows
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
    fun `initial interaction mode is neutral`() {
        val state = SharedPdfReaderState.initial(pageCount = 1)

        assertEquals(PdfInkTool.NONE, state.selectedTool)
        assertEquals(false, state.isTextSelectionMode)
    }

    @Test
    fun `initial shared reader keeps requested page until document count is known`() {
        val state = initialSharedPdfReaderState(
            persistedState = null,
            defaults = com.aryan.reader.shared.reader.ReaderSettings(themeId = "no_theme"),
            initialPageIndex = 37,
        )

        assertEquals(37, state.pageIndex)
        assertEquals(38, state.pageCount)
        assertEquals(12, state.copy(pageCount = 13).coerced().pageIndex)
    }

    @Test
    fun `legacy persisted reader without page count preserves its stored page`() {
        val restored = SharedPdfReaderStateSerializer.decode(
            raw = """{"pageIndex":37,"displayMode":"VERTICAL_SCROLL"}""",
            fallbackPageCount = 1,
            fallbackPageIndex = 12,
        )

        assertEquals(37, restored?.pageIndex)
        assertEquals(38, restored?.pageCount)
    }

    @Test
    fun `legacy persisted reader without position uses library restore page`() {
        val restored = SharedPdfReaderStateSerializer.decode(
            raw = """{"displayMode":"VERTICAL_SCROLL"}""",
            fallbackPageCount = 1,
            fallbackPageIndex = 12,
        )

        assertEquals(12, restored?.pageIndex)
        assertEquals(13, restored?.pageCount)
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
    fun `pdf theme selection persists through shared reader state`() {
        val themed = SharedPdfReaderState.initial(pageCount = 4)
            .reduce(SharedPdfReaderAction.ThemeChanged("sepia"))
        val restored = SharedPdfReaderStateSerializer.decode(
            SharedPdfReaderStateSerializer.encode(themed)
        )

        assertEquals("sepia", themed.themeId)
        assertEquals("sepia", restored?.themeId)
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
    fun `scroll lock captures and restores its zoom camera`() {
        val locked = SharedPdfReaderState.initial(pageCount = 8)
            .reduce(SharedPdfReaderAction.ScrollLockChanged(true, 4.25f, -120f, 340f))
        val restored = SharedPdfReaderStateSerializer.decode(SharedPdfReaderStateSerializer.encode(locked))

        assertEquals(true, restored?.isScrollLocked)
        assertEquals(4.25f, restored?.lockedZoomScale)
        assertEquals(-120f, restored?.lockedZoomOffsetX)
        assertEquals(340f, restored?.lockedZoomOffsetY)
    }

    @Test
    fun `invalid locked camera values are sanitized`() {
        val state = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.ScrollLockChanged(true, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN))

        assertEquals(1f, state.lockedZoomScale)
        assertEquals(0f, state.lockedZoomOffsetX)
        assertEquals(0f, state.lockedZoomOffsetY)
    }

    @Test
    fun `rich text document json survives reader state round trip`() {
        val withRichText = SharedPdfReaderState.initial(pageCount = 4).copy(richTextDocumentJson = """{"pages":[]}""")
        val restored = SharedPdfReaderStateSerializer.decode(SharedPdfReaderStateSerializer.encode(withRichText))

        assertEquals("""{"pages":[]}""", restored?.richTextDocumentJson)

        val legacy = SharedPdfReaderStateSerializer.decode("""{"pageIndex":0,"pageCount":4,"displayMode":"PAGINATION"}""")
        assertEquals("", legacy?.richTextDocumentJson)
    }

    @Test
    fun `reader viewport clamps zoom pages and scroll offsets`() {
        val viewport = SharedPdfReaderViewport(
            pageIndex = 99,
            displayMode = PdfDisplayMode.VERTICAL_SCROLL,
            zoom = Float.NaN,
            horizontalScrollOffset = -10,
            paginatedVerticalScrollOffset = -20,
            verticalFirstPageIndex = 40,
            verticalFirstPageScrollOffset = -30
        ).sanitized(
            pageCount = 5,
            zoomSpec = PdfZoomSpec(min = 0.5f, max = 4f, default = 1.25f)
        )

        assertEquals(PdfDisplayMode.VERTICAL_SCROLL, viewport.displayMode)
        assertEquals(4, viewport.pageIndex)
        assertEquals(4, viewport.verticalFirstPageIndex)
        assertEquals(1.25f, viewport.zoom)
        assertEquals(0, viewport.horizontalScrollOffset)
        assertEquals(0, viewport.paginatedVerticalScrollOffset)
        assertEquals(0, viewport.verticalFirstPageScrollOffset)
    }

    @Test
    fun `search query resets active result and out of range navigation does not wrap`() {
        val results = listOf(
            SharedPdfSearchResult(pageIndex = 1, preview = "first", matchIndex = 5),
            SharedPdfSearchResult(pageIndex = 3, preview = "second", matchIndex = 7)
        )

        val changed = SharedPdfReaderState.initial(pageCount = 5)
            .reduce(SharedPdfReaderAction.GoToSearchResult(0, results))
            .reduce(SharedPdfReaderAction.SearchHighlightModeChanged(SearchHighlightMode.FOCUSED))
            .reduce(SharedPdfReaderAction.SearchChanged("needle"))
        val state = changed
            .reduce(SharedPdfReaderAction.GoToSearchResult(-1, results))

        assertEquals("needle", changed.searchQuery)
        assertEquals(-1, changed.activeSearchResultIndex)
        assertEquals(SearchHighlightMode.FOCUSED, changed.searchHighlightMode)
        assertEquals(1, changed.pageIndex)
        assertEquals(-1, state.activeSearchResultIndex)
        assertEquals(1, state.pageIndex)
    }

    @Test
    fun `search chrome actions open toggle and close shared state`() {
        val opened = SharedPdfReaderState.initial(pageCount = 4)
            .reduce(SharedPdfReaderAction.SearchOpened)
        val typed = opened.reduce(SharedPdfReaderAction.SearchChanged("alpha"))
        val hidden = typed.reduce(SharedPdfReaderAction.SearchResultsPanelToggled)
        val closed = hidden.reduce(SharedPdfReaderAction.SearchClosed)

        assertTrue(opened.isSearchActive)
        assertTrue(opened.showSearchResultsPanel)
        assertEquals("alpha", typed.searchQuery)
        assertTrue(typed.isSearchActive)
        assertTrue(typed.showSearchResultsPanel)
        assertEquals(-1, typed.activeSearchResultIndex)
        assertEquals(false, hidden.showSearchResultsPanel)
        assertEquals(false, closed.isSearchActive)
        assertTrue(closed.showSearchResultsPanel)
        assertEquals("", closed.searchQuery)
        assertEquals(-1, closed.activeSearchResultIndex)
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
    fun `tool color and thickness changes persist per active tool`() {
        val penColor = 0xFF123456.toInt()
        val highlighterColor = 0x8CABCDEF.toInt()

        val penConfigured = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.PEN))
            .reduce(SharedPdfReaderAction.ColorSelected(penColor))
            .reduce(SharedPdfReaderAction.StrokeWidthChanged(0.012f))
        val highlighterConfigured = penConfigured
            .reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.HIGHLIGHTER))
            .reduce(SharedPdfReaderAction.ColorSelected(highlighterColor))
        val penAgain = highlighterConfigured.reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.PEN))
        val highlighterAgain = penAgain.reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.HIGHLIGHTER))

        assertEquals(penColor, penAgain.selectedColorArgb)
        assertEquals(0.012f, penAgain.strokeWidth)
        assertEquals(highlighterColor, highlighterAgain.selectedColorArgb)
    }

    @Test
    fun `pen palette changes follow android fixed slot behavior`() {
        val customColor = 0xFF010203.toInt()

        val state = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.PenPaletteChanged(listOf(0, customColor, 0xFF040506.toInt())))

        assertEquals(SharedPdfAnnotationDefaults.penPalette.size, state.penPalette.size)
        assertEquals(customColor, state.penPalette.first())
    }

    @Test
    fun `text selection markup tools and neutral mode are exclusive`() {
        val selectingText = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.PEN))
            .reduce(SharedPdfReaderAction.TextSelectionModeChanged(true))
        val addingTextAnnotation = selectingText.reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.TEXT))
        val neutral = addingTextAnnotation.reduce(SharedPdfReaderAction.ToolSelected(PdfInkTool.NONE))

        assertEquals(true, selectingText.isTextSelectionMode)
        assertEquals(PdfInkTool.NONE, selectingText.selectedTool)
        assertEquals(false, addingTextAnnotation.isTextSelectionMode)
        assertEquals(PdfInkTool.TEXT, addingTextAnnotation.selectedTool)
        assertEquals(false, neutral.isTextSelectionMode)
        assertEquals(PdfInkTool.NONE, neutral.selectedTool)
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
    fun `annotation undo and redo follow add remove history`() {
        val first = annotation("first", pageIndex = 0)
        val second = annotation("second", pageIndex = 0)

        val added = SharedPdfReaderState.initial(pageCount = 1)
            .reduce(SharedPdfReaderAction.AnnotationAdded(first))
            .reduce(SharedPdfReaderAction.AnnotationAdded(second))

        val undoneAdd = added.reduce(SharedPdfReaderAction.UndoAnnotationEdit)
        val redoneAdd = undoneAdd.reduce(SharedPdfReaderAction.RedoAnnotationEdit)
        val removed = redoneAdd.reduce(SharedPdfReaderAction.ClearPageAnnotations(0))
        val undoneRemove = removed.reduce(SharedPdfReaderAction.UndoAnnotationEdit)
        val redoneRemove = undoneRemove.reduce(SharedPdfReaderAction.RedoAnnotationEdit)

        assertEquals(listOf(first), undoneAdd.annotations)
        assertEquals(true, undoneAdd.canRedoAnnotationEdit)
        assertEquals(listOf(first, second), redoneAdd.annotations)
        assertEquals(emptyList(), removed.annotations)
        assertEquals(listOf(first, second), undoneRemove.annotations)
        assertEquals(emptyList(), redoneRemove.annotations)
    }

    @Test
    fun `bookmark actions toggle and normalize pages`() {
        val state = SharedPdfReaderState.initial(pageCount = 4)
            .reduce(
                SharedPdfReaderAction.BookmarksLoaded(
                    listOf(
                        SharedPdfBookmark(pageIndex = 2, label = "Two"),
                        SharedPdfBookmark(pageIndex = 99, label = "Invalid"),
                        SharedPdfBookmark(pageIndex = 2, label = "Duplicate")
                    )
                )
            )
            .reduce(SharedPdfReaderAction.BookmarkToggled(pageIndex = 1, createdAt = 10L))
            .reduce(SharedPdfReaderAction.BookmarkToggled(pageIndex = 2))

        assertEquals(listOf(1), state.bookmarks.map { it.pageIndex })
        assertEquals("Page 2", state.bookmarks.single().label)
    }

    @Test
    fun `bookmark rename and delete mutate the bookmark list`() {
        val base = SharedPdfReaderState.initial(pageCount = 4)
            .reduce(
                SharedPdfReaderAction.BookmarksLoaded(
                    listOf(
                        SharedPdfBookmark(pageIndex = 1, label = "One", createdAt = 10L),
                        SharedPdfBookmark(pageIndex = 2, label = "Two", createdAt = 20L)
                    )
                )
            )

        val renamed = base.reduce(SharedPdfReaderAction.BookmarkRenamed(pageIndex = 2, label = "Chapter two"))
        assertEquals("Chapter two", renamed.bookmarks.single { it.pageIndex == 2 }.label)
        assertEquals("One", renamed.bookmarks.single { it.pageIndex == 1 }.label)

        val renamedBlank = base.reduce(SharedPdfReaderAction.BookmarkRenamed(pageIndex = 2, label = "   "))
        assertEquals("Page 3", renamedBlank.bookmarks.single { it.pageIndex == 2 }.label)

        val deleted = base.reduce(SharedPdfReaderAction.BookmarkDeleted(pageIndex = 1))
        assertEquals(listOf(2), deleted.bookmarks.map { it.pageIndex })

        val missing = base.reduce(SharedPdfReaderAction.BookmarkDeleted(pageIndex = 99))
        assertEquals(base.bookmarks, missing.bookmarks)
    }

    @Test
    fun `shared page range label formats single pages and spreads`() {
        assertEquals("Page 3 of 10", sharedPdfPageRangeLabel("3", 10))
        assertEquals("Pages 2-3 of 10", sharedPdfPageRangeLabel("2-3", 10))
        assertEquals("Page 1 of 1", sharedPdfPageRangeLabel("", 1))
        assertEquals("Pages 4-5 of 12", sharedPdfPageRangeLabel("4-5", 12))
    }

    @Test
    fun `thumbnail grid rows chunk pages by three and locate row computes`() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4, 5)), sharedPdfThumbnailRows(6))
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), sharedPdfThumbnailRows(5))
        assertEquals(emptyList(), sharedPdfThumbnailRows(0))
        assertEquals(listOf(listOf(0)), sharedPdfThumbnailRows(1))
        assertEquals(0, sharedPdfThumbnailRowFor(0))
        assertEquals(0, sharedPdfThumbnailRowFor(2))
        assertEquals(1, sharedPdfThumbnailRowFor(3))
        assertEquals(2, sharedPdfThumbnailRowFor(6))
        assertEquals(0, sharedPdfThumbnailRowFor(-1))
    }

    @Test
    fun `keyboard navigation maps page keys and vertical scroll arrows`() {
        assertEquals(
            SharedPdfKeyboardNavigationAction.NEXT_PAGE,
            sharedPdfKeyboardNavigationAction(Key.PageDown, PdfDisplayMode.PAGINATION)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.PREVIOUS_PAGE,
            sharedPdfKeyboardNavigationAction(Key.PageUp, PdfDisplayMode.VERTICAL_SCROLL)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.FIRST_PAGE,
            sharedPdfKeyboardNavigationAction(Key.MoveHome, PdfDisplayMode.PAGINATION)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.LAST_PAGE,
            sharedPdfKeyboardNavigationAction(Key.MoveEnd, PdfDisplayMode.VERTICAL_SCROLL)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.SCROLL_DOWN,
            sharedPdfKeyboardNavigationAction(Key.DirectionDown, PdfDisplayMode.VERTICAL_SCROLL)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.NEXT_PAGE,
            sharedPdfKeyboardNavigationAction(Key.DirectionDown, PdfDisplayMode.PAGINATION)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.SCROLL_UP,
            sharedPdfKeyboardNavigationAction(Key.DirectionUp, PdfDisplayMode.VERTICAL_SCROLL)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.PREVIOUS_PAGE,
            sharedPdfKeyboardNavigationAction(Key.DirectionUp, PdfDisplayMode.PAGINATION)
        )
        assertEquals(
            SharedPdfKeyboardNavigationAction.NONE,
            sharedPdfKeyboardNavigationAction(Key.A, PdfDisplayMode.PAGINATION)
        )
    }

    @Test
    fun `highlight all rects merge overlapping lines and keep separate rows`() {
        val merged = sharedPdfMergeRectsIntoLines(
            listOf(
                PdfPageBounds(left = 0.1f, top = 0.1f, right = 0.3f, bottom = 0.2f),
                PdfPageBounds(left = 0.2f, top = 0.12f, right = 0.5f, bottom = 0.2f),
                PdfPageBounds(left = 0.1f, top = 0.4f, right = 0.2f, bottom = 0.5f),
                PdfPageBounds(left = 0.15f, top = 0.42f, right = 0.6f, bottom = 0.52f),
            )
        )
        assertEquals(2, merged.size)
        val first = merged[0]
        assertEquals(0.1f, first.left)
        assertEquals(0.5f, first.right)
        val second = merged[1]
        assertEquals(0.1f, second.left)
        assertEquals(0.6f, second.right)
    }

    @Test
    fun `highlight all rects sort out of order input`() {
        val merged = sharedPdfMergeRectsIntoLines(
            listOf(
                PdfPageBounds(left = 0.1f, top = 0.4f, right = 0.2f, bottom = 0.5f),
                PdfPageBounds(left = 0.1f, top = 0.1f, right = 0.3f, bottom = 0.2f),
            )
        )
        assertEquals(listOf(PdfPageBounds(0.1f, 0.1f, 0.3f, 0.2f), PdfPageBounds(0.1f, 0.4f, 0.2f, 0.5f)), merged)
    }

    @Test
    fun `highlight all rects handle empty input`() {
        assertEquals(emptyList(), sharedPdfMergeRectsIntoLines(emptyList()))
    }

    @Test
    fun `highlight all colors mirror android dark and light themes`() {
        val dark = sharedPdfHighlightAllColors(isDarkMode = true, primary = Color(0xFF6750A4))
        assertEquals(Color(0xFFFFEB3B).copy(alpha = 0.4f), dark.rectColor)
        assertEquals(Color.Transparent, dark.scrimColor)

        val light = sharedPdfHighlightAllColors(isDarkMode = false, primary = Color(0xFF6750A4))
        assertEquals(Color(0xFF6750A4).copy(alpha = 0f), light.rectColor)
        assertEquals(Color.Black.copy(alpha = 0.4f), light.scrimColor)
    }

    @Test
    fun `bookmark serializer round trips store and legacy arrays`() {
        val bookmarks = listOf(
            SharedPdfBookmark(pageIndex = 0, label = "Start", createdAt = 11L),
            SharedPdfBookmark(pageIndex = 3, label = "Appendix", createdAt = 22L)
        )

        assertEquals(bookmarks, SharedPdfBookmarkSerializer.decode(SharedPdfBookmarkSerializer.encode(bookmarks)))
        assertEquals(
            listOf(SharedPdfBookmark(pageIndex = 1, label = "Legacy", createdAt = 33L)),
            SharedPdfBookmarkSerializer.decode("""[{"pageIndex":1,"label":"Legacy","createdAt":33}]""")
        )
    }

    @Test
    fun `jump history records explicit jumps and exposes back and forward pages`() {
        val recorded = SharedPdfJumpHistory()
            .record(currentPageIndex = 0, targetPageIndex = 4, pageCount = 10)
            .record(currentPageIndex = 4, targetPageIndex = 8, pageCount = 10)

        val steppedBack = recorded.stepBack()
        val branched = steppedBack.record(currentPageIndex = 4, targetPageIndex = 2, pageCount = 10)

        assertEquals(listOf(0, 4, 8), recorded.pages)
        assertEquals(4, recorded.backPage)
        assertEquals(null, recorded.forwardPage)
        assertEquals(0, steppedBack.backPage)
        assertEquals(8, steppedBack.forwardPage)
        assertEquals(listOf(0, 4, 2), branched.pages)
        assertEquals(4, branched.backPage)
    }

    @Test
    fun `jump history replaces reverse adjacent jump like android`() {
        val history = SharedPdfJumpHistory()
            .record(currentPageIndex = 0, targetPageIndex = 4, pageCount = 10)
            .record(currentPageIndex = 0, targetPageIndex = 2, pageCount = 10)

        assertEquals(listOf(0, 2), history.pages)
        assertEquals(1, history.cursor)
        assertEquals(0, history.backPage)
    }

    @Test
    fun `jump history ignores invalid jumps prunes document bounds and caps entries`() {
        val unchanged = SharedPdfJumpHistory()
            .record(currentPageIndex = 0, targetPageIndex = 0, pageCount = 10)
            .record(currentPageIndex = 0, targetPageIndex = 99, pageCount = 10)

        val pruned = SharedPdfJumpHistory(pages = listOf(0, 3, 99, 4), cursor = 3)
            .pruned(pageCount = 5)

        val capped = (0 until 40).fold(SharedPdfJumpHistory(maxEntries = 5)) { history, page ->
            history.record(
                currentPageIndex = page,
                targetPageIndex = page + 1,
                pageCount = 50
            )
        }

        assertTrue(unchanged.pages.isEmpty())
        assertEquals(listOf(0, 3, 4), pruned.pages)
        assertEquals(2, pruned.cursor)
        assertEquals(listOf(36, 37, 38, 39, 40), capped.pages)
        assertEquals(4, capped.cursor)
    }

    @Test
    fun `annotation selection update and delete are shared`() {
        val first = annotation("first", pageIndex = 0)
        val second = annotation("second", pageIndex = 1)
        val updated = second.copy(text = "changed", colorArgb = 0xFF222222.toInt())

        val state = SharedPdfReaderState.initial(pageCount = 2)
            .reduce(SharedPdfReaderAction.AnnotationsLoaded(listOf(first, second)))
            .reduce(SharedPdfReaderAction.AnnotationSelected("second"))
            .reduce(SharedPdfReaderAction.AnnotationUpdated(updated))
            .reduce(SharedPdfReaderAction.AnnotationDeleted("second"))

        assertEquals(listOf(first), state.annotations)
        assertEquals(null, state.selectedAnnotationId)
    }

    @Test
    fun `search engine finds all case-insensitive matches with previews`() {
        val results = SharedPdfSearchEngine.search(
            pageTexts = listOf("Alpha beta alpha", "nothing", "ALPHA at the end"),
            query = "alpha"
        )

        assertEquals(listOf(0, 0, 2), results.map { it.pageIndex })
        assertEquals(listOf(0, 11, 0), results.map { it.matchIndex })
        assertEquals(listOf(5, 5, 5), results.map { it.matchLength })
        assertTrue(results.first().preview.contains("Alpha"))
    }

    @Test
    fun `search accepts android single-character queries`() {
        val results = SharedPdfSearchEngine.search(
            pageTexts = listOf("A cat", "beta"),
            query = "a",
        )

        assertEquals(listOf(0, 0, 1), results.map { it.pageIndex })
        assertEquals(listOf(0, 3, 3), results.map { it.matchIndex })
    }

    @Test
    fun `search index reuses indexed page text and preserves raw match ranges`() {
        val index = SharedPdfSearchIndex(pageCount = 3)
        index.putPage(0, "Alpha beta")
        index.putPage(1, "hello,\nworld appears here")
        index.putPage(2, "alpha again")

        val punctuationResults = index.search("hello, world")
        val alphaResults = index.search("alp")

        assertEquals(3, index.indexedPageCount)
        assertEquals(listOf(1), punctuationResults.map { it.pageIndex })
        assertEquals(0, punctuationResults.single().matchIndex)
        assertEquals("hello,\nworld".length, punctuationResults.single().matchLength)
        assertEquals(listOf(0, 2), alphaResults.map { it.pageIndex })
        assertEquals(listOf(3, 3), alphaResults.map { it.matchLength })
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

    @Test
    fun `vertical page gap option keeps default spacing or removes it`() {
        assertEquals(8.dp, pdfVerticalPageGapDp(isPageGapVisible = true, defaultGap = 8.dp))
        assertEquals(0.dp, pdfVerticalPageGapDp(isPageGapVisible = false, defaultGap = 8.dp))
    }

    @Test
    fun `vertical page layout removes fractional pixel seams when gap is hidden`() {
        val layout = calculatePdfVerticalPageLayoutPx(
            pageAspectRatios = listOf(0.707f, 0.721f, 0.69f),
            viewportWidthPx = 1081,
            viewportHeightPx = 1920,
            pageGapPx = 0
        )

        layout.pages.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.bottomPx, next.topPx)
        }
    }

    @Test
    fun `vertical page layout keeps exact configured page gap`() {
        val layout = calculatePdfVerticalPageLayoutPx(
            pageAspectRatios = listOf(0.707f, 0.721f),
            viewportWidthPx = 1081,
            viewportHeightPx = 1920,
            pageGapPx = 12
        )

        assertEquals(layout.pages.first().bottomPx + 12, layout.pages.last().topPx)
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
