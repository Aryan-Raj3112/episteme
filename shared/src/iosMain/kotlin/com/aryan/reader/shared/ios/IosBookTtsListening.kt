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
import com.aryan.reader.shared.SharedBookTtsListenState
import com.aryan.reader.shared.SharedBookTtsListeningProgress
import com.aryan.reader.shared.SharedTtsListenStartPolicy
import com.aryan.reader.shared.calculateSharedTtsAudiobookProgress
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.splitSharedTtsListenChunks
import com.aryan.reader.shared.reader.loadSharedEpubTtsChapters
import com.aryan.reader.shared.pdf.IosPdfiumRuntime
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSRange
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults

internal const val IOS_TTS_LISTEN_TAG = "ReaderBookTtsIOS"

internal fun iosTtsListenLog(message: String) {
    println("[$IOS_TTS_LISTEN_TAG] $message")
}

internal fun iosTtsListenLogError(message: String, error: Throwable?) {
    println("[$IOS_TTS_LISTEN_TAG] $message")
    error?.let {
        println("[$IOS_TTS_LISTEN_TAG]   ${it::class.simpleName}: ${it.message}")
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
internal class IosBookTtsListeningController {
    var state by mutableStateOf(SharedBookTtsListenState())
        private set

    val progressByBook: MutableMap<String, SharedBookTtsListeningProgress> = mutableStateMapOf()
    val chapterTitlesByBook: MutableMap<String, List<String>> = mutableStateMapOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val progressStore = IosTtsListeningProgressStore()
    private val contentCache = mutableMapOf<String, IosTtsListenBook>()

    private val synthesizer = AVSpeechSynthesizer()
    private val delegate = IosBookTtsSpeechDelegate(
        onStarted = ::utteranceStarted,
        onFinished = ::utteranceFinished,
        onCancelled = ::utteranceCancelled,
        onWillSpeakRange = ::utteranceWillSpeakRange,
    )

    private var generation = 0L
    private var activeUtterance: AVSpeechUtterance? = null
    private var currentBookId: String? = null
    private var currentChunks: List<IosTtsListenChunk> = emptyList()
    private var currentChunkIndex = -1
    private var currentChapterIndex = 0
    private var chapterCount = 0
    private var speechBaseOffset = 0
    private var latestWordOffset = 0
    private var wantsPlayback = false
    private var audioSessionActive = false
    private var sleepTimerJob: Job? = null
    private var persistJob: Job? = null

    init {
        synthesizer.delegate = delegate
        progressStore.load().forEach { (bookId, progress) ->
            progressByBook[bookId] = progress
        }
        iosTtsListenLog("Controller initialized; restored progress for ${progressByBook.size} book(s)")
    }

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
        iosTtsListenLog(
            "start() bookId=$bookId name=${book.displayName} type=${book.type} policy=$policy " +
                "chapterIndex=$chapterIndex path=${book.path ?: "<null>"} pathExists=" +
                (book.path?.let { NSFileManager.defaultManager.fileExistsAtPath(it) } == true)
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
                iosTtsListenLog("start() NO READABLE CHAPTER bookId=$bookId")
                state = SharedBookTtsListenState(error = "This book contains no readable text")
                return@launch
            }
            currentBookId = bookId
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
            generation += 1
            iosTtsListenLog("start() speaking chapter=$readableChapter chunk=$startChunk of ${currentChunks.size} chunks")
            speakChunkAt(startChunk, fromOffset = 0, wantsPlayback = true)
        }
    }

    fun togglePlay() {
        if (state.isPlaying || state.isLoading) pause() else resume()
    }

    fun pause() {
        if (currentChunks.isEmpty()) return
        iosTtsListenLog("pause() chunk=$currentChunkIndex")
        wantsPlayback = false
        generation += 1
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        state = state.copy(isPlaying = false, isLoading = false)
        persistNow(progressFor(state.chapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
    }

    fun resume() {
        if (currentChunks.isEmpty() || currentChunkIndex < 0) return
        if (state.isPlaying || state.isLoading) return
        iosTtsListenLog("resume() chunk=$currentChunkIndex wordOffset=$latestWordOffset")
        wantsPlayback = true
        speakChunkAt(currentChunkIndex, latestWordOffset, wantsPlayback = true)
    }

    fun stop() {
        iosTtsListenLog("stop() bookId=$currentBookId chunk=$currentChunkIndex")
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        persistNow(progressFor(state.chapterIndex, currentChunkIndex.coerceAtLeast(0), completed = false))
        generation += 1
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        currentChunks = emptyList()
        currentChunkIndex = -1
        wantsPlayback = false
        state = SharedBookTtsListenState(sessionEndedByStop = true)
        deactivateAudioSession()
    }

    fun previousChunk() = moveByChunk(-1)

    fun nextChunk() = moveByChunk(1)

    fun seekToChunk(chunkIndex: Int) {
        if (currentChunks.isEmpty() || currentChunkIndex < 0) return
        val target = chunkIndex.coerceIn(0, currentChunks.lastIndex)
        if (target == currentChunkIndex && (state.isPlaying || state.isLoading)) return
        iosTtsListenLog("seekToChunk target=$target (from $currentChunkIndex)")
        wantsPlayback = true
        speakChunkAt(target, fromOffset = 0, wantsPlayback = true)
    }

    fun previousChapter() = moveChapterBy(-1)

    fun nextChapter() = moveChapterBy(1)

    fun selectChapter(index: Int) {
        val content = contentCache[currentBookId ?: return] ?: return
        playChapterInternal(index.coerceIn(0, content.chapters.lastIndex), 0)
    }

    fun setParameters(rate: Float, pitch: Float) {
        val safeRate = rate.coerceIn(0.5f, 3f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        state = state.copy(speechRate = safeRate, pitch = safePitch)
        persistNow(progressFor(state.chapterIndex, state.chunkIndex.coerceAtLeast(0), completed = false))
        val chunk = currentChunks.getOrNull(currentChunkIndex) ?: return
        if ((state.isPlaying || state.isLoading) && currentChunkIndex >= 0) {
            val offset = latestWordOffset.coerceIn(0, chunk.spokenText.length)
            speakChunkAt(currentChunkIndex, offset, wantsPlayback = true)
        }
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            var remainingMs = minutes * 60_000L
            while (remainingMs > 0) {
                state = state.copy(sleepTimerRemainingMs = remainingMs)
                delay(1_000)
                remainingMs -= 1_000
            }
            state = state.copy(sleepTimerRemainingMs = 0L)
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
        speakChunkAt(target, fromOffset = 0, wantsPlayback = true)
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
        speakChunkAt(safeChunk, fromOffset = 0, wantsPlayback = true)
    }

    private fun speakChunkAt(chunkIndex: Int, fromOffset: Int, wantsPlayback: Boolean) {
        val chunk = currentChunks.getOrNull(chunkIndex) ?: return
        val chunkSpoken = chunk.spokenText
        val safeOffset = fromOffset.coerceIn(0, chunkSpoken.length)
        val utteranceText = chunkSpoken.substring(safeOffset)
        if (utteranceText.isBlank()) return
        iosTtsListenLog("speakChunkAt chunk=$chunkIndex offset=$safeOffset textLen=${utteranceText.length}")
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        speechBaseOffset = safeOffset
        latestWordOffset = safeOffset
        currentChunkIndex = chunkIndex
        this.wantsPlayback = wantsPlayback
        state = state.copy(
            chapterIndex = currentChapterIndex,
            chapterCount = chapterCount,
            chunkIndex = -1,
            chunkCount = currentChunks.size,
            chapterTitle = currentChapterTitle(),
            isLoading = true,
            isPlaying = false,
            sessionFinished = false,
            transcriptStartIndex = 0,
            transcriptChunks = emptyList(),
        )
        val utterance = AVSpeechUtterance(string = utteranceText).apply {
            rate = (0.5f * state.speechRate).coerceIn(0.1f, 1f)
            pitchMultiplier = state.pitch
        }
        activeUtterance = utterance
        synthesizer.speakUtterance(utterance)
    }

    private fun currentChapterTitle(): String? {
        val content = currentBookId?.let { contentCache[it] } ?: return null
        return content.chapters.getOrNull(currentChapterIndex)?.title
    }

    private fun invalidateActiveUtterance() {
        activeUtterance = null
    }

    private fun isActive(utterance: AVSpeechUtterance): Boolean =
        activeUtterance?.isEqual(utterance) == true

    private fun utteranceStarted(utterance: AVSpeechUtterance) {
        iosTtsListenLog("delegate didStart chunk=$currentChunkIndex active=${isActive(utterance)}")
        if (!isActive(utterance)) return
        val chunkIndex = currentChunkIndex
        state = state.copy(
            chunkIndex = chunkIndex,
            chunkCount = currentChunks.size,
            progressPercent = chunkProgress(chunkIndex),
            isLoading = false,
            isPlaying = true,
        )
        publishTranscript(chunkIndex)
        schedulePersist(chunkIndex)
    }

    private fun utteranceFinished(utterance: AVSpeechUtterance) {
        iosTtsListenLog("delegate didFinish chunk=$currentChunkIndex active=${isActive(utterance)}")
        if (!isActive(utterance)) return
        activeUtterance = null
        if (currentChunkIndex >= currentChunks.lastIndex) {
            scope.launch { advancePastChapterEnd() }
        } else {
            speakChunkAt(currentChunkIndex + 1, fromOffset = 0, wantsPlayback = true)
        }
    }

    private fun utteranceCancelled(utterance: AVSpeechUtterance) {
        // Stale utterances are invalidated before stopSpeakingAtBoundary, so
        // cancellation only matters when the whole session was torn down.
        if (currentChunks.isEmpty() || !state.isPlaying) {
            iosTtsListenLog("delegate didCancel chunk=$currentChunkIndex (session torn down)")
            state = state.copy(isLoading = false, isPlaying = false)
        } else {
            iosTtsListenLog("delegate didCancel chunk=$currentChunkIndex (stale utterance)")
        }
    }

    private fun utteranceWillSpeakRange(utterance: AVSpeechUtterance, range: CValue<NSRange>) {
        if (!isActive(utterance)) return
        val location = range.useContents { location.toInt() }
        iosTtsListenLog("delegate willSpeakRange chunk=$currentChunkIndex wordChar=$location")
        latestWordOffset = (speechBaseOffset + location)
            .coerceIn(0, currentChunks.getOrNull(currentChunkIndex)?.spokenText?.length ?: Int.MAX_VALUE)
    }

    private fun advancePastChapterEnd() {
        val bookId = currentBookId ?: return
        val content = contentCache[bookId] ?: return
        val next = content.chapters.indexOfFirst { it.index > currentChapterIndex && it.chunks.isNotEmpty() }
        if (next < 0) {
            iosTtsListenLog("BOOK COMPLETED bookId=$bookId")
            persistNow(progressFor(currentChapterIndex, currentChunks.lastIndex, completed = true))
            generation += 1
            invalidateActiveUtterance()
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
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
        invalidateActiveUtterance()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        state = state.copy(isLoading = false, isPlaying = false, sleepTimerRemainingMs = 0L)
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

    private fun loadContentOrNull(
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

    private fun buildIosTtsListenContent(
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
                    IosTtsListenChapter(
                        index = index,
                        id = chapter.id,
                        title = chapter.title.ifBlank { "Chapter ${index + 1}" },
                        chunks = splitSharedTtsListenChunks(chapter.plainText).mapIndexed { chunkIndex, text ->
                            IosTtsListenChunk(
                                text = text,
                                spokenText = ReaderTtsReplacementEngine.apply(text, replacements, book.id).text,
                                sourceCfi = "headless/$index/$chunkIndex",
                                startOffsetInSource = 0,
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
            val chunks = splitSharedTtsListenChunks(spineChapter.plainText).mapIndexed { chunkIndex, text ->
                IosTtsListenChunk(
                    text = text,
                    spokenText = ReaderTtsReplacementEngine.apply(text, replacements, book.id).text,
                    sourceCfi = "headless/$index/$chunkIndex",
                    startOffsetInSource = 0,
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

    private fun buildIosPdfListenChapters(
        book: BookItem,
        replacements: ReaderTtsReplacementPreferences,
    ): List<IosTtsListenChapter> {
        val path = book.path ?: error("PDF path is unavailable")
        val pages = extractIosPdfPageTexts(path)
        iosTtsListenLog("buildIosPdfListenChapters pages=${pages.size} path=$path")
        return pages.mapIndexed { pageIndex, rawText ->
            val normalized = rawText
                .replace("\r\n", "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
            var searchFrom = 0
            val chunks = splitSharedTtsListenChunks(normalized).map { chunkText ->
                val offset = normalized.indexOf(chunkText, startIndex = searchFrom)
                    .takeIf { it >= 0 }
                    ?: searchFrom
                searchFrom = offset + chunkText.length
                IosTtsListenChunk(
                    text = chunkText,
                    spokenText = ReaderTtsReplacementEngine.apply(chunkText, replacements, book.id).text,
                    sourceCfi = "pdf-page:$pageIndex",
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

    private fun extractIosPdfPageTexts(path: String): List<String> {
        val resolved = path
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { value ->
                if (value.startsWith("file://")) {
                    NSURL.URLWithString(value)?.path ?: value.removePrefix("file://")
                } else {
                    value
                }
            }
            ?: return emptyList()
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(resolved, null) ?: return emptyList()
        return try {
            val pageCount = FPDF_GetPageCount(document).toInt()
            (0 until pageCount).map { pageIndex -> extractIosPdfPageText(document, pageIndex) }
        } finally {
            FPDF_CloseDocument(document)
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
        val audioSession = AVAudioSession.sharedInstance()
        if (active) {
            val categorySet = audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
            iosTtsListenLog("configureAudioSession active=$active setCategory=$categorySet")
        }
        val activated = audioSession.setActive(active = active, error = null)
        audioSessionActive = active
        iosTtsListenLog("configureAudioSession active=$active setActive=$activated")
    }

    private fun deactivateAudioSession() {
        if (audioSessionActive) {
            configureAudioSession(active = false)
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
