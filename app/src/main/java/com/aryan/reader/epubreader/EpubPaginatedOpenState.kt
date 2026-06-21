package com.aryan.reader.epubreader

internal const val TAG_EPUB_PAGINATED_OPEN_DIAG = "EpubPaginatedOpenDiag"

internal fun shouldSavePaginatedOpenPosition(
    isPaginatedMode: Boolean,
    hasPaginator: Boolean,
    isPagerInitialized: Boolean,
    isReconfigurationRestoring: Boolean,
    pageCount: Int,
    pageToSave: Int
): Boolean {
    return isPaginatedMode &&
        hasPaginator &&
        isPagerInitialized &&
        !isReconfigurationRestoring &&
        pageCount > 0 &&
        pageToSave in 0 until pageCount
}
