package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.shared.reader.resolvePaginatedReaderTextAlign

fun resolveReaderParagraphStyle(
    baseTextStyle: TextStyle,
    cssStyle: CssStyle,
    isParagraph: Boolean,
    userTextAlign: TextAlign?
): ParagraphStyle {
    val merged = baseTextStyle.toParagraphStyle().merge(cssStyle.paragraphStyle)
    return ParagraphStyle(
        textAlign = resolvePaginatedReaderTextAlign(
            cssTextAlign = merged.textAlign,
            userTextAlign = userTextAlign.takeIf { isParagraph }
        ),
        textDirection = merged.textDirection.takeUnless { it == TextDirection.Unspecified }
            ?: TextDirection.ContentOrLtr,
        lineHeight = if (isParagraph && baseTextStyle.lineHeight.isSpecified) {
            baseTextStyle.lineHeight
        } else {
            merged.lineHeight
        },
        textIndent = merged.textIndent,
        platformStyle = merged.platformStyle,
        lineHeightStyle = merged.lineHeightStyle,
        lineBreak = LineBreak.Simple,
        hyphens = if (cssStyle.hyphens == "auto") Hyphens.Auto else Hyphens.None,
        textMotion = merged.textMotion
    )
}

fun resolveReaderBlockFontFamily(
    resolvedCssFamily: FontFamily?,
    baseFontFamily: FontFamily?
): FontFamily? = when {
    resolvedCssFamily == FontFamily.Monospace -> FontFamily.Monospace
    baseFontFamily != null && baseFontFamily != FontFamily.Default -> baseFontFamily
    else -> resolvedCssFamily ?: baseFontFamily
}

fun resolveReaderSpanFontFamily(
    resolvedCssFamily: FontFamily?,
    baseFontFamily: FontFamily?
): FontFamily? = when {
    resolvedCssFamily == FontFamily.Monospace -> FontFamily.Monospace
    baseFontFamily != null && baseFontFamily != FontFamily.Default -> baseFontFamily
    else -> resolvedCssFamily
}

fun readerBaselineShift(tag: String): BaselineShift? = when (tag) {
    "sub" -> BaselineShift.Subscript
    "sup" -> BaselineShift.Superscript
    else -> null
}

data class ReaderCustomUnderlinePolicy(
    val spanStyle: SpanStyle,
    val annotationData: String?
)

fun readerCustomUnderlinePolicy(
    spanStyle: SpanStyle,
    cssStyle: CssStyle
): ReaderCustomUnderlinePolicy {
    val hasCustomDecoration = cssStyle.textDecorationStyle != null ||
        cssStyle.textDecorationColor.isSpecified ||
        cssStyle.textUnderlineOffset.isSpecified
    val decoration = spanStyle.textDecoration ?: TextDecoration.None
    if (!hasCustomDecoration || !decoration.contains(TextDecoration.Underline)) {
        return ReaderCustomUnderlinePolicy(spanStyle, null)
    }
    val remainingDecorations = buildList {
        if (decoration.contains(TextDecoration.LineThrough)) add(TextDecoration.LineThrough)
    }
    val adjustedStyle = spanStyle.copy(
        textDecoration = if (remainingDecorations.isEmpty()) {
            TextDecoration.None
        } else {
            TextDecoration.combine(remainingDecorations)
        }
    )
    val style = cssStyle.textDecorationStyle ?: "solid"
    val color = if (cssStyle.textDecorationColor.isSpecified) {
        cssStyle.textDecorationColor.value.toString()
    } else {
        "Unspecified"
    }
    val offset = if (cssStyle.textUnderlineOffset.isSpecified) {
        cssStyle.textUnderlineOffset.value.toString()
    } else {
        "0"
    }
    return ReaderCustomUnderlinePolicy(adjustedStyle, "$style|$color|$offset")
}

fun readerWordSpacingOffsets(
    text: String,
    start: Int,
    end: Int,
    cssStyle: CssStyle
): List<Int> {
    val boundedStart = start.coerceIn(0, text.length)
    val boundedEnd = end.coerceIn(boundedStart, text.length)
    val spacing = cssStyle.wordSpacing
    if (!spacing.isSpecified || spacing.value == 0f || boundedStart >= boundedEnd) return emptyList()
    return (boundedStart until boundedEnd).filter { text[it] == ' ' }
}

fun readerContentBlockStyle(
    style: CssStyle,
    isParagraph: Boolean,
    paragraphGapMultiplier: Float
): BlockStyle {
    if (!isParagraph) return style.blockStyle
    val margin = style.blockStyle.margin
    return style.blockStyle.copy(
        margin = margin.copy(
            top = margin.top * paragraphGapMultiplier,
            bottom = margin.bottom * paragraphGapMultiplier
        )
    )
}

fun readerImageBlockStyle(style: CssStyle): BlockStyle =
    if (style.paragraphStyle.textAlign == androidx.compose.ui.text.style.TextAlign.Center) {
        style.blockStyle.copy(horizontalAlign = "center")
    } else {
        style.blockStyle
    }

fun shouldInvertReaderImage(style: CssStyle): Boolean = style.blockStyle.filter == "invert(100%)"

fun readerMathSvgContent(
    svgContent: String?,
    isFromMathJax: Boolean,
    adaptThemeColors: Boolean,
    applyTheme: (String) -> String,
    embedImages: (String) -> String
): String? {
    val nonBlankSvgContent = svgContent?.takeIf(String::isNotBlank)
    return when {
        isFromMathJax || nonBlankSvgContent == null -> svgContent
        !adaptThemeColors -> embedImages(nonBlankSvgContent)
        else -> embedImages(applyTheme(nonBlankSvgContent))
    }
}

fun readerChantUnits(
    block: SemanticFlexContainer,
    styleTextBlock: (SemanticBlock) -> TextContentBlock?
): List<ChantUnitBlock> {
    val units = mutableListOf<ChantUnitBlock>()
    fun addUnit(unit: SemanticFlexContainer, keepWithNext: Boolean) {
        val rows = unit.children.mapNotNull(styleTextBlock)
        val marker = unit.style.blockStyle.display.orEmpty()
        val isDropCap = marker == "reader-chant-dropcap"
        units += ChantUnitBlock(
            neume = if (isDropCap) AnnotatedString("") else rows.getOrNull(0)?.content ?: AnnotatedString(""),
            lyric = if (isDropCap) rows.getOrNull(0)?.content ?: AnnotatedString("") else rows.getOrNull(1)?.content ?: AnnotatedString(""),
            keepWithNext = keepWithNext,
            underlineBefore = ":before" in marker,
            underlineAfter = ":after" in marker,
            isDropCap = isDropCap
        )
    }
    block.children.filterIsInstance<SemanticFlexContainer>().forEach { container ->
        if (container.style.blockStyle.display == "reader-chant-nonbreaking") {
            val nestedUnits = container.children.filterIsInstance<SemanticFlexContainer>()
            nestedUnits.forEachIndexed { index, unit -> addUnit(unit, index < container.children.lastIndex) }
        } else {
            addUnit(container, keepWithNext = false)
        }
    }
    return units
}

fun AnnotatedString.adjustReaderLineHeightForEmphasis(): AnnotatedString {
    if (getStringAnnotations("TextEmphasis", 0, length).isEmpty()) return this
    val currentLineHeight = paragraphStyles.firstOrNull()?.item?.lineHeight ?: androidx.compose.ui.unit.TextUnit.Unspecified
    val adjustedLineHeight = when {
        currentLineHeight.isUnspecified || currentLineHeight.value == 0f -> 1.8.em
        currentLineHeight.isEm -> (currentLineHeight.value * 1.3f).em
        currentLineHeight.isSp -> (currentLineHeight.value * 1.3f).sp
        else -> 1.8.em
    }
    return buildAnnotatedString {
        withStyle(ParagraphStyle(lineHeight = adjustedLineHeight)) {
            append(this@adjustReaderLineHeightForEmphasis)
        }
    }
}

fun applyReaderThemeDuringContentStyling(
    style: CssStyle,
    adaptThemeColors: Boolean,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): CssStyle {
    if (!adaptThemeColors) return style
    val spanStyle = style.spanStyle.let { original ->
        val color = if (original.color.isSpecified) {
            CssParser.adaptColorForTheme(original.color, isDarkTheme, false, themeBackgroundColor, themeTextColor)
        } else {
            original.color
        }
        original.copy(color = color)
    }
    fun BorderStyle?.themed(): BorderStyle? = this?.copy(
        color = CssParser.adaptColorForTheme(color, isDarkTheme, false, themeBackgroundColor, themeTextColor)
    )
    val blockStyle = style.blockStyle.let { original ->
        val background = if (original.backgroundColor.isSpecified) {
            CssParser.adaptColorForTheme(original.backgroundColor, isDarkTheme, true, themeBackgroundColor, themeTextColor)
        } else {
            original.backgroundColor
        }
        original.copy(
            backgroundColor = background,
            borderTop = original.borderTop.themed(),
            borderRight = original.borderRight.themed(),
            borderBottom = original.borderBottom.themed(),
            borderLeft = original.borderLeft.themed()
        )
    }
    val decorationColor = if (style.textDecorationColor.isSpecified) {
        CssParser.adaptColorForTheme(style.textDecorationColor, isDarkTheme, false, themeBackgroundColor, themeTextColor)
    } else {
        style.textDecorationColor
    }
    return style.copy(
        spanStyle = spanStyle,
        blockStyle = blockStyle,
        textDecorationColor = decorationColor
    )
}

fun groupReaderFloatingBlocks(blocks: List<ContentBlock>): List<ContentBlock> {
    if (blocks.isEmpty()) return emptyList()
    val result = mutableListOf<ContentBlock>()
    val processingQueue = blocks.toMutableList()
    while (processingQueue.isNotEmpty()) {
        val currentBlock = processingQueue.removeAt(0)
        val floatDirection = (currentBlock as? ImageBlock)?.style?.float
        if (currentBlock is ImageBlock && floatDirection in listOf("left", "right")) {
            val paragraphsToWrap = mutableListOf<ParagraphBlock>()
            while (processingQueue.isNotEmpty()) {
                val nextBlock = processingQueue.first()
                val shouldClear = nextBlock.style.clear in listOf("both", floatDirection)
                if (nextBlock is ParagraphBlock && !shouldClear) {
                    paragraphsToWrap += processingQueue.removeAt(0) as ParagraphBlock
                } else {
                    break
                }
            }
            result += WrappingContentBlock(
                floatedImage = currentBlock,
                paragraphsToWrap = paragraphsToWrap,
                elementId = currentBlock.elementId,
                cfi = currentBlock.cfi,
                blockIndex = currentBlock.blockIndex
            )
        } else {
            result += currentBlock
        }
    }
    return result
}

fun readerListMarker(
    listStyleType: String?,
    counter: Int,
    isOrdered: Boolean
): String? {
    val finalType = listStyleType?.trim()?.lowercase() ?: if (isOrdered) "decimal" else "disc"
    return when (finalType) {
        "none" -> null
        "disc" -> "• "
        "circle" -> "◦ "
        "square" -> "■ "
        "decimal" -> "$counter. "
        "decimal-leading-zero" -> "${counter.toString().padStart(2, '0')}. "
        "lower-roman" -> toReaderRoman(counter).lowercase() + ". "
        "upper-roman" -> toReaderRoman(counter).uppercase() + ". "
        "lower-latin", "lower-alpha" -> toReaderAlpha(counter) + ". "
        "upper-latin", "upper-alpha" -> toReaderAlpha(counter).uppercase() + ". "
        else -> if (isOrdered) "$counter. " else "• "
    }
}

private fun toReaderRoman(number: Int): String {
    if (number !in 1..3999) return number.toString()
    val values = listOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = listOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    val result = StringBuilder()
    var remaining = number
    for (index in values.indices) {
        while (remaining >= values[index]) {
            remaining -= values[index]
            result.append(symbols[index])
        }
    }
    return result.toString()
}

private fun toReaderAlpha(number: Int): String {
    if (number < 1) return number.toString()
    var remaining = number
    val result = StringBuilder()
    while (remaining > 0) {
        remaining--
        result.insert(0, ('a' + remaining % 26))
        remaining /= 26
    }
    return result.toString()
}
