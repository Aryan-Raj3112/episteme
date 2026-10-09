package com.aryan.reader.epubreader

internal const val TAG_EPUB_VERTICAL_OPEN_DIAG = "EpubVerticalOpenDiag"

internal const val VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS =
    com.aryan.reader.shared.reader.SHARED_VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS

internal fun shouldSaveVerticalOpenPosition(
    isVerticalMode: Boolean,
    hasWebView: Boolean,
    isChapterReady: Boolean,
    isRestoreSettled: Boolean
): Boolean = com.aryan.reader.shared.reader.shouldSaveSharedVerticalOpenPosition(
    isVerticalMode = isVerticalMode,
    hasWebView = hasWebView,
    isChapterReady = isChapterReady,
    isRestoreSettled = isRestoreSettled,
)

internal fun resolveVerticalRestoreSettled(
    scrollFinishedReported: Boolean,
    restoreStartedAtMillis: Long,
    nowMillis: Long
): Boolean {
    if (scrollFinishedReported) return true
    if (restoreStartedAtMillis <= 0L) return true
    return com.aryan.reader.shared.reader.resolveSharedVerticalRestoreSettled(
        scrollFinishedReported = false,
        elapsedMillis = nowMillis - restoreStartedAtMillis,
        timeoutMillis = VERTICAL_RESTORE_SETTLE_TIMEOUT_MILLIS,
    )
}
