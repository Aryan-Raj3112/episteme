package com.aryan.reader.shared.reader

/** Android-benchmark policy for deciding when a paginated open position is stable enough to save. */
fun shouldSaveSharedPaginatedOpenPosition(
    isPaginatedMode: Boolean,
    hasPaginator: Boolean,
    isPagerInitialized: Boolean,
    isReconfigurationRestoring: Boolean,
    pageCount: Int,
    pageToSave: Int,
): Boolean = isPaginatedMode &&
    hasPaginator &&
    isPagerInitialized &&
    !isReconfigurationRestoring &&
    pageCount > 0 &&
    pageToSave in 0 until pageCount

/** Keeps Android's current-page-first anchor precedence during a pagination reconfiguration. */
fun <T> resolveSharedPaginatedReconfigurationAnchor(
    currentPageAnchor: T?,
    fallbackAnchor: T?,
): T? = currentPageAnchor ?: fallbackAnchor

/**
 * Resolves the current global index for a chapter without blocking on exact page counts for
 * preceding chapters. The returned index may be based on estimates, but the chapter-local
 * content is exact. Callers must keep the visible locator anchored while later page-count
 * corrections rebase the global index.
 *
 * Exact global numbering is less important than immediate, visually stable navigation for books
 * with very large spine lists.
 */
fun resolveSharedStableChapterStartPage(
    chapterIndex: Int,
    chapterCount: Int,
    chapterStartPage: (Int) -> Int?,
): Int? {
    if (chapterIndex !in 0 until chapterCount) return null
    return chapterStartPage(chapterIndex) ?: if (chapterIndex == 0) 0 else null
}
