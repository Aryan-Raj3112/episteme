package com.aryan.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.ZipInputStream

internal data class EmbeddedEbookMetadata(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val cover: EmbeddedEbookCover? = null
)

internal data class EmbeddedEbookCover(
    val bytes: ByteArray,
    val extension: String
)

internal object EmbeddedEbookMetadataExtractor {
    private const val MAX_XML_ENTRY_BYTES = 512 * 1024
    private const val MAX_COVER_BYTES = 24 * 1024 * 1024
    private const val MAX_MOBI_HEADER_RECORD_BYTES = 4 * 1024 * 1024
    private const val MAX_MOBI_RECORDS = 65_535
    private const val MAX_MOBI_EXTH_RECORDS = 10_000
    private const val MOBI_NOT_SET = -1

    private val ebookCoverTypes = setOf(FileType.EPUB, FileType.MOBI, FileType.FB2)
    private val rasterCoverExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun canExtractEmbeddedCover(type: FileType): Boolean = type in ebookCoverTypes

    fun extract(
        type: FileType,
        displayName: String,
        openStream: () -> InputStream?,
        extractCover: Boolean = true
    ): EmbeddedEbookMetadata {
        return when (type) {
            FileType.EPUB -> extractEpub(openStream, extractCover)
            FileType.MOBI -> extractMobi(openStream, extractCover)
            FileType.FB2 -> extractFb2(displayName, openStream, extractCover)
            else -> EmbeddedEbookMetadata()
        }
    }

    private fun extractEpub(openStream: () -> InputStream?, extractCover: Boolean): EmbeddedEbookMetadata {
        val containerXml = openStream()?.use { input ->
            readFirstZipTextEntry(input, MAX_XML_ENTRY_BYTES) { name ->
                name.equals("META-INF/container.xml", ignoreCase = true)
            }?.text
        }
        val declaredOpfPath = containerXml
            ?.let(::parseEpubRootfilePath)
            ?.let(::normalizeZipPath)
            ?.takeIf { it.isNotBlank() }

        val opfEntry = declaredOpfPath
            ?.let { path ->
                openStream()?.use { input ->
                    readFirstZipTextEntry(input, MAX_XML_ENTRY_BYTES) { name ->
                        name.equals(path, ignoreCase = true)
                    }
                }
            }
            ?: openStream()?.use { input ->
                readFirstZipTextEntry(input, MAX_XML_ENTRY_BYTES) { name ->
                    name.endsWith(".opf", ignoreCase = true)
                }
            }
            ?: return EmbeddedEbookMetadata()
        val opfPath = opfEntry.path
        val opf = opfEntry.text

        val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            .let { if (it.isBlank()) "" else "$it/" }
        val manifest = parseEpubManifest(opf)
        val cover = if (extractCover) {
            val coverItem = findExplicitEpubCover(opf, manifest)
            coverItem
                ?.takeIf { it.rasterExtension != null }
                ?.let { item ->
                    val rawPath = resolveEpubZipPath(basePath, item.href)
                    val decodedPath = resolveEpubZipPath(basePath, item.href.percentDecodedOrSelf())
                    listOf(decodedPath, rawPath).distinct().firstNotNullOfOrNull { path ->
                        readZipEntryBytes(openStream, path, MAX_COVER_BYTES)?.let { bytes ->
                            EmbeddedEbookCover(bytes = bytes, extension = item.rasterExtension ?: "png")
                        }
                    }
                }
        } else {
            null
        }

        return EmbeddedEbookMetadata(
            title = opf.tagText("title"),
            author = opf.tagText("creator"),
            description = opf.tagInnerContent("description"),
            seriesName = opf.metaContent("calibre:series"),
            seriesIndex = opf.metaContent("calibre:series_index")?.toDoubleOrNull(),
            cover = cover
        )
    }

    private fun readFirstZipTextEntry(
        input: InputStream,
        maxBytes: Int,
        matches: (String) -> Boolean
    ): ZipTextEntry? {
        var result: ZipTextEntry? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (entry.isDirectory) continue
                    val name = normalizeZipPath(entry.name)
                    if (matches(name)) {
                        result = zip.readBytesLimited(maxBytes)
                            ?.toString(Charsets.UTF_8)
                            ?.let { ZipTextEntry(path = name, text = it) }
                        break
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        return result
    }

    private fun readZipEntryBytes(
        openStream: () -> InputStream?,
        targetPath: String,
        maxBytes: Int
    ): ByteArray? {
        return openStream()?.use { input ->
            var result: ByteArray? = null
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    try {
                        if (!entry.isDirectory && normalizeZipPath(entry.name).equals(targetPath, ignoreCase = true)) {
                            result = zip.readBytesLimited(maxBytes)
                            break
                        }
                    } finally {
                        zip.closeEntry()
                    }
                }
            }
            result
        }
    }

    private fun parseEpubRootfilePath(containerXml: String): String? {
        return Regex("""<rootfile\b[^>]*\bfull-path=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(containerXml)
            ?.groupValues
            ?.get(1)
            ?.decodeEntities()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseEpubManifest(opf: String): List<EpubManifestItem> {
        return Regex("""<item\s+[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(opf)
            .mapNotNull { match ->
                val item = match.value
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isBlank() || href.isBlank()) {
                    null
                } else {
                    EpubManifestItem(
                        id = id,
                        href = href.decodeEntities(),
                        mediaType = item.attr("media-type"),
                        properties = item.attr("properties")
                    )
                }
            }
            .toList()
    }

    private fun findExplicitEpubCover(opf: String, manifest: List<EpubManifestItem>): EpubManifestItem? {
        val coverId = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(opf)
            .firstOrNull { it.value.attr("name").equals("cover", ignoreCase = true) }
            ?.value
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }

        return manifest.firstOrNull { it.id == coverId }
            ?: manifest.firstOrNull { item ->
                item.properties.split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) }
            }
    }

    private fun extractFb2(
        displayName: String,
        openStream: () -> InputStream?,
        extractCover: Boolean
    ): EmbeddedEbookMetadata {
        return openStream()?.use { input ->
            if (displayName.endsWith(".zip", ignoreCase = true)) {
                ZipInputStream(input.buffered()).use { zip ->
                    var metadata: EmbeddedEbookMetadata? = null
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        try {
                            if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                                metadata = parseFb2Xml(zip, extractCover)
                                break
                            }
                        } finally {
                            zip.closeEntry()
                        }
                    }
                    metadata ?: EmbeddedEbookMetadata()
                }
            } else {
                parseFb2Xml(input, extractCover)
            }
        } ?: EmbeddedEbookMetadata()
    }

    private fun parseFb2Xml(input: InputStream, extractCover: Boolean): EmbeddedEbookMetadata {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title: String? = null
        val authors = mutableListOf<String>()
        var inAuthor = false
        var inBody = false
        var inCoverPage = false
        val authorParts = mutableListOf<String>()
        var coverImageId: String? = null
        var cover: EmbeddedEbookCover? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.localXmlName()) {
                        "body" -> inBody = true
                        "coverpage" -> {
                            if (!inBody) inCoverPage = true
                        }
                        "author" -> {
                            if (!inBody) {
                                inAuthor = true
                                authorParts.clear()
                            }
                        }
                        "book-title" -> {
                            if (title == null) {
                                title = parser.nextTextOrNull()
                            }
                        }
                        "first-name", "middle-name", "last-name", "nickname" -> {
                            if (inAuthor) {
                                parser.nextTextOrNull()?.let(authorParts::add)
                            }
                        }
                        "image" -> {
                            if (inCoverPage && coverImageId == null) {
                                coverImageId = parser.hrefAttr()?.removePrefix("#")?.takeIf { it.isNotBlank() }
                            }
                        }
                        "binary" -> {
                            val id = parser.attrValue("id")
                            val contentType = parser.attrValue("content-type")
                            val isExplicitCover = id != null && id == coverImageId
                            if (extractCover && cover == null && isExplicitCover) {
                                val encoded = parser.nextTextOrNull()
                                val decoded = encoded
                                    ?.takeIf { it.length <= MAX_COVER_BYTES * 2 }
                                    ?.decodeBase64OrNull()
                                val extension = extensionFromMimeType(contentType)
                                    ?: id?.rasterExtension()
                                    ?: decoded?.rasterExtensionFromMagic()
                                    ?: "jpg"
                                if (decoded != null && decoded.size <= MAX_COVER_BYTES && extension in rasterCoverExtensions) {
                                    cover = EmbeddedEbookCover(bytes = decoded, extension = extension)
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.localXmlName()) {
                        "body" -> inBody = false
                        "coverpage" -> inCoverPage = false
                        "author" -> {
                            if (inAuthor && authorParts.isNotEmpty()) {
                                authors += authorParts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                            }
                            inAuthor = false
                            authorParts.clear()
                        }
                    }
                }
            }
            event = parser.next()
        }

        return EmbeddedEbookMetadata(
            title = title,
            author = authors.distinct().joinToString(", ").takeIf { it.isNotBlank() },
            cover = cover
        )
    }

    private fun extractMobi(openStream: () -> InputStream?, extractCover: Boolean): EmbeddedEbookMetadata {
        val scan = openStream()?.use { readMobiRecordScan(it) } ?: return EmbeddedEbookMetadata()
        val header = scan.headerRecord
        val headerInfo = parseMobiHeaderInfo(header)
        val charset = when (headerInfo.encoding ?: 1252) {
            65001 -> Charsets.UTF_8
            1200 -> Charsets.UTF_16
            1252 -> Charset.forName("windows-1252")
            else -> Charsets.UTF_8
        }
        val exth = parseMobiExth(header, charset)
        val cover = if (extractCover) {
            headerInfo.imageIndex
                ?.let { imageIndex -> exth.coverOffset?.let { imageIndex + it } }
                ?.takeIf { it > 0 }
                ?.let { recordIndex -> readMobiRecord(openStream, scan.offsets, recordIndex, MAX_COVER_BYTES) }
                ?.takeIf { it.size <= MAX_COVER_BYTES }
                ?.let { imageBytes ->
                    imageBytes.rasterExtensionFromMagic()?.let { extension ->
                        EmbeddedEbookCover(bytes = imageBytes, extension = extension)
                    }
                }
        } else {
            null
        }

        return EmbeddedEbookMetadata(
            title = exth.title,
            author = exth.author,
            cover = cover
        )
    }

    private fun readMobiRecordScan(input: InputStream): MobiRecordScan? {
        val buffered = input.buffered()
        val palmHeader = buffered.readExactBytesOrNull(78) ?: return null
        val recordCount = palmHeader.u16(76)
        if (recordCount <= 0 || recordCount > MAX_MOBI_RECORDS) return null

        val recordTable = buffered.readExactBytesOrNull(recordCount * 8) ?: return null
        val offsets = (0 until recordCount).map { index ->
            recordTable.u32(index * 8).toInt()
        }
        val record0Start = offsets.getOrNull(0) ?: return null
        val record0End = offsets.getOrNull(1)
        if (record0Start < 78 + recordTable.size) return null

        val headerRecord = readRecordFromCurrentStream(
            input = buffered,
            currentOffset = 78 + recordTable.size,
            recordStart = record0Start,
            recordEnd = record0End,
            maxBytes = MAX_MOBI_HEADER_RECORD_BYTES
        ) ?: return null

        return MobiRecordScan(offsets = offsets, headerRecord = headerRecord)
    }

    private fun readMobiRecord(
        openStream: () -> InputStream?,
        offsets: List<Int>,
        recordIndex: Int,
        maxBytes: Int
    ): ByteArray? {
        val recordStart = offsets.getOrNull(recordIndex) ?: return null
        val recordEnd = offsets.getOrNull(recordIndex + 1)
        if (recordStart < 0) return null

        return openStream()?.use { input ->
            val buffered = input.buffered()
            readRecordFromCurrentStream(
                input = buffered,
                currentOffset = 0,
                recordStart = recordStart,
                recordEnd = recordEnd,
                maxBytes = maxBytes
            )
        }
    }

    private fun readRecordFromCurrentStream(
        input: InputStream,
        currentOffset: Int,
        recordStart: Int,
        recordEnd: Int?,
        maxBytes: Int
    ): ByteArray? {
        if (recordStart < currentOffset) return null
        if (!input.skipFully((recordStart - currentOffset).toLong())) return null

        val length = recordEnd?.minus(recordStart)
        return if (length != null) {
            if (length <= 0 || length > maxBytes) return null
            input.readExactBytesOrNull(length)
        } else {
            input.readBytesLimited(maxBytes)
        }
    }

    private fun parseMobiHeaderInfo(header: ByteArray): MobiHeaderInfo {
        if (header.size < 32 || header.asciiAt(16, 4) != "MOBI") return MobiHeaderInfo()
        val mobiHeaderLength = header.u32(20).toInt()

        fun u32InHeader(offset: Int): Int? {
            if (mobiHeaderLength < offset + 4 || 16 + offset + 4 > header.size) return null
            return header.u32(16 + offset).toInt()
                .takeIf { it >= 0 && it != MOBI_NOT_SET }
        }

        return MobiHeaderInfo(
            encoding = u32InHeader(12),
            imageIndex = u32InHeader(92)
        )
    }

    private fun parseMobiExth(header: ByteArray, charset: Charset): MobiExthMetadata {
        if (header.size < 92 || header.asciiAt(16, 4) != "MOBI") return MobiExthMetadata()
        val mobiHeaderLength = header.u32(20).toInt()
        val fullNameOffset = header.u32(16 + 68).toInt()
        val fullNameLength = header.u32(16 + 72).toInt()
        val fullName = header.safeString(fullNameOffset, fullNameLength, charset)
        var exthTitle: String? = null
        var author: String? = null
        var coverOffset: Int? = null
        val exthOffsetLong = 16L + mobiHeaderLength
        if (mobiHeaderLength <= 0 || exthOffsetLong > Int.MAX_VALUE - 12L) {
            return MobiExthMetadata(title = fullName.takeUnlessBlank())
        }
        val exthOffset = exthOffsetLong.toInt()

        if (exthOffset + 12 <= header.size && header.asciiAt(exthOffset, 4) == "EXTH") {
            val recordCount = header.u32(exthOffset + 8).toInt()
            var offset = exthOffset + 12
            repeat(recordCount.coerceIn(0, MAX_MOBI_EXTH_RECORDS)) {
                if (offset + 8 > header.size) return@repeat
                val type = header.u32(offset).toInt()
                val size = header.u32(offset + 4).toInt()
                if (size < 8 || offset + size > header.size) return@repeat
                val dataOffset = offset + 8
                val dataSize = size - 8
                when (type) {
                    99 -> exthTitle = exthTitle ?: header.safeString(dataOffset, dataSize, charset)
                    100 -> author = author ?: header.safeString(dataOffset, dataSize, charset)
                    201 -> coverOffset = coverOffset ?: header.u32(dataOffset).toInt().takeIf { dataSize >= 4 }
                    503 -> exthTitle = exthTitle ?: header.safeString(dataOffset, dataSize, charset)
                }
                offset += size
            }
        }

        return MobiExthMetadata(
            title = exthTitle.takeUnlessBlank() ?: fullName.takeUnlessBlank(),
            author = author.takeUnlessBlank(),
            coverOffset = coverOffset
        )
    }

    private fun XmlPullParser.nextTextOrNull(): String? {
        return try {
            nextText()?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun XmlPullParser.attrValue(localName: String): String? {
        for (index in 0 until attributeCount) {
            val name = getAttributeName(index).localXmlName()
            if (name.equals(localName, ignoreCase = true)) {
                return getAttributeValue(index)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun XmlPullParser.hrefAttr(): String? {
        return attrValue("href")
            ?: getAttributeValue("http://www.w3.org/1999/xlink", "href")?.takeIf { it.isNotBlank() }
    }

    private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun InputStream.readExactBytesOrNull(size: Int): ByteArray? {
        if (size < 0) return null
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(bytes, offset, size - offset)
            if (read == -1) return null
            offset += read
        }
        return bytes
    }

    private fun InputStream.skipFully(bytes: Long): Boolean {
        var remaining = bytes
        val scratch = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }

            val read = read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (read == -1) return false
            remaining -= read
        }
        return true
    }

    private fun String.decodeBase64OrNull(): ByteArray? {
        return runCatching {
            Base64.getMimeDecoder().decode(this)
        }.getOrNull()
    }

    private fun String.attr(name: String): String {
        return Regex("""\b$name=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    }

    private fun String.tagText(tag: String): String? {
        return Regex(
            "<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.replace(Regex("<[^>]+>"), " ")
            ?.decodeEntities()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.tagInnerContent(tag: String): String? {
        return Regex(
            "<(?:[^:>]+:)?$tag\\b[^>]*>(.*?)</(?:[^:>]+:)?$tag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.decodeEntities()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.metaContent(name: String): String? {
        return Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(this)
            .firstOrNull { it.value.attr("name").equals(name, ignoreCase = true) }
            ?.value
            ?.attr("content")
            ?.decodeEntities()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.decodeEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
    }

    private fun normalizeZipPath(path: String): String {
        val parts = ArrayDeque<String>()
        path.replace('\\', '/').trimStart('/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun resolveEpubZipPath(basePath: String, href: String): String {
        return normalizeZipPath(if (href.startsWith('/')) href else basePath + href)
    }

    private fun String.percentDecodedOrSelf(): String {
        return runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrDefault(this)
    }

    private fun String.localXmlName(): String = substringAfter(':').lowercase()

    private fun String.rasterExtension(): String? {
        val extension = substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        return extension.takeIf { it in rasterCoverExtensions }
    }

    private fun extensionFromMimeType(mimeType: String?): String? {
        return when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> null
        }
    }

    private fun ByteArray.rasterExtensionFromMagic(): String? {
        return when {
            size >= 3 &&
                (this[0].toInt() and 0xFF) == 0xFF &&
                (this[1].toInt() and 0xFF) == 0xD8 &&
                (this[2].toInt() and 0xFF) == 0xFF -> "jpg"
            size >= 8 && asciiAt(1, 3) == "PNG" -> "png"
            size >= 6 && (asciiAt(0, 6) == "GIF87a" || asciiAt(0, 6) == "GIF89a") -> "gif"
            size >= 12 && asciiAt(0, 4) == "RIFF" && asciiAt(8, 4) == "WEBP" -> "webp"
            size >= 2 && asciiAt(0, 2) == "BM" -> "bmp"
            else -> null
        }
    }

    private fun ByteArray.u16(offset: Int): Int {
        if (offset + 2 > size) return 0
        return ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
    }

    private fun ByteArray.u32(offset: Int): Long {
        if (offset + 4 > size) return 0
        return ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)
    }

    private fun ByteArray.asciiAt(offset: Int, length: Int): String {
        if (offset < 0 || offset + length > size) return ""
        return copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
    }

    private fun ByteArray.safeString(offset: Int, length: Int, charset: Charset): String? {
        if (offset < 0 || length <= 0 || offset + length > size) return null
        return copyOfRange(offset, offset + length).toString(charset)
            .trim('\u0000', ' ', '\n', '\r', '\t')
            .takeUnlessBlank()
    }

    private fun String?.takeUnlessBlank(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }

    private val EpubManifestItem.rasterExtension: String?
        get() {
            href.rasterExtension()?.let { return it }
            return extensionFromMimeType(mediaType)
        }

    private data class ZipTextEntry(
        val path: String,
        val text: String
    )

    private data class EpubManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    private data class MobiHeaderInfo(
        val encoding: Int? = null,
        val imageIndex: Int? = null
    )

    private data class MobiRecordScan(
        val offsets: List<Int>,
        val headerRecord: ByteArray
    )

    private data class MobiExthMetadata(
        val title: String? = null,
        val author: String? = null,
        val coverOffset: Int? = null
    )
}
