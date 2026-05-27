package com.aryan.reader.tts

enum class ReaderTtsOverlaySize {
    LARGE,
    MEDIUM,
    SMALL
}

internal fun nextReaderTtsOverlaySize(current: ReaderTtsOverlaySize): ReaderTtsOverlaySize {
    return when (current) {
        ReaderTtsOverlaySize.LARGE -> ReaderTtsOverlaySize.MEDIUM
        ReaderTtsOverlaySize.MEDIUM -> ReaderTtsOverlaySize.SMALL
        ReaderTtsOverlaySize.SMALL -> ReaderTtsOverlaySize.LARGE
    }
}

internal fun readerTtsOverlayAlignmentBias(size: ReaderTtsOverlaySize): Float {
    return if (size == ReaderTtsOverlaySize.SMALL) 1f else 0f
}

internal fun formatReaderTtsChunkLabel(currentChunkIndex: Int, totalChunks: Int): String? {
    if (totalChunks <= 0) return null
    if (currentChunkIndex !in 0 until totalChunks) return null
    return "Chunk ${currentChunkIndex + 1}/$totalChunks"
}
