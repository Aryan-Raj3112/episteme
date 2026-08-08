package com.aryan.reader.shared

/** Platform-neutral audiobook state. Platform stores own the referenced local files. */
data class SharedAudiobook(
    val bookId: String,
    val filePath: String,
    val format: String,
    val title: String,
    val author: String? = null,
    val album: String? = null,
    val narrator: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val coverPath: String? = null,
    val addedAt: Long,
    val lastListenedAt: Long = 0L,
)

data class SharedBookTtsListeningProgress(
    val bookId: String,
    val chapterIndex: Int = 0,
    val chunkIndex: Int = 0,
    val sourceCfi: String? = null,
    val sourceOffset: Int = 0,
    val progressPercent: Float = 0f,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val voiceId: String? = null,
    val completed: Boolean = false,
    val updatedAt: Long = 0L,
)

enum class SharedAudiobookStatus { ALL, IN_PROGRESS, NOT_STARTED, COMPLETED }

enum class SharedAudiobookSort { RECENTLY_LISTENED, RECENTLY_ADDED, TITLE, AUTHOR, PROGRESS }

data class SharedAudiobookLibraryItem(
    val id: String,
    val progress: Float,
    val isTts: Boolean = false,
    val updatedAt: Long = 0L,
)

data class SharedAudiobookPlaybackState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val sleepTimerRemainingMs: Long = 0L,
    val error: String? = null,
)

data class SharedAudiobookPlaybackRequest(
    val bookId: String,
    val filePath: String,
    val title: String,
    val author: String? = null,
    val narrator: String? = null,
    val album: String? = null,
    val coverPath: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
)

enum class SharedListeningTarget { IMPORTED_AUDIOBOOK, GENERATED_BOOK_TTS }

enum class SharedListeningHandoff { STOP_TTS, STOP_AUDIOBOOK }

/** Preserves Android's single-active-listening-source policy. */
fun sharedListeningHandoff(target: SharedListeningTarget): SharedListeningHandoff = when (target) {
    SharedListeningTarget.IMPORTED_AUDIOBOOK -> SharedListeningHandoff.STOP_TTS
    SharedListeningTarget.GENERATED_BOOK_TTS -> SharedListeningHandoff.STOP_AUDIOBOOK
}

val SharedAudiobook.progressFraction: Float
    get() = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

fun filterSharedAudiobooks(
    items: List<SharedAudiobookLibraryItem>,
    status: SharedAudiobookStatus,
): List<SharedAudiobookLibraryItem> = items.filter { item ->
    when (status) {
        SharedAudiobookStatus.ALL -> true
        SharedAudiobookStatus.IN_PROGRESS -> item.progress > 0f && item.progress < 1f
        SharedAudiobookStatus.NOT_STARTED -> item.progress <= 0f
        SharedAudiobookStatus.COMPLETED -> item.progress >= 1f
    }
}

fun sharedAudiobookContinueItem(
    imported: List<SharedAudiobookLibraryItem>,
    tts: List<SharedAudiobookLibraryItem>,
): SharedAudiobookLibraryItem? = (imported + tts)
    .filter { it.progress in 0.001f..<1f }
    .maxByOrNull { if (it.isTts) it.updatedAt else 0L }

fun sortSharedAudiobooks(
    items: List<SharedAudiobook>,
    sort: SharedAudiobookSort,
): List<SharedAudiobook> = when (sort) {
    SharedAudiobookSort.RECENTLY_LISTENED -> items.sortedWith(
        compareByDescending<SharedAudiobook> { it.lastListenedAt }.thenByDescending { it.addedAt }
    )
    SharedAudiobookSort.RECENTLY_ADDED -> items.sortedByDescending { it.addedAt }
    SharedAudiobookSort.TITLE -> items.sortedBy { it.title.lowercase() }
    SharedAudiobookSort.AUTHOR -> items.sortedWith(
        compareBy<SharedAudiobook> { it.author?.lowercase().orEmpty() }.thenBy { it.title.lowercase() }
    )
    SharedAudiobookSort.PROGRESS -> items.sortedByDescending { it.progressFraction }
}

fun SharedAudiobook.matchesSharedAudiobookQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true
    return listOf(title, author, album, narrator)
        .any { it?.contains(normalized, ignoreCase = true) == true }
}

fun SharedAudiobook.toSharedAudiobookLibraryItem(): SharedAudiobookLibraryItem =
    SharedAudiobookLibraryItem(
        id = bookId,
        progress = progressFraction,
        isTts = false,
        updatedAt = lastListenedAt,
    )

fun formatSharedPlaybackTime(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun calculateSharedTtsAudiobookProgress(
    chapterIndex: Int,
    chapterCount: Int,
    chunkIndex: Int,
    chunkCount: Int,
): Float {
    if (chapterCount <= 0) return 0f
    val chapterFraction = if (chunkCount > 0) {
        (chunkIndex.coerceAtLeast(0) + 1f) / chunkCount
    } else {
        0f
    }
    return ((chapterIndex.coerceIn(0, chapterCount - 1) + chapterFraction) / chapterCount)
        .coerceIn(0f, 1f)
}

fun sharedAudiobookResumePosition(savedPositionMs: Long, rewindMs: Long = 10_000L): Long =
    (savedPositionMs - rewindMs).coerceAtLeast(0L)

fun formatSharedAudiobookSleepTimer(remainingSeconds: Int): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0)
    return "${safeSeconds / 60}:${(safeSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * "1 hr 30 min" style label for how much time is left in an audiobook,
 * mirroring the Listen row's remaining-time text on Android.
 */
fun sharedAudiobookRemainingLabel(durationMs: Long, positionMs: Long): String {
    if (durationMs <= 0L) return "Duration unavailable"
    val minutes = (durationMs - positionMs).coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60L) {
        "${minutes / 60} hr ${minutes % 60} min"
    } else {
        "$minutes min"
    }
}

/**
 * "M:SS" countdown label for an active sleep timer, mirroring Android's
 * [sleepTimerLabel] format ("Sleep" being represented by the zero value).
 */
fun formatSharedSleepTimerLabel(remainingMs: Long): String =
    formatSharedAudiobookSleepTimer((remainingMs.coerceAtLeast(0L) / 1000L).toInt())

object SharedAudiobookFormats {
    val supportedExtensions: Set<String> = setOf("mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac")

    fun supportsFileName(name: String): Boolean =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in supportedExtensions
}

/**
 * State of the "Listen with TTS" player (reading a library book as an audiobook
 * through the platform speech engine), mirroring the Android TtsState fields
 * used by the Listen UI. [progressPercent] is a 0..1 fraction of the whole book.
 */
data class SharedBookTtsListenState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chunkIndex: Int = -1,
    val chunkCount: Int = 0,
    val chapterTitle: String? = null,
    val progressPercent: Float = 0f,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val sleepTimerRemainingMs: Long = 0L,
    val sessionFinished: Boolean = false,
    val sessionEndedByStop: Boolean = false,
    val error: String? = null,
    val transcriptStartIndex: Int = 0,
    val transcriptChunks: List<String> = emptyList(),
)

enum class SharedTtsListenStartPolicy { RESUME, BEGINNING, READING_POSITION, CHAPTER }

object SharedTtsListenCapabilities {
    val reflowTypes: Set<FileType> = setOf(
        FileType.EPUB,
        FileType.MOBI,
        FileType.FB2,
        FileType.ODT,
        FileType.FODT,
        FileType.MD,
        FileType.TXT,
        FileType.HTML,
        FileType.DOCX,
    )

    fun supports(type: FileType): Boolean = type == FileType.PDF || type in reflowTypes
}

data class SharedTtsListenItem(
    val book: BookItem,
    val progress: SharedBookTtsListeningProgress?,
) {
    val title: String
        get() = book.title?.takeIf { it.isNotBlank() }
            ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
    val author: String
        get() = book.author ?: "Unknown author"
}

fun buildSharedTtsListenItems(
    books: List<BookItem>,
    progress: List<SharedBookTtsListeningProgress>,
): List<SharedTtsListenItem> {
    val progressByBook = progress.associateBy { it.bookId }
    return books
        .filter { it.type != FileType.AUDIOBOOK && SharedTtsListenCapabilities.supports(it.type) }
        .sortedByDescending { it.dateAddedTimestamp.coerceAtLeast(it.timestamp) }
        .map { SharedTtsListenItem(it, progressByBook[it.id]) }
}

/**
 * Mirrors Android's shouldAutoStartTtsAudiobook: a row click auto-starts TTS
 * unless the tapped book is already the active listening session.
 */
fun sharedShouldAutoStartTtsListen(requestedBookId: String?, state: SharedBookTtsListenState): Boolean =
    requestedBookId == null || state.bookId != requestedBookId || !state.connected

/**
 * Transcript window centered on the current chunk (2 behind, current, 3 ahead),
 * mirroring Android's resolveTtsTranscriptWindow. Returns an empty range when
 * there are no chunks.
 */
fun sharedTtsTranscriptWindow(currentChunkIndex: Int, chunkCount: Int): IntRange {
    if (chunkCount <= 0) return 0..-1
    val center = currentChunkIndex.coerceIn(0, chunkCount - 1)
    return (center - 2).coerceAtLeast(0)..(center + 3).coerceAtMost(chunkCount - 1)
}

fun SharedTtsListenItem.toSharedAudiobookLibraryItem(): SharedAudiobookLibraryItem =
    SharedAudiobookLibraryItem(
        id = book.id,
        progress = ((progress?.progressPercent ?: 0f) / 100f).coerceIn(0f, 1f),
        isTts = true,
        updatedAt = progress?.updatedAt ?: 0L,
    )

/**
 * Splits plain text into TTS chunks of at most [maxLength] characters, grouping
 * sentences and hard-splitting oversized sentences at word boundaries.
 *
 * Deliberately regex-free: the Kotlin/Native regex engine is ICU-backed, and
 * lookbehind-based sentence splits proved unreliable on device. The scan is
 * linear and engine-independent. Mirrors Android's chunking behavior.
 */
fun splitSharedTtsListenChunks(text: String, maxLength: Int = READER_TTS_CHUNK_MAX_LENGTH): List<String> {
    if (maxLength <= 0) return listOf(text).filter(String::isNotBlank)
    val normalized = text.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) return emptyList()
    val sentences = splitSharedTtsSentences(normalized)
    if (sentences.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    fun flush() {
        if (current.isNotBlank()) {
            chunks += current.toString().trim()
            current = StringBuilder()
        }
    }
    for (sentence in sentences) {
        if (sentence.length <= maxLength) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxLength) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
            continue
        }
        flush()
        var remaining = sentence
        while (remaining.length > maxLength) {
            var cut = remaining.lastIndexOf(' ', maxLength)
            if (cut <= 0) cut = maxLength
            chunks += remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty()) current.append(remaining)
    }
    flush()
    return chunks
}

private fun splitSharedTtsSentences(text: String): List<String> {
    // Sentence boundaries are runs of sentence punctuation (or a newline)
    // followed by whitespace.
    val sentences = mutableListOf<String>()
    var start = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '.' || char == '!' || char == '?' || char == '…' || char == '\n') {
            var end = index + 1
            while (end < text.length && (text[end] == '.' || text[end] == '!' || text[end] == '?' || text[end] == '…' || text[end] == '\n')) end++
            while (end < text.length && text[end].isWhitespace()) end++
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotBlank()) sentences += sentence
            start = end
            index = end
        } else {
            index++
        }
    }
    if (start < text.length) {
        val tail = text.substring(start).trim()
        if (tail.isNotBlank()) sentences += tail
    }
    return sentences
}
