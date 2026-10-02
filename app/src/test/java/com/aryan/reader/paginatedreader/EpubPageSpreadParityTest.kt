package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSpreadLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parity item B5. Proves Android's spread-index API and shared's page-index API
 * describe the *same* pagination once the index bases are translated.
 *
 * The two are deliberately parameterised differently and must stay that way:
 * Android's pager addresses spreads, shared's addresses book pages. The point is
 * that the arithmetic underneath is identical, so it should live in one place
 * rather than be re-derived (and re-drift) on each platform.
 *
 * Exhaustive over page counts 0..40 and every legal and several illegal indexes,
 * in all four mode combinations. Run before and after the extraction.
 */
class EpubPageSpreadParityTest {

    @Test
    fun `spread count matches shared slider step count for non-empty books`() {
        for (twoPage in BOOLEANS) {
            for (total in 1..40) {
                assertEquals(
                    "spreadCount($total, twoPage=$twoPage)",
                    ReaderSpreadLayout.sliderStepCount(total, settings(twoPage)),
                    EpubPageSpread.spreadCount(total, twoPage)
                )
            }
        }
    }

    @Test
    fun `spread index to book page matches shared normalize on the spread start`() {
        for (twoPage in BOOLEANS) {
            for (total in 1..40) {
                for (spread in -3..(total + 3)) {
                    val androidBookPage = EpubPageSpread.spreadToBookPage(spread, total, twoPage)
                    assertEquals(
                        "spreadToBookPage($spread, $total, twoPage=$twoPage)",
                        ReaderSpreadLayout.normalizePageIndex(androidBookPage, total, settings(twoPage)),
                        androidBookPage
                    )
                }
            }
        }
    }

    @Test
    fun `book page to spread matches the inverse spread translation`() {
        for (twoPage in BOOLEANS) {
            for (total in 1..40) {
                for (bookPage in -3..(total + 3)) {
                    val spread = EpubPageSpread.bookPageToSpread(bookPage, total, twoPage)
                    val firstVisible = EpubPageSpread.spreadToBookPage(spread, total, twoPage)
                    assertEquals(
                        "bookPageToSpread($bookPage, $total, twoPage=$twoPage) must land inside its own spread",
                        true,
                        firstVisible <= bookPage.coerceIn(0, total - 1) &&
                            bookPage.coerceIn(0, total - 1) < firstVisible + if (twoPage) 2 else 1
                    )
                }
            }
        }
    }

    @Test
    fun `visible book pages match shared visible indices`() {
        for (twoPage in BOOLEANS) {
            for (rtl in BOOLEANS) {
                for (total in 1..40) {
                    for (spread in 0 until EpubPageSpread.spreadCount(total, twoPage)) {
                        val android = EpubPageSpread.visibleBookPagesForDisplay(spread, total, twoPage, rtl)
                        val shared = ReaderSpreadLayout.visiblePageIndicesForDisplay(
                            EpubPageSpread.spreadToBookPage(spread, total, twoPage),
                            total,
                            settings(twoPage, rtl)
                        )
                        assertEquals(
                            "visibleBookPagesForDisplay($spread, $total, twoPage=$twoPage, rtl=$rtl)",
                            shared,
                            android
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `page range labels match`() {
        for (twoPage in BOOLEANS) {
            for (total in 1..40) {
                for (spread in 0 until EpubPageSpread.spreadCount(total, twoPage)) {
                    assertEquals(
                        "pageRangeLabel($spread, $total, twoPage=$twoPage)",
                        ReaderSpreadLayout.pageRangeLabel(
                            EpubPageSpread.spreadToBookPage(spread, total, twoPage),
                            total,
                            settings(twoPage)
                        ),
                        EpubPageSpread.pageRangeLabel(spread, total, twoPage)
                    )
                }
            }
        }
    }

    @Test
    fun `shared navigation agrees with Android stepping through spreads`() {
        for (twoPage in BOOLEANS) {
            for (total in 1..40) {
                val s = settings(twoPage)
                for (spread in 0 until EpubPageSpread.spreadCount(total, twoPage)) {
                    val bookPage = EpubPageSpread.spreadToBookPage(spread, total, twoPage)
                    val sharedNext = ReaderSpreadLayout.nextPageIndex(bookPage, total, s)
                    val androidNextSpread = EpubPageSpread.normalizeSpreadIndex(
                        spread + 1, total, twoPage
                    )
                    val expected = if (androidNextSpread > spread) {
                        EpubPageSpread.spreadToBookPage(androidNextSpread, total, twoPage)
                    } else {
                        bookPage
                    }
                    assertEquals(
                        "nextPageIndex from spread $spread of $total (twoPage=$twoPage)",
                        expected,
                        sharedNext
                    )
                    assertEquals(
                        "canGoNext from spread $spread of $total (twoPage=$twoPage)",
                        androidNextSpread > spread,
                        ReaderSpreadLayout.canGoNext(bookPage, total, s)
                    )
                }
            }
        }
    }

    @Test
    fun `empty and degenerate books behave identically on both sides`() {
        for (twoPage in BOOLEANS) {
            assertEquals(0, EpubPageSpread.spreadCount(0, twoPage))
            assertEquals(emptyList<Int>(), EpubPageSpread.visibleBookPages(0, 0, twoPage))
            assertEquals(0, EpubPageSpread.spreadToBookPage(4, 0, twoPage))
            assertEquals(0, EpubPageSpread.bookPageToSpread(4, 0, twoPage))
            assertEquals(0, EpubPageSpread.rawBookPageToSpread(-5, twoPage))

            val s = settings(twoPage)
            assertEquals(0, ReaderSpreadLayout.normalizePageIndex(4, 0, s))
            assertEquals(emptyList<Int>(), ReaderSpreadLayout.visiblePageIndices(4, 0, s))
            assertEquals(false, ReaderSpreadLayout.canGoNext(0, 0, s))
        }
    }

    private fun settings(twoPage: Boolean, rtl: Boolean = false) = ReaderSettings(
        readingMode = ReaderReadingMode.PAGINATED,
        pageSpreadMode = if (twoPage) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
        rightToLeftPagination = rtl
    )

    private companion object {
        val BOOLEANS = listOf(false, true)
    }
}
