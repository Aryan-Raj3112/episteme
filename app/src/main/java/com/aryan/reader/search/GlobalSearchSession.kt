package com.aryan.reader.search

import com.aryan.reader.shared.search.SharedGlobalSearchBookResult
import com.aryan.reader.shared.search.SharedGlobalSearchCoordinator
import com.aryan.reader.shared.search.SharedGlobalSearchStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide owner of the in-memory content index. Workers persist
 * per-book payloads; this holder loads them on demand and answers queries.
 * All entry points are main-safe and serialized by [mutex]: callers pass
 * current library entries, so a worker finishing mid-query cannot leave a
 * stale book visible.
 *
 * Eviction is by library membership: results are always intersected with
 * current library ids by the caller, so an in-memory payload whose book
 * left the library can never surface. Files are removed only by the
 * delete path ([GlobalSearchIndexer.cancel]).
 */
class GlobalSearchSession(
    filesDir: File,
    private val storage: SharedGlobalSearchStorage = GlobalSearchFileStorage.fromFilesDir(filesDir),
) {
    data class Entry(val bookId: String, val contentHash: String)

    private val coordinator = SharedGlobalSearchCoordinator(storage)
    private val mutex = Mutex()
    private val loadedHashes = mutableMapOf<String, String>()

    /**
     * Loads persisted payloads for new/changed books and forgets hashes of
     * books that left the library. Returns entries still needing a
     * background pass (nothing persisted for this hash).
     */
    suspend fun syncLibrary(entries: List<Entry>): List<Entry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            refreshLocked(entries.associate { it.bookId to it.contentHash })
            val missing = entries.filter { loadedHashes[it.bookId] != it.contentHash }
            GlobalSearchLog.d {
                "syncLibrary entries=${entries.size} missing=${missing.size} " +
                    "storedFiles=${storage.storedBookIds().size} inMemory=${coordinator.bookCount()}"
            }
            missing
        }
    }

    /**
     * Book results matching [rawQuery], best-first, after syncing [entries].
     * Empty means none. Snippets come from the persisted chunk texts, so the
     * library rows can preview the matched text and highlight it.
     */
    suspend fun query(entries: List<Entry>, rawQuery: String): List<SharedGlobalSearchBookResult> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                refreshLocked(entries.associate { it.bookId to it.contentHash })
                if (rawQuery.isBlank()) return@withLock emptyList()
                val results = coordinator.search(rawQuery)
                val wantedIds = entries.mapTo(HashSet()) { entry -> entry.bookId }
                val visible = results.filter { it.bookId in wantedIds }
                GlobalSearchLog.d {
                    "query raw='$rawQuery' entries=${entries.size} " +
                        "inMemory=${coordinator.bookCount()} books=${visible.size} " +
                        "ids=${visible.take(8).map { it.bookId }}"
                }
                visible
            }
        }

    /**
     * True when a persisted payload for [entry]'s exact hash is loaded, i.e.
     * indexing would be a no-op. Lets callers skip enqueueing work they can
     * already answer from disk.
     */
    suspend fun isFresh(entry: Entry): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadedHashes[entry.bookId] == entry.contentHash ||
                (coordinator.loadPersistedBook(entry.bookId) &&
                    coordinator.contentHashOf(entry.bookId) == entry.contentHash)
        }
    }

    /** Forgets one book's loaded hash (e.g. after delete). File removal is the caller's job. */
    suspend fun remove(bookId: String) {
        mutex.withLock { loadedHashes.remove(bookId) }
    }

    /** Ids with a persisted payload. Synchronous file IO: callers stay off Main. */
    fun storedBookIds(): Set<String> = storage.storedBookIds()

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
}
