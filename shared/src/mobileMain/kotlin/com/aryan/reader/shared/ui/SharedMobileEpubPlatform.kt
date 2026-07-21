package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.reader.SharedEpubBook

internal data class SharedMobileEpubLoadState(
    val isLoading: Boolean = true,
    val book: SharedEpubBook? = null,
    val errorMessage: String? = null
)

@Composable
internal expect fun rememberSharedMobileEpubLoadState(book: BookItem): SharedMobileEpubLoadState

@Composable
internal expect fun SharedMobileEpubWebView(
    html: String,
    contentChunks: List<String>,
    appearanceScript: String,
    navigationScript: String?,
    navigationRequestId: Long,
    onBridgeMessage: (method: String, payload: String) -> Unit,
    modifier: Modifier = Modifier
)

internal expect fun openSharedMobileEpubExternalLink(url: String): Boolean

/** iOS currently exposes device speech only; cloud TTS stays out of the shared mobile reader. */
internal enum class SharedMobileEpubLocalTtsState { IDLE, SPEAKING, PAUSED }

internal interface SharedMobileEpubLocalTts {
    val state: SharedMobileEpubLocalTtsState
    /** Remains true while moving between document pages, even when no utterance is active. */
    val isSessionActive: Boolean
    /** The shared planner's active chunk, used to keep the reader in sync with speech. */
    val progress: ReaderTtsProgress
    val speechRate: Float
    val speechPitch: Float
    /** Increments only when every chunk finishes naturally; explicit stop does not increment it. */
    val completionCount: Long
    /** Starts platform audio preparation while document text is still being extracted. */
    fun prepare()
    fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        startChunkIndex: Int = 0,
        playWhenReady: Boolean = true
    )
    fun pause()
    fun resume()
    fun skipPrevious()
    fun skipNext()
    fun setSpeechParameters(rate: Float, pitch: Float)
    fun stop()
}

@Composable
internal expect fun rememberSharedMobileEpubLocalTts(): SharedMobileEpubLocalTts
