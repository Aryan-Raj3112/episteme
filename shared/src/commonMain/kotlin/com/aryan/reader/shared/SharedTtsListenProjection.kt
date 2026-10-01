package com.aryan.reader.shared

/**
 * Engine-agnostic snapshot of a TTS session, used to project engine state into the
 * audiobook Listen UI.
 *
 * Android benchmark: `TtsPlaybackManager.TtsState` → `toSharedBookTtsListenState`
 * (`audiobook/BookTtsListening.kt`). That projector was Android-only, which is why iOS
 * Listen had to grow its own parallel state machine instead of reusing the engine. This
 * snapshot is the shared input both platforms now project from, so there is exactly one
 * implementation of the mapping.
 */
data class SharedTtsPlaybackSnapshot(
    /** Null when no session; the engine's own surface tag otherwise. */
    val playbackSource: String? = null,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val chapterTitle: String? = null,
    val chapterIndex: Int? = null,
    val totalChapters: Int? = null,
    val currentChunkIndex: Int = -1,
    val totalChunks: Int = 0,
    /** Engine-reported whole-book percentage, when it tracks one. */
    val bookProgressPercent: Int? = null,
    val sessionFinished: Boolean = false,
    val sessionEndedByStop: Boolean = false,
    val errorMessage: String? = null,
    val transcriptStartIndex: Int = 0,
    val transcriptChunks: List<String> = emptyList(),
)

/** Surface tag for a session started by the audiobook Listen feature. */
const val SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK: String = "AUDIOBOOK_TTS"

/** Surface tag for a session started by the in-book reader. */
const val SHARED_TTS_PLAYBACK_SOURCE_READER: String = "READER"

/** Saved Listen session parameters, as stored per book. */
data class SharedTtsListenSavedProgress(
    val chapterIndex: Int? = null,
    val speechRate: Float? = null,
    val pitch: Float? = null,
)

/**
 * Projects an engine session snapshot into the audiobook Listen state.
 *
 * Android benchmark (`toSharedBookTtsListenState`). `isBookListening` gates every live
 * field: a reader session streaming through the same engine must not make the Listen UI
 * look connected, which is the whole reason `playbackSource` exists.
 *
 * @param progress the saved per-book Listen progress, for rate/pitch and the chapter
 *   fallback when the engine has not reported one yet.
 * @param preparedChapterCount chapter count from the prepared book, used when the engine
 *   reports none (a session that has not started).
 * @param sleepTimerRemainingMs remaining sleep-timer time; 0 when no timer is running.
 */
fun SharedTtsPlaybackSnapshot.toSharedBookTtsListenState(
    progress: SharedTtsListenSavedProgress? = null,
    preparedChapterCount: Int = 0,
    sleepTimerRemainingMs: Long = 0L,
): SharedBookTtsListenState {
    val isBookListening = playbackSource == SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK
    val resolvedChapterCount = (totalChapters ?: preparedChapterCount).coerceAtLeast(0)
    val resolvedChapterIndex = (chapterIndex ?: progress?.chapterIndex ?: 0).coerceAtLeast(0)
    val resolvedProgress = bookProgressPercent
        ?.div(100f)
        ?: calculateSharedTtsAudiobookProgress(
            chapterIndex = resolvedChapterIndex,
            chapterCount = resolvedChapterCount,
            chunkIndex = currentChunkIndex,
            chunkCount = totalChunks,
        )
    return SharedBookTtsListenState(
        connected = isBookListening,
        bookId = bookId,
        isPlaying = isBookListening && isPlaying,
        isLoading = isBookListening && isLoading,
        chapterIndex = resolvedChapterIndex,
        chapterCount = resolvedChapterCount,
        chunkIndex = currentChunkIndex,
        chunkCount = totalChunks,
        chapterTitle = chapterTitle,
        progressPercent = resolvedProgress.coerceIn(0f, 1f),
        speechRate = progress?.speechRate ?: 1f,
        pitch = progress?.pitch ?: 1f,
        sleepTimerRemainingMs = sleepTimerRemainingMs.coerceAtLeast(0L),
        sessionFinished = sessionFinished,
        sessionEndedByStop = sessionEndedByStop,
        error = errorMessage,
        transcriptStartIndex = transcriptStartIndex,
        transcriptChunks = transcriptChunks,
    )
}
