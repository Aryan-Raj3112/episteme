package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal const val TAG_PAGINATED_LINK_DIAG = "PaginatedLinkDiag"

private const val LINK_DIAG_MAX_SAMPLES = 4
private val linkDiagWhitespaceRegex = Regex("\\s+")

internal fun Document.readerHtmlLinkDiagSummary(): String {
    val linkElements = getElementsByTag("a").mapNotNull { element ->
        element.readerHrefForDiagnostics()?.let { href -> element to href }
    }
    val samples = linkElements.take(LINK_DIAG_MAX_SAMPLES).joinToString(
        prefix = "[",
        postfix = "]"
    ) { (element, href) ->
        "href=${href.readerLinkDiagPreview()} text=\"${element.text().readerLinkDiagPreview()}\""
    }
    return "htmlAnchors=${linkElements.size} htmlSamples=$samples"
}

internal fun List<SemanticBlock>.readerSemanticLinkDiagSummary(): String {
    val collector = ReaderLinkDiagCollector()
    forEach { it.collectSemanticLinks(collector) }
    return collector.semanticSummary()
}

internal fun List<ContentBlock>.readerContentLinkDiagSummary(): String {
    val collector = ReaderLinkDiagCollector()
    forEach { it.collectContentLinks(collector, pageInChapter = null) }
    return collector.contentSummary()
}

internal fun Page.readerPageLinkDiagSummary(): String {
    val collector = ReaderLinkDiagCollector()
    content.forEach { it.collectContentLinks(collector, pageInChapter = null) }
    return collector.contentSummary()
}

internal fun List<Page>.readerPagesLinkDiagSummary(): String {
    val collector = ReaderLinkDiagCollector()
    forEachIndexed { pageInChapter, page ->
        page.content.forEach { it.collectContentLinks(collector, pageInChapter) }
    }
    return "pages=$size ${collector.contentSummary()}"
}

internal fun AnnotatedString.readerAnnotatedLinkDiagSummary(): String {
    val collector = ReaderLinkDiagCollector()
    collector.addAnnotatedLinks(
        blockIndex = null,
        blockType = "AnnotatedString",
        cfi = null,
        text = this,
        pageInChapter = null
    )
    return collector.contentSummary()
}

internal fun String.readerLinkDiagPreview(maxLength: Int = 96): String {
    val cleaned = replace(linkDiagWhitespaceRegex, " ").trim()
    return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 3) + "..."
}

private fun Element.readerHrefForDiagnostics(): String? {
    return attr("href")
        .ifBlank { attr("xlink:href") }
        .ifBlank { attr("l:href") }
        .ifBlank { attr("epub:href") }
        .ifBlank { null }
}

private class ReaderLinkDiagCollector {
    private var semanticTextBlocks = 0
    private var semanticLinkSpans = 0
    private val semanticSamples = mutableListOf<String>()

    private var contentTextBlocks = 0
    private var contentUrlAnnotations = 0
    private var contentLinksWithColor = 0
    private var contentLinksWithBackground = 0
    private var contentLinksWithUnderline = 0
    private var contentLinksWithCoveringStyle = 0
    private val contentSamples = mutableListOf<String>()

    fun addSemanticTextBlock(block: SemanticTextBlock) {
        semanticTextBlocks++
        block.spans.forEach { span ->
            val href = span.linkHref?.takeIf { it.isNotBlank() } ?: return@forEach
            semanticLinkSpans++
            if (semanticSamples.size < LINK_DIAG_MAX_SAMPLES) {
                val start = span.start.coerceIn(0, block.text.length)
                val end = span.end.coerceIn(start, block.text.length)
                semanticSamples += buildString {
                    append("block=")
                    append(block.blockIndex)
                    append(" type=")
                    append(block::class.simpleName ?: "Text")
                    append(" tag=")
                    append(span.tag)
                    append(" range=")
                    append(start)
                    append("..")
                    append(end)
                    append(" href=")
                    append(href.readerLinkDiagPreview())
                    append(" text=\"")
                    append(block.text.substring(start, end).readerLinkDiagPreview())
                    append("\"")
                }
            }
        }
    }

    fun addAnnotatedLinks(
        blockIndex: Int?,
        blockType: String,
        cfi: String?,
        text: AnnotatedString,
        pageInChapter: Int?
    ) {
        contentTextBlocks++
        val annotations = text.getStringAnnotations("URL", 0, text.length)
            .filter { it.item.isNotBlank() }
        annotations.forEach { annotation ->
            contentUrlAnnotations++
            val coverage = text.readerLinkStyleCoverage(annotation)
            if (coverage.hasColor) contentLinksWithColor++
            if (coverage.hasBackground) contentLinksWithBackground++
            if (coverage.hasUnderline) contentLinksWithUnderline++
            if (coverage.hasCoveringStyle) contentLinksWithCoveringStyle++
            if (contentSamples.size < LINK_DIAG_MAX_SAMPLES) {
                contentSamples += buildString {
                    if (pageInChapter != null) {
                        append("pageInChapter=")
                        append(pageInChapter)
                        append(" ")
                    }
                    append("block=")
                    append(blockIndex ?: -1)
                    append(" type=")
                    append(blockType)
                    if (!cfi.isNullOrBlank()) {
                        append(" cfi=")
                        append(cfi)
                    }
                    append(" range=")
                    append(annotation.start)
                    append("..")
                    append(annotation.end)
                    append(" href=")
                    append(annotation.item.readerLinkDiagPreview())
                    append(" style={color=")
                    append(coverage.hasColor)
                    append(",bg=")
                    append(coverage.hasBackground)
                    append(",underline=")
                    append(coverage.hasUnderline)
                    append(",covering=")
                    append(coverage.hasCoveringStyle)
                    append("} text=\"")
                    append(
                        text.text.substring(
                            annotation.start.coerceIn(0, text.length),
                            annotation.end.coerceIn(annotation.start.coerceIn(0, text.length), text.length)
                        ).readerLinkDiagPreview()
                    )
                    append("\"")
                }
            }
        }
    }

    fun semanticSummary(): String {
        return "semanticTextBlocks=$semanticTextBlocks semanticLinkSpans=$semanticLinkSpans semanticSamples=${semanticSamples.joinToString(prefix = "[", postfix = "]")}"
    }

    fun contentSummary(): String {
        return "contentTextBlocks=$contentTextBlocks urlAnnotations=$contentUrlAnnotations styled={color=$contentLinksWithColor,bg=$contentLinksWithBackground,underline=$contentLinksWithUnderline,covering=$contentLinksWithCoveringStyle} contentSamples=${contentSamples.joinToString(prefix = "[", postfix = "]")}"
    }
}

private data class ReaderLinkStyleCoverage(
    val hasColor: Boolean,
    val hasBackground: Boolean,
    val hasUnderline: Boolean,
    val hasCoveringStyle: Boolean
)

private fun AnnotatedString.readerLinkStyleCoverage(
    link: AnnotatedString.Range<String>
): ReaderLinkStyleCoverage {
    val overlappingStyles = spanStyles.filter { styleRange ->
        styleRange.start < link.end && styleRange.end > link.start
    }
    return ReaderLinkStyleCoverage(
        hasColor = overlappingStyles.any { it.item.color.isSpecified },
        hasBackground = overlappingStyles.any { it.item.background.isSpecified },
        hasUnderline = overlappingStyles.any {
            it.item.textDecoration?.contains(TextDecoration.Underline) == true
        },
        hasCoveringStyle = overlappingStyles.any {
            it.start <= link.start && it.end >= link.end
        }
    )
}

private fun SemanticBlock.collectSemanticLinks(collector: ReaderLinkDiagCollector) {
    when (this) {
        is SemanticList -> items.forEach { it.collectSemanticLinks(collector) }
        is SemanticTable -> rows.flatten().forEach { cell ->
            cell.content.forEach { it.collectSemanticLinks(collector) }
        }
        is SemanticFlexContainer -> children.forEach { it.collectSemanticLinks(collector) }
        is SemanticWrappingBlock -> paragraphsToWrap.forEach { it.collectSemanticLinks(collector) }
        is SemanticTextBlock -> collector.addSemanticTextBlock(this)
        else -> Unit
    }
}

private fun ContentBlock.collectContentLinks(
    collector: ReaderLinkDiagCollector,
    pageInChapter: Int?
) {
    when (this) {
        is WrappingContentBlock -> paragraphsToWrap.forEach {
            it.collectContentLinks(collector, pageInChapter)
        }
        is TableBlock -> rows.flatten().forEach { cell ->
            cell.content.forEach { it.collectContentLinks(collector, pageInChapter) }
        }
        is FlexContainerBlock -> children.forEach { it.collectContentLinks(collector, pageInChapter) }
        is TextContentBlock -> collector.addAnnotatedLinks(
            blockIndex = blockIndex,
            blockType = this::class.simpleName ?: "Text",
            cfi = cfi,
            text = content,
            pageInChapter = pageInChapter
        )
        else -> Unit
    }
}
