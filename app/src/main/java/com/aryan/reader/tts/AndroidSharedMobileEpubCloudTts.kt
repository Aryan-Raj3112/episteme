package com.aryan.reader.tts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.isByokCloudTtsAvailable
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderTtsCacheChapter
import com.aryan.reader.shared.ReaderTtsCacheSummary
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderVoiceSampleState
import com.aryan.reader.shared.readerTtsCacheSpeakerId
import com.aryan.reader.shared.ui.SharedMobileEpubCloudTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android implementation of [SharedMobileEpubCloudTts].
 *
 * Wraps the Media3-backed [TtsController] so the shared TTS settings sheet drives cloud playback
 * on Android exactly as it does on iOS. The controller is the existing Android benchmark path —
 * this adapter only translates types and keeps the little state the interface cannot express
 * (auth token, the active book whose cache is being browsed).
 *
 * Commands are non-suspending because the interface is called from Compose callbacks; the
 * controller already marshals onto the main looper and buffers a start issued before the
 * Media3 session has connected.
 */
@UnstableApi
internal class AndroidSharedMobileEpubCloudTts(
    context: Context,
    /**
     * Live service state and the command channel for a running session, owned by the host.
     *
     * Left null — the TTS settings sheet's case, since it only browses the cache and plays samples —
     * the adapter makes a private controller that never connects and releases it with itself. A host
     * that also renders playback (a reader) passes the controller it already drives, and keeps
     * owning it: [release] only releases the private one.
     */
    private val hostController: TtsController? = null,
    private val getAuthToken: suspend () -> String? = { null },
    /**
     * Host hook for cloud voice selection. The shared sheet calls this *and* its own
     * `onCloudTtsVoiceChange`; on Android the latter is the surface's real command (Listen persists
     * a speaker of its own), so this only covers hosts that drive a controller of their own.
     */
    private val onVoiceSelected: (String) -> Unit = { voiceId -> hostController?.changeSpeaker(voiceId) },
    /** Book whose chunk cache the settings sheet browses; null falls back to the running session. */
    private val cacheBookTitle: () -> String? = { null },
    /** The voice this surface is pinned to, for the cache browser's default filter. */
    private val currentVoiceId: () -> String? = { null },
) : SharedMobileEpubCloudTts {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val ownedController: TtsController? =
        if (hostController == null) TtsController(appContext) else null

    private val controller: TtsController get() = hostController ?: ownedController!!

    /** The book a running session was started with; the cache browser's fallback title. */
    private var sessionBookTitle: String? = null

    /** The interface's cache browser is voice-scoped, so it needs a book to look files up under. */
    private fun bookTitle(): String? = cacheBookTitle() ?: sessionBookTitle

    /** The cache browser calls this voice "current": the surface's pick, else the service's. */
    private fun currentVoice(): String? = currentVoiceId() ?: ttsState.speakerId

    private var authTokenOverride: String? = null
    private var workerUrlOverride: String = ""
    private var byokSettingsOverride: ReaderAiByokSettings? = null
    private var sessionCredits: Int = 0
    private var walletMicros: Long = 0L
    private var walletMigrated: Boolean = false

    private var samplePlayer: SpeakerSamplePlayer? = null
    private var sampleCacheRevision by mutableStateOf(0)

    private var extraState by mutableStateOf(ReaderCloudTtsState())
    private val ttsState get() = controller.ttsState.value

    override val state: ReaderCloudTtsState
        get() = extraState.copy(
            isAvailable = cloudAvailable,
            isPlaying = ttsState.isPlaying,
            isLoading = ttsState.isLoading,
            isPaused = extraState.isPaused,
            statusMessage = ttsState.bookTitle,
            errorMessage = ttsState.errorMessage,
            progress = ReaderTtsProgress(
                sessionId = extraState.progress.sessionId,
                scope = ReaderTtsReadScope.CHAPTER,
                chunks = activeChunks,
                currentChunkIndex = ttsState.currentChunkIndex
            ),
            cacheSummary = cacheSummary()
        )

    private val cloudAvailable: Boolean
        get() = workerUrlOverride.isNotBlank() || isByokCloudTtsAvailable(appContext)

    /** The chunk list the running session was started with, for the shared progress model. */
    private var activeChunks: List<ReaderTtsChunk> = emptyList()

    override fun configure(
        settings: ReaderAiByokSettings,
        isSignedIn: Boolean,
        isProUser: Boolean,
        credits: Int,
        authToken: String?,
        workerUrl: String,
        walletMicros: Long,
        walletMigrated: Boolean,
    ) {
        byokSettingsOverride = settings
        sessionCredits = credits
        authTokenOverride = authToken
        workerUrlOverride = workerUrl
        this.walletMicros = walletMicros
        this.walletMigrated = walletMigrated
        extraState = extraState.copy(
            cloudSessionSpendMicros = if (walletMigrated) walletMicros else 0L
        )
    }

    override fun start(
        chunks: List<ReaderTtsChunk>,
        bookTitle: String,
        bookId: String?,
        startChunkIndex: Int,
        playWhenReady: Boolean,
        continueSession: Boolean,
        playbackSource: String?,
        totalChapters: Int,
        // Accepted for interface parity; Android Listen never reaches this adapter (it drives
        // TtsPlaybackManager with its own per-call ttsMode + speakerId), and the reader path
        // leaves both default, so both are deliberately ignored here.
        speakerId: String?,
        cloudModeAsserted: Boolean,
    ) {
        if (chunks.isEmpty()) return
        sessionBookTitle = bookTitle
        activeChunks = chunks
        scope.launch {
            val token = authTokenOverride ?: getAuthToken()
            val firstChunk = chunks.getOrNull(startChunkIndex) ?: chunks.first()
            controller.start(
                chunks = chunks.map { it.toAndroidChunk() },
                bookTitle = bookTitle,
                chapterTitle = firstChunk.chapterTitle,
                coverImageUri = null,
                bookId = bookId,
                chapterIndex = firstChunk.chapterIndex,
                startChunkIndex = startChunkIndex,
                continueSession = continueSession,
                ttsMode = TtsPlaybackManager.TtsMode.CLOUD,
                // Null means the in-book reader, matching the shared local engine.
                playbackSource = playbackSource ?: PLAYBACK_SOURCE_READER,
                totalChapters = totalChapters,
                authToken = token
            )
        }
    }

    // Read back from the shared manager, which owns the real session identity. Android benchmark:
    // `TtsState.playbackSource`.
    override val playbackSource: String? get() = ttsState.playbackSource
    override val sessionBookId: String? get() = ttsState.bookId
    override val sessionTotalChapters: Int get() = ttsState.totalChapters ?: 0

    override fun pause() {
        extraState = extraState.copy(isPaused = true)
        controller.pause()
    }

    override fun resume() {
        extraState = extraState.copy(isPaused = false)
        controller.resume()
    }

    override fun skipPrevious() = controller.skipToPreviousChunk()

    override fun skipNext() = controller.skipToNextChunk()

    override fun setVoice(identifier: String) = onVoiceSelected(identifier)

    override fun clearCache() {
        val bookTitle = bookTitle() ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                TtsCacheManager(appContext).clearBookCache(bookTitle)
            }
            sampleCacheRevision++
        }
    }

    override fun stop() {
        extraState = extraState.copy(isPaused = false, progress = ReaderTtsProgress())
        activeChunks = emptyList()
        controller.stop()
    }

    override fun release() {
        samplePlayer?.release()
        samplePlayer = null
        // A host-supplied controller belongs to the surface that drives it.
        ownedController?.release()
        scope.cancel()
    }

    // --- cache browser (Android TtsCacheTab parity) ---

    private fun cacheManager() = TtsCacheManager(appContext)

    /**
     * Voice id for a cached chunk file. Uses the shared `TtsCacheManager` file-name parser so
     * this stays in step with the desktop/other readers instead of re-deriving the layout.
     */
    private fun cachedVoiceId(fileName: String): String? = readerTtsCacheSpeakerId(fileName)

    override fun cachedChapterVoices(): List<String> {
        val bookTitle = bookTitle() ?: return emptyList()
        val pinVoice = currentVoice()
        val fromCache = cacheManager().getBookCacheDir(bookTitle).listFiles().orEmpty()
            .flatMap { chapterDir: java.io.File -> chapterDir.listFiles().orEmpty().asIterable() }
            .mapNotNull { file -> cachedVoiceId(file.name) }
            .distinct()
            .sorted()
        return (listOfNotNull(pinVoice) + fromCache).distinct()
    }

    override fun cachedChapters(voiceId: String): List<ReaderTtsCacheChapter> {
        val bookTitle = bookTitle() ?: return emptyList()
        return cacheManager().getChapterCaches(bookTitle, voiceId).map { chapter ->
            ReaderTtsCacheChapter(
                bookTitle = bookTitle,
                chapterTitle = chapter.chapterTitle,
                voiceId = voiceId,
                chunkCount = chapter.chunkCount,
                sizeBytes = chapter.sizeBytes,
                entryKey = chapter.directory.absolutePath
            )
        }
    }

    override fun deleteCachedChapter(chapter: ReaderTtsCacheChapter) {
        val bookTitle = bookTitle() ?: return
        val cache = cacheManager()
        val match = cache.getChapterCaches(bookTitle, chapter.voiceId)
            .firstOrNull { it.directory.absolutePath == chapter.entryKey } ?: return
        scope.launch {
            withContext(Dispatchers.IO) { cache.deleteSpecificFiles(match.matchingFiles, match.directory) }
            sampleCacheRevision++
        }
    }

    override fun deleteCachedVoice(voiceId: String) {
        val bookTitle = bookTitle() ?: return
        val cache = cacheManager()
        scope.launch {
            withContext(Dispatchers.IO) {
                cache.getChapterCaches(bookTitle, voiceId).forEach { chapter ->
                    cache.deleteSpecificFiles(chapter.matchingFiles, chapter.directory)
                }
            }
            sampleCacheRevision++
        }
    }

    private fun cacheSummary(): ReaderTtsCacheSummary {
        val bookTitle = bookTitle() ?: return ReaderTtsCacheSummary()
        val pinVoice = currentVoice()
        val all = cacheManager().getChapterCaches(bookTitle)
        val forCurrentVoice = all.filter { chapter ->
            chapter.matchingFiles.any { file -> cachedVoiceId(file.name) == pinVoice }
        }
        return ReaderTtsCacheSummary(
            cachedChapterCount = all.size,
            cachedChunkCount = all.sumOf { it.chunkCount },
            currentVoiceChunkCount = forCurrentVoice.sumOf { it.chunkCount },
            totalSizeBytes = all.sumOf { it.sizeBytes },
            currentVoiceSizeBytes = forCurrentVoice.sumOf { it.sizeBytes }
        )
    }

    // --- voice samples (Android SpeakerSamplePlayer parity) ---

    private fun requireSamplePlayer(): SpeakerSamplePlayer =
        samplePlayer ?: SpeakerSamplePlayer(appContext, scope, getAuthToken).also { samplePlayer = it }

    override val voiceSampleState: ReaderVoiceSampleState
        get() {
            // Read the revision so the snapshot re-composes when a delete mutates the list.
            sampleCacheRevision
            val player = samplePlayer
            return ReaderVoiceSampleState(
                loadingVoiceId = player?.loadingSpeakerId,
                playingVoiceId = player?.playingSpeakerId,
                cachedVoiceIds = player?.cachedSpeakers?.toSet().orEmpty()
            )
        }

    override fun playOrStopVoiceSample(
        voiceId: String,
        fishReferenceId: String?,
        sampleAudioUrl: String?,
        sampleText: String?,
    ) {
        val player = requireSamplePlayer()
        if (fishReferenceId != null) {
            scope.launch {
                player.playFishSample(
                    voiceRef = fishReferenceId,
                    displayId = voiceId,
                    sampleText = sampleText.orEmpty(),
                    workerBaseUrl = workerUrlOverride.ifBlank { null },
                    authToken = authTokenOverride ?: getAuthToken(),
                    fishByokKey = byokSettingsOverride?.fishKey,
                    sampleAudioUrl = sampleAudioUrl
                )
            }
        } else {
            player.playOrStop(voiceId)
        }
    }

    override fun clearVoiceSamples() {
        val player = requireSamplePlayer()
        player.clearSamples()
        sampleCacheRevision++
    }

    private fun ReaderTtsChunk.toAndroidChunk() = TtsChunk(
        text = text,
        sourceCfi = sourceCfi.orEmpty(),
        startOffsetInSource = startOffset,
        spokenText = spokenText
    )

    private companion object {
        const val PLAYBACK_SOURCE_READER = "READER"
    }
}
