package com.aryan.reader.pdf

/**
 * Returns the pager page that represents the position when an explicit jump starts.
 * The visible/current page is authoritative during a pager transition; once settled, the
 * settled page avoids recording the page from the previous transition.
 */
internal fun authoritativePdfPaginationPageIndex(
    currentPageIndex: Int,
    settledPageIndex: Int,
    isScrollInProgress: Boolean,
): Int? {
    return if (isScrollInProgress) {
        currentPageIndex.takeIf { it >= 0 } ?: settledPageIndex.takeIf { it >= 0 }
    } else {
        settledPageIndex.takeIf { it >= 0 } ?: currentPageIndex.takeIf { it >= 0 }
    }
}
