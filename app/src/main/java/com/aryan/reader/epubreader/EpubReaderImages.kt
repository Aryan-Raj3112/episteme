package com.aryan.reader.epubreader

import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.AndroidHtmlResourceResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import com.aryan.reader.shared.reader.ReaderImageSourceIdentity
import com.aryan.reader.shared.reader.SharedEpubDrawerImage
import com.aryan.reader.shared.reader.isSharedEpubSvgSource

data class EpubReaderImageReference(
    val id: String,
    val index: Int,
    val sourcePath: String,
    val originalSource: String,
    val altText: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val elementId: String?,
    val ordinalInChapter: Int,
    val chunkIndex: Int?,
    val intrinsicWidth: Int?,
    val intrinsicHeight: Int?
) {
    /**
     * One resolution of the source pair, in one order, shared with
     * `ReaderImageReference` (parity item B9).
     *
     * These two fields used to be consulted independently — `sourceName()`
     * preferred `originalSource` while the extension and MIME preferred
     * `sourcePath` — so one image could be named after one source, typed from
     * another, and loaded from a third. `sourcePath` wins everywhere now,
     * because it is the resolved, percent-decoded, guaranteed-non-blank path
     * that `readDownloadBytes()` actually reads.
     */
    private val identity = ReaderImageSourceIdentity(
        resolved = sourcePath,
        declared = originalSource,
    )

    val displayTitle: String
        get() = altText?.trim()?.takeIf { it.isNotBlank() }
            ?: identity.sourceName()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "Image ${index + 1}"

    val dimensionLabel: String?
        get() {
            val width = intrinsicWidth?.takeIf { it > 0 }
            val height = intrinsicHeight?.takeIf { it > 0 }
            return if (width != null && height != null) "${width}x$height" else null
        }

    fun sourceName(): String? = identity.sourceName()

    fun suggestedDownloadFileName(): String = identity.suggestedDownloadFileName(altText, index)

    fun mimeType(): String = identity.mimeType()
}

fun EpubBook.readerImageReferencesForDrawer(): List<EpubReaderImageReference> {
    val references = mutableListOf<EpubReaderImageReference>()
    chapters.forEachIndexed { chapterIndex, chapter ->
        val html = chapter.readerImageHtml(extractionBasePath).takeIf { it.isNotBlank() }
            ?: return@forEachIndexed
        val sourceOrdinalByKey = mutableMapOf<String, Int>()
        val document = Jsoup.parse(html, chapter.absPath)

        document.select("img, image").forEach { element ->
            val originalSource = element.readerImageSource() ?: return@forEach
            val sourcePath = resolveReaderImageSource(chapter, extractionBasePath, originalSource)
            val sourceKey = sourcePath.readerImageLookupKey()
            val ordinal = sourceOrdinalByKey.getOrDefault(sourceKey, 0)
            sourceOrdinalByKey[sourceKey] = ordinal + 1
            val index = references.size

            references += EpubReaderImageReference(
                id = "android-epub-image:$chapterIndex:$ordinal:${sourcePath.hashCode()}:$index",
                index = index,
                sourcePath = sourcePath,
                originalSource = originalSource,
                altText = element.attr("alt").ifBlank { element.attr("title") }.ifBlank { null },
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title.ifBlank { "Chapter ${chapterIndex + 1}" },
                elementId = element.id().ifBlank { null },
                ordinalInChapter = ordinal,
                chunkIndex = element.readerTopLevelBodyChildIndex()?.let { it / 20 },
                intrinsicWidth = element.readerImageDimension("width"),
                intrinsicHeight = element.readerImageDimension("height")
            )
        }
    }
    return references
}

fun EpubReaderImageReference.readDownloadBytes(): ByteArray? {
    if (sourcePath.startsWith("data:", ignoreCase = true)) {
        return sourcePath.readerDataUriBytes()
    }
    return runCatching {
        File(sourcePath).takeIf { it.isFile }?.readBytes()
    }.getOrNull()
}

private fun EpubChapter.readerImageHtml(extractionBasePath: String): String {
    if (htmlContent.isNotBlank()) return htmlContent
    return runCatching {
        File(extractionBasePath, htmlFilePath).takeIf { it.isFile }?.readText().orEmpty()
    }.getOrDefault("")
}

private fun Element.readerImageSource(): String? {
    return listOf("src", "href", "xlink:href", "data-src")
        .firstNotNullOfOrNull { attrName ->
            attr(attrName).trim().takeIf { it.isNotBlank() }
        }
}

private fun Element.readerImageDimension(attribute: String): Int? {
    val raw = attr(attribute).trim().takeIf { it.isNotBlank() } ?: return null
    return Regex("""\d+""").find(raw)?.value?.toIntOrNull()?.takeIf { it > 0 }
}

private fun Element.readerTopLevelBodyChildIndex(): Int? {
    val body = ownerDocument()?.body() ?: return null
    val effective = effectiveReaderChunkNodes(body.childNodes().toList())
    var node: org.jsoup.nodes.Node? = this
    while (node != null) {
        val index = effective.indexOf(node)
        if (index >= 0) return index
        node = node.parent()
    }
    return null
}

private fun resolveReaderImageSource(
    chapter: EpubChapter,
    extractionBasePath: String,
    source: String
): String {
    val withoutFragment = source.substringBefore('#').substringBefore('?')
    if (withoutFragment.startsWith("data:", ignoreCase = true)) return source

    val fileUriPath = withoutFragment.readerFileUriPath()
    fileUriPath?.let { path ->
        val file = File(path)
        if (file.isFile) {
            return runCatching { file.canonicalFile.absolutePath }.getOrDefault(file.absolutePath)
        }
    }

    val sourceForResolve = fileUriPath ?: withoutFragment
    AndroidHtmlResourceResolver.resolvePath(chapter.absPath, extractionBasePath, sourceForResolve)?.let {
        return it
    }

    val fallbackCandidates = listOf(
        File(extractionBasePath, sourceForResolve),
        File(extractionBasePath, sourceForResolve.trimStart('/', '\\'))
    )
    return fallbackCandidates
        .firstOrNull { it.isFile }
        ?.let { runCatching { it.canonicalFile.absolutePath }.getOrDefault(it.absolutePath) }
        ?: source
}

private fun String.readerFileUriPath(): String? {
    if (!startsWith("file:", ignoreCase = true)) return null
    return runCatching {
        URI(this).path?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
    }.getOrNull()
}

private fun String.readerImageLookupKey(): String {
    return substringBefore('#')
        .substringBefore('?')
        .replace('\\', '/')
        .lowercase()
}

private fun readerDataUriMimeType(source: String): String? {
    if (!source.startsWith("data:", ignoreCase = true)) return null
    return source
        .drop(5)
        .substringBefore(';')
        .substringBefore(',')
        .takeIf { it.startsWith("image/", ignoreCase = true) }
}

private fun String.readerDataUriBytes(): ByteArray? {
    val commaIndex = indexOf(',')
    if (!startsWith("data:", ignoreCase = true) || commaIndex == -1) return null
    val metadata = substring(0, commaIndex)
    val data = substring(commaIndex + 1)
    return runCatching {
        if (metadata.contains(";base64", ignoreCase = true)) {
            Base64.getDecoder().decode(data)
        } else {
            URLDecoder.decode(data, StandardCharsets.UTF_8.name()).toByteArray(StandardCharsets.UTF_8)
        }
    }.getOrNull()
}

/**
 * Adapts the paginated reader's image model to the shared EPUB drawer row.
 *
 * The shared row is deliberately narrow (see `SharedEpubDrawerImage`): it carries only what the
 * list and thumbnail render, so the paginated reader does not have to fabricate the
 * `ReaderLocator` that `ReaderImageReference` requires but cannot supply for an image.
 */
internal fun EpubReaderImageReference.toDrawerImage() = SharedEpubDrawerImage(
    id = id,
    ordinal = index + 1,
    displayTitle = displayTitle,
    chapterTitle = chapterTitle,
    dimensionLabel = dimensionLabel,
    sourceName = sourceName(),
    source = sourcePath,
    isSvg = sourcePath.isSharedEpubSvgSource() || originalSource.isSharedEpubSvgSource(),
    loadBytes = { readDownloadBytes() }
)
