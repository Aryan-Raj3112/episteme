package com.aryan.reader.shared

/**
 * App-level reader TTS mini bar state. Mirrors Android's [TtsState] subset
 * used by `ReaderTtsMiniBar` (`app/.../tts/ReaderTtsMiniBar.kt`).
 *
 * Android is the benchmark and stays untouched; this shared contract lets the
 * iOS host show the same global bar when the user leaves the reader with an
 * active read-aloud session.
 */
data class SharedReaderTtsMiniBarState(
    val bookId: String? = null,
    val bookTitle: String? = null,
    val chapterTitle: String? = null,
    val chunkIndex: Int = -1,
    val totalChunks: Int = 0,
    val currentText: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val sessionEndedByStop: Boolean = false,
    val sessionFinished: Boolean = false,
    /** Android `playbackSource`: only `"READER"` sessions drive the mini bar. */
    val playbackSource: String = "READER"
)

/**
 * Android `shouldShowReaderTtsMiniBar` parity
 * (`ReaderTtsMiniBar.kt:43-51`): never on a reader route, only `READER`
 * sessions that haven't ended, with loading or non-blank text.
 */
fun shouldShowSharedReaderTtsMiniBar(
    state: SharedReaderTtsMiniBarState?,
    isOnReaderRoute: Boolean
): Boolean {
    if (state == null) return false
    if (isOnReaderRoute) return false
    if (state.playbackSource != "READER") return false
    if (state.sessionEndedByStop || state.sessionFinished) return false
    return state.isLoading || !state.currentText.isNullOrBlank()
}

/** Android `readerTtsMiniBarBottomPaddingDp` parity (`ReaderTtsMiniBar.kt:53-59`). */
fun sharedReaderTtsMiniBarBottomPaddingDp(isOnMainRoute: Boolean): Int =
    if (isOnMainRoute) 96 else 16

fun SharedReaderTtsMiniBarState.chunkLabel(): String =
    if (chunkIndex >= 0 && totalChunks > 0) "Chunk ${chunkIndex + 1} of $totalChunks" else ""

fun SharedReaderTtsMiniBarState.subtitle(): String =
    listOf(chunkLabel(), chapterTitle.orEmpty()).filter { it.isNotBlank() }.joinToString(" - ")
