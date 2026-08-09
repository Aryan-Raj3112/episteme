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
 * Resolves Android's stable chapter start without trusting estimated prefix page counts.
 * Prefix chapters are finalized in order before their accumulated page offset is consumed.
 */
suspend fun resolveSharedStableChapterStartPage(
    chapterIndex: Int,
    chapterCount: Int,
    pageCountsAreAccurate: Boolean,
    chapterStartPage: (Int) -> Int?,
    isChapterFinalized: (Int) -> Boolean,
    ensureChapterPaginated: suspend (Int) -> Boolean,
): Int? {
    if (chapterIndex !in 0 until chapterCount) return null

    if (!pageCountsAreAccurate) {
        for (prefixChapter in 0 until chapterIndex) {
            if (!isChapterFinalized(prefixChapter) && !ensureChapterPaginated(prefixChapter)) {
                return null
            }
        }
    }

    return chapterStartPage(chapterIndex) ?: if (chapterIndex == 0) 0 else null
}
