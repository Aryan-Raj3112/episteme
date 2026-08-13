package com.aryan.reader.shared

data class EpubBlockPosition(
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int
)

data class EpubVisibleTextRange(
    val chapterIndex: Int,
    val blockIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int
)

/** Android's current-page bookmark precedence for native vertical EPUB reading. */
fun <T> findEpubBookmarkForLocation(
    bookmarks: Iterable<T>,
    visibleRanges: List<EpubVisibleTextRange>,
    currentPosition: EpubBlockPosition?,
    currentPage: Int,
    cfi: (T) -> String,
    positionForCfi: (String) -> EpubBlockPosition?,
    pageForCfi: (String) -> Int?,
    nearbyOffsetTolerance: Int = 160
): T? {
    bookmarks.firstOrNull { bookmark ->
        val position = positionForCfi(cfi(bookmark)) ?: return@firstOrNull false
        visibleRanges.any { range ->
            range.chapterIndex == position.chapterIndex &&
                range.blockIndex == position.blockIndex &&
                position.charOffset in range.startCharOffset..range.endCharOffset
        }
    }?.let { return it }

    if (currentPosition != null) {
        bookmarks.firstOrNull { bookmark ->
            val position = positionForCfi(cfi(bookmark)) ?: return@firstOrNull false
            position.chapterIndex == currentPosition.chapterIndex &&
                position.blockIndex == currentPosition.blockIndex &&
                kotlin.math.abs(position.charOffset - currentPosition.charOffset) <= nearbyOffsetTolerance
        }?.let { return it }
    }

    return bookmarks.firstOrNull { bookmark -> pageForCfi(cfi(bookmark)) == currentPage }
}
