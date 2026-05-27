package com.aryan.reader.tts

import android.content.Context
import androidx.core.content.edit

enum class ReaderTtsOverlaySize {
    LARGE,
    MEDIUM,
    SMALL
}

private const val READER_PREFS_NAME = "reader_prefs"
private const val READER_TTS_OVERLAY_SIZE_KEY = "reader_tts_overlay_size"

internal fun readerTtsOverlayAlignmentBias(size: ReaderTtsOverlaySize): Float {
    return if (size == ReaderTtsOverlaySize.SMALL) 1f else 0f
}

internal fun readerTtsOverlayAlternativeSizes(size: ReaderTtsOverlaySize): List<ReaderTtsOverlaySize> {
    return ReaderTtsOverlaySize.entries.filter { it != size }
}

internal fun resolveReaderTtsOverlaySize(savedName: String?): ReaderTtsOverlaySize {
    return ReaderTtsOverlaySize.entries.firstOrNull { it.name == savedName }
        ?: ReaderTtsOverlaySize.LARGE
}

internal fun loadReaderTtsOverlaySize(context: Context): ReaderTtsOverlaySize {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    return resolveReaderTtsOverlaySize(prefs.getString(READER_TTS_OVERLAY_SIZE_KEY, null))
}

internal fun saveReaderTtsOverlaySize(context: Context, size: ReaderTtsOverlaySize) {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(READER_TTS_OVERLAY_SIZE_KEY, size.name) }
}

internal fun formatReaderTtsChunkLabel(currentChunkIndex: Int, totalChunks: Int): String? {
    if (totalChunks <= 0) return null
    if (currentChunkIndex !in 0 until totalChunks) return null
    return "Chunk ${currentChunkIndex + 1}/$totalChunks"
}
