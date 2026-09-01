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

internal const val SharedBookLoadCacheSchemaVersion = 1
internal const val SharedBookLoadCacheProcessingVersion = 12

enum class SharedBookLoadSemanticMode {
    FULL,
    SKIP
}

data class SharedBookLoadCacheKey(
    val canonicalPath: String,
    val type: FileType,
    val length: Long,
    val lastModified: Long,
    val semanticMode: SharedBookLoadSemanticMode = SharedBookLoadSemanticMode.FULL,
    val htmlChapterRange: String? = null
) {
    val cacheId: String = sharedSha256Hex(
        "$SharedBookLoadCacheProcessingVersion|${semanticMode.name}|${htmlChapterRange.orEmpty()}|$canonicalPath|${type.name}|$length|$lastModified"
    ).take(32)
}

interface SharedBookLoadCacheStorage {
    fun read(cacheId: String): ByteArray?
    fun write(cacheId: String, bytes: ByteArray): Boolean
    fun cleanupOldEntries()
    fun clear()
}

expect fun defaultSharedBookLoadCacheStorage(): SharedBookLoadCacheStorage

class SharedBookLoadCache(
    private val storage: SharedBookLoadCacheStorage = defaultSharedBookLoadCacheStorage()
) {
    private val proto = ProtoBuf {
        serializersModule = semanticBlockModule
        // ProtoBuf cannot encode explicit nulls for optional fields. Omitting default-null
        // Compose style properties preserves their defaults and keeps semantic blocks cacheable.
        encodeDefaults = false
    }

    fun load(key: SharedBookLoadCacheKey): SharedEpubBook? {
        val bytes = storage.read(key.cacheId) ?: return null
        return runCatching {
            val record = proto.decodeFromByteArray<CachedSharedEpubBook>(bytes)
            if (!record.matches(key)) return@runCatching null
            record.toBook().takeUnless {
                key.requiresCachedSemanticBlocks() && it.hasHtmlContentWithoutSemanticBlocks()
            }
        }.getOrNull()
    }

    fun save(key: SharedBookLoadCacheKey, book: SharedEpubBook) {
        if (key.requiresCachedSemanticBlocks() && book.hasHtmlContentWithoutSemanticBlocks()) return
        val record = CachedSharedEpubBook.from(key, book)
        runCatching {
            if (storage.write(key.cacheId, proto.encodeToByteArray(record))) {
                storage.cleanupOldEntries()
            }
        }
    }

    fun clear() {
        storage.clear()
    }
}

@Serializable
internal data class CachedSharedEpubBook(
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
    @ProtoNumber(14) val semanticMode: String = SharedBookLoadSemanticMode.FULL.name,
    @ProtoNumber(15) val htmlChapterRange: String? = null,
    @ProtoNumber(16) val pageList: List<MobileEpubPageTarget> = emptyList(),
    @ProtoNumber(17) val language: String = "en",
    @ProtoNumber(18) val seriesName: String? = null,
    @ProtoNumber(19) val seriesIndex: Double? = null,
    @ProtoNumber(20) val description: String? = null,
    @ProtoNumber(21) val images: List<MobileEpubImage> = emptyList(),
    @ProtoNumber(22) val coverImagePath: String? = null
) {
    fun toBook(): SharedEpubBook {
        return SharedEpubBook(
            id = id,
            fileName = fileName,
            title = title,
            author = author,
            css = css,
            chapters = chapters.map { it.toChapter() },
            tableOfContents = tableOfContents.map { it.toEntry() },
            pageList = pageList,
            language = language,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            description = description,
            images = images,
            coverImagePath = coverImagePath
        )
    }

    fun matches(key: SharedBookLoadCacheKey): Boolean {
        return schemaVersion == SharedBookLoadCacheSchemaVersion &&
            processingVersion == SharedBookLoadCacheProcessingVersion &&
            canonicalPath == key.canonicalPath &&
            type == key.type.name &&
            length == key.length &&
            lastModified == key.lastModified &&
            semanticMode == key.semanticMode.name &&
            htmlChapterRange == key.htmlChapterRange
    }

    companion object {
        fun from(key: SharedBookLoadCacheKey, book: SharedEpubBook): CachedSharedEpubBook {
            return CachedSharedEpubBook(
                schemaVersion = SharedBookLoadCacheSchemaVersion,
                processingVersion = SharedBookLoadCacheProcessingVersion,
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
                htmlChapterRange = key.htmlChapterRange,
                pageList = book.pageList,
                language = book.language,
                seriesName = book.seriesName,
                seriesIndex = book.seriesIndex,
                description = book.description,
                images = book.images,
                coverImagePath = book.coverImagePath
            )
        }
    }
}

@Serializable
internal data class CachedSharedEpubTocEntry(
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
internal data class CachedSharedEpubChapter(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val plainText: String,
    @ProtoNumber(4) val semanticBlocks: List<SemanticBlock>,
    @ProtoNumber(5) val htmlContent: String,
    @ProtoNumber(6) val baseHref: String?,
    @ProtoNumber(7) val fragmentId: String? = null,
    @ProtoNumber(8) val depth: Int = 0,
    @ProtoNumber(9) val isInToc: Boolean = true
) {
    fun toChapter(): SharedEpubChapter {
        return SharedEpubChapter(
            id = id,
            title = title,
            plainText = plainText,
            semanticBlocks = semanticBlocks,
            htmlContent = htmlContent,
            baseHref = baseHref,
            fragmentId = fragmentId,
            depth = depth,
            isInToc = isInToc
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
                baseHref = chapter.baseHref,
                fragmentId = chapter.fragmentId,
                depth = chapter.depth,
                isInToc = chapter.isInToc
            )
        }
    }
}

private fun SharedBookLoadCacheKey.requiresCachedSemanticBlocks(): Boolean {
    if (semanticMode == SharedBookLoadSemanticMode.SKIP) return false
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
