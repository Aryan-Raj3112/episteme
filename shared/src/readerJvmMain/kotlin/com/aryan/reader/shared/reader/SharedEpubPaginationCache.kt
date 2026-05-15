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
private const val SharedEpubPaginationProcessingVersion = 6
private const val SharedEpubPaginationPageCacheVersion = 1

data class SharedEpubPaginationCacheKey(
    val bookHash: String,
    val bookFingerprint: Int,
    val configHash: Int,
    val chapterVersions: List<Int>
) {
    val cacheId: String = "${bookHash}_${configHash.toUInt().toString(16)}"
}

class SharedEpubPaginationCache(
    private val cacheRoot: File = defaultCacheRoot()
) {
    private val proto = ProtoBuf {
        serializersModule = semanticBlockModule
        encodeDefaults = true
    }

    private val memoryCache = SharedJvmLruMemoryCache<String, List<ReaderPage>>(maxEntries = 10)

    suspend fun load(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        density: Float = 1f,
        fontScale: Float = 1f
    ): List<ReaderPage>? = withContext(Dispatchers.IO) {
        val key = keyFor(book, settings, viewport, density, fontScale)
        synchronized(memoryCache) {
            memoryCache[key.cacheId]?.let { return@withContext it }
        }

        val file = cacheFile(key)
        if (!file.isFile) return@withContext null

        runCatching {
            val record = proto.decodeFromByteArray<CachedReaderPages>(file.readBytes())
            if (!record.matches(key)) return@runCatching null
            val pages = record.pages.mapIndexed { index, page -> page.toReaderPage(index) }
            if (pages.isEmpty() || pages.size != record.pageCount) return@runCatching null
            synchronized(memoryCache) {
                memoryCache[key.cacheId] = pages
            }
            pages
        }.getOrNull()
    }

    suspend fun save(
        book: SharedEpubBook,
        settings: ReaderSettings,
        viewport: ReaderViewportSpec,
        pages: List<ReaderPage>,
        density: Float = 1f,
        fontScale: Float = 1f
    ): Unit = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext
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
            cleanupOldConfigurations(key.bookHash)
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
    }

    fun clearAll() {
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
        synchronized(memoryCache) {
            memoryCache.clear()
        }
    }

    private fun cacheFile(key: SharedEpubPaginationCacheKey): File {
        return File(File(cacheRoot, key.bookHash), "${key.configHash.toUInt().toString(16)}.pages.pb")
    }

    private fun cleanupOldConfigurations(bookHash: String) {
        val bookDir = File(cacheRoot, bookHash)
        val files = bookDir.listFiles { file -> file.isFile && file.name.endsWith(".pages.pb") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(3).forEach { it.delete() }
    }

    private fun CachedReaderPages.matches(key: SharedEpubPaginationCacheKey): Boolean {
        return schemaVersion == SharedEpubPaginationCacheSchemaVersion &&
            processingVersion == SharedEpubPaginationProcessingVersion &&
            pageCacheVersion == SharedEpubPaginationPageCacheVersion &&
            bookFingerprint == key.bookFingerprint &&
            configHash == key.configHash &&
            chapterVersions == key.chapterVersions
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
