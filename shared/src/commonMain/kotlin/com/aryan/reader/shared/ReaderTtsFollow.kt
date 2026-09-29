package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderReadingMode

/**
 * Android lets manual reader navigation temporarily detach from active speech.
 * The reader rejoins automatically when speech advances to a different chunk.
 */
fun shouldFollowReaderTtsChunk(
    detachedChunkIndex: Int?,
    currentChunkIndex: Int,
): Boolean = detachedChunkIndex == null || detachedChunkIndex != currentChunkIndex

enum class ReaderLifecycleAction {
    NONE,
    SAVE_POSITION,
    LOCATE_TTS,
}

fun resolveSharedTtsChunkSkipTarget(
    currentChunkIndex: Int,
    totalChunks: Int,
    direction: Int,
): Int? {
    if (totalChunks <= 0 || currentChunkIndex !in 0 until totalChunks || direction !in setOf(-1, 1)) {
        return null
    }
    return (currentChunkIndex + direction).takeIf { it in 0 until totalChunks }
}

fun resolveSharedTtsStartChunkIndex(requestedChunkIndex: Int, totalChunks: Int): Int =
    if (totalChunks <= 0) 0 else requestedChunkIndex.coerceIn(0, totalChunks - 1)

fun resolveSharedTtsTranscriptWindow(currentIndex: Int, chunkCount: Int): Pair<Int, IntRange> {
    if (chunkCount <= 0) return 0 to IntRange.EMPTY
    val center = currentIndex.coerceIn(0, chunkCount - 1)
    val start = (center - 2).coerceAtLeast(0)
    val end = (center + 3).coerceAtMost(chunkCount - 1)
    return start to (start..end)
}

fun readerLifecycleAction(
    isActive: Boolean,
    isTtsActive: Boolean,
    detachedChunkIndex: Int?,
    currentChunkIndex: Int?,
): ReaderLifecycleAction = when {
    !isActive -> ReaderLifecycleAction.SAVE_POSITION
    isTtsActive &&
        currentChunkIndex != null &&
        shouldFollowReaderTtsChunk(detachedChunkIndex, currentChunkIndex) -> ReaderLifecycleAction.LOCATE_TTS
    else -> ReaderLifecycleAction.NONE
}

/**
 * WebView readers apply the navigation script (which carries the TTS locator)
 * only on a new request ID. A TTS stop clears the chunk without navigating,
 * so without one final request the page keeps painting the last chunk's
 * highlight after the session ends. Native renderers clear via recomposition
 * and never need this — only the vertical WebView branch does.
 */
fun shouldRefreshReaderNavigationOnTtsSessionEnd(
    sessionWasActive: Boolean,
    sessionIsActive: Boolean,
    readingMode: ReaderReadingMode,
    useNativeVerticalRenderer: Boolean,
): Boolean = sessionWasActive &&
    !sessionIsActive &&
    readingMode == ReaderReadingMode.VERTICAL &&
    !useNativeVerticalRenderer
