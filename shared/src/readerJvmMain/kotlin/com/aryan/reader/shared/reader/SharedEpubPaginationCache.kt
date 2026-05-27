@file:OptIn(ExperimentalSerializationApi::class)

package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.semanticBlockModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private const val SharedEpubPaginationCacheSchemaVersion = 1
private const val SharedEpubPaginationProcessingVersion = 12
private const val SharedEpubPaginationPageCacheVersion = 2

data class SharedEpubPaginationCacheKey(
    val bookHash: String,
    val bookFingerprint: Int,
    val configHash: Int,
    val chapterVersions: List<Int>
) {
    val cacheId: String = "${bookHash}_${configHash.toUInt().toString(16)}"

    fun chapterCacheId(chapterIndex: Int): String {
        return "${cacheId}_chapter_$chapterIndex"
    }
}

class SharedEpubPaginationCache(
    private val cacheRoot: File = defaultCacheRoot()
) {
    private val proto = ProtoBuf {
        serializersModule = semanticBlockModule
        encodeDefaults = true
    }

    private val memoryCache = SharedJvmLruMemoryCache<String, List<ReaderPage>>(maxEntries = 10)
    private val chapterMemoryCache = SharedJvmLruMemoryCache<String, List<ReaderPage>>(maxEntries = 24)

    fun loadMemory(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        density: Float = 1f,
        fontScale: Float = 1f
    ): List<ReaderPage>? {
        val key = keyFor(book, settings, viewport, density, fontScale)
        return synchronized(memoryCache) {
            memoryCache[key.cacheId]
        }?.also { pages ->
            logEpubPaginationCache {
                "cache_lookup result=memory_hit book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)}"
            }
        }
    }

    suspend fun load(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        density: Float = 1f,
        fontScale: Float = 1f
    ): List<ReaderPage>? = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val key = keyFor(book, settings, viewport, density, fontScale)
        synchronized(memoryCache) {
            memoryCache[key.cacheId]?.let { pages ->
                logEpubPaginationCache {
                    "cache_lookup result=memory_hit book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                        "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                        "elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@withContext pages
            }
        }

        val file = cacheFile(key)
        if (!file.isFile) {
            logEpubPaginationCache {
                "cache_lookup result=miss reason=no_file book=\"${book.title.logPreview()}\" " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "path=\"${file.absolutePath.logPreview(220)}\" elapsedMs=${startedAt.elapsedMillis()}"
            }
            return@withContext null
        }

        runCatching {
            val record = proto.decodeFromByteArray<CachedReaderPages>(file.readBytes())
            if (!record.matches(key)) {
                logEpubPaginationCache {
                    "cache_lookup result=miss reason=stale_record book=\"${book.title.logPreview()}\" " +
                        "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                        "fileBytes=${file.length()} elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@runCatching null
            }
            val pages = record.pages.mapIndexed { index, page -> page.toReaderPage(index) }
            if (pages.isEmpty() || pages.size != record.pageCount) {
                logEpubPaginationCache {
                    "cache_lookup result=miss reason=page_count_mismatch book=\"${book.title.logPreview()}\" " +
                        "storedPages=${record.pageCount} decodedPages=${pages.size} elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@runCatching null
            }
            if (!pages.carriesRequiredSemanticBlocksFor(book)) {
                logEpubPaginationCache {
                    "cache_lookup result=miss reason=missing_semantic_blocks book=\"${book.title.logPreview()}\" " +
                        "pages=${pages.size} elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@runCatching null
            }
            synchronized(memoryCache) {
                memoryCache[key.cacheId] = pages
            }
            logEpubPaginationCache {
                "cache_lookup result=disk_hit book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "fileBytes=${file.length()} elapsedMs=${startedAt.elapsedMillis()}"
            }
            pages
        }.getOrElse { error ->
            logEpubPaginationCache {
                "cache_lookup result=miss reason=decode_failed book=\"${book.title.logPreview()}\" " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "error=\"${error.message.orEmpty().logPreview(180)}\" elapsedMs=${startedAt.elapsedMillis()}"
            }
            null
        }
    }

    suspend fun loadChapter(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        chapterIndex: Int,
        density: Float = 1f,
        fontScale: Float = 1f
    ): List<ReaderPage>? = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val key = keyFor(book, settings, viewport, density, fontScale)
        if (chapterIndex !in key.chapterVersions.indices) {
            logEpubPaginationCache {
                "chapter_cache_lookup result=miss reason=bad_chapter book=\"${book.title.logPreview()}\" " +
                    "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx}"
            }
            return@withContext null
        }
        val memoryKey = key.chapterCacheId(chapterIndex)
        synchronized(chapterMemoryCache) {
            chapterMemoryCache[memoryKey]?.let { pages ->
                logEpubPaginationCache {
                    "chapter_cache_lookup result=memory_hit book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                        "pages=${pages.size} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                        "config=${key.configHash.toUInt().toString(16)} elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@withContext pages
            }
        }

        val file = chapterCacheFile(key, chapterIndex)
        if (!file.isFile) {
            logEpubPaginationCache {
                "chapter_cache_lookup result=miss reason=no_file book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "elapsedMs=${startedAt.elapsedMillis()}"
            }
            return@withContext null
        }

        runCatching {
            val record = proto.decodeFromByteArray<CachedReaderChapterPages>(file.readBytes())
            if (!record.matches(key, chapterIndex)) {
                logEpubPaginationCache {
                    "chapter_cache_lookup result=miss reason=stale_record book=\"${book.title.logPreview()}\" " +
                        "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx} " +
                        "fileBytes=${file.length()} elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@runCatching null
            }
            val pages = record.pages.mapIndexed { index, page -> page.toReaderPage(record.firstPageIndex + index) }
            if (pages.isEmpty() || pages.size != record.pageCount) {
                logEpubPaginationCache {
                    "chapter_cache_lookup result=miss reason=page_count_mismatch book=\"${book.title.logPreview()}\" " +
                        "chapter=$chapterIndex storedPages=${record.pageCount} decodedPages=${pages.size} " +
                        "elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@runCatching null
            }
            if (!pages.carriesRequiredSemanticBlocksForChapter(book, chapterIndex)) {
                logEpubPaginationCache {
                    "chapter_cache_lookup result=miss reason=missing_semantic_blocks book=\"${book.title.logPreview()}\" " +
                        "chapter=$chapterIndex pages=${pages.size} elapsedMs=${startedAt.elapsedMillis()}"
                }
                return@runCatching null
            }
            synchronized(chapterMemoryCache) {
                chapterMemoryCache[memoryKey] = pages
            }
            logEpubPaginationCache {
                "chapter_cache_lookup result=disk_hit book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                    "pages=${pages.size} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                    "config=${key.configHash.toUInt().toString(16)} fileBytes=${file.length()} " +
                    "elapsedMs=${startedAt.elapsedMillis()}"
            }
            pages
        }.getOrElse { error ->
            logEpubPaginationCache {
                "chapter_cache_lookup result=miss reason=decode_failed book=\"${book.title.logPreview()}\" " +
                    "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx} " +
                    "error=\"${error.message.orEmpty().logPreview(180)}\" elapsedMs=${startedAt.elapsedMillis()}"
            }
            null
        }
    }

    suspend fun save(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        pages: List<ReaderPage>,
        density: Float = 1f,
        fontScale: Float = 1f
    ): Unit = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            logEpubPaginationCache {
                "cache_save result=skip reason=empty_pages book=\"${book.title.logPreview()}\" " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx}"
            }
            return@withContext
        }
        if (!pages.carriesRequiredSemanticBlocksFor(book)) {
            logEpubPaginationCache {
                "cache_save result=skip reason=missing_semantic_blocks book=\"${book.title.logPreview()}\" " +
                    "pages=${pages.size} viewport=${viewport.widthPx}x${viewport.heightPx}"
            }
            return@withContext
        }
        val key = keyFor(book, settings, viewport, density, fontScale)
        val record = CachedReaderPages(
            schemaVersion = SharedEpubPaginationCacheSchemaVersion,
            processingVersion = SharedEpubPaginationProcessingVersion,
            pageCacheVersion = SharedEpubPaginationPageCacheVersion,
            bookFingerprint = key.bookFingerprint,
            configHash = key.configHash,
            chapterVersions = key.chapterVersions,
            pageCount = pages.size,
            pages = pages.map(CachedReaderPage::from)
        )
        val file = cacheFile(key)
        runCatching {
            writeAtomically(file, proto.encodeToByteArray(record))
            synchronized(memoryCache) {
                memoryCache[key.cacheId] = pages.mapIndexed { index, page -> page.copy(pageIndex = index) }
            }
            saveChapterCaches(key, book, pages)
            cleanupOldConfigurations(key.bookHash)
            logEpubPaginationCache {
                "cache_save result=ok book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "fileBytes=${file.length()}"
            }
        }.onFailure { error ->
            logEpubPaginationCache {
                "cache_save result=failed book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "error=\"${error.message.orEmpty().logPreview(180)}\""
            }
        }
        Unit
    }

    fun keyFor(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        density: Float = 1f,
        fontScale: Float = 1f
    ): SharedEpubPaginationCacheKey {
        val chapterVersions = book.chapters.map(::chapterContentVersion)
        val bookFingerprint = bookFingerprint(book, chapterVersions)
        val configHash = stableHash(
            SharedEpubPaginationProcessingVersion,
            SharedEpubPaginationPageCacheVersion,
            viewport.widthPx,
            viewport.heightPx,
            density.roundCacheValue(),
            fontScale.roundCacheValue(),
            settings.fontSize,
            settings.lineSpacing.roundCacheValue(),
            settings.resolvedHorizontalMargin,
            settings.resolvedVerticalMargin,
            settings.readingMode.name,
            settings.textAlign.name,
            settings.pageWidth,
            settings.fontFamily,
            settings.paragraphSpacing.roundCacheValue(),
            settings.imageScale.roundCacheValue(),
            settings.pageSpreadMode.name,
            settings.customFontPath.orEmpty()
        )
        return SharedEpubPaginationCacheKey(
            bookHash = sha256Hex("${book.id}|${book.fileName}|$bookFingerprint").take(32),
            bookFingerprint = bookFingerprint,
            configHash = configHash,
            chapterVersions = chapterVersions
        )
    }

    fun clearBook(book: SharedEpubBook) {
        val chapterVersions = book.chapters.map(::chapterContentVersion)
        val bookFingerprint = bookFingerprint(book, chapterVersions)
        val bookHash = sha256Hex("${book.id}|${book.fileName}|$bookFingerprint").take(32)
        File(cacheRoot, bookHash).deleteRecursively()
        synchronized(memoryCache) {
            memoryCache.clear()
        }
        synchronized(chapterMemoryCache) {
            chapterMemoryCache.clear()
        }
    }

    fun clearAll() {
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
        synchronized(memoryCache) {
            memoryCache.clear()
        }
        synchronized(chapterMemoryCache) {
            chapterMemoryCache.clear()
        }
    }

    private fun cacheFile(key: SharedEpubPaginationCacheKey): File {
        return File(File(cacheRoot, key.bookHash), "${key.configHash.toUInt().toString(16)}.pages.pb")
    }

    private fun chapterCacheDir(key: SharedEpubPaginationCacheKey): File {
        return File(File(cacheRoot, key.bookHash), "${key.configHash.toUInt().toString(16)}.chapters")
    }

    private fun chapterCacheFile(key: SharedEpubPaginationCacheKey, chapterIndex: Int): File {
        return File(chapterCacheDir(key), "chapter_$chapterIndex.pages.pb")
    }

    private fun saveChapterCaches(
        key: SharedEpubPaginationCacheKey,
        book: SharedEpubBook,
        pages: List<ReaderPage>
    ) {
        pages.groupBy { it.chapterIndex }.forEach { (chapterIndex, chapterPages) ->
            if (chapterIndex !in key.chapterVersions.indices) return@forEach
            if (!chapterPages.carriesRequiredSemanticBlocksForChapter(book, chapterIndex)) return@forEach
            val firstPageIndex = chapterPages.minOfOrNull { it.pageIndex } ?: return@forEach
            val normalizedPages = chapterPages
                .sortedBy { it.pageIndex }
                .mapIndexed { index, page -> page.copy(pageIndex = firstPageIndex + index) }
            val record = CachedReaderChapterPages(
                schemaVersion = SharedEpubPaginationCacheSchemaVersion,
                processingVersion = SharedEpubPaginationProcessingVersion,
                pageCacheVersion = SharedEpubPaginationPageCacheVersion,
                bookFingerprint = key.bookFingerprint,
                configHash = key.configHash,
                chapterVersion = key.chapterVersions[chapterIndex],
                chapterIndex = chapterIndex,
                firstPageIndex = firstPageIndex,
                pageCount = normalizedPages.size,
                pages = normalizedPages.map(CachedReaderPage::from)
            )
            writeAtomically(chapterCacheFile(key, chapterIndex), proto.encodeToByteArray(record))
            synchronized(chapterMemoryCache) {
                chapterMemoryCache[key.chapterCacheId(chapterIndex)] = normalizedPages
            }
        }
    }

    private fun cleanupOldConfigurations(bookHash: String) {
        val bookDir = File(cacheRoot, bookHash)
        val files = bookDir.listFiles { file -> file.isFile && file.name.endsWith(".pages.pb") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(3).forEach { file ->
            file.delete()
            File(bookDir, file.name.removeSuffix(".pages.pb") + ".chapters").deleteRecursively()
        }
        val activeConfigNames = files.take(3).map { it.name.removeSuffix(".pages.pb") }.toSet()
        bookDir.listFiles { file -> file.isDirectory && file.name.endsWith(".chapters") }
            .orEmpty()
            .filterNot { dir -> dir.name.removeSuffix(".chapters") in activeConfigNames }
            .forEach { it.deleteRecursively() }
    }

    private fun CachedReaderPages.matches(key: SharedEpubPaginationCacheKey): Boolean {
        return schemaVersion == SharedEpubPaginationCacheSchemaVersion &&
            processingVersion == SharedEpubPaginationProcessingVersion &&
            pageCacheVersion == SharedEpubPaginationPageCacheVersion &&
            bookFingerprint == key.bookFingerprint &&
            configHash == key.configHash &&
            chapterVersions == key.chapterVersions
    }

    private fun CachedReaderChapterPages.matches(
        key: SharedEpubPaginationCacheKey,
        expectedChapterIndex: Int
    ): Boolean {
        return schemaVersion == SharedEpubPaginationCacheSchemaVersion &&
            processingVersion == SharedEpubPaginationProcessingVersion &&
            pageCacheVersion == SharedEpubPaginationPageCacheVersion &&
            bookFingerprint == key.bookFingerprint &&
            configHash == key.configHash &&
            chapterIndex == expectedChapterIndex &&
            expectedChapterIndex in key.chapterVersions.indices &&
            chapterVersion == key.chapterVersions[expectedChapterIndex]
    }

    companion object {
        fun defaultCacheRoot(): File {
            val overridePath = System.getProperty("reader.epub.pagination.cache.dir")
            if (!overridePath.isNullOrBlank()) return File(overridePath).apply { mkdirs() }
            return File(sharedJvmEpistemeCacheRoot(), "epub_page_cache").apply { mkdirs() }
        }
    }
}

@Serializable
private data class CachedReaderPages(
    @ProtoNumber(1) val schemaVersion: Int,
    @ProtoNumber(2) val processingVersion: Int,
    @ProtoNumber(3) val pageCacheVersion: Int,
    @ProtoNumber(4) val bookFingerprint: Int,
    @ProtoNumber(5) val configHash: Int,
    @ProtoNumber(6) val chapterVersions: List<Int>,
    @ProtoNumber(7) val pageCount: Int,
    @ProtoNumber(8) val pages: List<CachedReaderPage>
)

@Serializable
private data class CachedReaderChapterPages(
    @ProtoNumber(1) val schemaVersion: Int,
    @ProtoNumber(2) val processingVersion: Int,
    @ProtoNumber(3) val pageCacheVersion: Int,
    @ProtoNumber(4) val bookFingerprint: Int,
    @ProtoNumber(5) val configHash: Int,
    @ProtoNumber(6) val chapterVersion: Int,
    @ProtoNumber(7) val chapterIndex: Int,
    @ProtoNumber(8) val firstPageIndex: Int,
    @ProtoNumber(9) val pageCount: Int,
    @ProtoNumber(10) val pages: List<CachedReaderPage>
)

@Serializable
private data class CachedReaderPage(
    @ProtoNumber(1) val chapterIndex: Int,
    @ProtoNumber(2) val chapterTitle: String,
    @ProtoNumber(3) val text: String,
    @ProtoNumber(4) val startOffset: Int,
    @ProtoNumber(5) val endOffset: Int,
    @ProtoNumber(6) val semanticBlocks: List<SemanticBlock>
) {
    fun toReaderPage(pageIndex: Int): ReaderPage {
        return ReaderPage(
            pageIndex = pageIndex,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            text = text,
            startOffset = startOffset,
            endOffset = endOffset,
            semanticBlocks = semanticBlocks
        )
    }

    companion object {
        fun from(page: ReaderPage): CachedReaderPage {
            return CachedReaderPage(
                chapterIndex = page.chapterIndex,
                chapterTitle = page.chapterTitle,
                text = page.text,
                startOffset = page.startOffset,
                endOffset = page.endOffset,
                semanticBlocks = page.semanticBlocks
            )
        }
    }
}

internal fun sharedEpubChapterContentVersion(chapter: SharedEpubChapter): Int {
    return chapterContentVersion(chapter)
}

private fun chapterContentVersion(chapter: SharedEpubChapter): Int {
    return stableHash(
        chapter.id,
        chapter.title,
        chapter.baseHref.orEmpty(),
        chapter.plainText.length,
        chapter.plainText.hashCode(),
        chapter.htmlContent.length,
        chapter.htmlContent.hashCode(),
        chapter.semanticBlocks.hashCode()
    )
}

private fun bookFingerprint(book: SharedEpubBook, chapterVersions: List<Int>): Int {
    return stableHash(
        SharedEpubPaginationProcessingVersion,
        book.id,
        book.fileName,
        book.title,
        book.author.orEmpty(),
        book.css.hashCode(),
        chapterVersions.joinToString(",")
    )
}

private fun List<ReaderPage>.carriesRequiredSemanticBlocksFor(book: SharedEpubBook): Boolean {
    val semanticChapterIndexes = book.chapters
        .mapIndexedNotNull { index, chapter -> index.takeIf { chapter.semanticBlocks.isNotEmpty() } }
    if (semanticChapterIndexes.isEmpty()) return true
    return semanticChapterIndexes.all { chapterIndex ->
        any { page -> page.chapterIndex == chapterIndex && page.semanticBlocks.isNotEmpty() }
    }
}

private fun List<ReaderPage>.carriesRequiredSemanticBlocksForChapter(
    book: SharedEpubBook,
    chapterIndex: Int
): Boolean {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return false
    if (chapter.semanticBlocks.isEmpty()) return true
    return any { page -> page.chapterIndex == chapterIndex && page.semanticBlocks.isNotEmpty() }
}

internal fun stableHash(vararg parts: Any?): Int {
    return parts.joinToString(separator = "\u001F") { part ->
        when (part) {
            null -> "<null>"
            is Float -> part.roundCacheValue()
            is Double -> part.toFloat().roundCacheValue()
            else -> part.toString()
        }
    }.hashCode()
}

internal fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xFF) }
}

private fun Float.roundCacheValue(): String {
    return "%.4f".format(Locale.US, this)
}

private fun writeAtomically(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val parent = file.parentFile ?: file.absoluteFile.parentFile ?: File(".")
    val temp = File(parent, "${file.name}.tmp")
    temp.writeBytes(bytes)
    if (file.exists() && !file.delete()) {
        temp.delete()
        return
    }
    if (!temp.renameTo(file)) {
        file.writeBytes(bytes)
        temp.delete()
    }
}

private inline fun logEpubPaginationCache(message: () -> String) {
    logSharedReaderDiagnostic("EpistemeEpubPagination", message)
}

private fun Long.elapsedMillis(): Long {
    return ((System.nanoTime() - this) / 1_000_000L).coerceAtLeast(0L)
}

private fun String.logPreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}
