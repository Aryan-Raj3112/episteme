package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator

/** The validated origin and destination used for one PDF jump-history entry. */
data class PdfJumpHistoryOrigin(
    val currentPageIndex: Int,
    val targetPageIndex: Int,
)

/**
 * Returns the page currently rendered by a PDF surface, preferring its
 * immediate snapshot over the screen's last callback value.
 */
fun captureCurrentPdfHistoryPage(
    renderedCurrentPage: Int?,
    fallbackCurrentPage: Int,
    pageCount: Int,
    normalizePage: (Int) -> Int = { it },
): Int? {
    if (pageCount <= 0) return null
    val current = renderedCurrentPage
        ?.takeIf { it in 0 until pageCount }
        ?: fallbackCurrentPage.takeIf { it in 0 until pageCount }
        ?: return null
    return normalizePage(current).takeIf { it in 0 until pageCount }
}

/**
 * Resolves a jump origin from the renderer's immediate snapshot.
 *
 * The renderer snapshot is preferred over the screen's last callback value.
 * Keeping this validation in common code makes platform callers use the same
 * bounds contract while still allowing each renderer to provide its own
 * synchronous snapshot implementation.
 */
fun capturePdfJumpHistoryOrigin(
    renderedCurrentPage: Int?,
    fallbackCurrentPage: Int,
    targetPage: Int,
    pageCount: Int,
    normalizeCurrent: (Int) -> Int = { it },
    normalizeTarget: (Int) -> Int = { it },
): PdfJumpHistoryOrigin? {
    val current = captureCurrentPdfHistoryPage(
        renderedCurrentPage = renderedCurrentPage,
        fallbackCurrentPage = fallbackCurrentPage,
        pageCount = pageCount,
        normalizePage = normalizeCurrent,
    ) ?: return null
    val target = normalizeTarget(targetPage)
        .takeIf { it in 0 until pageCount }
        ?: return null
    return PdfJumpHistoryOrigin(currentPageIndex = current, targetPageIndex = target)
}

/**
 * Resolves the current EPUB locator from an immediate renderer snapshot.
 *
 * A null/invalid renderer result falls back to the last screen value. The
 * caller can then pass the result to [ReaderJumpHistory.record] or
 * [ReaderJumpHistory.updateCurrentLocation] without duplicating validation.
 */
fun captureReaderJumpHistoryOrigin(
    renderedCurrentLocator: ReaderLocator?,
    fallbackCurrentLocator: ReaderLocator?,
    chapterCount: Int,
): ReaderLocator? {
    if (chapterCount <= 0) return null
    return renderedCurrentLocator
        ?.takeIf { locator ->
            locator.chapterIndex == null || locator.chapterIndex in 0 until chapterCount
        }
        ?: fallbackCurrentLocator?.takeIf { locator ->
            locator.chapterIndex == null || locator.chapterIndex in 0 until chapterCount
        }
}
