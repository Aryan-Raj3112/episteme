@file:OptIn(ExperimentalSerializationApi::class)

package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.semanticBlockModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.math.roundToLong
import kotlin.time.TimeSource

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
    private val storage: SharedEpubPageCacheStorage = defaultSharedEpubPageCacheStorage()
) {
    private val proto = ProtoBuf {
        serializersModule = semanticBlockModule
        encodeDefaults = true
    }

    private val memoryCache = SharedLruMemoryCache<String, List<ReaderPage>>(maxEntries = 10)
    private val chapterMemoryCache = SharedLruMemoryCache<String, List<ReaderPage>>(maxEntries = 24)
    private val memoryCacheMutex = Mutex()
    private val chapterMemoryCacheMutex = Mutex()

    suspend fun loadMemory(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        density: Float = 1f,
        fontScale: Float = 1f
    ): List<ReaderPage>? {
        val key = keyFor(book, settings, viewport, density, fontScale)
        return memoryCacheMutex.withLock {
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
    ): List<ReaderPage>? = withContext(Dispatchers.Default) {
        val startedAt = TimeSource.Monotonic.markNow()
        val key = keyFor(book, settings, viewport, density, fontScale)
        memoryCacheMutex.withLock {
            memoryCache[key.cacheId]?.let { pages ->
                logEpubPaginationCache {
                    "cache_lookup result=memory_hit book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                        "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@withContext pages
            }
        }

        val bytes = storage.readBytes(cacheFilePath(key))
        if (bytes == null) {
            logEpubPaginationCache {
                "cache_lookup result=miss reason=no_file book=\"${book.title.logPreview()}\" " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "root=\"${storage.rootLabel().logPreview(220)}\" elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            return@withContext null
        }

        runCatching {
            val record = proto.decodeFromByteArray<CachedReaderPages>(bytes)
            if (!record.matches(key)) {
                logEpubPaginationCache {
                    "cache_lookup result=miss reason=stale_record book=\"${book.title.logPreview()}\" " +
                        "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                        "fileBytes=${bytes.size} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@runCatching null
            }
            val pages = record.pages.mapIndexed { index, page -> page.toReaderPage(index) }
            if (pages.isEmpty() || pages.size != record.pageCount) {
                logEpubPaginationCache {
                    "cache_lookup result=miss reason=page_count_mismatch book=\"${book.title.logPreview()}\" " +
                        "storedPages=${record.pageCount} decodedPages=${pages.size} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@runCatching null
            }
            if (!pages.carriesRequiredSemanticBlocksFor(book)) {
                logEpubPaginationCache {
                    "cache_lookup result=miss reason=missing_semantic_blocks book=\"${book.title.logPreview()}\" " +
                        "pages=${pages.size} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@runCatching null
            }
            memoryCacheMutex.withLock {
                memoryCache[key.cacheId] = pages
            }
            logEpubPaginationCache {
                "cache_lookup result=disk_hit book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "fileBytes=${bytes.size} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            pages
        }.getOrElse { error ->
            logEpubPaginationCache {
                "cache_lookup result=miss reason=decode_failed book=\"${book.title.logPreview()}\" " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "error=\"${error.message.orEmpty().logPreview(180)}\" elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
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
    ): List<ReaderPage>? = withContext(Dispatchers.Default) {
        val startedAt = TimeSource.Monotonic.markNow()
        val key = keyFor(book, settings, viewport, density, fontScale)
        if (chapterIndex !in key.chapterVersions.indices) {
            logEpubPaginationCache {
                "chapter_cache_lookup result=miss reason=bad_chapter book=\"${book.title.logPreview()}\" " +
                    "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx}"
            }
            return@withContext null
        }
        val memoryKey = key.chapterCacheId(chapterIndex)
        chapterMemoryCacheMutex.withLock {
            chapterMemoryCache[memoryKey]?.let { pages ->
                logEpubPaginationCache {
                    "chapter_cache_lookup result=memory_hit book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                        "pages=${pages.size} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                        "config=${key.configHash.toUInt().toString(16)} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@withContext pages
            }
        }

        val bytes = storage.readBytes(chapterCacheFilePath(key, chapterIndex))
        if (bytes == null) {
            logEpubPaginationCache {
                "chapter_cache_lookup result=miss reason=no_file book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            return@withContext null
        }

        runCatching {
            val record = proto.decodeFromByteArray<CachedReaderChapterPages>(bytes)
            if (!record.matches(key, chapterIndex)) {
                logEpubPaginationCache {
                    "chapter_cache_lookup result=miss reason=stale_record book=\"${book.title.logPreview()}\" " +
                        "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx} " +
                        "fileBytes=${bytes.size} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@runCatching null
            }
            val pages = record.pages.mapIndexed { index, page -> page.toReaderPage(record.firstPageIndex + index) }
            if (pages.isEmpty() || pages.size != record.pageCount) {
                logEpubPaginationCache {
                    "chapter_cache_lookup result=miss reason=page_count_mismatch book=\"${book.title.logPreview()}\" " +
                        "chapter=$chapterIndex storedPages=${record.pageCount} decodedPages=${pages.size} " +
                        "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@runCatching null
            }
            if (!pages.carriesRequiredSemanticBlocksForChapter(book, chapterIndex)) {
                logEpubPaginationCache {
                    "chapter_cache_lookup result=miss reason=missing_semantic_blocks book=\"${book.title.logPreview()}\" " +
                        "chapter=$chapterIndex pages=${pages.size} elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                }
                return@runCatching null
            }
            chapterMemoryCacheMutex.withLock {
                chapterMemoryCache[memoryKey] = pages
            }
            logEpubPaginationCache {
                "chapter_cache_lookup result=disk_hit book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                    "pages=${pages.size} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                    "config=${key.configHash.toUInt().toString(16)} fileBytes=${bytes.size} " +
                    "elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            }
            pages
        }.getOrElse { error ->
            logEpubPaginationCache {
                "chapter_cache_lookup result=miss reason=decode_failed book=\"${book.title.logPreview()}\" " +
                    "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx} " +
                    "error=\"${error.message.orEmpty().logPreview(180)}\" elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
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
    ): Unit = withContext(Dispatchers.Default) {
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
        runCatching {
            val bytes = proto.encodeToByteArray(record)
            storage.writeBytesAtomically(cacheFilePath(key), bytes)
            memoryCacheMutex.withLock {
                memoryCache[key.cacheId] = pages.mapIndexed { index, page -> page.copy(pageIndex = index) }
            }
            saveChapterCaches(key, book, pages)
            cleanupOldConfigurations(key.bookHash)
            logEpubPaginationCache {
                "cache_save result=ok book=\"${book.title.logPreview()}\" pages=${pages.size} " +
                    "viewport=${viewport.widthPx}x${viewport.heightPx} config=${key.configHash.toUInt().toString(16)} " +
                    "fileBytes=${bytes.size}"
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

    suspend fun saveChapter(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        chapterIndex: Int,
        pages: List<ReaderPage>,
        firstPageIndex: Int,
        density: Float = 1f,
        fontScale: Float = 1f
    ): Unit = withContext(Dispatchers.Default) {
        if (pages.isEmpty()) {
            logEpubPaginationCache {
                "chapter_cache_save result=skip reason=empty_pages book=\"${book.title.logPreview()}\" " +
                    "chapter=$chapterIndex viewport=${viewport.widthPx}x${viewport.heightPx}"
            }
            return@withContext
        }
        if (chapterIndex !in book.chapters.indices || !pages.carriesRequiredSemanticBlocksForChapter(book, chapterIndex)) {
            logEpubPaginationCache {
                "chapter_cache_save result=skip reason=missing_semantic_blocks book=\"${book.title.logPreview()}\" " +
                    "chapter=$chapterIndex pages=${pages.size} viewport=${viewport.widthPx}x${viewport.heightPx}"
            }
            return@withContext
        }
        val key = keyFor(book, settings, viewport, density, fontScale)
        if (chapterIndex !in key.chapterVersions.indices) return@withContext
        val normalizedPages = pages
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
        runCatching {
            storage.writeBytesAtomically(chapterCacheFilePath(key, chapterIndex), proto.encodeToByteArray(record))
            chapterMemoryCacheMutex.withLock {
                chapterMemoryCache[key.chapterCacheId(chapterIndex)] = normalizedPages
            }
            logEpubPaginationCache {
                "chapter_cache_save result=ok book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                    "pages=${normalizedPages.size} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                    "config=${key.configHash.toUInt().toString(16)}"
            }
        }.onFailure { error ->
            logEpubPaginationCache {
                "chapter_cache_save result=failed book=\"${book.title.logPreview()}\" chapter=$chapterIndex " +
                    "pages=${normalizedPages.size} viewport=${viewport.widthPx}x${viewport.heightPx} " +
                    "config=${key.configHash.toUInt().toString(16)} " +
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
            settings.customFontPath.orEmpty(),
            settings.hideImages
        )
        return SharedEpubPaginationCacheKey(
            bookHash = sharedSha256Hex("${book.id}|${book.fileName}|$bookFingerprint").take(32),
            bookFingerprint = bookFingerprint,
            configHash = configHash,
            chapterVersions = chapterVersions
        )
    }

    suspend fun clearBook(book: SharedEpubBook) {
        val chapterVersions = book.chapters.map(::chapterContentVersion)
        val bookFingerprint = bookFingerprint(book, chapterVersions)
        val bookHash = sharedSha256Hex("${book.id}|${book.fileName}|$bookFingerprint").take(32)
        storage.deleteDirectory(bookHash)
        memoryCacheMutex.withLock {
            memoryCache.clear()
        }
        chapterMemoryCacheMutex.withLock {
            chapterMemoryCache.clear()
        }
    }

    suspend fun clearAll() {
        storage.deleteAll()
        memoryCacheMutex.withLock {
            memoryCache.clear()
        }
        chapterMemoryCacheMutex.withLock {
            chapterMemoryCache.clear()
        }
    }

    private fun cacheFilePath(key: SharedEpubPaginationCacheKey): String {
        return "${key.bookHash}/${key.configHash.toUInt().toString(16)}.pages.pb"
    }

    private fun chapterCacheDirPath(key: SharedEpubPaginationCacheKey): String {
        return "${key.bookHash}/${key.configHash.toUInt().toString(16)}.chapters"
    }

    private fun chapterCacheFilePath(key: SharedEpubPaginationCacheKey, chapterIndex: Int): String {
        return "${chapterCacheDirPath(key)}/chapter_$chapterIndex.pages.pb"
    }

    private suspend fun saveChapterCaches(
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
            storage.writeBytesAtomically(chapterCacheFilePath(key, chapterIndex), proto.encodeToByteArray(record))
            chapterMemoryCacheMutex.withLock {
                chapterMemoryCache[key.chapterCacheId(chapterIndex)] = normalizedPages
            }
        }
    }

    private fun cleanupOldConfigurations(bookHash: String) {
        val pagesFiles = pageCacheFiles(bookHash)
        pagesFiles.drop(3).forEach { name ->
            storage.deleteFile("$bookHash/$name")
            storage.deleteDirectory("$bookHash/${name.removeSuffix(".pages.pb")}.chapters")
        }
        val activeConfigNames = pagesFiles.take(3).map { it.removeSuffix(".pages.pb") }.toSet()
        storage.listFileNames(bookHash)
            .filter { name -> name.endsWith(".chapters") && name.removeSuffix(".chapters") !in activeConfigNames }
            .forEach { name -> storage.deleteDirectory("$bookHash/$name") }
    }

    private fun pageCacheFiles(bookHash: String): List<String> {
        return storage.listFileNames(bookHash)
            .filter { name -> name.endsWith(".pages.pb") }
            .sortedByDescending { name -> storage.lastModifiedMillis("$bookHash/$name") }
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

private fun Float.roundCacheValue(): String {
    val scaled = (this * 10_000).roundToLong()
    val negative = scaled < 0
    val absolute = kotlin.math.abs(scaled)
    val integerPart = absolute / 10_000
    val fractionPart = (absolute % 10_000).toString().padStart(4, '0')
    return (if (negative) "-" else "") + "$integerPart.$fractionPart"
}

private inline fun logEpubPaginationCache(message: () -> String) {
    logSharedReaderDiagnostic("EpistemeEpubPagination", message)
}

private fun String.logPreview(maxLength: Int = 96): String {
    return replace(Regex("\\s+"), " ")
        .trim()
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}