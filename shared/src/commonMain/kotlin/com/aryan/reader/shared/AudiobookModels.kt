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
