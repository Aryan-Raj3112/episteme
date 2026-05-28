@file:OptIn(ExperimentalSerializationApi::class)

package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.semanticBlockModule
import com.aryan.reader.shared.FileType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File

private const val SharedJvmBookLoadCacheSchemaVersion = 1
private const val SharedJvmBookLoadCacheProcessingVersion = 8

enum class SharedJvmBookLoadSemanticMode {
    FULL,
    SKIP
}

data class SharedJvmBookLoadCacheKey(
    val canonicalPath: String,
    val type: FileType,
    val length: Long,
    val lastModified: Long,
    val semanticMode: SharedJvmBookLoadSemanticMode = SharedJvmBookLoadSemanticMode.FULL,
    val htmlChapterRange: String? = null
) {
    val cacheId: String = sha256Hex(
        "$SharedJvmBookLoadCacheProcessingVersion|${semanticMode.name}|${htmlChapterRange.orEmpty()}|$canonicalPath|${type.name}|$length|$lastModified"
    ).take(32)
}

class SharedJvmBookLoadCache(
    private val cacheRoot: File = defaultCacheRoot()
) {
    private val proto = ProtoBuf {
        serializersModule = semanticBlockModule
        encodeDefaults = true
    }

    fun load(key: SharedJvmBookLoadCacheKey): SharedEpubBook? {
        val file = cacheFile(key)
        if (!file.isFile) return null
        return runCatching {
            val record = proto.decodeFromByteArray<CachedSharedEpubBook>(file.readBytes())
            if (!record.matches(key)) return@runCatching null
            record.toBook().takeUnless {
                key.requiresCachedSemanticBlocks() && it.hasHtmlContentWithoutSemanticBlocks()
            }
        }.getOrNull()
    }

    fun save(key: SharedJvmBookLoadCacheKey, book: SharedEpubBook) {
        if (key.requiresCachedSemanticBlocks() && book.hasHtmlContentWithoutSemanticBlocks()) return
        val record = CachedSharedEpubBook.from(key, book)
        runCatching {
            writeBookLoadCacheAtomically(cacheFile(key), proto.encodeToByteArray(record))
            cleanupOldEntries()
        }
    }

    fun clear() {
        cacheRoot.deleteRecursively()
        cacheRoot.mkdirs()
    }

    private fun cacheFile(key: SharedJvmBookLoadCacheKey): File {
        return File(cacheRoot, "${key.cacheId}.book.pb")
    }

    private fun cleanupOldEntries() {
        val files = cacheRoot.listFiles { file -> file.isFile && file.name.endsWith(".book.pb") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(80).forEach { it.delete() }
    }

    private fun CachedSharedEpubBook.matches(key: SharedJvmBookLoadCacheKey): Boolean {
        return schemaVersion == SharedJvmBookLoadCacheSchemaVersion &&
            processingVersion == SharedJvmBookLoadCacheProcessingVersion &&
            canonicalPath == key.canonicalPath &&
            type == key.type.name &&
            length == key.length &&
            lastModified == key.lastModified &&
            semanticMode == key.semanticMode.name &&
            htmlChapterRange == key.htmlChapterRange
    }

    companion object {
        fun defaultCacheRoot(): File {
            val overridePath = System.getProperty("reader.book.load.cache.dir")
            if (!overridePath.isNullOrBlank()) return File(overridePath).apply { mkdirs() }
            return File(sharedJvmEpistemeCacheRoot(), "book_load_cache").apply { mkdirs() }
        }
    }
}

@Serializable
private data class CachedSharedEpubBook(
    @ProtoNumber(1) val schemaVersion: Int,
    @ProtoNumber(2) val processingVersion: Int,
    @ProtoNumber(3) val canonicalPath: String,
    @ProtoNumber(4) val type: String,
    @ProtoNumber(5) val length: Long,
    @ProtoNumber(6) val lastModified: Long,
    @ProtoNumber(7) val id: String,
    @ProtoNumber(8) val fileName: String,
    @ProtoNumber(9) val title: String,
    @ProtoNumber(10) val author: String?,
    @ProtoNumber(11) val css: Map<String, String>,
    @ProtoNumber(12) val chapters: List<CachedSharedEpubChapter>,
    @ProtoNumber(13) val tableOfContents: List<CachedSharedEpubTocEntry> = emptyList(),
    @ProtoNumber(14) val semanticMode: String = SharedJvmBookLoadSemanticMode.FULL.name,
    @ProtoNumber(15) val htmlChapterRange: String? = null
) {
    fun toBook(): SharedEpubBook {
        return SharedEpubBook(
            id = id,
            fileName = fileName,
            title = title,
            author = author,
            css = css,
            chapters = chapters.map { it.toChapter() },
            tableOfContents = tableOfContents.map { it.toEntry() }
        )
    }

    companion object {
        fun from(key: SharedJvmBookLoadCacheKey, book: SharedEpubBook): CachedSharedEpubBook {
            return CachedSharedEpubBook(
                schemaVersion = SharedJvmBookLoadCacheSchemaVersion,
                processingVersion = SharedJvmBookLoadCacheProcessingVersion,
                canonicalPath = key.canonicalPath,
                type = key.type.name,
                length = key.length,
                lastModified = key.lastModified,
                id = book.id,
                fileName = book.fileName,
                title = book.title,
                author = book.author,
                css = book.css,
                chapters = book.chapters.map(CachedSharedEpubChapter::from),
                tableOfContents = book.tableOfContents.map(CachedSharedEpubTocEntry::from),
                semanticMode = key.semanticMode.name,
                htmlChapterRange = key.htmlChapterRange
            )
        }
    }
}

@Serializable
private data class CachedSharedEpubTocEntry(
    @ProtoNumber(1) val label: String,
    @ProtoNumber(2) val href: String,
    @ProtoNumber(3) val fragmentId: String?,
    @ProtoNumber(4) val depth: Int
) {
    fun toEntry(): SharedEpubTocEntry {
        return SharedEpubTocEntry(
            label = label,
            href = href,
            fragmentId = fragmentId,
            depth = depth
        )
    }

    companion object {
        fun from(entry: SharedEpubTocEntry): CachedSharedEpubTocEntry {
            return CachedSharedEpubTocEntry(
                label = entry.label,
                href = entry.href,
                fragmentId = entry.fragmentId,
                depth = entry.depth
            )
        }
    }
}

@Serializable
private data class CachedSharedEpubChapter(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val plainText: String,
    @ProtoNumber(4) val semanticBlocks: List<SemanticBlock>,
    @ProtoNumber(5) val htmlContent: String,
    @ProtoNumber(6) val baseHref: String?
) {
    fun toChapter(): SharedEpubChapter {
        return SharedEpubChapter(
            id = id,
            title = title,
            plainText = plainText,
            semanticBlocks = semanticBlocks,
            htmlContent = htmlContent,
            baseHref = baseHref
        )
    }

    companion object {
        fun from(chapter: SharedEpubChapter): CachedSharedEpubChapter {
            return CachedSharedEpubChapter(
                id = chapter.id,
                title = chapter.title,
                plainText = chapter.plainText,
                semanticBlocks = chapter.semanticBlocks,
                htmlContent = chapter.htmlContent,
                baseHref = chapter.baseHref
            )
        }
    }
}

private fun SharedJvmBookLoadCacheKey.requiresCachedSemanticBlocks(): Boolean {
    if (semanticMode == SharedJvmBookLoadSemanticMode.SKIP) return false
    return type.requiresCachedSemanticBlocks()
}

private fun FileType.requiresCachedSemanticBlocks(): Boolean {
    return when (this) {
        FileType.EPUB,
        FileType.MOBI,
        FileType.HTML,
        FileType.FB2,
        FileType.DOCX,
        FileType.ODT,
        FileType.FODT -> true
        else -> false
    }
}

private fun SharedEpubBook.hasHtmlContentWithoutSemanticBlocks(): Boolean {
    return chapters.any { it.htmlContent.isNotBlank() } &&
        chapters.none { it.semanticBlocks.isNotEmpty() }
}

private fun writeBookLoadCacheAtomically(file: File, bytes: ByteArray) {
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
