package com.aryan.reader.audiobook

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.aryan.reader.FileType
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubParser
import com.aryan.reader.epub.Fb2Parser
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.epub.OdtParser
import com.aryan.reader.epub.SingleFileImporter
import com.aryan.reader.loadTtsReplacementPreferences
import com.aryan.reader.epubreader.loadTtsPitch
import com.aryan.reader.epubreader.loadTtsSpeechRate
import com.aryan.reader.paginatedreader.LocatorConverter
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.paginatedreader.semanticBlockModule
import com.aryan.reader.pdf.DocumentFactory
import com.aryan.reader.pdf.PdfiumCoreProvider
import com.aryan.reader.pdf.data.PdfTextDatabase
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileEntity
import com.aryan.reader.withTtsReplacements
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.jsoup.Jsoup
import timber.log.Timber
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.TtsService
import com.aryan.reader.tts.ACTION_START_BOOK_TTS
import com.aryan.reader.tts.ACTION_BOOK_TTS_PREVIOUS_CHAPTER
import com.aryan.reader.tts.ACTION_BOOK_TTS_NEXT_CHAPTER
import com.aryan.reader.tts.ACTION_BOOK_TTS_SELECT_CHAPTER
import com.aryan.reader.tts.ACTION_BOOK_TTS_SLEEP_TIMER
import com.aryan.reader.tts.ACTION_BOOK_TTS_CANCEL_SLEEP_TIMER
import com.aryan.reader.tts.EXTRA_BOOK_TTS_BOOK_ID
import com.aryan.reader.tts.EXTRA_BOOK_TTS_START_POLICY
import com.aryan.reader.tts.EXTRA_BOOK_TTS_CHAPTER_INDEX
import com.aryan.reader.tts.EXTRA_BOOK_TTS_SLEEP_MINUTES

@Entity(
    tableName = "book_tts_listening_progress",
    foreignKeys = [ForeignKey(
        entity = RecentFileEntity::class,
        parentColumns = ["bookId"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class BookTtsListeningProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int = 0,
    val chunkIndex: Int = 0,
    val sourceCfi: String? = null,
    val sourceOffset: Int = 0,
    val progressPercent: Float = 0f,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val voiceId: String? = null,
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface BookTtsListeningProgressDao {
    @Query("SELECT * FROM book_tts_listening_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BookTtsListeningProgressEntity>>

    @Query("SELECT * FROM book_tts_listening_progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): BookTtsListeningProgressEntity?

    @Upsert
    suspend fun upsert(progress: BookTtsListeningProgressEntity)

    @Query("DELETE FROM book_tts_listening_progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

data class ListeningChapter(
    val index: Int,
    val id: String,
    val title: String,
    val estimatedCharacters: Int
)

data class ListeningBook(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val type: FileType,
    val chapters: List<ListeningChapter>
)

data class ListeningChapterContent(
    val chapter: ListeningChapter,
    val chunks: List<TtsChunk>
)

class BookTtsContentRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val parsedBooks = ConcurrentHashMap<String, ListeningBookSource>()

    private sealed interface ListeningBookSource {
        val metadata: ListeningBook
        data class Reflow(override val metadata: ListeningBook, val book: EpubBook) : ListeningBookSource
        data class Pdf(override val metadata: ListeningBook, val uri: Uri) : ListeningBookSource
    }

    suspend fun loadBook(bookId: String): ListeningBook = source(bookId).metadata

    suspend fun loadChapter(bookId: String, chapterIndex: Int): ListeningChapterContent = withContext(Dispatchers.IO) {
        when (val source = source(bookId)) {
            is ListeningBookSource.Reflow -> loadReflowChapter(bookId, source, chapterIndex)
            is ListeningBookSource.Pdf -> loadPdfPage(bookId, source, chapterIndex)
        }
    }

    suspend fun loadProgress(bookId: String): BookTtsListeningProgressEntity? =
        database.bookTtsListeningProgressDao().get(bookId)

    suspend fun saveProgress(progress: BookTtsListeningProgressEntity) =
        database.bookTtsListeningProgressDao().upsert(progress)

    private suspend fun source(bookId: String): ListeningBookSource {
        parsedBooks[bookId]?.let { return it }
        return withContext(Dispatchers.IO) {
            parsedBooks[bookId] ?: parseSource(bookId).also { parsedBooks[bookId] = it }
        }
    }

    private suspend fun parseSource(bookId: String): ListeningBookSource {
        val item = requireNotNull(database.recentFileDao().getFileByBookId(bookId)) { "Book is no longer in the library" }
        val uri = Uri.parse(item.uriString)
        if (item.type == FileType.PDF) {
            val document = DocumentFactory.loadDocument(appContext, uri, FileType.PDF, null, PdfiumCoreProvider.core)
            val count = try { document.getPageCount() } finally { document.close() }
            val chapters = (0 until count).map { page -> ListeningChapter(page, "page-$page", "Page ${page + 1}", 0) }
            return ListeningBookSource.Pdf(item.listeningMetadata(chapters), uri)
        }
        require(item.type in REFLOW_TYPES) { "${item.type.name} does not expose readable text for audiobook playback" }
        val book = openInput(uri).use { input -> parseReflow(item, input) }
        val chapters = book.chapters.mapIndexed { index, chapter ->
            ListeningChapter(
                index = index,
                id = chapter.chapterId.ifBlank { "chapter-$index" },
                title = chapter.title.ifBlank { "Chapter ${index + 1}" },
                estimatedCharacters = chapter.plainTextLength
            )
        }
        require(chapters.isNotEmpty()) { "No readable chapters were found" }
        return ListeningBookSource.Reflow(item.listeningMetadata(chapters), book)
    }

    private suspend fun parseReflow(item: RecentFileEntity, input: InputStream): EpubBook = when (item.type) {
        FileType.EPUB -> EpubParser(appContext).createEpubBook(input, item.bookId, originalBookNameHint = item.displayName)
        FileType.MOBI -> requireNotNull(MobiParser(appContext).createMobiBook(input, item.bookId, originalBookNameHint = item.displayName)) {
            "This MOBI file could not be decoded"
        }
        FileType.FB2 -> Fb2Parser(appContext).createFb2Book(input, item.bookId, originalBookNameHint = item.displayName)
        FileType.ODT, FileType.FODT -> OdtParser(appContext).createOdtBook(
            input, item.bookId, originalBookNameHint = item.displayName, isFlat = item.type == FileType.FODT
        )
        FileType.MD, FileType.TXT, FileType.HTML, FileType.DOCX -> SingleFileImporter(appContext).importSingleFile(
            input, item.type, item.displayName, item.bookId
        )
        else -> error("Unsupported reflow type ${item.type}")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun loadReflowChapter(
        bookId: String,
        source: ListeningBookSource.Reflow,
        chapterIndex: Int
    ): ListeningChapterContent {
        val chapter = source.metadata.chapters.getOrNull(chapterIndex) ?: error("Chapter is unavailable")
        val converter = LocatorConverter(
            bookCacheDao = BookCacheDatabase.getDatabase(appContext).bookCacheDao(),
            proto = ProtoBuf { serializersModule = semanticBlockModule },
            context = appContext,
            stableBookId = bookId
        )
        var chunks = converter.getTtsChunksForChapter(source.book, chapterIndex, bookId).orEmpty()
        if (chunks.isEmpty()) {
            val raw = source.book.chapters.getOrNull(chapterIndex)?.let { epubChapter ->
                epubChapter.plainTextContent.takeIf(String::isNotBlank)
                    ?: Jsoup.parse(epubChapter.htmlContent).text()
            }.orEmpty()
            chunks = com.aryan.reader.tts.splitTextIntoChunks(raw).mapIndexed { index, text ->
                TtsChunk(text = text, sourceCfi = "headless/$chapterIndex/$index", startOffsetInSource = 0)
            }
        }
        chunks = chunks.withTtsReplacements(loadTtsReplacementPreferences(appContext), bookId)
        require(chunks.isNotEmpty()) { "This chapter contains no readable text" }
        return ListeningChapterContent(chapter, chunks)
    }

    private suspend fun loadPdfPage(
        bookId: String,
        source: ListeningBookSource.Pdf,
        pageIndex: Int
    ): ListeningChapterContent {
        val chapter = source.metadata.chapters.getOrNull(pageIndex) ?: error("Page is unavailable")
        val textDao = PdfTextDatabase.getDatabase(appContext).pdfTextDao()
        var text = textDao.getPageText(bookId, pageIndex)
        if (text.isNullOrBlank()) {
            val document = DocumentFactory.loadDocument(appContext, source.uri, FileType.PDF, null, PdfiumCoreProvider.core)
            text = try {
                document.openPage(pageIndex)?.use { page ->
                    page.openTextPage().use { textPage ->
                        val count = textPage.textPageCountChars()
                        if (count > 0) textPage.textPageGetText(0, count) else null
                    }
                }
            } finally {
                document.close()
            }
        }
        var searchFrom = 0
        val chunks = com.aryan.reader.tts.splitTextIntoChunks(text.orEmpty())
            .map { value ->
                val offset = text.orEmpty().indexOf(value, startIndex = searchFrom).takeIf { it >= 0 } ?: searchFrom
                searchFrom = offset + value.length
                TtsChunk(value, "pdf-page:$pageIndex", offset)
            }
            .withTtsReplacements(loadTtsReplacementPreferences(appContext), bookId)
        require(chunks.isNotEmpty()) { "Page ${pageIndex + 1} has no extractable text" }
        return ListeningChapterContent(chapter, chunks)
    }

    private fun openInput(uri: Uri): InputStream {
        return appContext.contentResolver.openInputStream(uri)
            ?: uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
            ?: error("The book file is unavailable")
    }

    private fun RecentFileEntity.listeningMetadata(chapters: List<ListeningChapter>) = ListeningBook(
        bookId = bookId,
        title = title?.takeIf(String::isNotBlank) ?: displayName.substringBeforeLast('.'),
        author = author,
        coverPath = coverImagePath,
        type = type,
        chapters = chapters
    )

    companion object {
        val REFLOW_TYPES = setOf(
            FileType.EPUB, FileType.MOBI, FileType.FB2, FileType.ODT, FileType.FODT,
            FileType.MD, FileType.TXT, FileType.HTML, FileType.DOCX
        )

        fun supports(type: FileType): Boolean = type == FileType.PDF || type in REFLOW_TYPES
    }
}

internal fun defaultBookTtsProgress(context: Context, bookId: String) = BookTtsListeningProgressEntity(
    bookId = bookId,
    speechRate = loadTtsSpeechRate(context),
    pitch = loadTtsPitch(context)
)

class BookTtsSessionCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val playbackManager: com.aryan.reader.tts.TtsPlaybackManager
) {
    private val appContext = context.applicationContext
    private val repository = BookTtsContentRepository(appContext)
    private var activeBook: ListeningBook? = null
    private var activeProgress: BookTtsListeningProgressEntity? = null
    private var transitionJob: Job? = null
    private var persistJob: Job? = null
    private var lastPersistedPosition: Pair<Int, Int>? = null

    init {
        scope.launch {
            playbackManager.ttsState.collectLatest(::onPlaybackState)
        }
    }

    fun start(
        bookId: String,
        startPolicy: String = START_RESUME,
        selectedChapterIndex: Int? = null
    ) {
        val playback = playbackManager.ttsState.value
        if (
            startPolicy == START_RESUME &&
            activeBook?.bookId == bookId &&
            playback.playbackSource == "AUDIOBOOK_TTS" &&
            !playback.sessionFinished
        ) {
            Timber.tag(TAG).i("Ignoring duplicate coordinator start for active book=$bookId")
            return
        }
        transitionJob?.cancel()
        transitionJob = scope.launch {
            runCatching {
                val book = repository.loadBook(bookId)
                val saved = repository.loadProgress(bookId) ?: defaultBookTtsProgress(context = appContext, bookId = bookId)
                val reading = AppDatabase.getDatabase(appContext).recentFileDao().getFileByBookId(bookId)
                val chapter = when (startPolicy) {
                    START_BEGINNING -> 0
                    START_READING_POSITION -> reading?.lastChapterIndex ?: reading?.lastPage ?: 0
                    START_CHAPTER -> selectedChapterIndex ?: saved.chapterIndex
                    else -> saved.chapterIndex
                }.coerceIn(book.chapters.indices)
                val chunk = if (startPolicy == START_RESUME && chapter == saved.chapterIndex) saved.chunkIndex else 0
                activeBook = book
                activeProgress = saved.copy(chapterIndex = chapter, chunkIndex = chunk, completed = false)
                playChapter(chapter, chunk, continueSession = false)
            }.onFailure { error ->
                Timber.tag(TAG).e(error, "Unable to start headless book TTS for $bookId")
                playbackManager.stopBookListeningSession()
            }
        }
    }

    fun skipChapter(direction: Int) {
        val book = activeBook ?: return
        val current = activeProgress?.chapterIndex ?: return
        val target = (current + direction).takeIf { it in book.chapters.indices } ?: return
        transitionJob?.cancel()
        transitionJob = scope.launch { playChapter(target, 0, continueSession = true, searchDirection = direction) }
    }

    fun selectChapter(chapterIndex: Int) {
        val book = activeBook ?: return
        if (chapterIndex !in book.chapters.indices) return
        transitionJob?.cancel()
        transitionJob = scope.launch { playChapter(chapterIndex, 0, continueSession = true) }
    }

    private suspend fun playChapter(
        chapterIndex: Int,
        startChunkIndex: Int,
        continueSession: Boolean,
        searchDirection: Int = 1
    ) {
        val book = activeBook ?: return
        val direction = if (searchDirection < 0) -1 else 1
        var playableChapterIndex = chapterIndex
        var content: ListeningChapterContent? = null
        var lastEmptyError: Throwable? = null
        while (playableChapterIndex in book.chapters.indices && content == null) {
            val attempt = runCatching { repository.loadChapter(book.bookId, playableChapterIndex) }
            val error = attempt.exceptionOrNull()
            if (error == null) {
                content = attempt.getOrThrow()
            } else if (error.isEmptyListeningChapter()) {
                lastEmptyError = error
                Timber.tag(TAG).i(
                    "Skipping empty book=${book.bookId} chapter=$playableChapterIndex direction=$direction"
                )
                playableChapterIndex += direction
            } else {
                throw error
            }
        }
        val playableContent = content ?: throw IllegalArgumentException(
            "No readable text was found from chapter ${chapterIndex + 1}",
            lastEmptyError
        )
        val latestSaved = repository.loadProgress(book.bookId)
        val progress = (latestSaved ?: activeProgress ?: defaultBookTtsProgress(context = appContext, bookId = book.bookId)).copy(
            chapterIndex = playableChapterIndex,
            chunkIndex = if (playableChapterIndex == chapterIndex) startChunkIndex.coerceIn(playableContent.chunks.indices) else 0,
            completed = false,
            updatedAt = System.currentTimeMillis()
        )
        activeProgress = progress
        repository.saveProgress(progress)
        playbackManager.startBookListeningChapter(
            book = book,
            content = playableContent,
            startChunkIndex = progress.chunkIndex,
            continueSession = continueSession,
            speechRate = progress.speechRate,
            pitch = progress.pitch
        )
        Timber.tag(TAG).i("Playing book=${book.bookId} chapter=$playableChapterIndex chunk=${progress.chunkIndex}")
    }

    private fun onPlaybackState(state: com.aryan.reader.tts.TtsPlaybackManager.TtsState) {
        if (state.playbackSource != "AUDIOBOOK_TTS") return
        val book = activeBook ?: return
        val chapterIndex = state.chapterIndex ?: activeProgress?.chapterIndex ?: 0
        if (state.sessionFinished) {
            if (transitionJob?.isActive == true) return
            if (chapterIndex < book.chapters.lastIndex) {
                transitionJob = scope.launch {
                    runCatching { playChapter(chapterIndex + 1, 0, continueSession = true) }
                        .onFailure { error ->
                            Timber.tag(TAG).w(error, "No further readable audiobook chapter for book=${book.bookId}")
                            persist(completed = true, state = state)
                            playbackManager.stopBookListeningSession()
                        }
                }
            } else {
                persist(completed = true, state = state)
                Timber.tag(TAG).i("Book completed; releasing local TTS engine for book=${book.bookId}")
                playbackManager.stopBookListeningSession()
            }
            return
        }
        val position = chapterIndex to state.currentChunkIndex
        if (state.currentChunkIndex >= 0 && position != lastPersistedPosition) {
            lastPersistedPosition = position
            persistJob?.cancel()
            persistJob = scope.launch {
                delay(350)
                persist(completed = false, state = state)
            }
        }
    }

    private fun persist(completed: Boolean, state: com.aryan.reader.tts.TtsPlaybackManager.TtsState) {
        val book = activeBook ?: return
        val old = activeProgress ?: return
        val chapter = (state.chapterIndex ?: old.chapterIndex).coerceIn(book.chapters.indices)
        val chunkFraction = if (state.totalChunks > 0) {
            (state.currentChunkIndex.coerceAtLeast(0) + 1f) / state.totalChunks
        } else 0f
        val progress = if (completed) 100f else ((chapter + chunkFraction) / book.chapters.size * 100f).coerceIn(0f, 99.9f)
        val next = old.copy(
            chapterIndex = chapter,
            chunkIndex = state.currentChunkIndex.coerceAtLeast(0),
            sourceCfi = state.currentWordSourceCfi ?: state.sourceCfi,
            sourceOffset = state.currentWordStartOffset.takeIf { it >= 0 } ?: state.startOffsetInSource.coerceAtLeast(0),
            progressPercent = progress,
            completed = completed,
            updatedAt = System.currentTimeMillis()
        )
        activeProgress = next
        scope.launch(Dispatchers.IO) { repository.saveProgress(next) }
    }

    fun release() {
        transitionJob?.cancel()
        persistJob?.cancel()
    }

    companion object {
        private const val TAG = "BOOK_TTS_AUDIOBOOK"
        const val START_RESUME = "resume"
        const val START_BEGINNING = "beginning"
        const val START_READING_POSITION = "reading_position"
        const val START_CHAPTER = "chapter"
    }
}

private fun Throwable.isEmptyListeningChapter(): Boolean =
    this is IllegalArgumentException && (
        message?.contains("contains no readable text", ignoreCase = true) == true ||
            message?.contains("has no extractable text", ignoreCase = true) == true
        )

data class BookTtsAudiobookUiState(
    val loading: Boolean = false,
    val book: ListeningBook? = null,
    val savedProgress: BookTtsListeningProgressEntity? = null,
    val error: String? = null
)

class BookTtsAudiobookController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private val repository = BookTtsContentRepository(appContext)
    val ttsController = TtsController(appContext)
    val playbackState = ttsController.ttsState
    private val _uiState = MutableStateFlow(BookTtsAudiobookUiState())
    val uiState = _uiState.asStateFlow()
    private val _sleepTimerLabel = MutableStateFlow("Sleep")
    val sleepTimerLabel = _sleepTimerLabel.asStateFlow()
    private var sleepTimerJob: Job? = null

    fun connect(bookId: String) {
        ttsController.connect()
        _uiState.value = BookTtsAudiobookUiState(loading = true)
        scope.launch {
            runCatching {
                repository.loadBook(bookId) to repository.loadProgress(bookId)
            }.onSuccess { (book, progress) ->
                _uiState.value = BookTtsAudiobookUiState(book = book, savedProgress = progress)
            }.onFailure { error ->
                _uiState.value = BookTtsAudiobookUiState(error = error.message ?: "Unable to prepare this book")
            }
        }
    }

    fun start(bookId: String, policy: String, chapterIndex: Int? = null) {
        val active = playbackState.value
        if (
            policy == BookTtsSessionCoordinator.START_RESUME &&
            active.playbackSource == "AUDIOBOOK_TTS" &&
            active.bookId == bookId &&
            !active.sessionFinished
        ) {
            Timber.tag("BOOK_TTS_AUDIOBOOK").i("Ignoring duplicate resume request for active book=$bookId")
            return
        }
        appContext.startService(Intent(appContext, AudiobookPlaybackService::class.java).setAction(ACTION_AUDIOBOOK_STOP))
        val intent = Intent(appContext, TtsService::class.java).apply {
            action = ACTION_START_BOOK_TTS
            putExtra(EXTRA_BOOK_TTS_BOOK_ID, bookId)
            putExtra(EXTRA_BOOK_TTS_START_POLICY, policy)
            chapterIndex?.let { putExtra(EXTRA_BOOK_TTS_CHAPTER_INDEX, it) }
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun togglePlay() {
        if (playbackState.value.isPlaying || playbackState.value.isLoading) ttsController.pause()
        else ttsController.resume()
    }

    fun stop() = ttsController.stop()
    fun previousChunk() = ttsController.skipToPreviousChunk()
    fun nextChunk() = ttsController.skipToNextChunk()
    fun setParameters(rate: Float, pitch: Float) {
        com.aryan.reader.epubreader.saveTtsSpeechRate(appContext, rate)
        com.aryan.reader.epubreader.saveTtsPitch(appContext, pitch)
        ttsController.setPlaybackParameters(rate, pitch)
        val current = _uiState.value.savedProgress ?: return
        val next = current.copy(speechRate = rate, pitch = pitch, updatedAt = System.currentTimeMillis())
        _uiState.value = _uiState.value.copy(savedProgress = next)
        scope.launch(Dispatchers.IO) { repository.saveProgress(next) }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (_sleepTimerLabel.value != "Sleep") {
            _sleepTimerLabel.value = "Sleep"
            appContext.startService(Intent(appContext, TtsService::class.java).setAction(ACTION_BOOK_TTS_CANCEL_SLEEP_TIMER))
            return
        }
        appContext.startService(Intent(appContext, TtsService::class.java).apply {
            action = ACTION_BOOK_TTS_SLEEP_TIMER
            putExtra(EXTRA_BOOK_TTS_SLEEP_MINUTES, minutes)
        })
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _sleepTimerLabel.value = "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
                delay(1_000)
                remaining--
            }
            ttsController.pause()
            _sleepTimerLabel.value = "Sleep"
        }
    }

    fun previousChapter() = sendChapterAction(ACTION_BOOK_TTS_PREVIOUS_CHAPTER)
    fun nextChapter() = sendChapterAction(ACTION_BOOK_TTS_NEXT_CHAPTER)
    fun selectChapter(index: Int) = sendChapterAction(ACTION_BOOK_TTS_SELECT_CHAPTER, index)

    private fun sendChapterAction(actionName: String, index: Int? = null) {
        appContext.startService(Intent(appContext, TtsService::class.java).apply {
            action = actionName
            index?.let { putExtra(EXTRA_BOOK_TTS_CHAPTER_INDEX, it) }
        })
    }

    fun release() {
        sleepTimerJob?.cancel()
        ttsController.release()
        scope.cancel()
    }
}
