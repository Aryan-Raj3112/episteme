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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import org.jsoup.Jsoup
import java.io.File
import java.net.URLDecoder

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ContentStyler(
    private val baseTextStyle: TextStyle,
    private val fontFamilyMap: Map<String, FontFamily>,
    private val density: Density,
    private val isDarkTheme: Boolean,
    private val chapterAbsPath: String,
    private val extractionBasePath: String,
    private val userTextAlign: TextAlign?
) {

    fun style(semanticBlocks: List<SemanticBlock>): List<ContentBlock> {
        return groupFloatingBlocks(semanticBlocks.mapNotNull { styleBlock(it) })
    }

    // ADD this function to group floating blocks, similar to the original parser
    private fun groupFloatingBlocks(blocks: List<ContentBlock>): List<ContentBlock> {
        if (blocks.isEmpty()) return emptyList()

        val result = mutableListOf<ContentBlock>()
        val processingQueue = blocks.toMutableList()

        while (processingQueue.isNotEmpty()) {
            val currentBlock = processingQueue.removeAt(0)
            val floatDirection = (currentBlock as? ImageBlock)?.style?.float

            if (currentBlock is ImageBlock && floatDirection in listOf("left", "right")) {
                val floatedImage = currentBlock
                val paragraphsToWrap = mutableListOf<ParagraphBlock>()

                while (processingQueue.isNotEmpty()) {
                    val nextBlock = processingQueue.first()
                    val nextBlockStyle = nextBlock.style
                    val shouldClear = nextBlockStyle.clear in listOf("both", floatDirection)
                    if (nextBlock is ParagraphBlock && !shouldClear) {
                        val paragraph = processingQueue.removeAt(0) as ParagraphBlock
                        paragraphsToWrap.add(paragraph)
                    } else {
                        break
                    }
                }
                val wrappingBlock = WrappingContentBlock(
                    floatedImage,
                    paragraphsToWrap,
                    elementId = floatedImage.elementId,
                    cfi = floatedImage.cfi,
                    blockIndex = floatedImage.blockIndex
                )
                result.add(wrappingBlock)
            } else {
                result.add(currentBlock)
            }
        }
        return result
    }

    private fun styleBlock(block: SemanticBlock): ContentBlock? {
        val themedStyle = applyThemeToStyle(block.style)
        return when (block) {
            is SemanticParagraph -> {
                val computedTextAlign = userTextAlign ?: themedStyle.paragraphStyle.textAlign

                ParagraphBlock(
                    content = buildAnnotatedString(block, themedStyle),
                    textAlign = computedTextAlign,
                    style = themedStyle.blockStyle,
                    elementId = block.elementId,
                    cfi = block.cfi,
                    startCharOffsetInSource = block.startCharOffsetInSource,
                    blockIndex = block.blockIndex
                )
            }

            is SemanticHeader -> HeaderBlock(
                level = block.level,
                content = buildAnnotatedString(block, themedStyle),
                textAlign = themedStyle.paragraphStyle.textAlign,
                style = themedStyle.blockStyle,
                elementId = block.elementId,
                cfi = block.cfi,
                startCharOffsetInSource = block.startCharOffsetInSource,
                blockIndex = block.blockIndex
            )

            is SemanticImage -> {
                var finalBlockStyle = themedStyle.blockStyle
                if (themedStyle.paragraphStyle.textAlign == TextAlign.Center) {
                    finalBlockStyle = finalBlockStyle.copy(horizontalAlign = "center")
                }
                val shouldInvert = themedStyle.blockStyle.filter == "invert(100%)"
                ImageBlock(
                    path = block.path,
                    altText = block.altText,
                    intrinsicWidth = block.intrinsicWidth,
                    intrinsicHeight = block.intrinsicHeight,
                    style = finalBlockStyle,
                    elementId = block.elementId,
                    cfi = block.cfi,
                    invertOnDarkTheme = shouldInvert,
                    blockIndex = block.blockIndex
                )
            }

            is SemanticMath -> {
                val finalSvgContent = when {
                    block.isFromMathJax || block.svgContent.isNullOrBlank() -> block.svgContent
                    else -> {
                        val themedSvg = applyThemeToSvg(block.svgContent)
                        embedImagesInSvg(themedSvg)
                    }
                }

                MathBlock(
                    svgContent = finalSvgContent,
                    altText = block.altText,
                    style = themedStyle.blockStyle,
                    elementId = block.elementId,
                    cfi = block.cfi,
                    svgWidth = block.svgWidth,
                    svgHeight = block.svgHeight,
                    svgViewBox = block.svgViewBox,
                    isFromMathJax = block.isFromMathJax,
                    blockIndex = block.blockIndex
                )
            }

            is SemanticList -> styleList(block, themedStyle)
            is SemanticTable -> styleTable(block, themedStyle)
            is SemanticSpacer -> {
                val height = if (block.isExplicitLineBreak) with(density) { baseTextStyle.fontSize.toDp() } else 8.dp
                SpacerBlock(height = height, style = themedStyle.blockStyle, elementId = block.elementId, cfi = block.cfi, blockIndex = block.blockIndex)
            }
            is SemanticFlexContainer -> FlexContainerBlock(
                children = block.children.mapNotNull { styleBlock(it) },
                style = themedStyle.blockStyle,
                elementId = block.elementId,
                cfi = block.cfi,
                blockIndex = block.blockIndex
            )
            is SemanticWrappingBlock -> {
                val styledImage = styleBlock(block.floatedImage) as? ImageBlock
                val styledParagraphs = block.paragraphsToWrap.mapNotNull { styleBlock(it) as? ParagraphBlock }
                if (styledImage != null) {
                    WrappingContentBlock(
                        floatedImage = styledImage,
                        paragraphsToWrap = styledParagraphs,
                        elementId = block.elementId,
                        cfi = block.cfi,
                        blockIndex = block.blockIndex
                    )
                } else {
                    null
                }
            }
            else -> {
                Timber.w("Unsupported or misplaced SemanticBlock type encountered: ${block::class.java.simpleName}")
                null
            }
        }
    }

    private fun applyThemeToStyle(style: CssStyle): CssStyle {
        val newSpanStyle = style.spanStyle.let { original ->
            val newColor = if (original.color.isSpecified) {
                CssParser.adaptColorForTheme(original.color, isDarkTheme, isBackground = false)
            } else {
                original.color
            }
            original.copy(color = newColor)
        }

        val newBlockStyle = style.blockStyle.let { original ->
            val newBgColor = if (original.backgroundColor.isSpecified) {
                CssParser.adaptColorForTheme(original.backgroundColor, isDarkTheme, isBackground = true)
            } else {
                original.backgroundColor
            }
            val newBorder = original.border?.let {
                val newBorderColor = CssParser.adaptColorForTheme(it.color, isDarkTheme, isBackground = false)
                it.copy(color = newBorderColor)
            }
            original.copy(backgroundColor = newBgColor, border = newBorder)
        }

        return style.copy(spanStyle = newSpanStyle, blockStyle = newBlockStyle)
    }

    private fun embedImagesInSvg(svgContent: String): String {
        try {
            val svgDocument = Jsoup.parseBodyFragment(svgContent)
            val svgElement = svgDocument.body().children().firstOrNull() ?: return svgContent

            svgElement.select("image").forEach { imageElement ->
                val href = imageElement.attr("href").ifBlank { imageElement.attr("xlink:href") }
                if (href.isNotBlank() && !href.startsWith("data:")) {
                    resolveImagePath(href)?.let { imageFile ->
                        try {
                            val imageBytes = imageFile.readBytes()
                            val mimeType = when (imageFile.extension.lowercase()) {
                                "jpg", "jpeg" -> "image/jpeg"
                                "png" -> "image/png"
                                "gif" -> "image/gif"
                                "webp" -> "image/webp"
                                else -> "application/octet-stream"
                            }
                            val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                            val dataUri = "data:$mimeType;base64,$base64"
                            imageElement.attr("xlink:href", dataUri)
                            imageElement.removeAttr("href")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to read and encode image file '$href' to Base64.")
                        }
                    }
                }
            }
            return svgElement.outerHtml()
        } catch (e: Exception) {
            Timber.e(e, "Error while embedding images in SVG content.")
            return svgContent
        }
    }

    private fun resolveImagePath(src: String): File? {
        if (src.isBlank()) return null
        val decodedSrc = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }
        val parentPath = File(this.chapterAbsPath).parent ?: ""
        val fromRelativeFile = File(this.extractionBasePath, File(parentPath, decodedSrc).path)
        if (fromRelativeFile.exists()) return fromRelativeFile
        val fromRootFile = File(this.extractionBasePath, decodedSrc)
        if (fromRootFile.exists()) return fromRootFile
        Timber.w("Image not found for SVG embedding. Tried: ${fromRelativeFile.absolutePath} and ${fromRootFile.absolutePath}")
        return null
    }

    private fun applyThemeToSvg(svgContent: String): String {
        if (svgContent.isBlank()) return svgContent
        try {
            val textColorHex = baseTextStyle.color.toCssHexString()
            val svgDocument = Jsoup.parseBodyFragment(svgContent)
            val svgElement = svgDocument.body().children().firstOrNull() ?: return svgContent

            svgElement.select("text").forEach { textElement ->
                val existingStyle = textElement.attr("style")
                val styleWithoutFill = existingStyle.replace(Regex("""\bfill\s*:\s*[^;]+;?"""), "")
                val newStyle = "fill:$textColorHex; $styleWithoutFill".trim()
                textElement.attr("style", newStyle)
                textElement.removeAttr("fill")
            }
            return svgElement.outerHtml()
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply dark theme to SVG content.")
            return svgContent
        }
    }

    private fun Color.toCssHexString(): String {
        val red = (this.red * 255).toInt()
        val green = (this.green * 255).toInt()
        val blue = (this.blue * 255).toInt()
        return String.format("#%02X%02X%02X", red, green, blue)
    }

    private fun buildAnnotatedString(
        block: SemanticTextBlock,
        blockStyle: CssStyle
    ): AnnotatedString {
        Timber.d("ContentStyler: Building annotated string. UserAlign=$userTextAlign, CSSAlign=${blockStyle.paragraphStyle.textAlign}")

        val builtString = buildAnnotatedString {
            val rootFontFamily = findFirstAvailableFontFamily(blockStyle.fontFamilies, fontFamilyMap)
            val hyphensValue = if (blockStyle.hyphens == "auto") Hyphens.Auto else Hyphens.None
            val mergedParagraphStyle = baseTextStyle.toParagraphStyle().merge(blockStyle.paragraphStyle)

            val finalTextAlign = if (block is SemanticParagraph && userTextAlign != null) {
                userTextAlign
            } else if (mergedParagraphStyle.textAlign == TextAlign.Justify) {
                TextAlign.Left
            } else {
                mergedParagraphStyle.textAlign
            }

            val isParagraph = block is SemanticParagraph
            val finalLineHeight = if (isParagraph && baseTextStyle.lineHeight.isSpecified) {
                baseTextStyle.lineHeight
            } else {
                mergedParagraphStyle.lineHeight
            }

            val finalParagraphStyle = ParagraphStyle(
                textAlign = finalTextAlign,
                textDirection = mergedParagraphStyle.textDirection,
                lineHeight = finalLineHeight,
                textIndent = mergedParagraphStyle.textIndent,
                platformStyle = mergedParagraphStyle.platformStyle,
                lineHeightStyle = mergedParagraphStyle.lineHeightStyle,
                lineBreak = LineBreak.Paragraph,
                hyphens = hyphensValue,
                textMotion = mergedParagraphStyle.textMotion
            )

            var initialSpanStyle = baseTextStyle.toSpanStyle()
                .merge(blockStyle.spanStyle)
                .copy(fontFamily = baseTextStyle.fontFamily)

            if (rootFontFamily == FontFamily.Monospace) {
                initialSpanStyle = initialSpanStyle.copy(fontFamily = rootFontFamily)
            }

            Timber.d("ContentStyler: InitialSpanStyle. BaseFontSize=${baseTextStyle.fontSize}, BlockFontSize=${blockStyle.spanStyle.fontSize} -> Merged=${initialSpanStyle.fontSize}")

            withStyle(finalParagraphStyle) {
                withStyle(initialSpanStyle) {
                    append(block.text)
                    block.spans.sortedBy { it.start }.forEach { span ->
                        val themedSpanStyle = applyThemeToStyle(span.style)
                        val fontFamily = findFirstAvailableFontFamily(themedSpanStyle.fontFamilies, fontFamilyMap)
                        val baselineShift = when (span.tag) {
                            "sub" -> BaselineShift.Subscript
                            "sup" -> BaselineShift.Superscript
                            else -> null
                        }
                        val finalSpanStyle = themedSpanStyle.spanStyle.copy(
                            fontFamily = fontFamily,
                            baselineShift = baselineShift
                        )
                        addStyle(initialSpanStyle.merge(finalSpanStyle), span.start, span.end)

                        if (span.linkHref != null) {
                            addStringAnnotation("URL", span.linkHref, span.start, span.end)
                        }
                        if (span.elementId != null) {
                            addStringAnnotation("ID", span.elementId, span.start, span.end)
                        }
                    }
                }
            }
        }
        return builtString.maybeAdjustLineHeightForEmphasis()
    }

    private fun AnnotatedString.maybeAdjustLineHeightForEmphasis(): AnnotatedString {
        if (this.getStringAnnotations("TextEmphasis", 0, this.length).isNotEmpty()) {
            val currentParagraphStyle = this.paragraphStyles.firstOrNull()?.item ?: ParagraphStyle()
            val currentLineHeight = currentParagraphStyle.lineHeight
            val newLineHeight = if (currentLineHeight.isUnspecified || currentLineHeight.value == 0f) {
                1.8.em
            } else if (currentLineHeight.isEm) {
                (currentLineHeight.value * 1.3f).em
            } else if (currentLineHeight.isSp) {
                (currentLineHeight.value * 1.3f).sp
            } else {
                1.8.em
            }
            return buildAnnotatedString {
                withStyle(ParagraphStyle(lineHeight = newLineHeight)) {
                    append(this@maybeAdjustLineHeightForEmphasis)
                }
            }
        }
        return this
    }

    private fun styleList(list: SemanticList, listStyle: CssStyle): ContentBlock {
        var itemCounter = 1

        val items = list.items.map { item ->
            val itemThemedStyle = applyThemeToStyle(item.style)
            val mergedBlockStyle = listStyle.blockStyle.merge(itemThemedStyle.blockStyle)

            val marker = getListMarker(
                listStyleType = mergedBlockStyle.listStyleType,
                counter = itemCounter,
                isOrdered = list.isOrdered
            )
            itemCounter++
            ListItemBlock(
                content = buildAnnotatedString(item, itemThemedStyle),
                itemMarker = marker,
                itemMarkerImage = item.itemMarkerImage,
                style = mergedBlockStyle,
                elementId = item.elementId,
                cfi = item.cfi,
                startCharOffsetInSource = item.startCharOffsetInSource,
                blockIndex = item.blockIndex
            )
        }
        return FlexContainerBlock(items, listStyle.blockStyle, list.elementId, list.cfi, list.blockIndex)
    }

    private fun styleTable(table: SemanticTable, tableStyle: CssStyle): TableBlock {
        val rows = table.rows.map { row ->
            row.map { cell ->
                val cellCssStyle = applyThemeToStyle(cell.style)
                TableCell(
                    content = cell.content.mapNotNull { styleBlock(it) },
                    isHeader = cell.isHeader,
                    style = cellCssStyle,
                    colspan = cell.colspan
                )
            }
        }
        return TableBlock(
            rows = rows,
            style = tableStyle.blockStyle,
            elementId = table.elementId,
            cfi = table.cfi,
            blockIndex = table.blockIndex
        )
    }

    private fun findFirstAvailableFontFamily(
        fontFamilyNames: List<String>,
        fontFamilyMap: Map<String, FontFamily>
    ): FontFamily? {
        if (fontFamilyNames.isEmpty()) return null
        val specificFont = fontFamilyNames.firstNotNullOfOrNull { fontFamilyMap[it] }
        if (specificFont != null) return specificFont
        return fontFamilyNames.firstNotNullOfOrNull { name -> FontFamilyMapper.nameToFontFamily(name) }
    }

    private fun toRoman(number: Int): String {
        if (number < 1 || number > 3999) return number.toString()
        val values = listOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = listOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val result = StringBuilder()
        var num = number
        for (i in values.indices) {
            while (num >= values[i]) {
                num -= values[i]
                result.append(symbols[i])
            }
        }
        return result.toString()
    }

    private fun toAlpha(number: Int): String {
        if (number < 1) return number.toString()
        var n = number
        val result = StringBuilder()
        while (n > 0) {
            n--
            result.insert(0, ('a' + n % 26))
            n /= 26
        }
        return result.toString()
    }

    private fun getListMarker(listStyleType: String?, counter: Int, isOrdered: Boolean): String? {
        val finalType = listStyleType?.trim()?.lowercase() ?: if (isOrdered) "decimal" else "disc"

        return when (finalType) {
            "none" -> null
            "disc" -> "• "
            "circle" -> "◦ "
            "square" -> "■ "
            "decimal" -> "$counter. "
            "decimal-leading-zero" -> "${counter.toString().padStart(2, '0')}. "
            "lower-roman" -> toRoman(counter).lowercase() + ". "
            "upper-roman" -> toRoman(counter).uppercase() + ". "
            "lower-latin", "lower-alpha" -> toAlpha(counter) + ". "
            "upper-latin", "upper-alpha" -> toAlpha(counter).uppercase() + ". "
            else -> if (isOrdered) "$counter. " else "• "
        }
    }
}