// EpubPageSpread.kt
package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.reader.ReaderSpreadMath

/**
 * Shared log tag for diagnosing page-blink / flash issues in the paginated
 * reader (both single and two-page spread). Filter logcat with:
 * `adb logcat | grep EpubSpreadBlink`
 */
const val EpubSpreadBlinkTag = "EpubSpreadBlink"

/**
 * Spread math for the Android EPUB paginated reader's two-page split view.
 *
 * Thin adapter over shared's [ReaderSpreadMath], which is also what shared's own
 * [com.aryan.reader.shared.reader.ReaderSpreadLayout] delegates to (parity item B5).
 * Both surfaces used to carry a separate copy of this arithmetic.
 *
 * The index bases intentionally differ and must not be unified: Android's pager
 * speaks spreads, so this object is spread-indexed, whereas shared is page-indexed.
 * `EpubPageSpreadParityTest` proves the two agree once translated.
 *
 * Spaces:
 * - book page: 0-based index into [BookPaginator] pages.
 * - spread: 0-based index into [androidx.compose.foundation.pager.PagerState]
 *   pages when two-page split view is enabled. Identical to book pages when
 *   disabled.
 */
object EpubPageSpread {
    /**
     * Default gutter between the two pages of a spread, in dp. Adjustable via
     * the format settings' Spread Gap control; kept in one place so the
     * paginator slot math and the render Row spacing stay identical.
     */
    const val SpreadGutterDp = 20

    fun spreadCount(totalBookPages: Int, isTwoPageSpread: Boolean): Int =
        ReaderSpreadMath.spreadCount(totalBookPages, isTwoPageSpread)

    fun normalizeSpreadIndex(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): Int =
        ReaderSpreadMath.normalizeSpreadIndex(spreadIndex, totalBookPages, isTwoPageSpread)

    /** First book page shown for [spreadIndex]. */
    fun spreadToBookPage(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): Int =
        ReaderSpreadMath.spreadToBookPage(spreadIndex, totalBookPages, isTwoPageSpread)

    /** Spread that contains [bookPage]. */
    fun bookPageToSpread(bookPage: Int, totalBookPages: Int, isTwoPageSpread: Boolean): Int =
        ReaderSpreadMath.bookPageToSpread(bookPage, totalBookPages, isTwoPageSpread)

    /**
     * Raw spread for [bookPage] without clamping to a (possibly still growing)
     * [totalBookPages]. Use for "wait until paginated" checks; normalize with
     * [normalizeSpreadIndex] before scrolling.
     */
    fun rawBookPageToSpread(bookPage: Int, isTwoPageSpread: Boolean): Int =
        ReaderSpreadMath.rawBookPageToSpread(bookPage, isTwoPageSpread)

    /** Book pages visible for [spreadIndex] in logical (LTR) order. */
    fun visibleBookPages(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): List<Int> =
        ReaderSpreadMath.visiblePageIndices(
            spreadToBookPage(spreadIndex, totalBookPages, isTwoPageSpread),
            totalBookPages,
            isTwoPageSpread
        )

    /** Book pages in display order (reversed for right-to-left pagination). */
    fun visibleBookPagesForDisplay(
        spreadIndex: Int,
        totalBookPages: Int,
        isTwoPageSpread: Boolean,
        isRightToLeft: Boolean
    ): List<Int> =
        ReaderSpreadMath.visiblePageIndicesForDisplay(
            pageIndex = spreadToBookPage(spreadIndex, totalBookPages, isTwoPageSpread),
            pageCount = totalBookPages,
            isTwoPageSpread = isTwoPageSpread,
            isRightToLeft = isRightToLeft
        )

    /** Human-readable label for a spread, e.g. "5" or "5-6" (1-based). */
    fun pageRangeLabel(spreadIndex: Int, totalBookPages: Int, isTwoPageSpread: Boolean): String =
        ReaderSpreadMath.pageRangeLabel(
            spreadToBookPage(spreadIndex, totalBookPages, isTwoPageSpread),
            totalBookPages,
            isTwoPageSpread
        )
}
