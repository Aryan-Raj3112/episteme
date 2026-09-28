@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Disk store for recognized OCR page text, giving iOS cross-session search
 * persistence comparable to Android's Room FTS database (PdfTextRepository).
 * Words are stored per book (keyed by resolved file path) and validated
 * against the file revision + OCR language, so edits or language changes
 * invalidate stale entries automatically. Entries live in the app caches
 * directory (not backed up, evictable by the OS) and the in-memory map is
 * bounded with LRU eviction.
 */
internal object IosPdfOcrTextStore {
    private const val DirName = "pdf_ocr_text_v1"
    private const val MaxBooks = 32

    @Serializable
    private data class StoredWord(
        val t: String,
        val l: Float,
        val tp: Float,
        val r: Float,
        val b: Float,
    )

    @Serializable
    private data class StoredBook(
        val revision: String,
        val languages: List<String>,
        val pages: Map<Int, List<StoredWord>>,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, StoredBook>()
    private val fileManager = NSFileManager.defaultManager
    private var cachedDir: String? = null

    private fun booksDirectory(): String? {
        cachedDir?.let { return it }
        val base = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val dir = "$base/$DirName"
        fileManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        cachedDir = dir
        return dir
    }

    private fun fileNameFor(bookKey: String): String =
        bookKey.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }
            .joinToString("")
            .take(120) + ".json"

    private fun fileFor(bookKey: String): String? =
        booksDirectory()?.let { "$it/${fileNameFor(bookKey)}" }

    private fun StoredBook.toWords(pageIndex: Int): List<IosPdfOcrWord> =
        pages[pageIndex].orEmpty().mapNotNull { word ->
            val bounds = PdfPageBounds(word.l, word.tp, word.r, word.b)
            if (word.t.isNotBlank() && bounds.right > bounds.left && bounds.bottom > bounds.top) {
                IosPdfOcrWord(word.t, bounds)
            } else {
                null
            }
        }

    private suspend fun loadBook(bookKey: String, revision: String, languages: List<String>): StoredBook? =
        mutex.withLock {
            memory[bookKey]?.let { cached ->
                return@withLock if (cached.revision == revision && cached.languages == languages) {
                    cached
                } else {
                    memory.remove(bookKey)
                    null
                }
            }
            val file = fileFor(bookKey) ?: return@withLock null
            val raw = withContext(Dispatchers.Default) {
                NSString.stringWithContentsOfFile(file, encoding = NSUTF8StringEncoding, error = null) as String?
            } ?: return@withLock null
            val stored = runCatching { json.decodeFromString<StoredBook>(raw) }.getOrNull()
            if (stored == null || stored.revision != revision || stored.languages != languages) {
                fileManager.removeItemAtPath(file, error = null)
                return@withLock null
            }
            memory[bookKey] = stored
            stored
        }.also { loaded ->
            if (loaded != null) evictLocked(bookKey)
        }

    private fun evictLocked(keepKey: String) {
        while (memory.size > MaxBooks) {
            memory.minByOrNull { it.key }?.let { oldest ->
                if (oldest.key == keepKey) break
                memory.remove(oldest.key)
            } ?: break
        }
    }

    /** Stored words for [pageIndex], or `null` when nothing valid is on disk. */
    suspend fun loadPage(
        bookKey: String,
        revision: String,
        languages: List<String>,
        pageIndex: Int,
    ): List<IosPdfOcrWord>? {
        val book = loadBook(bookKey, revision, languages) ?: return null
        return mutex.withLock { book.toWords(pageIndex).ifEmpty { null } }
    }

    /** Updates the in-memory store; call [flush] to persist to disk. */
    suspend fun savePage(
        bookKey: String,
        revision: String,
        languages: List<String>,
        pageIndex: Int,
        words: List<IosPdfOcrWord>,
    ) {
        if (words.isEmpty()) return
        mutex.withLock {
            val existing = memory[bookKey]
            val book = existing?.takeIf { it.revision == revision && it.languages == languages }
                ?: StoredBook(revision, languages, emptyMap())
            memory[bookKey] = book.copy(
                pages = book.pages + (pageIndex to words.map { word ->
                    StoredWord(word.text, word.bounds.left, word.bounds.top, word.bounds.right, word.bounds.bottom)
                }),
            )
        }
    }

    /** Persists the in-memory entry for [bookKey]; no-op when nothing changed. */
    suspend fun flush(bookKey: String) {
        val file = fileFor(bookKey) ?: return
        val book = mutex.withLock { memory[bookKey] } ?: return
        withContext(Dispatchers.Default) {
            val encoded = runCatching {
                json.encodeToString(StoredBook.serializer(), book)
            }.getOrNull() ?: return@withContext
            (encoded as NSString).writeToFile(
                file,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
        }
    }

    /** Removes both the in-memory entry and its disk file. */
    suspend fun clear(bookKey: String) {
        mutex.withLock { memory.remove(bookKey) }
        fileFor(bookKey)?.let { file ->
            withContext(Dispatchers.Default) { fileManager.removeItemAtPath(file, error = null) }
        }
    }
}
