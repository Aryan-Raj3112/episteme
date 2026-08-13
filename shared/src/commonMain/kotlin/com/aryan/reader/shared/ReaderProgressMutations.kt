package com.aryan.reader.shared

fun pdfReadingProgressPercentage(pageIndex: Int, totalPages: Int): Float {
    return if (totalPages > 0) {
        ((pageIndex + 1).toFloat() / totalPages.toFloat()) * 100f
    } else {
        0f
    }
}

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
            readerAutoScrollLocalMinSpeed == session.readerAutoScrollLocalMinSpeed &&
            readerAutoScrollLocalMaxSpeed == session.readerAutoScrollLocalMaxSpeed &&
            pdfAutoScrollIsLocal == session.pdfAutoScrollIsLocal &&
            pdfAutoScrollLocalSpeed == session.pdfAutoScrollLocalSpeed &&
            pdfAutoScrollLocalMinSpeed == session.pdfAutoScrollLocalMinSpeed &&
            pdfAutoScrollLocalMaxSpeed == session.pdfAutoScrollLocalMaxSpeed &&
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
        readerAutoScrollLocalMinSpeed = session.readerAutoScrollLocalMinSpeed,
        readerAutoScrollLocalMaxSpeed = session.readerAutoScrollLocalMaxSpeed,
        pdfAutoScrollIsLocal = session.pdfAutoScrollIsLocal,
        pdfAutoScrollLocalSpeed = session.pdfAutoScrollLocalSpeed,
        pdfAutoScrollLocalMinSpeed = session.pdfAutoScrollLocalMinSpeed,
        pdfAutoScrollLocalMaxSpeed = session.pdfAutoScrollLocalMaxSpeed,
        readerBookmarks = session.readerBookmarks,
        readerHighlights = session.readerHighlights,
        readingPositionModifiedTimestamp = modifiedAt,
    )
}
