/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import android.os.Build
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.regex.Pattern
import kotlin.math.roundToInt

private const val IMPORTANT_SPECIFICITY_BOOST = 10_000
private fun Color.luminance(): Float {
    if (!this.isSpecified) return 0f
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object CssParser {
    private val FONT_FACE_REGEX = "@font-face\\s*\\{([^}]+)\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
    private val URL_REGEX = "url\\((['\"]?)(.*?)\\1\\)".toRegex()
    private val ID_SELECTOR_PATTERN = Pattern.compile("#[^\\s,]+")
    private val CLASS_ATTRIBUTE_SELECTOR_PATTERN = Pattern.compile("\\.[^\\s,]+|\\[[^]]+]|:(?!:)[^\\s,]+")
    private val TYPE_PSEUDO_ELEMENT_SELECTOR_PATTERN = Pattern.compile("(?<![.#\\[])\\b[a-zA-Z-]+|::[a-zA-Z-]+")
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

    internal fun adaptColorForTheme(color: Color, isDarkTheme: Boolean, isBackground: Boolean): Color {
        if (!color.isSpecified) return color
        if (color.alpha < 0.9f) return color

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

    private fun splitDeclarations(declarations: String): List<String> {
        val parts = declarations.split(';').toMutableList()
        if (parts.size <= 1) return parts

        val result = mutableListOf<String>()
        val iterator = parts.listIterator()
        while(iterator.hasNext()) {
            var current = iterator.next()
            val originalCurrent = current
            var reassembled = false
            while (current.count { it == '(' } > current.count { it == ')' }) {
                if (!iterator.hasNext()) break
                val nextPart = iterator.next()
                current += ";$nextPart"
                reassembled = true
            }
            if (reassembled) {
                Timber.d("Reassembled declaration. Original: '$originalCurrent'. Final: '$current'")
            }
            result.add(current)
        }
        return result
    }

    private fun calculateSpecificity(selector: String): Int {
        val ids = ID_SELECTOR_PATTERN.matcher(selector).run {
            var count = 0
            while (find()) count++
            count
        }
        val classesAndAttributes = CLASS_ATTRIBUTE_SELECTOR_PATTERN.matcher(selector).run {
            var count = 0
            while (find()) count++
            count
        }
        val elementsAndPseudos = TYPE_PSEUDO_ELEMENT_SELECTOR_PATTERN.matcher(selector).run {
            var count = 0
            while (find()) count++
            count
        }
        val specificity = ids * 100 + classesAndAttributes * 10 + elementsAndPseudos
        return specificity
    }

    fun parse(
        cssContent: String,
        cssPath: String?,
        baseFontSizeSp: Float,
        density: Float,
        constraints: Constraints,
        isDarkTheme: Boolean
    ): OptimizedCssParseResult {
        val byTag = mutableMapOf<String, MutableList<CssRule>>()
        val byClass = mutableMapOf<String, MutableList<CssRule>>()
        val byId = mutableMapOf<String, MutableList<CssRule>>()
        val otherComplex = mutableListOf<CssRule>()
        val fontFaces = mutableListOf<FontFaceInfo>()

        val blockRegex = "([^{}]+)\\s*\\{([^}]+)\\}".toRegex()

        var cleanedCss = cssContent.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

        val mediaQueryRegex = Regex("@media[^{]+\\{((?>[^{}]+|\\{[^{}]*\\})*)\\}")
        mediaQueryRegex.findAll(cleanedCss).forEach { match ->
            val condition = match.groups[0]?.value?.trim() ?: ""
            if (isDarkTheme && condition.contains("prefers-color-scheme: dark")) {
                val darkCss = match.groups[1]?.value ?: ""
                cleanedCss += "\n$darkCss"
            }
        }
        cleanedCss = mediaQueryRegex.replace(cleanedCss, "")

        Timber.d("CssParser: Checking for @font-face rules...")
        val fontFaceMatches = FONT_FACE_REGEX.findAll(cleanedCss)
        if (!fontFaceMatches.any()) {
            Timber.d("CssParser: No @font-face rules found by regex.")
        }
        fontFaceMatches.forEach { match ->
            Timber.d("CssParser: Found a @font-face block. Parsing its properties.")
            val properties = match.groupValues[1]
            parseFontFace(properties, cssPath)?.let { fontFaces.add(it) }
        }
        cleanedCss = FONT_FACE_REGEX.replace(cleanedCss, "")

        blockRegex.findAll(cleanedCss).forEach { matchResult ->
            val selectorGroup = matchResult.groups[1]?.value?.trim() ?: ""
            val propertiesGroup = matchResult.groups[2]?.value?.trim() ?: ""

            val selectors = selectorGroup.split(',').map { it.trim() }

            for (originalSelector in selectors) {
                if (originalSelector.isBlank() || originalSelector.startsWith("@")) {
                    continue
                }
                val sanitizedSelector = originalSelector.replace(
                    Regex(":(link|visited|hover|active|focus)\\b|::(first-letter|first-line|marker)\\b", RegexOption.IGNORE_CASE),
                    ""
                ).trim()
                if (sanitizedSelector.isBlank()) {
                    continue
                }
                val specificity = calculateSpecificity(originalSelector)
                val normalStyle = parseProperties(
                    propertiesGroup, baseFontSizeSp, density, constraints, onlyImportant = false,
                    isDarkTheme
                )
                val importantStyle = parseProperties(
                    propertiesGroup, baseFontSizeSp, density, constraints, onlyImportant = true,
                    isDarkTheme
                )

                fun addRule(style: CssStyle, spec: Int) {
                    if (style == CssStyle()) return
                    val rule = CssRule(CssSelector(sanitizedSelector, spec), style)
                    when {
                        SIMPLE_ID_SELECTOR.matches(sanitizedSelector) ->
                            byId.getOrPut(sanitizedSelector.substring(1)) { mutableListOf() }.add(rule)
                        SIMPLE_CLASS_SELECTOR.matches(sanitizedSelector) ->
                            byClass.getOrPut(sanitizedSelector.substring(1)) { mutableListOf() }.add(rule)
                        SIMPLE_TAG_SELECTOR.matches(sanitizedSelector) ->
                            byTag.getOrPut(sanitizedSelector) { mutableListOf() }.add(rule)
                        else -> otherComplex.add(rule)
                    }
                }

                addRule(normalStyle, specificity)
                addRule(importantStyle, specificity + IMPORTANT_SPECIFICITY_BOOST)
            }
        }
        val optimizedRules = OptimizedCssRules(byTag, byClass, byId, otherComplex)
        return OptimizedCssParseResult(optimizedRules, fontFaces)
    }

    private fun parseFontFace(properties: String, cssPath: String?): FontFaceInfo? {
        val propsMap = splitDeclarations(properties)
            .map { it.trim().split(':', limit = 2).map { part -> part.trim() } }
            .filter { it.size == 2 && it[0].isNotBlank() }
            .associate { it[0].lowercase() to it[1] }

        val fontFamily = propsMap["font-family"]?.removeSurrounding("\"")?.removeSurrounding("'")?.lowercase()
        val srcString = propsMap["src"]
        Timber.d("Parsing font-face for family: $fontFamily. Raw src string: $srcString")

        if (fontFamily == null || srcString == null) {
            Timber.w("Incomplete @font-face rule: missing font-family or src.")
            return null
        }

        val urlWithFormatRegex = "url\\((['\"]?)(.*?)\\1\\)\\s*format\\((['\"]?)(.*?)\\3\\)".toRegex()

        val sources = srcString.split(Regex(",(?=\\s*url\\()")).mapNotNull { part ->
            val trimmedPart = part.trim()
            Timber.d("Processing src part: '$trimmedPart'")

            urlWithFormatRegex.find(trimmedPart)?.let {
                Timber.d("Matched url with format(). URL: ${it.groupValues[2]}, Format: ${it.groupValues[4]}")
                FontSource(url = it.groupValues[2], format = it.groupValues[4].lowercase().removeSurrounding("'"))
            } ?: URL_REGEX.find(trimmedPart)?.let {
                val url = it.groupValues[2]
                Timber.d("Matched url() only. URL: '$url'")
                val format = when {
                    url.startsWith("data:", ignoreCase = true) -> {
                        val mediaType = url.substringAfter("data:").substringBefore(';')
                        Timber.d("Data URI detected. Media type: '$mediaType'")
                        when {
                            mediaType.contains("opentype") -> "opentype"
                            mediaType.contains("truetype") -> "truetype"
                            mediaType.contains("woff2") -> "woff2"
                            mediaType.contains("woff") -> "woff"
                            else -> {
                                Timber.w("Unknown data URI media type: $mediaType")
                                null
                            }
                        }
                    }
                    url.endsWith(".woff2", ignoreCase = true) -> "woff2"
                    url.endsWith(".woff", ignoreCase = true) -> "woff"
                    url.endsWith(".otf", ignoreCase = true) -> "opentype"
                    url.endsWith(".ttf", ignoreCase = true) -> "truetype"
                    else -> {
                        Timber.w("Could not determine format from URL: $url")
                        null
                    }
                }
                Timber.d("Determined format: '$format'")
                if (format != null) {
                    FontSource(url = url, format = format)
                } else {
                    null
                }
            }
        }

        if (sources.isEmpty()) {
            Timber.w("Could not parse any valid source from @font-face src: $srcString")
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
        Timber.d("Selected font source for '$fontFamily': '${preferredSource.url}' with format '${preferredSource.format}'")

        val finalSrc = if (cssPath != null && !rawSrc.startsWith("data:")) {
            try {
                val cssParentDir = File(cssPath).parent ?: ""
                File(cssParentDir, rawSrc).normalize().path
            } catch (e: Exception) {
                Timber.e(e, "Could not resolve font path for src '$rawSrc' in css '$cssPath'")
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
        isDarkTheme: Boolean
    ): CssStyle {
        var spanStyle = SpanStyle()
        var paragraphStyle = ParagraphStyle()
        var padding = BoxBorders()
        var width: Dp = Dp.Unspecified
        var maxWidth: Dp = Dp.Unspecified
        var height: Dp = Dp.Unspecified
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
        var hyphens: String? = null
        var fontVariantNumeric: String? = null
        var textEmphasisStyleString: String? = null
        var textEmphasisColor: Color? = null
        var textEmphasisPositionString: String? = null
        var marginTopStr: String? = null
        var marginRightStr: String? = null
        var marginBottomStr: String? = null
        var marginLeftStr: String? = null

        splitDeclarations(properties).filter { it.isNotBlank() }.forEach { prop ->
            val parts = prop.split(':', limit = 2).map { it.trim() }
            if (parts.size == 2) {
                val key = parts[0].lowercase()
                val valueWithImportant = parts[1]
                val isImportant = valueWithImportant.contains("!important", ignoreCase = true)

                if (isImportant != onlyImportant) {
                    return@forEach
                }
                val value = if (isImportant) {
                    valueWithImportant.replace(Regex("\\s*!important", RegexOption.IGNORE_CASE), "").trim()
                } else {
                    valueWithImportant
                }

                // Helper to update border props ONLY if this border is significant
                fun updateUnifiedBorder(
                    widthStr: String?,
                    colorStr: String?,
                    styleStr: String?
                ) {
                    val parsedWidth = widthStr?.let { parseCssSizeToDp(it, baseFontSizeSp, density, containerWidthPx) } ?: 0.dp
                    val parsedColor = colorStr?.let { parseColor(it) }?.let { this@CssParser.adaptColorForTheme(it, isDarkTheme, isBackground = false) }

                    // We update the unified style if:
                    // 1. We found a width larger than what we've seen (prioritize visible borders)
                    // 2. Or we haven't seen any width yet and this is the first definition
                    // 3. Or the specific property is just setting style/color and we want to take the last one defined (standard CSS cascade behavior for same-specificity)
                    // However, for separate sides (left vs bottom), we strictly prioritize the one with width.

                    val isExplicitWidth = widthStr != null

                    if (parsedWidth > maxBorderWidthFound) {
                        maxBorderWidthFound = parsedWidth
                        if (parsedColor != null) finalBorderColor = parsedColor
                        if (styleStr != null) finalBorderStyle = styleStr
                    } else if (parsedWidth == maxBorderWidthFound && maxBorderWidthFound > 0.dp) {
                        // If equal non-zero width, let last defined win (cascade)
                        if (parsedColor != null) finalBorderColor = parsedColor
                        if (styleStr != null) finalBorderStyle = styleStr
                    } else if (!isExplicitWidth) {
                        // Just updating color or style without width
                        if (parsedColor != null) finalBorderColor = parsedColor
                        if (styleStr != null) finalBorderStyle = styleStr
                    }
                }

                when (key) {
                    // ... [Keep existing cases for font-family, font-size, font-weight, font-style, color, text-align, line-height, text-indent, text-decoration, letter-spacing, text-transform, font-variant, margin, margin-*, padding, padding-*, width, max-width, height, background-color] ...

                    "font-family" -> {
                        fontFamilies = value.split(',')
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
                        spanStyle = spanStyle.copy(fontWeight = when (value) {
                            "bold" -> FontWeight.Bold
                            "700" -> FontWeight.Bold
                            "600" -> FontWeight.SemiBold
                            "500" -> FontWeight.Medium
                            "300" -> FontWeight.Light
                            "200" -> FontWeight.ExtraLight
                            "100" -> FontWeight.Thin
                            "normal" -> FontWeight.Normal
                            "400" -> FontWeight.Normal
                            else -> value.toIntOrNull()?.let { FontWeight(it) } ?: spanStyle.fontWeight
                        })
                    }
                    "font-style" -> {
                        if (value == "italic" || value == "oblique") spanStyle = spanStyle.copy(fontStyle = FontStyle.Italic)
                        else if (value == "normal") spanStyle = spanStyle.copy(fontStyle = FontStyle.Normal)
                    }
                    "color" -> {
                        parseColor(value)?.let {
                            spanStyle = spanStyle.copy(color = this@CssParser.adaptColorForTheme(it, isDarkTheme, isBackground = false))
                        }
                    }
                    "text-align" -> {
                        val align = when (value) {
                            "center" -> TextAlign.Center
                            "right" -> TextAlign.End
                            "justify" -> TextAlign.Justify
                            else -> TextAlign.Start
                        }
                        paragraphStyle = paragraphStyle.copy(textAlign = align)
                    }
                    "line-height" -> {
                        val trimmedValue = value.trim()
                        var lineHeight = when {
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
                        if (lineHeight.isEm && lineHeight.value < 1.2f && lineHeight.value > 0) {
                            lineHeight = 2f.em
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
                        spanStyle = spanStyle.copy(
                            textDecoration = when(value) {
                                "underline" -> TextDecoration.Underline
                                "line-through" -> TextDecoration.LineThrough
                                "none" -> TextDecoration.None
                                else -> spanStyle.textDecoration
                            }
                        )
                    }
                    "letter-spacing" -> {
                        val letterSpacing = parseCssDimensionToTextUnit(value, containerWidthPx, density)
                        if (letterSpacing != TextUnit.Unspecified) {
                            spanStyle = spanStyle.copy(letterSpacing = letterSpacing)
                        }
                    }
                    "text-transform" -> {
                        textTransform = when (value) {
                            "uppercase", "lowercase", "capitalize", "none" -> value
                            else -> null
                        }
                    }
                    "font-variant" -> {
                        if (value.contains("small-caps")) {
                            spanStyle = spanStyle.copy(fontFeatureSettings = "\"smcp\" on")
                        }
                    }
                    "margin" -> {
                        val marginParts = value.split(' ').filter { it.isNotBlank() }
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

                    "width" -> width = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "max-width" -> maxWidth = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "height" -> height = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)

                    "background-color" -> {
                        val originalColor = parseColor(value) ?: Color.Unspecified
                        backgroundColor = this@CssParser.adaptColorForTheme(originalColor, isDarkTheme, isBackground = true)
                    }

                    // Border Properties - Logic Updated
                    "border-width" -> updateUnifiedBorder(value, null, null)
                    "border-color" -> updateUnifiedBorder(null, value, null)
                    "border-style" -> updateUnifiedBorder(null, null, value)

                    "border-top-width", "border-bottom-width", "border-left-width", "border-right-width" -> {
                        updateUnifiedBorder(value, null, null)
                    }
                    "border-top-color", "border-bottom-color", "border-left-color", "border-right-color" -> {
                        updateUnifiedBorder(null, value, null)
                    }
                    "border-top-style", "border-bottom-style", "border-left-style", "border-right-style" -> {
                        updateUnifiedBorder(null, null, value)
                    }

                    "border-bottom", "border-top", "border-left", "border-right", "border" -> {
                        val borderParts = value.split(" ").filter { it.isNotBlank() }
                        var widthVal: String? = null
                        var colorVal: String? = null
                        var styleVal: String? = null

                        borderParts.forEach { part ->
                            val parsedWidth = parseCssSizeToDp(part, baseFontSizeSp, density, containerWidthPx)
                            if (parsedWidth > 0.dp || part == "0" || part == "0px" || BORDER_WIDTH_KEYWORDS.containsKey(part)) {
                                widthVal = part
                            } else if (part in listOf("solid", "dotted", "dashed", "double", "groove", "ridge", "inset", "outset")) {
                                styleVal = part
                            } else if (parseColor(part) != null) {
                                colorVal = part
                            }
                        }
                        updateUnifiedBorder(widthVal, colorVal, styleVal)
                    }
                    // End Border Properties

                    "border-collapse" -> {
                        if (value in listOf("collapse", "separate")) {
                            borderCollapse = value
                        }
                    }
                    "border-spacing" -> {
                        borderSpacing = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    }
                    "border-radius" -> borderRadius = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)

                    // ... [Keep existing cases for list-style-*, page-break-*, display, flex-*, filter, box-sizing, content, position, top/left/etc, float, hyphens, etc] ...

                    "list-style-type" -> {
                        listStyleType = value
                    }
                    "list-style-image" -> {
                        URL_REGEX.find(value)?.groupValues?.get(2)?.let {
                            listStyleImage = it
                        }
                    }
                    "page-break-inside" -> {
                        if (value == "avoid") {
                            pageBreakInsideAvoid = true
                        }
                    }
                    "page-break-after" -> {
                        if (value == "avoid") {
                            pageBreakAfterAvoid = true
                        }
                    }
                    "display" -> display = value
                    "flex-direction" -> flexDirection = value
                    "justify-content" -> justifyContent = value
                    "align-items" -> alignItems = value
                    "filter" -> filter = value
                    "box-sizing" -> boxSizing = value
                    "content" -> content = value.removeSurrounding("\"").removeSurrounding("'")
                    "position" -> position = value
                    "left" -> left = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "right" -> right = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "top" -> top = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "bottom" -> bottom = parseCssSizeToDp(value, baseFontSizeSp, density, containerWidthPx)
                    "float" -> {
                        if (value in listOf("left", "right", "none")) {
                            float = value
                        }
                    }
                    "hyphens", "-webkit-hyphens", "-moz-hyphens", "-epub-hyphens", "adobe-hyphenate" -> {
                        if (value in listOf("auto", "manual", "none")) {
                            hyphens = value
                        }
                    }
                    "font-variant-numeric" -> {
                        fontVariantNumeric = value
                    }
                    "clear" -> {
                        if (value in listOf("left", "right", "both", "none")) {
                            clear = value
                        }
                    }
                    "text-emphasis", "-epub-text-emphasis" -> {
                        textEmphasisStyleString = value
                    }
                    "text-emphasis-style", "-epub-text-emphasis-style" -> {
                        textEmphasisStyleString = value
                    }
                    "text-emphasis-color", "-epub-text-emphasis-color" -> {
                        textEmphasisColor = parseColor(value)?.let { this@CssParser.adaptColorForTheme(it, isDarkTheme, isBackground = false) }
                    }
                    "text-emphasis-position", "-epub-text-emphasis-position" -> {
                        if (value in listOf("over", "under")) {
                            textEmphasisPositionString = value
                        }
                    }
                }
            }
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

        // Updated: Use the maxBorderWidthFound and corresponding colors
        val finalBorder = if (maxBorderWidthFound > 0.dp && finalBorderStyle != null) {
            val borderColor = finalBorderColor ?: spanStyle.color.takeIf { it.isSpecified } ?: Color.Black
            BorderStyle(
                width = maxBorderWidthFound,
                color = borderColor,
                style = finalBorderStyle
            )
        } else null

        val blockStyle = BlockStyle(
            margin = margin, padding = padding, width = width, maxWidth = maxWidth, height = height,
            backgroundColor = backgroundColor, border = finalBorder,
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
            borderRadius = borderRadius
        )
        return CssStyle(spanStyle, paragraphStyle, blockStyle, fontFamilies, display, fontSize, textTransform, boxSizing, content, hyphens, fontVariantNumeric, textEmphasis)
    }

    // ADD the parseCssSizeToDp function here at the bottom of the object or file
    internal fun parseCssSizeToDp(
        size: String,
        baseFontSizeSp: Float,
        density: Float,
        containerWidthPx: Int
    ): Dp {
        val trimmed = size.trim().lowercase()
        // Handle keywords
        BORDER_WIDTH_KEYWORDS[trimmed]?.let { return it }

        if (trimmed == "0" || trimmed == "0px") return 0.dp

        return when {
            trimmed.endsWith("px") -> trimmed.removeSuffix("px").toFloatOrNull()?.let { (it / density).dp } ?: 0.dp
            trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").toFloatOrNull()?.dp ?: 0.dp
            trimmed.endsWith("em") -> trimmed.removeSuffix("em").toFloatOrNull()?.let { (it * baseFontSizeSp).dp } ?: 0.dp
            trimmed.endsWith("rem") -> trimmed.removeSuffix("rem").toFloatOrNull()?.let { (it * baseFontSizeSp).dp } ?: 0.dp
            trimmed.endsWith("pt") -> trimmed.removeSuffix("pt").toFloatOrNull()?.let { (it * 1.33f).dp } ?: 0.dp // 1pt ≈ 1.33px
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toFloatOrNull()
                if (percent != null) {
                    ((percent / 100f) * containerWidthPx / density).dp
                } else {
                    0.dp
                }
            }
            trimmed.toFloatOrNull() != null -> (trimmed.toFloat() / density).dp
            else -> 0.dp
        }
    }

    internal fun parseCssDimensionToTextUnit(
        dimension: String?,
        containerWidthPx: Int,
        density: Float
    ): TextUnit {
        if (dimension.isNullOrBlank()) return TextUnit.Unspecified
        val trimmed = dimension.trim().lowercase()
        return when {
            trimmed.endsWith("px") -> trimmed.removeSuffix("px").toFloatOrNull()?.sp ?: TextUnit.Unspecified
            trimmed.endsWith("em") -> trimmed.removeSuffix("em").toFloatOrNull()?.em ?: TextUnit.Unspecified
            trimmed.endsWith("rem") -> trimmed.removeSuffix("rem").toFloatOrNull()?.em ?: TextUnit.Unspecified
            trimmed.endsWith("%") -> trimmed.removeSuffix("%").toFloatOrNull()?.let { (it / 100f).em } ?: TextUnit.Unspecified
            trimmed.endsWith("pt") -> trimmed.removeSuffix("pt").toFloatOrNull()?.let { (it * 1.33f).sp } ?: TextUnit.Unspecified
            else -> TextUnit.Unspecified
        }
    }

    internal fun parseColor(colorString: String): Color? {
        val sanitized = colorString.trim().lowercase()
        return when {
            sanitized.startsWith("#") -> {
                val hex = sanitized.substring(1)
                val colorLong = hex.toLongOrNull(16) ?: return null
                when (hex.length) {
                    3 -> { // #RGB
                        val r = (colorLong and 0xF00) shr 8
                        val g = (colorLong and 0x0F0) shr 4
                        val b = colorLong and 0x00F
                        Color(Color(0xFF000000 or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)).toArgb())
                    }
                    6 -> Color(Color(0xFF000000 or colorLong).toArgb()) // #RRGGBB
                    8 -> Color(Color(colorLong).toArgb()) // #AARRGGBB
                    else -> null
                }
            }
            sanitized.startsWith("rgb") -> {
                val isRgba = sanitized.startsWith("rgba")
                val valuesString = sanitized.substringAfter('(').substringBefore(')')
                val values = valuesString.split(',').map { it.trim() }

                if (values.size < 3) return null

                val r = values[0].toIntOrNull() ?: 0
                val g = values[1].toIntOrNull() ?: 0
                val b = values[2].toIntOrNull() ?: 0
                val a = if (isRgba && values.size == 4) (values[3].toFloatOrNull() ?: 1f) else 1f

                Color(r, g, b, (a * 255).roundToInt())
            }
            else -> when(sanitized) {
                "black" -> Color.Black
                "white" -> Color.White
                "red" -> Color.Red
                "green" -> Color.Green
                "blue" -> Color.Blue
                "gray", "grey" -> Color.Gray
                "cyan" -> Color.Cyan
                "magenta" -> Color.Magenta
                "yellow" -> Color.Yellow
                "transparent" -> Color.Transparent
                else -> null
            }
        }
    }

    private fun parseBoxBorders(value: String, baseFontSizeSp: Float, density: Float, containerWidthPx: Int): BoxBorders {
        val parts = value.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
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