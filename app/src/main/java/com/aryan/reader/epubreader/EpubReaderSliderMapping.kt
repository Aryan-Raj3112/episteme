package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.EpubPageSpread
import kotlin.math.roundToInt

/**
 * Translation between the EPUB page slider's two spaces.
 *
 * The Android reader drives its slider in 1-based *steps*: spreads once two-page split view is on,
 * book pages otherwise. The shared `SharedMobileEpubSlider` speaks 0-based *book pages* and derives
 * the step count itself, so `EpubReaderPageSlider` needs this translation at its boundary. Kept as
 * plain functions (not inline in the composable) because the split-view half is the part that can
 * silently point at the wrong page.
 */

internal data class EpubSliderBookPage(
    /** Book pages the slider spans. */
    val pageCount: Int,
    /** 0-based book page for the current step. */
    val pageIndex: Int,
)

/**
 * Resolve the current slider step to the shared widget's page space.
 *
 * In two-page paginated mode the step is a spread index and the book paginator supplies the page
 * count. In every other mode the step *is* the page: native vertical reading and per-chapter scroll
 * each carry their own total in [totalPages], and [EpubPageSpread.spreadToBookPage] clamps to
 * whatever count it is handed — so passing it the (possibly still-zero) book paginator total there
 * would pin the thumb to page 0.
 */
internal fun epubSliderBookPage(
    totalPages: Int,
    sliderCurrentPage: Float,
    isTwoPageSpread: Boolean,
    totalBookPageCount: Int,
): EpubSliderBookPage {
    val maxStep = totalPages.coerceAtLeast(1)
    val step = sliderCurrentPage.roundToInt().coerceIn(1, maxStep)
    if (!isTwoPageSpread) {
        return EpubSliderBookPage(pageCount = maxStep, pageIndex = step - 1)
    }
    val pageCount = totalBookPageCount.coerceAtLeast(1)
    val pageIndex = EpubPageSpread
        .spreadToBookPage(step - 1, totalBookPageCount, true)
        .coerceIn(0, pageCount - 1)
    return EpubSliderBookPage(pageCount = pageCount, pageIndex = pageIndex)
}

/** Inverse of [epubSliderBookPage]: the 1-based slider step that shows [bookPage]. */
internal fun epubSliderStepForBookPage(
    bookPage: Int,
    isTwoPageSpread: Boolean,
    totalBookPageCount: Int,
): Int {
    return if (isTwoPageSpread) {
        EpubPageSpread.bookPageToSpread(bookPage, totalBookPageCount, true) + 1
    } else {
        bookPage + 1
    }
}
