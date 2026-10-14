package com.aryan.reader.audiobook

import android.content.Context
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.mediaoverlay.AndroidSharedMediaOverlayPlayback
import com.aryan.reader.shared.SharedListenMediaOverlayInfo
import com.aryan.reader.shared.reader.SharedJvmEpubArchiveReader
import com.aryan.reader.shared.reader.SharedMediaOverlayDocument
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import com.aryan.reader.shared.reader.SharedMediaOverlayPlaybackState
import com.aryan.reader.shared.reader.SharedMediaOverlayProjection
import com.aryan.reader.shared.reader.SharedMediaOverlayProjectionSource
import com.aryan.reader.shared.reader.SharedMediaOverlaySession
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemIndexByChapter
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemsInReadingOrder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Process-wide owner of Listen's publisher-narration session.
 *
 * The Listen player sheet is transient — collapsing it destroys the sheet's
 * controller — but narration must keep playing like any audiobook. So the
 * overlay engine and its session live here, keyed to one book at a time, and
 * every [BookTtsAudiobookController] delegates to the holder instead of
 * owning an engine. Reopening a book reuses its live session; only an
 * explicit [start] for another book replaces playback.
 *
 * Android benchmark for the shape: the reader's narration keeps its session
 * on its screen for the same reason. The engine underneath is the same
 * [AndroidSharedMediaOverlayPlayback] (and singleton service) the reader
 * uses, so sequencing stays identical and last starter wins.
 */
class ListenOverlayHost private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = BookTtsContentRepository(appContext)

    val engine = AndroidSharedMediaOverlayPlayback(appContext)
    val playback: StateFlow<SharedMediaOverlayPlaybackState> = engine.state

    /** Snapshot of the holder's book-scoped state for sheet projection. */
    data class OverlayUi(
        val playback: SharedMediaOverlayPlaybackState = SharedMediaOverlayPlaybackState(),
        val progress: BookTtsListeningProgressEntity? = null,
        val chapterIndex: Int = -1,
        val book: ListeningBook? = null,
    )

    private val _book = MutableStateFlow<ListeningBook?>(null)
    /** The book owning the live narration session, or null. Drives the mini player. */
    val book: StateFlow<ListeningBook?> = _book.asStateFlow()
    private var preparedBook: ListeningBook? = null
    private val _info = MutableStateFlow(SharedListenMediaOverlayInfo())
    val info: StateFlow<SharedListenMediaOverlayInfo> = _info.asStateFlow()
    private val _chapterIndex = MutableStateFlow(-1)
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()
    private val _progress = MutableStateFlow<BookTtsListeningProgressEntity?>(null)
    val progress: StateFlow<BookTtsListeningProgressEntity?> = _progress.asStateFlow()
    private val _speed = MutableStateFlow(loadListenOverlaySpeed(appContext))
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private var session: SharedMediaOverlaySession? = null
    private var sessionBookId: String? = null
    private var archiveReader: SharedJvmEpubArchiveReader? = null
    private var archiveFile: File? = null
    private var chapterIndexBySpine: Map<Int, Int> = emptyMap()
    private var lastSpineItem: Int? = null
    private var baseProgress: BookTtsListeningProgressEntity? = null

    val ui: StateFlow<OverlayUi> = combine(playback, _progress, _chapterIndex, _book) { state, progress, chapter, book ->
        OverlayUi(state, progress, chapter, book)
    }.stateIn(scope, SharingStarted.Eagerly, OverlayUi())

    init {
        engine.setSpeed(_speed.value)
        scope.launch {
            engine.state.collect(::onPlaybackState)
        }
    }

    /**
     * Loads narration facts for [bookId] without touching audio.
     *
     * Opening a book's player while another book narrates must not interrupt
     * it, so a live session is never torn down here — not even for another
     * book. Only an explicit [start] replaces playback. A prepared book is
     * returned untouched, so reopening the player mid-narration never
     * interrupts playback. Anything missing — no overlays, no seekable
     * archive, unreadable archive — resolves to an empty info, the `RS §9`
     * reader-without-support behavior.
     */
    suspend fun prepare(bookId: String): SharedListenMediaOverlayInfo {
        preparedBook?.takeIf { it.bookId == bookId }?.let { return _info.value }
        val book = repository.loadBook(bookId)
        preparedBook = book
        if (!book.mediaOverlay.hasNarration) {
            _info.value = SharedListenMediaOverlayInfo()
            return _info.value
        }
        // Readability probe only: the archive stays closed until playback, so
        // a prepared book costs a book parse and nothing else.
        val archive = repository.loadOverlayArchiveFile(bookId)
        val readable = archive != null && SharedJvmEpubArchiveReader(archive).use { it.isReadable() }
        if (!readable) {
            Timber.tag(TAG).i("Listen overlay unavailable for book=$bookId: no seekable archive")
            _info.value = SharedListenMediaOverlayInfo()
            return _info.value
        }
        archiveFile = archive
        _info.value = book.mediaOverlay
        _speed.value = loadListenOverlaySpeed(appContext)
        return _info.value
    }

    /**
     * Starts narration at [chapterIndex], resolving to the nearest narrated
     * chapter at or after it. Building the session here is the explicit
     * switch: playback of any previous book is replaced, and only here.
     * Navigation within narration afterwards never switches back to
     * synthesis. Returns false when there is nothing to narrate.
     */
    suspend fun start(chapterIndex: Int): Boolean {
        val book = preparedBook ?: return false
        if (!book.mediaOverlay.hasNarration || !_info.value.hasNarration) return false
        val archive = archiveFile ?: return false
        var live = session?.takeIf { sessionBookId == book.bookId }
        if (live == null) {
            live = buildSession(book, archive) ?: return false
        }
        val narrated = book.mediaOverlay.narratedChapterIndices
        if (narrated.isEmpty()) return false
        val target = chapterIndex.takeIf { it in narrated }
            ?: narrated.filter { it >= chapterIndex }.minOrNull()
            ?: narrated.minOrNull()
            ?: return false
        live.setSpeed(_speed.value)
        val started = live.start(target, readerOffset = null)
        if (started) _book.value = book
        return started
    }

    private suspend fun buildSession(book: ListeningBook, archive: File): SharedMediaOverlaySession? {
        val reader = SharedJvmEpubArchiveReader(archive)
        if (!reader.isReadable()) {
            reader.close()
            return null
        }
        // Teardown of the previous book happens here, and only here: this is
        // the explicit switch, so whatever narrated before ends now.
        session?.stop()
        session = null
        sessionBookId = null
        runCatching { archiveReader?.close() }
        archiveReader = reader
        val cache = SharedMediaOverlayDocumentCache(
            book.overlayIndex,
            readSmil = { path -> reader.readTextOrNull(path) },
        )
        val spineByChapter = sharedMediaOverlaySpineItemIndexByChapter(book.chapterContentPaths, book.overlayIndex)
        chapterIndexBySpine = buildMap {
            // First writer wins: a spine item split across chapters resolves to its first chapter.
            spineByChapter.entries.sortedBy { it.key }.forEach { (chapter, spine) -> putIfAbsent(spine, chapter) }
        }
        engine.attachBook(archive)
        engine.setSpeed(_speed.value)
        val built = SharedMediaOverlaySession(
            engine = engine,
            projector = ListenOverlayProjector(cache, chapterIndexBySpine),
            bookId = book.bookId,
            bookTitle = book.title,
            narrator = book.mediaOverlay.narrator,
            totalDurationMs = book.overlayIndex.totalDurationMs,
            overlayIndex = book.overlayIndex,
            spineItemIndexByChapter = spineByChapter,
            spineItemsInReadingOrder = sharedMediaOverlaySpineItemsInReadingOrder(book.chapters.size, spineByChapter),
        )
        session = built
        sessionBookId = book.bookId
        // Ownership itself transfers on a successful start, not here: building
        // is preparation, and a prepared-but-idle book must never borrow
        // another book's live position.
        baseProgress = withContext(Dispatchers.IO) { repository.loadProgress(book.bookId) }
        _progress.value = null
        _chapterIndex.value = -1
        lastSpineItem = null
        return built
    }

    fun toggle() {
        if (playback.value.hasBook) session?.togglePlayPause()
    }

    fun stop() {
        stopInternalPlayback()
        // Ownership clears with playback: the mini player and sheets key off
        // the owner, so nothing keeps reporting a stopped session.
        _book.value = null
    }

    fun setSpeed(speed: Float) {
        val resolved = speed.coerceIn(0.5f, 3.0f)
        _speed.value = resolved
        saveListenOverlaySpeed(appContext, resolved)
        session?.setSpeed(resolved)
    }

    private fun stopInternalPlayback() {
        session?.stop()
    }

    private fun onPlaybackState(state: SharedMediaOverlayPlaybackState) {
        val spine = state.spineItemIndex ?: run {
            lastSpineItem = null
            return
        }
        if (spine == lastSpineItem) return
        lastSpineItem = spine
        val book = _book.value ?: return
        val chapter = chapterIndexBySpine[spine] ?: return
        _chapterIndex.value = chapter
        // Chapter-granular progress: narration advances per clip, but Listen
        // persistence is per chapter — the honest resolution both hold.
        val percent = ((chapter + 1f) / book.chapters.size.coerceAtLeast(1) * 100f).coerceIn(0f, 99.9f)
        val old = baseProgress
        val next = (old?.takeIf { it.bookId == book.bookId }
            ?: BookTtsListeningProgressEntity(
                bookId = book.bookId,
                speechRate = loadTtsSpeechRate(appContext),
                pitch = loadTtsPitch(appContext),
            )).copy(
            chapterIndex = chapter,
            progressPercent = percent,
            completed = false,
            updatedAt = System.currentTimeMillis(),
        )
        baseProgress = next
        _progress.value = next
        scope.launch(Dispatchers.IO) { repository.saveProgress(next) }
    }

    /**
     * Listen-side overlay projector: chapter-level only.
     *
     * The reader resolves every clip to word offsets for highlighting; Listen
     * has no text surface in narration mode (its transcript panel says so),
     * so positions resolve to chapters and fragments stay null — audio-first,
     * with nothing faked for paint.
     */
    private class ListenOverlayProjector(
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

    companion object {
        private const val TAG = "LISTEN_OVERLAY_HOST"

        @Volatile
        private var instance: ListenOverlayHost? = null

        fun get(context: Context): ListenOverlayHost =
            instance ?: synchronized(this) {
                instance ?: ListenOverlayHost(context.applicationContext).also { instance = it }
            }
    }
}
