package com.aryan.reader.shared

/**
 * Applies a crash-recovery reader snapshot only when its reading clock is newer.
 * Library metadata is never sourced from this session-only record.
 */
fun BookItem.withNewerReaderSession(recovery: BookItem): BookItem {
    val legacyRecoveryHasState = recovery.readingPositionModifiedTimestamp == 0L &&
        readingPositionModifiedTimestamp == 0L &&
        !hasReaderSessionState() &&
        recovery.hasReaderSessionState()
    if (recovery.readingPositionModifiedTimestamp <= readingPositionModifiedTimestamp &&
        !legacyRecoveryHasState
    ) return this
    return copy(
        progressPercentage = recovery.progressPercentage,
        lastPageIndex = recovery.lastPageIndex,
        readerPosition = recovery.readerPosition,
        readerSettings = recovery.readerSettings,
        readerFormatIsLocal = recovery.readerFormatIsLocal,
        readerLocalFormatSettings = recovery.readerLocalFormatSettings,
        readerAutoScrollIsLocal = recovery.readerAutoScrollIsLocal,
        readerAutoScrollLocalSpeed = recovery.readerAutoScrollLocalSpeed,
        readerBookmarks = recovery.readerBookmarks,
        readerHighlights = recovery.readerHighlights,
        readingPositionModifiedTimestamp = recovery.readingPositionModifiedTimestamp,
    )
}

private fun BookItem.hasReaderSessionState(): Boolean {
    return progressPercentage != null ||
        lastPageIndex != null ||
        readerPosition != null ||
        readerSettings != null ||
        readerBookmarks.isNotEmpty() ||
        readerHighlights.isNotEmpty()
}
