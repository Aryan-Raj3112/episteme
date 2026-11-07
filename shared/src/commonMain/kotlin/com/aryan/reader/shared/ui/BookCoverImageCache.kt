package com.aryan.reader.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.aryan.reader.shared.LIBRARY_COVER_LOG_TAG
import com.aryan.reader.shared.reader.writeSharedReaderDiagnostic
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Longest edge a decoded cover is kept at. Covers render inside ~100-160dp
 * library cells, so decoding the 1200px source at full size only burns memory.
 */
internal const val BookCoverMaxDimensionPx = 512

/** Roughly two screens of a dense grid. */
internal const val BookCoverMaxEntries = 160

/**
 * Decoding is CPU+IO bound; more slots than this just trades memory for latency.
 */
private const val BookCoverMaxConcurrentDecodes = 2

/**
 * Identity of the bytes currently at [path], or null when nothing is there.
 *
 * Cover files are rewritten *in place* (iOS persists to `Covers/<bookId>.<ext>`),
 * so a path alone does not identify its contents. Keying a cache on the path
 * alone makes a replaced cover unreachable until the process restarts, which is
 * the iOS/Android divergence this cache removes.
 */
internal expect fun bookCoverFileStamp(path: String): String?

/** Decodes [path] and downscales it to fit [maxDimension] on its longest edge. */
internal expect fun decodeBookCoverBitmap(path: String, maxDimension: Int): ImageBitmap?

/**
 * In-memory cover cache shared by every platform, so iOS, Android and desktop all
 * get the behaviour Android's Coil pipeline provides: a decoded bitmap that
 * survives scrolling, is reused across screens, downsampled to the size it is
 * drawn at, re-read when the file on disk changes, and decoded off the
 * composition thread.
 *
 * The table is immutable and swapped wholesale on write, so [peek] is a lock-free
 * read on the composition thread and a decode racing another decode cannot corrupt
 * it. Copy-on-write is affordable because a write only happens on a cache miss,
 * which is immediately preceded by a far more expensive decode.
 *
 * Eviction is oldest-insertion-first. Recency is not tracked, because doing so
 * would mean republishing the table on every hit, and [peek] runs on the
 * composition thread; at this size the only cost of the simpler policy is an
 * occasional re-decode.
 */
internal object BookCoverImageCache {
    private class Entry(val stamp: String, val bitmap: ImageBitmap)

    private val decodeSlots = Semaphore(BookCoverMaxConcurrentDecodes)
    private var entries by mutableStateOf<Map<String, Entry>>(emptyMap())
    private val revisionState = mutableStateOf(0)

    /**
     * Bumped whenever a *displayed* cover is invalidated, so mounted composables
     * re-read instead of holding a bitmap decoded from the previous contents.
     */
    val revision: Int get() = revisionState.value

    /** Instant first paint for an already-cached cover. Never blocks on IO. */
    fun peek(path: String): ImageBitmap? = entries[path]?.bitmap

    suspend fun load(path: String): ImageBitmap? {
        val stamp = bookCoverFileStamp(path)
        if (stamp == null) {
            // The renderer was handed a path with no file behind it. Either the
            // cover was never written, or it was written somewhere else.
            writeSharedReaderDiagnostic(
                LIBRARY_COVER_LOG_TAG,
                "cache.load.miss-no-file path=${path.coverLogPath()}",
            )
            return null
        }
        val cached = entries[path]
        if (cached != null) {
            // Cache hits are the steady state and repeat on every recomposition of
            // every visible cell, so only the interesting outcomes are logged.
            if (cached.stamp == stamp) return cached.bitmap
            writeSharedReaderDiagnostic(
                LIBRARY_COVER_LOG_TAG,
                "cache.load.stale path=${path.coverLogPath()} cachedStamp=${cached.stamp} diskStamp=$stamp",
            )
        }
        val bitmap = decodeSlots.withPermit { decodeBookCoverBitmap(path, BookCoverMaxDimensionPx) }
        if (bitmap == null) {
            writeSharedReaderDiagnostic(
                LIBRARY_COVER_LOG_TAG,
                "cache.load.decode-failed path=${path.coverLogPath()} stamp=$stamp",
            )
            return null
        }
        writeSharedReaderDiagnostic(
            LIBRARY_COVER_LOG_TAG,
            "cache.load.decoded path=${path.coverLogPath()} " +
                "size=${bitmap.width}x${bitmap.height} storedStamp=$stamp",
        )
        val current = entries
        val updated = LinkedHashMap<String, Entry>(current.size + 1)
        updated.putAll(current)
        updated.remove(path)
        updated[path] = Entry(stamp, bitmap)
        entries = if (updated.size > BookCoverMaxEntries) {
            updated.entries
                .drop(updated.size - BookCoverMaxEntries)
                .associateTo(LinkedHashMap()) { it.key to it.value }
        } else {
            updated
        }
        return bitmap
    }

    /**
     * Drops [path] after its file was rewritten, so the next draw shows the new
     * bytes.
     *
     * The revision only moves when something was actually cached: bulk extraction
     * writes a cover for every imported book, and bumping per write would make
     * every mounted cover re-read once per imported book.
     *
     * A write racing a concurrent [load] can drop that load's entry. That is
     * harmless — the entry's stamp no longer matches, so the next [load] decodes
     * again.
     */
    fun invalidate(path: String) {
        val wasCached = entries.containsKey(path)
        if (!wasCached) return
        entries = entries.filterKeys { it != path }
        revisionState.value = revisionState.value + 1
        writeSharedReaderDiagnostic(
            LIBRARY_COVER_LOG_TAG,
            "cache.invalidate path=${path.coverLogPath()} revision=$revision",
        )
    }

    fun clearForTests() {
        entries = emptyMap()
    }
}

/**
 * Full paths are logged, but the container UUID and the user's folder names are
 * not something to copy into a bug report by accident. The tail is enough to tell
 * two covers apart and to match against the extraction log.
 */
internal fun String.coverLogPath(): String {
    val segments = split('/').filter(String::isNotBlank)
    if (segments.size <= 2) return this
    return ".../${segments.takeLast(2).joinToString("/")}"
}