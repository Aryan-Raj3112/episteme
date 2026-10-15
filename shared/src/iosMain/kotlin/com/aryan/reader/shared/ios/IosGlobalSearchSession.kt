package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import com.aryan.reader.shared.search.SharedGlobalSearchCoordinator
import com.aryan.reader.shared.search.SharedGlobalSearchStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * iOS owner of the in-memory inside-files index. Mirrors Android's
 * `GlobalSearchSession` (same entry points, same membership-eviction
 * contract); the only difference is indexing runs in-process on
 * [Dispatchers.Default] ([indexBook]) instead of a WorkManager worker, so
 * there is no cross-process handoff to reconcile.
 *
 * All entry points are main-safe and serialized by [mutex]: callers pass
 * current library entries, so an index finishing mid-query cannot leave a
 * stale book visible. Results are always intersected with current library
 * ids; files are removed only by the delete path.
 */
internal class IosGlobalSearchSession(
    private val storage: SharedGlobalSearchStorage = IosGlobalSearchStorage(),
) {
    data class Entry(val bookId: String, val contentHash: String)

    private val coordinator = SharedGlobalSearchCoordinator(storage)
    private val mutex = Mutex()
    private val loadedHashes = mutableMapOf<String, String>()

    /**
     * Loads persisted payloads for new/changed books and forgets hashes of
     * books that left the library. Returns entries still needing an
     * indexing pass (nothing persisted for this hash).
     */
    suspend fun syncLibrary(entries: List<Entry>): List<Entry> = withContext(Dispatchers.Default) {
        mutex.withLock {
            refreshLocked(entries.associate { it.bookId to it.contentHash })
            entries.filter { loadedHashes[it.bookId] != it.contentHash }
        }
    }

    /**
     * Book results matching [rawQuery], best-first, after syncing [entries].
     * Empty means none. Snippets come from the persisted chunk texts, so the
     * library rows can preview the matched text and highlight it.
     */
    suspend fun query(entries: List<Entry>, rawQuery: String): List<SharedGlobalSearchBookResult> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                refreshLocked(entries.associate { it.bookId to it.contentHash })
                if (rawQuery.isBlank()) return@withLock emptyList()
                val wantedIds = entries.mapTo(HashSet()) { it.bookId }
                coordinator.search(rawQuery).filter { it.bookId in wantedIds }
            }
        }

    /**
     * True when a payload for [entry]'s exact hash is loaded, i.e. indexing
     * would be a no-op. Lets callers skip books they can already answer.
     */
    suspend fun isFresh(entry: Entry): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock {
            loadedHashes[entry.bookId] == entry.contentHash ||
                (coordinator.loadPersistedBook(entry.bookId) &&
                    coordinator.contentHashOf(entry.bookId) == entry.contentHash)
        }
    }

    /**
     * Extracts native text for [book] and upserts it under [contentHash].
     * Unparseable books (DRM, truncated) persist a NO_NATIVE_TEXT tombstone
     * so they are not re-extracted on every pass — the Android terminal
     * contract, not a retry loop.
     */
    suspend fun indexBook(book: BookItem, contentHash: String): Boolean =
        withContext(Dispatchers.Default) {
            val chunks = try {
                extractIosGlobalSearchChunks(book)
            } catch (_: Exception) {
                emptyList()
            }
            mutex.withLock {
                val indexed = coordinator.upsertBookChunks(book.id, contentHash, chunks)
                if (coordinator.contentHashOf(book.id) == contentHash) {
                    loadedHashes[book.id] = contentHash
                } else {
                    loadedHashes.remove(book.id)
                }
                indexed
            }
        }

    /** Ids with a persisted payload. File IO: callers stay off Main. */
    suspend fun storedBookIds(): Set<String> =
        withContext(Dispatchers.Default) { storage.storedBookIds() }

    /** Forgets one book's loaded hash (e.g. after delete). File removal is the caller's job. */    suspend fun remove(bookId: String) {
        mutex.withLock { loadedHashes.remove(bookId) }
        withContext(Dispatchers.Default) {
            mutex.withLock { coordinator.removeBook(bookId) }
        }
    }

    private fun refreshLocked(wanted: Map<String, String>) {
        for ((bookId, hash) in wanted) {
            if (loadedHashes[bookId] != hash) {
                // A payload indexed under an older hash must not masquerade
                // as fresh: only record when the persisted hash matches.
                val loaded = coordinator.loadPersistedBook(bookId) &&
                    coordinator.contentHashOf(bookId) == hash
                if (loaded) {
                    loadedHashes[bookId] = hash
                } else {
                    loadedHashes.remove(bookId)
                }
            }
        }
        loadedHashes.keys.retainAll(wanted.keys)
    }

    companion object {
        /**
         * Identity for "did this book change". Same `size:mtime` scheme as
         * Android's `GlobalSearchIndexer`: sha256 would require reading every
         * file on every scan, while the extractor re-checks freshness before
         * any heavy work.
         */
        fun contentHashFor(book: BookItem): String =
            "${book.fileSize}:${book.fileContentModifiedTimestamp}"
    }
}
