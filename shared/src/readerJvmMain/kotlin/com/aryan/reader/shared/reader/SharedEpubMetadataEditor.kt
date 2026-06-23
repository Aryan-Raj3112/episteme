package com.aryan.reader.shared.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SharedEpubMetadataUpdate(
    val title: String?,
    val author: String?,
    val description: String?,
    val seriesName: String?,
    val seriesIndex: Double?,
    val cover: SharedEpubCoverUpdate? = null
)

data class SharedEpubMetadataSnapshot(
    val title: String?,
    val author: String?,
    val description: String?,
    val seriesName: String?,
    val seriesIndex: Double?
)

data class SharedEpubCoverUpdate(
    val bytes: ByteArray,
    val extension: String
) {
    val normalizedExtension: String = extension.lowercase().trimStart('.')
    val mediaType: String = normalizedExtension.coverMediaType()
}

data class SharedEpubCoverSnapshot(
    val bytes: ByteArray,
    val extension: String
)

object SharedEpubMetadataEditor {
    fun readMetadata(source: File): SharedEpubMetadataSnapshot? {
        if (!source.isFile) return null
        return runCatching {
            ZipFile(source).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opf = zip.getEntry(opfPath)?.let { zip.readUtf8(it) } ?: return null
                val metadata = parseOpfMetadata(opf) ?: return null
                metadata.toSnapshot()
            }
        }.getOrNull()
    }

    fun readCover(source: File): SharedEpubCoverSnapshot? {
        if (!source.isFile) return null
        return runCatching {
            ZipFile(source).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opf = zip.getEntry(opfPath)?.let { zip.readUtf8(it) } ?: return null
                val coverPath = findCoverPath(opf, opfPath) ?: return null
                val entry = zip.getEntry(coverPath) ?: return null
                val extension = coverPath.substringAfterLast('.', "").lowercase()
                    .takeIf { it in SupportedCoverExtensions } ?: return null
                SharedEpubCoverSnapshot(bytes = zip.readBytes(entry), extension = extension)
            }
        }.getOrNull()
    }

    fun rewrite(
        source: File,
        destination: File,
        update: SharedEpubMetadataUpdate
    ): SharedEpubMetadataSnapshot {
        require(source.isFile) { "EPUB source does not exist: ${source.path}" }
        destination.parentFile?.mkdirs()

        ZipFile(source).use { zip ->
            val opfPath = findOpfPath(zip) ?: error("EPUB package document was not found.")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB package document entry is missing.")
            val originalOpf = zip.readUtf8(opfEntry)
            val coverPath = update.cover?.let { cover ->
                require(cover.bytes.isNotEmpty()) { "EPUB cover image is empty." }
                require(cover.normalizedExtension in SupportedCoverExtensions) {
                    "Unsupported EPUB cover image type: ${cover.extension}"
                }
                resolveCoverPath(originalOpf, opfPath, cover.normalizedExtension)
            }
            val updatedOpf = rewriteOpf(originalOpf, update, coverPath?.relativeToOpf)
            val updatedOpfBytes = updatedOpf.toByteArray(StandardCharsets.UTF_8)

            if (destination.exists()) destination.delete()
            ZipOutputStream(destination.outputStream().buffered()).use { output ->
                val entries = zip.entries().asSequence().toList()
                entries.firstOrNull { it.name == "mimetype" }?.let { mimetype ->
                    output.putStoredEntry(mimetype.name, zip.readBytes(mimetype), mimetype.time)
                }

                entries.forEach { entry ->
                    when {
                        entry.name == "mimetype" -> Unit
                        entry.name == opfPath -> output.putDeflatedEntry(entry.name, updatedOpfBytes, entry.time)
                        coverPath != null && entry.name == coverPath.zipEntry -> Unit
                        entry.isDirectory -> output.putDirectoryEntry(entry)
                        else -> output.putCopiedEntry(entry, zip.readBytes(entry))
                    }
                }
                if (coverPath != null) {
                    output.putDeflatedEntry(coverPath.zipEntry, update.cover.bytes, System.currentTimeMillis())
                }
            }
        }

        return readMetadata(destination) ?: error("Rewritten EPUB failed metadata validation.")
    }

    fun rewriteInPlace(
        source: File,
        backup: File?,
        update: SharedEpubMetadataUpdate
    ): SharedEpubMetadataSnapshot {
        require(source.isFile) { "EPUB source does not exist: ${source.path}" }
        val temp = File(source.parentFile ?: source.absoluteFile.parentFile, "${source.name}.metadata.tmp")
        return try {
            val snapshot = rewrite(source, temp, update)
            if (backup != null && !backup.exists()) {
                backup.parentFile?.mkdirs()
                Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            runCatching {
                Files.move(
                    temp.toPath(),
                    source.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }.getOrElse {
                Files.move(temp.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            snapshot
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}

private fun rewriteOpf(opf: String, update: SharedEpubMetadataUpdate, coverHref: String?): String {
    val document = Jsoup.parse(opf, "", Parser.xmlParser())
    document.outputSettings()
        .syntax(Document.OutputSettings.Syntax.xml)
        .prettyPrint(false)
        .charset(StandardCharsets.UTF_8)

    val packageElement = document.children().firstOrNull { it.localNameEquals("package") }
    packageElement?.let { ensureDcNamespace(it) }

    val metadata = document.getAllElements().firstOrNull { it.localNameEquals("metadata") }
        ?: error("EPUB package metadata section is missing.")

    metadata.upsertDcText("title", update.title)
    metadata.upsertDcText("creator", update.author)
    metadata.upsertDcText("description", update.description)
    metadata.upsertMetaContent("calibre:series", update.seriesName)
    metadata.upsertMetaContent("calibre:series_index", update.seriesIndex?.formatSeriesIndex())
    if (coverHref != null) {
        val manifest = document.getAllElements().firstOrNull { it.localNameEquals("manifest") }
            ?: error("EPUB package manifest section is missing.")
        val coverId = manifest.upsertCoverItem(coverHref)
        metadata.upsertMetaContent("cover", coverId)
    }

    return document.outerHtml()
}

private fun Element.toSnapshot(): SharedEpubMetadataSnapshot {
    return SharedEpubMetadataSnapshot(
        title = firstChildText("title"),
        author = firstChildText("creator"),
        description = firstChildText("description"),
        seriesName = firstMetaContent("calibre:series"),
        seriesIndex = firstMetaContent("calibre:series_index")?.toDoubleOrNull()
    )
}

private fun parseOpfMetadata(opf: String): Element? {
    val document = Jsoup.parse(opf, "", Parser.xmlParser())
    return document.getAllElements().firstOrNull { it.localNameEquals("metadata") }
}

private fun findOpfPath(zip: ZipFile): String? {
    val container = zip.getEntry("META-INF/container.xml")
        ?.let { zip.readUtf8(it) }
    val declared = container
        ?.let { rootfilePathRegex.find(it)?.groupValues?.getOrNull(1) }
        ?.trim()
        ?.trimStart('/')
        ?.takeIf { it.isNotBlank() }
    if (declared != null && zip.getEntry(declared) != null) return declared

    return zip.entries()
        .asSequence()
        .map { it.name }
        .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
}

private val rootfilePathRegex = Regex(
    """<rootfile\b[^>]*\bfull-path=["']([^"']+)["'][^>]*>""",
    RegexOption.IGNORE_CASE
)

private fun ensureDcNamespace(packageElement: Element) {
    if (packageElement.attributes().asList().none { it.key.equals("xmlns:dc", ignoreCase = true) }) {
        packageElement.attr("xmlns:dc", "http://purl.org/dc/elements/1.1/")
    }
}

private fun Element.upsertDcText(localName: String, value: String?) {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
    val existing = children().filter { it.localNameEquals(localName) }
    if (normalized == null) {
        existing.forEach { it.remove() }
        return
    }
    val target = existing.firstOrNull() ?: Element(Tag.valueOf("dc:$localName"), "").also { appendChild(it) }
    target.text(normalized)
    existing.drop(1).forEach { it.remove() }
}

private fun Element.upsertMetaContent(name: String, value: String?) {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
    val existing = children().filter { child ->
        child.localNameEquals("meta") && child.attr("name").equals(name, ignoreCase = true)
    }
    if (normalized == null) {
        existing.forEach { it.remove() }
        return
    }
    val target = existing.firstOrNull() ?: Element(Tag.valueOf("meta"), "").also { appendChild(it) }
    target.attr("name", name)
    target.attr("content", normalized)
    existing.drop(1).forEach { it.remove() }
}

private fun Element.upsertCoverItem(href: String): String {
    val mediaType = href.substringAfterLast('.', "").lowercase().coverMediaType()
    val existingCover = children().firstOrNull { child ->
        child.localNameEquals("item") &&
            child.attr("properties").split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) }
    } ?: children().firstOrNull { child ->
        child.localNameEquals("item") && child.attr("id").equals("cover-image", ignoreCase = true)
    } ?: children().firstOrNull { child ->
        child.localNameEquals("item") && child.attr("id").equals("cover", ignoreCase = true)
    }

    val target = existingCover ?: Element(Tag.valueOf("item"), "").also { appendChild(it) }
    val id = target.attr("id").takeIf { it.isNotBlank() } ?: "cover-image"
    target.attr("id", id)
    target.attr("href", href)
    target.attr("media-type", mediaType)
    val properties = target.attr("properties")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .toMutableList()
    if (properties.none { it.equals("cover-image", ignoreCase = true) }) {
        properties.add("cover-image")
    }
    target.attr("properties", properties.joinToString(" "))
    return id
}

private fun Element.firstChildText(localName: String): String? {
    return children()
        .firstOrNull { it.localNameEquals(localName) }
        ?.text()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun Element.firstMetaContent(name: String): String? {
    return children()
        .firstOrNull { it.localNameEquals("meta") && it.attr("name").equals(name, ignoreCase = true) }
        ?.attr("content")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun Element.localNameEquals(expected: String): Boolean {
    return tagName().substringAfter(':').equals(expected, ignoreCase = true)
}

private fun Double.formatSeriesIndex(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        toString().trimEnd('0').trimEnd('.')
    }
}

private data class CoverPath(
    val zipEntry: String,
    val relativeToOpf: String
)

private val SupportedCoverExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun resolveCoverPath(opf: String, opfPath: String, extension: String): CoverPath {
    val existing = findCoverPath(opf, opfPath)
    val zipEntry = existing?.takeIf { it.substringAfterLast('.', "").lowercase() in SupportedCoverExtensions }
        ?: opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            .let { base -> listOf(base, "Images", "cover.$extension").filter { it.isNotBlank() }.joinToString("/") }
    val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
    val relative = if (basePath.isBlank()) zipEntry else zipEntry.removePrefix("$basePath/")
    return CoverPath(zipEntry = zipEntry, relativeToOpf = relative)
}

private fun findCoverPath(opf: String, opfPath: String): String? {
    val document = Jsoup.parse(opf, "", Parser.xmlParser())
    val manifest = document.getAllElements().firstOrNull { it.localNameEquals("manifest") } ?: return null
    val metadata = document.getAllElements().firstOrNull { it.localNameEquals("metadata") }
    val coverId = metadata?.children()?.firstOrNull { child ->
        child.localNameEquals("meta") && child.attr("name").equals("cover", ignoreCase = true)
    }?.attr("content")?.takeIf { it.isNotBlank() }

    val item = manifest.children().firstOrNull { child ->
        coverId != null && child.localNameEquals("item") && child.attr("id") == coverId
    } ?: manifest.children().firstOrNull { child ->
        child.localNameEquals("item") &&
            child.attr("properties").split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) }
    } ?: manifest.children().firstOrNull { child ->
        child.localNameEquals("item") &&
            child.attr("media-type").startsWith("image/", ignoreCase = true) &&
            child.attr("href").contains("cover", ignoreCase = true)
    }

    val href = item?.attr("href")?.trim()?.trimStart('/')?.takeIf { it.isNotBlank() } ?: return null
    val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
    return if (basePath.isBlank() || href.contains("://")) href else "$basePath/$href"
}

private fun String.coverMediaType(): String {
    return when (this) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "application/octet-stream"
    }
}

private fun ZipFile.readUtf8(entry: ZipEntry): String {
    return readBytes(entry).toString(StandardCharsets.UTF_8)
}

private fun ZipFile.readBytes(entry: ZipEntry): ByteArray {
    return getInputStream(entry).use { it.readBytes() }
}

private fun ZipOutputStream.putDirectoryEntry(entry: ZipEntry) {
    val copy = ZipEntry(entry.name)
    if (entry.time >= 0L) copy.time = entry.time
    copy.comment = entry.comment
    copy.extra = entry.extra
    putNextEntry(copy)
    closeEntry()
}

private fun ZipOutputStream.putCopiedEntry(entry: ZipEntry, bytes: ByteArray) {
    if (entry.method == ZipEntry.STORED) {
        putStoredEntry(entry.name, bytes, entry.time)
    } else {
        putDeflatedEntry(entry.name, bytes, entry.time)
    }
}

private fun ZipOutputStream.putStoredEntry(name: String, bytes: ByteArray, time: Long) {
    val crc = CRC32().apply { update(bytes) }.value
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        this.crc = crc
        if (time >= 0L) this.time = time
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.putDeflatedEntry(name: String, bytes: ByteArray, time: Long) {
    val entry = ZipEntry(name).apply {
        method = ZipEntry.DEFLATED
        if (time >= 0L) this.time = time
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}