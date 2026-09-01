package com.aryan.reader.pdf

import com.aryan.reader.shared.pdf.SharedPdfAnnotationSessionPhase
import com.aryan.reader.shared.pdf.SharedPdfAnnotationSessionState
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.reader.ReaderSettings

fun resolveEraserStrokeWidth(
    isEraserOverride: Boolean,
    activeToolThickness: Float,
    eraserToolThickness: Float,
): Float = if (isEraserOverride) eraserToolThickness else activeToolThickness

fun canUsePdfSidecarsForBook(
    activeBookId: String?,
    loadedSidecarBookId: String?,
    areSidecarsLoaded: Boolean,
): Boolean = SharedPdfAnnotationSessionState(
    bookId = loadedSidecarBookId,
    phase = if (areSidecarsLoaded) SharedPdfAnnotationSessionPhase.READY else SharedPdfAnnotationSessionPhase.EMPTY,
).canUseFor(activeBookId)

fun canManagePdfVirtualPages(
    isDocumentReady: Boolean,
    currentBookId: String?,
    loadedPageLayoutBookId: String?,
    virtualPageCount: Int,
): Boolean = isDocumentReady && currentBookId != null &&
    loadedPageLayoutBookId == currentBookId && virtualPageCount > 0

data class SharedPdfDocumentOpenBookPlan(
    val bookId: String,
    /** Non-null when legacy fast-id sidecars should be migrated into [bookId]. */
    val migrationTargetBookId: String?,
    val shouldResetSidecarState: Boolean,
) {
    val shouldMigrateLegacyBookId: Boolean
        get() = migrationTargetBookId != null
}

/**
 * Resolves the reader book id for a document-open pass and whether sidecar
 * (annotation) state may be reset.
 *
 * [shouldResetSidecarState] is false when the pass resolves the book the
 * reader already loaded. Re-opening the same book (split panes re-run the
 * open effect, password unlocks restart it) must not reset sidecar state:
 * the sidecar load only restarts when the book id changes, so a reset would
 * strand the session non-ready and committed ink strokes would vanish.
 */
fun sharedPdfDocumentOpenBookPlan(
    currentBookId: String?,
    fastId: String,
    selectedBookId: String?,
): SharedPdfDocumentOpenBookPlan {
    val shouldUseSelectedId = selectedBookId != null && selectedBookId != fastId
    val bookId = if (shouldUseSelectedId) selectedBookId!! else fastId
    return SharedPdfDocumentOpenBookPlan(
        bookId = bookId,
        migrationTargetBookId = if (shouldUseSelectedId) selectedBookId else null,
        shouldResetSidecarState = currentBookId != bookId,
    )
}

fun pdfPageToPersist(
    initialRestorationComplete: Boolean,
    currentPage: Int,
    pendingRestorePage: Int?,
): Int = if (initialRestorationComplete) currentPage else pendingRestorePage ?: 0

fun shouldApplyPdfTextDockImePadding(
    layoutHeightPx: Int,
    windowHeightPx: Int,
    imeHeightPx: Int,
): Boolean {
    if (imeHeightPx <= 0) return false
    if (layoutHeightPx <= 0 || windowHeightPx <= 0) return true
    return layoutHeightPx + imeHeightPx > windowHeightPx
}

fun pdfTouchpadScrollTargetPanY(
    currentPanY: Float,
    scrollDeltaY: Float,
    scrollStepPx: Float,
    minPanY: Float,
    maxPanY: Float,
): Float = (currentPanY - (scrollDeltaY * scrollStepPx)).coerceIn(minPanY, maxPanY)

fun sharedCurrentPageScaleAfterPdfPageChange(
    isPaginationMode: Boolean,
    isScrollLocked: Boolean,
    lockedState: Triple<Float, Float, Float>?,
    currentActiveScale: Float,
): Float = if (isPaginationMode && isScrollLocked) lockedState?.first ?: currentActiveScale else 1f

fun sharedPdfPageRangeText(
    pageIndex: Int,
    pageCount: Int,
    isPaginationMode: Boolean,
    settings: ReaderSettings,
): String = "${sharedPdfPageRange(pageIndex, pageCount, isPaginationMode, settings)}/$pageCount"

fun sharedPdfPageRangeLabel(
    pageIndex: Int,
    pageCount: Int,
    isPaginationMode: Boolean,
    settings: ReaderSettings,
): String = "${sharedPdfPageRange(pageIndex, pageCount, isPaginationMode, settings)}/$pageCount"

private fun sharedPdfPageRange(
    pageIndex: Int,
    pageCount: Int,
    isPaginationMode: Boolean,
    settings: ReaderSettings,
): String = if (isPaginationMode) {
    PdfSpreadLayout.pageRangeLabel(pageIndex, pageCount, settings)
} else {
    "${pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) + 1}"
}
