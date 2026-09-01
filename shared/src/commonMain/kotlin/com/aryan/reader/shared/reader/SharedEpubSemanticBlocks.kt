package com.aryan.reader.shared.reader

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticSpan
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTableCell

/**
 * Converts a chapter's (sanitized and resource-rewritten) XHTML body into the same
 * [SemanticBlock] model the desktop and Android readers use. Block CFIs are produced
 * with the Android path algorithm ([SharedSemanticDomElement.cfiPath]), and
 * `startCharOffsetInSource` aligns each block to the chapter's normalized plain text
 * ([normalizeEpubWhitespace] of the tag-space transform), keeping search and
 * text-range locators consistent with [SharedEpubChapter.plainText].
 */
internal fun sharedEpubHtmlToSemanticBlocks(html: String): List<SemanticBlock> {
    val document = SharedSemanticHtmlDocument.parse(html) ?: return emptyList()
    return SharedEpubSemanticBlockBuilder(document).build()
}

private val SharedEpubBlockCloseTags = setOf(
    "p", "div", "section", "article", "aside", "main", "header", "footer",
    "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "table", "blockquote", "ul", "ol"
)

private val SharedEpubVoidElementNames = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
    "param", "source", "track", "wbr"
)

private val SharedEpubInlineVoidTags = setOf("br", "img")

private val SharedEpubNonRenderableTags = setOf(
    "script", "style", "head", "title", "meta", "link", "iframe", "object", "embed",
    "form", "noscript", "template", "svg"
)

private val SharedEpubBlockTags = setOf(
    "p", "div", "section", "article", "aside", "main", "header", "footer", "nav",
    "figure", "figcaption", "blockquote", "pre", "address", "center", "details",
    "summary", "caption", "dt", "dd", "h1", "h2", "h3", "h4", "h5", "h6", "ul",
    "ol", "li", "table", "tr", "td", "th", "br", "hr", "img", "math"
)

private val SharedEpubContainerTags = setOf(
    "div", "section", "article", "aside", "main", "header", "footer", "nav",
    "figure", "blockquote", "details", "body", "html"
)

private val SharedEpubHeaderLevels = mapOf(
    "h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6
)

private val SharedEpubBoldTags = setOf("b", "strong")
private val SharedEpubItalicTags = setOf("i", "em", "cite", "var")
private val SharedEpubUnderlineTags = setOf("u", "ins")
private val SharedEpubStrikeTags = setOf("s", "strike", "del")
private val SharedEpubCodeTags = setOf("code", "kbd", "samp", "tt")

private sealed interface SharedSemanticDomNode {
    val parent: SharedSemanticDomElement?
    var normalizedStart: Int
}

private class SharedSemanticDomElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<SharedSemanticDomNode> = mutableListOf(),
    override val parent: SharedSemanticDomElement?
) : SharedSemanticDomNode {
    val localName: String get() = name.substringAfter(':').lowercase()

    fun attribute(localName: String): String? = attributes[localName]

    fun elementId(): String? = attribute("id")?.trim()?.takeIf(String::isNotBlank)

    val isBlockElement: Boolean get() = localName in SharedEpubBlockTags

    fun hasBlockDescendant(): Boolean {
        val stack = ArrayDeque<SharedSemanticDomNode>()
        children.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node is SharedSemanticDomElement) {
                if (node.isBlockElement && node.localName !in SharedEpubInlineVoidTags) return true
                node.children.forEach { stack.addLast(it) }
            }
        }
        return false
    }

    /**
     * Android-compatible CFI path. Walks ancestors to the document root, indexing
     * this element among its parent's meaningful children (elements plus non-blank
     * text runs), using `(nodeIndex * 2) + 2` per level with the `/4` prefix.
     */
    fun cfiPath(): String? {
        val path = mutableListOf<Int>()
        var currentNode: SharedSemanticDomNode? = this
        while (currentNode is SharedSemanticDomElement && currentNode.parent != null) {
            val parent = currentNode.parent
            val meaningful = parent.children.filter { node ->
                node is SharedSemanticDomElement || (node is SharedSemanticDomText && node.text.isNotBlank())
            }
            val nodeIndex = meaningful.indexOfFirst { it === currentNode }
            if (nodeIndex == -1) {
                currentNode = parent
                continue
            }
            path.add(0, (nodeIndex * 2) + 2)
            currentNode = parent
        }
        if (path.isEmpty()) return null
        path.add(0, 4)
        return "/" + path.joinToString("/")
    }

    var contentStart: Int = 0
    var contentEnd: Int = 0
    override var normalizedStart: Int = -1
}

private class SharedSemanticDomText(
    val text: String,
    override val parent: SharedSemanticDomElement?
) : SharedSemanticDomNode {
    var sourceStart: Int = 0
    var sourceEnd: Int = 0
    override var normalizedStart: Int = -1
}

private class SharedSemanticHtmlTree(
    val root: SharedSemanticDomElement,
    val normalizedMapping: IntArray
)

private object SharedSemanticHtmlDocument {

    fun parse(html: String): SharedSemanticHtmlTree? {
        val tokens = sharedEpubXmlTokens(html).toList()
        val root = SharedSemanticDomElement("#document", emptyMap(), parent = null)
        val stack = ArrayDeque<SharedSemanticDomElement>().apply { addLast(root) }
        var cursor = 0
        for (token in tokens) {
            if (token.start > cursor) {
                val raw = html.substring(cursor, token.start)
                if (raw.isNotBlank()) {
                    stack.last().children += SharedSemanticDomText(raw.decodeEpubEntities(), stack.last())
                }
            }
            cursor = token.endExclusive
            val value = token.value
            when {
                value.startsWith("<!--") || value.startsWith("<?") || value.startsWith("<!DOCTYPE", ignoreCase = true) -> Unit
                value.startsWith("<![CDATA[") -> {
                    val content = value.removePrefix("<![CDATA[").removeSuffix("]]>")
                    if (content.isNotBlank()) {
                        stack.last().children += SharedSemanticDomText(content, stack.last())
                    }
                }
                value.startsWith("</") -> {
                    val closingName = value.removePrefix("</").substringBefore('>').trim()
                        .takeWhile { !it.isWhitespace() && it != '/' && it != '>' }
                    if (closingName.isBlank()) continue
                    val matched = stack.indexOfLast {
                        it.localName == closingName.substringAfter(':').lowercase()
                    }
                    if (matched > 0) {
                        while (stack.size > matched) stack.removeLast()
                    }
                }
                value.startsWith("<") -> {
                    val selfClosing = value.trimEnd().endsWith("/>")
                    val inside = value.removePrefix("<").removeSuffix(">").removeSuffix("/").trim()
                    val rawName = inside.takeWhile { !it.isWhitespace() }
                    if (rawName.isBlank()) continue
                    val localName = rawName.substringAfter(':').lowercase()
                    val attributeText = inside.substring(rawName.length)
                    val element = SharedSemanticDomElement(
                        rawName,
                        parseAttributes(attributeText),
                        parent = stack.last()
                    )
                    stack.last().children += element
                    if (!selfClosing && localName !in SharedEpubVoidElementNames) {
                        stack.addLast(element)
                    }
                }
            }
        }
        if (cursor < html.length && html.substring(cursor).isNotBlank()) {
            stack.last().children += SharedSemanticDomText(html.substring(cursor).decodeEpubEntities(), stack.last())
        }
        val mapping = buildTextSpace(root)
        return SharedSemanticHtmlTree(root, mapping)
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val attributes = linkedMapOf<String, String>()
        var offset = 0
        while (offset < raw.length) {
            while (offset < raw.length && raw[offset].isWhitespace()) offset++
            val match = EpubXmlAttributeRegex.find(raw, offset)
            if (match == null || match.range.first != offset) break
            val name = match.groupValues[1].substringAfter(':').lowercase()
            attributes[name] = match.groupValues[3].decodeEpubEntities()
            offset = match.range.last + 1
        }
        return attributes
    }

    private fun buildTextSpace(root: SharedSemanticDomElement): IntArray {
        val text = StringBuilder()
        fun appendElement(element: SharedSemanticDomElement) {
            val isBlockClose = element.localName in SharedEpubBlockCloseTags
            element.contentStart = text.length + 1
            text.append(' ')
            element.children.forEach { node ->
                when (node) {
                    is SharedSemanticDomText -> {
                        node.sourceStart = text.length
                        text.append(node.text)
                        node.sourceEnd = text.length
                    }
                    is SharedSemanticDomElement -> appendElement(node)
                }
            }
            text.append(if (element.localName == "br") '\n' else if (isBlockClose) '\n' else ' ')
            element.contentEnd = text.length
        }
        root.children.forEach { node ->
            when (node) {
                is SharedSemanticDomText -> {
                    node.sourceStart = text.length
                    text.append(node.text)
                    node.sourceEnd = text.length
                }
                is SharedSemanticDomElement -> appendElement(node)
            }
        }
        val (_, mapping) = normalizeWithSourceMapping(text.toString())
        assignNormalizedRanges(root, mapping)
        return mapping
    }

    private fun assignNormalizedRanges(root: SharedSemanticDomElement, mapping: IntArray) {
        fun firstNormalizedAtOrAfter(sourceIndex: Int): Int {
            var low = 0
            var high = mapping.size
            while (low < high) {
                val mid = (low + high) / 2
                if (mapping[mid] >= sourceIndex) high = mid else low = mid + 1
            }
            return low
        }

        fun walk(node: SharedSemanticDomNode) {
            when (node) {
                is SharedSemanticDomText -> {
                    val start = firstNormalizedAtOrAfter(node.sourceStart)
                    node.normalizedStart = if (start < mapping.size && mapping[start] < node.sourceEnd) start else -1
                }
                is SharedSemanticDomElement -> {
                    val start = firstNormalizedAtOrAfter(node.contentStart)
                    node.normalizedStart = if (start < mapping.size && mapping[start] < node.contentEnd) start else -1
                    node.children.forEach(::walk)
                }
            }
        }
        walk(root)
    }
}

private val SharedEpubWhitespaceChars = charArrayOf(' ', '\t', '\u000B', '\u000C', '\r')

/**
 * Replicates [normalizeEpubWhitespace] (pass by pass) while tracking, for every
 * output character, the index of the source character it derives from.
 */
internal fun normalizeWithSourceMapping(input: String): Pair<String, IntArray> {
    // Pass 1: '\u0000' -> ' '
    var text = input
    var map = IntArray(input.length) { it }
    if (text.contains('\u0000')) {
        val output = text.replace('\u0000', ' ')
        text = output
    }

    // Pass 2: collapse runs of [ \t\x0B\f\r] into a single space
    if (text.any { it in SharedEpubWhitespaceChars }) {
        val output = StringBuilder(text.length)
        val outputMap = ArrayList<Int>(text.length)
        text.forEachIndexed { index, char ->
            if (char in SharedEpubWhitespaceChars) {
                if (index == 0 || text[index - 1] !in SharedEpubWhitespaceChars) {
                    output.append(' ')
                    outputMap += map[index]
                }
            } else {
                output.append(char)
                outputMap += map[index]
            }
        }
        text = output.toString()
        map = outputMap.toIntArray()
    }

    // Pass 3: " *\n *" -> "\n"
    if (text.contains('\n')) {
        val output = StringBuilder(text.length)
        val outputMap = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\n' -> {
                    var cursor = index + 1
                    while (cursor < text.length && text[cursor] == ' ') cursor++
                    output.append('\n')
                    outputMap += map[index]
                    index = cursor - 1
                }
                ' ' -> if (index + 1 >= text.length || text[index + 1] != '\n') {
                    output.append(' ')
                    outputMap += map[index]
                }
                else -> {
                    output.append(text[index])
                    outputMap += map[index]
                }
            }
            index++
        }
        text = output.toString()
        map = outputMap.toIntArray()
    }

    // Pass 4: "\n{3,}" -> "\n\n"
    if (text.contains("\n\n\n")) {
        val output = StringBuilder(text.length)
        val outputMap = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                var cursor = index + 1
                while (cursor < text.length && text[cursor] == '\n') cursor++
                output.append('\n')
                outputMap += map[index]
                if (cursor - index > 2) {
                    output.append('\n')
                    outputMap += map[index + 1]
                }
                index = cursor - 1
            } else {
                output.append(text[index])
                outputMap += map[index]
            }
            index++
        }
        text = output.toString()
        map = outputMap.toIntArray()
    }

    // Pass 5: trim
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return "" to IntArray(0)
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return text.substring(start, end) to map.copyOfRange(start, end)
}

private class SharedEpubSemanticBlockBuilder(
    private val document: SharedSemanticHtmlTree
) {
    private val blocks = mutableListOf<SemanticBlock>()
    private var nextBlockIndex = 0

    fun build(): List<SemanticBlock> {
        buildContainer(document.root, blocks)
        return blocks
    }

    private fun buildContainer(element: SharedSemanticDomElement, target: MutableList<SemanticBlock>) {
        val buffer = mutableListOf<SharedSemanticDomNode>()
        var chunkOrdinal = 0

        fun flush() {
            if (buffer.isEmpty()) return
            val nodes = buffer.toList()
            buffer.clear()
            val local = buildLocalText(nodes)
            if (local.text.isBlank()) return
            target += SemanticParagraph(
                text = local.text,
                spans = local.spans,
                style = CssStyle(),
                elementId = element.elementId().takeIf { chunkOrdinal == 0 },
                cfi = element.cfiPath() ?: if (element === document.root) "/4" else null,
                startCharOffsetInSource = local.startCharOffsetInSource,
                blockIndex = nextBlockIndex++
            )
            chunkOrdinal++
        }

        element.children.forEach { child ->
            when (child) {
                is SharedSemanticDomText -> buffer += child
                is SharedSemanticDomElement -> when {
                    child.localName in SharedEpubNonRenderableTags -> Unit
                    child.localName == "br" || child.localName == "hr" || child.localName == "img" ||
                        child.localName == "math" || child.localName == "table" ||
                        child.localName == "ul" || child.localName == "ol" -> {
                        flush()
                        buildSpecific(child, target)
                    }
                    child.localName in SharedEpubHeaderLevels -> {
                        flush()
                        buildHeader(child, target)
                    }
                    child.localName == "pre" -> {
                        flush()
                        buildPreTextBlock(child, target)
                    }
                    child.localName in SharedEpubContainerTags || child.hasBlockDescendant() -> {
                        flush()
                        buildContainer(child, target)
                    }
                    child.localName in SharedEpubBlockTags -> {
                        flush()
                        buildTextBlock(child, target)
                    }
                    else -> buffer += child
                }
            }
        }
        flush()
    }

    private fun buildSpecific(element: SharedSemanticDomElement, target: MutableList<SemanticBlock>) {
        when (element.localName) {
            "br" -> target += SemanticSpacer(
                style = CssStyle(),
                elementId = element.elementId(),
                cfi = element.cfiPath(),
                isExplicitLineBreak = true,
                blockIndex = nextBlockIndex++
            )
            "hr" -> target += SemanticSpacer(
                style = CssStyle(),
                elementId = element.elementId(),
                cfi = element.cfiPath(),
                blockIndex = nextBlockIndex++
            )
            "img" -> buildImage(element)?.let { target += it }
            "math" -> {
                val text = buildLocalText(listOf(element)).text.trim()
                target += SemanticMath(
                    svgContent = null,
                    altText = text.ifBlank { "Equation" },
                    svgWidth = null,
                    svgHeight = null,
                    svgViewBox = null,
                    isFromMathJax = false,
                    style = CssStyle(),
                    elementId = element.elementId(),
                    cfi = element.cfiPath(),
                    blockIndex = nextBlockIndex++
                )
            }
            "table" -> buildTable(element)?.let { target += it }
            "ul", "ol" -> buildList(element)?.let { target += it }
        }
    }

    private fun buildImage(element: SharedSemanticDomElement): SemanticImage? {
        val src = element.attribute("src")?.trim()?.takeIf(String::isNotBlank) ?: return null
        return SemanticImage(
            path = src,
            altText = element.attribute("alt"),
            intrinsicWidth = element.attribute("width")?.toFloatOrNull(),
            intrinsicHeight = element.attribute("height")?.toFloatOrNull(),
            style = CssStyle(),
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildHeader(element: SharedSemanticDomElement, target: MutableList<SemanticBlock>) {
        val local = buildLocalText(listOf(element))
        if (local.text.isBlank()) return
        target += SemanticHeader(
            level = SharedEpubHeaderLevels[element.localName] ?: 1,
            text = local.text,
            spans = local.spans,
            style = CssStyle(),
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = local.startCharOffsetInSource,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildTextBlock(element: SharedSemanticDomElement, target: MutableList<SemanticBlock>) {
        val local = buildLocalText(listOf(element))
        if (local.text.isBlank()) return
        target += SemanticParagraph(
            text = local.text,
            spans = local.spans,
            style = CssStyle(),
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = local.startCharOffsetInSource,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildPreTextBlock(element: SharedSemanticDomElement, target: MutableList<SemanticBlock>) {
        val local = buildLocalText(listOf(element), preserveWhitespace = true)
        if (local.text.isBlank()) return
        target += SemanticParagraph(
            text = local.text,
            spans = local.spans,
            style = CssStyle(whiteSpace = "pre-wrap"),
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            startCharOffsetInSource = local.startCharOffsetInSource,
            blockIndex = nextBlockIndex++
        )
    }

    private fun buildList(element: SharedSemanticDomElement): SemanticList? {
        val ordered = element.localName == "ol"
        val list = SemanticList(
            items = emptyList(),
            isOrdered = ordered,
            style = CssStyle(),
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
        val items = buildListItems(element, inheritedOrdinal = element.attribute("start")?.toIntOrNull() ?: 1)
        if (items.isEmpty()) return null
        return list.copy(items = items)
    }

    private fun buildListItems(listElement: SharedSemanticDomElement, inheritedOrdinal: Int): List<SemanticListItem> {
        var ordinal = inheritedOrdinal
        val items = mutableListOf<SemanticListItem>()
        val ordered = listElement.localName == "ol"
        listElement.children.forEach { child ->
            if (child !is SharedSemanticDomElement || child.localName != "li") return@forEach
            val nestedLists = child.children
                .filterIsInstance<SharedSemanticDomElement>()
                .filter { it.localName == "ul" || it.localName == "ol" }
                .toSet()
            val local = buildLocalText(child.children.filterNot { it in nestedLists })
            val value = child.attribute("value")?.toIntOrNull() ?: ordinal
            if (local.text.isNotBlank()) {
                items += SemanticListItem(
                    text = local.text,
                    spans = local.spans,
                    style = CssStyle(),
                    elementId = child.elementId(),
                    cfi = child.cfiPath(),
                    startCharOffsetInSource = local.startCharOffsetInSource,
                    itemMarkerImage = null,
                    blockIndex = nextBlockIndex++,
                    markerText = if (ordered) "$value." else "•"
                )
            }
            ordinal = value + 1
            nestedLists.forEach { nested ->
                items += buildListItems(nested, inheritedOrdinal = nested.attribute("start")?.toIntOrNull() ?: 1)
            }
        }
        return items
    }

    private fun buildTable(element: SharedSemanticDomElement): SemanticTable? {
        val rows = mutableListOf<List<SemanticTableCell>>()
        fun collectRows(node: SharedSemanticDomElement) {
            if (node.localName == "tr") {
                val cells = node.children.mapNotNull { child ->
                    if (child !is SharedSemanticDomElement || child.localName !in setOf("td", "th")) return@mapNotNull null
                    val cellBlocks = mutableListOf<SemanticBlock>()
                    buildContainer(child, cellBlocks)
                    SemanticTableCell(
                        content = cellBlocks,
                        isHeader = child.localName == "th",
                        colspan = child.attribute("colspan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        style = CssStyle()
                    )
                }
                if (cells.isNotEmpty()) rows += cells
                return
            }
            node.children.forEach { child ->
                if (child is SharedSemanticDomElement) collectRows(child)
            }
        }
        collectRows(element)
        if (rows.isEmpty()) return null
        return SemanticTable(
            rows = rows,
            style = CssStyle(),
            elementId = element.elementId(),
            cfi = element.cfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    private class LocalTextResult(
        val text: String,
        val spans: List<SemanticSpan>,
        val startCharOffsetInSource: Int
    )

    private fun buildLocalText(
        nodes: List<SharedSemanticDomNode>,
        preserveWhitespace: Boolean = false
    ): LocalTextResult {
        val text = StringBuilder()
        val spans = mutableListOf<SemanticSpan>()

        fun spanStyleFor(tag: String): SpanStyle? = when {
            tag in SharedEpubBoldTags -> SpanStyle(fontWeight = FontWeight.Bold)
            tag in SharedEpubItalicTags -> SpanStyle(fontStyle = FontStyle.Italic)
            tag in SharedEpubUnderlineTags -> SpanStyle(textDecoration = TextDecoration.Underline)
            tag in SharedEpubStrikeTags -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            tag in SharedEpubCodeTags -> SpanStyle(fontFamily = FontFamily.Monospace)
            else -> null
        }

        fun process(node: SharedSemanticDomNode, inheritedLinkHref: String?) {
            when (node) {
                is SharedSemanticDomText -> text.append(node.text)
                is SharedSemanticDomElement -> when {
                    node.localName in SharedEpubNonRenderableTags -> Unit
                    node.localName == "br" -> text.append('\n')
                    node.localName == "img" -> Unit
                    else -> {
                        val start = text.length
                        val href = (if (node.localName == "a") node.attribute("href") else null)
                            ?: inheritedLinkHref
                        node.children.forEach { process(it, href) }
                        val end = text.length
                        val style = spanStyleFor(node.localName)
                        val elementId = node.elementId()
                        if (style != null || elementId != null || (href != null && node.localName == "a")) {
                            spans += SemanticSpan(
                                start = start,
                                end = end,
                                style = CssStyle(spanStyle = style ?: SpanStyle()),
                                linkHref = href,
                                tag = node.localName,
                                elementId = elementId
                            )
                        }
                    }
                }
            }
        }

        nodes.forEach { process(it, null) }

        val rawText = text.toString()
        val startOffset = nodes.firstOrNull()?.normalizedStart ?: -1
        if (preserveWhitespace) {
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) {
                return LocalTextResult(
                    text = "",
                    spans = emptyList(),
                    startCharOffsetInSource = startOffset.coerceAtLeast(0)
                )
            }
            val leadingTrim = rawText.indexOf(trimmed[0])
            val adjustedSpans = spans.mapNotNull { span ->
                val adjustedStart = (span.start - leadingTrim).coerceAtLeast(0)
                val adjustedEnd = (span.end - leadingTrim).coerceAtLeast(adjustedStart)
                if (adjustedEnd > adjustedStart || span.elementId != null) {
                    span.copy(start = adjustedStart, end = adjustedEnd)
                } else {
                    null
                }
            }
            return LocalTextResult(
                text = trimmed,
                spans = adjustedSpans,
                startCharOffsetInSource = startOffset.coerceAtLeast(0)
            )
        }
        val (normalizedText, mapping) = normalizeWithSourceMapping(rawText)
        if (normalizedText.isEmpty()) {
            return LocalTextResult(
                text = "",
                spans = emptyList(),
                startCharOffsetInSource = startOffset.coerceAtLeast(0)
            )
        }
        val inverse = IntArray(rawText.length) { -1 }
        mapping.forEachIndexed { index, sourceIndex ->
            if (sourceIndex in inverse.indices) inverse[sourceIndex] = index
        }
        val adjustedSpans = spans.mapNotNull { span ->
            val adjustedStart = inverse.getOrElse(span.start.coerceAtMost(rawText.lastIndex)) { -1 }
            val adjustedEnd = if (span.end <= 0) {
                -1
            } else {
                inverse.getOrElse((span.end - 1).coerceAtMost(rawText.lastIndex)) { -1 } + 1
            }
            if (adjustedStart >= 0 && adjustedEnd >= 0 && (adjustedEnd > adjustedStart || span.elementId != null)) {
                span.copy(start = adjustedStart, end = adjustedEnd.coerceAtLeast(adjustedStart))
            } else {
                null
            }
        }
        return LocalTextResult(
            text = normalizedText,
            spans = adjustedSpans,
            startCharOffsetInSource = startOffset.coerceAtLeast(0)
        )
    }
}
