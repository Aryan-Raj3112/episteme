package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderBookmark
import kotlin.math.abs

/**
 * Android's drawer removes duplicate bookmark CFIs before presentation.
 * Portable locators may not have a CFI, so use the shared location comparator.
 */
fun List<ReaderBookmark>.deduplicatedReaderBookmarks(): List<ReaderBookmark> {
    return fold(emptyList()) { unique, bookmark ->
        if (unique.any { it.locator.sameLocation(bookmark.locator) }) unique
        else unique + bookmark
    }
}

/**
 * Mirrors Android's active bookmark checks:
 * 1. exact portable location;
 * 2. same native text block within a small scroll-position tolerance;
 * 3. the currently visible paginated page.
 */
fun List<ReaderBookmark>.matchingReaderBookmark(
    locator: ReaderLocator?,
    visiblePageIndex: Int?,
    nearbyCharTolerance: Int = 160,
): ReaderBookmark? {
    if (locator == null && visiblePageIndex == null) return null

    locator?.let { current ->
        firstOrNull { it.locator.sameLocation(current) }?.let { return it }

        if (current.hasBlockPosition) {
            firstOrNull { bookmark ->
                val saved = bookmark.locator
                saved.chapterIndex == current.chapterIndex &&
                    saved.blockIndex == current.blockIndex &&
                    saved.charOffset != null &&
                    current.charOffset != null &&
                    abs(saved.charOffset - current.charOffset) <= nearbyCharTolerance
            }?.let { return it }
        }
    }

    return visiblePageIndex?.let { page ->
        firstOrNull { bookmark ->
            bookmark.pageIndex == page || bookmark.locator.pageIndex == page
        }
    }
}

/**
 * Removes every legacy duplicate that Android would treat as the active bookmark.
 * This keeps one toggle from exposing another duplicate and leaving the icon active.
 */
fun List<ReaderBookmark>.withoutMatchingReaderBookmarks(
    locator: ReaderLocator?,
    visiblePageIndex: Int?,
    nearbyCharTolerance: Int = 160,
): List<ReaderBookmark> {
    return filterNot { bookmark ->
        listOf(bookmark).matchingReaderBookmark(
            locator = locator,
            visiblePageIndex = visiblePageIndex,
            nearbyCharTolerance = nearbyCharTolerance,
        ) != null
    }
}
