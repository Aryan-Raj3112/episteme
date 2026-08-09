package com.aryan.reader.paginatedreader

internal suspend fun resolveStableChapterStartPage(
    chapterIndex: Int,
    chapterCount: Int,
    pageCountsAreAccurate: Boolean,
    chapterStartPage: (Int) -> Int?,
    isChapterFinalized: (Int) -> Boolean,
    ensureChapterPaginated: suspend (Int) -> Boolean
): Int? = com.aryan.reader.shared.reader.resolveSharedStableChapterStartPage(
    chapterIndex = chapterIndex,
    chapterCount = chapterCount,
    pageCountsAreAccurate = pageCountsAreAccurate,
    chapterStartPage = chapterStartPage,
    isChapterFinalized = isChapterFinalized,
    ensureChapterPaginated = ensureChapterPaginated,
)
