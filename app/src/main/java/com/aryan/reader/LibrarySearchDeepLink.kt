package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.search.SharedGlobalSearchHit

/**
 * Pure mapping for library inside-files deep-links ([MainViewModel.openLibrarySearchHit]).
 * Kept free of stores/scopes so it stays unit-testable; the ViewModel only
 * resolves the URI and forwards these params to `openBook`.
 */

/** EPUB landing chapter: hit chapter, else resume, else book start. */
internal fun libraryHitEpubTargetChapter(hit: SharedGlobalSearchHit, resumeChapter: Int?): Int =
    (hit.chapterIndex ?: resumeChapter ?: 0).coerceAtLeast(0)

/**
 * PDF landing page: native page, else slide order for PPTX (slides index on
 * the chapter axis but open in the PDF viewer), else resume. Null keeps the
 * reader's normal resume behavior.
 */
internal fun libraryHitPdfTargetPage(hit: SharedGlobalSearchHit, resumePage: Int?): Int? =
    hit.pageIndex ?: hit.chapterIndex ?: resumePage

/** Saved EPUB position for the jump-history seed; null skips the seed. */
internal fun libraryHitEpubOrigin(recent: RecentFileItem?): SharedReaderLocator? {
    if (recent?.lastChapterIndex == null && recent?.lastPositionCfi == null) return null
    return SharedReaderLocator(
        chapterIndex = recent?.lastChapterIndex,
        blockIndex = recent?.locatorBlockIndex,
        charOffset = recent?.locatorCharOffset,
        cfi = recent?.lastPositionCfi,
    )
}
