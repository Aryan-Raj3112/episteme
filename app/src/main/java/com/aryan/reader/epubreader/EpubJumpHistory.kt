package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.shared.ReaderLocator

/**
 * Returns the page that represents the reader position at the moment an explicit jump starts.
 *
 * A settled page is authoritative after a pager scroll has completed. While a pager scroll is
 * still in progress, currentPage is the page currently presented by the pager and is the best
 * available snapshot for an explicit navigation event.
 */
internal fun authoritativePaginatedPageIndex(
    currentPageIndex: Int,
    settledPageIndex: Int,
    isScrollInProgress: Boolean
): Int? {
    return if (isScrollInProgress) {
        currentPageIndex.takeIf { it >= 0 } ?: settledPageIndex.takeIf { it >= 0 }
    } else {
        settledPageIndex.takeIf { it >= 0 } ?: currentPageIndex.takeIf { it >= 0 }
    }
}

internal fun paginatedEpubJumpLocator(
    currentPageIndex: Int,
    settledPageIndex: Int,
    isScrollInProgress: Boolean,
    locatorForPage: (Int) -> Locator?,
    fallbackLocator: Locator?,
    fallbackChapterIndex: Int? = null
): ReaderLocator? {
    val pageIndex = authoritativePaginatedPageIndex(
        currentPageIndex = currentPageIndex,
        settledPageIndex = settledPageIndex,
        isScrollInProgress = isScrollInProgress
    ) ?: return null
    return locatorForPage(pageIndex)?.toEpubJumpLocator(pageIndex = pageIndex)
        ?: fallbackLocator
            ?.takeIf { fallbackChapterIndex == null || it.chapterIndex == fallbackChapterIndex }
            ?.toEpubJumpLocator(pageIndex = pageIndex)
}

internal fun Locator.toEpubJumpLocator(
    pageIndex: Int? = null,
    cfiOverride: String? = null
): ReaderLocator {
    return ReaderLocator(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        blockIndex = blockIndex,
        charOffset = charOffset,
        cfi = cfiOverride ?: "android-locator:$chapterIndex:$blockIndex:$charOffset"
    )
}
