package com.aryan.reader.shared.reader

/**
 * Single source of truth for two-page spread arithmetic.
 *
 * Android's EPUB pager addresses *spreads* while shared's reader addresses *book
 * pages*, which is why both used to carry their own copy of this arithmetic
 * (parity item B5). The index bases must stay different — Android's
 * `PagerState` counts spreads — but the maths underneath is identical, so it
 * lives here once and both sides delegate.
 *
 * Everything takes plain booleans rather than [ReaderSettings] so Android can use
 * it without depending on shared's settings model. The [ReaderSettings]-flavoured
 * wrappers stay in [ReaderSpreadLayout], which is also where the
 * "spread only applies in paginated mode" rules are applied.
 */
object ReaderSpreadMath {

    fun pageStep(isTwoPageSpread: Boolean): Int = if (isTwoPageSpread) 2 else 1

    /** Number of spreads needed to lay out [pageCount] pages. Zero for an empty book. */
    fun spreadCount(pageCount: Int, isTwoPageSpread: Boolean): Int {
        if (pageCount <= 0) return 0
        return if (isTwoPageSpread) (pageCount + 1) / 2 else pageCount
    }

    /**
     * Steps offered by the page scrubber. Unlike [spreadCount] this is never zero:
     * a slider needs a valid range even before the book has paginated.
     */
    fun sliderStepCount(pageCount: Int, isTwoPageSpread: Boolean): Int {
        val total = pageCount.coerceAtLeast(1)
        return if (isTwoPageSpread) (total + 1) / 2 else total
    }

    /** Clamps [pageIndex] into range and, in two-page mode, aligns it to the spread start. */
    fun normalizePageIndex(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        if (pageCount <= 0) return 0
        val clamped = pageIndex.coerceIn(0, pageCount - 1)
        return if (isTwoPageSpread) {
            (clamped - (clamped % 2)).coerceIn(0, pageCount - 1)
        } else {
            clamped
        }
    }

    fun canGoNext(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Boolean {
        if (pageCount <= 1) return false
        val current = normalizePageIndex(pageIndex, pageCount, isTwoPageSpread)
        return current + pageStep(isTwoPageSpread) < pageCount
    }

    fun nextPageIndex(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        return normalizePageIndex(pageIndex + pageStep(isTwoPageSpread), pageCount, isTwoPageSpread)
    }

    fun previousPageIndex(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        return normalizePageIndex(pageIndex - pageStep(isTwoPageSpread), pageCount, isTwoPageSpread)
    }

    /** Book pages laid out for [pageIndex], in logical left-to-right order. */
    fun visiblePageIndices(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): List<Int> {
        if (pageCount <= 0) return emptyList()
        val start = normalizePageIndex(pageIndex, pageCount, isTwoPageSpread)
        if (!isTwoPageSpread) return listOf(start)
        return listOf(start, start + 1).filter { it in 0 until pageCount }
    }

    fun visiblePageIndicesForDisplay(
        pageIndex: Int,
        pageCount: Int,
        isTwoPageSpread: Boolean,
        isRightToLeft: Boolean
    ): List<Int> {
        val indices = visiblePageIndices(pageIndex, pageCount, isTwoPageSpread)
        return if (isRightToLeft) indices.asReversed() else indices
    }

    /** 1-based label for the spread at [pageIndex], e.g. `5` or `5-6`. */
    fun pageRangeLabel(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): String {
        val total = pageCount.coerceAtLeast(1)
        val pages = visiblePageIndices(pageIndex, total, isTwoPageSpread).ifEmpty { listOf(0) }
        val first = pages.first() + 1
        val last = pages.last() + 1
        return if (first == last) "$first" else "$first-$last"
    }

    fun sliderPositionForPage(pageIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        val normalized = normalizePageIndex(pageIndex, pageCount, isTwoPageSpread)
        val position = if (isTwoPageSpread) (normalized / 2) + 1 else normalized + 1
        return position.coerceIn(1, sliderStepCount(pageCount, isTwoPageSpread))
    }

    fun pageNumberForSliderPosition(position: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        val clamped = position.coerceIn(1, sliderStepCount(pageCount, isTwoPageSpread))
        val pageIndex = if (isTwoPageSpread) (clamped - 1) * 2 else clamped - 1
        return normalizePageIndex(pageIndex, pageCount, isTwoPageSpread) + 1
    }

    // --- Spread-indexed half. Android-only in practice; kept here so both bases
    // --- stay in one file and cannot drift apart.

    fun normalizeSpreadIndex(spreadIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        val count = spreadCount(pageCount, isTwoPageSpread)
        if (count <= 0) return 0
        return spreadIndex.coerceIn(0, count - 1)
    }

    fun spreadToBookPage(spreadIndex: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        if (pageCount <= 0) return 0
        return if (isTwoPageSpread) {
            (normalizeSpreadIndex(spreadIndex, pageCount, true) * 2).coerceIn(0, pageCount - 1)
        } else {
            spreadIndex.coerceIn(0, pageCount - 1)
        }
    }

    fun bookPageToSpread(bookPage: Int, pageCount: Int, isTwoPageSpread: Boolean): Int {
        if (pageCount <= 0) return 0
        val safeBookPage = bookPage.coerceIn(0, pageCount - 1)
        return if (isTwoPageSpread) {
            (safeBookPage / 2).coerceIn(0, spreadCount(pageCount, true) - 1)
        } else {
            safeBookPage
        }
    }

    /**
     * Raw spread for [bookPage] *without* clamping to a possibly still-growing
     * page count. Use for "wait until paginated" checks, then normalise with
     * [normalizeSpreadIndex] before scrolling.
     */
    fun rawBookPageToSpread(bookPage: Int, isTwoPageSpread: Boolean): Int {
        if (!isTwoPageSpread) return bookPage.coerceAtLeast(0)
        return (bookPage / 2).coerceAtLeast(0)
    }
}
