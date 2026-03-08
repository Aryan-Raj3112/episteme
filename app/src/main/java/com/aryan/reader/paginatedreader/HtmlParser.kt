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

import android.graphics.BitmapFactory
import android.os.Build
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Selector
import java.io.File
import java.net.URLDecoder
import java.nio.file.Paths

private val unsupportedPseudoElementRegex = Regex("::?(before|after|first-letter|first-line|marker|selection)", RegexOption.IGNORE_CASE)

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

/**
 * The public entry point for converting HTML to a list of [SemanticBlock]s.
 * This function sets up a parsing context and delegates the work to a [SemanticHtmlParser] instance.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
    mathSvgCache: Map<String, String> = emptyMap()
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
        mathSvgCache
    ).parse(html)
}

/**
 * A stateful parser that holds the context for a single HTML-to-SemanticBlock conversion.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class SemanticHtmlParser(
    cssRules: OptimizedCssRules,
    private val textStyle: TextStyle,
    private val chapterAbsPath: String,
    private val extractionBasePath: String,
    private val density: Density,
    fontFamilyMap: Map<String, FontFamily>,
    private val constraints: Constraints,
    private val imageDimensionsCache: Map<String, Pair<Float, Float>>,
    private val mathSvgCache: Map<String, String>
) {
    private val styleCache = mutableMapOf<String, CssStyle>()
    private var combinedRules: OptimizedCssRules = cssRules
    private val currentFontFamilyMap: MutableMap<String, FontFamily> = fontFamilyMap.toMutableMap()
    private var nextBlockIndex = 0

    fun parse(html: String): List<SemanticBlock> {
        val document = Jsoup.parse(html, chapterAbsPath)
        val inlineCssContent = document.head().select("style").joinToString(separator = "\n") { it.data() }

        if (inlineCssContent.isNotBlank()) {
            Timber.d("Found inline <style> content in $chapterAbsPath. Parsing...")
            val inlineParseResult = CssParser.parse(
                cssContent = inlineCssContent,
                cssPath = chapterAbsPath,
                baseFontSizeSp = textStyle.fontSize.value,
                density = density.density,
                constraints = constraints,
                isDarkTheme = false // Semantic parsing is always theme-agnostic
            )

            if (inlineParseResult.fontFaces.isNotEmpty()) {
                val newFonts = loadFontFamilies(inlineParseResult.fontFaces, extractionBasePath)
                if (newFonts.isNotEmpty()) {
                    currentFontFamilyMap.putAll(newFonts)
                }
            }
            combinedRules = combinedRules.merge(inlineParseResult.rules)
        }

        val body = document.body()
        return body.children().flatMap { childElement ->
            parseNodeToSemanticBlocks(childElement, getElementStyle(body))
        }
    }

    private fun parseNodeToSemanticBlocks(
        element: Element,
        inheritedStyle: CssStyle
    ): List<SemanticBlock> {
        val elementOwnStyle = getElementStyle(element)
        val finalBlockStyle = elementOwnStyle.blockStyle.copy(
            listStyleType = elementOwnStyle.blockStyle.listStyleType ?: inheritedStyle.blockStyle.listStyleType,
            listStyleImage = elementOwnStyle.blockStyle.listStyleImage ?: inheritedStyle.blockStyle.listStyleImage
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
            textEmphasis = elementOwnStyle.textEmphasis ?: inheritedStyle.textEmphasis
        )

        if (finalStyle.display == "none") return emptyList()

        return elementToSemanticBlocks(element, finalStyle)
    }

    private fun getElementDescriptor(element: Element): String {
        return buildString {
            append(element.tagName())
            val id = element.id()
            if (id.isNotEmpty()) append('#').append(id)
            val classes = element.classNames()
            if (classes.isNotEmpty()) append('.').append(classes.sorted().joinToString("."))
        }
    }

    private fun getElementStyle(element: Element): CssStyle {
        val cacheKey = getElementDescriptor(element)

        val baseStyle = styleCache.getOrPut(cacheKey) {
            val potentialRules = mutableListOf<CssRule>()
            combinedRules.byTag[element.tagName()]?.let { potentialRules.addAll(it) }
            element.id().takeIf { it.isNotEmpty() }?.let { id ->
                combinedRules.byId[id]?.let { potentialRules.addAll(it) }
            }
            element.classNames().forEach { className ->
                combinedRules.byClass[className]?.let { potentialRules.addAll(it) }
            }
            potentialRules.addAll(combinedRules.otherComplex)

            val matchingRules = potentialRules.filter { rule ->
                if (unsupportedPseudoElementRegex.containsMatchIn(rule.selector.selector)) return@filter false
                try {
                    element.`is`(rule.selector.selector)
                } catch (e: Selector.SelectorParseException) {
                    Timber.w(e, "Jsoup failed to parse selector '${rule.selector.selector}'.")
                    false
                }
            }

            matchingRules.sortedBy { it.selector.specificity }.fold(CssStyle()) { acc, rule ->
                acc.merge(rule.style)
            }
        }

        var elementStyle = baseStyle
        val inlineStyleAttribute = element.attr("style")
        if (inlineStyleAttribute.isNotBlank()) {
            val inlineStyle = CssParser.parseProperties(inlineStyleAttribute, textStyle.fontSize.value, density.density, constraints, onlyImportant = false, isDarkTheme = false)
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

    private fun elementToSemanticBlocks(
        element: Element,
        elementStyle: CssStyle
    ): List<SemanticBlock> {
        val elementId = element.id().ifBlank { null }
        val cfi = element.getCfiPath()

        if (element.tagName().equals("br", ignoreCase = true)) {
            return listOf(SemanticSpacer(style = elementStyle, elementId = elementId, cfi = cfi, isExplicitLineBreak = true, blockIndex = nextBlockIndex++))
        }

        if (elementStyle.blockStyle.display == "flex") {
            val children = element.children().flatMap { child ->
                parseNodeToSemanticBlocks(child, elementStyle)
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
                    val children = element.children().flatMap { child ->
                        parseNodeToSemanticBlocks(child, elementStyle)
                    }
                    listOf(SemanticFlexContainer(children, elementStyle, elementId, cfi, blockIndex = nextBlockIndex++))
                } else {
                    parseContainer(element, elementStyle)
                }
            }
            "svg" -> parseSvgElementToSemantic(element, elementStyle)?.let { listOf(it) } ?: emptyList()
            "table" -> parseTableElementToSemantic(element, elementStyle)?.let { listOf(it) } ?: emptyList()
            "math-placeholder" -> parseMathPlaceholderToSemantic(element, elementStyle)
            "img" -> parseImageElementToSemantic(element, elementStyle)?.let { listOf(it) } ?: emptyList()
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val (text, spans) = buildSemanticTextAndSpans(element, elementStyle)
                if (text.isNotBlank()) {
                    val level = tagName.substring(1).toIntOrNull() ?: 1
                    listOf(SemanticHeader(level, text, spans, elementStyle, elementId, cfi, blockIndex = nextBlockIndex++))
                } else emptyList()
            }
            "hr" -> listOf(SemanticSpacer(style = elementStyle, elementId = elementId, cfi = cfi, blockIndex = nextBlockIndex++))
            "ul", "ol" -> parseListElementToSemantic(element, elementStyle)
            else -> {
                if (element.isBlock) {
                    parseContainer(element, elementStyle)
                } else {
                    val (text, spans) = buildSemanticTextAndSpans(element, elementStyle)
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

    private fun parseContainer(element: Element, style: CssStyle): List<SemanticBlock> {
        val children = mutableListOf<SemanticBlock>()
        val textNodesBuffer = mutableListOf<Node>()

        fun flushTextBuffer() {
            if (textNodesBuffer.isEmpty()) return
            val (text, spans) = buildSemanticTextAndSpansFromNodes(textNodesBuffer, style)
            if (text.isNotBlank()) {
                children.add(SemanticParagraph(text, spans, style, element.id().ifBlank { null }, element.getCfiPath(), blockIndex = nextBlockIndex++))            }
            textNodesBuffer.clear()
        }

        element.childNodes().forEach { node ->
            if (node is Element) {
                val isEffectivelyBlock = node.isBlock || node.tagName().lowercase() in listOf("img", "svg", "math-placeholder", "hr")

                if (isEffectivelyBlock) {
                    flushTextBuffer()
                    children.addAll(parseNodeToSemanticBlocks(node, style))
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
        rootStyle: CssStyle
    ): Pair<String, List<SemanticSpan>> {
        return buildSemanticTextAndSpansFromNodes(rootElement.childNodes(), rootStyle)
    }

    private fun buildSemanticTextAndSpansFromNodes(
        nodes: List<Node>,
        rootStyle: CssStyle
    ): Pair<String, List<SemanticSpan>> {
        val textBuilder = StringBuilder()
        val spans = mutableListOf<SemanticSpan>()

        fun processNode(node: Node, inheritedStyle: CssStyle) {
            when (node) {
                is TextNode -> {
                    var text = node.wholeText.replace('\n', ' ')
                    when (inheritedStyle.textTransform) {
                        "uppercase" -> text = text.uppercase()
                        "lowercase" -> text = text.lowercase()
                        "capitalize" -> text = text.capitalizeWords()
                    }
                    textBuilder.append(text)
                }
                is Element -> {
                    if (node.tagName().lowercase() == "br") {
                        textBuilder.append('\n'); return
                    }
                    val currentElementStyle = getElementStyle(node)
                    val newStyle = inheritedStyle.merge(currentElementStyle)
                    val startIndex = textBuilder.length
                    node.childNodes().forEach { processNode(it, newStyle) }
                    val endIndex = textBuilder.length

                    val elementId = node.id().ifBlank { null }
                    val isAnchor = node.tagName().lowercase() == "a" || elementId != null

                    // Capture span if it has content OR if it has an ID (anchor)
                    if (startIndex < endIndex || elementId != null) {
                        val href = if (node.tagName().lowercase() == "a") node.attr("href").ifBlank { null } else null
                        spans.add(SemanticSpan(
                            start = startIndex,
                            end = endIndex,
                            style = newStyle,
                            linkHref = href,
                            tag = node.tagName().lowercase(),
                            elementId = elementId // Pass the ID here
                        ))
                    }
                }
            }
        }
        nodes.forEach { processNode(it, rootStyle) }

        var processedText = textBuilder.toString()
        if (processedText.isNotEmpty() && processedText.last().isWhitespace()) {
            // 1. Find the index where trailing whitespace begins
            var newLength = processedText.length
            while (newLength > 0 && processedText[newLength - 1].isWhitespace()) {
                newLength--
            }

            // 2. Cut the text
            processedText = processedText.substring(0, newLength)

            // 3. Filter or Cap spans so they don't point to indices that no longer exist
            val adjustedSpans = spans.mapNotNull { span ->
                if (span.start >= newLength) {
                    // Span started in the whitespace area, remove it
                    null
                } else if (span.end > newLength) {
                    // Span ended in the whitespace area, cap it
                    span.copy(end = newLength)
                } else {
                    span
                }
            }
            return processedText to adjustedSpans
        }

        return processedText to spans
    }

    private fun parseMathPlaceholderToSemantic(element: Element, style: CssStyle): List<SemanticBlock> {
        val uniqueId = element.id()
        val svgContent = mathSvgCache[uniqueId]
        val altText = element.attr("alttext").ifBlank { "Equation" }
        var svgWidth: String? = null
        var svgHeight: String? = null
        var svgViewBox: String? = null
        if (svgContent != null) {
            val svgDoc = Jsoup.parse(svgContent)
            svgDoc.selectFirst("svg")?.let {
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
            Timber.d("Detected SVG acting as a wrapper for an image. Parsing as SemanticImage.")
            val href = imageElement.attr("href").ifBlank { imageElement.attr("xlink:href") }
            if (href.isBlank()) return null

            val imageFile = resolveImagePath(href) ?: return null

            val (width, height) = imageDimensionsCache[imageFile.absolutePath] ?: run {
                try {
                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        .also { BitmapFactory.decodeFile(imageFile.absolutePath, it) }
                        .let { Pair(it.outWidth.toFloat(), it.outHeight.toFloat()) }
                } catch (_: Exception) {
                    Pair(null, null)
                }
            }

            return SemanticImage(
                path = imageFile.absolutePath,
                altText = svgElement.selectFirst("title")?.text() ?: "Cover Image",
                intrinsicWidth = width,
                intrinsicHeight = height,
                style = style,
                elementId = svgElement.id().ifBlank { null },
                cfi = svgElement.getCfiPath(),
                blockIndex = nextBlockIndex++
            )
        }

        Timber.d("Parsing genuine SVG content into SemanticMath block.")
        val title = svgElement.selectFirst("title")?.text()
        val desc = svgElement.selectFirst("desc")?.text()
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

        val imageFile = resolveImagePath(src) ?: return null

        if (imageFile.extension.equals("svg", ignoreCase = true)) {
            return try {
                val svgContent = imageFile.readText()
                val svgElement = Jsoup.parseBodyFragment(svgContent).body().children().firstOrNull()
                svgElement?.let { parseSvgElementToSemantic(it, style) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read SVG from <img> tag: ${imageFile.path}")
                null
            }
        }

        val (width, height) = imageDimensionsCache[imageFile.absolutePath] ?: run {
            try {
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    .also { BitmapFactory.decodeFile(imageFile.absolutePath, it) }
                    .let { Pair(it.outWidth.toFloat(), it.outHeight.toFloat()) }
            } catch (_: Exception) {
                Pair(null, null)
            }
        }

        return SemanticImage(
            path = imageFile.absolutePath,
            altText = element.attr("alt"),
            intrinsicWidth = width,
            intrinsicHeight = height,
            style = style,
            elementId = element.id().ifBlank { null },
            cfi = element.getCfiPath(),
            blockIndex = nextBlockIndex++
        )
    }

    private fun resolveImagePath(src: String): File? {
        if (src.isBlank()) return null
        val decodedSrc = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }
        val parentPath = File(chapterAbsPath).parent ?: ""
        val relativePath = Paths.get(parentPath, decodedSrc).normalize().toString()
        val fromRelativeFile = File(extractionBasePath, relativePath)
        try {
            if (fromRelativeFile.exists()) return fromRelativeFile.canonicalFile
            val fromRootFile = File(extractionBasePath, decodedSrc)
            if (fromRootFile.exists()) return fromRootFile.canonicalFile
        } catch (e: java.io.IOException) {
            Timber.e(e, "Could not get canonical path for image at $src")
            return null
        }
        Timber.w("Image not found. Tried: ${fromRelativeFile.absolutePath} and ${File(extractionBasePath, decodedSrc).absolutePath}")
        return null
    }

    private fun parseListElementToSemantic(listElement: Element, listStyle: CssStyle): List<SemanticBlock> {
        val isOrdered = listElement.tagName().lowercase() == "ol"
        val items = listElement.children().mapNotNull { child ->
            if (child.tagName().lowercase() != "li") return@mapNotNull null
            val itemStyle = listStyle.merge(getElementStyle(child))
            val (text, spans) = buildSemanticTextAndSpans(child, itemStyle)
            val imageSrc = itemStyle.blockStyle.listStyleImage?.let { resolveImagePath(it)?.absolutePath }
            SemanticListItem(text, spans, itemStyle, child.id().ifBlank { null }, child.getCfiPath(), 0, imageSrc, blockIndex = nextBlockIndex++)
        }
        return listOf(SemanticList(items, isOrdered, listStyle, listElement.id().ifBlank { null }, listElement.getCfiPath(), blockIndex = nextBlockIndex++))
    }

    private fun parseTableElementToSemantic(tableElement: Element, tableStyle: CssStyle): SemanticTable? {
        val rows = tableElement.select("tr").mapNotNull { rowElement ->
            val rowStyle = getElementStyle(rowElement)
            if (rowStyle.display == "none") return@mapNotNull null

            val cells = rowElement.children().mapNotNull { cellElement ->
                val tagName = cellElement.tagName().lowercase()
                if (tagName !in listOf("td", "th")) return@mapNotNull null

                var cellCssStyle = getElementStyle(cellElement)
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

                val cellContent = parseContainer(cellElement, cellCssStyle)
                SemanticTableCell(cellContent, tagName == "th", cellElement.attr("colspan").toIntOrNull() ?: 1, cellCssStyle)
            }
            cells.ifEmpty { null }
        }
        if (rows.isEmpty()) return null
        return SemanticTable(rows, tableStyle, tableElement.id().ifBlank { null }, tableElement.getCfiPath(), blockIndex = nextBlockIndex++)
    }
}