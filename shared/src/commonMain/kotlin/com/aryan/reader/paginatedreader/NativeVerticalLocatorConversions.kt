package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.ReaderLocator

/**
 * Centralised conversions between the two cursors (B1 axis B).
 *
 * `ReaderNavigationTarget` (aka Android `Locator`) is the pagination-internal
 * cursor: chapter + block + char offset, nothing else. `ReaderLocator` is the
 * annotation/external cursor: ten optional fields covering chapter, page,
 * offsets, block position, quote and CFI. Both stay — merging them would force
 * pagination internals to carry annotation payload and vice versa.
 *
 * This pair single-sources the field mapping so the two directions cannot
 * drift. Android's `LocatorConverter` keeps its book/CFI parsing (it needs
 * Room, ProtoBuf and on-demand HTML); it converts *to* these cursors, not
 * between them.
 */

/**
 * Expands a pagination cursor into the canonical locator. The three
 * pagination fields always survive; page/offsets/quote/CFI ride along only
 * when the caller knows them (e.g. shared's `ReaderPage.toNativeReaderLocator`
 * supplies page + offsets + quote + CFI, Android's CFI resolution supplies
 * the CFI only).
 */
fun ReaderNavigationTarget.toNativeReaderLocator(
    pageIndex: Int? = null,
    startOffset: Int? = null,
    endOffset: Int? = null,
    textQuote: String? = null,
    cfi: String? = null,
): ReaderLocator = buildNativeReaderLocator(
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    startOffset = startOffset,
    endOffset = endOffset,
    blockIndex = blockIndex,
    charOffset = charOffset,
    textQuote = textQuote,
    cfi = cfi,
)

/**
 * Field assignment for pagination → canonical. Nullable block position is
 * preserved as null (an empty page names no block), so `withFallbacks`
 * callers can still fill it from the flow item's block.
 */
fun buildNativeReaderLocator(
    chapterIndex: Int?,
    pageIndex: Int? = null,
    startOffset: Int? = null,
    endOffset: Int? = null,
    blockIndex: Int? = null,
    charOffset: Int? = null,
    textQuote: String? = null,
    cfi: String? = null,
): ReaderLocator = ReaderLocator(
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    startOffset = startOffset,
    endOffset = endOffset,
    blockIndex = blockIndex,
    charOffset = charOffset,
    textQuote = textQuote,
    cfi = cfi,
)

/**
 * Collapses a canonical locator back to the pagination cursor.
 * Returns null when there is no chapter (a locator without one names no
 * paginated position); block defaults to 0 and the offset prefers the block
 * position over the page range, matching
 * `nativeVerticalNavigationTargetForItem`'s `(chapter, 0, 0)` fallback shape.
 */
fun ReaderLocator.toNativeVerticalTarget(): ReaderNavigationTarget? {
    val chapter = chapterIndex ?: return null
    return ReaderNavigationTarget(
        chapterIndex = chapter,
        blockIndex = blockIndex ?: 0,
        charOffset = charOffset ?: startOffset ?: 0,
    )
}
