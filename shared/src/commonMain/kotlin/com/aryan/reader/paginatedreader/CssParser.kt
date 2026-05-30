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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import kotlin.math.roundToInt

private const val IMPORTANT_SPECIFICITY_BOOST = 10_000

private object ReaderCssLog {
    fun d(@Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun w(@Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun w(@Suppress("UNUSED_PARAMETER") throwable: Throwable, @Suppress("UNUSED_PARAMETER") message: String) = Unit
    fun e(@Suppress("UNUSED_PARAMETER") throwable: Throwable, @Suppress("UNUSED_PARAMETER") message: String) = Unit
}

private fun Color.luminance(): Float {
    if (!this.isSpecified) return 0f
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

private fun resolveCssRelativePath(cssPath: String, rawSrc: String): String {
    if (rawSrc.startsWith("/") || rawSrc.contains("://")) return rawSrc
    val normalizedBase = cssPath.replace('\\', '/').substringBeforeLast('/', "")
    val parts = ArrayDeque<String>()
    (if (normalizedBase.isBlank()) rawSrc else "$normalizedBase/$rawSrc")
        .replace('\\', '/')
        .split('/')
        .forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
    return parts.joinToString("/")
}

object CssParser {
    private val FONT_FACE_REGEX = "@font-face\\s*\\{([^}]+)\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val URL_REGEX = "url\\((['\"]?)(.*?)\\1\\)".toRegex()
    private val ID_SELECTOR_REGEX = Regex("#[^\\s,]+")
    private val CLASS_ATTRIBUTE_SELECTOR_REGEX = Regex("\\.[^\\s,]+|\\[[^]]+]|:(?!:)[^\\s,]+")
    private val TYPE_PSEUDO_ELEMENT_SELECTOR_REGEX = Regex("(?<![.#\\[])\\b[a-zA-Z-]+|::[a-zA-Z-]+")
    private data class FontSource(val url: String, val format: String?)

    // Regex to identify simple, single-part selectors for fast categorization
    private val SIMPLE_TAG_SELECTOR = Regex("^[a-zA-Z0-9]+$")
    private val SIMPLE_CLASS_SELECTOR = Regex("^\\.[a-zA-Z0-9_-]+$")
    private val SIMPLE_ID_SELECTOR = Regex("^#[a-zA-Z0-9_-]+$")

    private val BORDER_WIDTH_KEYWORDS = mapOf(
        "thin" to 1.dp,
        "medium" to 3.dp,
        "thick" to 5.dp
    )

    fun adaptColorForTheme(
        color: Color,
        isDarkTheme: Boolean,
        isBackground: Boolean,
        themeBackground: Color = Color.Unspecified,
        themeText: Color = Color.Unspecified
    ): Color {
        if (!color.isSpecified) return color
        if (color.alpha < 0.9f && color != Color.Transparent) return color
        if (color == Color.Transparent) return color

        if (!themeBackground.isSpecified || !themeText.isSpecified) {
            val luminance = color.luminance()
            return if (isDarkTheme) {
                if (isBackground) {
                    if (luminance > 0.9) Color.Transparent else color
                } else {
                    if (luminance < 0.2) Color.White.copy(alpha = 0.87f) else color
                }
            } else {
                if (isBackground) {
                    if (luminance < 0.1) Color.Transparent else color
                } else {
                    if (luminance > 0.8) Color.Black.copy(alpha = 0.87f) else color
                }
            }
        }

        val bgLuminance = themeBackground.luminance()
        val colorLuminance = color.luminance()

        val l1 = maxOf(bgLuminance, colorLuminance)
        val l2 = minOf(bgLuminance, colorLuminance)
        val contrast = (l1 + 0.05f) / (l2 + 0.05f)

        if (isBackground) {
            return if (isDarkTheme && colorLuminance > 0.5f) {
                Color.Transparent
            } else if (!isDarkTheme && colorLuminance < 0.2f) {
                Color.Transparent
            } else {
                color
            }
        } else {
            if (contrast >= 4.5f) {
                return color
            }

            return themeText.takeIf { it.isSpecified } ?: color
        }
    }

    private data class CssBlock(val header: String, val body: String, val sourceOrder: Int)
    private data class ParsedSelector(val selector: String, val pseudoElement: String?)

    private fun splitDeclarations(declarations: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var parenDepth = 0
        var bracketDepth = 0

        declarations.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> {
                    current.append(ch)
                    escaped = true
                }
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '"' || ch == '\'' -> {
                    current.append(ch)
                    quote = ch
                }
                ch == '(' -> {
                    current.append(ch)
                    parenDepth++
                }
                ch == ')' -> {
                    current.append(ch)
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                }
                ch == '[' -> {
                    current.append(ch)
                    bracketDepth++
                }
                ch == ']' -> {
                    current.append(ch)
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                }
                ch == ';' && parenDepth == 0 && bracketDepth == 0 -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result += current.toString()
        return result
    }

    private fun stripCssComments(css: String): String {
        val result = StringBuilder(css.length)
        var index = 0
        var quote: Char? = null
        var escaped = false
        while (index < css.length) {
            val ch = css[index]
            if (escaped) {
                result.append(ch)
                escaped = false
                index++
                continue
            }
            if (ch == '\\') {
                result.append(ch)
                escaped = true
                index++
                continue
            }
            if (quote != null) {
                result.append(ch)
                if (ch == quote) quote = null
                index++
                continue
            }
            if (ch == '"' || ch == '\'') {
                quote = ch
                result.append(ch)
                index++
                continue
            }
            if (ch == '/' && index + 1 < css.length && css[index + 1] == '*') {
                index += 2
                while (index + 1 < css.length && !(css[index] == '*' && css[index + 1] == '/')) {
                    index++
                }
                index = (index + 2).coerceAtMost(css.length)
                continue
            }
            result.append(ch)
            index++
        }
        return result.toString()
    }

    private fun parseCssBlocks(
        css: String,
        constraints: Constraints,
        isDarkTheme: Boolean,
        adaptThemeColors: Boolean,
        sourceCounter: IntArray = intArrayOf(0)
    ): Pair<List<CssBlock>, List<String>> {
        val blocks = mutableListOf<CssBlock>()
        val fontFaceBlocks = mutableListOf<String>()
        var index = 0

        fun skipWhitespace() {
            while (index < css.length && css[index].isWhitespace()) index++
        }

        fun findMatchingBrace(openBrace: Int): Int {
            var depth = 1
            var i = openBrace + 1
            var quote: Char? = null
            var escaped = false
            while (i < css.length) {
                val ch = css[i]
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    quote != null -> if (ch == quote) quote = null
                    ch == '"' || ch == '\'' -> quote = ch
                    ch == '{' -> depth++
                    ch == '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
                i++
            }
            return css.lastIndex
        }

        while (index < css.length) {
            skipWhitespace()
            if (index >= css.length) break
            val headerStart = index
            var quote: Char? = null
            var escaped = false
            var parenDepth = 0
            var bracketDepth = 0
            while (index < css.length) {
                val ch = css[index]
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    quote != null -> if (ch == quote) quote = null
                    ch == '"' || ch == '\'' -> quote = ch
                    ch == '(' -> parenDepth++
                    ch == ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    ch == '[' -> bracketDepth++
                    ch == ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    ch == ';' && parenDepth == 0 && bracketDepth == 0 -> {
                        index++
                        break
                    }
                    ch == '{' && parenDepth == 0 && bracketDepth == 0 -> break
                }
                index++
            }
            if (index >= css.length || css[index] != '{') continue

            val header = css.substring(headerStart, index).trim()
            val close = findMatchingBrace(index)
            val body = css.substring(index + 1, close.coerceAtMost(css.length))
            index = close + 1

            when {
                header.startsWith("@media", ignoreCase = true) -> {
                    if (mediaQueryApplies(header, constraints, isDarkTheme, adaptThemeColors)) {
                        val nested = parseCssBlocks(body, constraints, isDarkTheme, adaptThemeColors, sourceCounter)
                        blocks += nested.first
                        fontFaceBlocks += nested.second
                    }
                }
                header.startsWith("@supports", ignoreCase = true) -> {
                    val nested = parseCssBlocks(body, constraints, isDarkTheme, adaptThemeColors, sourceCounter)
                    blocks += nested.first
                    fontFaceBlocks += nested.second
                }
                header.startsWith("@font-face", ignoreCase = true) -> fontFaceBlocks += body
                header.startsWith("@") -> Unit
                header.isNotBlank() -> blocks += CssBlock(header, body, sourceCounter[0]++)
            }
        }

        return blocks to fontFaceBlocks
    }

    private fun mediaQueryApplies(
        header: String,
        constraints: Constraints,
        isDarkTheme: Boolean,
        adaptThemeColors: Boolean
    ): Boolean {
        val query = header.removePrefix("@media").trim().lowercase()
        if (query.isBlank() || query == "all" || query == "screen") return true
        if (query.contains("print")) return false
        if (query.contains("prefers-color-scheme")) {
            val wantsDark = query.contains("prefers-color-scheme") && query.contains("dark")
            val wantsLight = query.contains("prefers-color-scheme") && query.contains("light")
            if (!adaptThemeColors) return !wantsDark
            if (wantsDark && !isDarkTheme) return false
            if (wantsLight && isDarkTheme) return false
        }
        Regex("""min-width\s*:\s*([^)]+)""").findAll(query).forEach { match ->
            val minWidth = parseCssDimension(match.groupValues[1], 16f, 1f, constraints.maxWidth)
            if (minWidth.isSpecified && minWidth.value > constraints.maxWidth) return false
        }
        Regex("""max-width\s*:\s*([^)]+)""").findAll(query).forEach { match ->
            val maxWidth = parseCssDimension(match.groupValues[1], 16f, 1f, constraints.maxWidth)
            if (maxWidth.isSpecified && maxWidth.value < constraints.maxWidth) return false
        }
        return true
    }

    private fun splitCssList(value: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var parenDepth = 0
        var bracketDepth = 0
        value.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> {
                    current.append(ch)
                    escaped = true
                }
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '"' || ch == '\'' -> {
                    current.append(ch)
                    quote = ch
                }
                ch == '(' -> {
                    current.append(ch)
                    parenDepth++
                }
                ch == ')' -> {
                    current.append(ch)
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                }
                ch == '[' -> {
                    current.append(ch)
                    bracketDepth++
                }
                ch == ']' -> {
                    current.append(ch)
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                }
                ch == delimiter && parenDepth == 0 && bracketDepth == 0 -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result += current.toString()
        return result
    }

    private fun splitCssTokens(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var parenDepth = 0
        value.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> {
                    current.append(ch)
                    escaped = true
                }
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '"' || ch == '\'' -> {
                    current.append(ch)
                    quote = ch
                }
                ch == '(' -> {
                    current.append(ch)
                    parenDepth++
                }
                ch == ')' -> {
                    current.append(ch)
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                }
                ch.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotBlank()) {
                        result += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result += current.toString()
        return result
    }

    private fun parseSelector(selector: String): ParsedSelector {
        var pseudoElement: String? = null
        var sanitized = selector
        Regex("::?(before|after)\\b", RegexOption.IGNORE_CASE).find(sanitized)?.let { match ->
            pseudoElement = match.groupValues[1].lowercase()
            sanitized = sanitized.removeRange(match.range)
        }
        sanitized = sanitized
            .replace(Regex(":(link|visited|hover|active|focus)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("::?(first-letter|first-line|marker|selection)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex(":root\\b", RegexOption.IGNORE_CASE), "html")
            .trim()
        return ParsedSelector(sanitized, pseudoElement)
    }

    private fun calculateSpecificity(selector: String): Int {
        val ids = ID_SELECTOR_REGEX.findAll(selector).count()
        val classesAndAttributes = CLASS_ATTRIBUTE_SELECTOR_REGEX.findAll(selector).count()
        val elementsAndPseudos = TYPE_PSEUDO_ELEMENT_SELECTOR_REGEX.findAll(selector).count()
        val specificity = ids * 100 + classesAndAttributes * 10 + elementsAndPseudos
        return specificity
    }

    fun parse(
        cssContent: String,
        cssPath: String?,
        baseFontSizeSp: Float,
        density: Float,
        constraints: Constraints,
        isDarkTheme: Boolean,
        themeBackgroundColor: Color = Color.Unspecified,
        themeTextColor: Color = Color.Unspecified,
        adaptThemeColors: Boolean = true
    ): OptimizedCssParseResult {
        val byTag = mutableMapOf<String, MutableList<CssRule>>()
        val byClass = mutableMapOf<String, MutableList<CssRule>>()
        val byId = mutableMapOf<String, MutableList<CssRule>>()
        val otherComplex = mutableListOf<CssRule>()
        val fontFaces = mutableListOf<FontFaceInfo>()

        val cleanedCss = stripCssComments(cssContent)
        val (styleBlocks, fontFaceBlocks) = parseCssBlocks(
            css = cleanedCss,
            constraints = constraints,
            isDarkTheme = isDarkTheme,
            adaptThemeColors = adaptThemeColors
        )
        fontFaceBlocks.forEach { properties ->
            parseFontFace(properties, cssPath)?.let { fontFaces.add(it) }
        }

        val rootCustomProperties = styleBlocks
            .filter { block ->
                splitCssList(block.header, ',').any { selector ->
                    val normalized = selector.trim().lowercase()
                    normalized == ":root" || normalized == "html" || normalized == "body"
                }
            }
            .fold(emptyMap<String, String>()) { acc, block -> acc + extractCustomProperties(block.body, acc) }

        val allRules = mutableListOf<CssRule>()
        styleBlocks.forEach { block ->
            val selectorGroup = block.header.trim()
            val propertiesGroup = block.body.trim()
            val selectors = splitCssList(selectorGroup, ',').map { it.trim() }

            for (originalSelector in selectors) {
                if (originalSelector.isBlank()) {
                    continue
                }
                val parsedSelector = parseSelector(originalSelector)
                val sanitizedSelector = parsedSelector.selector
                if (sanitizedSelector.isBlank()) {
                    continue
                }
                val specificity = calculateSpecificity(originalSelector)
                val normalStyle = parseProperties(
                    properties = propertiesGroup,
                    baseFontSizeSp = baseFontSizeSp,
                    density = density,
                    constraints = constraints,
                    onlyImportant = false,
                    isDarkTheme = isDarkTheme,
                    themeBackgroundColor = themeBackgroundColor,
                    themeTextColor = themeTextColor,
                    adaptThemeColors = adaptThemeColors,
                    inheritedCustomProperties = rootCustomProperties
                )
                val importantStyle = parseProperties(
                    properties = propertiesGroup,
                    baseFontSizeSp = baseFontSizeSp,
                    density = density,
                    constraints = constraints,
                    onlyImportant = true,
                    isDarkTheme = isDarkTheme,
                    themeBackgroundColor = themeBackgroundColor,
                    themeTextColor = themeTextColor,
                    adaptThemeColors = adaptThemeColors,
                    inheritedCustomProperties = rootCustomProperties
                )

                fun addRule(style: CssStyle, spec: Int) {
                    if (style == CssStyle()) return
                    val rule = CssRule(
                        selector = CssSelector(sanitizedSelector, spec),
                        style = style,
                        pseudoElement = parsedSelector.pseudoElement,
                        sourceOrder = block.sourceOrder
                    )
                    allRules += rule
                    when {
                        parsedSelector.pseudoElement == null && SIMPLE_ID_SELECTOR.matches(sanitizedSelector) ->
                            byId.getOrPut(sanitizedSelector.substring(1)) { mutableListOf() }.add(rule)
                        parsedSelector.pseudoElement == null && SIMPLE_CLASS_SELECTOR.matches(sanitizedSelector) ->
                            byClass.getOrPut(sanitizedSelector.substring(1)) { mutableListOf() }.add(rule)
                        parsedSelector.pseudoElement == null && SIMPLE_TAG_SELECTOR.matches(sanitizedSelector) ->
                            byTag.getOrPut(sanitizedSelector) { mutableListOf() }.add(rule)
                        else -> otherComplex.add(rule)
                    }
                }

                addRule(normalStyle, specificity)
                addRule(importantStyle, specificity + IMPORTANT_SPECIFICITY_BOOST)
            }
        }
        val optimizedRules = OptimizedCssRules(byTag, byClass, byId, otherComplex, allRules)
        return OptimizedCssParseResult(optimizedRules, fontFaces)
    }

    private fun parseFontFace(properties: String, cssPath: String?): FontFaceInfo? {
        val propsMap = splitDeclarations(properties)
            .map { it.trim().split(':', limit = 2).map { part -> part.trim() } }
            .filter { it.size == 2 && it[0].isNotBlank() }
            .associate { it[0].lowercase() to it[1] }

        val fontFamily = propsMap["font-family"]?.removeSurrounding("\"")?.removeSurrounding("'")?.lowercase()
        val srcString = propsMap["src"]
        ReaderCssLog.d("Parsing font-face for family: $fontFamily. Raw src string: $srcString")

        if (fontFamily == null || srcString == null) {
            ReaderCssLog.w("Incomplete @font-face rule: missing font-family or src.")
            return null
        }

        val urlWithFormatRegex = "url\\((['\"]?)(.*?)\\1\\)\\s*format\\((['\"]?)(.*?)\\3\\)".toRegex()

        val sources = srcString.split(Regex(",(?=\\s*url\\()")).mapNotNull { part ->
            val trimmedPart = part.trim()
            ReaderCssLog.d("Processing src part: '$trimmedPart'")

            urlWithFormatRegex.find(trimmedPart)?.let {
                ReaderCssLog.d("Matched url with format(). URL: ${it.groupValues[2]}, Format: ${it.groupValues[4]}")
                FontSource(url = it.groupValues[2], format = it.groupValues[4].lowercase().removeSurrounding("'"))
            } ?: URL_REGEX.find(trimmedPart)?.let {
                val url = it.groupValues[2]
                ReaderCssLog.d("Matched url() only. URL: '$url'")
                val format = when {
                    url.startsWith("data:", ignoreCase = true) -> {
                        val mediaType = url.substringAfter("data:").substringBefore(';')
                        ReaderCssLog.d("Data URI detected. Media type: '$mediaType'")
                        when {
                            mediaType.contains("opentype") -> "opentype"
                            mediaType.contains("truetype") -> "truetype"
                            mediaType.contains("woff2") -> "woff2"
                            mediaType.contains("woff") -> "woff"
                            else -> {
                                ReaderCssLog.w("Unknown data URI media type: $mediaType")
                                null
                            }
                        }
                    }
                    url.endsWith(".woff2", ignoreCase = true) -> "woff2"
                    url.endsWith(".woff", ignoreCase = true) -> "woff"
                    url.endsWith(".otf", ignoreCase = true) -> "opentype"
                    url.endsWith(".ttf", ignoreCase = true) -> "truetype"
                    else -> {
                        ReaderCssLog.w("Could not determine format from URL: $url")
                        null
                    }
                }
                ReaderCssLog.d("Determined format: '$format'")
                if (format != null) {
                    FontSource(url = url, format = format)
                } else {
                    null
                }
            }
        }

        if (sources.isEmpty()) {
            ReaderCssLog.w("Could not parse any valid source from @font-face src: $srcString")
            return null
        }

        val preferredSource = sources.minByOrNull {
            when (it.format) {
                "opentype", "otf" -> 1
                "truetype", "ttf" -> 2
                "woff2" -> 3
                "woff" -> 4
                else -> 5
            }
        }!!

        val rawSrc = preferredSource.url
        ReaderCssLog.d("Selected font source for '$fontFamily': '${preferredSource.url}' with format '${preferredSource.format}'")

        val finalSrc = if (cssPath != null && !rawSrc.startsWith("data:")) {
            try {
                resolveCssRelativePath(cssPath, rawSrc)
            } catch (e: Exception) {
                ReaderCssLog.e(e, "Could not resolve font path for src '$rawSrc' in css '$cssPath'")
                rawSrc // Fallback to the raw path on error
            }
        } else {
            rawSrc
        }
        val fontWeight = when (propsMap["font-weight"]) {
            "bold" -> FontWeight.Bold
            "700" -> FontWeight.Bold
            "600" -> FontWeight.SemiBold
            "500" -> FontWeight.Medium
            "300" -> FontWeight.Light
            "200" -> FontWeight.ExtraLight
            "100" -> FontWeight.Thin
            else -> FontWeight.Normal
        }

        val fontStyle = when (propsMap["font-style"]) {
            "italic", "oblique" -> FontStyle.Italic
            else -> FontStyle.Normal
        }

        return FontFaceInfo(fontFamily, finalSrc, fontWeight, fontStyle)
    }

    internal fun parseProperties(
        properties: String,
        baseFontSizeSp: Float,
        density: Float,
        constraints: Constraints,
        onlyImportant: Boolean,
        isDarkTheme: Boolean,
        themeBackgroundColor: Color = Color.Unspecified,
        themeTextColor: Color = Color.Unspecified,
        adaptThemeColors: Boolean = true,
        inheritedCustomProperties: Map<String, String> = emptyMap()
    ): CssStyle {
        val localCustomProperties = if (!onlyImportant) extractCustomProperties(properties, inheritedCustomProperties) else emptyMap()
        val customProperties = inheritedCustomProperties + localCustomProperties
        var hasApplicableDeclaration = false
        var spanStyle = SpanStyle()
        var paragraphStyle = ParagraphStyle()
        var padding = BoxBorders()
        var width: Dp = Dp.Unspecified
        var maxWidth: Dp = Dp.Unspecified
        var height: Dp = Dp.Unspecified
        var minWidth: Dp = Dp.Unspecified
        var minHeight: Dp = Dp.Unspecified
        var maxHeight: Dp = Dp.Unspecified
        var backgroundColor: Color = Color.Unspecified

        // Changed: Track the max width found to prioritize visible borders
        var maxBorderWidthFound: Dp = 0.dp
        var finalBorderColor: Color? = null
        var finalBorderStyle: String? = null

        var fontFamilies: List<String> = emptyList()
        var fontSize: TextUnit = TextUnit.Unspecified
        var pageBreakInsideAvoid = false
        var listStyleType: String? = null
        var listStyleImage: String? = null
        var display: String? = null
        val containerWidthPx = constraints.maxWidth
        var pageBreakAfterAvoid = false
        var textTransform: String? = null
        var boxSizing: String? = null
        var float: String? = null
        var clear: String? = null
        var content: String? = null
        var position: String? = null
        var left: Dp = Dp.Unspecified
        var top: Dp = Dp.Unspecified
        var right: Dp = Dp.Unspecified
        var bottom: Dp = Dp.Unspecified
        var flexDirection: String? = null
        var justifyContent: String? = null
        var alignItems: String? = null
        var filter: String? = null
        var borderCollapse: String? = null
        var borderSpacing: Dp = 0.dp
        var borderRadius: Dp = 0.dp
        var overflow: String? = null
        var breakBefore: String? = null
        var breakAfter: String? = null
        var breakInside: String? = null
        var widows: Int = 2
        var orphans: Int = 2
        var visibility: String? = null
        var objectFit: String? = null
        var objectPosition: String? = null
        var backgroundImage: String? = null
        var whiteSpace: String? = null
        var verticalAlign: String? = null
        var hyphens: String? = null
        var fontVariantNumeric: String? = null
        var textEmphasisStyleString: String? = null
        var textEmphasisColor: Color? = null
        var textEmphasisPositionString: String? = null
        var marginTopStr: String? = null
        var marginRightStr: String? = null
        var marginBottomStr: String? = null
        var marginLeftStr: String? = null

        var wordSpacing: TextUnit = TextUnit.Unspecified
        var textDecorationStyle: String? = null
        var textDecorationColor: Color = Color.Unspecified
        var textUnderlineOffset: Dp = Dp.Unspecified

        var borderTopWidth: Dp? = null
        var borderRightWidth: Dp? = null
        var borderBottomWidth: Dp? = null
        var borderLeftWidth: Dp? = null

        var borderTopStyle: String? = null
        var borderRightStyle: String? = null
        var borderBottomStyle: String? = null
        var borderLeftStyle: String? = null

        var borderTopColor: Color? = null
        var borderRightColor: Color? = null
        var borderBottomColor: Color? = null
        var borderLeftColor: Color? = null

        var borderTopLeftRadius: Dp = 0.dp
        var borderTopRightRadius: Dp = 0.dp
        var borderBottomRightRadius: Dp = 0.dp
        var borderBottomLeftRadius: Dp = 0.dp

        fun maybeAdaptColor(color: Color, isBackground: Boolean): Color {
            return if (adaptThemeColors) {
                this@CssParser.adaptColorForTheme(color, isDarkTheme, isBackground, themeBackgroundColor, themeTextColor)
            } else {
                color
            }
        }

        splitDeclarations(properties).filter { it.isNotBlank() }.forEach { prop ->
            val parts = prop.split(':', limit = 2).map { it.trim() }
            if (parts.size == 2) {
                val key = parts[0].lowercase()
                val valueWithImportant = parts[1]
                val isImportant = valueWithImportant.contains("!important", ignoreCase = true)
                if (key.startsWith("--")) {
                    return@forEach
                }

                if (isImportant != onlyImportant) {
                    return@forEach
                }
                hasApplicableDeclaration = true
                val rawValue = if (isImportant) {
                    valueWithImportant.replace(Regex("\\s*!important", RegexOption.IGNORE_CASE), "").trim()
                } else {
                    valueWithImportant.trim()
                }
                val value = resolveCssVariables(rawValue, customProperties)
                val valueLower = value.trim().lowercase()

                fun updateUnifiedBorder(
                    widthStr: String?,
                    colorStr: String?,
                    styleStr: String?
                ) {
                    val parsedWidth = widthStr?.let { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) } ?: 0.dp
                    val parsedColor = colorStr?.let { parseColor(it, spanStyle.color) }?.let { maybeAdaptColor(it, isBackground = false) }

                    val isExplicitWidth = widthStr != null

                    if (parsedWidth > maxBorderWidthFound) {
                        maxBorderWidthFound = parsedWidth
                        if (parsedColor != null) finalBorderColor = parsedColor
                        if (styleStr != null) finalBorderStyle = styleStr
                    } else if (parsedWidth == maxBorderWidthFound && maxBorderWidthFound > 0.dp) {
                        if (parsedColor != null) finalBorderColor = parsedColor
                        if (styleStr != null) finalBorderStyle = styleStr
                    } else if (!isExplicitWidth) {
                        if (parsedColor != null) finalBorderColor = parsedColor
                        if (styleStr != null) finalBorderStyle = styleStr
                    }
                }

                when (key) {
                    "font-family" -> {
                        fontFamilies = splitCssList(value, ',')
                            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").lowercase() }
                    }
                    "font-size" -> {
                        val trimmedValue = value.trim().lowercase()
                        fontSize = if (trimmedValue.endsWith("%")) {
                            val percentage = trimmedValue.removeSuffix("%").toFloatOrNull()
                            if (percentage != null) {
                                (percentage / 100f).em
                            } else {
                                TextUnit.Unspecified
                            }
                        } else {
                            parseCssDimensionToTextUnit(value, containerWidthPx, density)
                        }
                    }
                    "font-weight" -> {
                        spanStyle = spanStyle.copy(fontWeight = when (valueLower) {
                            "bold" -> FontWeight.Bold
                            "700" -> FontWeight.Bold
                            "600" -> FontWeight.SemiBold
                            "500" -> FontWeight.Medium
                            "300" -> FontWeight.Light
                            "200" -> FontWeight.ExtraLight
                            "100" -> FontWeight.Thin
                            "normal" -> FontWeight.Normal
                            "400" -> FontWeight.Normal
                            else -> valueLower.toIntOrNull()?.let { FontWeight(it) } ?: spanStyle.fontWeight
                        })
                    }
                    "font-style" -> {
                        if (valueLower == "italic" || valueLower == "oblique") spanStyle = spanStyle.copy(fontStyle = FontStyle.Italic)
                        else if (valueLower == "normal") spanStyle = spanStyle.copy(fontStyle = FontStyle.Normal)
                    }
                    "color" -> {
                        parseColor(value, spanStyle.color)?.let {
                            spanStyle = spanStyle.copy(color = maybeAdaptColor(it, isBackground = false))
                        }
                    }
                    "text-align" -> {
                        val align = when (valueLower) {
                            "center" -> TextAlign.Center
                            "right", "end" -> TextAlign.End
                            "justify" -> TextAlign.Justify
                            "left", "start" -> TextAlign.Start
                            else -> TextAlign.Start
                        }
                        paragraphStyle = paragraphStyle.copy(textAlign = align)
                    }
                    "line-height" -> {
                        val trimmedValue = value.trim()
                        val lineHeight = when {
                            trimmedValue.endsWith("%") -> {
                                val percentage = trimmedValue.removeSuffix("%").toFloatOrNull()
                                if (percentage != null) {
                                    (percentage / 100f).em
                                } else {
                                    TextUnit.Unspecified
                                }
                            }
                            trimmedValue.toFloatOrNull() != null && trimmedValue.none { it.isLetter() } -> {
                                trimmedValue.toFloatOrNull()?.em ?: TextUnit.Unspecified
                            }
                            else -> parseCssDimensionToTextUnit(trimmedValue, containerWidthPx, density)
                        }
                        if (lineHeight != TextUnit.Unspecified) {
                            paragraphStyle = paragraphStyle.copy(lineHeight = lineHeight)
                        }
                    }
                    "text-indent" -> {
                        val indent = parseCssDimensionToTextUnit(value, containerWidthPx, density)
                        if (indent != TextUnit.Unspecified) {
                            paragraphStyle = paragraphStyle.copy(textIndent = TextIndent(firstLine = indent))
                        }
                    }
                    "text-decoration" -> {
                        val parts = splitCssTokens(valueLower)
                        val decos = mutableListOf<TextDecoration>()

                        if (parts.contains("underline")) decos.add(TextDecoration.Underline)
                        if (parts.contains("line-through")) decos.add(TextDecoration.LineThrough)

                        if (parts.contains("none")) {
                            spanStyle = spanStyle.copy(textDecoration = TextDecoration.None)
                        } else if (decos.isNotEmpty()) {
                            spanStyle = spanStyle.copy(textDecoration = TextDecoration.combine(decos))
                        }

                        val styles = listOf("solid", "double", "dotted", "dashed", "wavy")
                        parts.firstOrNull { it in styles }?.let { textDecorationStyle = it }
                        parts.firstNotNullOfOrNull { parseColor(it, spanStyle.color) }?.let { color ->
                            textDecorationColor = maybeAdaptColor(color, isBackground = false)
                        }
                    }
                    "word-spacing" -> {
                        val trimmedValue = value.trim()
                        wordSpacing = if (trimmedValue.lowercase() == "normal") {
                            TextUnit.Unspecified
                        } else {
                            parseCssDimensionToTextUnit(value, containerWidthPx, density)
                        }
                    }
                    "text-decoration-style" -> {
                        textDecorationStyle = valueLower
                    }
                    "text-decoration-color" -> {
                        parseColor(value, spanStyle.color)?.let {
                            textDecorationColor = maybeAdaptColor(it, isBackground = false)
                        }
                    }
                    "text-underline-offset" -> {
                        textUnderlineOffset = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    }
                    "letter-spacing" -> {
                        val letterSpacing = parseCssDimensionToTextUnit(value, containerWidthPx, density)
                        if (letterSpacing != TextUnit.Unspecified) {
                            spanStyle = spanStyle.copy(letterSpacing = letterSpacing)
                        }
                    }
                    "text-transform" -> {
                        textTransform = when (valueLower) {
                            "uppercase", "lowercase", "capitalize", "none" -> valueLower
                            else -> null
                        }
                    }
                    "font-variant" -> {
                        if (value.contains("small-caps")) {
                            spanStyle = spanStyle.copy(fontFeatureSettings = "\"smcp\" on")
                        }
                    }
                    "margin" -> {
                        val marginParts = splitCssTokens(value)
                        when (marginParts.size) {
                            1 -> {
                                marginTopStr = marginParts[0]; marginRightStr = marginParts[0]; marginBottomStr = marginParts[0]; marginLeftStr = marginParts[0]
                            }
                            2 -> {
                                marginTopStr = marginParts[0]; marginBottomStr = marginParts[0]
                                marginRightStr = marginParts[1]; marginLeftStr = marginParts[1]
                            }
                            3 -> {
                                marginTopStr = marginParts[0]
                                marginRightStr = marginParts[1]; marginLeftStr = marginParts[1]
                                marginBottomStr = marginParts[2]
                            }
                            4 -> {
                                marginTopStr = marginParts[0]; marginRightStr = marginParts[1]; marginBottomStr = marginParts[2]; marginLeftStr = marginParts[3]
                            }
                        }
                    }
                    "margin-top" -> marginTopStr = value
                    "margin-bottom" -> marginBottomStr = value
                    "margin-left" -> marginLeftStr = value
                    "margin-right" -> marginRightStr = value

                    "padding" -> padding = parseBoxBorders(value, baseFontSizeSp, density, containerWidthPx)
                    "padding-top" -> padding = padding.copy(top = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx))
                    "padding-bottom" -> padding = padding.copy(bottom = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx))
                    "padding-left" -> padding = padding.copy(left = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx))
                    "padding-right" -> padding = padding.copy(right = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx))

                    "width" -> width = parseCssDimension(value, baseFontSizeSp, density, containerWidthPx)
                    "max-width" -> maxWidth = parseCssDimension(value, baseFontSizeSp, density, containerWidthPx)
                    "height" -> height = parseCssDimension(value, baseFontSizeSp, density, containerWidthPx)
                    "min-width" -> minWidth = parseCssDimension(value, baseFontSizeSp, density, containerWidthPx)
                    "min-height" -> minHeight = parseCssDimension(value, baseFontSizeSp, density, containerWidthPx)
                    "max-height" -> maxHeight = parseCssDimension(value, baseFontSizeSp, density, containerWidthPx)

                    "background-color" -> {
                        val originalColor = parseColor(value, spanStyle.color) ?: Color.Unspecified
                        backgroundColor = maybeAdaptColor(originalColor, isBackground = true)
                    }
                    "background-image" -> backgroundImage = value.takeIf { valueLower != "none" }
                    "background" -> {
                        URL_REGEX.find(value)?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }?.let {
                            backgroundImage = it
                        }
                        splitCssTokens(value).firstNotNullOfOrNull { token ->
                            parseColor(token, spanStyle.color)
                        }?.let { color ->
                            backgroundColor = maybeAdaptColor(color, isBackground = true)
                        }
                    }

                    // Border Properties
                    "border-width" -> {
                        val widths = parseShorthand4(value, baseFontSizeSp, density, containerWidthPx)
                        borderTopWidth = widths[0]; borderRightWidth = widths[1]; borderBottomWidth = widths[2]; borderLeftWidth = widths[3]
                    }
                    "border-style" -> {
                        val styles = parseShorthand4Strings(value)
                        borderTopStyle = styles[0]; borderRightStyle = styles[1]; borderBottomStyle = styles[2]; borderLeftStyle = styles[3]
                    }
                    "border-color" -> {
                        val colors = parseShorthand4Colors(value)
                        borderTopColor = colors[0]; borderRightColor = colors[1]; borderBottomColor = colors[2]; borderLeftColor = colors[3]
                    }

                    "border-top-width" -> borderTopWidth = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "border-right-width" -> borderRightWidth = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "border-bottom-width" -> borderBottomWidth = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "border-left-width" -> borderLeftWidth = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)

                    "border-top-style" -> borderTopStyle = valueLower
                    "border-right-style" -> borderRightStyle = valueLower
                    "border-bottom-style" -> borderBottomStyle = valueLower
                    "border-left-style" -> borderLeftStyle = valueLower

                    "border-top-color" -> borderTopColor = parseColor(value, spanStyle.color)
                    "border-right-color" -> borderRightColor = parseColor(value, spanStyle.color)
                    "border-bottom-color" -> borderBottomColor = parseColor(value, spanStyle.color)
                    "border-left-color" -> borderLeftColor = parseColor(value, spanStyle.color)

                    "border-top" -> {
                        val (w, s, c) = parseBorderShorthand(value, baseFontSizeSp, density, containerWidthPx)
                        if (w != null) borderTopWidth = w
                        if (s != null) borderTopStyle = s
                        if (c != null) borderTopColor = c
                    }
                    "border-right" -> {
                        val (w, s, c) = parseBorderShorthand(value, baseFontSizeSp, density, containerWidthPx)
                        if (w != null) borderRightWidth = w
                        if (s != null) borderRightStyle = s
                        if (c != null) borderRightColor = c
                    }
                    "border-bottom" -> {
                        val (w, s, c) = parseBorderShorthand(value, baseFontSizeSp, density, containerWidthPx)
                        if (w != null) borderBottomWidth = w
                        if (s != null) borderBottomStyle = s
                        if (c != null) borderBottomColor = c
                    }
                    "border-left" -> {
                        val (w, s, c) = parseBorderShorthand(value, baseFontSizeSp, density, containerWidthPx)
                        if (w != null) borderLeftWidth = w
                        if (s != null) borderLeftStyle = s
                        if (c != null) borderLeftColor = c
                    }

                    "border" -> {
                        val (w, s, c) = parseBorderShorthand(value, baseFontSizeSp, density, containerWidthPx)
                        if (w != null) { borderTopWidth = w; borderRightWidth = w; borderBottomWidth = w; borderLeftWidth = w }
                        if (s != null) { borderTopStyle = s; borderRightStyle = s; borderBottomStyle = s; borderLeftStyle = s }
                        if (c != null) { borderTopColor = c; borderRightColor = c; borderBottomColor = c; borderLeftColor = c }
                    }

                    "border-radius" -> {
                        val radii = parseShorthand4(value, baseFontSizeSp, density, containerWidthPx)
                        borderTopLeftRadius = radii[0]
                        borderTopRightRadius = radii[1]
                        borderBottomRightRadius = radii[2]
                        borderBottomLeftRadius = radii[3]
                    }
                    "border-top-left-radius" -> borderTopLeftRadius = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "border-top-right-radius" -> borderTopRightRadius = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "border-bottom-right-radius" -> borderBottomRightRadius = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "border-bottom-left-radius" -> borderBottomLeftRadius = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)

                    "border-collapse" -> {
                        if (valueLower in listOf("collapse", "separate")) {
                            borderCollapse = valueLower
                        }
                    }
                    "border-spacing" -> {
                        borderSpacing = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    }

                    "list-style-type" -> {
                        listStyleType = valueLower
                    }
                    "list-style-image" -> {
                        URL_REGEX.find(value)?.groupValues?.get(2)?.let {
                            listStyleImage = it
                        }
                    }
                    "list-style" -> {
                        URL_REGEX.find(value)?.groupValues?.get(2)?.takeIf { it.isNotBlank() }?.let {
                            listStyleImage = it
                        }
                        val positions = setOf("inside", "outside")
                        splitCssTokens(valueLower)
                            .firstOrNull { token -> token !in positions && !token.startsWith("url(") }
                            ?.let { listStyleType = it }
                    }
                    "page-break-inside" -> {
                        if (valueLower == "avoid") {
                            pageBreakInsideAvoid = true
                        }
                        breakInside = normalizeBreakValue(valueLower)
                    }
                    "page-break-after" -> {
                        if (valueLower == "avoid") {
                            pageBreakAfterAvoid = true
                        }
                        breakAfter = normalizeBreakValue(valueLower)
                    }
                    "page-break-before" -> breakBefore = normalizeBreakValue(valueLower)
                    "break-before" -> breakBefore = normalizeBreakValue(valueLower)
                    "break-after" -> breakAfter = normalizeBreakValue(valueLower)
                    "break-inside" -> breakInside = normalizeBreakValue(valueLower)
                    "display" -> display = valueLower
                    "flex-direction" -> flexDirection = valueLower
                    "justify-content" -> justifyContent = valueLower
                    "align-items" -> alignItems = valueLower
                    "filter" -> filter = valueLower
                    "box-sizing" -> boxSizing = valueLower
                    "content" -> content = value
                    "position" -> position = valueLower
                    "left" -> left = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "right" -> right = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "top" -> top = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "bottom" -> bottom = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "float" -> {
                        if (valueLower in listOf("left", "right", "none")) {
                            float = valueLower
                        }
                    }
                    "hyphens", "-webkit-hyphens", "-moz-hyphens", "-epub-hyphens", "adobe-hyphenate" -> {
                        if (valueLower in listOf("auto", "manual", "none")) {
                            hyphens = valueLower
                        }
                    }
                    "white-space" -> {
                        if (valueLower in listOf("normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces")) {
                            whiteSpace = valueLower
                        }
                    }
                    "visibility" -> {
                        if (valueLower in listOf("visible", "hidden", "collapse")) visibility = valueLower
                    }
                    "overflow" -> {
                        if (valueLower in listOf("visible", "hidden", "clip", "scroll", "auto")) overflow = valueLower
                    }
                    "font-variant-numeric" -> {
                        fontVariantNumeric = valueLower
                    }
                    "widows" -> widows = valueLower.toIntOrNull()?.coerceAtLeast(1) ?: widows
                    "orphans" -> orphans = valueLower.toIntOrNull()?.coerceAtLeast(1) ?: orphans
                    "vertical-align" -> verticalAlign = valueLower
                    "object-fit" -> objectFit = valueLower
                    "object-position" -> objectPosition = rawValue
                    "clear" -> {
                        if (valueLower in listOf("left", "right", "both", "none")) {
                            clear = valueLower
                        }
                    }
                    "text-emphasis", "-epub-text-emphasis" -> {
                        textEmphasisStyleString = value
                    }
                    "text-emphasis-style", "-epub-text-emphasis-style" -> {
                        textEmphasisStyleString = value
                    }
                    "text-emphasis-color", "-epub-text-emphasis-color" -> {
                        textEmphasisColor = parseColor(value, spanStyle.color)?.let { maybeAdaptColor(it, isBackground = false) }
                    }
                    "text-emphasis-position", "-epub-text-emphasis-position" -> {
                        if (valueLower in listOf("over", "under")) {
                            textEmphasisPositionString = valueLower
                        }
                    }
                }
            }
        }
        if (!hasApplicableDeclaration && localCustomProperties.isEmpty()) {
            return CssStyle()
        }
        val finalHorizontalAlign = if (marginLeftStr == "auto" && marginRightStr == "auto") "center" else null

        val margin = BoxBorders(
            top = marginTopStr?.let { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) } ?: 0.dp,
            bottom = marginBottomStr?.let { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) } ?: 0.dp,
            left = marginLeftStr.takeIf { it != "auto" }?.let { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) } ?: 0.dp,
            right = marginRightStr.takeIf { it != "auto" }?.let { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) } ?: 0.dp
        )

        // ... [TextEmphasis object creation code remains same] ...
        val textEmphasis = if (textEmphasisStyleString != null) {
            val parts = textEmphasisStyleString.split(' ').filter { it.isNotBlank() }
            var fill: String? = null
            var style: String? = null

            parts.forEach { part ->
                when (part) {
                    "filled", "open" -> fill = part
                    "dot", "circle", "double-circle", "triangle", "sesame" -> style = part
                    else -> {
                        style = part.removeSurrounding("'").removeSurrounding("\"")
                    }
                }
            }
            TextEmphasis(
                style = style,
                fill = fill,
                color = textEmphasisColor ?: Color.Unspecified,
                position = textEmphasisPositionString
            )
        } else {
            null
        }

        val finalBorder = if (maxBorderWidthFound > 0.dp && finalBorderStyle != null) {
            val borderColor = finalBorderColor ?: spanStyle.color.takeIf { it.isSpecified } ?: Color.Black
            BorderStyle(
                width = maxBorderWidthFound,
                color = borderColor,
                style = finalBorderStyle
            )
        } else null

        fun makeBorder(width: Dp?, style: String?, color: Color?): BorderStyle? {
            val finalWidth = width ?: if (style != null && style != "none" && style != "hidden") 3.dp else 0.dp
            val finalStyle = style ?: "none"
            val finalColor = color ?: spanStyle.color.takeIf { it.isSpecified } ?: Color.Black

            val adaptedColor = maybeAdaptColor(finalColor, isBackground = false)

            if (finalWidth > 0.dp && finalStyle != "none" && finalStyle != "hidden") {
                return BorderStyle(finalWidth, adaptedColor, finalStyle)
            }
            return null
        }

        val finalBorderTop = makeBorder(borderTopWidth, borderTopStyle, borderTopColor)
        val finalBorderRight = makeBorder(borderRightWidth, borderRightStyle, borderRightColor)
        val finalBorderBottom = makeBorder(borderBottomWidth, borderBottomStyle, borderBottomColor)
        val finalBorderLeft = makeBorder(borderLeftWidth, borderLeftStyle, borderLeftColor)

        val blockStyle = BlockStyle(
            margin = margin, padding = padding, width = width, maxWidth = maxWidth, height = height,
            backgroundColor = backgroundColor,
            borderTop = finalBorderTop,
            borderRight = finalBorderRight,
            borderBottom = finalBorderBottom,
            borderLeft = finalBorderLeft,
            borderTopLeftRadius = borderTopLeftRadius,
            borderTopRightRadius = borderTopRightRadius,
            borderBottomRightRadius = borderBottomRightRadius,
            borderBottomLeftRadius = borderBottomLeftRadius,
            listStyleType = listStyleType,
            listStyleImage = listStyleImage,
            pageBreakInsideAvoid = pageBreakInsideAvoid,
            pageBreakAfterAvoid = pageBreakAfterAvoid,
            boxSizing = boxSizing,
            float = float,
            clear = clear,
            position = position,
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            display = display,
            flexDirection = flexDirection,
            justifyContent = justifyContent,
            alignItems = alignItems,
            horizontalAlign = finalHorizontalAlign,
            filter = filter,
            borderCollapse = borderCollapse,
            borderSpacing = borderSpacing,
            minWidth = minWidth,
            minHeight = minHeight,
            maxHeight = maxHeight,
            overflow = overflow,
            breakBefore = breakBefore,
            breakAfter = breakAfter,
            breakInside = breakInside,
            widows = widows,
            orphans = orphans,
            visibility = visibility,
            objectFit = objectFit,
            objectPosition = objectPosition,
            backgroundImage = backgroundImage
        )
        return CssStyle(
            spanStyle, paragraphStyle, blockStyle, fontFamilies, display, fontSize, textTransform, boxSizing, content, hyphens, fontVariantNumeric, textEmphasis,
            wordSpacing, textDecorationStyle, textDecorationColor, textUnderlineOffset, whiteSpace, verticalAlign, customProperties
        )
    }

    private fun parseShorthand4(value: String, baseFontSize: Float, density: Float, containerWidth: Int): List<Dp> {
        val parts = splitCssTokens(value)
        val dps = parts.map { parseCssSizeToDp(it, baseFontSize, density, containerWidth) }
        return when (dps.size) {
            1 -> listOf(dps[0], dps[0], dps[0], dps[0])
            2 -> listOf(dps[0], dps[1], dps[0], dps[1]) // Top/Bottom, Left/Right
            3 -> listOf(dps[0], dps[1], dps[2], dps[1]) // Top, Left/Right, Bottom
            4 -> listOf(dps[0], dps[1], dps[2], dps[3]) // Top, Right, Bottom, Left
            else -> listOf(0.dp, 0.dp, 0.dp, 0.dp)
        }
    }

    private fun parseShorthand4Strings(value: String): List<String?> {
        val parts = splitCssTokens(value).map { it.lowercase() }
        return when (parts.size) {
            1 -> listOf(parts[0], parts[0], parts[0], parts[0])
            2 -> listOf(parts[0], parts[1], parts[0], parts[1])
            3 -> listOf(parts[0], parts[1], parts[2], parts[1])
            4 -> listOf(parts[0], parts[1], parts[2], parts[3])
            else -> listOf(null, null, null, null)
        }
    }

    private fun parseShorthand4Colors(value: String): List<Color?> {
        if (value.contains("(") || value.contains(",")) {
            val c = parseColor(value)
            return listOf(c, c, c, c)
        }
        val parts = splitCssTokens(value)
        val colors = parts.map { parseColor(it) }
        return when (colors.size) {
            1 -> listOf(colors[0], colors[0], colors[0], colors[0])
            2 -> listOf(colors[0], colors[1], colors[0], colors[1])
            3 -> listOf(colors[0], colors[1], colors[2], colors[1])
            4 -> listOf(colors[0], colors[1], colors[2], colors[3])
            else -> listOf(null, null, null, null)
        }
    }

    private fun parseBorderShorthand(value: String, baseFontSize: Float, density: Float, containerWidth: Int): Triple<Dp?, String?, Color?> {
        val parts = splitCssTokens(value)
        var w: Dp? = null
        var s: String? = null
        var c: Color? = null

        val keywords = mapOf("thin" to 1.dp, "medium" to 3.dp, "thick" to 5.dp)

        parts.forEach { part ->
            val lower = part.lowercase()
            if (keywords.containsKey(lower)) {
                w = keywords[lower]
            } else if (lower.endsWith("px") || lower.endsWith("em") || lower.endsWith("rem") || lower.endsWith("%") || lower.first().isDigit()) {
                val parsed = parseCssSizeToDp(part, baseFontSize, density, containerWidth)
                if (parsed > 0.dp || part == "0") w = parsed
            } else if (lower in listOf("solid", "dashed", "dotted", "double", "none", "hidden", "groove", "ridge", "inset", "outset")) {
                s = lower
            } else {
                val parsedColor = parseColor(part)
                if (parsedColor != null) c = parsedColor
            }
        }
        return Triple(w, s, c)
    }

    private fun extractCustomProperties(
        properties: String,
        inheritedCustomProperties: Map<String, String>
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        splitDeclarations(properties).forEach { declaration ->
            val parts = declaration.split(':', limit = 2).map { it.trim() }
            if (parts.size != 2 || !parts[0].startsWith("--")) return@forEach
            val rawValue = parts[1].replace(Regex("\\s*!important", RegexOption.IGNORE_CASE), "").trim()
            result[parts[0]] = resolveCssVariables(rawValue, inheritedCustomProperties + result)
        }
        return result
    }

    private fun resolveCssVariables(value: String, customProperties: Map<String, String>): String {
        if (!value.contains("var(")) return value
        var resolved = value
        repeat(8) {
            val next = Regex("""var\(\s*(--[A-Za-z0-9_-]+)\s*(?:,\s*([^()]*))?\)""").replace(resolved) { match ->
                customProperties[match.groupValues[1]] ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() } ?: ""
            }
            if (next == resolved) return resolved
            resolved = next
        }
        return resolved
    }

    private fun normalizeBreakValue(value: String): String? {
        return when (value.lowercase()) {
            "always" -> "page"
            "avoid", "avoid-page", "page", "left", "right", "recto", "verso" -> value.lowercase()
            "auto" -> null
            else -> null
        }
    }

    internal fun parseCssDimension(
        size: String,
        baseFontSizeSp: Float,
        density: Float,
        containerWidthPx: Int
    ): Dp {
        val trimmed = size.trim().lowercase()
        if (trimmed in listOf("auto", "none", "max-content", "min-content", "fit-content", "inherit", "initial", "unset", "revert")) {
            return Dp.Unspecified
        }
        if (trimmed == "0" || trimmed == "0px") return 0.dp
        if (trimmed.startsWith("calc(") && trimmed.endsWith(")")) {
            val px = evaluateCssLengthExpression(
                expression = trimmed.removePrefix("calc(").removeSuffix(")"),
                baseFontSizeSp = baseFontSizeSp,
                density = density,
                containerWidthPx = containerWidthPx
            )
            if (px != null) return (px / density).dp
        }

        return when {
            trimmed.endsWith("px") -> trimmed.removeSuffix("px").toFloatOrNull()?.let { (it / density).dp } ?: Dp.Unspecified
            trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").toFloatOrNull()?.dp ?: Dp.Unspecified
            trimmed.endsWith("em") -> trimmed.removeSuffix("em").toFloatOrNull()?.let { (it * baseFontSizeSp).dp } ?: Dp.Unspecified
            trimmed.endsWith("rem") -> trimmed.removeSuffix("rem").toFloatOrNull()?.let { (it * baseFontSizeSp).dp } ?: Dp.Unspecified
            trimmed.endsWith("pt") -> trimmed.removeSuffix("pt").toFloatOrNull()?.let { (it * 1.33f).dp } ?: Dp.Unspecified
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toFloatOrNull()
                if (percent != null) {
                    ((percent / 100f) * containerWidthPx / density).dp
                } else {
                    Dp.Unspecified
                }
            }
            trimmed.endsWith("vw") -> {
                val percent = trimmed.removeSuffix("vw").toFloatOrNull()
                if (percent != null) {
                    ((percent / 100f) * containerWidthPx / density).dp
                } else {
                    Dp.Unspecified
                }
            }
            trimmed.endsWith("vh") -> Dp.Unspecified
            trimmed.toFloatOrNull() != null -> (trimmed.toFloat() / density).dp
            else -> Dp.Unspecified
        }
    }

    private fun evaluateCssLengthExpression(
        expression: String,
        baseFontSizeSp: Float,
        density: Float,
        containerWidthPx: Int
    ): Float? {
        val tokens = Regex("""(\d*\.?\d+(?:px|dp|em|rem|pt|%)?)|([+\-*/()])""")
            .findAll(expression.replace("\\s+".toRegex(), ""))
            .map { it.value }
            .toList()
        if (tokens.isEmpty()) return null

        var index = 0
        var parseExpression: (() -> Float?)? = null
        fun parseNumber(token: String): Float? {
            return when {
                token.endsWith("px") -> token.removeSuffix("px").toFloatOrNull()
                token.endsWith("dp") -> token.removeSuffix("dp").toFloatOrNull()?.let { it * density }
                token.endsWith("em") -> token.removeSuffix("em").toFloatOrNull()?.let { it * baseFontSizeSp * density }
                token.endsWith("rem") -> token.removeSuffix("rem").toFloatOrNull()?.let { it * baseFontSizeSp * density }
                token.endsWith("pt") -> token.removeSuffix("pt").toFloatOrNull()?.let { it * 1.333f * density }
                token.endsWith("%") -> token.removeSuffix("%").toFloatOrNull()?.let { (it / 100f) * containerWidthPx }
                else -> token.toFloatOrNull()
            }
        }

        fun parseFactor(): Float? {
            val token = tokens.getOrNull(index++) ?: return null
            return when (token) {
                "+" -> parseFactor()
                "-" -> parseFactor()?.let { -it }
                "(" -> {
                    val value = parseExpression?.invoke() ?: return null
                    if (tokens.getOrNull(index) == ")") index++
                    value
                }
                else -> parseNumber(token)
            }
        }

        fun parseTerm(): Float? {
            var value = parseFactor() ?: return null
            while (true) {
                when (tokens.getOrNull(index)) {
                    "*" -> {
                        index++
                        value *= parseFactor() ?: return null
                    }
                    "/" -> {
                        index++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0f) return null
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        parseExpression = fun(): Float? {
            var value = parseTerm() ?: return null
            while (true) {
                when (tokens.getOrNull(index)) {
                    "+" -> {
                        index++
                        value += parseTerm() ?: return null
                    }
                    "-" -> {
                        index++
                        value -= parseTerm() ?: return null
                    }
                    else -> return value
                }
            }
        }

        val result = parseExpression?.invoke()
        return if (result != null && index == tokens.size) result else null
    }

    internal fun parseCssSizeToDp(
        size: String,
        baseFontSizeSp: Float,
        density: Float,
        containerWidthPx: Int
    ): Dp {
        val trimmed = size.trim().lowercase()
        BORDER_WIDTH_KEYWORDS[trimmed]?.let { return it }
        val dim = parseCssDimension(size, baseFontSizeSp, density, containerWidthPx)
        return if (dim.isSpecified) dim else 0.dp
    }

    internal fun parseColor(colorString: String, currentColor: Color = Color.Unspecified): Color? {
        val sanitized = colorString.trim().lowercase()
        return when {
            sanitized == "currentcolor" -> currentColor.takeIf { it.isSpecified }
            sanitized.startsWith("#") -> {
                val hex = sanitized.substring(1)
                val colorLong = hex.toLongOrNull(16) ?: return null
                when (hex.length) {
                    3 -> { // #RGB
                        val r = (colorLong and 0xF00) shr 8
                        val g = (colorLong and 0x0F0) shr 4
                        val b = colorLong and 0x00F
                        Color((r * 17).toInt(), (g * 17).toInt(), (b * 17).toInt(), 255)
                    }
                    4 -> {
                        val r = (colorLong and 0xF000) shr 12
                        val g = (colorLong and 0x0F00) shr 8
                        val b = (colorLong and 0x00F0) shr 4
                        val a = colorLong and 0x000F
                        Color((r * 17).toInt(), (g * 17).toInt(), (b * 17).toInt(), (a * 17).toInt())
                    }
                    6 -> Color(
                        ((colorLong shr 16) and 0xFF).toInt(),
                        ((colorLong shr 8) and 0xFF).toInt(),
                        (colorLong and 0xFF).toInt(),
                        255
                    ) // #RRGGBB
                    8 -> Color(
                        ((colorLong shr 24) and 0xFF).toInt(),
                        ((colorLong shr 16) and 0xFF).toInt(),
                        ((colorLong shr 8) and 0xFF).toInt(),
                        (colorLong and 0xFF).toInt()
                    ) // CSS #RRGGBBAA
                    else -> null
                }
            }
            sanitized.startsWith("rgb") -> {
                val valuesString = sanitized.substringAfter('(').substringBefore(')')
                val alphaParts = valuesString.split('/').map { it.trim() }
                val values = if (alphaParts.first().contains(',')) {
                    alphaParts.first().split(',').map { it.trim() }
                } else {
                    splitCssTokens(alphaParts.first())
                }

                if (values.size < 3) return null

                fun channel(part: String): Int {
                    return if (part.endsWith("%")) {
                        (((part.removeSuffix("%").toFloatOrNull() ?: 0f) / 100f) * 255f).roundToInt()
                    } else {
                        part.toFloatOrNull()?.roundToInt() ?: 0
                    }.coerceIn(0, 255)
                }
                fun alpha(part: String?): Float {
                    if (part.isNullOrBlank()) return 1f
                    return if (part.endsWith("%")) {
                        ((part.removeSuffix("%").toFloatOrNull() ?: 100f) / 100f)
                    } else {
                        part.toFloatOrNull() ?: 1f
                    }.coerceIn(0f, 1f)
                }

                val r = channel(values[0])
                val g = channel(values[1])
                val b = channel(values[2])
                val a = alpha(alphaParts.getOrNull(1) ?: values.getOrNull(3))

                Color(r, g, b, (a * 255).roundToInt())
            }
            sanitized.startsWith("hsl") -> parseHslColor(sanitized)
            else -> when(sanitized) {
                "black" -> Color.Black
                "white" -> Color.White
                "red" -> Color.Red
                "green" -> Color(0, 128, 0)
                "lime" -> Color.Green
                "blue" -> Color.Blue
                "gray", "grey" -> Color.Gray
                "silver" -> Color(192, 192, 192)
                "maroon" -> Color(128, 0, 0)
                "olive" -> Color(128, 128, 0)
                "purple" -> Color(128, 0, 128)
                "teal" -> Color(0, 128, 128)
                "navy" -> Color(0, 0, 128)
                "orange" -> Color(255, 165, 0)
                "brown" -> Color(165, 42, 42)
                "pink" -> Color(255, 192, 203)
                "cyan" -> Color.Cyan
                "aqua" -> Color.Cyan
                "fuchsia" -> Color.Magenta
                "magenta" -> Color.Magenta
                "yellow" -> Color.Yellow
                "transparent" -> Color.Transparent
                else -> null
            }
        }
    }

    private fun parseHslColor(value: String): Color? {
        val valuesString = value.substringAfter('(').substringBefore(')')
        val alphaParts = valuesString.split('/').map { it.trim() }
        val values = if (alphaParts.first().contains(',')) {
            alphaParts.first().split(',').map { it.trim() }
        } else {
            splitCssTokens(alphaParts.first())
        }
        if (values.size < 3) return null
        val hue = values[0].removeSuffix("deg").toFloatOrNull() ?: return null
        val saturation = values[1].removeSuffix("%").toFloatOrNull()?.div(100f) ?: return null
        val lightness = values[2].removeSuffix("%").toFloatOrNull()?.div(100f) ?: return null
        val alpha = alphaParts.getOrNull(1)?.let {
            if (it.endsWith("%")) (it.removeSuffix("%").toFloatOrNull() ?: 100f) / 100f else it.toFloatOrNull() ?: 1f
        } ?: values.getOrNull(3)?.let {
            if (it.endsWith("%")) (it.removeSuffix("%").toFloatOrNull() ?: 100f) / 100f else it.toFloatOrNull() ?: 1f
        } ?: 1f

        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        val (r1, g1, b1) = when {
            normalizedHue < 60f -> Triple(c, x, 0f)
            normalizedHue < 120f -> Triple(x, c, 0f)
            normalizedHue < 180f -> Triple(0f, c, x)
            normalizedHue < 240f -> Triple(0f, x, c)
            normalizedHue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255f).roundToInt().coerceIn(0, 255),
            (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        )
    }

    private fun parseBoxBorders(value: String, baseFontSizeSp: Float, density: Float, containerWidthPx: Int): BoxBorders {
        val parts = splitCssTokens(value)
        val dps = parts.map { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) }
        return when (dps.size) {
            1 -> BoxBorders(top = dps[0], right = dps[0], bottom = dps[0], left = dps[0])
            2 -> BoxBorders(top = dps[0], bottom = dps[0], right = dps[1], left = dps[1])
            3 -> BoxBorders(top = dps[0], right = dps[1], left = dps[1], bottom = dps[2])
            4 -> BoxBorders(top = dps[0], right = dps[1], bottom = dps[2], left = dps[3])
            else -> BoxBorders()
        }
    }
}
