package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.paginatedreader.BorderStyle
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.shared.ReaderTexture
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun SharedEpubChapter.toHtml(searchQuery: String, searchOptions: ReaderSearchOptions): String {
    val normalizedText = normalizedReaderText()
    semanticBlocks.takeIf { it.isNotEmpty() }?.let { blocks ->
        val prewrapBlocks = blocks.flattenHtmlSemanticBlocks()
            .filterIsInstance<SemanticTextBlock>()
            .count { it.text.needsPreservedWhitespace() }
        logSharedReaderDiagnostic(TxtFormatTraceTag) {
            "event=shared_html_chapter_route route=semantic chapter=\"${title.txtFormatTracePreview()}\" " +
                "plainChars=${plainText.length} normalizedChars=${normalizedText.length} blocks=${blocks.size} prewrapBlocks=$prewrapBlocks " +
                "htmlChars=${htmlContent.length} preview=\"${normalizedText.txtFormatTracePreview()}\""
        }
        return blocks.joinToString("") { it.toHtml(searchQuery, searchOptions) }
    }
    htmlContent.takeIf { it.isNotBlank() }?.let { html ->
        logSharedReaderDiagnostic(TxtFormatTraceTag) {
            "event=shared_html_chapter_route route=htmlContent chapter=\"${title.txtFormatTracePreview()}\" " +
                "plainChars=${plainText.length} normalizedChars=${normalizedText.length} htmlChars=${html.length} " +
                "containsPreWrap=${html.contains("white-space: pre-wrap") || html.contains("white-space:pre-wrap")} " +
                "htmlPreview=\"${html.txtFormatTracePreview()}\" plainPreview=\"${normalizedText.txtFormatTracePreview()}\""
        }
        return html
    }
    logSharedReaderDiagnostic(TxtFormatTraceTag) {
        "event=shared_html_chapter_route route=plainFallback chapter=\"${title.txtFormatTracePreview()}\" " +
            "plainChars=${plainText.length} normalizedChars=${normalizedText.length} " +
            "needsWhitespace=${normalizedText.needsPreservedWhitespace()} preview=\"${normalizedText.txtFormatTracePreview()}\""
    }
    return normalizedText.textToParagraphHtml(searchQuery, searchOptions)
}

internal fun List<SemanticBlock>.blocksForPage(page: ReaderPage): List<SemanticBlock> {
    return mapIndexedNotNull { index, block ->
        block.clipToPage(page)
            ?: block.takeIf {
                val previousText = asSequence()
                    .take(index)
                    .mapNotNull { it.lastTextBlock() }
                    .lastOrNull()
                val nextText =
                    asSequence().drop(index + 1).firstNotNullOfOrNull { it.firstTextBlock() }
                val anchor = previousText?.let { it.startCharOffsetInSource + it.text.length }
                    ?: nextText?.startCharOffsetInSource
                    ?: 0
                anchor in page.startOffset..page.endOffset
            }
    }
}

internal fun SemanticTextBlock.intersects(startOffset: Int, endOffset: Int): Boolean {
    val start = startCharOffsetInSource
    val end = start + text.length
    return start < endOffset && end > startOffset
}

internal fun SemanticBlock.clipToPage(page: ReaderPage): SemanticBlock? {
    return when (this) {
        is SemanticTextBlock -> takeIf { intersects(page.startOffset, page.endOffset) }
        is SemanticList -> {
            val visibleItems = items.filter { it.intersects(page.startOffset, page.endOffset) }
            takeIf { visibleItems.isNotEmpty() }?.copy(items = visibleItems)
        }
        is SemanticTable -> {
            val visibleRows = rows.mapNotNull { row ->
                val visibleCells = row.mapNotNull { cell ->
                    val visibleContent = cell.content.mapNotNull { it.clipToPage(page) }
                    cell.takeIf { visibleContent.isNotEmpty() }?.copy(content = visibleContent)
                }
                visibleCells.takeIf { it.isNotEmpty() }
            }
            takeIf { visibleRows.isNotEmpty() }?.copy(rows = visibleRows)
        }
        is SemanticFlexContainer -> {
            val visibleChildren = children.mapNotNull { it.clipToPage(page) }
            takeIf { visibleChildren.isNotEmpty() }?.copy(children = visibleChildren)
        }
        is SemanticWrappingBlock -> {
            val visibleParagraphs = paragraphsToWrap.filter { it.intersects(page.startOffset, page.endOffset) }
            takeIf { visibleParagraphs.isNotEmpty() }?.copy(paragraphsToWrap = visibleParagraphs)
        }
        else -> null
    }
}

internal fun SemanticBlock.firstTextBlock(): SemanticTextBlock? {
    return when (this) {
        is SemanticTextBlock -> this
        is SemanticList -> items.firstOrNull()
        is SemanticTable -> rows.asSequence().flatMap { it.asSequence() }
            .flatMap { it.content.asSequence() }.firstNotNullOfOrNull { it.firstTextBlock() }

        is SemanticFlexContainer -> children
            .firstNotNullOfOrNull { it.firstTextBlock() }

        is SemanticWrappingBlock -> paragraphsToWrap.firstOrNull()
        else -> null
    }
}

internal fun SemanticBlock.lastTextBlock(): SemanticTextBlock? {
    return when (this) {
        is SemanticTextBlock -> this
        is SemanticList -> items.lastOrNull()
        is SemanticTable -> rows.asReversed().asSequence()
            .flatMap { it.asReversed().asSequence() }
            .flatMap { it.content.asReversed().asSequence() }
            .firstNotNullOfOrNull { it.lastTextBlock() }

        is SemanticFlexContainer -> children.asReversed()
            .firstNotNullOfOrNull { it.lastTextBlock() }

        is SemanticWrappingBlock -> paragraphsToWrap.lastOrNull()
        else -> null
    }
}

internal fun List<SemanticBlock>.blockSummary(): String {
    var textBlocks = 0
    var lists = 0
    var listItems = 0
    var tables = 0
    var tableCells = 0
    var flex = 0
    var images = 0
    var math = 0
    fun visit(block: SemanticBlock) {
        when (block) {
            is SemanticTextBlock -> textBlocks++
            is SemanticList -> {
                lists++
                listItems += block.items.size
                block.items.forEach(::visit)
            }
            is SemanticTable -> {
                tables++
                tableCells += block.rows.sumOf { it.size }
                block.rows.flatten().forEach { cell -> cell.content.forEach(::visit) }
            }
            is SemanticFlexContainer -> {
                flex++
                block.children.forEach(::visit)
            }
            is SemanticWrappingBlock -> {
                images++
                block.paragraphsToWrap.forEach(::visit)
            }
            is SemanticImage -> images++
            is SemanticMath -> math++
            else -> Unit
        }
    }
    forEach(::visit)
    return "text=$textBlocks lists=$lists items=$listItems tables=$tables cells=$tableCells flex=$flex images=$images math=$math"
}

internal fun List<SemanticBlock>.styleSummary(): String {
    val fontSizes = mutableListOf<String>()
    val listStyles = mutableListOf<String>()
    val displayValues = mutableListOf<String>()
    fun collectStyle(style: CssStyle) {
        style.fontSize.toDiagnosticTextUnit()?.let { fontSizes += it }
        style.spanStyle.fontSize.toDiagnosticTextUnit()?.let { fontSizes += it }
        style.blockStyle.listStyleType?.takeIf { it.isNotBlank() }?.let { listStyles += "type=$it" }
        style.blockStyle.listStyleImage?.takeIf { it.isNotBlank() }?.let { listStyles += "image=$it" }
        style.display?.takeIf { it.isNotBlank() }?.let { displayValues += it }
        style.blockStyle.display?.takeIf { it.isNotBlank() }?.let { displayValues += it }
    }
    fun visit(block: SemanticBlock) {
        collectStyle(block.style)
        when (block) {
            is SemanticTextBlock -> block.spans.forEach { collectStyle(it.style) }
            is SemanticList -> block.items.forEach(::visit)
            is SemanticTable -> block.rows.flatten().forEach { cell ->
                collectStyle(cell.style)
                cell.content.forEach(::visit)
            }
            is SemanticFlexContainer -> block.children.forEach(::visit)
            is SemanticWrappingBlock -> {
                visit(block.floatedImage)
                block.paragraphsToWrap.forEach(::visit)
            }
            else -> Unit
        }
    }
    forEach(::visit)
    return "fontSizes=${fontSizes.distinct().take(12)} listStyles=${listStyles.distinct().take(12)} display=${displayValues.distinct().take(12)}"
}

internal fun SemanticBlock.toHtml(searchQuery: String, searchOptions: ReaderSearchOptions): String {
    return when (this) {
        is SemanticHeader -> "<h${level.coerceIn(1, 6)}${textOffsetAttributes()}${styleAttribute(preservedWhitespaceStyle())}>${textHtml(searchQuery, searchOptions)}</h${level.coerceIn(1, 6)}>"
        is SemanticParagraph -> "<p${textOffsetAttributes()}${styleAttribute(preservedWhitespaceStyle())}>${textHtml(searchQuery, searchOptions)}</p>"
        is SemanticListItem -> "<li${textOffsetAttributes()}${listItemStyleAttribute(preservedWhitespaceStyle())}>${textHtml(searchQuery, searchOptions)}</li>"
        is SemanticList -> {
            val tag = if (isOrdered) "ol" else "ul"
            "<$tag${styleAttribute()}>${items.joinToString("") { it.toHtml(searchQuery, searchOptions) }}</$tag>"
        }
        is SemanticImage -> "<figure${imageAnchorAttributes()}${styleAttribute()}><img src=\"${path.escapeHtml()}\" alt=\"${altText.orEmpty().escapeHtml()}\" loading=\"lazy\" decoding=\"async\"${imageSizeAttribute()}></figure>"
        is SemanticMath -> svgContent ?: "<pre${styleAttribute()}>${altText.orEmpty().highlightAndEscape(searchQuery, searchOptions)}</pre>"
        is SemanticSpacer -> if (isExplicitLineBreak) "<br>" else "<div${styleAttribute("height:1em")}></div>"
        is SemanticTable -> rows.joinToString("", "<table${styleAttribute()}><tbody>", "</tbody></table>") { row ->
            row.joinToString("", "<tr>", "</tr>") { cell ->
                val tag = if (cell.isHeader) "th" else "td"
                "<$tag colspan=\"${cell.colspan.coerceAtLeast(1)}\"${cell.style.toStyleAttribute()}>${cell.content.joinToString("") { it.toHtml(searchQuery, searchOptions) }}</$tag>"
            }
        }
        is SemanticFlexContainer -> children.joinToString("", "<div${styleAttribute()}>", "</div>") { it.toHtml(searchQuery, searchOptions) }
        is SemanticWrappingBlock -> floatedImage.toHtml(searchQuery, searchOptions) + paragraphsToWrap.joinToString("") { it.toHtml(searchQuery, searchOptions) }
        is SemanticTextBlock -> "<p${textOffsetAttributes()}${styleAttribute(preservedWhitespaceStyle())}>${textHtml(searchQuery, searchOptions)}</p>"
    }
}

internal fun String.textToParagraphHtml(
    searchQuery: String,
    searchOptions: ReaderSearchOptions,
    baseOffset: Int = 0
): String {
    val segments = paragraphSegments()
    logSharedReaderDiagnostic(TxtFormatTraceTag) {
        "event=shared_plain_fallback_segments baseOffset=$baseOffset chars=$length segments=${segments.size} " +
            "prewrapSegments=${segments.count { it.text.needsPreservedWhitespace() }} " +
            "firstSegment=\"${segments.firstOrNull()?.text.orEmpty().txtFormatTracePreview()}\""
    }
    return segments
        .joinToString("") { paragraph ->
            val start = baseOffset + paragraph.startOffset
            val end = start + paragraph.text.length
            val whitespaceStyle = if (paragraph.text.needsPreservedWhitespace()) {
                """ style="white-space:pre-wrap"""
            } else {
                ""
            }
            """<p data-reader-text-start="$start" data-reader-text-end="$end"$whitespaceStyle>${paragraph.text.highlightAndEscape(searchQuery, searchOptions)}</p>"""
        }
        .ifBlank { "<p></p>" }
}

internal fun String.needsPreservedWhitespace(): Boolean {
    return any { it == '\n' || it == '\t' } || contains(Regex(" {2,}"))
}

internal fun String.txtFormatTracePreview(maxLength: Int = 220): String {
    return replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
        .replace("\"", "\\\"")
}

internal fun String.paragraphSegments(): List<TextSegment> {
    val segments = mutableListOf<TextSegment>()
    var index = 0
    while (index < length) {
        while (index < length && this[index].isWhitespace()) index++
        val start = index
        if (start >= length) break

        var end = start
        while (end < length) {
            if (this[end] == '\n') {
                var probe = end
                var newlineCount = 0
                while (probe < length && this[probe].isWhitespace()) {
                    if (this[probe] == '\n') newlineCount++
                    probe++
                }
                if (newlineCount >= 2) break
            }
            end++
        }

        val raw = substring(start, end)
        val trimmedEnd = raw.indexOfLast { !it.isWhitespace() }
        if (trimmedEnd >= 0) {
            segments += TextSegment(
                text = raw.substring(0, trimmedEnd + 1),
                startOffset = start
            )
        }
        index = end + 1
    }
    return segments
}

internal fun SharedEpubChapter.normalizedReaderText(): String {
    return plainText
        .replace("\r\n", "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

internal fun SemanticTextBlock.textOffsetAttributes(): String {
    val start = startCharOffsetInSource.coerceAtLeast(0)
    val end = (start + text.length).coerceAtLeast(start)
    return buildString {
        append(" data-reader-text-start=\"$start\" data-reader-text-end=\"$end\"")
        append(" data-reader-block-index=\"$blockIndex\"")
        elementId?.takeIf { it.isNotBlank() }?.let {
            append(" id=\"${it.escapeHtml()}\" data-reader-element-id=\"${it.escapeHtml()}\"")
        }
        cfi?.takeIf { it.isNotBlank() }?.let {
            append(" data-reader-cfi=\"${it.escapeHtml()}\"")
        }
    }
}

internal fun SemanticImage.imageAnchorAttributes(): String {
    return buildString {
        append(" data-reader-block-index=\"$blockIndex\"")
        elementId?.takeIf { it.isNotBlank() }?.let {
            append(" id=\"${it.escapeHtml()}\" data-reader-element-id=\"${it.escapeHtml()}\"")
        }
        cfi?.takeIf { it.isNotBlank() }?.let {
            append(" data-reader-cfi=\"${it.escapeHtml()}\"")
        }
    }
}

internal fun SemanticTextBlock.textHtml(
    searchQuery: String,
    searchOptions: ReaderSearchOptions
): String {
    if (text.isEmpty()) return ""
    val inlineSpans = spans
        .filter { it.end > it.start }
        .map {
            it.copy(
                start = it.start.coerceIn(0, text.length),
                end = it.end.coerceIn(0, text.length)
            )
        }
        .filter { it.end > it.start }
        .sortedWith(compareBy({ it.start }, { it.end }))
    val linkSpans = inlineSpans.filter { !it.linkHref.isNullOrBlank() }
    val markersByOffset = spans
        .mapNotNull { span ->
            span.elementId
                ?.takeIf { it.isNotBlank() }
                ?.let { id -> span.start.coerceIn(0, text.length) to id }
        }
        .groupBy({ it.first }, { it.second })

    if (inlineSpans.isEmpty() && markersByOffset.isEmpty()) {
        return text.highlightAndEscape(searchQuery, searchOptions)
    }

    val boundaries = mutableSetOf(0, text.length)
    inlineSpans.forEach { span ->
        boundaries += span.start
        boundaries += span.end
    }
    boundaries += markersByOffset.keys

    val ordered = boundaries.sorted()
    val builder = StringBuilder()
    fun appendMarkers(offset: Int) {
        markersByOffset[offset].orEmpty().distinct().forEach { id ->
            builder.append("""<span id="${id.escapeHtml()}" data-reader-element-id="${id.escapeHtml()}"></span>""")
        }
    }

    for (index in 0 until ordered.lastIndex) {
        val start = ordered[index]
        val end = ordered[index + 1]
        appendMarkers(start)
        if (end <= start) continue
        val html = text.substring(start, end).highlightAndEscape(searchQuery, searchOptions)
        val link = linkSpans.firstOrNull { it.start <= start && it.end >= end }
        val segmentStyle = inlineSpans
            .filter { it.start <= start && it.end >= end }
            .fold(CssStyle()) { merged, span -> merged.merge(span.style) }
            .toStyleAttribute()
        if (link?.linkHref != null) {
            builder.append("""<a href="${link.linkHref.escapeHtml()}" data-reader-link="true"$segmentStyle>$html</a>""")
        } else if (segmentStyle.isNotEmpty()) {
            builder.append("""<span$segmentStyle>$html</span>""")
        } else {
            builder.append(html)
        }
    }
    appendMarkers(text.length)
    return builder.toString()
}

internal fun SemanticBlock.styleAttribute(extra: String? = null): String {
    return style.toStyleAttribute(extra)
}

internal fun SemanticTextBlock.preservedWhitespaceStyle(): String? {
    return if (text.needsPreservedWhitespace()) "white-space:pre-wrap" else null
}

internal fun SemanticListItem.listItemStyleAttribute(extra: String? = null): String {
    val markerStyle = itemMarkerImage
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { style.blockStyle.listStyleImage.isNullOrBlank() }
        ?.let { "list-style-image:url('${it.escapeHtml()}')" }
    val extras = listOfNotNull(markerStyle, extra).joinToString(";").takeIf { it.isNotBlank() }
    return style.toStyleAttribute(extras)
}

internal fun CssStyle.toStyleAttribute(extra: String? = null): String {
    val declarations = mutableListOf<String>()
    extra?.takeIf { it.isNotBlank() }?.let { declarations += it }
    (spanStyle.fontSize.takeIf { it.isSpecified } ?: fontSize.takeIf { it.isSpecified })
        ?.toCssLength()
        ?.let { declarations += "font-size:$it" }
    wordSpacing.toCssLength()?.let { declarations += "word-spacing:$it" }
    textTransform?.takeIf { it.isNotBlank() }?.let { declarations += "text-transform:$it" }
    hyphens?.takeIf { it.isNotBlank() }?.let { declarations += "hyphens:$it" }
    fontVariantNumeric?.takeIf { it.isNotBlank() }?.let { declarations += "font-variant-numeric:$it" }
    if (spanStyle.color.isSpecified) declarations += "color:${spanStyle.color.toCssHex()}"
    if (spanStyle.background.isSpecified) declarations += "background-color:${spanStyle.background.toCssHex()}"
    spanStyle.fontWeight?.let { declarations += "font-weight:${it.weight}" }
    spanStyle.fontStyle?.let { declarations += "font-style:${it.toString().substringAfterLast('.').lowercase()}" }
    spanStyle.textDecoration
        ?.takeIf { it.toString() != "None" }
        ?.let { declarations += "text-decoration:${it.toString().lowercase()}" }
    textDecorationStyle?.takeIf { it.isNotBlank() }?.let { declarations += "text-decoration-style:$it" }
    if (textDecorationColor.isSpecified) declarations += "text-decoration-color:${textDecorationColor.toCssHex()}"
    if (textUnderlineOffset.isSpecified) declarations += "text-underline-offset:${textUnderlineOffset.value}px"
    fontFamilies.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
        declarations += "font-family:'${it.escapeHtml()}'"
    }
    paragraphStyle.lineHeight.toCssLength()?.let { declarations += "line-height:$it" }
    paragraphStyle.textIndent?.firstLine
        ?.takeIf { it.isSpecified && it.value != 0f }
        ?.toCssLength()
        ?.let { declarations += "text-indent:$it" }
    paragraphStyle.textAlign
        ?.takeIf { it.toString() != "Unspecified" }
        ?.let { align ->
        declarations += "text-align:${align.toString().lowercase()}"
    }
    val block = blockStyle
    display?.takeIf { it.isNotBlank() }?.let { declarations += "display:$it" }
    boxSizing?.takeIf { it.isNotBlank() }?.let { declarations += "box-sizing:$it" }
    if (block.backgroundColor.isSpecified) declarations += "background-color:${block.backgroundColor.toCssHex()}"
    if (block.width.isSpecified) declarations += "width:${block.width.value}px"
    if (block.maxWidth.isSpecified) declarations += "max-width:${block.maxWidth.value}px"
    if (block.height.isSpecified) declarations += "height:${block.height.value}px"
    block.boxSizing?.takeIf { it.isNotBlank() }?.let { declarations += "box-sizing:$it" }
    if (block.margin.top.isSpecified && block.margin.top.value != 0f) declarations += "margin-top:${block.margin.top.value}px"
    if (block.margin.right.isSpecified && block.margin.right.value != 0f) declarations += "margin-right:${block.margin.right.value}px"
    if (block.margin.bottom.isSpecified && block.margin.bottom.value != 0f) declarations += "margin-bottom:${block.margin.bottom.value}px"
    if (block.margin.left.isSpecified && block.margin.left.value != 0f) declarations += "margin-left:${block.margin.left.value}px"
    if (block.padding.top.isSpecified && block.padding.top.value != 0f) declarations += "padding-top:${block.padding.top.value}px"
    if (block.padding.right.isSpecified && block.padding.right.value != 0f) declarations += "padding-right:${block.padding.right.value}px"
    if (block.padding.bottom.isSpecified && block.padding.bottom.value != 0f) declarations += "padding-bottom:${block.padding.bottom.value}px"
    if (block.padding.left.isSpecified && block.padding.left.value != 0f) declarations += "padding-left:${block.padding.left.value}px"
    block.borderTop?.toCssBorder()?.let { declarations += "border-top:$it" }
    block.borderRight?.toCssBorder()?.let { declarations += "border-right:$it" }
    block.borderBottom?.toCssBorder()?.let { declarations += "border-bottom:$it" }
    block.borderLeft?.toCssBorder()?.let { declarations += "border-left:$it" }
    if (block.borderTopLeftRadius.isSpecified && block.borderTopLeftRadius.value != 0f) declarations += "border-top-left-radius:${block.borderTopLeftRadius.value}px"
    if (block.borderTopRightRadius.isSpecified && block.borderTopRightRadius.value != 0f) declarations += "border-top-right-radius:${block.borderTopRightRadius.value}px"
    if (block.borderBottomRightRadius.isSpecified && block.borderBottomRightRadius.value != 0f) declarations += "border-bottom-right-radius:${block.borderBottomRightRadius.value}px"
    if (block.borderBottomLeftRadius.isSpecified && block.borderBottomLeftRadius.value != 0f) declarations += "border-bottom-left-radius:${block.borderBottomLeftRadius.value}px"
    block.float?.takeIf { it.isNotBlank() }?.let { declarations += "float:$it" }
    block.clear?.takeIf { it.isNotBlank() }?.let { declarations += "clear:$it" }
    block.position?.takeIf { it.isNotBlank() }?.let { declarations += "position:$it" }
    if (block.top.isSpecified) declarations += "top:${block.top.value}px"
    if (block.right.isSpecified) declarations += "right:${block.right.value}px"
    if (block.bottom.isSpecified) declarations += "bottom:${block.bottom.value}px"
    if (block.left.isSpecified) declarations += "left:${block.left.value}px"
    block.display?.takeIf { it.isNotBlank() }?.let { declarations += "display:$it" }
    block.flexDirection?.takeIf { it.isNotBlank() }?.let { declarations += "flex-direction:$it" }
    block.justifyContent?.takeIf { it.isNotBlank() }?.let { declarations += "justify-content:$it" }
    block.alignItems?.takeIf { it.isNotBlank() }?.let { declarations += "align-items:$it" }
    block.horizontalAlign?.takeIf { it.isNotBlank() }?.let { declarations += "text-align:$it" }
    block.filter?.takeIf { it.isNotBlank() }?.let { declarations += "filter:$it" }
    block.borderCollapse?.takeIf { it.isNotBlank() }?.let { declarations += "border-collapse:$it" }
    if (block.borderSpacing.isSpecified && block.borderSpacing.value != 0f) declarations += "border-spacing:${block.borderSpacing.value}px"
    block.listStyleType?.takeIf { it.isNotBlank() }?.let { declarations += "list-style-type:$it" }
    block.listStyleImage?.takeIf { it.isNotBlank() }?.let { declarations += "list-style-image:url('${it.escapeHtml()}')" }
    return if (declarations.isEmpty()) "" else " style=\"${declarations.joinToString(";").escapeHtml()}\""
}

internal fun BorderStyle.toCssBorder(): String? {
    if (!width.isSpecified || width.value <= 0f) return null
    val styleValue = style.takeIf { it.isNotBlank() } ?: "solid"
    val colorValue = if (color.isSpecified) color.toCssHex() else "currentColor"
    return "${width.value}px $styleValue $colorValue"
}

internal fun TextUnit.toCssLength(): String? {
    if (!isSpecified || value <= 0f) return null
    return when {
        isEm -> "${value}em"
        isSp -> "${value}px"
        else -> value.toString()
    }
}

internal fun TextUnit.toDiagnosticTextUnit(): String? {
    if (!isSpecified || value <= 0f) return null
    return when {
        isEm -> "${value}em"
        isSp -> "${value}sp"
        else -> value.toString()
    }
}

internal fun SemanticImage.imageSizeAttribute(): String {
    val declarations = buildList {
        intrinsicWidth?.takeIf { it > 0f }?.let { add("width:${it}px") }
        intrinsicHeight?.takeIf { it > 0f }?.let { add("height:${it}px") }
    }
    return if (declarations.isEmpty()) "" else " style=\"${declarations.joinToString(";")}\""
}

internal fun String.highlightAndEscape(searchQuery: String, searchOptions: ReaderSearchOptions): String {
    val escaped = escapeHtml()
    val query = searchQuery.trim()
    if (query.isEmpty()) return escaped
    val escapedQuery = Regex.escape(query.escapeHtml())
    val pattern = if (searchOptions.wholeWords) {
        "(^|[^A-Za-z0-9_])($escapedQuery)(?=$|[^A-Za-z0-9_])"
    } else {
        "($escapedQuery)"
    }
    val options: Set<RegexOption> = if (searchOptions.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return escaped.replace(Regex(pattern, options)) {
        val leading = if (searchOptions.wholeWords) it.groupValues[1] else ""
        val value = if (searchOptions.wholeWords) it.groupValues[2] else it.groupValues[1]
        "$leading<span class=\"reader-highlight\">$value</span>"
    }
}

internal fun Long.toCssColor(): String {
    val value = this and 0xFFFFFFFFL
    val red = ((value shr 16) and 0xFF).toString(16).padStart(2, '0')
    val green = ((value shr 8) and 0xFF).toString(16).padStart(2, '0')
    val blue = (value and 0xFF).toString(16).padStart(2, '0')
    return "#$red$green$blue"
}

internal fun readerLinkCssColors(backgroundArgb: Long, textArgb: Long, darkMode: Boolean): ReaderLinkCssColors {
    val backgroundLuminance = backgroundArgb.relativeLuminance()
    val textLuminance = textArgb.relativeLuminance()
    val candidates = if (darkMode || backgroundLuminance < 0.45f) {
        listOf(0xFF7DD3FCL, 0xFF5EEAD4L, 0xFFA5B4FCL, 0xFFFDE68AL, 0xFFFFFFFFL)
    } else {
        listOf(0xFF005FCCL, 0xFF006D75L, 0xFF7A1E52L, 0xFF4A148CL, 0xFF111827L)
    }
    val linkColor = candidates.firstOrNull {
        it.contrastRatio(backgroundArgb) >= 4.5f && abs(it.relativeLuminance() - textLuminance) >= 0.08f
    } ?: candidates.maxByOrNull { it.contrastRatio(backgroundArgb) } ?: if (darkMode) 0xFF7DD3FCL else 0xFF005FCCL
    val alpha = if (backgroundLuminance < 0.45f) 0.24f else 0.16f
    return ReaderLinkCssColors(
        color = linkColor.toCssColor(),
        decoration = linkColor.toCssColor(),
        background = linkColor.toCssRgba(alpha)
    )
}

internal fun Long.toCssRgba(alpha: Float): String {
    val value = this and 0xFFFFFFFFL
    val red = (value shr 16) and 0xFF
    val green = (value shr 8) and 0xFF
    val blue = value and 0xFF
    return "rgba($red, $green, $blue, ${alpha.coerceIn(0f, 1f)})"
}

internal fun Long.contrastRatio(other: Long): Float {
    val first = relativeLuminance()
    val second = other.relativeLuminance()
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun Long.relativeLuminance(): Float {
    val value = this and 0xFFFFFFFFL
    fun channel(shift: Int): Float {
        val normalized = (((value shr shift) and 0xFF).toFloat() / 255f)
        return if (normalized <= 0.03928f) {
            normalized / 12.92f
        } else {
            ((normalized + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }
    return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
}

internal data class ReaderLinkCssColors(
    val color: String,
    val decoration: String,
    val background: String
)

internal data class ReaderDocumentAppearanceCss(
    val background: String,
    val foreground: String,
    val linkColors: ReaderLinkCssColors,
    val highlight: String,
    val colorScheme: String,
    val textureOverlayCss: String
)

internal fun ReaderSettings.toDocumentAppearanceCss(textureDataUri: String?): ReaderDocumentAppearanceCss {
    val bgArgb = backgroundColorArgb ?: if (darkMode) 0xFF171A17L else 0xFFFFFCF5L
    val fgArgb = textColorArgb ?: if (darkMode) 0xFFE7E3D8L else 0xFF24231FL
    return ReaderDocumentAppearanceCss(
        background = bgArgb.toCssColor(),
        foreground = fgArgb.toCssColor(),
        linkColors = readerLinkCssColors(bgArgb, fgArgb, darkMode),
        highlight = if (darkMode) "#675A00" else "#FFE36E",
        colorScheme = if (darkMode) "dark" else "light",
        textureOverlayCss = textureId
            ?.takeIf { textureAlpha > 0.01f }
            ?.toTextureOverlayCss(textureAlpha, darkMode, textureDataUri)
            .orEmpty()
    )
}

internal fun ReaderSettings.readerTextAlignCss(): String {
    return when (textAlign) {
        SharedReaderTextAlign.START -> ""
        SharedReaderTextAlign.LEFT -> "left"
        SharedReaderTextAlign.RIGHT -> "right"
        SharedReaderTextAlign.JUSTIFY -> "justify"
        SharedReaderTextAlign.CENTER -> "center"
    }
}

internal fun ReaderSettings.readerCustomFontUrl(): String? {
    return customFontPath?.takeIf { it.isNotBlank() }?.toCssFontUrl()
}

internal fun ReaderSettings.readerCustomFontFaceCss(): String {
    val customFontUrl = readerCustomFontUrl() ?: return ""
    return "@font-face { font-family: 'ReaderCustomFont'; src: url('$customFontUrl'); font-display: swap; }"
}

internal fun ReaderSettings.readerFontFamilyCss(): String {
    return if (readerCustomFontUrl() != null) {
        "'ReaderCustomFont', Georgia, 'Times New Roman', serif"
    } else {
        when (fontFamily) {
            "Serif" -> "Georgia, 'Times New Roman', serif"
            "Sans" -> "Inter, Segoe UI, Arial, sans-serif"
            "Mono" -> "'Roboto Mono', Consolas, monospace"
            else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
        }
    }
}

internal fun ReaderSettings.readerFontWeightCss(): String {
    return fontWeight.takeIf { it > 0 }?.coerceIn(1, 1000)?.toString() ?: "normal"
}

internal fun ReaderSettings.readerVerticalMarginY(): Int {
    return (resolvedVerticalMargin / 3f).roundToInt().coerceIn(0, 56)
}

internal fun ReaderSettings.readerImageScaleCss(): String {
    return "${(imageScale * 100f).roundToInt().coerceIn(50, 200)}%"
}

internal fun String.toCssFontUrl(): String {
    val trimmed = trim()
    val normalizedInput = trimmed.replace("\\", "/")
    val withScheme = when {
        normalizedInput.startsWith("file:///") -> normalizedInput
        normalizedInput.startsWith("file:/") -> "file:///" + normalizedInput.removePrefix("file:/")
        normalizedInput.contains("://") -> normalizedInput
        normalizedInput.matches(Regex("^[A-Za-z]:/.*")) -> "file:///$normalizedInput"
        else -> normalizedInput
    }
    return withScheme
        .replace(" ", "%20")
        .replace("'", "%27")
        .replace(")", "%29")
        .replace("(", "%28")
}

internal fun String.toTextureOverlayCss(alpha: Float, darkMode: Boolean, dataUri: String?): String {
    val hasTextureData = !dataUri.isNullOrBlank()
    val texture = dataUri
        ?.takeIf { hasTextureData }
        ?.let { "url('${it.escapeCssString()}')" }
        ?: when (this) {
            ReaderTexture.NATURAL_WHITE.id,
            ReaderTexture.PAPER.id -> "radial-gradient(circle at 20% 30%, rgba(0,0,0,.09) 0 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.22), rgba(0,0,0,.04))"
            ReaderTexture.NATURAL_BLACK.id,
            ReaderTexture.SLATE.id -> "radial-gradient(circle at 20% 30%, rgba(255,255,255,.12) 0 1px, transparent 1px), linear-gradient(120deg, rgba(255,255,255,.08), rgba(0,0,0,.18))"
            ReaderTexture.LIGHT_VENEER.id,
            ReaderTexture.RETINA_WOOD.id -> "repeating-linear-gradient(90deg, rgba(120,76,32,.10) 0 3px, rgba(255,255,255,.09) 3px 7px)"
            ReaderTexture.GREY_WASH.id -> "repeating-linear-gradient(135deg, rgba(255,255,255,.07) 0 2px, rgba(0,0,0,.08) 2px 5px)"
            ReaderTexture.CLASSY_FABRIC.id,
            ReaderTexture.CANVAS.id -> "repeating-linear-gradient(0deg, rgba(255,255,255,.08) 0 1px, transparent 1px 4px), repeating-linear-gradient(90deg, rgba(0,0,0,.08) 0 1px, transparent 1px 4px)"
            ReaderTexture.RETRO_INTRO.id,
            ReaderTexture.EINK.id -> "radial-gradient(circle, rgba(0,0,0,.12) 0 1px, transparent 1px)"
            else -> "linear-gradient(135deg, rgba(255,255,255,.08), rgba(0,0,0,.08))"
        }
    val size = if (hasTextureData) {
        "auto"
    } else {
        when (this) {
            ReaderTexture.EINK.id,
            ReaderTexture.RETRO_INTRO.id,
            ReaderTexture.PAPER.id,
            ReaderTexture.NATURAL_WHITE.id,
            ReaderTexture.NATURAL_BLACK.id -> "7px 7px, 100% 100%"
            else -> "auto"
        }
    }
    return """
            body::before {
              content: "";
              position: fixed;
              inset: 0;
              pointer-events: none;
              background-image: $texture;
              background-size: $size;
              opacity: ${alpha.coerceIn(0f, 1f)};
              mix-blend-mode: ${if (darkMode) "screen" else "multiply"};
              z-index: 0;
            }
    """.trimIndent()
}

internal fun String.escapeCssString(): String {
    return replace("\\", "\\\\").replace("'", "\\'")
}

internal fun String.toJsStringLiteral(): String {
    return buildString {
        append('"')
        this@toJsStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}
