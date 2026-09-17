// EpubPageSpread.kt
package com.aryan.reader.paginatedreader

/**
 * Spread math for the Android EPUB paginated reader's two-page split view.
 *
 * Mirrors the shared `ReaderSpreadLayout` semantics (even-aligned spreads,
 * step of 2, slider counts spreads) but operates on plain book-page indices so
 * the Android pager — which historically speaks book pages — can speak
 * spreads without depending on shared `ReaderSettings`.
 *
 * Spaces:
 * - book page: 0-based index into [BookPaginator] pages.
 * - spread: 0-based index into [androidx.compose.foundation.pager.PagerState]
 *   pages when two-page split view is enabled. Identical to book pages when
 *   disabled.
 */
object EpubPageSpread {
    /** Gutter between the two pages of a spread. Matches shared paginator/render gap. */
    const val SpreadGutterDp = 28

    fun spreadCount(totalBookPages: Int, isTwoPageSpread: Boolean): Int {
        if (totalBookPages <= 0) return 0
        return if (isTwoPageSpread) (totalBookPages + 1) / 2 else totalBookPages
    }

    fun normalizeSpreadIndex(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): Int {
        val count = spreadCount(totalBookPages, isTwoPageSpread)
        if (count <= 0) return 0
        return spreadIndex.coerceIn(0, count - 1)
    }

    /** First book page shown for [spreadIndex]. */
    fun spreadToBookPage(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): Int {
        if (totalBookPages <= 0) return 0
        return if (isTwoPageSpread) {
            (normalizeSpreadIndex(spreadIndex, totalBookPages, true) * 2)
                .coerceIn(0, totalBookPages - 1)
        } else {
            spreadIndex.coerceIn(0, totalBookPages - 1)
        }
    }

    /** Spread that contains [bookPage]. */
    fun bookPageToSpread(bookPage: Int, totalBookPages: Int, isTwoPageSpread: Boolean): Int {
        if (totalBookPages <= 0) return 0
        val safeBookPage = bookPage.coerceIn(0, totalBookPages - 1)
        return if (isTwoPageSpread) {
            (safeBookPage / 2).coerceIn(0, spreadCount(totalBookPages, true) - 1)
        } else {
            safeBookPage
        }
    }

    /** Book pages visible for [spreadIndex] in logical (LTR) order. */
    fun visibleBookPages(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): List<Int> {
        if (totalBookPages <= 0) return emptyList()
        val first = spreadToBookPage(spreadIndex, totalBookPages, isTwoPageSpread)
        if (!isTwoPageSpread) return listOf(first)
        return listOf(first, first + 1).filter { it in 0 until totalBookPages }
    }

    /** Book pages in display order (reversed for right-to-left pagination). */
    fun visibleBookPagesForDisplay(
        spreadIndex: Int,
        totalBookPages: Int,
        isTwoPageSpread: Boolean,
        isRightToLeft: Boolean
    ): List<Int> {
        val pages = visibleBookPages(spreadIndex, totalBookPages, isTwoPageSpread)
        return if (isRightToLeft) pages.asReversed() else pages
    }

    /** Human-readable label for a spread, e.g. "5" or "5-6" (1-based). */
    fun pageRangeLabel(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): String {
        val total = totalBookPages.coerceAtLeast(1)
        val pages = visibleBookPages(spreadIndex, total, isTwoPageSpread).ifEmpty { listOf(0) }
        val first = pages.first() + 1
        val last = pages.last() + 1
        return if (first == last) "$first" else "$first-$last"
    }
}
