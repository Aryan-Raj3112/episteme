@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.docparse.SharedDocxDocumentParser
import com.aryan.reader.shared.docparse.SharedFb2DocumentParser
import com.aryan.reader.shared.docparse.SharedMarkdownConverter
import com.aryan.reader.shared.docparse.SharedOdtDocumentParser
import com.aryan.reader.shared.sharedDocumentMetadataArchivePath
import com.aryan.reader.shared.mobi.MOBI_ENCRYPTION_NONE
import com.aryan.reader.shared.mobi.MOBI_SUCCESS
import com.aryan.reader.shared.mobi.MOBIFiletype
import com.aryan.reader.shared.mobi.mobi_free
import com.aryan.reader.shared.mobi.mobi_free_rawml
import com.aryan.reader.shared.mobi.mobi_decode_exthstring
import com.aryan.reader.shared.mobi.mobi_decode_exthvalue
import com.aryan.reader.shared.mobi.mobi_init
import com.aryan.reader.shared.mobi.mobi_init_rawml
import com.aryan.reader.shared.mobi.mobi_load_file
import com.aryan.reader.shared.mobi.mobi_parse_rawml_opt
import com.aryan.reader.shared.mobi.mobi_meta_get_author
import com.aryan.reader.shared.mobi.mobi_meta_get_title
import com.aryan.reader.shared.mobi.reader_mobi_flow_type
import com.aryan.reader.shared.mobi.reader_mobi_cover_size
import com.aryan.reader.shared.mobi.reader_mobi_copy_cover
import com.aryan.reader.shared.mobi.reader_mobi_toc_count
import com.aryan.reader.shared.mobi.reader_mobi_toc_entry
import com.aryan.reader.shared.libarchive.ARCHIVE_EOF
import com.aryan.reader.shared.libarchive.ARCHIVE_OK
import com.aryan.reader.shared.libarchive.archive_entry_pathname_utf8
import com.aryan.reader.shared.libarchive.archive_entry_size
import com.aryan.reader.shared.libarchive.archive_error_string
import com.aryan.reader.shared.libarchive.archive_read_data
import com.aryan.reader.shared.libarchive.archive_read_free
import com.aryan.reader.shared.libarchive.archive_read_new
import com.aryan.reader.shared.libarchive.archive_read_next_header
import com.aryan.reader.shared.libarchive.archive_read_open_filename
import com.aryan.reader.shared.libarchive.archive_read_support_filter_all
import com.aryan.reader.shared.libarchive.archive_read_support_format_7zip
import com.aryan.reader.shared.libarchive.archive_read_support_format_rar
import com.aryan.reader.shared.libarchive.archive_read_support_format_rar5
import com.aryan.reader.shared.libarchive.archive_entry_free
import com.aryan.reader.shared.libarchive.archive_entry_new
import com.aryan.reader.shared.libarchive.archive_entry_set_filetype
import com.aryan.reader.shared.libarchive.archive_entry_set_pathname
import com.aryan.reader.shared.libarchive.archive_entry_set_perm
import com.aryan.reader.shared.libarchive.archive_entry_set_size
import com.aryan.reader.shared.libarchive.archive_write_close
import com.aryan.reader.shared.libarchive.archive_write_data
import com.aryan.reader.shared.libarchive.archive_write_free
import com.aryan.reader.shared.libarchive.archive_write_header
import com.aryan.reader.shared.libarchive.archive_write_new
import com.aryan.reader.shared.libarchive.archive_write_open_filename
import com.aryan.reader.shared.libarchive.archive_write_set_format_zip
import com.aryan.reader.shared.libarchive.archive_write_set_options
import cnames.structs.archive_entry
import com.aryan.reader.shared.reader.SharedBookLoadCache
import com.aryan.reader.shared.reader.SharedBookLoadCacheKey
import com.aryan.reader.shared.reader.SharedEpubArchive
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.SharedEpubPackageLoader
import com.aryan.reader.shared.reader.SharedEpubTocEntry
import com.aryan.reader.shared.reader.SharedLruMemoryCache
import com.aryan.reader.shared.reader.SharedMobiTocPoint
import com.aryan.reader.shared.reader.rewriteMobiResourceReferences
import com.aryan.reader.shared.reader.splitMobiHtml
import com.aryan.reader.shared.reader.readComicTarEntries
import com.aryan.reader.shared.opds.SharedOpdsStreamUri
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.UIntVar
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToLong
import com.aryan.reader.shared.libarchive.AE_IFREG
import platform.posix.memcpy
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.free
import platform.zlib.MAX_WBITS
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

internal const val IOS_MOBI_LOG_TAG = "ReaderMobiIOS"

internal inline fun iosMobiLog(message: () -> String) {
    val value = message()
    IosDiagnosticLogStore.record(IOS_MOBI_LOG_TAG, value)
    println("[$IOS_MOBI_LOG_TAG] $value")
}

internal const val IOS_EPUB_LOAD_LOG_TAG = "ReaderIosEpub"

internal inline fun iosEpubLoadLog(message: () -> String) {
    val value = message()
    IosDiagnosticLogStore.record(IOS_EPUB_LOAD_LOG_TAG, value)
    println("[$IOS_EPUB_LOAD_LOG_TAG] $value")
}

internal data class IosBookPresentation(
    val title: String? = null,
    val author: String? = null,
    val coverBytes: ByteArray? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
)

internal data class IosEpubMetadataWriteResult(
    val title: String?,
    val author: String?,
    val description: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
    val coverBytes: ByteArray?,
)

internal fun rewriteIosEpubMetadata(
    sourcePath: String,
    destinationPath: String,
    title: String?,
    author: String?,
    description: String?,
    seriesName: String?,
    seriesIndex: Double?,
    coverPath: String?,
    restoreCoverFromPath: String? = null,
): IosEpubMetadataWriteResult {
    val archive = IosZipEpubArchive(sourcePath)
    val opfPath = archive.findIosOpfPath()
        ?: error("EPUB package document was not found.")
    val originalOpf = archive.readText(opfPath)
        ?: error("EPUB package document entry is missing.")
    val restoredCover = restoreCoverFromPath?.takeIf(String::isNotBlank)?.readIosEpubCover()
    val coverBytes = coverPath?.takeIf(String::isNotBlank)?.readIosFileBytes()
        ?: restoredCover?.first
    val coverExtension = coverPath?.substringAfterLast('.', "")?.lowercase()
        ?: restoredCover?.second
    if (coverBytes != null) {
        require(coverBytes.isNotEmpty()) { "EPUB cover image is empty." }
        require(coverExtension in IosSupportedCoverExtensions) {
            "Choose a JPG, PNG, GIF, WebP, or BMP cover image."
        }
    }
    val existingCoverPath = originalOpf.findIosEpubCoverPath(opfPath)
    val outputCoverPath = if (coverBytes != null) {
        existingCoverPath?.takeIf {
            it.substringAfterLast('.', "").lowercase() in IosSupportedCoverExtensions
        } ?: opfPath.substringBeforeLast('/', "")
            .let { base -> listOf(base, "Images", "cover.$coverExtension").filter(String::isNotBlank).joinToString("/") }
    } else {
        null
    }
    val opfParent = opfPath.substringBeforeLast('/', "")
    val coverHref = outputCoverPath?.let {
        if (opfParent.isBlank()) it else it.removePrefix("$opfParent/")
    }
    val rewrittenOpf = originalOpf.rewriteIosOpfMetadata(
        title = title,
        author = author,
        description = description,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        coverHref = coverHref,
    )
    val replacements = buildMap {
        put(opfPath, rewrittenOpf.encodeToByteArray())
        if (outputCoverPath != null && coverBytes != null) put(outputCoverPath, coverBytes)
    }
    val removedPaths = outputCoverPath?.let(::setOf).orEmpty()
    writeIosZipArchive(
        destinationPath = destinationPath,
        orderedEntries = buildList {
            if ("mimetype" in archive.entryPaths) add("mimetype")
            addAll(archive.entryPaths.filterNot { it == "mimetype" || it in removedPaths })
            addAll(replacements.keys)
        }.distinct(),
        bytesForPath = { path -> replacements[path] ?: archive.readBytes(path) },
    )
    val rewritten = IosZipEpubArchive(destinationPath)
    val verifiedOpf = rewritten.readText(opfPath)
        ?: error("Rewritten EPUB failed metadata validation.")
    val verifiedCoverPath = verifiedOpf.findIosEpubCoverPath(opfPath)
    return IosEpubMetadataWriteResult(
        title = verifiedOpf.iosXmlElementText("title"),
        author = verifiedOpf.iosXmlElementText("creator"),
        description = verifiedOpf.iosXmlElementText("description"),
        seriesName = verifiedOpf.iosMetaContent("calibre:series"),
        seriesIndex = verifiedOpf.iosMetaContent("calibre:series_index")?.toDoubleOrNull(),
        coverBytes = verifiedCoverPath?.let(rewritten::readBytes),
    )
}

private fun String.readIosEpubCover(): Pair<ByteArray, String>? {
    val archive = IosZipEpubArchive(this)
    val opfPath = archive.findIosOpfPath() ?: return null
    val opf = archive.readText(opfPath) ?: return null
    val coverPath = opf.findIosEpubCoverPath(opfPath) ?: return null
    val extension = coverPath.substringAfterLast('.', "").lowercase()
        .takeIf { it in IosSupportedCoverExtensions } ?: return null
    return (archive.readBytes(coverPath) ?: return null) to extension
}

internal fun extractIosBookPresentation(book: BookItem): IosBookPresentation = runCatching {
    when (book.type) {
        FileType.EPUB -> extractIosEpubPresentation(book)
        FileType.MOBI -> extractIosMobiPresentation(book)
        FileType.PDF -> extractIosPdfPresentation(book)
        FileType.DOCX,
        FileType.PPTX,
        FileType.ODT -> extractIosZipDocumentPresentation(
            book,
            requireNotNull(sharedDocumentMetadataArchivePath(book.type)),
        )
        FileType.FODT -> extractIosFlatDocumentPresentation(book)
        FileType.FB2 -> extractIosFb2Presentation(book)
        FileType.CBZ -> {
            val path = book.path.resolveIosEpubSourcePath() ?: return@runCatching IosBookPresentation()
            val archive = IosZipEpubArchive(path)
            val firstImage = archive.entryPaths
                .filter { it.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp") }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .firstOrNull()
            IosBookPresentation(coverBytes = firstImage?.let(archive::readBytes))
        }
        else -> IosBookPresentation()
    }
}.getOrElse { throwable ->
    println("[ReaderLibraryIOS] Presentation extraction failed id=${book.id} type=${book.type} message=${throwable.message}")
    IosBookPresentation()
}

private fun extractIosEpubPresentation(book: BookItem): IosBookPresentation {
    val path = book.path.resolveIosEpubSourcePath() ?: return IosBookPresentation()
    val archive = IosZipEpubArchive(path)
    val container = archive.readText("META-INF/container.xml").orEmpty()
    val opfPath = Regex("""<rootfile\b[^>]*\bfull-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(container)?.groupValues?.getOrNull(1)
        ?: archive.entryPaths.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
        ?: return IosBookPresentation()
    val opf = archive.readText(opfPath).orEmpty()
    val title = opf.iosXmlElementText("title")
    val author = opf.iosXmlElementText("creator")
    val coverId = Regex(
        """<meta\b[^>]*\bname\s*=\s*["']cover["'][^>]*\bcontent\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    ).find(opf)?.groupValues?.getOrNull(1)
    val itemTags = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf).map { it.value }.toList()
    val coverItem = itemTags.firstOrNull { tag ->
        Regex("""\bproperties\s*=\s*["'][^"']*\bcover-image\b[^"']*["']""", RegexOption.IGNORE_CASE).containsMatchIn(tag)
    } ?: coverId?.let { id ->
        itemTags.firstOrNull { tag ->
            tag.iosXmlAttribute("id")?.equals(id, ignoreCase = true) == true
        }
    }
    val coverPath = coverItem?.iosXmlAttribute("href")?.let { href ->
        val parent = opfPath.substringBeforeLast('/', "")
        (if (parent.isBlank()) href else "$parent/$href").normalizeIosZipPathSegments()
    }
    return IosBookPresentation(
        title = title,
        author = author,
        coverBytes = coverPath?.let(archive::readBytes),
        seriesName = opf.iosMetaContent("calibre:series"),
        seriesIndex = opf.iosMetaContent("calibre:series_index")?.toDoubleOrNull(),
    )
}

private fun extractIosMobiPresentation(book: BookItem): IosBookPresentation {
    val path = book.path.resolveIosEpubSourcePath() ?: return IosBookPresentation()
    val file = fopen(path, "rb") ?: return IosBookPresentation()
    val mobi = mobi_init() ?: run {
        fclose(file)
        return IosBookPresentation()
    }
    try {
        if (mobi_load_file(mobi, file) != MOBI_SUCCESS) return IosBookPresentation()
        fun ownedText(value: kotlinx.cinterop.CPointer<ByteVar>?): String? {
            if (value == null) return null
            return try {
                value.toKString().trim().takeIf(String::isNotBlank)
            } finally {
                free(value)
            }
        }
        val coverSize = reader_mobi_cover_size(mobi).toInt()
        val cover = if (coverSize > 0) {
            ByteArray(coverSize).also { bytes ->
                val copied = bytes.usePinned { pinned ->
                    reader_mobi_copy_cover(mobi, pinned.addressOf(0).reinterpret(), coverSize.convert())
                }
                if (copied.toInt() != coverSize) return@also
            }
        } else {
            null
        }
        // Calibre Kindle formats carry series in EXTH records 508/509 (same
        // byte-level contract Android's EmbeddedEbookMetadataExtractor reads).
        val exthRecords = generateSequence(mobi.pointed.eh) { it.pointed.next }.map { it.pointed }
        fun exthString(tag: UInt): String? = exthRecords
            .firstOrNull { it.tag == tag }
            ?.takeIf { it.size > 0u && it.data != null }
            ?.let { header -> ownedText(mobi_decode_exthstring(mobi, header.data!!.reinterpret(), header.size.convert())) }
        fun exthValue(tag: UInt): Double? = exthRecords
            .firstOrNull { it.tag == tag }
            ?.takeIf { it.size >= 4u && it.data != null }
            ?.let { header -> mobi_decode_exthvalue(header.data!!.reinterpret(), header.size.convert()).toDouble() }
        return IosBookPresentation(
            title = ownedText(mobi_meta_get_title(mobi)),
            author = ownedText(mobi_meta_get_author(mobi)),
            coverBytes = cover,
            seriesName = exthString(508u),
            seriesIndex = exthValue(509u),
        )
    } finally {
        fclose(file)
        mobi_free(mobi)
    }
}

private fun String.iosXmlElementText(localName: String): String? =
    Regex(
        """<(?:[\w.-]+:)?$localName\b[^>]*>([\s\S]*?)</(?:[\w.-]+:)?$localName\s*>""",
        RegexOption.IGNORE_CASE,
    ).find(this)?.groupValues?.getOrNull(1)
        ?.replace(Regex("""<[^>]+>"""), " ")
        ?.decodeIosXmlEntities()
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun String.iosXmlAttribute(name: String): String? =
    Regex("""\b$name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(1)

private fun String.normalizeIosZipPathSegments(): String {
    val output = mutableListOf<String>()
    replace('\\', '/').split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (output.isNotEmpty()) output.removeAt(output.lastIndex)
            else -> output += segment
        }
    }
    return output.joinToString("/")
}

internal fun loadIosEpubBook(book: BookItem): SharedEpubBook {
    val startedAt = currentTimestamp()
    iosEpubLoadLog { "Load started id=${book.id} type=${book.type} name=${book.displayName} path=${book.path ?: "<null>"}" }
    // PPTX chapters embed slide images as HTML data URIs. Keeping those chapters in the
    // cross-book memory/disk cache duplicates the already-large base64 payload and can retain
    // an entire image-heavy deck after the reader closes it. Reparse PPTX on demand; the
    // streaming loader keeps only one slide's decoded bytes alive while doing so.
    val cacheKey = book.iosBookLoadCacheKey().takeUnless { book.type == FileType.PPTX }
    if (cacheKey != null) {
        iosBookLoadMemoryGet(cacheKey.cacheId)?.let { cached ->
            iosEpubLoadLog { "Load memory cache hit cacheId=${cacheKey.cacheId} elapsed=${currentTimestamp() - startedAt}ms" }
            return cached
        }
        iosBookLoadDiskCache.load(cacheKey)?.let { cached ->
            iosBookLoadMemoryPut(cacheKey.cacheId, cached)
            iosEpubLoadLog { "Load disk cache hit cacheId=${cacheKey.cacheId} elapsed=${currentTimestamp() - startedAt}ms" }
            return cached
        }
    }
    if (book.type == FileType.CBZ) {
        return loadIosCbzBook(book).also { book.iosBookLoadCacheSave(cacheKey, it) }
    }
    if (book.type == FileType.CBT) {
        return loadIosCbtBook(book).also { book.iosBookLoadCacheSave(cacheKey, it) }
    }
    if (book.type == FileType.CBR || book.type == FileType.CB7) {
        return loadIosLibarchiveComicBook(book).also { book.iosBookLoadCacheSave(cacheKey, it) }
    }
    if (book.type == FileType.MOBI) {
        iosMobiLog { "Routing book to MOBI loader id=${book.id} file=${book.displayName}" }
        return loadIosMobiBook(book).also { book.iosBookLoadCacheSave(cacheKey, it) }
    }
    if (book.type in IOS_ZIP_DOCUMENT_READER_TYPES) {
        return loadIosZipDocumentBook(book).also { book.iosBookLoadCacheSave(cacheKey, it) }
    }
    if (book.type in IOS_SINGLE_DOCUMENT_READER_TYPES) {
        return loadIosSingleDocumentBook(book).also { book.iosBookLoadCacheSave(cacheKey, it) }
    }
    val path = book.path.resolveIosEpubSourcePath()
        ?: error("EPUB path is unavailable")
    val archive = IosZipEpubArchive(path)
    iosEpubLoadLog { "Archive opened path=$path entries=${archive.entryPaths.size} elapsed=${currentTimestamp() - startedAt}ms" }
    val loaded = SharedEpubPackageLoader.load(
        archive = archive,
        sourceId = book.id,
        fileName = book.displayName.ifBlank { path.substringAfterLast('/') }
    )
    iosEpubLoadLog { "Load finished chapters=${loaded.chapters.size} elapsed=${currentTimestamp() - startedAt}ms" }
    book.iosBookLoadCacheSave(cacheKey, loaded)
    return loaded
}

private val iosBookLoadMemoryCache = SharedLruMemoryCache<String, SharedEpubBook>(maxEntries = 6)
private val iosBookLoadDiskCache = SharedBookLoadCache()
private val iosBookLoadLock = NSLock()

private fun BookItem.iosBookLoadCacheKey(): SharedBookLoadCacheKey? {
    val path = path.resolveIosEpubSourcePath() ?: return null
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return null
    val length = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
    if (length <= 0L) return null
    val lastModified = ((attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0)
        .let { (it * 1000.0).roundToLong() }
    return SharedBookLoadCacheKey(
        canonicalPath = path,
        type = type,
        length = length,
        lastModified = lastModified
    )
}

private fun BookItem.iosBookLoadCacheSave(key: SharedBookLoadCacheKey?, book: SharedEpubBook) {
    if (key == null) return
    iosBookLoadDiskCache.save(key, book)
    iosBookLoadMemoryPut(key.cacheId, book)
}

private fun iosBookLoadMemoryGet(cacheId: String): SharedEpubBook? {
    iosBookLoadLock.lock()
    try {
        return iosBookLoadMemoryCache[cacheId]
    } finally {
        iosBookLoadLock.unlock()
    }
}

private fun iosBookLoadMemoryPut(cacheId: String, book: SharedEpubBook) {
    iosBookLoadLock.lock()
    try {
        iosBookLoadMemoryCache[cacheId] = book
    } finally {
        iosBookLoadLock.unlock()
    }
}

private val IOS_ZIP_DOCUMENT_READER_TYPES = setOf(
    FileType.DOCX,
    FileType.ODT,
    FileType.PPTX,
)

private val IOS_SINGLE_DOCUMENT_READER_TYPES = setOf(
    FileType.TXT,
    FileType.MD,
    FileType.HTML,
    FileType.FB2,
    FileType.FODT,
)

private fun loadIosSingleDocumentBook(book: BookItem): SharedEpubBook {
    val path = book.path.resolveIosEpubSourcePath()
        ?: error("${book.type.name} path is unavailable")
    val source = path.readIosFileBytes().decodeEpubText()
    val title = book.title?.takeIf { it.isNotBlank() }
        ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
    val chapters: List<SharedEpubChapter> = when (book.type) {
        FileType.HTML -> listOf(singleIosDocumentChapter(book, path, title, source))
        FileType.FB2 -> loadIosFb2Chapters(book, source, fallbackTitle = title)
        FileType.FODT -> loadIosFodtChapters(book, source, fallbackTitle = title)
        FileType.MD -> loadIosMarkdownChapters(book, source, fallbackTitle = title)
        else -> listOf(
            singleIosDocumentChapter(
                book,
                path,
                title,
                "<pre>${source.escapeIosReaderHtml()}</pre>",
            )
        )
    }
    return SharedEpubBook(
        id = book.id,
        fileName = book.displayName.ifBlank { path.substringAfterLast('/') },
        title = title,
        author = book.author,
        chapters = chapters,
    )
}

private fun singleIosDocumentChapter(
    book: BookItem,
    path: String,
    title: String,
    htmlContent: String,
): SharedEpubChapter {
    val plainText = htmlContent.iosReaderPlainText()
    return SharedEpubChapter(
        id = "${book.id}-document",
        title = title,
        plainText = plainText,
        htmlContent = htmlContent,
        baseHref = NSURL.fileURLWithPath(path).absoluteString,
    )
}

/**
 * Structured FB2 rendering matching Android's Fb2Parser output: one chapter per
 * section, section titles as chapter names, and binary images embedded as data
 * URIs (the platform equivalent of Android's extracted image files).
 */
private fun loadIosFb2Chapters(book: BookItem, source: String, fallbackTitle: String): List<SharedEpubChapter> {
    val parsed = SharedFb2DocumentParser.parse(source, fallbackTitle)
        ?: error("No valid content found in FB2 file.")
    val binarySrcById = parsed.binariesById.mapValues { (_, binary) ->
        val mime = binary.contentType?.substringBefore(';')?.trim()?.takeIf(String::isNotBlank)
            ?: "image/png"
        "data:$mime;base64,${binary.base64}"
    }
    val imageSrcRegex = Regex("""<img src="([^"]+)"[ ]*/>""")
    return parsed.chapters.mapIndexed { index, chapter ->
        val html = chapter.html.replace(imageSrcRegex) { match ->
            val src = match.groupValues[1]
            val resolved = binarySrcById[src] ?: src
            """<img src="${resolved.escapeIosReaderHtmlAttribute()}" />"""
        }
        SharedEpubChapter(
            id = "${book.id}-chapter-${index + 1}",
            title = chapter.title.ifBlank { fallbackTitle },
            plainText = html.iosReaderPlainText(),
            htmlContent = "<style>$IosFb2CssStyle</style>\n${html.trim()}",
        )
    }
}

/** Flat-ODT rendering; images arrive from the parser already as data URIs. */
private fun loadIosFodtChapters(book: BookItem, source: String, fallbackTitle: String): List<SharedEpubChapter> =
    loadIosOdtResultChapters(
        book = book,
        result = SharedOdtDocumentParser.parse(source, stylesXml = null, isFlat = true, fileNameHint = fallbackTitle),
        resolveImageSrc = null,
    )

private fun loadIosMarkdownChapters(book: BookItem, source: String, fallbackTitle: String): List<SharedEpubChapter> {
    val sections = SharedMarkdownConverter.convert(source)
    require(sections.isNotEmpty()) { "No valid content found in Markdown file." }
    return sections.mapIndexed { index, section ->
        val pageTitle = section.title ?: "Page ${index + 1}"
        SharedEpubChapter(
            id = "${book.id}-page-${index + 1}",
            title = pageTitle,
            plainText = section.html.iosReaderPlainText(),
            htmlContent = "<style>$IosMarkdownCssStyle</style>\n${section.html.trim()}",
        )
    }
}

private fun loadIosOdtResultChapters(
    book: BookItem,
    result: SharedOdtDocumentParser.Result?,
    resolveImageSrc: ((String) -> String?)?,
): List<SharedEpubChapter> {
    val parsed = result ?: error("No valid content found in ODT file.")
    val imageSrcRegex = Regex("""<img src="([^"]+)"[ ]*/>""")
    return parsed.chapters.mapIndexed { index, chapter ->
        val html = if (resolveImageSrc != null) {
            chapter.html.replace(imageSrcRegex) { match ->
                val src = match.groupValues[1]
                val resolved = resolveImageSrc(src) ?: src
                """<img src="${resolved.escapeIosReaderHtmlAttribute()}" />"""
            }
        } else {
            chapter.html
        }
        SharedEpubChapter(
            id = "${book.id}-part-${index + 1}",
            title = chapter.title,
            plainText = html.iosReaderPlainText(),
            htmlContent = "<style>$IosOdtCssStyle</style>\n${html.trim()}",
        )
    }
}

private fun String.iosReaderPlainText(): String {
    return replace(Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""&amp;"""), "&")
        .replace(Regex("""&lt;"""), "<")
        .replace(Regex("""&gt;"""), ">")
        .replace(Regex("""&quot;"""), "\"")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n\s*\n\s*\n+"""), "\n\n")
        .trim()
}

private const val IosFb2CssStyle = """
    body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
    p { margin-bottom: 1em; text-indent: 1.5em; text-align: justify; }
    h1, h2, h3, h4 { text-align: center; margin-top: 1.5em; margin-bottom: 1em; }
    .empty-line { height: 1.5em; }
    img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
    .epigraph { margin-left: 2em; font-style: italic; margin-bottom: 1.5em; }
    .cite { border-left: 4px solid currentColor; padding-left: 1em; margin-left: 0; opacity: 0.8; font-style: italic; }
    .poem { margin: 1.5em 0; padding-left: 2em; }
    .stanza { margin-bottom: 1em; }
"""

private const val IosOdtCssStyle = """
    body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
    p { margin-bottom: 1em; text-align: justify; }
    h1, h2, h3, h4, h5, h6 { text-align: center; margin-top: 1.5em; margin-bottom: 1em; }
    ul, ol { margin-bottom: 1em; padding-left: 2em; }
    img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 1em; }
    th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
    .footnote { font-size: 0.85em; background-color: #f9f9f9; padding: 0 4px; border: 1px solid #ddd; border-radius: 3px; }
"""

private const val IosMarkdownCssStyle = """
    body { font-family: sans-serif; line-height: 1.6; padding: 1em; max-width: 800px; margin: 0 auto; }
    table { border-collapse: collapse; width: 100%; margin: 1em 0; }
    th, td { border: 1px solid currentColor; padding: 0.5em; text-align: left; }
    blockquote { border-left: 4px solid currentColor; padding-left: 1em; margin-left: 0; opacity: 0.8; }
    pre { overflow-x: auto; background: rgba(127,127,127,0.1); padding: 1em; border-radius: 4px; }
    img { max-width: 100%; height: auto; }
    hr { border: 0; border-top: 1px solid #ccc; margin: 2em 0; }
"""

private fun loadIosCbzBook(book: BookItem): SharedEpubBook {
    SharedOpdsStreamUri.parse(book.path)?.let { stream ->
        val title = book.title?.takeIf { it.isNotBlank() }
            ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
        return SharedEpubBook(
            id = book.id,
            fileName = book.displayName,
            title = title,
            author = book.author,
            chapters = (0 until stream.count).map { pageIndex ->
                val imageUrl = stream.urlTemplate.replace("{pageNumber}", pageIndex.toString())
                SharedEpubChapter(
                    id = "${book.id}-page-${pageIndex + 1}",
                    title = "Page ${pageIndex + 1}",
                    plainText = "Page ${pageIndex + 1}",
                    htmlContent = """
                        <div style="margin:0;padding:0;text-align:center">
                          <img src="${imageUrl.escapeIosReaderHtmlAttribute()}"
                               alt="Page ${pageIndex + 1}"
                               style="display:block;width:100%;height:auto;margin:0 auto" />
                        </div>
                    """.trimIndent(),
                    baseHref = imageUrl,
                )
            },
        )
    }
    val path = book.path.resolveIosEpubSourcePath() ?: error("CBZ path is unavailable")
    val archive = IosZipEpubArchive(path)
    val imagePaths = archive.entryPaths
        .filter { it.isIosComicImagePath() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    require(imagePaths.isNotEmpty()) { "This CBZ archive contains no readable images" }
    return buildIosComicBook(book, imagePaths.mapNotNull { path ->
        archive.readBytes(path)?.let { path to it }
    })
}

private fun loadIosCbtBook(book: BookItem): SharedEpubBook {
    val path = book.path.resolveIosEpubSourcePath() ?: error("CBT path is unavailable")
    val entries = path.readIosFileBytes()
        .readComicTarEntries()
        .filter { it.first.isIosComicImagePath() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
    require(entries.isNotEmpty()) { "This CBT archive contains no readable images" }
    return buildIosComicBook(book, entries)
}

private fun loadIosLibarchiveComicBook(book: BookItem): SharedEpubBook {
    val path = book.path.resolveIosEpubSourcePath() ?: error("${book.type.name} path is unavailable")
    val archive = archive_read_new() ?: error("Could not initialize archive reader")
    try {
        archive_read_support_filter_all(archive)
        when (book.type) {
            FileType.CBR -> {
                archive_read_support_format_rar(archive)
                archive_read_support_format_rar5(archive)
            }
            FileType.CB7 -> archive_read_support_format_7zip(archive)
            else -> error("Unsupported comic archive type")
        }
        val openResult = archive_read_open_filename(archive, path, 64uL * 1024uL)
        require(openResult == ARCHIVE_OK) {
            archive_error_string(archive)?.toKString() ?: "Could not open ${book.type.name} archive"
        }
        val images = memScoped {
            val entry = alloc<CPointerVar<archive_entry>>()
            buildList {
                while (true) {
                    val result = archive_read_next_header(archive, entry.ptr)
                    if (result == ARCHIVE_EOF) break
                    require(result == ARCHIVE_OK) {
                        archive_error_string(archive)?.toKString() ?: "Could not read archive entry"
                    }
                    val current = entry.value ?: continue
                    val entryPath = archive_entry_pathname_utf8(current)?.toKString().orEmpty()
                    val entrySize = archive_entry_size(current)
                    if (!entryPath.isIosComicImagePath() || entrySize <= 0L) continue
                    require(entrySize <= IosMaximumComicImageBytes) {
                        "Comic image is too large: $entryPath"
                    }
                    val bytes = ByteArray(entrySize.toInt())
                    var offset = 0
                    bytes.usePinned { pinned ->
                        while (offset < bytes.size) {
                            val read = archive_read_data(
                                archive,
                                pinned.addressOf(offset),
                                (bytes.size - offset).convert()
                            )
                            require(read >= 0) {
                                archive_error_string(archive)?.toKString() ?: "Could not decompress $entryPath"
                            }
                            if (read == 0L) break
                            offset += read.toInt()
                        }
                    }
                    if (offset > 0) add(entryPath to bytes.copyOf(offset))
                }
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
        require(images.isNotEmpty()) {
            "This ${book.type.name} archive contains no readable images"
        }
        return buildIosComicBook(book, images)
    } finally {
        archive_read_free(archive)
    }
}

private fun loadIosMobiBook(book: BookItem): SharedEpubBook {
    try {
        iosMobiLog {
            "Load requested id=${book.id} file=${book.displayName} storedPathPresent=${!book.path.isNullOrBlank()}"
        }
        val path = book.path.resolveIosEpubSourcePath() ?: error("MOBI path is unavailable")
        iosMobiLog {
            "Resolved source file=${path.substringAfterLast('/')} exists=${NSFileManager.defaultManager.fileExistsAtPath(path)}"
        }
        val file = fopen(path, "rb") ?: error("Could not open MOBI file")
        val mobi = mobi_init() ?: run {
            fclose(file)
            error("Could not initialize MOBI parser")
        }
        try {
            iosMobiLog { "Native parser initialized; loading records" }
            val loadResult = mobi_load_file(mobi, file)
            fclose(file)
            iosMobiLog { "mobi_load_file result=$loadResult expected=$MOBI_SUCCESS" }
            require(loadResult == MOBI_SUCCESS) { "Could not parse MOBI file (error $loadResult)" }
            val encryptionType = mobi.pointed.rh?.pointed?.encryption_type?.toInt()
            iosMobiLog { "Record header encryptionType=$encryptionType" }
            require(encryptionType == MOBI_ENCRYPTION_NONE) {
                "DRM-protected MOBI files are not supported"
            }
            val rawml = mobi_init_rawml(mobi) ?: error("Could not initialize MOBI content parser")
            try {
                val parseResult = mobi_parse_rawml_opt(rawml, mobi, true, false, true)
                iosMobiLog { "mobi_parse_rawml_opt result=$parseResult expected=$MOBI_SUCCESS" }
                require(parseResult == MOBI_SUCCESS) { "Could not reconstruct MOBI content (error $parseResult)" }

                fun readOwnedMetadata(value: kotlinx.cinterop.CPointer<ByteVar>?): String? {
                    if (value == null) return null
                    return try {
                        value.toKString().trim().takeIf { it.isNotBlank() }
                    } finally {
                        free(value)
                    }
                }
                val parsedTitle = readOwnedMetadata(mobi_meta_get_title(mobi))
                val parsedAuthor = readOwnedMetadata(mobi_meta_get_author(mobi))

                val flowParts = buildList {
                    var part = rawml.pointed.flow
                    while (part != null) {
                        val data = part.pointed.data
                        val size = part.pointed.size.toInt()
                        if (data != null && size > 0) add(data.readBytes(size))
                        part = part.pointed.next
                    }
                }
                val flowBytes = flowParts.fold(ByteArray(0)) { combined, bytes -> combined + bytes }
                iosMobiLog {
                    "Reconstructed flows count=${flowParts.size} sizes=${flowParts.map { it.size }} totalBytes=${flowBytes.size}"
                }
                require(flowBytes.isNotEmpty()) { "This MOBI file contains no readable content" }

                var resourceCount = 0
                var resourceBytes = 0L
                val imageResources = buildList {
                    var part = rawml.pointed.resources
                    while (part != null) {
                        resourceCount += 1
                        val data = part.pointed.data
                        val size = part.pointed.size.toInt()
                        if (data != null && size > 0) {
                            resourceBytes += size
                            val bytes = data.readBytes(size)
                            bytes.iosImageMimeType()?.let { mime ->
                                add(Triple(part.pointed.uid.toLong(), mime, bytes))
                            }
                        }
                        part = part.pointed.next
                    }
                }.sortedBy { it.first }
                iosMobiLog {
                    "Resources count=$resourceCount totalBytes=$resourceBytes recognizedImages=${imageResources.size}"
                }
                val imageDataUris = imageResources.map { (_, mime, bytes) ->
                    "data:$mime;base64,${bytes.toIosBase64()}"
                }

                val cssDataUris = flowParts.mapIndexedNotNull { index, bytes ->
                    if (reader_mobi_flow_type(rawml, index.convert()) != MOBIFiletype.T_CSS) {
                        return@mapIndexedNotNull null
                    }
                    index to "data:text/css;base64,${bytes.toIosBase64()}"
                }.toMap()
                val toc = memScoped {
                    val count = reader_mobi_toc_count(rawml).toInt()
                    (0 until count).mapNotNull { index ->
                        val position = alloc<UIntVar>()
                        val titleBuffer = allocArray<ByteVar>(1024)
                        if (reader_mobi_toc_entry(
                                rawml,
                                index.convert(),
                                position.ptr,
                                titleBuffer,
                                1024.convert(),
                            ) == 0
                        ) {
                            null
                        } else {
                            SharedMobiTocPoint(titleBuffer.toKString(), position.value.toInt())
                        }
                    }
                }

                val rawHtml = flowBytes.decodeEpubText()
                val kindleEmbedCount = Regex("""kindle:embed:(\d+)""", RegexOption.IGNORE_CASE)
                    .findAll(rawHtml)
                    .count()
                val recindexCount = Regex("""\srecindex=["']?\d+""", RegexOption.IGNORE_CASE)
                    .findAll(rawHtml)
                    .count()
                iosMobiLog {
                    "Decoded HTML chars=${rawHtml.length} toc=${toc.size} cssFlows=${cssDataUris.size} kindleEmbeds=$kindleEmbedCount recindexes=$recindexCount hasHtmlTag=${rawHtml.contains("<html", ignoreCase = true)} hasBodyTag=${rawHtml.contains("<body", ignoreCase = true)}"
                }

                val title = parsedTitle
                    ?: book.title?.takeIf { it.isNotBlank() }
                    ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
                val sections = splitMobiHtml(rawHtml, toc, title).map { section ->
                    section.copy(
                        html = rewriteMobiResourceReferences(section.html, imageDataUris, cssDataUris),
                    )
                }
                val chapters = sections.mapIndexed { index, section ->
                    val chapterHref = "chapter_$index.html"
                    SharedEpubChapter(
                        id = "${book.id}-mobi-$index",
                        title = section.title,
                        plainText = section.html.iosHtmlToPlainText(),
                        htmlContent = section.html,
                        baseHref = chapterHref,
                    )
                }
                iosMobiLog {
                    "Reader payload ready titlePresent=${title.isNotBlank()} authorPresent=${!parsedAuthor.isNullOrBlank()} htmlChars=${rawHtml.length} chapters=${chapters.size}"
                }
                return SharedEpubBook(
                    id = book.id,
                    fileName = book.displayName,
                    title = title,
                    author = parsedAuthor ?: book.author,
                    chapters = chapters,
                    tableOfContents = sections.mapIndexed { index, section ->
                        SharedEpubTocEntry(
                            label = section.title,
                            href = "chapter_$index.html",
                        )
                    },
                )
            } finally {
                mobi_free_rawml(rawml)
                iosMobiLog { "Released reconstructed MOBI content" }
            }
        } finally {
            mobi_free(mobi)
            iosMobiLog { "Released native MOBI parser" }
        }
    } catch (throwable: Throwable) {
        iosMobiLog {
            "Load failed type=${throwable::class.simpleName} message=${throwable.message ?: "<none>"}"
        }
        throw throwable
    }
}

private fun ByteArray.iosImageMimeType(): String? = when {
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte() -> "image/jpeg"
    size >= 8 && copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
    size >= 6 && decodeToString(0, 6) in setOf("GIF87a", "GIF89a") -> "image/gif"
    size >= 12 && decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WEBP" -> "image/webp"
    size >= 2 && this[0] == 'B'.code.toByte() && this[1] == 'M'.code.toByte() -> "image/bmp"
    else -> null
}

private fun String.iosHtmlToPlainText(): String = this
    .replace(Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("""<[^>]+>"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun buildIosComicBook(
    book: BookItem,
    images: List<Pair<String, ByteArray>>,
): SharedEpubBook {
    val title = book.title?.takeIf { it.isNotBlank() }
        ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
    return SharedEpubBook(
        id = book.id,
        fileName = book.displayName,
        title = title,
        author = book.author,
        chapters = images.mapIndexed { index, (imagePath, bytes) ->
            val extension = imagePath.substringAfterLast('.', "").lowercase()
            val mime = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "application/octet-stream"
            }
            SharedEpubChapter(
                id = "${book.id}-page-${index + 1}",
                title = "Page ${index + 1}",
                plainText = "Page ${index + 1}",
                htmlContent = """
                    <div style="margin:0;padding:0;text-align:center">
                      <img src="data:$mime;base64,${bytes.toIosBase64()}"
                           alt="Page ${index + 1}"
                           style="display:block;width:100%;height:auto;margin:0 auto" />
                    </div>
                """.trimIndent(),
            )
        }
    )
}

private fun String.isIosComicImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun String.escapeIosReaderHtmlAttribute(): String = buildString(length) {
    for (char in this@escapeIosReaderHtmlAttribute) {
        append(
            when (char) {
                '&' -> "&amp;"
                '"' -> "&quot;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                else -> char
            }
        )
    }
}

private fun loadIosZipDocumentBook(book: BookItem): SharedEpubBook {
    if (book.type == FileType.PPTX) {
        return loadIosPptxBook(book)
    }
    val path = book.path.resolveIosEpubSourcePath() ?: error("${book.type.name} path is unavailable")
    val archive = IosZipEpubArchive(path)
    val title = book.title?.takeIf { it.isNotBlank() }
        ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
    val chapters = when (book.type) {
        FileType.DOCX -> loadIosDocxChapters(book, archive, title)
        FileType.ODT -> loadIosOdtZipChapters(book, archive, title)
        else -> error("Unsupported zip document type ${book.type.name}")
    }
    require(chapters.isNotEmpty()) { "No readable text was found in this ${book.type.name} file" }
    return SharedEpubBook(
        id = book.id,
        fileName = book.displayName,
        title = title,
        author = book.author,
        chapters = chapters,
    )
}

/**
 * Structured DOCX rendering (headings become chapters, formatting is preserved,
 * media embeds as data URIs) matching Android's mammoth-based import fidelity.
 */
private fun loadIosDocxChapters(
    book: BookItem,
    archive: IosZipEpubArchive,
    title: String,
): List<SharedEpubChapter> {
    val documentXml = archive.readText("word/document.xml")
        ?: error("No readable text was found in this DOCX file")
    val relsXml = archive.readText("word/_rels/document.xml.rels")
    val hyperlinkTargets = relsXml?.let(SharedDocxDocumentParser::parseHyperlinkTargets).orEmpty()
    val mediaTargets = relsXml?.let(SharedDocxDocumentParser::parseMediaTargets).orEmpty()
    val numberingXml = archive.readText("word/numbering.xml")
    val mediaSrcCache = mutableMapOf<String, String?>()
    val parsed = SharedDocxDocumentParser.parse(
        documentXml = documentXml,
        numberingXml = numberingXml,
        hyperlinkTargets = hyperlinkTargets,
        mediaSrcResolver = { relationshipId ->
            mediaSrcCache.getOrPut(relationshipId) {
                mediaTargets[relationshipId]?.let { target ->
                    archive.readBytes("word/${target.removePrefix("./")}")?.toIosDataUri(target)
                }
            }
        },
    ) ?: error("No readable text was found in this DOCX file")
    return parsed.chapters.mapIndexed { index, chapter ->
        val chapterTitle = chapter.title
            ?: if (parsed.chapters.size == 1) title else "Part ${index + 1}"
        SharedEpubChapter(
            id = "${book.id}-part-${index + 1}",
            title = chapterTitle,
            plainText = chapter.html.iosReaderPlainText(),
            htmlContent = chapter.html.trim(),
        )
    }
}

private fun loadIosOdtZipChapters(
    book: BookItem,
    archive: IosZipEpubArchive,
    title: String,
): List<SharedEpubChapter> {
    val contentXml = archive.readText("content.xml")
        ?: error("content.xml not found in ODT archive.")
    val stylesXml = archive.readText("styles.xml")
    val result = SharedOdtDocumentParser.parse(
        contentXml = contentXml,
        stylesXml = stylesXml,
        isFlat = false,
        fileNameHint = title,
    )
    return loadIosOdtResultChapters(book, result) { href ->
        val cleanPath = href.removePrefix("./")
        archive.readBytes(cleanPath)?.toIosDataUri(cleanPath)
    }
}

private fun ByteArray.toIosDataUri(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    val mime = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "image/png"
    }
    return "data:$mime;base64,${toIosBase64()}"
}

private fun String.decodeIosXmlEntities(): String {
    return replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

private fun ByteArray.toIosBase64(): String {
    if (isEmpty()) return ""
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    return buildString(((size + 2) / 3) * 4) {
        var index = 0
        while (index < this@toIosBase64.size) {
            val first = this@toIosBase64[index++].toInt() and 0xFF
            val second = if (index < this@toIosBase64.size) this@toIosBase64[index++].toInt() and 0xFF else -1
            val third = if (index < this@toIosBase64.size) this@toIosBase64[index++].toInt() and 0xFF else -1
            append(alphabet[first shr 2])
            append(alphabet[((first and 0x03) shl 4) or if (second >= 0) second shr 4 else 0])
            append(if (second >= 0) alphabet[((second and 0x0F) shl 2) or if (third >= 0) third shr 6 else 0] else '=')
            append(if (third >= 0) alphabet[third and 0x3F] else '=')
        }
    }
}

private fun String.escapeIosReaderHtml(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

internal class IosZipEpubArchive(path: String) : SharedEpubArchive {
    private val archiveBytes = path.readIosFileBytes()
    private val entries: Map<String, IosZipEntry> = parseZipEntries(archiveBytes)

    override val entryPaths: Set<String> = entries.values.mapTo(linkedSetOf()) { it.path }

    override fun readBytes(path: String): ByteArray? {
        val normalized = normalizeIosZipPath(path) ?: return null
        val entry = entries[normalized.lowercase()] ?: return null
        val localOffset = entry.localHeaderOffset.checkedInt("local header offset")
        archiveBytes.requireSignature(localOffset, ZipLocalHeaderSignature, "local file header")
        val fileNameLength = archiveBytes.u16(localOffset + 26)
        val extraLength = archiveBytes.u16(localOffset + 28)
        val dataOffsetLong = entry.localHeaderOffset + 30L + fileNameLength + extraLength
        val dataOffset = dataOffsetLong.checkedInt("entry data offset")
        val compressedSize = entry.compressedSize.checkedInt("compressed entry size")
        require(
            dataOffsetLong >= 0 &&
                dataOffsetLong + entry.compressedSize >= dataOffsetLong &&
                dataOffsetLong + entry.compressedSize <= archiveBytes.size.toLong()
        ) {
            "EPUB ZIP entry exceeds archive bounds: ${entry.path}"
        }
        val compressed = archiveBytes.copyOfRange(dataOffset, dataOffset + compressedSize)
        val output = when (entry.compressionMethod) {
            ZipMethodStored -> compressed
            ZipMethodDeflate -> inflateRawZipEntry(compressed, entry.uncompressedSize.checkedInt("uncompressed entry size"))
            else -> error("Unsupported EPUB ZIP compression method ${entry.compressionMethod}: ${entry.path}")
        }
        require(output.size.toLong() == entry.uncompressedSize) {
            "EPUB ZIP entry size mismatch: ${entry.path}"
        }
        require(output.crc32() == entry.crc32) {
            "EPUB ZIP entry checksum mismatch: ${entry.path}"
        }
        return output
    }

    override fun readText(path: String): String? {
        val bytes = readBytes(path) ?: return null
        return bytes.decodeEpubText()
    }
}

private fun IosZipEpubArchive.findIosOpfPath(): String? {
    val container = readText("META-INF/container.xml")
    val declared = container?.let {
        Regex(
            """<rootfile\b[^>]*\bfull-path\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ).find(it)?.groupValues?.getOrNull(1)
    }?.trim()?.trimStart('/')?.takeIf(String::isNotBlank)
    return declared?.takeIf { it in entryPaths }
        ?: entryPaths.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
}

private val IosSupportedCoverExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun String.rewriteIosOpfMetadata(
    title: String?,
    author: String?,
    description: String?,
    seriesName: String?,
    seriesIndex: Double?,
    coverHref: String?,
): String {
    val metadataMatch = Regex(
        """<((?:[\w.-]+:)?metadata)\b([^>]*)>([\s\S]*?)</\1\s*>""",
        RegexOption.IGNORE_CASE,
    ).find(this) ?: error("EPUB package metadata section is missing.")
    var metadataBody = metadataMatch.groupValues[3]
    metadataBody = metadataBody.upsertIosDcElement("title", title)
    metadataBody = metadataBody.upsertIosDcElement("creator", author)
    metadataBody = metadataBody.upsertIosDcElement("description", description)
    metadataBody = metadataBody.upsertIosMetaContent("calibre:series", seriesName)
    metadataBody = metadataBody.upsertIosMetaContent(
        "calibre:series_index",
        seriesIndex?.formatIosSeriesIndex(),
    )

    var output = replaceRange(
        metadataMatch.range,
        "<${metadataMatch.groupValues[1]}${metadataMatch.groupValues[2]}>$metadataBody</${metadataMatch.groupValues[1]}>",
    )
    if (coverHref != null) {
        val manifestMatch = Regex(
            """<((?:[\w.-]+:)?manifest)\b([^>]*)>([\s\S]*?)</\1\s*>""",
            RegexOption.IGNORE_CASE,
        ).find(output) ?: error("EPUB package manifest section is missing.")
        val coverId = output.iosMetaContent("cover").orEmpty().ifBlank { "cover-image" }
        val manifestBody = manifestMatch.groupValues[3].upsertIosCoverManifestItem(
            preferredId = coverId,
            href = coverHref,
        )
        output = output.replaceRange(
            manifestMatch.range,
            "<${manifestMatch.groupValues[1]}${manifestMatch.groupValues[2]}>$manifestBody</${manifestMatch.groupValues[1]}>",
        )
        val refreshedMetadata = Regex(
            """<((?:[\w.-]+:)?metadata)\b([^>]*)>([\s\S]*?)</\1\s*>""",
            RegexOption.IGNORE_CASE,
        ).find(output) ?: error("EPUB package metadata section is missing.")
        val withCover = refreshedMetadata.groupValues[3].upsertIosMetaContent("cover", coverId)
        output = output.replaceRange(
            refreshedMetadata.range,
            "<${refreshedMetadata.groupValues[1]}${refreshedMetadata.groupValues[2]}>$withCover</${refreshedMetadata.groupValues[1]}>",
        )
    }
    if (!Regex("""\bxmlns:dc\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(output)) {
        output = output.replaceFirst(
            Regex("""<((?:[\w.-]+:)?package)\b""", RegexOption.IGNORE_CASE),
            "<$1 xmlns:dc=\"http://purl.org/dc/elements/1.1/\"",
        )
    }
    return output
}

private fun String.upsertIosDcElement(localName: String, value: String?): String {
    val expression = Regex(
        """<((?:[\w.-]+:)?$localName)\b[^>]*>[\s\S]*?</\1\s*>""",
        RegexOption.IGNORE_CASE,
    )
    val normalized = value?.trim()?.takeIf(String::isNotBlank)
    val matches = expression.findAll(this).toList()
    if (normalized == null) {
        return expression.replace(this, "")
    }
    val replacement = "<dc:$localName>${normalized.escapeIosXmlText()}</dc:$localName>"
    if (matches.isEmpty()) return this + replacement
    var output = replaceRange(matches.first().range, replacement)
    expression.findAll(output).drop(1).toList().asReversed().forEach { duplicate ->
        output = output.removeRange(duplicate.range)
    }
    return output
}

private fun String.upsertIosMetaContent(name: String, value: String?): String {
    val expression = Regex(
        """<meta\b(?=[^>]*\bname\s*=\s*["']${Regex.escape(name)}["'])[^>]*(?:/>|>[\s\S]*?</meta\s*>)""",
        RegexOption.IGNORE_CASE,
    )
    val normalized = value?.trim()?.takeIf(String::isNotBlank)
    val replacement = normalized?.let {
        "<meta name=\"${name.escapeIosXmlAttribute()}\" content=\"${it.escapeIosXmlAttribute()}\"/>"
    }
    val matches = expression.findAll(this).toList()
    if (replacement == null) return expression.replace(this, "")
    if (matches.isEmpty()) return this + replacement
    var output = replaceRange(matches.first().range, replacement)
    expression.findAll(output).drop(1).toList().asReversed().forEach { duplicate ->
        output = output.removeRange(duplicate.range)
    }
    return output
}

private fun String.upsertIosCoverManifestItem(preferredId: String, href: String): String {
    val items = Regex("""<item\b[^>]*(?:/>|>[\s\S]*?</item\s*>)""", RegexOption.IGNORE_CASE)
        .findAll(this)
        .toList()
    val target = items.firstOrNull { match ->
        match.value.iosXmlAttribute("properties")
            ?.split(Regex("""\s+"""))
            ?.any { it.equals("cover-image", ignoreCase = true) } == true
    } ?: items.firstOrNull { match ->
        match.value.iosXmlAttribute("id")?.equals(preferredId, ignoreCase = true) == true
    } ?: items.firstOrNull { match ->
        match.value.iosXmlAttribute("id")?.equals("cover-image", ignoreCase = true) == true ||
            match.value.iosXmlAttribute("id")?.equals("cover", ignoreCase = true) == true
    }
    val id = target?.value?.iosXmlAttribute("id")?.takeIf(String::isNotBlank) ?: preferredId
    val existingProperties = target?.value?.iosXmlAttribute("properties")
        ?.split(Regex("""\s+"""))
        ?.filter(String::isNotBlank)
        .orEmpty()
    val properties = (existingProperties + "cover-image").distinctBy(String::lowercase)
    val extension = href.substringAfterLast('.', "").lowercase()
    val mediaType = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "application/octet-stream"
    }
    val replacement = buildString {
        append("<item id=\"").append(id.escapeIosXmlAttribute())
        append("\" href=\"").append(href.escapeIosXmlAttribute())
        append("\" media-type=\"").append(mediaType)
        append("\" properties=\"").append(properties.joinToString(" ").escapeIosXmlAttribute())
        append("\"/>")
    }
    return if (target == null) this + replacement else replaceRange(target.range, replacement)
}

private fun String.findIosEpubCoverPath(opfPath: String): String? {
    val coverId = iosMetaContent("cover")
    val items = Regex("""<item\b[^>]*(?:/>|>[\s\S]*?</item\s*>)""", RegexOption.IGNORE_CASE)
        .findAll(this)
        .map { it.value }
        .toList()
    val item = items.firstOrNull {
        coverId != null && it.iosXmlAttribute("id") == coverId
    } ?: items.firstOrNull {
        it.iosXmlAttribute("properties")
            ?.split(Regex("""\s+"""))
            ?.any { property -> property.equals("cover-image", ignoreCase = true) } == true
    } ?: items.firstOrNull {
        it.iosXmlAttribute("media-type")?.startsWith("image/", ignoreCase = true) == true &&
            it.iosXmlAttribute("href")?.contains("cover", ignoreCase = true) == true
    }
    val href = item?.iosXmlAttribute("href")?.trim()?.trimStart('/')?.takeIf(String::isNotBlank)
        ?: return null
    val parent = opfPath.substringBeforeLast('/', "")
    return (if (parent.isBlank() || href.contains("://")) href else "$parent/$href")
        .normalizeIosZipPathSegments()
}

private fun String.iosMetaContent(name: String): String? {
    val tag = Regex(
        """<meta\b(?=[^>]*\bname\s*=\s*["']${Regex.escape(name)}["'])[^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(this)?.value ?: return null
    return tag.iosXmlAttribute("content")?.decodeIosXmlEntities()?.trim()?.takeIf(String::isNotBlank)
}

private fun String.escapeIosXmlText(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun String.escapeIosXmlAttribute(): String = escapeIosXmlText()
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun Double.formatIosSeriesIndex(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString().trimEnd('0').trimEnd('.')

internal fun writeIosZipArchive(
    destinationPath: String,
    orderedEntries: List<String>,
    bytesForPath: (String) -> ByteArray?,
) {
    val writer = archive_write_new() ?: error("Could not create EPUB ZIP writer.")
    try {
        checkArchiveResult(archive_write_set_format_zip(writer), writer, "configure EPUB ZIP writer")
        checkArchiveResult(
            archive_write_set_options(writer, "zip:compression=store"),
            writer,
            "configure EPUB ZIP compression",
        )
        checkArchiveResult(
            archive_write_open_filename(writer, destinationPath),
            writer,
            "open rewritten EPUB",
        )
        orderedEntries.forEach { path ->
            checkArchiveResult(
                archive_write_set_options(
                    writer,
                    if (path == "mimetype") "zip:compression=store" else "zip:compression=deflate",
                ),
                writer,
                "configure EPUB ZIP entry compression",
            )
            val bytes = bytesForPath(path) ?: error("EPUB ZIP entry is unreadable: $path")
            val entry = archive_entry_new() ?: error("Could not create EPUB ZIP entry.")
            try {
                archive_entry_set_pathname(entry, path)
                archive_entry_set_filetype(entry, AE_IFREG.toUInt())
                archive_entry_set_perm(entry, 0x1A4.toUShort())
                archive_entry_set_size(entry, bytes.size.toLong())
                checkArchiveResult(archive_write_header(writer, entry), writer, "write EPUB ZIP entry header")
                if (bytes.isNotEmpty()) {
                    val written = bytes.usePinned { pinned ->
                        archive_write_data(writer, pinned.addressOf(0), bytes.size.convert())
                    }
                    require(written == bytes.size.toLong()) {
                        "Could not write the complete EPUB ZIP entry: $path"
                    }
                }
            } finally {
                archive_entry_free(entry)
            }
        }
        checkArchiveResult(archive_write_close(writer), writer, "close rewritten EPUB")
    } finally {
        archive_write_free(writer)
    }
}

private fun checkArchiveResult(result: Int, archive: kotlinx.cinterop.CPointer<cnames.structs.archive>, action: String) {
    if (result >= ARCHIVE_OK) return
    val detail = archive_error_string(archive)?.toKString()
    error("Could not $action${detail?.let { ": $it" }.orEmpty()}")
}

private data class IosZipEntry(
    val path: String,
    val compressionMethod: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: UInt,
    val localHeaderOffset: Long
)

private fun parseZipEntries(bytes: ByteArray): Map<String, IosZipEntry> {
    val eocdOffset = bytes.findZipEndOfCentralDirectory()
        ?: error("EPUB ZIP central directory was not found")
    val diskNumber = bytes.u16(eocdOffset + 4)
    val centralDisk = bytes.u16(eocdOffset + 6)
    require(diskNumber == 0 && centralDisk == 0) { "Multi-disk EPUB ZIP archives are not supported" }

    val entriesOnDisk = bytes.u16(eocdOffset + 8)
    var entryCount = bytes.u16(eocdOffset + 10).toLong()
    require(entriesOnDisk == 0xFFFF || entriesOnDisk.toLong() == entryCount) {
        "Multi-disk EPUB ZIP archives are not supported"
    }
    var centralSize = bytes.u32(eocdOffset + 12)
    var centralOffset = bytes.u32(eocdOffset + 16)
    if (entryCount == 0xFFFFL || centralSize == ZipUInt32Sentinel || centralOffset == ZipUInt32Sentinel) {
        val locatorOffset = eocdOffset - 20
        bytes.requireSignature(locatorOffset, Zip64LocatorSignature, "ZIP64 locator")
        require(bytes.u32(locatorOffset + 4) == 0L && bytes.u32(locatorOffset + 16) == 1L) {
            "Multi-disk ZIP64 EPUB archives are not supported"
        }
        val zip64Offset = bytes.u64(locatorOffset + 8).checkedInt("ZIP64 directory offset")
        bytes.requireSignature(zip64Offset, Zip64EndOfCentralDirectorySignature, "ZIP64 central directory")
        require(bytes.u32(zip64Offset + 16) == 0L && bytes.u32(zip64Offset + 20) == 0L) {
            "Multi-disk ZIP64 EPUB archives are not supported"
        }
        entryCount = bytes.u64(zip64Offset + 32)
        centralSize = bytes.u64(zip64Offset + 40)
        centralOffset = bytes.u64(zip64Offset + 48)
    }
    require(entryCount in 0..ZipMaximumEntries) { "EPUB ZIP has an unreasonable number of entries: $entryCount" }
    require(centralSize in 0..ZipMaximumCentralDirectoryBytes) { "EPUB ZIP central directory is too large" }
    require(centralOffset >= 0 && centralOffset + centralSize >= centralOffset && centralOffset + centralSize <= eocdOffset.toLong()) {
        "EPUB ZIP central directory exceeds archive bounds"
    }

    val output = linkedMapOf<String, IosZipEntry>()
    var totalUncompressedSize = 0L
    var cursor = centralOffset.checkedInt("central directory offset")
    repeat(entryCount.toInt()) {
        bytes.requireSignature(cursor, ZipCentralHeaderSignature, "central directory entry")
        val flags = bytes.u16(cursor + 8)
        require(flags and ZipEncryptedFlag == 0) { "Encrypted EPUB ZIP entries are not supported" }
        val method = bytes.u16(cursor + 10)
        val checksum = bytes.u32(cursor + 16).toUInt()
        var compressedSize = bytes.u32(cursor + 20)
        var uncompressedSize = bytes.u32(cursor + 24)
        val nameLength = bytes.u16(cursor + 28)
        val extraLength = bytes.u16(cursor + 30)
        val commentLength = bytes.u16(cursor + 32)
        var localOffset = bytes.u32(cursor + 42)
        val nameStart = cursor + 46
        val extraStart = nameStart + nameLength
        val entryEnd = extraStart + extraLength + commentLength
        require(nameStart >= 0 && entryEnd <= bytes.size) { "EPUB ZIP central entry exceeds archive bounds" }
        val rawName = bytes.copyOfRange(nameStart, nameStart + nameLength).decodeZipEntryName(flags)

        if (compressedSize == ZipUInt32Sentinel || uncompressedSize == ZipUInt32Sentinel || localOffset == ZipUInt32Sentinel) {
            val zip64 = bytes.parseZip64Extra(extraStart, extraLength)
            var valueIndex = 0
            if (uncompressedSize == ZipUInt32Sentinel) uncompressedSize = zip64.getOrElse(valueIndex++) { error("Missing ZIP64 uncompressed size") }
            if (compressedSize == ZipUInt32Sentinel) compressedSize = zip64.getOrElse(valueIndex++) { error("Missing ZIP64 compressed size") }
            if (localOffset == ZipUInt32Sentinel) localOffset = zip64.getOrElse(valueIndex) { error("Missing ZIP64 local offset") }
        }
        require(uncompressedSize <= ZipMaximumEntryBytes) { "EPUB ZIP entry is too large: $rawName" }
        require(totalUncompressedSize <= ZipMaximumTotalUncompressedBytes - uncompressedSize) {
            "EPUB ZIP expands beyond the supported size"
        }
        totalUncompressedSize += uncompressedSize
        val normalized = normalizeIosZipPath(rawName)
        if (normalized != null && !rawName.endsWith('/')) {
            output[normalized.lowercase()] = IosZipEntry(
                path = normalized,
                compressionMethod = method,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                crc32 = checksum,
                localHeaderOffset = localOffset
            )
        }
        cursor = entryEnd
    }
    require(output.isNotEmpty()) { "EPUB ZIP contains no files" }
    return output
}

private fun ByteArray.parseZip64Extra(offset: Int, length: Int): List<Long> {
    var cursor = offset
    val end = offset + length
    while (cursor + 4 <= end) {
        val headerId = u16(cursor)
        val dataSize = u16(cursor + 2)
        val dataStart = cursor + 4
        val dataEnd = dataStart + dataSize
        require(dataEnd <= end) { "Malformed EPUB ZIP extra field" }
        if (headerId == Zip64ExtraHeaderId) {
            val values = mutableListOf<Long>()
            var valueCursor = dataStart
            while (valueCursor + 8 <= dataEnd) {
                values += u64(valueCursor)
                valueCursor += 8
            }
            return values
        }
        cursor = dataEnd
    }
    return emptyList()
}

private fun inflateRawZipEntry(compressed: ByteArray, expectedSize: Int): ByteArray {
    if (expectedSize == 0) return ByteArray(0)
    require(compressed.isNotEmpty()) { "Compressed EPUB ZIP entry is empty" }
    val output = ByteArray(expectedSize)
    compressed.usePinned { inputPinned ->
        output.usePinned { outputPinned ->
            memScoped {
                val stream = alloc<z_stream>()
                stream.next_in = inputPinned.addressOf(0).reinterpret()
                stream.avail_in = compressed.size.convert()
                stream.total_in = 0uL
                stream.next_out = outputPinned.addressOf(0).reinterpret()
                stream.avail_out = output.size.convert()
                stream.total_out = 0uL
                stream.msg = null
                stream.state = null
                stream.zalloc = null
                stream.zfree = null
                stream.opaque = null
                stream.data_type = 0
                stream.adler = 0uL
                stream.reserved = 0uL
                val initialized = inflateInit2(stream.ptr, -MAX_WBITS)
                require(initialized == Z_OK) { "Could not initialize EPUB ZIP decompression: $initialized" }
                try {
                    val result = inflate(stream.ptr, Z_FINISH)
                    require(result == Z_STREAM_END) { "Could not decompress EPUB ZIP entry: $result" }
                    require(stream.total_out.toLong() == expectedSize.toLong()) { "EPUB ZIP decompression produced the wrong size" }
                } finally {
                    inflateEnd(stream.ptr)
                }
            }
        }
    }
    return output
}

internal fun String?.resolveIosEpubSourcePath(): String? {
    val raw = this?.takeIf(String::isNotBlank) ?: return null
    if (NSFileManager.defaultManager.fileExistsAtPath(raw)) return raw
    val fileName = raw.substringAfterLast('/').takeIf(String::isNotBlank) ?: return null
    val appSupport = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL
    val importCandidate = appSupport?.URLByAppendingPathComponent("Imports", isDirectory = true)
        ?.URLByAppendingPathComponent(fileName, isDirectory = false)
        ?.path
    val documentsCandidate = (NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL)
        ?.URLByAppendingPathComponent(fileName, isDirectory = false)
        ?.path
    return listOfNotNull(importCandidate, documentsCandidate)
        .firstOrNull(NSFileManager.defaultManager::fileExistsAtPath)
}

internal fun String.readIosFileBytes(): ByteArray {
    val data = NSFileManager.defaultManager.contentsAtPath(this) ?: error("Could not read EPUB file: $this")
    require(data.length <= ZipMaximumArchiveBytes.toULong()) { "EPUB file is too large" }
    val output = ByteArray(data.length.toInt())
    if (output.isNotEmpty()) {
        output.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, output.size.convert()) }
    }
    return output
}

internal fun ByteArray.decodeEpubText(): String {
    if (size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte()) {
        return buildString((size - 2) / 2) {
            var index = 2
            while (index + 1 < size) {
                append(((this@decodeEpubText[index].toInt() and 0xFF) or ((this@decodeEpubText[index + 1].toInt() and 0xFF) shl 8)).toChar())
                index += 2
            }
        }
    }
    if (size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte()) {
        return buildString((size - 2) / 2) {
            var index = 2
            while (index + 1 < size) {
                append((((this@decodeEpubText[index].toInt() and 0xFF) shl 8) or (this@decodeEpubText[index + 1].toInt() and 0xFF)).toChar())
                index += 2
            }
        }
    }
    val utf8 = decodeToString().removePrefix("\uFEFF")
    if ('\uFFFD' !in utf8) return utf8
    return buildString(size) { this@decodeEpubText.forEach { append((it.toInt() and 0xFF).toChar()) } }
}

private fun ByteArray.decodeZipEntryName(flags: Int): String {
    val decoded = decodeToString()
    if ('\uFFFD' !in decoded || flags and ZipUtf8Flag != 0) return decoded
    return buildString(size) { this@decodeZipEntryName.forEach { append((it.toInt() and 0xFF).toChar()) } }
}

private fun normalizeIosZipPath(path: String): String? {
    val parts = ArrayDeque<String>()
    path.replace('\\', '/').split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeLast()
            else -> {
                if ('\u0000' in part) return null
                parts.addLast(part)
            }
        }
    }
    return parts.joinToString("/").takeIf(String::isNotBlank)
}

private fun ByteArray.u16(offset: Int): Int {
    require(offset >= 0 && offset + 2 <= size) { "Unexpected end of EPUB ZIP" }
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.u32(offset: Int): Long {
    require(offset >= 0 && offset + 4 <= size) { "Unexpected end of EPUB ZIP" }
    return (u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)) and 0xFFFF_FFFFL
}

private fun ByteArray.u64(offset: Int): Long {
    val low = u32(offset)
    val high = u32(offset + 4)
    require(high <= 0x7FFF_FFFFL) { "EPUB ZIP64 value exceeds supported range" }
    return low or (high shl 32)
}

private fun ByteArray.findZipEndOfCentralDirectory(): Int? {
    val minimum = (size - ZipMaximumCommentLength - 22).coerceAtLeast(0)
    for (offset in (size - 22).coerceAtLeast(0) downTo minimum) {
        if (
            u32(offset) == ZipEndOfCentralDirectorySignature &&
            offset + 22 + u16(offset + 20) == size
        ) {
            return offset
        }
    }
    return null
}

private fun ByteArray.requireSignature(offset: Int, signature: Long, label: String) {
    require(offset >= 0 && offset + 4 <= size && u32(offset) == signature) { "Malformed EPUB ZIP $label" }
}

private fun Long.checkedInt(label: String): Int {
    require(this in 0..Int.MAX_VALUE.toLong()) { "EPUB ZIP $label exceeds supported range" }
    return toInt()
}

private fun ByteArray.crc32(): UInt {
    var crc = 0xFFFF_FFFFu
    forEach { byte ->
        crc = crc xor (byte.toUInt() and 0xFFu)
        repeat(8) { crc = if (crc and 1u != 0u) (crc shr 1) xor 0xEDB8_8320u else crc shr 1 }
    }
    return crc xor 0xFFFF_FFFFu
}

private const val ZipLocalHeaderSignature = 0x04034B50L
private const val ZipCentralHeaderSignature = 0x02014B50L
private const val ZipEndOfCentralDirectorySignature = 0x06054B50L
private const val Zip64EndOfCentralDirectorySignature = 0x06064B50L
private const val Zip64LocatorSignature = 0x07064B50L
private const val ZipUInt32Sentinel = 0xFFFF_FFFFL
private const val Zip64ExtraHeaderId = 0x0001
private const val ZipMethodStored = 0
private const val ZipMethodDeflate = 8
private const val ZipEncryptedFlag = 0x0001
private const val ZipUtf8Flag = 0x0800
private const val ZipMaximumCommentLength = 65_535
private const val ZipMaximumEntries = 100_000L
private const val ZipMaximumCentralDirectoryBytes = 128L * 1024L * 1024L
private const val ZipMaximumEntryBytes = 512L * 1024L * 1024L
private const val ZipMaximumTotalUncompressedBytes = 1L * 1024L * 1024L * 1024L
private const val ZipMaximumArchiveBytes = 1L * 1024L * 1024L * 1024L
private const val IosMaximumComicImageBytes = 128L * 1024L * 1024L
