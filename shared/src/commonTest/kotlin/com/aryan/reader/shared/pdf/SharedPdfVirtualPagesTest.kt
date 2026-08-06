package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedPdfVirtualPagesTest {

    private fun blank(after: Int, id: String = "b$after") = SharedPdfBlankPageInsertion(
        afterPdfIndex = after,
        widthPx = 400f,
        heightPx = 600f,
        id = id,
    )

    @Test
    fun `empty insertions yield a plain pdf layout`() {
        val layout = buildSharedPdfVirtualPageLayout(pageCount = 3, insertions = emptyList())
        assertEquals(3, layout.size)
        assertTrue(layout.all { it is SharedPdfVirtualPage.PdfPage })
        assertEquals(listOf(0, 1, 2), layout.map { (it as SharedPdfVirtualPage.PdfPage).pdfIndex })
    }

    @Test
    fun `blank inserted after a pdf page shifts subsequent pages`() {
        val layout = buildSharedPdfVirtualPageLayout(pageCount = 3, insertions = listOf(blank(1)))
        assertEquals(4, layout.size)
        assertEquals(SharedPdfVirtualPage.PdfPage(0), layout[0])
        assertEquals(SharedPdfVirtualPage.PdfPage(1), layout[1])
        assertTrue(layout[2] is SharedPdfVirtualPage.BlankPage)
        assertEquals(SharedPdfVirtualPage.PdfPage(2), layout[3])
    }

    @Test
    fun `multiple blanks at the same slot stay grouped in insertion order`() {
        val layout = buildSharedPdfVirtualPageLayout(
            pageCount = 2,
            insertions = listOf(blank(0, "first"), blank(0, "second"), blank(1, "third")),
        )
        assertEquals(5, layout.size)
        assertEquals(SharedPdfVirtualPage.PdfPage(0), layout[0])
        assertEquals("first", (layout[1] as SharedPdfVirtualPage.BlankPage).insertion.id)
        assertEquals("second", (layout[2] as SharedPdfVirtualPage.BlankPage).insertion.id)
        assertEquals(SharedPdfVirtualPage.PdfPage(1), layout[3])
        assertEquals("third", (layout[4] as SharedPdfVirtualPage.BlankPage).insertion.id)
    }

    @Test
    fun `out of range insertions are clamped to the last page`() {
        val layout = buildSharedPdfVirtualPageLayout(pageCount = 2, insertions = listOf(blank(5), blank(-1)))
        assertEquals(4, layout.size)
        assertTrue(layout[1] is SharedPdfVirtualPage.BlankPage)
        assertTrue(layout[3] is SharedPdfVirtualPage.BlankPage)
        assertEquals(SharedPdfVirtualPage.PdfPage(1), layout[2])
    }

    @Test
    fun `empty document with insertions collapses to no pages`() {
        val layout = buildSharedPdfVirtualPageLayout(pageCount = 0, insertions = listOf(blank(0)))
        assertEquals(0, layout.size)
    }

    @Test
    fun `pdf page index mapping resolves blanks and shifted pages`() {
        val layout = buildSharedPdfVirtualPageLayout(pageCount = 3, insertions = listOf(blank(0)))
        assertEquals(0, sharedPdfPdfPageIndexAt(layout, 0))
        assertNull(sharedPdfPdfPageIndexAt(layout, 1))
        assertEquals(1, sharedPdfPdfPageIndexAt(layout, 2))
        assertNull(sharedPdfPdfPageIndexAt(layout, 99))
        assertEquals(0, sharedPdfDisplayIndexFor(layout, 0))
        assertEquals(2, sharedPdfDisplayIndexFor(layout, 1))
        assertEquals(3, sharedPdfDisplayIndexFor(layout, 2))
        assertEquals(4, sharedPdfDisplayIndexFor(layout, 9))
    }

    @Test
    fun `nearest pdf page for blanks walks back to the preceding page`() {
        val layout = buildSharedPdfVirtualPageLayout(
            pageCount = 2,
            insertions = listOf(blank(0, "a"), blank(0, "b")),
        )
        assertEquals(0, sharedPdfNearestPdfPageIndex(layout, 1))
        assertEquals(0, sharedPdfNearestPdfPageIndex(layout, 2))
        assertEquals(1, sharedPdfNearestPdfPageIndex(layout, 3))
        assertEquals(0, sharedPdfNearestPdfPageIndex(layout, 0))
        assertNull(sharedPdfNearestPdfPageIndex(emptyList(), 0))
    }

    @Test
    fun `state display count and coercion account for blank pages`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(0), blank(2)),
            pageIndex = 4,
        )
        assertEquals(5, state.displayPageCount)
        assertEquals(4, state.lastPageIndex)
        assertNull(state.currentPdfPageIndex)
        assertEquals(4, state.coerced().pageIndex)
        val onBlank = state.copy(pageIndex = 1)
        assertNull(onBlank.currentPdfPageIndex)
        assertEquals(0, onBlank.currentNearestPdfPageIndex)
        val onPdf = state.copy(pageIndex = 2)
        assertEquals(1, onPdf.currentPdfPageIndex)
        assertEquals(1, onPdf.currentNearestPdfPageIndex)
        val onLast = state.copy(pageIndex = 99)
        assertEquals(4, onLast.coerced().pageIndex)
    }

    @Test
    fun `go to page clamps against display count`() {
        val state = SharedPdfReaderState.initial(pageCount = 2).copy(
            blankPageInsertions = listOf(blank(0)),
        )
        assertEquals(1, state.reduce(SharedPdfReaderAction.NextPage).pageIndex)
        assertEquals(0, state.reduce(SharedPdfReaderAction.FirstPage).pageIndex)
        assertTrue(state.reduce(SharedPdfReaderAction.NextPage).canGoNext)
        assertEquals(2, state.copy(pageIndex = 1).reduce(SharedPdfReaderAction.NextPage).pageIndex)
    }

    @Test
    fun `insert blank page lands after the current display page and navigates to it`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(pageIndex = 1)
        val next = state.reduce(
            SharedPdfReaderAction.InsertBlankPageAt(
                displayIndex = 1,
                widthPx = 400f,
                heightPx = 600f,
                id = "new"
            )
        )
        assertEquals(4, next.displayPageCount)
        assertEquals(listOf(SharedPdfBlankPageInsertion(1, 400f, 600f, "new")), next.blankPageInsertions)
        assertEquals(2, next.pageIndex)
        assertNull(next.currentPdfPageIndex)
        assertEquals(1, next.currentNearestPdfPageIndex)
    }

    @Test
    fun `insert on a blank display targets the preceding pdf page`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(1, "a")),
            pageIndex = 2,
        )
        val next = state.reduce(
            SharedPdfReaderAction.InsertBlankPageAt(displayIndex = 2, widthPx = 400f, heightPx = 600f, id = "b")
        )
        assertEquals(1, next.blankPageInsertions.last().afterPdfIndex)
        assertEquals(3, next.pageIndex)
        assertEquals(5, next.virtualPageLayout.size)
        assertEquals(
            listOf(
                SharedPdfVirtualPage.PdfPage(0),
                SharedPdfVirtualPage.PdfPage(1),
                SharedPdfVirtualPage.BlankPage(blank(1, "a")),
                SharedPdfVirtualPage.BlankPage(blank(1, "b")),
                SharedPdfVirtualPage.PdfPage(2),
            ),
            next.virtualPageLayout
        )
    }

    @Test
    fun `insert past the last page clamps to the last pdf page`() {
        val state = SharedPdfReaderState.initial(pageCount = 2).copy(pageIndex = 99)
        val next = state.reduce(
            SharedPdfReaderAction.InsertBlankPageAt(displayIndex = 99, widthPx = 400f, heightPx = 600f, id = "x")
        )
        assertEquals(1, next.blankPageInsertions.single().afterPdfIndex)
        assertEquals(2, next.pageIndex)
    }

    @Test
    fun `insert blank page carries the manual flag from the action`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(pageIndex = 0)

        val manual = state.reduce(
            SharedPdfReaderAction.InsertBlankPageAt(displayIndex = 0, widthPx = 400f, heightPx = 600f, id = "m")
        )
        assertTrue(manual.blankPageInsertions.single().wasManuallyAdded)

        val auto = state.reduce(
            SharedPdfReaderAction.InsertBlankPageAt(displayIndex = 0, widthPx = 400f, heightPx = 600f, id = "a", wasManuallyAdded = false)
        )
        assertTrue(!auto.blankPageInsertions.single().wasManuallyAdded)
    }

    @Test
    fun `delete blank page removes it and keeps the spot`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(0, "a")),
            pageIndex = 1,
        )
        val next = state.reduce(SharedPdfReaderAction.DeleteBlankPageAt(1))
        assertTrue(next.blankPageInsertions.isEmpty())
        assertEquals(1, next.pageIndex)
        assertEquals(1, next.currentPdfPageIndex)

        val onLast = SharedPdfReaderState.initial(pageCount = 2).copy(
            blankPageInsertions = listOf(blank(1, "a")),
            pageIndex = 2,
        )
        val afterDelete = onLast.reduce(SharedPdfReaderAction.DeleteBlankPageAt(2))
        assertEquals(1, afterDelete.pageIndex)
        assertEquals(1, afterDelete.currentPdfPageIndex)
    }

    @Test
    fun `delete on a pdf page is a no-op`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(1, "a")),
            pageIndex = 0,
        )
        assertEquals(state, state.reduce(SharedPdfReaderAction.DeleteBlankPageAt(0)))
    }

    @Test
    fun `bookmarks stay pdf indexed and survive coercion with blanks`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(1)),
            bookmarks = listOf(SharedPdfBookmark(pageIndex = 2, label = "Last", createdAt = 1)),
        )
        val coerced = state.coerced()
        assertEquals(1, coerced.bookmarks.size)
        assertEquals(2, coerced.bookmarks.single().pageIndex)
        val toggled = state.reduce(SharedPdfReaderAction.BookmarkToggled(pageIndex = 2))
        assertEquals(0, toggled.bookmarks.size)
        assertTrue(toggled.bookmarks.none { it.pageIndex == 2 })
    }

    @Test
    fun `go to search result maps pdf page to its display position`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(0)),
        )
        val result = SharedPdfSearchResult(
            pageIndex = 1,
            preview = "hello",
            matchIndex = 0,
            matchLength = 5,
            boundsList = emptyList(),
        )
        val next = state.reduce(
            SharedPdfReaderAction.GoToSearchResult(resultIndex = 0, results = listOf(result))
        )
        assertEquals(2, next.pageIndex)
        assertEquals(0, next.activeSearchResultIndex)
    }

    @Test
    fun `serializer round trips blank insertions and tolerates legacy payloads`() {
        val state = SharedPdfReaderState.initial(pageCount = 3).copy(
            blankPageInsertions = listOf(blank(1, "keep")),
        )
        val encoded = SharedPdfReaderStateSerializer.encode(state)
        val decoded = SharedPdfReaderStateSerializer.decode(encoded)!!
        assertEquals(listOf(blank(1, "keep")), decoded.blankPageInsertions)

        val legacy = SharedPdfReaderStateSerializer.decode(
            """{"version":1,"pageIndex":2,"pageCount":3,"displayMode":"PAGINATION","themeId":"no_theme"}"""
        )!!
        assertEquals(emptyList(), legacy.blankPageInsertions)
        assertEquals(2, legacy.pageIndex)
    }
}
