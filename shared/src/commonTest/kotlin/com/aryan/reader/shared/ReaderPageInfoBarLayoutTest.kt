package com.aryan.reader.shared

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

    @Test
    fun `corner clearance insets the edge by the radius beyond existing padding`() {
        // A 40px radius against the bar's 42px side padding: already covered.
        assertEquals(0, readerPageInfoCornerClearancePx(maxCornerRadiusPx = 40, barSidePaddingPx = 42))
        // A generous radius has to push the content further in, or the clock and
        // percentage get sliced by the corner curve.
        assertEquals(38, readerPageInfoCornerClearancePx(maxCornerRadiusPx = 80, barSidePaddingPx = 42))
    }

    @Test
    fun `corner clearance is zero on a screen with no rounded corners`() {
        // The API is a no-op on square displays, so the bar must keep its exact
        // benchmark spacing rather than gaining stray padding.
        assertEquals(0, readerPageInfoCornerClearancePx(maxCornerRadiusPx = 0, barSidePaddingPx = 42))
    }

    @Test
    fun `font step picks the largest size whose text fits`() {
        // Only the two smallest rungs fit, so the title shrinks before it wraps.
        val step = readerPageInfoTitleFontStep { it <= 0.85f }
        assertEquals(0.85f, step)
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