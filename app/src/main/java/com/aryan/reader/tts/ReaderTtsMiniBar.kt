package com.aryan.reader.tts

import com.aryan.reader.shared.SharedReaderTtsMiniBarState

/**
 * Projects the playback state onto the shared mini-bar contract.
 *
 * The bar itself renders in shared (`SharedReaderTtsMiniBar`); only this
 * mapping stays platform-side because [TtsPlaybackManager.TtsState] is
 * Android's Media3-backed model.
 */
internal fun TtsPlaybackManager.TtsState.toSharedReaderTtsMiniBarState(): SharedReaderTtsMiniBarState =
    SharedReaderTtsMiniBarState(
        bookId = bookId,
        bookTitle = bookTitle,
        chapterTitle = chapterTitle,
        chunkIndex = currentChunkIndex,
        totalChunks = totalChunks,
        currentText = currentText,
        isLoading = isLoading,
        isPlaying = isPlaying,
        sessionEndedByStop = sessionEndedByStop,
        sessionFinished = sessionFinished,
        playbackSource = playbackSource.orEmpty()
    )
