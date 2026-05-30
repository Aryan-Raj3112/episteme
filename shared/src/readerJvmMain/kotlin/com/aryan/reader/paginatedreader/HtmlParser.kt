/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Selector
import java.util.ArrayDeque
import java.util.IdentityHashMap

private val unsupportedPseudoElementRegex = Regex("::?(first-letter|first-line|marker|selection)", RegexOption.IGNORE_CASE)
private val cssUrlRegex = Regex("""url\((['"]?)(.*?)\1\)""", RegexOption.IGNORE_CASE)
private const val MAX_SEMANTIC_TEXT_BLOCK_CHARS = 32_000
private const val TEXT_APPEND_SLICE_CHARS = 2_048
private val semanticBlockDescendantTags = setOf(
    "img",
    "svg",
    "math-placeholder",
    "table",
    "hr",
    "div",
    "p",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "ul",
    "ol",
    "li",
    "blockquote",
    "figure",
    "article",
    "aside",
    "header",
    "footer",
    "nav",
    "section",
    "main"
)
private val forcedStandaloneSemanticTags = setOf("img", "svg", "math-placeholder", "hr", "table")

interface HtmlResourceResolver {
    fun resolvePath(chapterAbsPath: String, extractionBasePath: String, src: String): String?
    fun readText(path: String): String?
    fun imageDimensions(path: String): Pair<Float?, Float?>?
}

interface HtmlFontFamilyLoader {
    fun load(fontFaces: List<FontFaceInfo>, extractionBasePath: String): Map<String, FontFamily>
}

object NoOpHtmlResourceResolver : HtmlResourceResolver {
    override fun resolvePath(chapterAbsPath: String, extractionBasePath: String, src: String): String? = null
    override fun readText(path: String): String? = null
    override fun imageDimensions(path: String): Pair<Float?, Float?>? = null
}

object NoOpHtmlFontFamilyLoader : HtmlFontFamilyLoader {
    override fun load(fontFaces: List<FontFaceInfo>, extractionBasePath: String): Map<String, FontFamily> = emptyMap()
}

private object HtmlParserLog {
    fun d(@Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun w(@Suppress("UNUSED_PARAMETER") throwable: Throwable, @Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun e(@Suppress("UNUSED_PARAMETER") throwable: Throwable, @Suppress("UNUSED_PARAMETER") message: String) = Unit
}

private fun Element.getCfiPath(): String {
    val path = mutableListOf<Int>()
    var currentNode: Node? = this
    while (currentNode != null && (currentNode !is Element || currentNode.tagName() != "body")) {
        val parent = currentNode.parent() ?: break
        val children = parent.childNodes().filter { node ->
            node is Element || (node is TextNode && node.text().trim().isNotEmpty())
        }
        val nodeIndex = children.indexOf(currentNode)
        if (nodeIndex == -1) {
            currentNode = parent
            continue
        }
        val cfiIndex = (nodeIndex * 2) + 2
        path.add(0, cfiIndex)
        currentNode = parent
    }
    path.add(0, 4)
    return "/" + path.joinToString("/")
}

private fun String.capitalizeWords(): String =
    split(' ').joinToString(" ") { word ->
        if (word.isNotEmpty()) word.replaceFirstChar { it.titlecase() } else ""
    }

private data class SemanticTextChunk(
    val text: String,
    val spans: List<SemanticSpan>,
    val startCharOffsetInSource: Int
)

/**
 * The public entry point for converting HTML to a list of [SemanticBlock]s.
 * This function sets up a parsing context and delegates the work to a [SemanticHtmlParser] instance.
 */
fun htmlToSemanticBlocks(
    html: String,
    cssRules: OptimizedCssRules,
    textStyle: TextStyle,
    chapterAbsPath: String,
    extractionBasePath: String,
    density: Density,
    fontFamilyMap: Map<String, FontFamily>,
    constraints: Constraints,
    imageDimensionsCache: Map<String, Pair<Float, Float>> = emptyMap(),
    mathSvgCache: Map<String, String> = emptyMap(),
    resourceResolver: HtmlResourceResolver = NoOpHtmlResourceResolver,
    fontFamilyLoader: HtmlFontFamilyLoader = NoOpHtmlFontFamilyLoader,
    adaptThemeColors: Boolean = false
): List<SemanticBlock> {
    return SemanticHtmlParser(
        cssRules,
        textStyle,
        chapterAbsPath,
        extractionBasePath,
        density,
        fontFamilyMap,
        constraints,
        imageDimensionsCache,
        mathSvgCache,
        resourceResolver,
        fontFamilyLoader,
        adaptThemeColors
    ).parse(html)
}

/**
 * A stateful parser that holds the context for a single HTML-to-SemanticBlock conversion.
 */
private class SemanticHtmlParser(
    cssRules: OptimizedCssRules,
    private val textStyle: TextStyle,
    private val chapterAbsPath: String,
    private val extractionBasePath: String,
    private val density: Density,
    fontFamilyMap: Map<String, FontFamily>,
    private val constraints: Constraints,
    private val imageDimensionsCache: Map<String, Pair<Float, Float>>,
    private val mathSvgCache: Map<String, String>,
    private val resourceResolver: HtmlResourceResolver,
    private val fontFamilyLoader: HtmlFontFamilyLoader,
    private val adaptThemeColors: Boolean
) {
    private val semanticBlockDescendantCache = IdentityHashMap<Element, Boolean>()
    private var combinedRules: OptimizedCssRules = cssRules
    private val currentFontFamilyMap: MutableMap<String, FontFamily> = fontFamilyMap.toMutableMap()
    private var nextBlockIndex = 0

    fun parse(html: String): List<SemanticBlock> {
        val document = Jsoup.parse(html, chapterAbsPath)
        val inlineCssContent = document.head().getElementsByTag("style").joinToString(separator = "\n") { it.data() }

        if (inlineCssContent.isNotBlank()) {
            HtmlParserLog.d("Found inline <style> content in $chapterAbsPath. Parsing...")
            val inlineParseResult = CssParser.parse(
                cssContent = inlineCssContent,
                cssPath = chapterAbsPath,
                baseFontSizeSp = textStyle.fontSize.value,
                density = density.density,
                constraints = constraints,
                isDarkTheme = false,
                adaptThemeColors = adaptThemeColors
            )

            if (inlineParseResult.fontFaces.isNotEmpty()) {
                val newFonts = fontFamilyLoader.load(inlineParseResult.fontFaces, extractionBasePath)
                if (newFonts.isNotEmpty()) {
                    currentFontFamilyMap.putAll(newFonts)
                }
            }
            combinedRules = combinedRules.merge(inlineParseResult.rules)
        }

        val body = document.body()
        return parseContainer(body, getElementStyle(body))
    }

    private inline fun Element.anyChildElement(predicate: (Element) -> Boolean): Boolean {
        childNodes().forEach { child ->
            if (child is Element && predicate(child)) {
                return true
            }
        }
        return false
    }

    private fun Element.hasSemanticBlockDescendant(): Boolean {
        semanticBlockDescendantCache[this]?.let { return it }

        if (anyChildElement { child -> child.tagName().lowercase() in semanticBlockDescendantTags }) {
            semanticBlockDescendantCache[this] = true
            return true
        }

        val stack = ArrayDeque<Element>()
        stack.add(this)
        val expanded = IdentityHashMap<Element, Boolean>()

        while (stack.isNotEmpty()) {
            val current = stack.peekLast()
            if (semanticBlockDescendantCache.containsKey(current)) {
                stack.removeLast()
                continue
            }

            if (expanded.put(current, true) == null) {
                current.childNodes().forEach { child ->
                    if (child is Element && !semanticBlockDescendantCache.containsKey(child)) {
                        stack.add(child)
                    }
                }
                continue
            }

            stack.removeLast()
            val hasSemanticDescendant = current.anyChildElement { child ->
                child.tagName().lowercase() in semanticBlockDescendantTags ||
                        semanticBlockDescendantCache[child] == true
            }
            semanticBlockDescendantCache[current] = hasSemanticDescendant
        }

        return semanticBlockDescendantCache[this] == true
    }

    private fun Element.isEffectivelySemanticBlock(): Boolean {
        val tagName = tagName().lowercase()
        return isBlock ||
                tagName in forcedStandaloneSemanticTags ||
                (!isBlock && hasSemanticBlockDescendant())
    }

    private fun parseNodeToSemanticBlocks(
        element: Element,
        inheritedStyle: CssStyle,
        inheritedLinkHref: String? = null
    ): List<SemanticBlock> {
        val elementOwnStyle = getElementStyle(element, inheritedStyle.customProperties)
        val finalBlockStyle = elementOwnStyle.blockStyle.copy(
            listStyleType = elementOwnStyle.blockStyle.listStyleType ?: inheritedStyle.blockStyle.listStyleType,
            listStyleImage = elementOwnStyle.blockStyle.listStyleImage ?: inheritedStyle.blockStyle.listStyleImage,
            visibility = elementOwnStyle.blockStyle.visibility ?: inheritedStyle.blockStyle.visibility
        )

        val finalStyle = elementOwnStyle.copy(
            spanStyle = inheritedStyle.spanStyle.merge(elementOwnStyle.spanStyle),
            paragraphStyle = inheritedStyle.paragraphStyle.merge(elementOwnStyle.paragraphStyle),
            blockStyle = finalBlockStyle,
            fontFamilies = elementOwnStyle.fontFamilies.ifEmpty { inheritedStyle.fontFamilies },
            fontSize = if (elementOwnStyle.fontSize.isSpecified) elementOwnStyle.fontSize else inheritedStyle.fontSize,
            textTransform = elementOwnStyle.textTransform ?: inheritedStyle.textTransform,
            hyphens = elementOwnStyle.hyphens ?: inheritedStyle.hyphens,
            fontVariantNumeric = elementOwnStyle.fontVariantNumeric ?: inheritedStyle.fontVariantNumeric,
            textEmphasis = elementOwnStyle.textEmphasis ?: inheritedStyle.textEmphasis,
            whiteSpace = elementOwnStyle.whiteSpace ?: inheritedStyle.whiteSpace,
            customProperties = inheritedStyle.customProperties + elementOwnStyle.customProperties
        )

        if (finalStyle.display == "none") return emptyList()

        val linkHref = element.linkHrefOrNull() ?: inheritedLinkHref
        return elementToSemanticBlocks(element, finalStyle.withResolvedBlockResources(), linkHref)
    }

    private fun CssRule.matchesElement(element: Element, pseudoElement: String? = null): Boolean {
        if (this.pseudoElement != pseudoElement) return false
        if (unsupportedPseudoElementRegex.containsMatchIn(selector.selector)) return false
        return try {
            element.`is`(selector.selector)
        } catch (e: Selector.SelectorParseException) {
            HtmlParserLog.w(e, "Jsoup failed to parse selector '${selector.selector}'.")
            false
        }
    }

    private fun rulesForElement(element: Element, pseudoElement: String? = null): List<CssRule> {
        val rules = combinedRules.toFlatList()
        return rules
            .asSequence()
            .filter { it.matchesElement(element, pseudoElement) }
            .sortedWith(compareBy<CssRule> { it.selector.specificity }.thenBy { it.sourceOrder })
            .toList()
    }

    private fun getElementStyle(element: Element, inheritedCustomProperties: Map<String, String> = emptyMap()): CssStyle {
        val baseStyle = rulesForElement(element).fold(CssStyle(customProperties = inheritedCustomProperties)) { acc, rule ->
            acc.merge(rule.style)
        }
        var elementStyle = baseStyle
        val inlineStyleAttribute = element.attr("style")
        if (inlineStyleAttribute.isNotBlank()) {
            val inlineStyle = CssParser.parseProperties(
                inlineStyleAttribute,
                textStyle.fontSize.value,
                density.density,
                constraints,
                onlyImportant = false,
                isDarkTheme = false,
                adaptThemeColors = adaptThemeColors,
                inheritedCustomProperties = elementStyle.customProperties
            )
            elementStyle = elementStyle.merge(inlineStyle)
        }

        element.attr("align").takeIf { it.isNotBlank() }?.let { align ->
            val textAlign = when (align.lowercase()) {
                "center" -> TextAlign.Center; "right" -> TextAlign.End
                "justify" -> TextAlign.Justify; "left" -> TextAlign.Start
                else -> null
            }
            if (textAlign != null) {
                elementStyle = elementStyle.merge(CssStyle(paragraphStyle = ParagraphStyle(textAlign = textAlign)))
            }
        }
        return elementStyle
    }

    private fun getPseudoElementStyle(
        element: Element,
        pseudoElement: String,
        inheritedStyle: CssStyle
    ): CssStyle {
        val pseudoStyle = rulesForElement(element, pseudoElement).fold(CssStyle()) { acc, rule ->
            acc.merge(rule.style)
        }
        return inheritedStyle.merge(pseudoStyle)
    }

    private fun firstCssUrl(value: String): String? {
        return cssUrlRegex.find(value)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun CssStyle.withResolvedBlockResources(): CssStyle {
        val resolvedBackgroundImage = blockStyle.backgroundImage?.let { raw ->
            val url = firstCssUrl(raw) ?: raw.takeIf { !it.contains("(") } ?: return@let raw
            resolveImagePath(url) ?: raw
        }
        return if (resolvedBackgroundImage == blockStyle.backgroundImage) {
            this
        } else {
            copy(blockStyle = blockStyle.copy(backgroundImage = resolvedBackgroundImage))
        }
    }

    private fun elementToSemanticBlocks(
        element: Element,
        elementStyle: CssStyle,
        inheritedLinkHref: String?
    ): List<SemanticBlock> {
        val elementId = element.id().ifBlank { null }
        val cfi = element.getCfiPath()

        if (element.tagName().equals("br", ignoreCase = true)) {
            return listOf(SemanticSpacer(style = elementStyle, elementId = elementId, cfi = cfi, isExplicitLineBreak = true, blockIndex = nextBlockIndex++))
        }

        if (elementStyle.blockStyle.display == "flex") {
            val children = element.children().flatMap { child ->
                parseNodeToSemanticBlocks(child, elementStyle, inheritedLinkHref)
            }
            return listOf(SemanticFlexContainer(children, elementStyle, elementId, cfi, blockIndex = nextBlockIndex++))
        }

        val result = when (val tagName = element.tagName().lowercase()) {
            "div", "header", "section", "article", "aside", "main", "footer", "nav", "figure" -> {
                val hasBoxStyles = elementStyle.blockStyle.backgroundColor.isSpecified ||
                        elementStyle.blockStyle.borderTop != null ||
                        elementStyle.blockStyle.borderRight != null ||
                        elementStyle.blockStyle.borderBottom != null ||
                        elementStyle.blockStyle.borderLeft != null ||
                        elementStyle.blockStyle.padding != BoxBorders() ||
                        elementStyle.blockStyle.borderTopLeftRadius > 0.dp ||
                        elementStyle.blockStyle.borderTopRightRadius > 0.dp ||
                        elementStyle.blockStyle.borderBottomRightRadius > 0.dp ||
                        elementStyle.blockStyle.borderBottomLeftRadius > 0.dp

                if (hasBoxStyles) {
                    val childStyle = elementStyle.copy(
                        blockStyle = elementStyle.blockStyle.copy(
                            backgroundColor = Color.Unspecified,
                            borderTop = null, borderRight = null, borderBottom = null, borderLeft = null,
                            padding = BoxBorders(),
                            margin = BoxBorders()
                        )
                    )
                    val children = parseContainer(element, childStyle, inheritedLinkHref)
                    listOf(SemanticFlexContainer(children, elementStyle, elementId, cfi, blockIndex = nextBlockIndex++))
                } else {
                    parseContainer(element, elementStyle, inheritedLinkHref)
                }
            }
            "svg" -> parseSvgElementToSemantic(element, elementStyle)?.let { listOf(it) } ?: emptyList()
            "table" -> parseTableElementToSemantic(element, elementStyle, inheritedLinkHref)?.let { listOf(it) } ?: emptyList()
            "math-placeholder" -> parseMathPlaceholderToSemantic(element, elementStyle)
            "img" -> parseImageElementToSemantic(element, elementStyle)?.let { listOf(it) } ?: emptyList()
            "p" -> {
                if (element.hasSemanticBlockDescendant()) {
                    parseContainer(element, elementStyle, inheritedLinkHref)
                } else {
                    textElementToSemanticParagraphs(element, elementStyle, inheritedLinkHref)
                }
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val hasNonTextChildren = element.hasSemanticBlockDescendant()
                if (hasNonTextChildren) {
                    val level = tagName.substring(1).toIntOrNull() ?: 1
                    val fontSizeMultiplier = when (level) {
                        1 -> 1.5f; 2 -> 1.4f; 3 -> 1.3f; 4 -> 1.2f; 5 -> 1.1f; else -> 1.0f
                    }
                    val headerStyle = elementStyle.copy(
                        spanStyle = elementStyle.spanStyle.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (textStyle.fontSize.value * fontSizeMultiplier).sp
                        )
                    )

                    val hasBoxStyles = headerStyle.blockStyle.backgroundColor.isSpecified ||
                            headerStyle.blockStyle.borderTop != null ||
                            headerStyle.blockStyle.borderRight != null ||
                            headerStyle.blockStyle.borderBottom != null ||
                            headerStyle.blockStyle.borderLeft != null ||
                            headerStyle.blockStyle.padding != BoxBorders() ||
                            headerStyle.blockStyle.borderTopLeftRadius > 0.dp ||
                            headerStyle.blockStyle.borderTopRightRadius > 0.dp ||
                            headerStyle.blockStyle.borderBottomRightRadius > 0.dp ||
                            headerStyle.blockStyle.borderBottomLeftRadius > 0.dp

                    if (hasBoxStyles) {
                        val childStyle = headerStyle.copy(
                            blockStyle = headerStyle.blockStyle.copy(
                                backgroundColor = Color.Unspecified,
                                borderTop = null, borderRight = null, borderBottom = null, borderLeft = null,
                                padding = BoxBorders(),
                                margin = BoxBorders()
                            )
                        )
                        val children = parseContainer(element, childStyle, inheritedLinkHref)
                        listOf(SemanticFlexContainer(children, headerStyle, elementId, cfi, blockIndex = nextBlockIndex++))
                    } else {
                        parseContainer(element, headerStyle, inheritedLinkHref)
                    }
                } else {
                    val (text, spans) = buildSemanticTextAndSpans(element, elementStyle, inheritedLinkHref)
                    if (text.isNotBlank()) {
                        val level = tagName.substring(1).toIntOrNull() ?: 1
                        listOf(SemanticHeader(level, text, spans, elementStyle, elementId, cfi, blockIndex = nextBlockIndex++))
                    } else emptyList()
                }
            }
            "hr" -> listOf(SemanticSpacer(style = elementStyle, elementId = elementId, cfi = cfi, blockIndex = nextBlockIndex++))
            "ul", "ol" -> parseListElementToSemantic(element, elementStyle, inheritedLinkHref)
            else -> {
                val hasBlockDescendant = !element.isBlock && element.hasSemanticBlockDescendant()
                if (element.isBlock || hasBlockDescendant) {
                    parseContainer(element, elementStyle, inheritedLinkHref)
                } else {
                    val (text, spans) = buildSemanticTextAndSpans(element, elementStyle, inheritedLinkHref)
                    if (text.isNotBlank()) {
                        listOf(SemanticParagraph(text, spans, elementStyle, elementId, cfi, blockIndex = nextBlockIndex++))
                    } else emptyList()
                }
            }
        }

        return if (elementId != null && result.isNotEmpty()) {
            val first = result.first()
            if (first.elementId == null) {
                listOf(first.withElementId(elementId)) + result.drop(1)
            } else result
        } else result
    }

    private fun textElementToSemanticParagraphs(
        element: Element,
        style: CssStyle,
        inheritedLinkHref: String?
    ): List<SemanticBlock> {
        val textChunks = buildSemanticTextAndSpanChunksFromNodes(
            nodes = element.childNodes(),
            rootStyle = style,
            rootElement = element,
            inheritedLinkHref = inheritedLinkHref
        )
        val elementId = element.id().ifBlank { null }
        val cfi = element.getCfiPath()
        return textChunks.mapIndexedNotNull { chunkIndex, chunk ->
            if (chunk.text.isBlank()) {
                null
            } else {
                SemanticParagraph(
                    text = chunk.text,
                    spans = chunk.spans,
                    style = style,
                    elementId = elementId.takeIf { chunkIndex == 0 },
                    cfi = cfi,
                    startCharOffsetInSource = chunk.startCharOffsetInSource,
                    blockIndex = nextBlockIndex++
                )
            }
        }
    }

    private fun parseContainer(
        element: Element,
        style: CssStyle,
        inheritedLinkHref: String? = null
    ): List<SemanticBlock> {
        val children = mutableListOf<SemanticBlock>()
        val textNodesBuffer = mutableListOf<Node>()
        val linkHref = element.linkHrefOrNull() ?: inheritedLinkHref

        fun flushTextBuffer() {
            if (textNodesBuffer.isEmpty()) return
            val textChunks = buildSemanticTextAndSpanChunksFromNodes(
                nodes = textNodesBuffer,
                rootStyle = style,
                inheritedLinkHref = linkHref
            )
            val containerElementId = element.id().ifBlank { null }
            val containerCfi = element.getCfiPath()
            textChunks.forEachIndexed { chunkIndex, chunk ->
                if (chunk.text.isBlank()) return@forEachIndexed

                children.add(
                    SemanticParagraph(
                        text = chunk.text,
                        spans = chunk.spans,
                        style = style,
                        elementId = containerElementId.takeIf { chunkIndex == 0 },
                        cfi = containerCfi,
                        startCharOffsetInSource = chunk.startCharOffsetInSource,
                        blockIndex = nextBlockIndex++
                    )
                )
            }
            textNodesBuffer.clear()
        }

        element.childNodes().forEach { node ->
            if (node is Element) {
                val isEffectivelyBlock = node.isEffectivelySemanticBlock()

                if (isEffectivelyBlock) {
                    flushTextBuffer()
                    children.addAll(parseNodeToSemanticBlocks(node, style, linkHref))
                } else {
                    textNodesBuffer.add(node)
                }
            } else {
                textNodesBuffer.add(node)
            }
        }

        flushTextBuffer()
        return children
    }

    private fun buildSemanticTextAndSpans(
        rootElement: Element,
        rootStyle: CssStyle,
        inheritedLinkHref: String? = null
    ): Pair<String, List<SemanticSpan>> {
        return buildSemanticTextAndSpansFromNodes(rootElement.childNodes(), rootStyle, rootElement, inheritedLinkHref)
    }

    private fun buildSemanticTextAndSpansFromNodes(
        nodes: List<Node>,
        rootStyle: CssStyle,
        rootElement: Element? = null,
        inheritedLinkHref: String? = null
    ): Pair<String, List<SemanticSpan>> {
        val chunks = buildSemanticTextAndSpanChunksFromNodes(nodes, rootStyle, rootElement, inheritedLinkHref)
        val firstChunk = chunks.firstOrNull() ?: return "" to emptyList()
        return firstChunk.text to firstChunk.spans
    }

    private fun buildSemanticTextAndSpanChunksFromNodes(
        nodes: List<Node>,
        rootStyle: CssStyle,
        rootElement: Element? = null,
        inheritedLinkHref: String? = null
    ): List<SemanticTextChunk> {
        val textBuilder = StringBuilder()
        val spans = mutableListOf<SemanticSpan>()
        val chunks = mutableListOf<SemanticTextChunk>()
        val activeSpans = mutableListOf<ActiveSemanticSpan>()
        var currentChunkStartOffset = 0

        fun addSpan(
            start: Int,
            end: Int,
            style: CssStyle,
            linkHref: String?,
            tag: String,
            elementId: String?
        ) {
            if (start < end || elementId != null) {
                spans.add(
                    SemanticSpan(
                        start = start.coerceAtLeast(0),
                        end = end.coerceAtLeast(start),
                        style = style,
                        linkHref = linkHref,
                        tag = tag,
                        elementId = elementId
                    )
                )
            }
        }

        fun trimTrailingWhitespace(
            text: String,
            sourceSpans: List<SemanticSpan>
        ): Pair<String, List<SemanticSpan>> {
            var newLength = text.length
            while (newLength > 0 && text[newLength - 1].isWhitespace()) {
                newLength--
            }

            if (newLength == text.length) return text to sourceSpans.toList()

            val adjustedSpans = sourceSpans.mapNotNull { span ->
                if (span.start >= newLength) {
                    null
                } else if (span.end > newLength) {
                    span.copy(end = newLength)
                } else {
                    span
                }
            }
            return text.substring(0, newLength) to adjustedSpans
        }

        fun flushChunk(trimTrailing: Boolean) {
            if (textBuilder.isEmpty()) return

            activeSpans.forEach { active ->
                addSpan(
                    start = active.startInChunk,
                    end = textBuilder.length,
                    style = active.style,
                    linkHref = active.linkHref,
                    tag = active.tag,
                    elementId = active.elementId
                )
            }

            if (!inheritedLinkHref.isNullOrBlank()) {
                addSpan(
                    start = 0,
                    end = textBuilder.length,
                    style = rootStyle,
                    linkHref = inheritedLinkHref,
                    tag = "a",
                    elementId = rootElement?.id()?.ifBlank { null }
                )
            }

            val rawText = textBuilder.toString()
            val rawLength = rawText.length
            val (trimmedText, trimmedSpans) = if (trimTrailing) {
                trimTrailingWhitespace(rawText, spans)
            } else {
                rawText to spans.toList()
            }
            if (trimmedText.isNotBlank()) {
                chunks.add(
                    SemanticTextChunk(
                        text = trimmedText,
                        spans = trimmedSpans,
                        startCharOffsetInSource = currentChunkStartOffset
                    )
                )
            }

            currentChunkStartOffset += rawLength
            textBuilder.clear()
            spans.clear()
            activeSpans.forEach { it.startInChunk = 0 }
        }

        fun appendText(text: String) {
            var offset = 0
            while (offset < text.length) {
                if (textBuilder.length >= MAX_SEMANTIC_TEXT_BLOCK_CHARS) {
                    flushChunk(trimTrailing = false)
                }
                val available = (MAX_SEMANTIC_TEXT_BLOCK_CHARS - textBuilder.length).coerceAtLeast(1)
                val end = (offset + available).coerceAtMost(text.length)
                textBuilder.append(text, offset, end)
                offset = end
                if (textBuilder.length >= MAX_SEMANTIC_TEXT_BLOCK_CHARS) {
                    flushChunk(trimTrailing = false)
                }
            }
        }

        fun normalizeTextForWhiteSpace(rawText: String, whiteSpace: String?): String {
            return when (whiteSpace) {
                "pre", "pre-wrap", "break-spaces" -> rawText
                "pre-line" -> rawText.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                else -> rawText.replace(Regex("\\s+"), " ")
            }
        }

        fun appendTransformedText(rawText: String, style: CssStyle) {
            val normalizedText = normalizeTextForWhiteSpace(rawText, style.whiteSpace)
            var start = 0
            while (start < normalizedText.length) {
                val end = (start + TEXT_APPEND_SLICE_CHARS).coerceAtMost(normalizedText.length)
                val normalizedSlice = buildString(end - start) {
                    for (i in start until end) {
                        append(if (normalizedText[i] == '\n' && style.whiteSpace !in listOf("pre", "pre-wrap", "pre-line", "break-spaces")) ' ' else normalizedText[i])
                    }
                }
                val transformedSlice = when (style.textTransform) {
                    "uppercase" -> normalizedSlice.uppercase()
                    "lowercase" -> normalizedSlice.lowercase()
                    "capitalize" -> normalizedSlice.capitalizeWords()
                    else -> normalizedSlice
                }
                appendText(transformedSlice)
                start = end
            }
        }

        fun materializeCssContent(rawContent: String?, element: Element): String? {
            if (rawContent.isNullOrBlank()) return null
            val content = rawContent.trim()
            if (content == "none" || content == "normal") return null
            val tokens = Regex("""attr\(([^)]+)\)|"((?:\\.|[^"])*)"|'((?:\\.|[^'])*)'""")
                .findAll(content)
                .mapNotNull { match ->
                    when {
                        match.groupValues[1].isNotBlank() -> element.attr(match.groupValues[1].trim()).ifBlank { null }
                        match.groupValues[2].isNotBlank() -> match.groupValues[2].replace("\\\"", "\"")
                        match.groupValues[3].isNotBlank() -> match.groupValues[3].replace("\\'", "'")
                        else -> null
                    }
                }
                .toList()
            return tokens.joinToString("").ifBlank {
                content.removeSurrounding("\"").removeSurrounding("'").takeIf { it.isNotBlank() }
            }
        }

        fun appendGeneratedContent(element: Element, inheritedStyle: CssStyle, pseudoElement: String) {
            val generatedStyle = getPseudoElementStyle(element, pseudoElement, inheritedStyle)
            if (generatedStyle.display == "none") return
            val text = materializeCssContent(generatedStyle.content, element) ?: return
            val start = textBuilder.length
            appendTransformedText(text, generatedStyle)
            val end = textBuilder.length
            addSpan(start, end, generatedStyle, null, "::$pseudoElement", element.id().ifBlank { null })
        }

        fun processNode(node: Node, inheritedStyle: CssStyle) {
            when (node) {
                is TextNode -> {
                    appendTransformedText(node.wholeText, inheritedStyle)
                }
                is Element -> {
                    if (node.tagName().lowercase() == "br") {
                        appendText("\n"); return
                    }
                    val currentElementStyle = getElementStyle(node, inheritedStyle.customProperties)
                    val newStyle = inheritedStyle.merge(currentElementStyle)
                    if (newStyle.display == "none") return
                    val tag = node.tagName().lowercase()
                    val href = node.linkHrefOrNull()
                        ?: activeSpans.asReversed().firstOrNull { !it.linkHref.isNullOrBlank() }?.linkHref
                        ?: inheritedLinkHref
                    val elementId = node.id().ifBlank { null }
                    val activeSpan = ActiveSemanticSpan(
                        startInChunk = textBuilder.length,
                        style = newStyle,
                        linkHref = href,
                        tag = tag,
                        elementId = elementId
                    )
                    activeSpans.add(activeSpan)
                    appendGeneratedContent(node, newStyle, "before")
                    node.childNodes().forEach { processNode(it, newStyle) }
                    appendGeneratedContent(node, newStyle, "after")
                    activeSpans.removeAt(activeSpans.lastIndex)
                    val endIndex = textBuilder.length

                    // Capture span if it has content OR if it has an ID (anchor)
                    addSpan(
                        start = activeSpan.startInChunk,
                        end = endIndex,
                        style = newStyle,
                        linkHref = href,
                        tag = tag,
                        elementId = elementId
                    )
                }
            }
        }
        rootElement?.let { appendGeneratedContent(it, rootStyle, "before") }
        nodes.forEach { processNode(it, rootStyle) }
        rootElement?.let { appendGeneratedContent(it, rootStyle, "after") }
        flushChunk(trimTrailing = true)
        return chunks
    }

    private data class ActiveSemanticSpan(
        var startInChunk: Int,
        val style: CssStyle,
        val linkHref: String?,
        val tag: String,
        val elementId: String?
    )

    private fun parseMathPlaceholderToSemantic(element: Element, style: CssStyle): List<SemanticBlock> {
        val uniqueId = element.id()
        val svgContent = mathSvgCache[uniqueId]
        val altText = element.attr("alttext").ifBlank { "Equation" }
        var svgWidth: String? = null
        var svgHeight: String? = null
        var svgViewBox: String? = null
        if (svgContent != null) {
            val svgDoc = Jsoup.parse(svgContent)
            svgDoc.getElementsByTag("svg").firstOrNull()?.let {
                svgWidth = it.attr("width")
                svgHeight = it.attr("height")
                svgViewBox = it.attr("viewBox")
            }
        }
        return listOf(
            SemanticMath(
                svgContent, altText, svgWidth, svgHeight, svgViewBox,
                isFromMathJax = true, style = style,
                elementId = element.id().ifBlank { null }, cfi = element.getCfiPath(), blockIndex = nextBlockIndex++
            )
        )
    }

    private fun parseSvgElementToSemantic(svgElement: Element, style: CssStyle): SemanticBlock? {
        val children = svgElement.children()
        val imageElement = children.firstOrNull()?.takeIf { children.size == 1 && it.tagName() == "image" }

        if (imageElement != null) {
            HtmlParserLog.d("Detected SVG acting as a wrapper for an image. Parsing as SemanticImage.")
            val href = imageElement.attr("href").ifBlank { imageElement.attr("xlink:href") }
            if (href.isBlank()) return null

            val imagePath = resolveImagePath(href) ?: return null

            val (width, height) = imageDimensionsCache[imagePath]
                ?: resourceResolver.imageDimensions(imagePath)
                ?: Pair(null, null)

            return SemanticImage(
                path = imagePath,
                altText = svgElement.getElementsByTag("title").firstOrNull()?.text() ?: "Cover Image",
                intrinsicWidth = width,
                intrinsicHeight = height,
                style = style,
                elementId = svgElement.id().ifBlank { null },
                cfi = svgElement.getCfiPath(),
                blockIndex = nextBlockIndex++
            )
        }

        HtmlParserLog.d("Parsing genuine SVG content into SemanticMath block.")
        val title = svgElement.getElementsByTag("title").firstOrNull()?.text()
        val desc = svgElement.getElementsByTag("desc").firstOrNull()?.text()
        val altText = title ?: desc ?: "SVG Image"

        return SemanticMath(
            svgContent = svgElement.outerHtml(),
            altText = altText,
            style = style,
            elementId = svgElement.id().ifBlank { null },
            cfi = svgElement.getCfiPath(),
            svgWidth = svgElement.attr("width").ifBlank { null },
            svgHeight = svgElement.attr("height").ifBlank { null },
            svgViewBox = svgElement.attr("viewBox").ifBlank { null },
            isFromMathJax = false,
            blockIndex = nextBlockIndex++
        )
    }

    private fun parseImageElementToSemantic(element: Element, style: CssStyle): SemanticBlock? {
        val src = element.attr("src")
        if (src.isBlank()) return null

        val imagePath = resolveImagePath(src) ?: return null

        if (imagePath.substringAfterLast('.', "").equals("svg", ignoreCase = true)) {
            return try {
                val svgContent = resourceResolver.readText(imagePath) ?: return null
                val svgElement = Jsoup.parseBodyFragment(svgContent).body().children().firstOrNull()
                svgElement?.let { parseSvgElementToSemantic(it, style) }
            } catch (e: Exception) {
                HtmlParserLog.e(e, "Failed to read SVG from <img> tag: $imagePath")
                null
            }
        }

        val (width, height) = imageDimensionsCache[imagePath]
            ?: resourceResolver.imageDimensions(imagePath)
            ?: Pair(null, null)

        return SemanticImage(
            path = imagePath,
            altText = element.attr("alt"),
            intrinsicWidth = width,
            intrinsicHeight = height,
            style = style,
            elementId = element.id().ifBlank { null },
            cfi = element.getCfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    private fun resolveImagePath(src: String): String? {
        if (src.isBlank()) return null
        return resourceResolver.resolvePath(chapterAbsPath, extractionBasePath, src)
    }

    private fun parseListElementToSemantic(
        listElement: Element,
        listStyle: CssStyle,
        inheritedLinkHref: String?
    ): List<SemanticBlock> {
        val isOrdered = listElement.tagName().lowercase() == "ol"
        val items = listElement.children().mapNotNull { child ->
            if (child.tagName().lowercase() != "li") return@mapNotNull null
            val itemStyle = listStyle.merge(getElementStyle(child, listStyle.customProperties)).withResolvedBlockResources()
            val (text, spans) = buildSemanticTextAndSpans(child, itemStyle, inheritedLinkHref)
            val imageSrc = itemStyle.blockStyle.listStyleImage?.let { resolveImagePath(it) }
            SemanticListItem(text, spans, itemStyle, child.id().ifBlank { null }, child.getCfiPath(), 0, imageSrc, blockIndex = nextBlockIndex++)
        }
        return listOf(SemanticList(items, isOrdered, listStyle, listElement.id().ifBlank { null }, listElement.getCfiPath(), blockIndex = nextBlockIndex++))
    }

    private fun parseTableElementToSemantic(
        tableElement: Element,
        tableStyle: CssStyle,
        inheritedLinkHref: String?
    ): SemanticTable? {
        val rows = tableElement.getElementsByTag("tr").mapNotNull { rowElement ->
            val rowStyle = getElementStyle(rowElement, tableStyle.customProperties)
            if (rowStyle.display == "none") return@mapNotNull null

            val cells = rowElement.children().mapNotNull { cellElement ->
                val tagName = cellElement.tagName().lowercase()
                if (tagName !in listOf("td", "th")) return@mapNotNull null

                var cellCssStyle = getElementStyle(cellElement, rowStyle.customProperties).withResolvedBlockResources()
                if (cellCssStyle.display == "none") return@mapNotNull null

                if (!cellCssStyle.blockStyle.backgroundColor.isSpecified) {
                    if (rowStyle.blockStyle.backgroundColor.isSpecified) {
                        cellCssStyle = cellCssStyle.copy(
                            blockStyle = cellCssStyle.blockStyle.copy(
                                backgroundColor = rowStyle.blockStyle.backgroundColor
                            )
                        )
                    }
                }

                val cellContent = parseContainer(cellElement, cellCssStyle, inheritedLinkHref)
                SemanticTableCell(cellContent, tagName == "th", cellElement.attr("colspan").toIntOrNull() ?: 1, cellCssStyle)
            }
            cells.ifEmpty { null }
        }
        if (rows.isEmpty()) return null
        return SemanticTable(rows, tableStyle, tableElement.id().ifBlank { null }, tableElement.getCfiPath(), blockIndex = nextBlockIndex++)
    }

    private fun Element.linkHrefOrNull(): String? {
        val normalizedTagName = tagName().substringAfter(':')
        if (!normalizedTagName.equals("a", ignoreCase = true)) return null

        return attr("href")
            .ifBlank { attr("xlink:href") }
            .ifBlank { attr("l:href") }
            .ifBlank { attr("epub:href") }
            .ifBlank { null }
    }
}
