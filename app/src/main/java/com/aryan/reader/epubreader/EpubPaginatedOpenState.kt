package com.aryan.reader.epubreader

internal const val TAG_EPUB_PAGINATED_OPEN_DIAG = "EpubPaginatedOpenDiag"

internal fun shouldSavePaginatedOpenPosition(
    isPaginatedMode: Boolean,
    hasPaginator: Boolean,
    isPagerInitialized: Boolean,
    isReconfigurationRestoring: Boolean,
    pageCount: Int,
    pageToSave: Int
): Boolean = com.aryan.reader.shared.reader.shouldSaveSharedPaginatedOpenPosition(
    isPaginatedMode = isPaginatedMode,
    hasPaginator = hasPaginator,
    isPagerInitialized = isPagerInitialized,
    isReconfigurationRestoring = isReconfigurationRestoring,
    pageCount = pageCount,
    pageToSave = pageToSave,
)
