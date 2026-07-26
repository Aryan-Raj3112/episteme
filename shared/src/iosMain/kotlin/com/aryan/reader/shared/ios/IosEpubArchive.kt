@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.mobi.MOBI_ENCRYPTION_NONE
import com.aryan.reader.shared.mobi.MOBI_SUCCESS
import com.aryan.reader.shared.mobi.MOBIFiletype
import com.aryan.reader.shared.mobi.mobi_free
import com.aryan.reader.shared.mobi.mobi_free_rawml
import com.aryan.reader.shared.mobi.mobi_init
import com.aryan.reader.shared.mobi.mobi_init_rawml
import com.aryan.reader.shared.mobi.mobi_load_file
import com.aryan.reader.shared.mobi.mobi_parse_rawml_opt
import com.aryan.reader.shared.mobi.mobi_meta_get_author
import com.aryan.reader.shared.mobi.mobi_meta_get_title
import com.aryan.reader.shared.mobi.reader_mobi_flow_type
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
import cnames.structs.archive_entry
import com.aryan.reader.shared.reader.SharedEpubArchive
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.SharedEpubPackageLoader
import com.aryan.reader.shared.reader.SharedEpubTocEntry
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
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
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
    println("[$IOS_MOBI_LOG_TAG] ${message()}")
}

internal fun loadIosEpubBook(book: BookItem): SharedEpubBook {
    if (book.type == FileType.CBZ) {
        return loadIosCbzBook(book)
    }
    if (book.type == FileType.CBT) {
        return loadIosCbtBook(book)
    }
    if (book.type == FileType.CBR || book.type == FileType.CB7) {
        return loadIosLibarchiveComicBook(book)
    }
    if (book.type == FileType.MOBI) {
        iosMobiLog { "Routing book to MOBI loader id=${book.id} file=${book.displayName}" }
        return loadIosMobiBook(book)
    }
    if (book.type in IOS_ZIP_DOCUMENT_READER_TYPES) {
        return loadIosZipDocumentBook(book)
    }
    if (book.type in IOS_SINGLE_DOCUMENT_READER_TYPES) {
        return loadIosSingleDocumentBook(book)
    }
    val path = book.path.resolveIosEpubSourcePath()
        ?: error("EPUB path is unavailable")
    val archive = IosZipEpubArchive(path)
    return SharedEpubPackageLoader.load(
        archive = archive,
        sourceId = book.id,
        fileName = book.displayName.ifBlank { path.substringAfterLast('/') }
    )
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
    val html = when (book.type) {
        FileType.HTML -> source
        FileType.FB2 -> source
            .replace(Regex("""<\?xml[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?FictionBook[^>]*>""", RegexOption.IGNORE_CASE), "")
        FileType.FODT -> source
        FileType.MD -> "<pre class=\"reader-markdown\">${source.escapeIosReaderHtml()}</pre>"
        else -> "<pre>${source.escapeIosReaderHtml()}</pre>"
    }
    val plainText = source
        .replace(Regex("""<script\b[^>]*>[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n\s*\n\s*\n+"""), "\n\n")
        .trim()
        .ifBlank { source.trim() }
    return SharedEpubBook(
        id = book.id,
        fileName = book.displayName.ifBlank { path.substringAfterLast('/') },
        title = title,
        author = book.author,
        chapters = listOf(
            SharedEpubChapter(
                id = "${book.id}-document",
                title = title,
                plainText = plainText,
                htmlContent = html,
                baseHref = NSURL.fileURLWithPath(path).absoluteString,
            )
        ),
    )
}

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
    val path = book.path.resolveIosEpubSourcePath() ?: error("${book.type.name} path is unavailable")
    val archive = IosZipEpubArchive(path)
    val contentPaths = when (book.type) {
        FileType.DOCX -> listOf("word/document.xml")
        FileType.ODT -> listOf("content.xml")
        FileType.PPTX -> archive.entryPaths
            .filter { it.matches(Regex("""ppt/slides/slide\d+\.xml""", RegexOption.IGNORE_CASE)) }
            .sorted()
        else -> emptyList()
    }
    val title = book.title?.takeIf { it.isNotBlank() }
        ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
    val chapters = contentPaths.mapIndexedNotNull { index, contentPath ->
        val source = archive.readText(contentPath) ?: return@mapIndexedNotNull null
        val text = source
            .replace(Regex("""</(?:w:p|text:p|text:h|a:p)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<w:tab[^>]*/>""", RegexOption.IGNORE_CASE), "\t")
            .replace(Regex("""<[^>]+>"""), "")
            .decodeIosXmlEntities()
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        if (text.isBlank()) return@mapIndexedNotNull null
        SharedEpubChapter(
            id = "${book.id}-part-${index + 1}",
            title = if (contentPaths.size == 1) title else "Slide ${index + 1}",
            plainText = text,
            htmlContent = "<pre>${text.escapeIosReaderHtml()}</pre>",
        )
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

private class IosZipEpubArchive(path: String) : SharedEpubArchive {
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

private fun String?.resolveIosEpubSourcePath(): String? {
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

private fun String.readIosFileBytes(): ByteArray {
    val data = NSFileManager.defaultManager.contentsAtPath(this) ?: error("Could not read EPUB file: $this")
    require(data.length <= ZipMaximumArchiveBytes.toULong()) { "EPUB file is too large" }
    val output = ByteArray(data.length.toInt())
    if (output.isNotEmpty()) {
        output.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, output.size.convert()) }
    }
    return output
}

private fun ByteArray.decodeEpubText(): String {
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
