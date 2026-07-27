package com.aryan.reader.shared

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
