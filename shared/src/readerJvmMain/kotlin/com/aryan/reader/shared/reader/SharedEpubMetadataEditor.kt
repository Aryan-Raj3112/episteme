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
    val seriesIndex: Double?
)

data class SharedEpubMetadataSnapshot(
    val title: String?,
    val author: String?,
    val description: String?,
    val seriesName: String?,
    val seriesIndex: Double?
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
            val updatedOpf = rewriteOpf(originalOpf, update)
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
                        entry.isDirectory -> output.putDirectoryEntry(entry)
                        else -> output.putCopiedEntry(entry, zip.readBytes(entry))
                    }
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

private fun rewriteOpf(opf: String, update: SharedEpubMetadataUpdate): String {
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
