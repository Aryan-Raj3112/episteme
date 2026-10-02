package com.aryan.reader.shared.ios

import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTts
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import com.aryan.reader.shared.ui.SharedMobileEpubVoice

/**
 * Exposes the audiobook Listen controller through the shared local-TTS interface so the shared
 * TTS voice-settings panels (device voices, and later the cloud panels) can be reused for
 * audiobooks instead of a second iOS-only settings UI.
 *
 * Android benchmark: the audiobook player sheet exposes `onOpenVoiceSettings`, which opens the
 * same voice settings the reader uses, with Listen keeping its own voice choice. Playback is
 * *not* driven through this interface — the Listen controller keeps owning it — so the transport
 * members below deliberately refuse to start a session and the settings panels (the only
 * consumer) never call them.
 */
internal class IosListenLocalTtsAdapter(
    private val controller: IosBookTtsListeningController
) : SharedMobileEpubLocalTts {

    override val state: SharedMobileEpubLocalTtsState
        get() = when {
            controller.state.isPlaying -> SharedMobileEpubLocalTtsState.SPEAKING
            controller.state.connected -> SharedMobileEpubLocalTtsState.PAUSED
            else -> SharedMobileEpubLocalTtsState.IDLE
        }

    override val isSessionActive: Boolean
        get() = controller.state.connected

    /**
     * Listen's player sheet closes as soon as playback stops, so requiring a full stop to reach
     * the voice settings makes the voice unreachable. Pausing is enough: nothing is being
     * spoken, so the rows, the language filter and preview are all safe to use.
     */
    override val isVoiceSelectionLocked: Boolean
        get() = controller.state.isPlaying || controller.state.isLoading

    /**
     * The shared planner's chunk view. Listen indexes chunks per chapter, so only the current
     * chapter's window is meaningful here.
     */
    override val progress: ReaderTtsProgress
        get() = ReaderTtsProgress(
            sessionId = controller.state.chapterIndex.toLong(),
            currentChunkIndex = controller.state.chunkIndex
        )

    override val speechRate: Float get() = controller.state.speechRate
    override val speechPitch: Float get() = controller.state.pitch
    override val previewSampleText: String get() = controller.previewSampleText
    override val availableVoices: List<SharedMobileEpubVoice> get() = controller.availableVoices
    override val selectedVoiceIdentifier: String? get() = controller.selectedVoiceIdentifier
    override val favoriteVoiceIdentifiers: Set<String> get() = controller.favoriteVoiceIdentifiers
    override val errorMessage: String? get() = controller.state.error
    override val completionCount: Long
        get() = controller.progressByBook.values.count { it.completed }.toLong()
    override val playbackSource: String? get() = controller.enginePlaybackSource
    override val sessionBookId: String? get() = controller.state.bookId
    override val sessionTotalChapters: Int get() = controller.state.chapterCount
    override val currentSpokenOffset: Int get() = controller.currentSpokenOffset

    // The voice settings panels only read state and drive voice selection/preview. Refusing the
    // transport members keeps a future caller from silently fighting the Listen controller for
    // the synthesizer instead of failing loudly.
    private fun unsupported(operation: String): Nothing =
        throw UnsupportedOperationException(
            "IosListenLocalTtsAdapter.$operation: audiobook playback is owned by " +
                "IosBookTtsListeningController, not the shared local-TTS interface."
        )

    override fun prepare() = unsupported("prepare")

    override fun start(
        chunks: List<com.aryan.reader.shared.ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean,
        playbackSource: String?,
        totalChapters: Int,
        continueSession: Boolean,
        authToken: String?,
    ) = unsupported("start")

    override fun pause() = unsupported("pause")
    override fun resume() = unsupported("resume")
    override fun skipPrevious() = unsupported("skipPrevious")
    override fun skipNext() = unsupported("skipNext")

    override fun setSpeechParameters(rate: Float, pitch: Float) =
        controller.setParameters(rate, pitch)

    override fun setPreviewSampleText(text: String) = controller.setPreviewSampleText(text)
    override fun toggleFavoriteVoice(identifier: String) = controller.toggleFavoriteVoice(identifier)
    override fun setVoice(identifier: String?) = controller.setVoice(identifier)
    override fun previewVoice(identifier: String?) = controller.previewVoice(identifier)

    /** Stops only the preview utterance; playback is the controller's business. */
    override fun stop() = controller.stopVoicePreview()
}
