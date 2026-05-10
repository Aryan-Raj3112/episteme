package com.aryan.reader.paginatedreader

internal suspend fun resolveStableChapterStartPage(
    chapterIndex: Int,
    chapterCount: Int,
    pageCountsAreAccurate: Boolean,
    chapterStartPage: (Int) -> Int?,
    isChapterFinalized: (Int) -> Boolean,
    ensureChapterPaginated: suspend (Int) -> Boolean
): Int? {
    if (chapterIndex !in 0 until chapterCount) return null

    if (!pageCountsAreAccurate) {
        for (prefixChapter in 0 until chapterIndex) {
            if (!isChapterFinalized(prefixChapter)) {
                val ready = ensureChapterPaginated(prefixChapter)
                if (!ready) return null
            }
        }
    }

    return chapterStartPage(chapterIndex) ?: if (chapterIndex == 0) 0 else null
}
