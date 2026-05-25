package com.aryan.reader.shared.reader

import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.BoxBorders
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SharedMeasuredEpubPaginatorTest {

    @Test
    fun `two page geometry caps each page to rendered page width on wide viewports`() {
        val geometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                margin = 48,
                readingMode = ReaderReadingMode.PAGINATED,
                pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
            ),
            viewport = ReaderViewportSpec(widthPx = 2_400, heightPx = 1_200)
        )

        assertEquals(760, geometry.pageWidthPx)
        assertEquals(1_104, geometry.pageHeightPx)
    }

    @Test
    fun `two page geometry subtracts margins inside each rendered page on constrained viewports`() {
        val geometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                horizontalMargin = 80,
                verticalMargin = 40,
                readingMode = ReaderReadingMode.PAGINATED,
                pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
            ),
            viewport = ReaderViewportSpec(widthPx = 1_300, heightPx = 900)
        )

        assertEquals(476, geometry.pageWidthPx)
        assertEquals(820, geometry.pageHeightPx)
    }

    @Test
    fun `paginated single page geometry matches one rendered page in a spread`() {
        val singlePageGeometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                horizontalMargin = 80,
                verticalMargin = 40,
                readingMode = ReaderReadingMode.PAGINATED,
                pageSpreadMode = ReaderPageSpreadMode.SINGLE
            ),
            viewport = ReaderViewportSpec(widthPx = 1_300, heightPx = 900)
        )
        val twoPageGeometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                horizontalMargin = 80,
                verticalMargin = 40,
                readingMode = ReaderReadingMode.PAGINATED,
                pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE
            ),
            viewport = ReaderViewportSpec(widthPx = 1_300, heightPx = 900)
        )

        assertEquals(twoPageGeometry, singlePageGeometry)
        assertEquals(476, singlePageGeometry.pageWidthPx)
        assertEquals(820, singlePageGeometry.pageHeightPx)
    }

    @Test
    fun `geometry does not invent minimum page space beyond the rendered viewport`() {
        val geometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                horizontalMargin = 80,
                verticalMargin = 120
            ),
            viewport = ReaderViewportSpec(widthPx = 300, heightPx = 220)
        )

        assertEquals(140, geometry.pageWidthPx)
        assertEquals(1, geometry.pageHeightPx)
    }

    @Test
    fun `geometry scales css-sized page settings to measured desktop pixels`() {
        val geometry = measuredPageGeometryFor(
            settings = ReaderSettings(
                pageWidth = 760,
                horizontalMargin = 0,
                verticalMargin = 0
            ),
            viewport = ReaderViewportSpec(widthPx = 1_900, heightPx = 860),
            densityScale = 1.25f
        )

        assertEquals(950, geometry.pageWidthPx)
        assertEquals(860, geometry.pageHeightPx)
    }

    @Test
    fun `paragraph split trims whitespace and prepares continuation styling`() {
        val paragraph = SemanticParagraph(
            text = "Alpha beta  gamma delta",
            spans = emptyList(),
            style = CssStyle(
                paragraphStyle = ParagraphStyle(
                    textIndent = TextIndent(firstLine = 24.sp, restLine = 8.sp)
                ),
                blockStyle = BlockStyle(
                    margin = BoxBorders(top = 12.dp)
                )
            ),
            elementId = null,
            cfi = null,
            startCharOffsetInSource = 100,
            blockIndex = 7
        )

        val split = assertNotNull(splitSemanticTextBlockAtOffsetForPagination(paragraph, 11))

        assertEquals("Alpha beta", split.first.text)
        assertEquals(100, split.first.startCharOffsetInSource)
        assertEquals("gamma delta", split.second.text)
        assertEquals(112, split.second.startCharOffsetInSource)
        assertEquals(
            TextIndent(firstLine = 0.sp, restLine = 8.sp),
            split.second.style.paragraphStyle.textIndent
        )
        assertEquals(0.dp, split.second.style.blockStyle.margin.top)
    }

    @Test
    fun `pagination stack collapses adjacent margins and can ignore trailing bottom margin`() {
        val items = listOf(
            PaginationStackItem(contentHeightPx = 100, marginTopPx = 18, marginBottomPx = 18),
            PaginationStackItem(contentHeightPx = 80, marginTopPx = 18, marginBottomPx = 18)
        )

        assertEquals(
            216,
            collapsedPaginationStackHeight(items, includeTrailingBottomMargin = false)
        )
        assertEquals(
            234,
            collapsedPaginationStackHeight(items, includeTrailingBottomMargin = true)
        )
    }

    @Test
    fun `pagination stack prefix fitting includes trailing bottom margin`() {
        val items = listOf(
            PaginationStackItem(contentHeightPx = 100, marginTopPx = 10, marginBottomPx = 30),
            PaginationStackItem(contentHeightPx = 80, marginTopPx = 10, marginBottomPx = 30)
        )

        assertEquals(
            1,
            paginationStackPrefixCountThatFits(
                items = items,
                availableHeightPx = 220,
                includeTrailingBottomMargin = true
            )
        )
        assertEquals(
            2,
            paginationStackPrefixCountThatFits(
                items = items,
                availableHeightPx = 220,
                includeTrailingBottomMargin = false
            )
        )
    }
}
