package com.aryan.reader.shared

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderPageInfoBarLayoutTest {

    @Test
    fun `side reserve keeps the old 48dp spacing for narrow labels`() {
        // Clock "9:41" and "3.2%" are both well under 48dp, so the title gets
        // the established inset instead of growing into the label space.
        val reserve = readerPageInfoTitleSideReservePx(
            leftLabelWidthPx = 60,
            rightLabelWidthPx = 80,
            minGapPx = 24,
            minReservePx = 120,
            barWidthPx = 1000
        )
        assertEquals(120, reserve)
    }

    @Test
    fun `wide labels push the reserve past the old 48dp floor`() {
        // A 12-hour clock plus "100.0%" at a large font scale: the reserve has to
        // grow to widest + gap, otherwise the labels overlap the title.
        val reserve = readerPageInfoTitleSideReservePx(
            leftLabelWidthPx = 300,
            rightLabelWidthPx = 260,
            minGapPx = 24,
            minReservePx = 120,
            barWidthPx = 1000
        )
        assertEquals(324, reserve)
        assertTrue(reserve > ReaderPageInfoTitleMinSideReserve.value * 2f)
    }

    @Test
    fun `reserve is symmetric so the title stays centred`() {
        // The clock is much wider than the percentage; using the widest on both
        // sides keeps the title centred on the bar.
        val reserve = readerPageInfoTitleSideReservePx(
            leftLabelWidthPx = 300,
            rightLabelWidthPx = 100,
            minGapPx = 24,
            minReservePx = 0,
            barWidthPx = 1000
        )
        assertEquals(324, reserve)
    }

    @Test
    fun `reserve never squeezes the title below its minimum share`() {
        // Absurdly wide labels must not push the title to zero width, which would
        // hide the chapter entirely on a narrow screen.
        val barWidthPx = 400
        val reserve = readerPageInfoTitleSideReservePx(
            leftLabelWidthPx = 2000,
            rightLabelWidthPx = 2000,
            minGapPx = 24,
            minReservePx = 120,
            barWidthPx = barWidthPx
        )
        assertEquals(150, reserve)
        assertEquals(100, readerPageInfoTitleWidthPx(barWidthPx, reserve))
    }

    @Test
    fun `title width is the bar minus both reserves and never goes negative`() {
        assertEquals(1000 - 2 * 150, readerPageInfoTitleWidthPx(1000, 150))
        assertEquals(0, readerPageInfoTitleWidthPx(1000, 600))
        assertEquals(0, readerPageInfoTitleWidthPx(0, 0))
    }

    private fun clearance(
        startTop: Dp = 0.dp,
        startBottom: Dp = 0.dp,
        endTop: Dp = 0.dp,
        endBottom: Dp = 0.dp,
        startInset: Dp = 0.dp,
        endInset: Dp = 0.dp,
        barSidePadding: Dp = 16.dp
    ) = readerPageInfoCornerClearance(
        startTopRadius = startTop,
        startBottomRadius = startBottom,
        endTopRadius = endTop,
        endBottomRadius = endBottom,
        startInset = startInset,
        endInset = endInset,
        barSidePadding = barSidePadding
    )

    @Test
    fun `corner clearance insets the edge by the radius beyond existing padding`() {
        // A radius inside the bar's 16dp side padding: already covered.
        assertEquals(
            0.dp,
            clearance(startTop = 16.dp, startBottom = 16.dp, endTop = 16.dp, endBottom = 16.dp).start
        )
        // A generous radius has to push the content further in, or the clock and
        // percentage get sliced by the corner curve.
        assertEquals(
            14.dp,
            clearance(startTop = 30.dp, startBottom = 30.dp, endTop = 30.dp, endBottom = 30.dp).start
        )
    }

    @Test
    fun `corner clearance is zero on a screen with no rounded corners`() {
        // The API is a no-op on square displays, so the bar must keep its exact
        // benchmark spacing rather than gaining stray padding.
        assertEquals(clearance(), ReaderPageInfoCornerClearance(0.dp, 0.dp))
    }

    @Test
    fun `corner clearance subtracts the system insets already applied`() {
        // calculatePadding is `radius - margin - padding`. Leaving the margin out
        // double-counts the inset and pushes the labels further in than the
        // guideline asks -- a landscape gesture pill already holds the label clear.
        assertEquals(
            10.dp,
            clearance(
                startTop = 40.dp,
                startBottom = 40.dp,
                endTop = 40.dp,
                endBottom = 40.dp,
                startInset = 14.dp
            ).start
        )
        assertEquals(
            0.dp,
            clearance(
                startTop = 40.dp,
                startBottom = 40.dp,
                endTop = 40.dp,
                endBottom = 40.dp,
                startInset = 24.dp
            ).start
        )
    }

    @Test
    fun `corner clearance resolves each side from that side's own corners`() {
        // The guide pairs corners per edge: start = max(topStart, bottomStart).
        // Reading all four as one maximum borrows the far corner's radius for the
        // near one and shifts the labels off-centre.
        assertEquals(
            14.dp,
            clearance(
                startTop = 20.dp,
                startBottom = 30.dp,
                endTop = 40.dp,
                endBottom = 10.dp
            ).start
        )
        assertEquals(
            24.dp,
            clearance(
                startTop = 20.dp,
                startBottom = 30.dp,
                endTop = 40.dp,
                endBottom = 10.dp
            ).end
        )
    }

    @Test
    fun `corner clearance can differ per side when the insets do`() {
        // Landscape with a gesture pill on one edge: that side needs no corner
        // clearance at all, the other still does.
        assertEquals(
            ReaderPageInfoCornerClearance(start = 0.dp, end = 24.dp),
            clearance(
                startTop = 40.dp,
                startBottom = 40.dp,
                endTop = 40.dp,
                endBottom = 40.dp,
                startInset = 24.dp
            )
        )
    }

    @Test
    fun `font step keeps the full size when the title fits`() {
        assertEquals(ReaderPageInfoTitleFontSteps.first(), readerPageInfoTitleFontStep { true })
    }

    @Test
    fun `font step falls back to the smallest size when nothing fits`() {
        // Overflowing even at the smallest rung still renders, just ellipsized.
        assertEquals(
            ReaderPageInfoTitleFontSteps.last(),
            readerPageInfoTitleFontStep { false }
        )
    }

    @Test
    fun `font steps descend and stay inside a readable range`() {
        assertEquals(ReaderPageInfoTitleFontSteps.sortedDescending(), ReaderPageInfoTitleFontSteps)
        assertEquals(1f, ReaderPageInfoTitleFontSteps.first())
        // Never so small the title becomes unreadable.
        assertTrue(ReaderPageInfoTitleFontSteps.last() >= 0.6f)
    }

    @Test
    fun `title is never smaller than the side labels`() {
        // The title is the subject of the bar and the clock/percentage are its
        // metadata. Expressing the bump as a ratio of the label size means the
        // title cannot end up smaller than them whatever typography or system
        // font scale does.
        assertTrue(
            ReaderPageInfoTitleSizeRatio > 1f,
            "title must be larger than the labels, was $ReaderPageInfoTitleSizeRatio"
        )
        // Modest: a jump to bodyMedium-sized text makes the bar look unbalanced.
        assertTrue(ReaderPageInfoTitleSizeRatio <= 1.15f)
    }

    @Test
    fun `bar height fits two full size title lines`() {
        // 13.sp at 1.15 leading, two lines, plus the row's vertical padding.
        val lineHeightPx = 30
        val paddingPx = 6
        val heightPx = readerPageInfoBarContentHeightPx(
            titleLineHeightPx = lineHeightPx,
            maxLines = ReaderPageInfoTitleMaxLines,
            verticalPaddingPx = paddingPx,
            minHeightPx = 0
        )
        assertEquals(lineHeightPx * ReaderPageInfoTitleMaxLines + paddingPx, heightPx)
        assertTrue(heightPx >= lineHeightPx * ReaderPageInfoTitleMaxLines)
    }

    @Test
    fun `bar height never drops below the shipped minimum`() {
        // A very small system font scale must not shrink the bar below the height
        // the row always had.
        assertEquals(
            25,
            readerPageInfoBarContentHeightPx(
                titleLineHeightPx = 2,
                maxLines = ReaderPageInfoTitleMaxLines,
                verticalPaddingPx = 1,
                minHeightPx = 25
            )
        )
    }
}