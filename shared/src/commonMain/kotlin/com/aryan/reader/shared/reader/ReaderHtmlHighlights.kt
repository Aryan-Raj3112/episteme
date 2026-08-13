package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import kotlin.math.roundToInt

internal fun String.applyUserHighlights(
    highlights: List<UserHighlight>,
    contentStartOffset: Int,
    contentEndOffset: Int
): String {
    val rangedHighlights = highlights
        .mapNotNull { it.toRenderHighlight(this, contentStartOffset, contentEndOffset) }
        .distinctBy { "${it.absoluteStart}:${it.absoluteEnd}:${it.id}" }
        .sortedWith(compareByDescending<RenderedHighlight> { it.relativeStart }.thenByDescending { it.relativeEnd })
    val rangedHighlightIds = rangedHighlights.map { it.id }.toSet()

    val rangedHtml = rangedHighlights.fold(this) { html, highlight ->
        val markerStart = """<span class="reader-user-highlight ${highlight.color.cssClass}"${highlight.highlightAttributes()} data-reader-highlight-id="${highlight.id.escapeHtml()}" data-cfi="${highlight.cfi.escapeHtml()}" data-reader-start-offset="${highlight.absoluteStart}" data-reader-end-offset="${highlight.absoluteEnd}">"""
        html.htmlRangesForHighlight(highlight)
            .sortedByDescending { it.first }
            .fold(html) { current, htmlRange ->
                val startIndex = htmlRange.first
                val endIndex = htmlRange.last
                if (startIndex >= endIndex || endIndex > current.length) return@fold current
                val markedText = current.substring(startIndex, endIndex)
                if (markedText.visibleHtmlText().isBlank()) return@fold current
                current.replaceRange(startIndex, endIndex, markedText.wrapVisibleHtmlText(markerStart, "</span>"))
            }
    }

    return highlights
        .filterNot { it.id in rangedHighlightIds }
        .filterNot { it.locator.withFallbacks(chapterIndex = it.chapterIndex, cfi = it.cfi, textQuote = it.text).hasTextRange }
        .fold(rangedHtml) { html, highlight ->
            val text = highlight.text.trim().takeIf { it.isNotBlank() } ?: return@fold html
            val escapedText = text.escapeHtml()
            val markedText = """<span class="reader-user-highlight ${highlight.color.cssClass}"${highlight.highlightAttributes()} data-reader-highlight-id="${highlight.id.escapeHtml()}" data-cfi="${highlight.cfi.escapeHtml()}">$escapedText</span>"""
            html.replaceFirst(escapedText, markedText)
        }
}

internal fun RenderedHighlight.highlightAttributes(): String {
    return highlightAttributes(style, colorArgb)
}

internal fun UserHighlight.highlightAttributes(): String {
    return highlightAttributes(style, colorArgb)
}

internal fun highlightAttributes(style: HighlightStyle, colorArgb: Int?): String {
    val declarations = highlightStyleDeclarations(style, colorArgb)
    val styleAttribute = declarations.takeIf { it.isNotBlank() }?.let { " style=\"$it\"" }.orEmpty()
    return "$styleAttribute data-reader-highlight-style=\"${style.id}\""
}

internal fun highlightStyleDeclarations(style: HighlightStyle, colorArgb: Int?): String {
    val rgb = colorArgb?.let { it and 0x00FFFFFF }
    val colorCss = rgb?.let { "#${it.toString(16).padStart(6, '0').uppercase()}" }
    return when (style) {
        HighlightStyle.BACKGROUND -> colorCss?.let { "background-color:$it !important" }.orEmpty()
        HighlightStyle.UNDERLINE -> highlightLineStyle(colorCss, "underline", "solid")
        HighlightStyle.WAVY_UNDERLINE -> highlightLineStyle(colorCss, "underline", "wavy")
        HighlightStyle.STRIKETHROUGH -> highlightLineStyle(colorCss, "line-through", "solid")
    }
}

internal fun highlightLineStyle(colorCss: String?, line: String, decorationStyle: String): String {
    val colorDeclaration = colorCss?.let { "; text-decoration-color:$it !important" }.orEmpty()
    return "background-color:transparent !important; text-decoration-line:$line !important; text-decoration-style:$decorationStyle !important$colorDeclaration"
}

internal fun String.wrapVisibleHtmlText(markerStart: String, markerEnd: String): String {
    val output = StringBuilder(length + markerStart.length + markerEnd.length)
    var index = 0
    var markerOpen = false

    fun openMarker() {
        if (!markerOpen) {
            output.append(markerStart)
            markerOpen = true
        }
    }

    fun closeMarker() {
        if (markerOpen) {
            output.append(markerEnd)
            markerOpen = false
        }
    }

    while (index < length) {
        when (this[index]) {
            '<' -> {
                closeMarker()
                val tagEnd = indexOf('>', startIndex = index + 1)
                if (tagEnd < 0) {
                    openMarker()
                    output.append(this[index])
                    index++
                } else {
                    output.append(substring(index, tagEnd + 1))
                    index = tagEnd + 1
                }
            }

            '&' -> {
                openMarker()
                val entityEnd = indexOf(';', startIndex = index + 1)
                if (entityEnd > index) {
                    output.append(substring(index, entityEnd + 1))
                    index = entityEnd + 1
                } else {
                    output.append(this[index])
                    index++
                }
            }

            else -> {
                val nextTag = indexOf('<', startIndex = index).takeIf { it >= 0 } ?: length
                val nextEntity = indexOf('&', startIndex = index).takeIf { it >= 0 } ?: length
                val nextBoundary = minOf(nextTag, nextEntity)
                val textRun = substring(index, nextBoundary)
                if (textRun.isBlank()) {
                    output.append(textRun)
                } else {
                    openMarker()
                    output.append(textRun)
                }
                index = nextBoundary
            }
        }
    }
    closeMarker()
    return output.toString()
}

internal fun String.visibleHtmlText(): String {
    val output = StringBuilder(length)
    var index = 0
    while (index < length) {
        when (this[index]) {
            '<' -> {
                val tagEnd = indexOf('>', startIndex = index + 1)
                index = if (tagEnd < 0) index + 1 else tagEnd + 1
            }

            '&' -> {
                output.append('x')
                val entityEnd = indexOf(';', startIndex = index + 1)
                index = if (entityEnd > index) entityEnd + 1 else index + 1
            }

            else -> {
                output.append(this[index])
                index++
            }
        }
    }
    return output.toString()
}

internal fun String.htmlRangesForHighlight(highlight: RenderedHighlight): List<IntRange> {
    val overlappingBlocks = textBlockStartPattern.findAll(this).mapNotNull { match ->
        val tagName = match.groupValues[1]
        val blockStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val blockEnd = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        val overlapStart = maxOf(highlight.absoluteStart, blockStart)
        val overlapEnd = minOf(highlight.absoluteEnd, blockEnd)
        if (overlapStart >= overlapEnd) return@mapNotNull null
        val contentStart = match.range.last + 1
        val contentEnd = indexOf("</$tagName>", startIndex = contentStart, ignoreCase = true)
        if (contentEnd < contentStart) return@mapNotNull null
        val startIndex = htmlIndexForTextOffset(overlapStart - blockStart, contentStart, contentEnd) ?: return@mapNotNull null
        val endIndex = htmlIndexForTextOffset(overlapEnd - blockStart, contentStart, contentEnd) ?: return@mapNotNull null
        startIndex..endIndex
    }.toList()
    if (overlappingBlocks.isNotEmpty()) return overlappingBlocks

    val block = findTextBlockRange(highlight.absoluteStart, highlight.absoluteEnd)
    if (block != null) {
        val startIndex = htmlIndexForTextOffset(
            targetOffset = highlight.absoluteStart - block.startOffset,
            startIndex = block.contentStartIndex,
            endIndex = block.contentEndIndex
        ) ?: return emptyList()
        val endIndex = htmlIndexForTextOffset(
            targetOffset = highlight.absoluteEnd - block.startOffset,
            startIndex = block.contentStartIndex,
            endIndex = block.contentEndIndex
        ) ?: return emptyList()
        return listOf(startIndex..endIndex)
    }
    val startIndex = htmlIndexForTextOffset(highlight.relativeStart) ?: return emptyList()
    val endIndex = htmlIndexForTextOffset(highlight.relativeEnd) ?: return emptyList()
    return listOf(startIndex..endIndex)
}

internal fun String.findTextBlockRange(absoluteStart: Int, absoluteEnd: Int): HtmlTextBlockRange? {
    return textBlockStartPattern.findAll(this).mapNotNull { match ->
        val tagName = match.groupValues[1]
        val blockStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val blockEnd = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        if (absoluteStart < blockStart || absoluteEnd > blockEnd) return@mapNotNull null
        val contentStart = match.range.last + 1
        val closingTag = "</$tagName>"
        val contentEnd = indexOf(closingTag, startIndex = contentStart, ignoreCase = true)
        if (contentEnd < contentStart) return@mapNotNull null
        HtmlTextBlockRange(
            startOffset = blockStart,
            endOffset = blockEnd,
            contentStartIndex = contentStart,
            contentEndIndex = contentEnd
        )
    }.firstOrNull()
}

internal fun String.findTextBlockRangeByCfi(cfiPath: String): HtmlTextBlockRange? {
    return findTextBlockRangeByAttribute("data-reader-cfi", cfiPath)
}

internal fun String.findTextBlockRangeByBlockIndex(blockIndex: Int): HtmlTextBlockRange? {
    return findTextBlockRangeByAttribute("data-reader-block-index", blockIndex.toString())
}

internal fun String.findTextBlockRangeByAttribute(attributeName: String, attributeValue: String): HtmlTextBlockRange? {
    return textBlockStartPattern.findAll(this).mapNotNull { match ->
        val openingTag = substring(match.range.first, match.range.last + 1)
        if (openingTag.htmlAttributeValue(attributeName) != attributeValue) return@mapNotNull null
        val tagName = match.groupValues[1]
        val blockStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val blockEnd = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        val contentStart = match.range.last + 1
        val closingTag = "</$tagName>"
        val contentEnd = indexOf(closingTag, startIndex = contentStart, ignoreCase = true)
        if (contentEnd < contentStart) return@mapNotNull null
        HtmlTextBlockRange(
            startOffset = blockStart,
            endOffset = blockEnd,
            contentStartIndex = contentStart,
            contentEndIndex = contentEnd
        )
    }.firstOrNull()
}

internal fun String.htmlAttributeValue(attributeName: String): String? {
    return Regex("""\b${Regex.escape(attributeName)}="([^"]*)"""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
}

internal fun String.htmlIndexForTextOffset(
    targetOffset: Int,
    startIndex: Int = 0,
    endIndex: Int = length
): Int? {
    if (targetOffset < 0) return null
    var index = startIndex.coerceIn(0, length)
    val limit = endIndex.coerceIn(index, length)
    var textOffset = 0
    var boundaryAfterText: Int? = null
    while (index < limit) {
        when (this[index]) {
            '<' -> {
                val tagEnd = indexOf('>', startIndex = index + 1)
                if (tagEnd < 0 || tagEnd >= limit) return null
                index = tagEnd + 1
            }

            '&' -> {
                if (textOffset == targetOffset) return index
                val entityEnd = indexOf(';', startIndex = index + 1)
                if (entityEnd > index) {
                    textOffset++
                    index = entityEnd + 1
                } else {
                    textOffset++
                    index++
                }
                boundaryAfterText = index
            }

            else -> {
                if (textOffset == targetOffset) return index
                textOffset++
                index++
                boundaryAfterText = index
            }
        }
    }
    return if (textOffset == targetOffset) boundaryAfterText ?: startIndex else null
}

internal fun UserHighlight.toRenderHighlight(
    html: String,
    contentStartOffset: Int,
    contentEndOffset: Int
): RenderedHighlight? {
    val normalizedLocator = locator.withFallbacks(chapterIndex = chapterIndex, cfi = cfi, textQuote = text)
    val sourceCfi = normalizedLocator.cfi ?: cfi
    html.absoluteRangeForSourceCfi(sourceCfi, normalizedLocator.textQuote ?: text)
        ?.toRenderedHighlight(
            source = this,
            cfi = sourceCfi,
            contentStartOffset = contentStartOffset,
            contentEndOffset = contentEndOffset
        )
        ?.let { return it }
    html.absoluteRangeForBlockLocator(normalizedLocator, normalizedLocator.textQuote ?: text)
        ?.toRenderedHighlight(
            source = this,
            cfi = sourceCfi,
            contentStartOffset = contentStartOffset,
            contentEndOffset = contentEndOffset
        )
        ?.let { return it }
    val start = normalizedLocator.startOffset ?: return null
    val end = normalizedLocator.endOffset ?: start
    if (end < start) return null
    return HtmlAbsoluteTextRange(start, end).toRenderedHighlight(
        source = this,
        cfi = sourceCfi,
        contentStartOffset = contentStartOffset,
        contentEndOffset = contentEndOffset
    )
}

internal fun HtmlAbsoluteTextRange.toRenderedHighlight(
    source: UserHighlight,
    cfi: String,
    contentStartOffset: Int,
    contentEndOffset: Int
): RenderedHighlight? {
    if (end < start) return null
    val boundedStart = start.coerceAtLeast(contentStartOffset)
    val boundedEnd = end.coerceAtMost(contentEndOffset)
    if (boundedEnd <= boundedStart) return null
    return RenderedHighlight(
        id = source.id,
        cfi = cfi,
        color = source.color,
        colorArgb = source.colorArgb,
        style = source.style,
        absoluteStart = boundedStart,
        absoluteEnd = boundedEnd,
        relativeStart = boundedStart - contentStartOffset,
        relativeEnd = boundedEnd - contentStartOffset
    )
}

internal fun String.absoluteRangeForSourceCfi(cfi: String?, quote: String): HtmlAbsoluteTextRange? {
    val points = cfi
        ?.takeIf { it.startsWith("/") || it.contains("|/") }
        ?.split('|')
        ?.mapNotNull { it.toHtmlSourceCfiPointOrNull() }
        ?: return null
    val startPoint = points.firstOrNull() ?: return null
    val endPoint = points.lastOrNull() ?: startPoint
    val startBlock = findTextBlockRangeByCfi(startPoint.path) ?: return null
    val endBlock = findTextBlockRangeByCfi(endPoint.path) ?: startBlock
    val start = startBlock.htmlCfiOffsetToAbsolute(startPoint.offset)
    var end = endBlock.htmlCfiOffsetToAbsolute(endPoint.offset)
    if (end == start && quote.isNotBlank()) {
        end = start + quote.length
    }
    return if (end > start) HtmlAbsoluteTextRange(start, end) else null
}

internal fun String.absoluteRangeForBlockLocator(locator: ReaderLocator, quote: String): HtmlAbsoluteTextRange? {
    val blockIndex = locator.blockIndex ?: return null
    val block = findTextBlockRangeByBlockIndex(blockIndex) ?: return null
    val blockLength = block.endOffset - block.startOffset
    val startOffset = locator.startOffset ?: locator.charOffset ?: return null
    val rawEnd = locator.endOffset
        ?: quote.takeIf { it.isNotBlank() }?.let { startOffset + it.length }
        ?: return null
    val start = block.htmlScopedOffsetToAbsoluteOrNull(startOffset, blockLength) ?: return null
    val end = block.htmlScopedOffsetToAbsoluteOrNull(rawEnd, blockLength) ?: return null
    return if (end > start) HtmlAbsoluteTextRange(start, end) else null
}

internal fun HtmlTextBlockRange.htmlCfiOffsetToAbsolute(offset: Int): Int {
    val blockLength = endOffset - startOffset
    return when {
        offset in 0..blockLength -> startOffset + offset
        offset in startOffset..endOffset -> offset
        else -> startOffset + offset.coerceIn(0, blockLength)
    }
}

internal fun HtmlTextBlockRange.htmlScopedOffsetToAbsoluteOrNull(offset: Int, blockLength: Int): Int? {
    return when {
        offset in 0..blockLength -> startOffset + offset
        offset in startOffset..endOffset -> offset
        else -> null
    }
}

internal fun UserHighlight.belongsToPage(page: ReaderPage): Boolean {
    val normalizedLocator = locator.withFallbacks(chapterIndex = chapterIndex, cfi = cfi, textQuote = text)
    val locatorChapterIndex = normalizedLocator.chapterIndex ?: chapterIndex
    if (locatorChapterIndex != page.chapterIndex) return false
    return page.containsHighlightLocator(normalizedLocator, cfi)
}

internal fun ReaderPage.containsHighlightLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
    if (containsBlockLocator(locator)) return true
    if (containsSourceCfiLocator(locator, fallbackCfi)) return true
    if (locator.hasTextRange) {
        if (locator.hasHtmlStructuralScope(fallbackCfi)) return false
        val start = locator.startOffset ?: return false
        val end = locator.endOffset ?: start
        return if (start == end) {
            start in startOffset..endOffset
        } else {
            start < endOffset && end > startOffset
        }
    }
    locator.pageIndex?.let { return it == pageIndex }
    val prefix = "desktop:${chapterIndex}:"
    val desktopPageIndex = fallbackCfi
        .takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.substringBefore(':')
        ?.toIntOrNull()
    return desktopPageIndex != null && desktopPageIndex >= 0 && desktopPageIndex == pageIndex
}

internal fun ReaderLocator.hasHtmlStructuralScope(fallbackCfi: String): Boolean {
    val sourceCfi = cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi
    return blockIndex != null || sourceCfi.startsWith("/")
}

internal fun ReaderPage.containsBlockLocator(locator: ReaderLocator): Boolean {
    val blockIndex = locator.blockIndex ?: return false
    val blocks = semanticBlocks.flattenHtmlSemanticBlocks()
    if (blocks.isEmpty()) return false
    val matchingBlocks = blocks.filter { it.blockIndex == blockIndex }
    if (matchingBlocks.isEmpty()) return false
    val charOffset = locator.charOffset ?: return true
    val offsetFallsOnPage = if (startOffset == endOffset) {
        charOffset == startOffset
    } else {
        charOffset >= startOffset && charOffset < endOffset
    }
    if (!offsetFallsOnPage) return false
    return matchingBlocks.filterIsInstance<SemanticTextBlock>().any { block ->
        val start = block.startCharOffsetInSource
        val end = start + block.text.length
        charOffset in start until end || (block.text.isEmpty() && charOffset == start)
    }
}

internal fun ReaderPage.containsSourceCfiLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
    val cfi = (locator.cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi)
        .takeIf { it.startsWith("/") || it.contains("|/") }
        ?: return false
    val blocks = semanticBlocks.flattenHtmlSemanticBlocks().filterIsInstance<SemanticTextBlock>()
    if (blocks.isEmpty()) return false
    val parts = cfi.split('|').mapNotNull { it.toHtmlSourceCfiPointOrNull() }
    val startPoint = parts.firstOrNull() ?: return false
    val endPoint = parts.lastOrNull() ?: startPoint
    val quoteLength = locator.textQuote?.length ?: 0
    return blocks.any { block ->
        val blockPath = block.cfi?.substringBefore(':')?.takeIf { it.startsWith("/") } ?: return@any false
        val startMatches = htmlSourceCfiPathsEquivalent(startPoint.path, blockPath)
        val endMatches = htmlSourceCfiPathsEquivalent(endPoint.path, blockPath)
        val isIntermediate = parts.size > 1 &&
            !startMatches &&
            !endMatches &&
            htmlSourceCfiPathStrictlyBetween(blockPath, startPoint.path, endPoint.path)
        if (!startMatches && !endMatches && !isIntermediate) return@any false
        val blockStart = block.startCharOffsetInSource
        val blockEnd = blockStart + block.text.length
        val rangeStart = when {
            startMatches -> htmlSourceCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length)
            isIntermediate || endMatches -> blockStart
            else -> blockStart
        }
        val rangeEnd = when {
            endMatches && parts.size > 1 -> htmlSourceCfiOffsetToAbsolute(endPoint.offset, blockStart, block.text.length)
            startMatches && parts.size == 1 && quoteLength > 0 ->
                htmlSourceCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length) + quoteLength
            startMatches && parts.size == 1 -> htmlSourceCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length)
            isIntermediate -> blockEnd
            else -> blockEnd
        }
        if (rangeStart == rangeEnd) {
            rangeStart in startOffset..endOffset
        } else {
            minOf(rangeStart, rangeEnd) < endOffset && maxOf(rangeStart, rangeEnd) > startOffset
        }
    }
}

internal fun htmlSourceCfiOffsetToAbsolute(offset: Int, blockStart: Int, textLength: Int): Int {
    val blockEnd = blockStart + textLength
    return when {
        offset in 0..textLength -> blockStart + offset
        offset in blockStart..blockEnd -> offset
        else -> blockStart + offset.coerceIn(0, textLength)
    }
}

internal data class HtmlSourceCfiPoint(
    val path: String,
    val offset: Int
)

internal fun String.toHtmlSourceCfiPointOrNull(): HtmlSourceCfiPoint? {
    val value = trim()
    if (!value.startsWith("/")) return null
    val separator = value.lastIndexOf(':')
    return if (separator > 0 && separator < value.lastIndex) {
        HtmlSourceCfiPoint(value.substring(0, separator), value.substring(separator + 1).toIntOrNull() ?: 0)
    } else {
        HtmlSourceCfiPoint(value, 0)
    }
}

internal fun htmlSourceCfiPathsEquivalent(first: String, second: String): Boolean {
    if (first == second || first.startsWith("$second/") || second.startsWith("$first/")) return true
    val firstParts = first.split('/').filter { it.isNotEmpty() }
    val secondParts = second.split('/').filter { it.isNotEmpty() }
    if (firstParts == secondParts) return true
    return firstParts.size == secondParts.size &&
        firstParts.isNotEmpty() &&
        firstParts.drop(1) == secondParts.drop(1)
}

internal fun htmlSourceCfiPathStrictlyBetween(candidate: String, start: String, end: String): Boolean {
    val candidateParts = candidate.htmlSourceCfiNumericPathParts() ?: return false
    val startParts = start.htmlSourceCfiNumericPathParts() ?: return false
    val endParts = end.htmlSourceCfiNumericPathParts() ?: return false
    return htmlSourceCfiComparePathParts(candidateParts, startParts) > 0 &&
        htmlSourceCfiComparePathParts(candidateParts, endParts) < 0
}

internal fun String.htmlSourceCfiNumericPathParts(): List<Int>? {
    val parts = split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

internal fun htmlSourceCfiComparePathParts(first: List<Int>, second: List<Int>): Int {
    val length = minOf(first.size, second.size)
    for (index in 0 until length) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
}

internal fun List<SemanticBlock>.flattenHtmlSemanticBlocks(): List<SemanticBlock> {
    return flatMap { it.flattenHtmlSemanticBlock() }
}

internal fun SemanticBlock.flattenHtmlSemanticBlock(): List<SemanticBlock> {
    return when (this) {
        is SemanticList -> listOf(this) + items
        is SemanticTable -> listOf(this) + rows.flatMap { row -> row.flatMap { cell -> cell.content.flattenHtmlSemanticBlocks() } }
        is SemanticFlexContainer -> listOf(this) + children.flattenHtmlSemanticBlocks()
        is SemanticWrappingBlock -> listOf(this, floatedImage) + paragraphsToWrap
        is SemanticImage,
        is SemanticMath,
        is SemanticSpacer,
        is SemanticTextBlock -> listOf(this)
    }
}

internal val UserHighlight.locatedChapterIndex: Int
    get() = locator.chapterIndex ?: chapterIndex

internal fun ReaderLocator.toNavigationAttributes(): String {
    val attributes = buildList {
        chapterIndex?.let { add("data-reader-active-chapter-index=\"$it\"") }
        pageIndex?.let { add("data-reader-active-page-index=\"$it\"") }
        startOffset?.let { add("data-reader-active-start-offset=\"$it\"") }
        endOffset?.let { add("data-reader-active-end-offset=\"$it\"") }
        blockIndex?.let { add("data-reader-active-block-index=\"$it\"") }
        charOffset?.let { add("data-reader-active-char-offset=\"$it\"") }
        cfi?.takeIf { it.isNotBlank() }?.let { add("data-reader-active-cfi=\"${it.escapeHtml()}\"") }
    }
    return if (attributes.isEmpty()) "" else " " + attributes.joinToString(" ")
}

internal fun List<ReaderPage>.toPageAnchorJson(): String {
    if (isEmpty()) return "[]"
    return joinToString(prefix = "[", postfix = "]") { page ->
        """{"pageIndex":${page.pageIndex},"chapterIndex":${page.chapterIndex},"startOffset":${page.startOffset},"endOffset":${page.endOffset}}"""
    }
}

internal fun readerSelectionActionButton(action: String, label: String, pathData: String): String {
    val safeLabel = label.escapeHtml()
    return """<button type="button" class="reader-selection-action" data-action="${action.escapeHtml()}" aria-label="$safeLabel"><span class="reader-selection-icon" aria-hidden="true">${readerSelectionSvg(pathData)}</span><span>$safeLabel</span></button>"""
}

internal fun ReaderHighlightPalette.toSelectionColorButtons(): String {
    return sanitized().colors.joinToString("\n") { color ->
        """<button type="button" class="reader-selection-color" data-action="highlight" data-color-id="${color.id}" title="Highlight ${color.id.escapeHtml()}" style="--selection-color:${color.color.toCssHex()}"><span></span></button>"""
    }
}

internal fun ReaderHighlightPalette.toSelectionPaletteButtons(): String {
    return listOf(
        toSelectionColorButtons(),
        """<button type="button" class="reader-selection-spectrum" data-action="palette" title="Customize highlight palette" aria-label="Customize highlight palette"><span></span></button>"""
    ).joinToString("\n")
}

internal fun readerSelectionSvg(pathData: String): String {
    return """<svg viewBox="0 0 960 960" focusable="false" aria-hidden="true"><path d="$pathData"></path></svg>"""
}

internal data class TextSegment(
    val text: String,
    val startOffset: Int
)

internal data class RenderedHighlight(
    val id: String,
    val cfi: String,
    val color: HighlightColor,
    val colorArgb: Int?,
    val style: HighlightStyle,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val relativeStart: Int,
    val relativeEnd: Int
)

internal data class HtmlTextBlockRange(
    val startOffset: Int,
    val endOffset: Int,
    val contentStartIndex: Int,
    val contentEndIndex: Int
)

internal data class HtmlAbsoluteTextRange(
    val start: Int,
    val end: Int
)

internal val textBlockStartPattern = Regex(
    """<([A-Za-z][A-Za-z0-9]*)\b[^>]*\bdata-reader-text-start="(\d+)"[^>]*\bdata-reader-text-end="(\d+)"[^>]*>"""
)

internal const val ReaderSelectionIconCopyPath =
    "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640L720,160L360,160L360,640ZM200,880Q167,880 143.5,856.5Q120,833 120,800L120,240L200,240L200,800L640,800L640,880L200,880Z"
internal const val ReaderSelectionIconDefinePath =
    "M480,800Q432,762 376,741Q320,720 260,720Q218,720 177.5,731Q137,742 100,762Q79,773 59.5,761Q40,749 40,726L40,244Q40,233 45.5,223Q51,213 62,208Q108,184 158,172Q208,160 260,160Q318,160 373.5,175Q429,190 480,220Q531,190 586.5,175Q642,160 700,160Q752,160 802,172Q852,184 898,208Q909,213 914.5,223Q920,233 920,244L920,726Q920,749 900.5,761Q881,773 860,762Q823,742 782.5,731Q742,720 700,720Q640,720 584,741Q528,762 480,800ZM520,682Q564,661 608.5,650.5Q653,640 700,640Q736,640 770.5,646Q805,652 840,664L840,268Q807,254 771.5,247Q736,240 700,240Q653,240 607,252Q561,264 520,288L520,682ZM440,682L440,288Q399,264 353,252Q307,240 260,240Q224,240 188.5,247Q153,254 120,268L120,664Q155,652 189.5,646Q224,640 260,640Q307,640 351.5,650.5Q396,661 440,682Z"
internal const val ReaderSelectionIconSpeakPath =
    "M560,828L560,746Q653,719 706.5,642Q760,565 760,466Q760,367 706.5,290Q653,213 560,186L560,104Q687,133 763.5,234Q840,335 840,466Q840,597 763.5,698Q687,799 560,828ZM120,600L120,360L280,360L480,160L480,800L280,600L120,600ZM560,640L560,292Q612,317 646,364.5Q680,412 680,466Q680,520 646,567.5Q612,615 560,640Z"
internal const val ReaderSelectionIconTranslatePath =
    "M440,800L600,400L760,800L685,800L645,690L555,690L515,800L440,800ZM578,625L622,625L600,560L578,625ZM160,720L105,665L300,470Q263,430 235,382Q207,334 190,280L270,280Q284,318 304,350Q324,382 350,410Q390,365 419,312Q448,259 464,200L80,200L80,120L320,120L320,40L400,40L400,120L640,120L640,200L544,200Q526,276 489,344Q452,412 405,470L500,565L470,645L350,525L160,720Z"
internal const val ReaderSelectionIconNotePath =
    "M200,840Q167,840 143.5,816.5Q120,793 120,760L120,200Q120,167 143.5,143.5Q167,120 200,120L760,120Q793,120 816.5,143.5Q840,167 840,200L840,620L620,840L200,840ZM200,760L580,760L580,580L760,580L760,200L200,200L200,760ZM280,520L680,520L680,440L280,440L280,520ZM280,360L680,360L680,280L280,280L280,360Z"
internal const val ReaderSelectionIconSearchPath =
    "M784,840L532,588Q502,612 463,626Q424,640 380,640Q271,640 195.5,564.5Q120,489 120,380Q120,271 195.5,195.5Q271,120 380,120Q489,120 564.5,195.5Q640,271 640,380Q640,424 626,463Q612,502 588,532L840,784L784,840ZM380,560Q455,560 507.5,507.5Q560,455 560,380Q560,305 507.5,252.5Q455,200 380,200Q305,200 252.5,252.5Q200,305 200,380Q200,455 252.5,507.5Q305,560 380,560Z"
internal const val ReaderSelectionIconClearPath =
    "M256,760L200,704L424,480L200,256L256,200L480,424L704,200L760,256L536,480L760,704L704,760L480,536L256,760Z"
internal const val ReaderSelectionIconTeardropPath =
    "M480,860Q347,860 253.5,768Q160,676 160,544Q160,481 184.5,423.5Q209,366 254,322L480,100L706,322Q751,366 775.5,423.5Q800,481 800,544Q800,676 706.5,768Q613,860 480,860Z"

internal fun String.escapeHtml(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

internal fun androidx.compose.ui.graphics.Color.toCssHex(): String {
    fun channel(value: Float): String = (value * 255f).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

internal fun androidx.compose.ui.graphics.Color.toCssRgba(alpha: Float): String {
    fun channel(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)
    val safeAlpha = alpha.coerceIn(0f, 1f)
    return "rgba(${channel(red)}, ${channel(green)}, ${channel(blue)}, ${safeAlpha.formatCssAlpha()})"
}

internal fun Float.formatCssAlpha(): String {
    val scaled = (this * 1000f).roundToInt()
    val whole = scaled / 1000
    val fraction = (scaled % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

internal fun logReaderHtml(message: String) {
    logSharedReaderDiagnostic("ReaderHtmlRender") { message }
}
