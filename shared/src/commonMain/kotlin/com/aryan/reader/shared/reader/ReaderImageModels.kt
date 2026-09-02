package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.ReaderLocator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class ReaderImageReference(
    val id: String,
    val index: Int,
    val source: String,
    val altText: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val blockIndex: Int,
    val cfi: String?,
    val intrinsicWidth: Float?,
    val intrinsicHeight: Float?,
    val locator: ReaderLocator
) {
    val displayTitle: String
        get() = altText?.trim()?.takeIf { it.isNotBlank() }
            ?: sourceName()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "Image ${index + 1}"

    val dimensionLabel: String?
        get() {
            val width = intrinsicWidth?.takeIf { it > 0f }?.toInt()
            val height = intrinsicHeight?.takeIf { it > 0f }?.toInt()
            return if (width != null && height != null) "${width}x$height" else null
        }

    fun sourceName(): String? {
        if (source.startsWith("data:", ignoreCase = true)) return null
        return source
            .substringBefore('#')
            .substringBefore('?')
            .replace('\\', '/')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
    }
    fun suggestedDownloadFileName(): String {
        val extension = source.readerImageExtension()
        val sourceName = sourceName()
        val base = altText?.trim()?.takeIf { it.isNotBlank() }
            ?: sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "image-${index + 1}"
        val safeBase = base.sanitizedReaderImageFileBase().ifBlank { "image-${index + 1}" }
        val safeExtension = extension?.takeIf { it.isNotBlank() } ?: "png"
        return "$safeBase.$safeExtension"
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun downloadBytes(): ByteArray? {
        resolveSharedEpubResourceBytes(source)?.let { return it }
        if (!source.startsWith("data:", ignoreCase = true)) return null
        val comma = source.indexOf(',')
        if (comma <= 5) return null
        val metadata = source.substring(5, comma)
        val payload = source.substring(comma + 1)
        if (!metadata.contains(";base64", ignoreCase = true)) return null
        return runCatching { Base64.Default.decode(payload) }.getOrNull()
    }
}

fun SharedEpubBook.readerImageReferences(pages: List<ReaderPage> = emptyList()): List<ReaderImageReference> {
    val references = mutableListOf<ReaderImageReference>()
    chapters.forEachIndexed { chapterIndex, chapter ->
        val markers = chapter.semanticBlocks.readerImageMarkers()
        val semanticImages = markers.filterIsInstance<ReaderImageMarker.Image>()
        markers.forEachIndexed { markerIndex, marker ->
            if (marker !is ReaderImageMarker.Image) return@forEachIndexed
            val image = marker.image
            val anchorOffset = markers.nearestTextOffsetFor(markerIndex)
            val pageIndex = pages.findPageIndexForImage(chapterIndex, image)
                ?: anchorOffset?.let { offset ->
                    pages.firstOrNull { page ->
                        page.chapterIndex == chapterIndex && offset in page.startOffset..page.endOffset
                    }?.pageIndex
                }
                ?: pages.firstOrNull { it.chapterIndex == chapterIndex }?.pageIndex
            val startOffset = if (pageIndex == null) anchorOffset else null
            val endOffset = startOffset
            val locator = ReaderLocator(
                chapterIndex = chapterIndex,
                chapterId = chapter.id,
                href = chapter.baseHref,
                pageIndex = pageIndex,
                startOffset = startOffset,
                endOffset = endOffset,
                textQuote = image.altText?.takeIf { it.isNotBlank() },
                cfi = image.cfi
            )
            val index = references.size
            references += ReaderImageReference(
                id = "image:$chapterIndex:${image.blockIndex}:${image.cfi.orEmpty()}:${image.path.hashCode()}:$index",
                index = index,
                source = image.path,
                altText = image.altText,
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title,
                blockIndex = image.blockIndex,
                cfi = image.cfi,
                intrinsicWidth = image.intrinsicWidth,
                intrinsicHeight = image.intrinsicHeight,
                locator = locator
            )
        }
        if (semanticImages.isEmpty()) {
            chapter.htmlContent.htmlReaderImageTags().forEachIndexed { imageIndex, image ->
                val index = references.size
                val locator = ReaderLocator(
                    chapterIndex = chapterIndex,
                    chapterId = chapter.id,
                    href = chapter.baseHref,
                    pageIndex = pages.firstOrNull { it.chapterIndex == chapterIndex }?.pageIndex,
                    textQuote = image.altText?.takeIf { it.isNotBlank() }
                )
                references += ReaderImageReference(
                    id = "html-image:$chapterIndex:${image.source.hashCode()}:$imageIndex",
                    index = index,
                    source = image.source,
                    altText = image.altText,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title,
                    blockIndex = imageIndex,
                    cfi = null,
                    intrinsicWidth = image.width,
                    intrinsicHeight = image.height,
                    locator = locator
                )
            }
        }
    }
    return references
}

private data class HtmlReaderImage(
    val source: String,
    val altText: String?,
    val width: Float?,
    val height: Float?
)

private fun String.htmlReaderImageTags(): List<HtmlReaderImage> {
    val imageTag = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    val sourceAttribute = Regex("""\bsrc\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    val altAttribute = Regex("""\balt\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    val widthAttribute = Regex("""\bwidth\s*=\s*(['"]?)([\d.]+)\1""", RegexOption.IGNORE_CASE)
    val heightAttribute = Regex("""\bheight\s*=\s*(['"]?)([\d.]+)\1""", RegexOption.IGNORE_CASE)
    return imageTag.findAll(this).mapNotNull { match ->
        val tag = match.value
        val source = sourceAttribute.find(tag)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        HtmlReaderImage(
            source = source,
            altText = altAttribute.find(tag)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
            width = widthAttribute.find(tag)?.groupValues?.getOrNull(2)?.toFloatOrNull(),
            height = heightAttribute.find(tag)?.groupValues?.getOrNull(2)?.toFloatOrNull()
        )
    }.toList()
}

private sealed interface ReaderImageMarker {
    data class Text(val startOffset: Int, val endOffset: Int) : ReaderImageMarker
    data class Image(val image: SemanticImage) : ReaderImageMarker
}

private fun List<SemanticBlock>.readerImageMarkers(): List<ReaderImageMarker> {
    return flatMap { it.readerImageMarkers() }
}

private fun SemanticBlock.readerImageMarkers(): List<ReaderImageMarker> {
    return when (this) {
        is SemanticTextBlock -> listOf(
            ReaderImageMarker.Text(
                startOffset = startCharOffsetInSource,
                endOffset = startCharOffsetInSource + text.length
            )
        )
        is SemanticImage -> listOf(ReaderImageMarker.Image(this))
        is SemanticList -> items.flatMap { it.readerImageMarkers() }
        is SemanticTable -> rows.flatMap { row -> row.flatMap { cell -> cell.content.readerImageMarkers() } }
        is SemanticFlexContainer -> children.readerImageMarkers()
        is SemanticWrappingBlock -> listOf(ReaderImageMarker.Image(floatedImage)) +
            paragraphsToWrap.flatMap { it.readerImageMarkers() }
        else -> emptyList()
    }
}

private fun List<ReaderImageMarker>.nearestTextOffsetFor(index: Int): Int? {
    val previous = asSequence()
        .take(index)
        .filterIsInstance<ReaderImageMarker.Text>()
        .lastOrNull()
        ?.endOffset
    if (previous != null) return previous
    return asSequence()
        .drop(index + 1)
        .filterIsInstance<ReaderImageMarker.Text>()
        .firstOrNull()
        ?.startOffset
}

private fun List<ReaderPage>.findPageIndexForImage(chapterIndex: Int, image: SemanticImage): Int? {
    return firstOrNull { page ->
        page.chapterIndex == chapterIndex && page.semanticBlocks.any { it.containsReaderImage(image) }
    }?.pageIndex
}

private fun SemanticBlock.containsReaderImage(target: SemanticImage): Boolean {
    return when (this) {
        is SemanticImage -> sameReaderImageAs(target)
        is SemanticList -> items.any { it.containsReaderImage(target) }
        is SemanticTable -> rows.any { row -> row.any { cell -> cell.content.any { it.containsReaderImage(target) } } }
        is SemanticFlexContainer -> children.any { it.containsReaderImage(target) }
        is SemanticWrappingBlock -> floatedImage.sameReaderImageAs(target) ||
            paragraphsToWrap.any { it.containsReaderImage(target) }
        else -> false
    }
}

private fun SemanticImage.sameReaderImageAs(other: SemanticImage): Boolean {
    val thisCfi = cfi?.takeIf { it.isNotBlank() }
    val otherCfi = other.cfi?.takeIf { it.isNotBlank() }
    if (thisCfi != null && otherCfi != null) return thisCfi == otherCfi

    val thisElementId = elementId?.takeIf { it.isNotBlank() }
    val otherElementId = other.elementId?.takeIf { it.isNotBlank() }
    if (thisElementId != null && otherElementId != null) return thisElementId == otherElementId

    return blockIndex == other.blockIndex && path == other.path
}

private fun String.readerImageExtension(): String? {
    parseSharedEpubResourceUrl(this)?.let { reference ->
        return reference.entryPath
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif") }
    }
    val dataMime = Regex("""^data:([^;,]+)""", RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
    val extensionFromMime = when (dataMime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        "image/svg+xml" -> "svg"
        else -> null
    }
    if (extensionFromMime != null) return extensionFromMime
    return substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") }
}

private fun String.sanitizedReaderImageFileBase(): String {
    return replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('.')
        .take(80)
}
