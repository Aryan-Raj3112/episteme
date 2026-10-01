@file:OptIn(
    ExperimentalForeignApi::class,
    ExperimentalSerializationApi::class,
    ExperimentalCoroutinesApi::class,
)

package com.aryan.reader.shared.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.LocalTtsInterruptionAction
import com.aryan.reader.shared.LocalTtsInterruptionEvent
import com.aryan.reader.shared.LocalTtsInterruptionState
import com.aryan.reader.shared.SharedBookTtsListenState
import com.aryan.reader.shared.SharedBookTtsListeningProgress
import com.aryan.reader.shared.SharedTtsListenStartPolicy
import com.aryan.reader.shared.calculateSharedTtsAudiobookProgress
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.advanceSharedSleepTimer
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.splitSharedTtsListenChunks
import com.aryan.reader.shared.reader.loadSharedEpubTtsChapters
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
import com.aryan.reader.shared.pdf.PdfTextProcessing
import com.aryan.reader.shared.pdfium.c.FPDF_DOCUMENT
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDFText_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDFText_CountChars
import com.aryan.reader.shared.pdfium.c.FPDFText_GetText
import com.aryan.reader.shared.pdfium.c.FPDFText_LoadPage
import com.aryan.reader.shared.sharedTtsTranscriptWindow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.CValue
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import com.aryan.reader.shared.ReaderTtsVoiceOverride
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import com.aryan.reader.shared.ui.SharedMobileEpubVoice
import com.aryan.reader.shared.ui.iosTtsLanguageDisplayName
import com.aryan.reader.shared.ui.iosTtsVoiceQuality
import com.aryan.reader.shared.ui.sortedForTtsDisplay
import com.aryan.reader.shared.ui.SHARED_MOBILE_TTS_SAMPLE_DEFAULT
import com.aryan.reader.shared.ui.effectiveSharedMobileTtsSampleText
import com.aryan.reader.shared.ui.sanitizeSharedMobileTtsSampleText
import com.aryan.reader.shared.ui.toggleSharedMobileTtsVoiceFavorite
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSFileManager
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueCount
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackQueueIndex
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.Foundation.NSRange
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults

internal const val IOS_TTS_LISTEN_TAG = "ReaderBookTtsIOS"

/**
 * Listen's own device-voice override. Absent key inherits the reader's voice
 * (`reader.tts.voiceIdentifier`) until the user picks here; blank is an explicit
 * "system default". Android benchmark: `ListenTtsVoicePreferences`, resolved by the
 * shared [ReaderTtsVoiceOverride] so both platforms behave identically.
 */
private const val IOS_LISTEN_TTS_VOICE_OVERRIDE_KEY = "reader.bookTtsListening.voiceOverride"
private const val IOS_READER_TTS_VOICE_KEY = "reader.tts.voiceIdentifier"
private const val ENGINE_OBSERVATION_INTERVAL_MS = 120L

private const val IOS_LISTEN_TTS_FAVORITES_KEY = "reader.bookTtsListening.favoriteVoices"
private const val IOS_LISTEN_TTS_SAMPLE_TEXT_KEY = "reader.bookTtsListening.previewSampleText"
private const val IOS_READER_TTS_RATE_KEY = "reader.tts.speechRate"
private const val IOS_READER_TTS_PITCH_KEY = "reader.tts.pitch"

internal fun iosTtsListenLog(message: String) {
    IosDiagnosticLogStore.record(IOS_TTS_LISTEN_TAG, message)
    println("[$IOS_TTS_LISTEN_TAG] $message")
}

internal fun iosTtsListenLogError(message: String, error: Throwable?) {
    IosDiagnosticLogStore.record(IOS_TTS_LISTEN_TAG, message)
    println("[$IOS_TTS_LISTEN_TAG] $message")
    error?.let {
        val detail = "  ${it::class.simpleName}: ${it.message}"
        IosDiagnosticLogStore.record(IOS_TTS_LISTEN_TAG, detail)
        println("[$IOS_TTS_LISTEN_TAG]$detail")
        it.printStackTrace()
    }
}

internal data class IosTtsListenChunk(
    val text: String,
    val spokenText: String = text,
    val sourceCfi: String,
    val startOffsetInSource: Int,
)

internal data class IosTtsListenChapter(
    val index: Int,
    val id: String,
    val title: String,
    val chunks: List<IosTtsListenChunk>,
)

internal data class IosTtsListenBook(
    val bookId: String,
    val title: String,
    val chapters: List<IosTtsListenChapter>,
)

/**
 * "Listen with TTS" player: reads any supported library book as an audiobook
 * through AVSpeechSynthesizer, mirroring Android's DirectLocalTtsPlayer /
 * BookTtsSessionCoordinator logic:
 *
 * - one utterance per chunk via speakUtterance (the iOS equivalent of
 *   tts.speak()); QUEUE_FLUSH semantics by stopping before each chunk
 * - pause/resume at word boundaries (willSpeakRangeOfSpeechString), speed
 *   changes re-speak from the current word
 * - per-chunk progress persisted with a 350 ms debounce, chapter-start
 *   persist, sleep-timer stop persist and completion persist
 * - auto-advance across chapters, skipping chapters with no readable text
 */
internal class IosBookTtsListeningController(
    /**
     * The app-level shared engine, the same instance the in-book reader drives.
     *
     * Android benchmark: `BookTtsSessionCoordinator` is constructed with the reader's own
     * `TtsPlaybackManager`, so audiobook Listen has no engine of its own — it resolves content
     * and hands one chapter's chunks to the shared engine. This controller is that coordinator:
     * it owns content, resume policy, per-book progress and the sleep timer, and delegates all
     * synthesis to [localEngine].
     */
    private val localEngine: com.aryan.reader.shared.ui.SharedMobileEpubLocalTts,
) {
    var state by mutableStateOf(SharedBookTtsListenState())
        private set

    val progressByBook: MutableMap<String, SharedBookTtsListeningProgress> = mutableStateMapOf()
    val chapterTitlesByBook: MutableMap<String, List<String>> = mutableStateMapOf()

    /** Device voices offered in the Listen voice settings. */
    var availableVoices by mutableStateOf(emptyList<SharedMobileEpubVoice>())
        private set

    /**
     * Voice the next utterance will use. Null = the platform system default, which is also what
     * an explicit "system default" pick resolves to.
     */
    var selectedVoiceIdentifier by mutableStateOf<String?>(null)
        private set

    /** Starred voice ids for the Listen voice list. */
    var favoriteVoiceIdentifiers by mutableStateOf(emptySet<String>())
        private set

    /** Text spoken by the voice preview; never blank. */
    var previewSampleText by mutableStateOf(SHARED_MOBILE_TTS_SAMPLE_DEFAULT)
        private set

    /** Separate synthesizer so previewing never interrupts the book being read. */
    private val previewSynthesizer = AVSpeechSynthesizer()

    private val listenDefaults: NSUserDefaults get() = NSUserDefaults.standardUserDefaults

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val progressStore = IosTtsListeningProgressStore()
    private val contentCache = mutableMapOf<String, IosTtsListenBook>()


    private var generation = 0L
    // Start-session stopwatch for ReaderTtsStart diagnostics: set at start(),
    // consumed at the first delegate audio callback, cleared at stop().
    private var ttsStartMark: TimeMark? = null
    private var currentBookId: String? = null
    private var currentBookTitle = ""
    private var currentBookAuthor: String? = null
    private var remoteCommandsInstalled = false
    private var currentChunks: List<IosTtsListenChunk> = emptyList()
    private var currentChunkIndex = -1
    private var currentChapterIndex = 0
    private var chapterCount = 0
    private var speechBaseOffset = 0
    private var latestWordOffset = 0
    private var wantsPlayback = false
    private var audioSessionActive = false
    private var audioSessionGeneration = 0
    private var interruptionState = LocalTtsInterruptionState()
    private var sleepTimerJob: Job? = null
    private var persistJob: Job? = null
    private val interruptionMonitor = IosTtsAudioInterruptionMonitor(::handleAudioInterruption)

    init {
        observeSharedEngine()
        progressStore.load().forEach { (bookId, progress) ->
            progressByBook[bookId] = progress
        }
        availableVoices = enumerateListenVoices()
        selectedVoiceIdentifier = resolveListenVoiceIdentifier()
        favoriteVoiceIdentifiers = loadListenFavorites()
        previewSampleText = effectiveSharedMobileTtsSampleText(
            listenDefaults.stringForKey(IOS_LISTEN_TTS_SAMPLE_TEXT_KEY)
        )
        iosTtsListenLog(
            "Controller initialized; restored progress for ${progressByBook.size} book(s); " +
                "voices=${availableVoices.size} selected=${selectedVoiceIdentifier ?: "<system>"}"
        )
    }

    /**
     * Picks the Listen voice, persisting it as an explicit choice so it stops following the
     * reader. Passing null stores the system-default sentinel rather than clearing the key.
     */
    fun setVoice(identifier: String?) {
        val resolved = identifier?.takeIf { candidate ->
            availableVoices.any { it.identifier == candidate }
        }
        selectedVoiceIdentifier = resolved
        listenDefaults.setObject(
            ReaderTtsVoiceOverride.encodeVoiceOverride(resolved),
            forKey = IOS_LISTEN_TTS_VOICE_OVERRIDE_KEY
        )
        iosTtsListenLog("setVoice voice=${resolved ?: "<system>"}")
    }

    private fun resolveListenVoiceIdentifier(): String? {
        val stored = listenDefaults.stringForKey(IOS_LISTEN_TTS_VOICE_OVERRIDE_KEY)
        val resolved = ReaderTtsVoiceOverride.resolveVoiceIdentifier(
            isOverrideStored = listenDefaults.objectForKey(IOS_LISTEN_TTS_VOICE_OVERRIDE_KEY) != null,
            storedOverride = stored,
            inheritedVoiceIdentifier = listenDefaults.stringForKey(IOS_READER_TTS_VOICE_KEY)
        )
        // A stored voice that iOS no longer has (OS upgrade, deleted download) falls back to the
        // system default instead of speaking with a missing identifier.
        return resolved?.takeIf { candidate -> availableVoices.any { it.identifier == candidate } }
    }

    /** Stars/unstars a voice. Independent of the reader's own favorites. */
    fun toggleFavoriteVoice(identifier: String) {
        if (identifier.isBlank()) return
        favoriteVoiceIdentifiers = toggleSharedMobileTtsVoiceFavorite(favoriteVoiceIdentifiers, identifier)
        listenDefaults.setObject(favoriteVoiceIdentifiers.toList(), forKey = IOS_LISTEN_TTS_FAVORITES_KEY)
    }

    /** Persists custom preview text; blank clears it back to the shared default. */
    fun setPreviewSampleText(text: String) {
        val sanitized = sanitizeSharedMobileTtsSampleText(text)
        listenDefaults.setObject(sanitized, forKey = IOS_LISTEN_TTS_SAMPLE_TEXT_KEY)
        previewSampleText = effectiveSharedMobileTtsSampleText(sanitized)
    }

    /** Speaks [identifier] (or the selected voice when null) without touching playback. */
    fun previewVoice(identifier: String? = selectedVoiceIdentifier) {
        previewSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance(string = previewSampleText).apply {
            rate = (0.5f * state.speechRate).coerceIn(0.1f, 1f)
            pitchMultiplier = state.pitch
            identifier
                ?.let(AVSpeechSynthesisVoice::voiceWithIdentifier)
                ?.let { voice = it }
        }
        previewSynthesizer.speakUtterance(utterance)
    }

    private fun loadListenFavorites(): Set<String> =
        runCatching { listenDefaults.stringArrayForKey(IOS_LISTEN_TTS_FAVORITES_KEY) as? List<*> }
            .getOrNull()
            .orEmpty()
            .mapNotNull { it as? String }
            .filter { it.isNotBlank() }
            .toSet()

    /** Listen inherits the reader's rate/pitch until it saves its own via [setSpeechRate]. */
    fun inheritReaderSpeechParameters(): Pair<Float, Float> =
        (listenDefaults.objectForKey(IOS_READER_TTS_RATE_KEY)?.let { listenDefaults.doubleForKey(IOS_READER_TTS_RATE_KEY).toFloat() } ?: 1f) to
            (listenDefaults.objectForKey(IOS_READER_TTS_PITCH_KEY)?.let { listenDefaults.doubleForKey(IOS_READER_TTS_PITCH_KEY).toFloat() } ?: 1f)

    private fun enumerateListenVoices(): List<SharedMobileEpubVoice> =
        AVSpeechSynthesisVoice.speechVoices()
            .mapNotNull { it as? AVSpeechSynthesisVoice }
            .map { voice ->
                val languageTag = voice.language.orEmpty()
                SharedMobileEpubVoice(
                    identifier = voice.identifier,
                    name = voice.name,
                    language = iosTtsLanguageDisplayName(languageTag).ifBlank { languageTag },
                    languageTag = languageTag,
                    quality = iosTtsVoiceQuality(voice),
                )
            }
            .sortedForTtsDisplay()

    /** Loads chapter titles for a book without starting playback. */
    fun ensureContent(book: BookItem, replacements: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences()) {
        if (contentCache.containsKey(book.id)) return
        iosTtsListenLog("ensureContent requested bookId=${book.id} name=${book.displayName} type=${book.type}")
        scope.launch {
            val content = withContext(Dispatchers.Default) { loadContentOrNull(book, replacements) }
            if (content == null) {
                iosTtsListenLog("ensureContent FAILED bookId=${book.id} error=${lastContentError}")
                return@launch
            }
            contentCache[book.id] = content
            chapterTitlesByBook[book.id] = content.chapters.map { it.title }
            iosTtsListenLog("ensureContent loaded bookId=${book.id} chapters=${content.chapters.size}")
        }
    }

    fun start(
        book: BookItem,
        policy: SharedTtsListenStartPolicy,
        chapterIndex: Int? = null,
        replacements: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    ) {
        val bookId = book.id
        ttsStartMark = TimeSource.Monotonic.markNow()
        iosTtsStartLog("listen.start", "bookId=$bookId policy=$policy chapterIndex=$chapterIndex")
        iosTtsListenLog(
            "start() bookId=$bookId name=${book.displayName} type=${book.type} policy=$policy " +
                "chapterIndex=$chapterIndex path=${book.path ?: "<null>"} pathExists=" +
                book.path.isIosReadableBookPath()
        )
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        scope.launch {
            val content = contentCache[bookId]
                ?: withContext(Dispatchers.Default) { loadContentOrNull(book, replacements) }
                ?: run {
                    val message = lastContentError ?: "This book cannot be read with text-to-speech"
                    iosTtsListenLog("start() ABORTED bookId=$bookId error=$message")
                    state = SharedBookTtsListenState(error = message)
                    return@launch
                }
            contentCache[bookId] = content
            chapterTitlesByBook[bookId] = content.chapters.map { it.title }
            iosTtsListenLog(
                "start() content ready bookId=$bookId chapters=${content.chapters.size} " +
                    "chunks=${content.chapters.sumOf { it.chunks.size }}"
            )
            val saved = progressByBook[bookId]
            val savedChapter = saved?.chapterIndex?.coerceIn(0, content.chapters.lastIndex) ?: 0
            val requestedChapter = when (policy) {
                SharedTtsListenStartPolicy.RESUME -> savedChapter
                SharedTtsListenStartPolicy.BEGINNING -> 0
                SharedTtsListenStartPolicy.READING_POSITION ->
                    book.readerPosition?.chapterIndex ?: book.lastPageIndex ?: savedChapter
                SharedTtsListenStartPolicy.CHAPTER ->
                    (chapterIndex ?: savedChapter).coerceIn(0, content.chapters.lastIndex)
            }
            val readableChapter = content.chapters
                .indexOfFirst { it.index >= requestedChapter && it.chunks.isNotEmpty() }
                .takeIf { it >= 0 }
                ?: content.chapters.indexOfFirst { it.chunks.isNotEmpty() }
            if (readableChapter < 0) {
                val chapterCount = content.chapters.size
                val emptyCount = content.chapters.count { it.chunks.isEmpty() }
                iosTtsListenLog(
                    "start() NO READABLE CHAPTER bookId=$bookId type=${book.type} " +
                        "chapters=$chapterCount emptyChapters=$emptyCount"
                )
                state = SharedBookTtsListenState(
                    error = if (book.type == FileType.PDF) {
                        "This PDF has no extractable text. Scanned documents need OCR before " +
                            "text-to-speech can read them."
                    } else {
                        "This book contains no readable text"
                    }
                )
                return@launch
            }
            currentBookId = bookId
            currentBookTitle = book.title?.takeIf(String::isNotBlank) ?: book.displayName
            currentBookAuthor = book.author
            chapterCount = content.chapters.size
            currentChapterIndex = readableChapter
            currentChunks = content.chapters[readableChapter].chunks
            val startChunk = if (policy == SharedTtsListenStartPolicy.RESUME && readableChapter == savedChapter) {
                (saved?.chunkIndex ?: 0).coerceIn(0, currentChunks.lastIndex)
            } else {
                0
            }
            wantsPlayback = true
            state = SharedBookTtsListenState(
                connected = true,
                bookId = bookId,
                chapterIndex = readableChapter,
                chapterCount = chapterCount,
                chunkCount = currentChunks.size,
                chapterTitle = content.chapters[readableChapter].title,
                speechRate = (saved?.speechRate ?: 1f).coerceIn(0.5f, 3f),
                pitch = (saved?.pitch ?: 1f).coerceIn(0.5f, 2f),
            )
            persistNow(progressFor(readableChapter, startChunk, completed = false))
            configureAudioSession(active = true)
            installBookTtsRemoteCommands()
            generation += 1
            iosTtsListenLog("start() speaking chapter=$readableChapter chunk=$startChunk of ${currentChunks.size} chunks")
            startEngineAt(startChunk, playWhenReady = true)
            updateBookTtsNowPlaying()
        }
    }

    fun togglePlay() {
        if (state.isPlaying || state.isLoading) pause() else resume()
    }

    fun pause() {
        interruptionState = LocalTtsInterruptionState()
        pauseInternal()
    }

    private fun pauseInternal() {
        if (currentChunks.isEmpty()) return
        iosTtsListenLog("pause() chunk=$currentChunkIndex")
        wantsPlayback = false
        generation += 1
        localEngine.pause()
        state = state.copy(isPlaying = false, isLoading = false)
        persistNow(progressFor(state.chapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        updateBookTtsNowPlaying()
    }

    fun resume() {
        interruptionState = LocalTtsInterruptionState()
        if (currentChunks.isEmpty() || currentChunkIndex < 0) return
        if (state.isPlaying || state.isLoading) return
        iosTtsListenLog("resume() chunk=$currentChunkIndex wordOffset=$latestWordOffset")
        wantsPlayback = true
        // The shared engine owns the position now; Listen resumes by restarting the chunk.
        startEngineAt(currentChunkIndex, playWhenReady = true)
        updateBookTtsNowPlaying()
    }

    fun stop() {
        interruptionState = LocalTtsInterruptionState()
        ttsStartMark = null
        iosTtsListenLog("stop() bookId=$currentBookId chunk=$currentChunkIndex")
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        persistNow(progressFor(state.chapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        generation += 1
        localEngine.stop()
        currentChunks = emptyList()
        currentChunkIndex = -1
        currentBookId = null
        wantsPlayback = false
        state = SharedBookTtsListenState(sessionEndedByStop = true)
        deactivateAudioSession()
        clearBookTtsNowPlaying()
    }

    fun release() {
        stop()
        interruptionMonitor.close()
        scope.cancel()
    }

    private fun handleAudioInterruption(interruption: IosTtsAudioInterruption) {
        val event = when (interruption) {
            IosTtsAudioInterruption.Began -> LocalTtsInterruptionEvent.Began(
                playbackWasActive = state.isPlaying || state.isLoading
            )
            is IosTtsAudioInterruption.Ended -> LocalTtsInterruptionEvent.Ended(
                systemAllowsResume = interruption.systemAllowsResume
            )
            IosTtsAudioInterruption.OutputBecameUnavailable ->
                LocalTtsInterruptionEvent.OutputBecameNoisy(
                    playbackWasActive = state.isPlaying || state.isLoading
                )
        }
        val transition = interruptionState.reduce(event)
        interruptionState = transition.state
        when (transition.action) {
            LocalTtsInterruptionAction.NONE -> Unit
            LocalTtsInterruptionAction.PAUSE -> pauseInternal()
            LocalTtsInterruptionAction.RESUME -> scope.launch {
                // Activating the audio session blocks while the interrupting app
                // releases the route, so keep it off the notification callback.
                configureAudioSession(active = true)
                resume()
            }
        }
    }

    fun previousChunk() = moveByChunk(-1)

    fun nextChunk() = moveByChunk(1)

    fun seekToChunk(chunkIndex: Int) {
        if (currentChunks.isEmpty() || currentChunkIndex < 0) return
        val target = chunkIndex.coerceIn(0, currentChunks.lastIndex)
        if (target == currentChunkIndex && (state.isPlaying || state.isLoading)) return
        iosTtsListenLog("seekToChunk target=$target (from $currentChunkIndex)")
        wantsPlayback = true
        startEngineAt(target, playWhenReady = true)
    }

    fun previousChapter() = moveChapterBy(-1)

    fun nextChapter() = moveChapterBy(1)

    fun selectChapter(index: Int) {
        val content = contentCache[currentBookId ?: return] ?: return
        playChapterInternal(index.coerceIn(0, content.chapters.lastIndex), 0)
    }

    /** Stops only the voice-preview utterance, leaving book playback untouched. */
    fun stopVoicePreview() {
        previewSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    /** Word-level position within the current chunk, for word-accurate resume. */
    val currentSpokenOffset: Int get() = latestWordOffset

    fun setParameters(rate: Float, pitch: Float) {
        val safeRate = rate.coerceIn(0.5f, 3f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        state = state.copy(speechRate = safeRate, pitch = safePitch)
        persistNow(progressFor(state.chapterIndex, state.chunkIndex.coerceAtLeast(0), completed = false))
        val chunk = currentChunks.getOrNull(currentChunkIndex) ?: return
        localEngine.setSpeechParameters(safeRate, safePitch)
        if ((state.isPlaying || state.isLoading) && currentChunkIndex >= 0) {
            startEngineAt(currentChunkIndex, playWhenReady = true)
        }
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            var remainingSeconds = minutes * 60
            while (remainingSeconds > 0) {
                state = state.copy(sleepTimerRemainingMs = remainingSeconds * 1_000L)
                delay(1_000)
                // Keep the timer anchored to actual speech, matching Android's
                // shared contract. Pausing TTS must pause the countdown too.
                remainingSeconds = advanceSharedSleepTimer(remainingSeconds, state.isPlaying)
            }
            state = state.copy(sleepTimerRemainingMs = 0L)
            sleepTimerJob = null
            stopForSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        state = state.copy(sleepTimerRemainingMs = 0L)
    }

    private fun moveByChunk(delta: Int) {
        if (currentChunks.isEmpty() || currentChunkIndex < 0) return
        val target = currentChunkIndex + delta
        if (target !in 0..currentChunks.lastIndex) return
        wantsPlayback = true
        startEngineAt(target, playWhenReady = true)
    }

    private fun moveChapterBy(direction: Int) {
        val content = contentCache[currentBookId ?: return] ?: return
        val target = if (direction > 0) {
            content.chapters.indexOfFirst { it.index > currentChapterIndex && it.chunks.isNotEmpty() }
        } else {
            content.chapters.indexOfLast { it.index < currentChapterIndex && it.chunks.isNotEmpty() }
        }
        if (target >= 0) playChapterInternal(target, 0)
    }

    private fun playChapterInternal(requested: Int, chunkIndex: Int) {
        val content = contentCache[currentBookId ?: return] ?: return
        val chapter = content.chapters.getOrNull(requested) ?: return
        if (chapter.chunks.isEmpty()) {
            val next = content.chapters.indexOfFirst { it.index > requested && it.chunks.isNotEmpty() }
            val fallback = content.chapters.indexOfLast { it.index < requested && it.chunks.isNotEmpty() }
            val readable = next.takeIf { it >= 0 } ?: fallback
            if (readable < 0) return
            playChapterInternal(readable, chunkIndex)
            return
        }
        iosTtsListenLog("playChapterInternal chapter=${chapter.index} title=${chapter.title}")
        currentChapterIndex = chapter.index
        currentChunks = chapter.chunks
        val safeChunk = chunkIndex.coerceIn(0, currentChunks.lastIndex)
        persistNow(progressFor(chapter.index, safeChunk, completed = false))
        state = state.copy(
            chapterIndex = chapter.index,
            chapterCount = chapterCount,
            chunkCount = currentChunks.size,
            chapterTitle = chapter.title,
            sessionFinished = false,
            transcriptStartIndex = 0,
            transcriptChunks = emptyList(),
        )
        wantsPlayback = true
        startEngineAt(safeChunk, playWhenReady = true)
    }

    /**
     * Hands the current chapter's chunks to the shared engine.
     *
     * Android benchmark: `BookTtsSessionCoordinator` calls
     * `TtsPlaybackManager.startBookListeningChapter(content, ...)`, which hands
     * `handleStartTts` the chapter's chunks and lets the engine own synthesis. Listen keeps the
     * chapter-by-chapter orchestration (Android does too — the coordinator loads one chapter at
     * a time) and no longer speaks anything itself.
     */
    private fun startEngineAt(chunkIndex: Int, playWhenReady: Boolean) {
        val chunks = currentChunks
        if (chunks.isEmpty()) return
        val safeChunk = chunkIndex.coerceIn(0, chunks.lastIndex)
        val engineChunks = chunks.map { chunk ->
            com.aryan.reader.shared.ReaderTtsChunk(
                index = 0,
                pageIndex = 0,
                chapterIndex = currentChapterIndex,
                chapterTitle = currentChapterTitle().orEmpty(),
                text = chunk.text,
                startOffset = chunk.startOffsetInSource,
                endOffset = chunk.startOffsetInSource + chunk.text.length,
                sourceCfi = chunk.sourceCfi,
                spokenText = chunk.spokenText,
            )
        }
        wantsPlayback = playWhenReady
        currentChunkIndex = safeChunk
        state = state.copy(
            chapterIndex = currentChapterIndex,
            chapterCount = chapterCount,
            chunkIndex = -1,
            chunkCount = chunks.size,
            chapterTitle = currentChapterTitle(),
            isLoading = true,
            isPlaying = false,
            sessionFinished = false,
            transcriptStartIndex = 0,
            transcriptChunks = emptyList(),
        )
        iosTtsListenLog(
            "startEngineAt chapter=$currentChapterIndex chunk=$safeChunk of ${chunks.size} " +
                "playWhenReady=$playWhenReady"
        )
        // Listen's own voice/rate choices, applied to the shared engine for this session.
        localEngine.setSpeechParameters(state.speechRate, state.pitch)
        selectedVoiceIdentifier?.let { localEngine.setVoice(it) }
        localEngine.start(
            chunks = engineChunks,
            bookTitle = currentBookTitle,
            bookId = currentBookId,
            startChunkIndex = safeChunk,
            playWhenReady = playWhenReady,
            playbackSource = com.aryan.reader.shared.SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK,
            totalChapters = chapterCount,
        )
    }

    private fun currentChapterTitle(): String? {
        val content = currentBookId?.let { contentCache[it] } ?: return null
        return content.chapters.getOrNull(currentChapterIndex)?.title
    }

    /**
     * Mirrors the shared engine's state into the Listen state.
     *
     * Replaces the controller's own `AVSpeechSynthesizerDelegate` callbacks: the engine now
     * owns synthesis and chunk advance, so Listen observes instead of driving. Chapter end is
     * still the controller's job (Android's coordinator does the same) and is detected from the
     * engine reporting the chapter's chunk list exhausted.
     */
    private fun observeSharedEngine() {
        scope.launch {
            var lastCompletionCount = localEngine.completionCount
            var lastChunkIndex = -1
            while (true) {
                val observation = EngineObservation(
                    state = localEngine.state,
                    chunkIndex = localEngine.progress.currentChunkIndex,
                    chunkCount = localEngine.progress.chunks.size,
                    completionCount = localEngine.completionCount,
                    spokenOffset = localEngine.currentSpokenOffset,
                )
                if (currentBookId != null) {
                    val chapterExhausted = observation.completionCount > lastCompletionCount
                    lastCompletionCount = observation.completionCount
                    val engineChunkIndex = observation.chunkIndex
                    if (chapterExhausted) {
                        advancePastChapterEnd()
                    } else if (engineChunkIndex >= 0) {
                        currentChunkIndex = engineChunkIndex
                        latestWordOffset = observation.spokenOffset
                        val speaking = observation.state == SharedMobileEpubLocalTtsState.SPEAKING
                        if (engineChunkIndex != lastChunkIndex) {
                            lastChunkIndex = engineChunkIndex
                            state = state.copy(
                                chunkIndex = engineChunkIndex,
                                chunkCount = observation.chunkCount,
                                isLoading = false,
                                isPlaying = speaking,
                            )
                            publishTranscript(engineChunkIndex)
                            schedulePersist(engineChunkIndex)
                            updateBookTtsNowPlaying()
                        } else if (speaking != state.isPlaying) {
                            state = state.copy(isLoading = false, isPlaying = speaking)
                        }
                    }
                }
                delay(ENGINE_OBSERVATION_INTERVAL_MS)
            }
        }
    }

    private data class EngineObservation(
        val state: SharedMobileEpubLocalTtsState,
        val chunkIndex: Int,
        val chunkCount: Int,
        val completionCount: Long,
        val spokenOffset: Int,
    )

    private fun advancePastChapterEnd() {
        val bookId = currentBookId ?: return
        val content = contentCache[bookId] ?: return
        val next = content.chapters.indexOfFirst { it.index > currentChapterIndex && it.chunks.isNotEmpty() }
        if (next < 0) {
            iosTtsListenLog("BOOK COMPLETED bookId=$bookId")
            persistNow(progressFor(currentChapterIndex, currentChunks.lastIndex, completed = true))
            generation += 1
            localEngine.stop()
            state = state.copy(
                isLoading = false,
                isPlaying = false,
                sessionFinished = true,
                connected = false,
                bookId = null,
                sleepTimerRemainingMs = 0L,
            )
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            deactivateAudioSession()
            return
        }
        iosTtsListenLog("advancing chapter $currentChapterIndex -> $next")
        playChapterInternal(next, 0)
    }

    private fun stopForSleepTimer() {
        iosTtsListenLog("stopForSleepTimer chapter=$currentChapterIndex chunk=$currentChunkIndex")
        persistNow(progressFor(state.chapterIndex, state.chunkIndex.coerceAtLeast(0), completed = false))
        generation += 1
        localEngine.stop()
        currentChunks = emptyList()
        currentChunkIndex = -1
        currentBookId = null
        wantsPlayback = false
        state = SharedBookTtsListenState(sessionEndedByStop = true)
        deactivateAudioSession()
        clearBookTtsNowPlaying()
    }

    private fun chunkProgress(chunkIndex: Int): Float =
        calculateSharedTtsAudiobookProgress(
            chapterIndex = currentChapterIndex,
            chapterCount = chapterCount,
            chunkIndex = chunkIndex,
            chunkCount = currentChunks.size,
        )

    private fun publishTranscript(chunkIndex: Int) {
        val window = sharedTtsTranscriptWindow(chunkIndex, currentChunks.size)
        if (window.isEmpty()) {
            state = state.copy(transcriptStartIndex = 0, transcriptChunks = emptyList())
            return
        }
        state = state.copy(
            transcriptStartIndex = window.first,
            transcriptChunks = window.map { currentChunks[it].text },
        )
    }

    private fun schedulePersist(chunkIndex: Int) {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(350)
            persistNow(progressFor(currentChapterIndex, chunkIndex.coerceAtLeast(0), completed = false))
        }
    }

    private fun progressFor(chapterIndex: Int, chunkIndex: Int, completed: Boolean): SharedBookTtsListeningProgress {
        val bookId = currentBookId ?: return SharedBookTtsListeningProgress(bookId = "")
        val safeChapterCount = chapterCount.coerceAtLeast(1)
        val safeChunkCount = currentChunks.size.coerceAtLeast(1)
        val percent = if (completed) {
            100f
        } else {
            ((chapterIndex.coerceIn(0, safeChapterCount - 1) + (chunkIndex.coerceAtLeast(0) + 1f) / safeChunkCount) / safeChapterCount * 100f)
                .coerceIn(0f, 99.9f)
        }
        return SharedBookTtsListeningProgress(
            bookId = bookId,
            chapterIndex = chapterIndex,
            chunkIndex = chunkIndex,
            sourceCfi = currentChunks.getOrNull(chunkIndex)?.sourceCfi,
            sourceOffset = if (chunkIndex == state.chunkIndex) latestWordOffset else 0,
            progressPercent = percent,
            speechRate = state.speechRate,
            pitch = state.pitch,
            completed = completed,
            updatedAt = currentTimestamp(),
        )
    }

    private fun persistNow(progress: SharedBookTtsListeningProgress) {
        progressByBook[progress.bookId] = progress
        progressStore.save(progressByBook.toMap())
    }

    private var lastContentError: String? = null

    private suspend fun loadContentOrNull(
        book: BookItem,
        replacements: ReaderTtsReplacementPreferences,
    ): IosTtsListenBook? {
        val startedAt = currentTimestamp()
        val result = runCatching { buildIosTtsListenContent(book, replacements) }
        lastContentError = result.exceptionOrNull()?.message
        if (result.isFailure) {
            iosTtsListenLogError(
                "loadContentOrNull FAILED bookId=${book.id} type=${book.type} path=${book.path ?: "<null>"} " +
                    "elapsed=${currentTimestamp() - startedAt}ms",
                result.exceptionOrNull(),
            )
        } else {
            val content = result.getOrNull()
            iosTtsListenLog(
                "loadContentOrNull OK bookId=${book.id} type=${book.type} chapters=${content?.chapters?.size} " +
                    "chunks=${content?.chapters?.sumOf { it.chunks.size }} " +
                    "elapsed=${currentTimestamp() - startedAt}ms"
            )
        }
        return result.getOrNull()
    }

    private suspend fun buildIosTtsListenContent(
        book: BookItem,
        replacements: ReaderTtsReplacementPreferences,
    ): IosTtsListenBook {
        val startedAt = currentTimestamp()
        iosTtsListenLog("buildIosTtsListenContent type=${book.type} path=${book.path ?: "<null>"}")
        val chapters = when (book.type) {
            FileType.PDF -> buildIosPdfListenChapters(book, replacements)
            FileType.EPUB -> {
                val built = buildIosEpubListenChapters(book, replacements)
                iosTtsListenLog(
                    "buildIosTtsListenContent fast EPUB path chapters=${built.size} " +
                        "elapsed=${currentTimestamp() - startedAt}ms"
                )
                built
            }
            else -> {
                val shared = loadIosEpubBook(book)
                val splitStart = currentTimestamp()
                iosTtsListenLog(
                    "buildIosTtsListenContent loadIosEpubBook OK chapters=${shared.chapters.size} " +
                        "elapsed=${splitStart - startedAt}ms; splitting chunks..."
                )
                shared.chapters.mapIndexed { index, chapter ->
                    if (index % 10 == 0) {
                        iosTtsListenLog(
                            "buildIosTtsListenContent splitting chapter $index of ${shared.chapters.size} " +
                                "elapsed=${currentTimestamp() - startedAt}ms"
                        )
                    }
                    // Shared-first parity: no `headless/...` CFIs. Emit the
                    // stable `desktop:chapter:start:end` form with real
                    // plain-text offsets so Listen progress is resumable in
                    // the reader (ReaderTtsPlanner falls back to
                    // offset/text matching for desktop anchors).
                    val plainText = chapter.plainText
                    var searchFrom = 0
                    IosTtsListenChapter(
                        index = index,
                        id = chapter.id,
                        title = chapter.title.ifBlank { "Chapter ${index + 1}" },
                        chunks = splitSharedTtsListenChunks(plainText).map { text ->
                            val offset = plainText.indexOf(text, startIndex = searchFrom)
                                .takeIf { it >= 0 } ?: searchFrom
                            searchFrom = (offset + text.length).coerceAtMost(plainText.length)
                            val end = (offset + text.length).coerceAtMost(plainText.length)
                            IosTtsListenChunk(
                                text = text,
                                spokenText = ReaderTtsReplacementEngine.apply(text, replacements, book.id).text,
                                sourceCfi = "desktop:$index:$offset:$end",
                                startOffsetInSource = offset,
                            )
                        },
                    )
                }
            }
        }
        return IosTtsListenBook(
            bookId = book.id,
            title = book.title?.takeIf { it.isNotBlank() }
                ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName },
            chapters = chapters,
        )
    }

    private fun buildIosEpubListenChapters(
        book: BookItem,
        replacements: ReaderTtsReplacementPreferences,
    ): List<IosTtsListenChapter> {
        val path = book.path.resolveIosEpubSourcePath()
            ?: error("EPUB path is unavailable")
        val spineChapters = loadSharedEpubTtsChapters(
            archive = IosZipEpubArchive(path),
            fileName = book.displayName.ifBlank { path.substringAfterLast('/') },
        )
        iosTtsListenLog("buildIosEpubListenChapters spineDocs=${spineChapters.size} path=$path")
        return spineChapters.mapIndexed { index, spineChapter ->
            // Same stable-CFI contract as the shared-book path above: real
            // offsets, no `headless/...`, so progress survives into the reader.
            val plainText = spineChapter.plainText
            var searchFrom = 0
            val chunks = splitSharedTtsListenChunks(plainText).map { text ->
                val offset = plainText.indexOf(text, startIndex = searchFrom)
                    .takeIf { it >= 0 } ?: searchFrom
                searchFrom = (offset + text.length).coerceAtMost(plainText.length)
                val end = (offset + text.length).coerceAtMost(plainText.length)
                IosTtsListenChunk(
                    text = text,
                    spokenText = ReaderTtsReplacementEngine.apply(text, replacements, book.id).text,
                    sourceCfi = "desktop:$index:$offset:$end",
                    startOffsetInSource = offset,
                )
            }
            IosTtsListenChapter(
                index = index,
                id = spineChapter.id,
                title = spineChapter.title.ifBlank { "Chapter ${index + 1}" },
                chunks = chunks,
            )
        }
    }

    private suspend fun buildIosPdfListenChapters(
        book: BookItem,
        replacements: ReaderTtsReplacementPreferences,
    ): List<IosTtsListenChapter> {
        val path = book.path ?: error("PDF path is unavailable")
        val resolvedPath = path.resolveIosReadablePath()
        iosTtsListenLog("buildIosPdfListenChapters ref=$path resolved=$resolvedPath")
        val pages = extractIosPdfPageTexts(path)
        // Distinguishes "scanned PDF with no text layer" from "extraction is broken": a scan
        // yields pages but zero characters, while a bug yields characters in the wrong places or
        // a page count of 0. Without this the user only sees "no readable text".
        val pagesWithText = pages.count { it.isNotBlank() }
        val totalChars = pages.sumOf { it.length }
        iosTtsListenLog(
            "buildIosPdfListenChapters pages=${pages.size} pagesWithText=$pagesWithText " +
                "totalChars=$totalChars emptyPages=${pages.size - pagesWithText} path=$path"
        )
        if (pages.isEmpty()) {
            error("This PDF could not be opened for text-to-speech (0 pages)")
        }
        if (pagesWithText == 0) {
            error(
                "No text layer found on any of ${pages.size} pages. This looks like a scanned " +
                    "PDF, which has no extractable text. Run OCR on it first."
            )
        }
        return pages.mapIndexed { pageIndex, rawText ->
            val normalized = rawText
                .replace("\r\n", "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .let(PdfTextProcessing::joinHyphenatedLineBreaks)
                .trim()
            var searchFrom = 0
            val chunks = splitSharedTtsListenChunks(normalized).map { chunkText ->
                val offset = normalized.indexOf(chunkText, startIndex = searchFrom)
                    .takeIf { it >= 0 }
                    ?: searchFrom
                searchFrom = (offset + chunkText.length).coerceAtMost(normalized.length)
                val end = (offset + chunkText.length).coerceAtMost(normalized.length)
                IosTtsListenChunk(
                    text = chunkText,
                    spokenText = ReaderTtsReplacementEngine.apply(chunkText, replacements, book.id).text,
                    sourceCfi = "pdf-page:$pageIndex:$offset:$end",
                    startOffsetInSource = offset,
                )
            }
            IosTtsListenChapter(
                index = pageIndex,
                id = "page-$pageIndex",
                title = "Page ${pageIndex + 1}",
                chunks = chunks,
            )
        }
    }

    private suspend fun extractIosPdfPageTexts(path: String): List<String> {
        // `resolveIosReadablePath` is the single resolver for the three shapes a book path can
        // take. This used to be a local file://-only copy, so a folder-book ref
        // (`ios-folder-book://...`) was handed to PDFium verbatim and every PDF failed with
        // "0 pages" even when it was a perfectly good text PDF.
        val resolved = path.resolveIosReadablePath()
        if (resolved == null) {
            error("This PDF's file reference could not be resolved (${path.take(120)}). " +
                "Re-add the book from its folder so the app can access it again.")
        }
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolved)) {
            error("This PDF's file is missing ($resolved). It may have been moved or deleted.")
        }
        // PDFium is not thread-safe: serialize against rendering, search, and outline work
        // exactly like the shared reader pipeline does.
        return IosPdfiumRuntime.withPdfium {
            val document = FPDF_LoadDocument(resolved, null) ?: return@withPdfium emptyList()
            try {
                val pageCount = FPDF_GetPageCount(document).toInt()
                (0 until pageCount).map { pageIndex -> extractIosPdfPageText(document, pageIndex) }
            } finally {
                FPDF_CloseDocument(document)
            }
        }
    }

    private fun extractIosPdfPageText(document: FPDF_DOCUMENT, pageIndex: Int): String {
        val page = FPDF_LoadPage(document, pageIndex) ?: return ""
        return try {
            val textPage = FPDFText_LoadPage(page) ?: return ""
            try {
                val count = FPDFText_CountChars(textPage).toInt()
                if (count <= 0) return ""
                memScoped {
                    val buffer = allocArray<UShortVar>(count + 1)
                    val written = FPDFText_GetText(textPage, 0, count, buffer)
                    if (written <= 0) return@memScoped ""
                    val chars = CharArray(written) { index -> buffer[index].toInt().toChar() }
                    chars.concatToString().trimEnd('\u0000')
                }
            } finally {
                FPDFText_ClosePage(textPage)
            }
        } finally {
            FPDF_ClosePage(page)
        }
    }

    private fun configureAudioSession(active: Boolean) {
        // Activation-only (deactivation goes through the guarded
        // IosTtsAudioSessionTeardown.deactivateIfStillOwner): setActive blocks
        // on route negotiation, so it must stay off the main thread.
        if (!active) return
        audioSessionGeneration += 1
        iosTtsListenLog("configureAudioSession requesting background activation")
        IosTtsAudioSessionTeardown.activate()
        audioSessionActive = true
    }

    /**
     * AVAudioSession.setActive(false) can block for seconds while the system
     * tears the audio route down, so it must never run on the main thread —
     * stopping a book used to hang the UI. Skip the teardown when a newer
     * listening session already re-activated the session meanwhile.
     */
    private fun deactivateAudioSession() {
        if (!audioSessionActive) return
        audioSessionActive = false
        val generation = ++audioSessionGeneration
        IosTtsAudioSessionTeardown.deactivateIfStillOwner {
            !audioSessionActive && generation == audioSessionGeneration
        }
    }

    /**
     * Lock-screen/Control-Center controls for book listening, matching the media
     * notification Android shows for its TTS foreground service.
     */
    private fun installBookTtsRemoteCommands() {
        if (remoteCommandsInstalled) return
        remoteCommandsInstalled = true
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.playCommand.addTargetWithHandler {
            resume()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.pauseCommand.addTargetWithHandler {
            pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.stopCommand.addTargetWithHandler {
            stop()
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.nextTrackCommand.addTargetWithHandler {
            moveByChunk(1)
            MPRemoteCommandHandlerStatusSuccess
        }
        commands.previousTrackCommand.addTargetWithHandler {
            moveByChunk(-1)
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun updateBookTtsNowPlaying() {
        if (!remoteCommandsInstalled) return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf(
            MPMediaItemPropertyTitle to currentBookTitle,
            MPMediaItemPropertyArtist to state.chapterTitle.orEmpty().ifBlank { "Reading" },
            MPNowPlayingInfoPropertyPlaybackQueueIndex to state.chunkIndex,
            MPNowPlayingInfoPropertyPlaybackQueueCount to state.chunkCount,
            MPNowPlayingInfoPropertyPlaybackRate to if (state.isPlaying) state.speechRate.toDouble() else 0.0,
        )
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.previousTrackCommand.enabled = state.chunkIndex > 0
        commands.nextTrackCommand.enabled = state.chunkIndex in 0 until (state.chunkCount - 1)
    }

    private fun clearBookTtsNowPlaying() {
        if (!remoteCommandsInstalled) return
        remoteCommandsInstalled = false
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        for (command in listOf(
            commands.playCommand,
            commands.pauseCommand,
            commands.stopCommand,
            commands.nextTrackCommand,
            commands.previousTrackCommand,
        )) {
            command.removeTarget(null)
            command.enabled = true
        }
    }
}

private class IosBookTtsSpeechDelegate(
    private val onStarted: (AVSpeechUtterance) -> Unit,
    private val onFinished: (AVSpeechUtterance) -> Unit,
    private val onCancelled: (AVSpeechUtterance) -> Unit,
    private val onWillSpeakRange: (AVSpeechUtterance, CValue<NSRange>) -> Unit,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didStartSpeechUtterance: AVSpeechUtterance,
    ) {
        onStarted(didStartSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance,
    ) {
        onFinished(didFinishSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didCancelSpeechUtterance: AVSpeechUtterance,
    ) {
        onCancelled(didCancelSpeechUtterance)
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString: CValue<NSRange>,
        utterance: AVSpeechUtterance,
    ) {
        onWillSpeakRange(utterance, willSpeakRangeOfSpeechString)
    }
}

private class IosTtsListeningProgressStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val storeKey = "reader.bookTtsListeningProgress.v1"

    fun load(): Map<String, SharedBookTtsListeningProgress> {
        val raw = defaults.stringForKey(storeKey) ?: return emptyMap()
        return runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            root.mapNotNull { (bookId, element) ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                SharedBookTtsListeningProgress(
                    bookId = bookId,
                    chapterIndex = obj.intValue("chapterIndex"),
                    chunkIndex = obj.intValue("chunkIndex"),
                    sourceCfi = obj.stringValue("sourceCfi"),
                    sourceOffset = obj.intValue("sourceOffset"),
                    progressPercent = obj.floatValue("progressPercent"),
                    speechRate = obj.floatValue("speechRate", 1f),
                    pitch = obj.floatValue("pitch", 1f),
                    voiceId = obj.stringValue("voiceId"),
                    completed = obj.booleanValue("completed"),
                    updatedAt = obj.longValue("updatedAt"),
                )
            }.associateBy { it.bookId }
        }.getOrDefault(emptyMap())
    }

    fun save(progress: Map<String, SharedBookTtsListeningProgress>) {
        val root = buildJsonObject {
            progress.forEach { (bookId, entry) ->
                put(
                    bookId,
                    buildJsonObject {
                        put("chapterIndex", entry.chapterIndex)
                        put("chunkIndex", entry.chunkIndex)
                        entry.sourceCfi?.let { put("sourceCfi", it) }
                        put("sourceOffset", entry.sourceOffset)
                        put("progressPercent", entry.progressPercent)
                        put("speechRate", entry.speechRate)
                        put("pitch", entry.pitch)
                        entry.voiceId?.let { put("voiceId", it) }
                        put("completed", entry.completed)
                        put("updatedAt", entry.updatedAt)
                    },
                )
            }
        }
        defaults.setObject(Json.encodeToString(JsonElement.serializer(), root), forKey = storeKey)
    }

    private fun JsonObject.intValue(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.longValue(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.floatValue(key: String, fallback: Float = 0f): Float =
        (this[key] as? JsonPrimitive)?.floatOrNull ?: fallback

    private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.booleanValue(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
}
