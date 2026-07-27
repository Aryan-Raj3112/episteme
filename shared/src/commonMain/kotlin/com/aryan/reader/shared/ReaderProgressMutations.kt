package com.aryan.reader.shared

fun BookItem.withPdfReadingProgress(
    pageIndex: Int,
    progressPercentage: Float,
    modifiedAt: Long = currentTimestamp(),
): BookItem {
    val normalizedProgress = progressPercentage.coerceIn(0f, 100f)
    if (lastPageIndex == pageIndex && this.progressPercentage == normalizedProgress) {
        return this
    }
    return copy(
        lastPageIndex = pageIndex,
        progressPercentage = normalizedProgress,
        readingPositionModifiedTimestamp = modifiedAt,
    )
}

fun BookItem.withReaderSessionState(
    session: BookItem,
    modifiedAt: Long = currentTimestamp(),
): BookItem {
    val readingStateIsUnchanged =
        progressPercentage == session.progressPercentage &&
            lastPageIndex == session.lastPageIndex &&
            readerPosition == session.readerPosition &&
            readerSettings == session.readerSettings &&
            readerFormatIsLocal == session.readerFormatIsLocal &&
            readerLocalFormatSettings == session.readerLocalFormatSettings &&
            readerAutoScrollIsLocal == session.readerAutoScrollIsLocal &&
            readerAutoScrollLocalSpeed == session.readerAutoScrollLocalSpeed &&
            readerBookmarks == session.readerBookmarks &&
            readerHighlights == session.readerHighlights
    if (readingStateIsUnchanged) return this
    return copy(
        progressPercentage = session.progressPercentage,
        lastPageIndex = session.lastPageIndex,
        readerPosition = session.readerPosition,
        readerSettings = session.readerSettings,
        readerFormatIsLocal = session.readerFormatIsLocal,
        readerLocalFormatSettings = session.readerLocalFormatSettings,
        readerAutoScrollIsLocal = session.readerAutoScrollIsLocal,
        readerAutoScrollLocalSpeed = session.readerAutoScrollLocalSpeed,
        readerBookmarks = session.readerBookmarks,
        readerHighlights = session.readerHighlights,
        readingPositionModifiedTimestamp = modifiedAt,
    )
}
