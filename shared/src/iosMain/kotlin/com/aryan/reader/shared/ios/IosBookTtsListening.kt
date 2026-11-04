@file:OptIn(
    ExperimentalForeignApi::class,
    ExperimentalSerializationApi::class,
    ExperimentalCoroutinesApi::class,
)

package com.aryan.reader.shared.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SharedListenAudioSource
import com.aryan.reader.shared.SharedListenMediaOverlayInfo
import com.aryan.reader.shared.isOverlay
import com.aryan.reader.shared.resolveSharedListenAudioSource
import com.aryan.reader.shared.sharedListenMediaOverlayInfo
import com.aryan.reader.shared.reader.SharedMediaOverlayDocument
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import com.aryan.reader.shared.reader.SharedMediaOverlayIndex
import com.aryan.reader.shared.reader.SharedMediaOverlayPlayback
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackState
import com.aryan.reader.shared.reader.SharedMediaOverlayProjection
import com.aryan.reader.shared.reader.SharedMediaOverlayProjectionSource
import com.aryan.reader.shared.reader.SharedMediaOverlaySession
import com.aryan.reader.shared.reader.IosMediaOverlayNarrationProgressStore
import com.aryan.reader.shared.reader.IosSharedMediaOverlayPlayback
import com.aryan.reader.shared.reader.iosMediaOverlaySmilReaderFor
import com.aryan.reader.shared.reader.loadSharedEpubMediaOverlayIndex
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemIndexByChapter
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemsInReadingOrder
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
import com.aryan.reader.shared.ReaderTtsProgress
import com.aryan.reader.shared.SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK
import com.aryan.reader.shared.SharedTtsEngine
import com.aryan.reader.shared.SharedTtsListenSavedProgress
import com.aryan.reader.shared.SharedTtsPlaybackSnapshot
import com.aryan.reader.shared.toSharedBookTtsListenState
import com.aryan.reader.shared.SharedOverlayClipChunkSpan
import com.aryan.reader.shared.sharedOverlayClipChunkSpans
import com.aryan.reader.shared.sharedOverlayTranscriptChunkIndex
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
/**
 * Listen's own cloud-voice override. Same tri-state as the device voice above, resolved by the
 * shared [ReaderTtsVoiceOverride]: absent inherits the reader's cloud speaker until the user
 * picks here. Android benchmark: Listen's independent cloud speaker (`loadListenTtsSpeaker`
 * never reads the reader-synced state for a Listen session).
 */
private const val IOS_LISTEN_TTS_CLOUD_VOICE_OVERRIDE_KEY = "reader.bookTtsListening.cloudVoiceOverride"
/**
 * Listen's Cloud/Device engine choice. Absent means "follow the reader", which is why presence is
 * what matters, not the value. Android benchmark: `LISTEN_TTS_MODE_KEY` in `reader_prefs`.
 */
const val IOS_LISTEN_TTS_ENGINE_MODE_KEY: String = "reader.bookTtsListening.engineMode"

private const val ENGINE_OBSERVATION_INTERVAL_MS = 120L

private const val IOS_LISTEN_TTS_FAVORITES_KEY = "reader.bookTtsListening.favoriteVoices"
private const val IOS_LISTEN_TTS_SAMPLE_TEXT_KEY = "reader.bookTtsListening.previewSampleText"
private const val IOS_LISTEN_TTS_OVERLAY_SPEED_KEY = "reader.bookTtsListening.overlaySpeed"
private const val IOS_LISTEN_TTS_SOURCE_KEY_PREFIX = "reader.bookTtsListening.listenSource."
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
    val mediaOverlay: SharedListenMediaOverlayInfo = SharedListenMediaOverlayInfo(),
    val overlayIndex: SharedMediaOverlayIndex = SharedMediaOverlayIndex.EMPTY,
    val chapterContentPaths: List<String> = emptyList(),
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
    /**
     * The app's cloud engine, the same instance the in-book reader drives.
     *
     * Android has one TTS engine that branches on a mode parameter
     * (`startBookListeningChapter` -> `handleStartTts(ttsMode = ...)`); iOS has two engine
     * objects, so Listen picks which one to drive. Null means this build serves no cloud speech,
     * and every session falls back to the device engine.
     */
    private val cloudTts: com.aryan.reader.shared.ui.SharedMobileEpubCloudTts? = null,
    /**
     * Cloud voice id for a Listen session, from the shared cloud settings.
     *
     * Listen's own [selectedVoiceIdentifier] is a *device* voice: Android keeps the same split
     * (`loadListenNativeVoice` for native, a cloud speaker for cloud). In cloud mode the shared
     * voice sheet owns the choice, so the controller applies whatever it last saw rather than
     * overriding it.
     */
    initialCloudVoiceIdentifier: String? = null,
    /**
     * Whether Listen speaks with cloud speech, already resolved from the tri-state rule in
     * [com.aryan.reader.shared.ReaderTtsEngineOverride] by the host (which owns the store).
     */
    initialCloudModeEnabled: Boolean = false,
    /** Persists a Listen engine-mode change. The host owns the backing store. */
    private val onCloudModeChanged: (Boolean) -> Unit = {},
    /**
     * App-level publisher-narration engine, shared with the reader (Android
     * benchmark: the singleton `MediaOverlayPlaybackService` behind both the
     * reader and Listen overlay engines).
     *
     * Null when the host has none, in which case Listen reports no narration
     * rather than offering a button that cannot play.
     */
    private val overlayEngine: SharedMediaOverlayPlayback? = null,
) {
    /**
     * The audiobook Listen state, projected out of the shared engine.
     *
     * Android benchmark: `BookTtsAudiobookController.sharedPlaybackState` is
     * `combine(playbackState, _uiState, _sleepTimerRemainingMs) { ... }.stateIn(...)` — a purely
     * derived value that nothing ever assigns. This is the same shape in Compose terms: the
     * engine owns the session, and everything Listen displays is computed from it here.
     *
     * Nothing below may assign `state`; a field that the engine reports comes from the engine,
     * and a field only this coordinator knows (prepared content, saved rate/pitch, the sleep
     * timer) is a projector input instead.
     */
    val state: SharedBookTtsListenState by derivedStateOf { projectListenState() }

    val progressByBook: MutableMap<String, SharedBookTtsListeningProgress> = mutableStateMapOf()
    val chapterTitlesByBook: MutableMap<String, List<String>> = mutableStateMapOf()
    /** Publisher-narration availability by book, for Listen badges and the source switcher. */
    val overlayInfoByBook: MutableMap<String, SharedListenMediaOverlayInfo> = mutableStateMapOf()
    /** Explicit TTS/narration choice by book. Absent means "prefer narration when present". */
    val listenSourceByBook: MutableMap<String, SharedListenAudioSource> = mutableStateMapOf()

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
    private var currentBook: BookItem? = null
    private var currentBookTitle = ""
    private var currentBookAuthor: String? = null
    // Projector inputs below: observable because `state` reads them. Each changes on a
    // user action or a chapter boundary, never per word.
    /** Chapter the current session is on; the projector's fallback when the engine has no chunk yet. */
    private var currentChapterIndex by mutableStateOf(0)
    /** Prepared chapter count, used before the engine reports `sessionTotalChapters`. */
    private var chapterCount by mutableStateOf(0)
    /** Rate/pitch are projected from the saved progress, but persist needs the live pair. */
    private var sessionSpeechRate by mutableStateOf(1f)
    private var sessionPitch by mutableStateOf(1f)
    /** Prepared-content failure (unreadable book). The engine has no field for it. */
    private var preparedError by mutableStateOf<String?>(null)
    private var sessionFinished by mutableStateOf(false)
    private var sessionEndedByStop by mutableStateOf(false)
    private var sleepTimerRemainingMs by mutableStateOf(0L)
    /** Engine for *new* sessions. A running session keeps the engine it started on. */
    var cloudModeEnabled by mutableStateOf(initialCloudModeEnabled)
        private set
    /** Cloud voice last seen from the shared settings; applied at session start. */
    var cloudVoiceIdentifier by mutableStateOf(initialCloudVoiceIdentifier)
        private set
    /** Whether the *running* session is cloud. Set at start, cleared at stop. */
    private var sessionIsCloud = false
    /** Whether the *running* session is publisher narration. Set at start, cleared at stop. */
    private var sessionIsOverlay by mutableStateOf(false)
    private var overlaySession: SharedMediaOverlaySession? = null
    private var overlaySessionBookId: String? = null
    private var overlayChapterIndexBySpine: Map<Int, Int> = emptyMap()
    private var overlaySpineByChapter: Map<Int, Int> = emptyMap()
    /**
     * Clip-to-chunk anchors for the narrating chapter, built from the same
     * text-element anchors the reader highlights. Empty until anchored (or
     * when anchoring is impossible); the highlight falls back to the
     * fractional estimate then, never to a stale table.
     */
    private var overlayClipSpans: List<SharedOverlayClipChunkSpan> = emptyList()
    private var overlaySpansBookId: String? = null
    private var overlaySpansChapter: Int = -1
    private var overlayPlayback by mutableStateOf(SharedMediaOverlayPlaybackState())
    private var overlayChapterIndex by mutableStateOf(0)
    private var overlaySpeed by mutableStateOf(1f)
    private var lastOverlaySpine: Int? = null
    private var remoteCommandsInstalled = false
    private var currentChunks: List<IosTtsListenChunk> = emptyList()
    private var currentChunkIndex = -1
    private var speechBaseOffset = 0
    private var latestWordOffset = 0
    private var wantsPlayback = false
    private var audioSessionActive = false
    private var audioSessionGeneration = 0
    private var interruptionState = LocalTtsInterruptionState()
    private var sleepTimerJob: Job? = null
    private var persistJob: Job? = null
    /** Replacements of the latest start, so a later resume restarts with the same text. */
    private var lastReplacements: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences()
    private val interruptionMonitor = IosTtsAudioInterruptionMonitor(::handleAudioInterruption)

    init {
        observeSharedEngine()
        observeOverlayEngine()
        progressStore.load().forEach { (bookId, progress) ->
            progressByBook[bookId] = progress
        }
        overlaySpeed = listenDefaults.doubleForKey(IOS_LISTEN_TTS_OVERLAY_SPEED_KEY)
            .toFloat().takeIf { it in 0.5f..3.0f } ?: 1f
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
        iosTtsListenLog("previewVoice voice=${identifier ?: "<system>"}")
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
            rememberOverlayFor(book.id, content)
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
        lastReplacements = replacements
        scope.launch {
            val content = contentCache[bookId]
                ?: withContext(Dispatchers.Default) { loadContentOrNull(book, replacements) }
                ?: run {
                    val message = lastContentError ?: "This book cannot be read with text-to-speech"
                    iosTtsListenLog("start() ABORTED bookId=$bookId error=$message")
                    preparedError = message
                    return@launch
                }
            contentCache[bookId] = content
            rememberOverlayFor(bookId, content)
            chapterTitlesByBook[bookId] = content.chapters.map { it.title }
            iosTtsListenLog(
                "start() content ready bookId=$bookId chapters=${content.chapters.size} " +
                    "chunks=${content.chapters.sumOf { it.chunks.size }}"
            )
            // Prefer publisher narration when present; an explicit saved pick
            // always wins. No silent fallback: the session stays on the
            // resolved source until the user switches it.
            if (listenSourceFor(bookId).isOverlay() && content.mediaOverlay.hasNarration && overlayEngine != null) {
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
                sessionSpeechRate = (saved?.speechRate ?: 1f).coerceIn(0.5f, 3f)
                sessionPitch = (saved?.pitch ?: 1f).coerceIn(0.5f, 2f)
                startOverlaySession(book, content, requestedChapter)
                return@launch
            }
            sessionIsOverlay = false
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
                preparedError = if (book.type == FileType.PDF) {
                        "This PDF has no extractable text. Scanned documents need OCR before " +
                        "text-to-speech can read them."
                } else {
                    "This book contains no readable text"
                }
                return@launch
            }
            currentBookId = bookId
            currentBook = book
            currentBookTitle = book.title?.takeIf(String::isNotBlank) ?: book.displayName
            currentBookAuthor = book.author
            chapterCount = content.chapters.size
            currentChapterIndex = readableChapter
            currentChunks = content.chapters[readableChapter].chunks
            chunksWereStartedForThisChapter = false
            val startChunk = if (policy == SharedTtsListenStartPolicy.RESUME && readableChapter == savedChapter) {
                (saved?.chunkIndex ?: 0).coerceIn(0, currentChunks.lastIndex)
            } else {
                0
            }
            wantsPlayback = true
            sessionSpeechRate = (saved?.speechRate ?: 1f).coerceIn(0.5f, 3f)
            sessionPitch = (saved?.pitch ?: 1f).coerceIn(0.5f, 2f)
            preparedError = null
            sessionFinished = false
            sessionEndedByStop = false
            sleepTimerRemainingMs = 0L
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
        if (sessionIsOverlay) {
            pauseOverlay()
            return
        }
        pauseInternal()
    }

    private fun pauseOverlay() {
        iosTtsListenLog("pauseOverlay() chapter=$currentChapterIndex")
        wantsPlayback = false
        generation += 1
        overlayEngine?.pause()
        persistNow(progressForOverlay(currentChapterIndex))
        updateBookTtsNowPlaying()
    }

    private fun pauseInternal() {
        if (currentChunks.isEmpty()) return
        iosTtsListenLog("pause() chunk=$currentChunkIndex")
        wantsPlayback = false
        generation += 1
        enginePause()
        persistNow(progressFor(currentChapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        updateBookTtsNowPlaying()
    }

    fun resume() {
        interruptionState = LocalTtsInterruptionState()
        if (sessionIsOverlay) {
            if (overlayPlayback.isPlaying || overlayPlayback.isLoading) return
            iosTtsListenLog("resumeOverlay() chapter=$currentChapterIndex")
            wantsPlayback = true
            if (overlayPlayback.hasBook) {
                overlayEngine?.resume()
            } else {
                val book = currentBook
                val hasContent = currentBookId?.let(contentCache::get) != null
                if (book != null && hasContent) {
                    playOverlayChapterInternal(currentChapterIndex)
                }
            }
            updateBookTtsNowPlaying()
            return
        }
        // Selected narration but idle (e.g. right after switching sources):
        // play starts it rather than stalling on empty TTS chunks.
        val selectedBookId = currentBookId
        val selectedBook = currentBook
        if (selectedBookId != null && selectedBook != null &&
            listenSourceFor(selectedBookId).isOverlay() &&
            overlayInfoFor(selectedBookId).hasNarration && overlayEngine != null
        ) {
            iosTtsListenLog("resume() starting selected narration bookId=$selectedBookId")
            start(selectedBook, SharedTtsListenStartPolicy.RESUME, null, lastReplacements)
            return
        }
        // Switched back to TTS with no live chunks: restart from saved
        // progress rather than stalling the play button.
        if ((currentChunks.isEmpty() || currentChunkIndex < 0) && selectedBook != null) {
            iosTtsListenLog("resume() restarting TTS bookId=$selectedBookId")
            start(selectedBook, SharedTtsListenStartPolicy.RESUME, null, lastReplacements)
            return
        }
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
        iosTtsListenLog("stop() bookId=$currentBookId chunk=$currentChunkIndex overlay=$sessionIsOverlay")
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        persistNow(progressFor(currentChapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        generation += 1
        engineStop()
        stopOverlaySession()
        currentChunks = emptyList()
        currentChunkIndex = -1
        currentBookId = null
        currentBook = null
        wantsPlayback = false
        sessionEndedByStop = true
        deactivateAudioSession()
        clearBookTtsNowPlaying()
    }

    /**
     * Releases Listen's hold on the shared engine when another surface claims it.
     *
     * Android benchmark: `BookTtsSessionCoordinator` never stops TTS to hand off — the reader
     * and audiobook Listen are the same engine there, so a reader session simply replaces the
     * audiobook one. Now that iOS Listen drives the same engine, calling [stop] for a handoff
     * would tear down the *new* session: the reader's start and Listen's teardown land on one
     * engine, and whichever runs second wins. This drops Listen's own session only.
     */
    fun releaseForHandoff() {
        if (!state.connected) return
        iosTtsListenLog("releaseForHandoff bookId=$currentBookId chapter=$currentChapterIndex")
        persistNow(progressFor(currentChapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        // Narration owns real audio on the shared overlay engine, so unlike
        // TTS it must be stopped rather than released: the winner speaks
        // elsewhere and both would otherwise be heard at once.
        stopOverlaySession()
        currentChunks = emptyList()
        currentChunkIndex = -1
        currentBookId = null
        currentBook = null
        wantsPlayback = false
        sessionFinished = false
        sessionEndedByStop = true
        // The engine's audio session is deliberately left alone: the surface that just claimed
        // it is using it, and Listen never owned it directly.
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
            // pause(), not pauseInternal(): an interruption must also still
            // narration, which owns real audio on another engine.
            LocalTtsInterruptionAction.PAUSE -> pause()
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
        // Narration is clip-sequenced, not chunk-sequenced; the sheet hides
        // these controls in narration mode, so reaching here is a no-op.
        if (sessionIsOverlay) return
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
        // No TTS fallback: a chapter without narration in narration mode is
        // unreachable by tapping (the sheet disables it).
        if (sessionIsOverlay) {
            val bookId = currentBookId ?: return
            val content = contentCache[bookId] ?: return
            if (index in content.mediaOverlay.narratedChapterIndices) playOverlayChapterInternal(index)
            return
        }
        val content = contentCache[currentBookId ?: return] ?: return
        playChapterInternal(index.coerceIn(0, content.chapters.lastIndex), 0)
    }

    private fun playOverlayChapterInternal(index: Int) {
        val book = currentBook ?: return
        val bookId = currentBookId ?: return
        val content = contentCache[bookId] ?: return
        if (index !in content.mediaOverlay.narratedChapterIndices) return
        iosTtsListenLog("playOverlayChapterInternal chapter=$index")
        currentChapterIndex = index
        overlayChapterIndex = index
        persistNow(progressForOverlay(index))
        sessionFinished = false
        wantsPlayback = true
        scope.launch {
            overlaySession?.setSpeed(overlaySpeed)
            val started = overlaySession?.start(index, readerOffset = null) ?: false
            if (!started) preparedError = "This chapter has no narration audio"
            else refreshOverlayClipSpans(bookId, index)
            updateBookTtsNowPlaying()
        }
    }

    /** Publisher-narration availability for a book, or none when unknown. */
    fun overlayInfoFor(bookId: String): SharedListenMediaOverlayInfo =
        overlayInfoByBook[bookId] ?: SharedListenMediaOverlayInfo()

    /** Explicit TTS/narration choice, defaulting to narration when present. Never a silent switch. */
    fun listenSourceFor(bookId: String): SharedListenAudioSource =
        resolveSharedListenAudioSource(
            hasNarration = overlayInfoFor(bookId).hasNarration && overlayEngine != null,
            savedPreference = listenSourceByBook[bookId],
        )

    /**
     * Explicit source choice. Never starts playback: switching stops the other
     * engine outright and records the pick, and the next play tap starts the
     * newly chosen source from where it left off. The host arbitrates first
     * (`onMediaOverlayStarting` / `onListenSessionStarting`), so the engines
     * stopped here belong to the outgoing session.
     */
    fun selectListenSource(book: BookItem, source: SharedListenAudioSource) {
        // Guard before persisting: an unavailable source must never pin the
        // book to a choice that cannot play.
        if (source.isOverlay() && (!overlayInfoFor(book.id).hasNarration || overlayEngine == null)) return
        listenDefaults.setObject(source.name, forKey = IOS_LISTEN_TTS_SOURCE_KEY_PREFIX + book.id)
        listenSourceByBook[book.id] = source
        if (source.isOverlay()) {
            engineStop()
        } else {
            stopOverlaySession()
        }
    }

    /** Source-aware speed: publisher-narration speed or the TTS rate. */
    fun setListenSpeed(rate: Float) {
        if (sessionIsOverlay) {
            setOverlaySpeed(rate)
            return
        }
        setParameters(rate, sessionPitch)
    }

    fun setOverlaySpeed(speed: Float) {
        val resolved = speed.coerceIn(0.5f, 3.0f)
        overlaySpeed = resolved
        listenDefaults.setDouble(resolved.toDouble(), forKey = IOS_LISTEN_TTS_OVERLAY_SPEED_KEY)
        overlaySession?.setSpeed(resolved)
    }

    private fun loadListenSource(bookId: String) {
        // Stored picks only: absent means "prefer narration when present",
        // resolved per call by [listenSourceFor]. Storing a default here
        // would pin TTS and silently defeat the overlay preference.
        val raw = listenDefaults.stringForKey(IOS_LISTEN_TTS_SOURCE_KEY_PREFIX + bookId)
        val saved = raw?.let { runCatching { SharedListenAudioSource.valueOf(it) }.getOrNull() }
        if (saved != null) {
            listenSourceByBook[bookId] = saved
        } else {
            listenSourceByBook.remove(bookId)
        }
    }

    /**
     * Records narration availability for prepared content.
     *
     * Cheap facts only (OPF index + chapter mapping); SMIL bodies stay lazy
     * behind the session's document cache. Called wherever content is cached
     * so badges and the source switcher agree with the player.
     */
    private fun rememberOverlayFor(bookId: String, content: IosTtsListenBook) {
        overlayInfoByBook[bookId] = content.mediaOverlay
        if (!listenSourceByBook.containsKey(bookId)) loadListenSource(bookId)
        iosTtsListenLog(
            "rememberOverlay bookId=$bookId hasNarration=${content.mediaOverlay.hasNarration} " +
                "narrated=${content.mediaOverlay.narratedChapterIndices.size} " +
                "source=${listenSourceFor(bookId)}"
        )
    }

    private fun overlaySessionFor(book: BookItem, content: IosTtsListenBook): SharedMediaOverlaySession? {
        val engine = overlayEngine ?: return null
        if (!content.mediaOverlay.hasNarration) return null
        if (overlaySession != null && overlaySessionBookId == book.id) return overlaySession
        val readSmil = iosMediaOverlaySmilReaderFor(book.path ?: "") ?: return null
        val cache = SharedMediaOverlayDocumentCache(
            content.overlayIndex,
            readSmil = readSmil,
        )
        val spineByChapter = sharedMediaOverlaySpineItemIndexByChapter(
            chapterContentPaths = content.chapterContentPaths,
            overlayIndex = content.overlayIndex,
        )
        overlayChapterIndexBySpine = spineByChapter.entries
            .sortedBy { it.key }
            .associate { (chapter, spine) -> spine to chapter }
        overlaySpineByChapter = spineByChapter
        // Last starter owns continuation on the shared engine: the reader's
        // session reinstalls its own hook on its next start, and vice versa.
        // Without this, whoever stopped last would keep narrating the other.
        (engine as? IosSharedMediaOverlayPlayback)?.attachArchive(book)
        val session = SharedMediaOverlaySession(
            engine = engine,
            projector = IosListenOverlayProjector(cache, overlayChapterIndexBySpine),
            bookId = book.id,
            bookTitle = content.title,
            narrator = content.mediaOverlay.narrator,
            totalDurationMs = content.overlayIndex.totalDurationMs,
            overlayIndex = content.overlayIndex,
            spineItemIndexByChapter = spineByChapter,
            spineItemsInReadingOrder = sharedMediaOverlaySpineItemsInReadingOrder(
                content.chapters.size,
                spineByChapter,
            ),
            // The reader's store: a listener who stops in Listen resumes at the same sentence when
            // they open the book, and the reverse.
            narrationProgressStore = IosMediaOverlayNarrationProgressStore.shared,
        )
        overlaySession = session
        overlaySessionBookId = book.id
        return session
    }

    /**
     * Listen-side overlay projector: chapter-level only.
     *
     * Same contract as Android's (`ListenOverlayProjector`): Listen has no
     * text surface in narration mode, so positions resolve to chapters and
     * fragments stay null — audio-first, with nothing faked for paint.
     */
    private class IosListenOverlayProjector(
        private val cache: SharedMediaOverlayDocumentCache,
        private val chapterIndexBySpine: Map<Int, Int>,
    ) : SharedMediaOverlayProjectionSource {
        override fun document(spineItemIndex: Int): SharedMediaOverlayDocument? =
            cache.document(spineItemIndex)

        override suspend fun project(spineItemIndex: Int, clipIndex: Int): SharedMediaOverlayProjection? {
            val document = cache.document(spineItemIndex) ?: return null
            val clip = document.clips.getOrNull(clipIndex) ?: return null
            return SharedMediaOverlayProjection(
                spineItemIndex = spineItemIndex,
                clipIndex = clipIndex,
                chapterIndex = chapterIndexBySpine[spineItemIndex],
                fragment = null,
                elementId = clip.elementId?.takeIf(String::isNotBlank),
            )
        }

        override suspend fun clipIndexForReaderPosition(spineItemIndex: Int, readerOffset: Int): Int? = null
    }

    private fun startOverlaySession(
        book: BookItem,
        content: IosTtsListenBook,
        chapterIndex: Int,
    ) {
        val session = overlaySessionFor(book, content) ?: run {
            preparedError = "Publisher narration is unavailable for this book"
            return
        }
        val narrated = content.mediaOverlay.narratedChapterIndices
        // Nearest narrated chapter at or after the target, else the first —
        // navigation within narration, never a switch back to synthesis.
        val target = chapterIndex.takeIf { it in narrated }
            ?: narrated.filter { it >= chapterIndex }.minOrNull()
            ?: narrated.minOrNull()
            ?: run {
                preparedError = "This book has no narrated chapters"
                return
            }
        currentBookId = book.id
        currentBook = book
        currentBookTitle = content.title
        currentBookAuthor = book.author
        chapterCount = content.chapters.size
        currentChapterIndex = target
        overlayChapterIndex = target
        currentChunks = emptyList()
        currentChunkIndex = -1
        chunksWereStartedForThisChapter = false
        preparedError = null
        sessionFinished = false
        sessionEndedByStop = false
        sleepTimerRemainingMs = 0L
        sessionIsCloud = false
        sessionIsOverlay = true
        lastOverlaySpine = null
        wantsPlayback = true
        persistNow(progressForOverlay(target))
        configureAudioSession(active = true)
        installBookTtsRemoteCommands()
        generation += 1
        iosTtsListenLog("startOverlaySession chapter=$target bookId=${book.id}")
        scope.launch {
            session.setSpeed(overlaySpeed)
            val started = session.start(target, readerOffset = null)
            if (!started) {
                preparedError = "This chapter has no narration audio"
            } else {
                refreshOverlayClipSpans(book.id, target)
            }
            updateBookTtsNowPlaying()
        }
        updateBookTtsNowPlaying()
    }

    private fun progressForOverlay(chapterIndex: Int): SharedBookTtsListeningProgress {
        val bookId = currentBookId ?: return SharedBookTtsListeningProgress(bookId = "")
        val safeChapterCount = chapterCount.coerceAtLeast(1)
        return SharedBookTtsListeningProgress(
            bookId = bookId,
            chapterIndex = chapterIndex.coerceIn(0, safeChapterCount - 1),
            chunkIndex = 0,
            progressPercent = (chapterIndex.coerceIn(0, safeChapterCount - 1).toFloat() / safeChapterCount * 100f)
                .coerceIn(0f, 99.9f),
            speechRate = sessionSpeechRate,
            pitch = sessionPitch,
            completed = false,
            updatedAt = currentTimestamp(),
        )
    }

    private fun stopOverlaySession() {
        overlaySession?.stop()
        overlaySessionBookId = null
        lastOverlaySpine = null
        sessionIsOverlay = false
        overlayClipSpans = emptyList()
        overlaySpansBookId = null
        overlaySpansChapter = -1
    }

    /**
     * Drops narration bookkeeping when another surface claims the shared
     * overlay engine (arbiter's `stopListenOverlay`).
     *
     * The engine is already stopped by the winner's claim; this only clears
     * Listen's session flags and persists position, so the sheet never
     * reports a session it no longer owns.
     */
    fun stopOverlayForHandoff() {
        if (!sessionIsOverlay) return
        iosTtsListenLog("stopOverlayForHandoff bookId=$currentBookId chapter=$currentChapterIndex")
        persistNow(progressForOverlay(currentChapterIndex))
        overlaySessionBookId = null
        lastOverlaySpine = null
        sessionIsOverlay = false
        overlayClipSpans = emptyList()
        overlaySpansBookId = null
        overlaySpansChapter = -1
    }

    /** Transcript rows for a narrating chapter: the same texts the panel shows. */
    private fun overlayChapterTranscriptTexts(bookId: String, chapter: Int): List<String> =
        contentCache[bookId]?.chapters?.getOrNull(chapter)
            ?.chunks?.map { it.text }?.filter { it.isNotBlank() }
            .orEmpty()

    /**
     * Anchors the narrating chapter's clips to its transcript chunks, using
     * the same text-element anchors the reader highlights — one line at a
     * time instead of the fractional estimate. Clears (rather than keeps) on
     * any mismatch, so a stale chapter's anchors can never display.
     */
    private suspend fun refreshOverlayClipSpans(bookId: String, chapter: Int) {
        val chunkTexts = overlayChapterTranscriptTexts(bookId, chapter)
        val session = overlaySession?.takeIf {
            sessionIsOverlay && overlaySessionBookId == bookId &&
                it.currentSpineItemIndex == overlaySpineByChapter[chapter]
        }
        val entries = session?.currentPlanEntries.orEmpty()
        if (entries.isEmpty() || chunkTexts.isEmpty()) {
            if (overlaySpansBookId == bookId && overlaySpansChapter == chapter) {
                overlayClipSpans = emptyList()
            }
            return
        }
        val book = currentBook?.takeIf { it.id == bookId } ?: return
        val readHtml = iosMediaOverlaySmilReaderFor(book.path ?: "") ?: return
        val spans = withContext(Dispatchers.Default) {
            runCatching {
                sharedOverlayClipChunkSpans(
                    clips = entries,
                    readHtml = { path -> runCatching { readHtml(path) }.getOrNull() },
                    chunkTexts = chunkTexts,
                )
            }.getOrDefault(emptyList())
        }
        // The chapter moved on while anchoring: never display the old table.
        if (!sessionIsOverlay || currentBookId != bookId || overlayChapterIndex != chapter) return
        if (overlaySession?.currentPlanEntries != entries) return
        overlayClipSpans = spans
        overlaySpansBookId = bookId
        overlaySpansChapter = chapter
    }

    private fun observeOverlayEngine() {
        val engine = overlayEngine ?: return
        scope.launch {
            engine.state.collect { state ->
                // The engine is shared with the reader: only track it while a
                // Listen narration session owns the book. Anything else is
                // the reader's playback, which must not move Listen's UI.
                if (!sessionIsOverlay || overlaySessionBookId != currentBookId) return@collect
                overlayPlayback = state
                val spine = state.spineItemIndex ?: run {
                    lastOverlaySpine = null
                    return@collect
                }
                if (spine == lastOverlaySpine) return@collect
                lastOverlaySpine = spine
                val chapter = overlayChapterIndexBySpine[spine] ?: return@collect
                overlayChapterIndex = chapter
                currentChapterIndex = chapter
                persistNow(progressForOverlay(chapter))
                // Narration crossing into a new chapter carries the transcript
                // anchors with it, like Android's host reload does.
                currentBookId?.let { refreshOverlayClipSpans(it, chapter) }
                updateBookTtsNowPlaying()
            }
        }
    }

    /** Stops only the voice-preview utterance, leaving book playback untouched. */
    fun stopVoicePreview() {
        iosTtsListenLog("stopVoicePreview isSpeaking=${previewSynthesizer.isSpeaking()}")
        previewSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    /** Word-level position within the current chunk, for word-accurate resume. */
    val currentSpokenOffset: Int get() = latestWordOffset

    /**
     * The engine tag for the current session.
     *
     * Listen drives the shared engine directly, so the voice-settings facade reports the real
     * tag rather than a placeholder.
     */
    val enginePlaybackSource: String? get() = localEngine.playbackSource

    /**
     * Engine currently holding Listen's session, or null when it has none.
     *
     * Read by [IosListeningArbiter] to decide whether a competing surface must *stop* Listen or
     * merely *release* it: the two are only equivalent when both surfaces share an engine, and
     * getting that backwards is what previously made a winner stop itself.
     */
    val sessionEngine: SharedTtsEngine?
        get() = when {
            currentBookId == null -> null
            sessionIsCloud -> SharedTtsEngine.CLOUD
            else -> SharedTtsEngine.LOCAL
        }

    /** The host's backing store for Listen's engine choice, kept beside the other Listen keys. */
    val listenTtsDefaults: NSUserDefaults get() = listenDefaults

    /**
     * Pins Listen to cloud or device speech.
     *
     * Android benchmark: `saveListenTtsMode`. Any explicit pick makes Listen stop following the
     * reader's engine, which is why the host stores the choice rather than the resolved value.
     */
    fun setCloudModeEnabled(enabled: Boolean) {
        val resolved = enabled && cloudTts != null
        if (cloudModeEnabled == resolved) return
        cloudModeEnabled = resolved
        onCloudModeChanged(resolved)
        iosTtsListenLog("setCloudModeEnabled cloud=$resolved")
    }

    /** Tracks the cloud voice chosen in the shared settings sheet. */
    fun setCloudVoiceIdentifier(identifier: String?) {
        cloudVoiceIdentifier = identifier
        listenDefaults.setObject(
            ReaderTtsVoiceOverride.encodeVoiceOverride(identifier),
            forKey = IOS_LISTEN_TTS_CLOUD_VOICE_OVERRIDE_KEY,
        )
        iosTtsListenLog("setCloudVoice voice=${identifier ?: "<inherit>"}")
    }

    /** Whether Listen picked its own cloud voice instead of following the reader's speaker. */
    fun hasOwnCloudVoice(): Boolean =
        listenDefaults.objectForKey(IOS_LISTEN_TTS_CLOUD_VOICE_OVERRIDE_KEY) != null

    /**
     * Follows the reader's live speaker while no override is stored; never persists, so the
     * absent-key tri-state survives the host's sync. Once Listen picks its own voice this is a
     * no-op and reader edits stop reaching it.
     */
    fun inheritCloudVoiceIdentifier(identifier: String?) {
        if (hasOwnCloudVoice() || cloudVoiceIdentifier == identifier) return
        cloudVoiceIdentifier = identifier
    }

    /**
     * Effective cloud voice for a new session: Listen's pick when stored, else the reader's
     * live speaker. Android benchmark: `loadListenTtsSpeaker` with reader inheritance.
     */
    fun resolveListenCloudVoiceIdentifier(inheritedVoiceIdentifier: String?): String? =
        ReaderTtsVoiceOverride.resolveVoiceIdentifier(
            isOverrideStored = hasOwnCloudVoice(),
            storedOverride = listenDefaults.stringForKey(IOS_LISTEN_TTS_CLOUD_VOICE_OVERRIDE_KEY),
            inheritedVoiceIdentifier = inheritedVoiceIdentifier,
        )

    /** True when this build can serve cloud speech for Listen. */
    val isCloudAvailable: Boolean get() = cloudTts != null

    fun setParameters(rate: Float, pitch: Float) {
        val safeRate = rate.coerceIn(0.5f, 3f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        sessionSpeechRate = safeRate
        sessionPitch = safePitch
        persistNow(progressFor(currentChapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        val chunk = currentChunks.getOrNull(currentChunkIndex) ?: return
        // Only the device engine has rate/pitch: cloud synthesis takes them server-side.
        // In-memory push (see startEngineAt): Listen's rate must not rewrite
        // the reader's stored pick.
        if (!sessionEngineIsCloud) localEngine.applySessionVoice(selectedVoiceIdentifier, safeRate, safePitch)
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
                sleepTimerRemainingMs = remainingSeconds * 1_000L
                delay(1_000)
                // Keep the timer anchored to actual speech, matching Android's
                // shared contract. Pausing TTS must pause the countdown too.
                remainingSeconds = advanceSharedSleepTimer(remainingSeconds, state.isPlaying)
            }
            sleepTimerRemainingMs = 0L
            sleepTimerJob = null
            stopForSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerRemainingMs = 0L
    }

    private fun moveByChunk(delta: Int) {
        // Lock-screen previous/next stepping: clips in narration mode, TTS
        // chunks otherwise. Both stay inside the running source.
        if (sessionIsOverlay) {
            if (delta > 0) overlaySession?.nextClip() else overlaySession?.previousClip()
            return
        }
        if (currentChunks.isEmpty() || currentChunkIndex < 0) return
        val target = currentChunkIndex + delta
        if (target !in 0..currentChunks.lastIndex) return
        wantsPlayback = true
        startEngineAt(target, playWhenReady = true)
    }

    private fun moveChapterBy(direction: Int) {
        // Narration steps across narrated chapters only: skipping an
        // un-narrated middle is navigation within narration, and ending the
        // run there would strand the listener on silence.
        if (sessionIsOverlay) {
            moveOverlayChapterBy(direction)
            return
        }
        val content = contentCache[currentBookId ?: return] ?: return
        val target = if (direction > 0) {
            content.chapters.indexOfFirst { it.index > currentChapterIndex && it.chunks.isNotEmpty() }
        } else {
            content.chapters.indexOfLast { it.index < currentChapterIndex && it.chunks.isNotEmpty() }
        }
        if (target >= 0) playChapterInternal(target, 0)
    }

    private fun moveOverlayChapterBy(direction: Int) {
        val bookId = currentBookId ?: return
        val content = contentCache[bookId] ?: return
        val narrated = content.mediaOverlay.narratedChapterIndices.sorted()
        if (narrated.isEmpty()) return
        val target = if (direction > 0) {
            narrated.firstOrNull { it > currentChapterIndex }
        } else {
            narrated.lastOrNull { it < currentChapterIndex }
        } ?: return
        playOverlayChapterInternal(target)
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
        chunksWereStartedForThisChapter = false
        val safeChunk = chunkIndex.coerceIn(0, currentChunks.lastIndex)
        persistNow(progressFor(chapter.index, safeChunk, completed = false))
        sessionFinished = false
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
        sessionFinished = false
        // Chaining into a later chapter keeps one USD session spend alive; a fresh start opens a
        // new one. Android benchmark: `startBookListeningChapter(continueSession = ...)` fed from
        // the chapter-advance path.
        val continueSession = chunksWereStartedForThisChapter
        chunksWereStartedForThisChapter = true
        val cloud = cloudTts
        if (cloudModeEnabled && cloud != null) {
            sessionIsCloud = true
            iosTtsListenLog(
                "startEngineAt CLOUD chapter=$currentChapterIndex chunk=$safeChunk of ${chunks.size} " +
                    "playWhenReady=$playWhenReady continueSession=$continueSession"
            )
            cloud.start(
                chunks = engineChunks,
                bookTitle = currentBookTitle,
                bookId = currentBookId,
                startChunkIndex = safeChunk,
                playWhenReady = playWhenReady,
                continueSession = continueSession,
                playbackSource = SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK,
                totalChapters = chapterCount,
                // Surface separation (Android `startBookListeningChapter` parity): Listen speaks
                // with its own pinned cloud decision and its own cloud voice, never the
                // reader's settings. The engine trusts the caller and still enforces backend
                // availability and wallet spend itself.
                speakerId = cloudVoiceIdentifier,
                cloudModeAsserted = true,
            )
            return
        }
        sessionIsCloud = false
        iosTtsListenLog(
            "startEngineAt chapter=$currentChapterIndex chunk=$safeChunk of ${chunks.size} " +
                "playWhenReady=$playWhenReady"
        )
        // Listen's own voice/rate choices, applied to the shared engine for this
        // session only: applySessionVoice is in-memory, so Listen's picks never
        // leak into the reader's stored settings, and a null (system-default)
        // pick clears the engine's stored voice instead of leaving a stale one.
        localEngine.applySessionVoice(selectedVoiceIdentifier, sessionSpeechRate, sessionPitch)
        localEngine.start(
            chunks = engineChunks,
            bookTitle = currentBookTitle,
            bookId = currentBookId,
            startChunkIndex = safeChunk,
            playWhenReady = playWhenReady,
            playbackSource = SHARED_TTS_PLAYBACK_SOURCE_AUDIOBOOK,
            totalChapters = chapterCount,
        )
    }

    /**
     * Whether this chapter has already handed a session to an engine.
     *
     * Reset when a new chapter begins so only the advance *within* a chapter and the chain *into
     * the next* chapter keep the spend open — a fresh Listen start must open a new one.
     */
    private var chunksWereStartedForThisChapter = false

    private fun currentChapterTitle(): String? {
        val content = currentBookId?.let { contentCache[it] } ?: return null
        return content.chapters.getOrNull(currentChapterIndex)?.title
    }

    /**
     * Builds the engine snapshot the shared projector consumes.
     *
     * Mirrors Android's `TtsState.toSharedTtsPlaybackSnapshot()`: the adapter is a thin
     * translation of whatever the engine exposes, with no Listen-specific interpretation.
     * Chapter and transcript both come from the engine's active chunk, which is why Listen
     * now has no transcript or chapter state of its own to keep in sync.
     */
    private val sessionEngineIsCloud: Boolean get() = sessionIsCloud && cloudTts != null

    /**
     * Progress for the running session.
     *
     * Both engines expose the same [ReaderTtsProgress], which is what makes the projection
     * engine-agnostic: the Listen state is built from this regardless of which engine speaks.
     */
    private fun engineProgress(): ReaderTtsProgress =
        if (sessionEngineIsCloud) cloudTts!!.state.progress else localEngine.progress

    private fun engineCompletionCount(): Long =
        if (sessionEngineIsCloud) cloudTts!!.state.completionCount else localEngine.completionCount

    private fun engineIsPlaying(): Boolean = if (sessionEngineIsCloud) {
        cloudTts!!.state.isPlaying
    } else {
        localEngine.state == SharedMobileEpubLocalTtsState.SPEAKING
    }

    /**
     * The engine has no PREPARING state, so "a live session with nothing spoken yet" is loading.
     * Cloud reports its own flag, which covers waiting on the network.
     */
    private fun engineIsLoading(): Boolean = if (sessionEngineIsCloud) {
        cloudTts!!.state.isLoading
    } else {
        localEngine.isSessionActive && localEngine.state == SharedMobileEpubLocalTtsState.IDLE
    }

    private fun engineErrorMessage(): String? =
        if (sessionEngineIsCloud) cloudTts!!.state.errorMessage else localEngine.errorMessage

    private fun enginePlaybackSource(): String? =
        if (sessionEngineIsCloud) cloudTts!!.playbackSource else localEngine.playbackSource

    private fun engineSessionBookId(): String? =
        if (sessionEngineIsCloud) cloudTts!!.sessionBookId else localEngine.sessionBookId

    private fun engineTotalChapters(): Int =
        if (sessionEngineIsCloud) cloudTts!!.sessionTotalChapters else localEngine.sessionTotalChapters

    /** Word offset is a device-engine detail; cloud has no equivalent. */
    private fun engineSpokenOffset(): Int =
        if (sessionEngineIsCloud) 0 else localEngine.currentSpokenOffset

    /** Transport, dispatched to whichever engine owns the session. */
    private fun enginePause() = if (sessionEngineIsCloud) cloudTts!!.pause() else localEngine.pause()

    /**
     * Stops the running session's engine.
     *
     * Only ever called for a session Listen owns; a handoff uses [releaseForHandoff], which must
     * not touch the engine the next surface just claimed.
     */
    private fun engineStop() {
        if (sessionEngineIsCloud) cloudTts!!.stop() else localEngine.stop()
        sessionIsCloud = false
    }

    private fun engineSnapshot(): SharedTtsPlaybackSnapshot {
        val engineProgress = engineProgress()
        val engineChunks = engineProgress.chunks
        val engineChunkIndex = engineProgress.currentChunkIndex
        val activeChunk = engineChunks.getOrNull(engineChunkIndex)
        val window = sharedTtsTranscriptWindow(engineChunkIndex, engineChunks.size)
        return SharedTtsPlaybackSnapshot(
            playbackSource = enginePlaybackSource(),
            bookId = engineSessionBookId(),
            isPlaying = engineIsPlaying(),
            isLoading = engineIsLoading(),
            chapterTitle = activeChunk?.chapterTitle?.takeIf(String::isNotBlank),
            chapterIndex = activeChunk?.chapterIndex,
            totalChapters = engineTotalChapters(),
            currentChunkIndex = engineChunkIndex,
            totalChunks = engineChunks.size,
            sessionFinished = sessionFinished,
            sessionEndedByStop = sessionEndedByStop,
            errorMessage = engineErrorMessage(),
            transcriptStartIndex = window.firstOrNull() ?: 0,
            transcriptChunks = if (window.isEmpty()) {
                emptyList()
            } else {
                window.map { engineChunks[it].text }
            },
        )
    }

    /**
     * The single place the Listen state is built.
     *
     * Android benchmark: `BookTtsAudiobookController.sharedPlaybackState`. Engine session
     * fields go through the shared projector so both platforms run the same mapping; the
     * coordinator-only signals the engine has no field for are merged back in afterwards.
     */
    private fun projectListenState(): SharedBookTtsListenState {
        // Narration mode projects the overlay engine, not the TTS engines:
        // the sheet stays on one source with no silent crossover. The
        // transcript is the same chapter text TTS would speak, with the
        // highlight anchored to the exact narrated line (the same
        // text-element anchors the reader highlights) and the fractional
        // estimate only as fallback — audio-first, never word sync.
        if (sessionIsOverlay && currentBookId != null) {
            val saved = currentBookId?.let(progressByBook::get)
            val titles = currentBookId?.let(chapterTitlesByBook::get).orEmpty()
            val bookId = currentBookId
            val chapterTexts = overlayChapterTranscriptTexts(bookId ?: "", overlayChapterIndex)
            val spans = overlayClipSpans.takeIf {
                overlaySpansBookId == bookId && overlaySpansChapter == overlayChapterIndex
            }.orEmpty()
            val estimatedChunk = sharedOverlayTranscriptChunkIndex(
                clipPlaybackIndex = overlayPlayback.clipIndex,
                clipCount = overlaySession?.clipCount ?: 0,
                chunkCount = chapterTexts.size,
                clipFraction = overlayPlayback.clipProgress,
                spans = spans,
            )
            val window = sharedTtsTranscriptWindow(estimatedChunk, chapterTexts.size)
            return SharedBookTtsListenState(
                connected = overlayPlayback.hasBook,
                bookId = currentBookId,
                isPlaying = overlayPlayback.isPlaying,
                isLoading = overlayPlayback.isLoading,
                chapterIndex = overlayChapterIndex,
                chapterCount = chapterCount,
                chunkIndex = estimatedChunk,
                chunkCount = chapterTexts.size,
                chapterTitle = titles.getOrNull(overlayChapterIndex),
                progressPercent = ((saved?.progressPercent ?: 0f) / 100f).coerceIn(0f, 1f),
                speechRate = overlayPlayback.speed.takeIf { it > 0f } ?: overlaySpeed,
                pitch = sessionPitch,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                sessionFinished = sessionFinished,
                sessionEndedByStop = sessionEndedByStop,
                error = preparedError ?: overlayPlayback.error,
                transcriptStartIndex = window.firstOrNull() ?: 0,
                transcriptChunks = if (window.isEmpty()) emptyList() else window.map { chapterTexts[it] },
            )
        }
        val projected = engineSnapshot().toSharedBookTtsListenState(
            progress = SharedTtsListenSavedProgress(
                chapterIndex = currentChapterIndex,
                speechRate = sessionSpeechRate,
                pitch = sessionPitch,
            ),
            preparedChapterCount = chapterCount,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
        )
        return projected.copy(
            error = preparedError ?: projected.error,
            sessionFinished = sessionFinished || projected.sessionFinished,
            sessionEndedByStop = sessionEndedByStop || projected.sessionEndedByStop,
        )
    }

    /**
     * Watches the shared engine for the two things only the coordinator can act on.
     *
     * What Listen *displays* no longer comes from here — that is projected out of the engine by
     * [projectListenState]. What remains is the coordinator's own job, which Android's
     * coordinator also keeps: noticing the end of a chapter to load the next one, and recording
     * position for persistence.
     *
     * Replaces the controller's own `AVSpeechSynthesizerDelegate` callbacks. `snapshotFlow` is
     * unavailable on this target's coroutines build, hence polling.
     */
    private fun observeSharedEngine() {
        scope.launch {
            var lastCompletionCount = localEngine.completionCount
            var lastChunkIndex = -1
            while (true) {
                val engineChunkIndex = engineProgress().currentChunkIndex
                val completionCount = engineCompletionCount()
                if (currentBookId != null) {
                    if (completionCount > lastCompletionCount) {
                        lastCompletionCount = completionCount
                        advancePastChapterEnd()
                    } else if (engineChunkIndex >= 0) {
                        currentChunkIndex = engineChunkIndex
                        latestWordOffset = engineSpokenOffset()
                        if (engineChunkIndex != lastChunkIndex) {
                            lastChunkIndex = engineChunkIndex
                            schedulePersist(engineChunkIndex)
                            updateBookTtsNowPlaying()
                        }
                    }
                }
                delay(ENGINE_OBSERVATION_INTERVAL_MS)
            }
        }
    }

    private fun advancePastChapterEnd() {
        val bookId = currentBookId ?: return
        val content = contentCache[bookId] ?: return
        val next = content.chapters.indexOfFirst { it.index > currentChapterIndex && it.chunks.isNotEmpty() }
        if (next < 0) {
            iosTtsListenLog("BOOK COMPLETED bookId=$bookId")
            persistNow(progressFor(currentChapterIndex, currentChunks.lastIndex, completed = true))
            generation += 1
            engineStop()
            sessionFinished = true
            sleepTimerRemainingMs = 0L
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
        persistNow(progressFor(currentChapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        generation += 1
        localEngine.stop()
        // The sleep timer owns the whole Listen session, whichever engine
        // holds it: narration must not outlive the timer that stopped TTS.
        stopOverlaySession()
        currentChunks = emptyList()
        currentChunkIndex = -1
        currentBookId = null
        currentBook = null
        wantsPlayback = false
        sessionEndedByStop = true
        deactivateAudioSession()
        clearBookTtsNowPlaying()
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
            sourceOffset = if (chunkIndex == currentChunkIndex) latestWordOffset else 0,
            progressPercent = percent,
            speechRate = sessionSpeechRate,
            pitch = sessionPitch,
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
        val bookTitle = book.title?.takeIf(String::isNotBlank)
            ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
        when (book.type) {
            FileType.PDF -> return IosTtsListenBook(
                bookId = book.id,
                title = bookTitle,
                chapters = buildIosPdfListenChapters(book, replacements),
            )
            FileType.EPUB -> {
                val built = buildIosEpubListenChapters(book, replacements)
                iosTtsListenLog(
                    "buildIosTtsListenContent fast EPUB path chapters=${built.chapters.size} " +
                        "elapsed=${currentTimestamp() - startedAt}ms"
                )
                return IosTtsListenBook(
                    bookId = book.id,
                    title = bookTitle,
                    chapters = built.chapters,
                    mediaOverlay = sharedListenMediaOverlayInfo(built.contentPaths, built.overlayIndex),
                    overlayIndex = built.overlayIndex,
                    chapterContentPaths = built.contentPaths,
                )
            }
            else -> {
                val shared = loadIosEpubBook(book)
                val splitStart = currentTimestamp()
                iosTtsListenLog(
                    "buildIosTtsListenContent loadIosEpubBook OK chapters=${shared.chapters.size} " +
                        "elapsed=${splitStart - startedAt}ms; splitting chunks..."
                )
                // Non-EPUB types never carry media overlays: narration is an
                // EPUB 3 package fact, so there is nothing to map here.
                val chapters = shared.chapters.mapIndexed { index, chapter ->
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
                return IosTtsListenBook(
                    bookId = book.id,
                    title = bookTitle,
                    chapters = chapters,
                )
            }
        }
    }

    private data class IosEpubListenContent(
        val chapters: List<IosTtsListenChapter>,
        val overlayIndex: SharedMediaOverlayIndex,
        val contentPaths: List<String>,
    )

    private fun buildIosEpubListenChapters(
        book: BookItem,
        replacements: ReaderTtsReplacementPreferences,
    ): IosEpubListenContent {
        val archive = openIosZipArchive(book.path)
            ?: error("EPUB path is unavailable")
        // Register the stable path (not this transient instance) so
        // narration's SMIL/audio borrows resolve through the store for a book
        // the reader never opened. Without this the OPF index above reports
        // narration while every archive read fails, which presents as
        // per-chapter "no narration audio" on a narrated book. Path-only, so
        // a reader-open book keeps its instance and nothing doubles the
        // resident 124 MB reference.
        book.path?.takeIf { it.isNotBlank() }?.let { IosEpubResourceStore.register(book.id, it) }
        val overlayIndex = runCatching { loadSharedEpubMediaOverlayIndex(archive) }
            .getOrDefault(SharedMediaOverlayIndex.EMPTY)
        val spineChapters = loadSharedEpubTtsChapters(
            archive = archive,
            fileName = book.displayName.ifBlank { (book.path ?: "").substringAfterLast('/') },
        )
        iosTtsListenLog("buildIosEpubListenChapters spineDocs=${spineChapters.size} ref=${book.path}")
        val chapters = spineChapters.mapIndexed { index, spineChapter ->
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
        return IosEpubListenContent(
            chapters = chapters,
            overlayIndex = overlayIndex,
            contentPaths = spineChapters.map { it.contentPath },
        )
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
        // Scoped resolve: folder-book refs need held scope across FPDF open.
        // `resolveIosReadablePath` alone dies on device before FPDF reads.
        val scope = acquireScopeForBookPath(path)
        try {
            return extractIosPdfPageTextsScoped(path)
        } finally {
            scope?.close()
        }
    }

    private suspend fun extractIosPdfPageTextsScoped(path: String): List<String> {
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
        if (sessionIsOverlay) {
            // Narration steps by clip, not by TTS chunk: keep both directions
            // live while a chapter is loaded rather than reporting chunk -1.
            commands.previousTrackCommand.enabled = overlayPlayback.hasBook
            commands.nextTrackCommand.enabled = overlayPlayback.hasBook
            return
        }
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
