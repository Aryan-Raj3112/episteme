package com.aryan.reader.paginatedreader

internal fun resolveStableChapterStartPage(
    chapterIndex: Int,
    chapterCount: Int,
    chapterStartPage: (Int) -> Int?,
): Int? = com.aryan.reader.shared.reader.resolveSharedStableChapterStartPage(
    chapterIndex = chapterIndex,
    chapterCount = chapterCount,
    chapterStartPage = chapterStartPage,
)
