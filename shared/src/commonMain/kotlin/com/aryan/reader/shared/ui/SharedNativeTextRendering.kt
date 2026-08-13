package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.BorderStyle
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.resolveSharedReaderFontFeatureSettings
import com.aryan.reader.shared.reader.resolveSharedReaderTextAlign
import kotlin.math.abs

@Composable
internal fun SharedSemanticTextView(
    block: SemanticTextBlock,
    page: ReaderPage,
    modifier: Modifier,
    background: Color,
    foreground: Color,
    searchQuery: String,
    searchHighlight: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    fallbackFontFamily: FontFamily,
    settings: ReaderSettings,
    fontWeight: FontWeight? = null,
    onReaderTap: () -> Unit,
    onSelectionChange: (SharedNativeReaderTextSelection?) -> Unit,
    onSelectionGestureActiveChange: (Boolean) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onLinkClicked: (SharedNativeReaderLinkClick) -> Unit,
    selectionLayouts: MutableMap<String, SharedNativeTextLayoutInfo>,
    onTextLaidOut: ((SharedNativeTextFit) -> Unit)? = null
) {
    val textStyle = block.renderedTextStyle(
        settings = settings,
        fallbackFontFamily = fallbackFontFamily,
        fallbackTextAlign = fallbackTextAlign,
        fontWeight = fontWeight,
        background = background,
        foreground = foreground
    )
    val textColor = textStyle.color.takeIf { it.isSpecified } ?: foreground
    SharedNativeInteractiveText(
        text = block.toAnnotatedString(
            query = searchQuery,
                highlightColor = searchHighlight,
                highlights = highlights,
                activeSelection = activeSelection,
                selectionHighlight = selectionHighlight,
                fallbackTextAlign = fallbackTextAlign,
                blockFontSizeSp = textStyle.fontSize.value,
                chapterIndex = page.chapterIndex,
            pageIndex = page.pageIndex,
            blockCfi = block.cfi,
            blockIndex = block.blockIndex,
            blockCharOffset = block.startCharOffsetInSource,
            background = background,
            foreground = foreground,
            isDarkTheme = settings.darkMode
        ),
        page = page,
        textBlock = SharedNativeTextBlockDescriptor(
            chapterIndex = page.chapterIndex,
            pageIndex = page.pageIndex,
            blockIndex = block.blockIndex,
            blockCharOffset = block.startCharOffsetInSource,
            baseCfi = block.cfi,
            textStartOffset = block.startCharOffsetInSource,
            text = block.text
        ),
        textStartOffset = block.startCharOffsetInSource,
        color = textColor,
        modifier = modifier,
        textAlign = resolveSharedReaderTextAlign(
            cssTextAlign = block.style.paragraphStyle.textAlign,
            fallbackTextAlign = fallbackTextAlign
        ),
        style = textStyle,
        activeSelection = activeSelection,
        onReaderTap = onReaderTap,
        onSelectionChange = onSelectionChange,
        onSelectionGestureActiveChange = onSelectionGestureActiveChange,
        onHighlightSelected = onHighlightSelected,
        onLinkClicked = onLinkClicked,
        selectionLayouts = selectionLayouts,
        onTextLaidOut = onTextLaidOut,
        fitLabel = SharedNativeTextFitLabel(
            page = page,
            blockIndex = block.blockIndex,
            kind = block.sharedNativeKindName(),
            sourceRange = block.sharedNativeSourceRangeLabel(),
            textChars = block.text.length
        )
    )
}

@Composable
internal fun SemanticTextBlock.renderedTextStyle(
    settings: ReaderSettings,
    fallbackFontFamily: FontFamily,
    fallbackTextAlign: TextAlign,
    fontWeight: FontWeight? = null,
    background: Color,
    foreground: Color
): TextStyle {
    val fontSize = (style.fontSize.takeIfSpecified()
        ?: style.spanStyle.fontSize.takeIfSpecified())
        ?.resolveFontSizeSp(settings.fontSize.toFloat())
        ?: when (this) {
            is SemanticHeader -> (settings.fontSize * headerScale(level)).sp
            else -> settings.fontSize.sp
        }
    val lineHeight = style.paragraphStyle.lineHeight.takeIfSpecified()
        ?.resolveLineHeightSp(fontSize.value)
        ?: (fontSize.value * settings.lineSpacing).sp
    return MaterialTheme.typography.bodyLarge.copy(
        color = style.spanStyle.color.sharedNativeThemeColor(
            isDarkTheme = settings.darkMode,
            isBackground = false,
            background = background,
            foreground = foreground
        ).takeIf { it.isSpecified } ?: foreground,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = style.spanStyle.fontFamily ?: fallbackFontFamily,
        fontWeight = settings.fontWeight.takeIf { it > 0 }?.let(::FontWeight)
            ?: fontWeight
            ?: style.spanStyle.fontWeight
            ?: if (this is SemanticHeader) FontWeight.Bold else MaterialTheme.typography.bodyLarge.fontWeight,
        fontStyle = style.spanStyle.fontStyle ?: MaterialTheme.typography.bodyLarge.fontStyle,
        textDecoration = style.spanStyle.textDecoration ?: MaterialTheme.typography.bodyLarge.textDecoration,
        textAlign = resolveSharedReaderTextAlign(
            cssTextAlign = style.paragraphStyle.textAlign,
            fallbackTextAlign = fallbackTextAlign
        )
    ).withAndroidPaginationTextMetrics(settings.letterSpacing)
}

internal fun TextStyle.withAndroidPaginationTextMetrics(readerLetterSpacing: Float = 0f): TextStyle {
    return copy(
        lineBreak = LineBreak.Paragraph,
        letterSpacing = readerLetterSpacing.em,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None
        )
    )
}

internal fun SemanticTextBlock.toAnnotatedString(
    query: String,
    highlightColor: Color,
    highlights: List<UserHighlight>,
    activeSelection: SharedNativeReaderTextSelection?,
    selectionHighlight: Color,
    fallbackTextAlign: TextAlign,
    blockFontSizeSp: Float,
    chapterIndex: Int,
    pageIndex: Int,
    blockCfi: String?,
    blockIndex: Int,
    blockCharOffset: Int,
    background: Color,
    foreground: Color,
    isDarkTheme: Boolean
): AnnotatedString {
    val normalized = query.trim()
    return buildAnnotatedString {
        withStyle(sharedNativeParagraphStyle(fallbackTextAlign)) {
            append(text)
        }
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start < end) {
                val linkHref = span.linkHref?.takeIf { it.isNotBlank() }
                val spanStyle = span.style.toRenderedSpanStyle(blockFontSizeSp, span.tag)
                    .sharedNativeThemeSpanStyle(
                        isDarkTheme = isDarkTheme,
                        background = background,
                        foreground = foreground
                    )
                    .let { renderedStyle ->
                        if (linkHref != null) {
                            renderedStyle.withSharedNativeReaderLinkStyle(
                                isDarkTheme = isDarkTheme,
                                themeBackgroundColor = background,
                                themeTextColor = foreground
                            )
                        } else {
                            renderedStyle
                        }
                    }
                addStyle(
                    spanStyle,
                    start,
                    end
                )
                addSharedNativeWordSpacing(
                    text = text,
                    start = start,
                    end = end,
                    wordSpacing = span.style.wordSpacing
                )
                linkHref?.let { href ->
                    addStringAnnotation(ReaderNativeAnnotationUrl, href, start, end)
                }
            }
        }
        highlights.forEach { highlight ->
            applyHighlightToTextRange(
                highlight = highlight,
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                blockCfi = blockCfi,
                blockIndex = blockIndex,
                blockCharOffset = blockCharOffset,
                textStartOffset = startCharOffsetInSource,
                textLength = text.length,
                text = text
            )
        }
        applySelectionToTextRange(
            selection = activeSelection,
            pageIndex = pageIndex,
            blockIndex = blockIndex,
            blockCharOffset = blockCharOffset,
            textStartOffset = startCharOffsetInSource,
            textLength = text.length,
            color = selectionHighlight
        )
        if (normalized.length >= 2) {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = text.indexOf(normalized, startIndex, ignoreCase = true)
                if (index < 0) break
                addStyle(SpanStyle(background = highlightColor), index, index + normalized.length)
                startIndex = index + normalized.length
            }
        }
    }
}

internal fun SemanticTextBlock.sharedNativeParagraphStyle(fallbackTextAlign: TextAlign): ParagraphStyle {
    return ParagraphStyle(
        textAlign = resolveSharedReaderTextAlign(
            cssTextAlign = style.paragraphStyle.textAlign,
            fallbackTextAlign = fallbackTextAlign
        ),
        textIndent = style.paragraphStyle.textIndent,
        lineBreak = LineBreak.Paragraph,
        hyphens = style.sharedNativeHyphens()
    )
}

internal fun AnnotatedString.Builder.addSharedNativeWordSpacing(
    text: String,
    start: Int,
    end: Int,
    wordSpacing: TextUnit
) {
    if (!wordSpacing.isSpecified || wordSpacing.value == 0f) return
    for (index in start until end) {
        if (text[index] == ' ') {
            addStyle(SpanStyle(letterSpacing = wordSpacing), index, index + 1)
        }
    }
}

internal fun CssStyle.sharedNativeHyphens(): Hyphens {
    return if (hyphens == "auto") Hyphens.Auto else Hyphens.None
}

internal fun BlockStyle.isSharedNativeVisibilityHidden(): Boolean {
    return visibility == "hidden"
}

internal fun Modifier.sharedNativeVisibility(blockStyle: BlockStyle): Modifier {
    return if (blockStyle.isSharedNativeVisibilityHidden()) {
        graphicsLayer(alpha = 0f)
    } else {
        this
    }
}

internal fun TextUnit.takeIfSpecified(): TextUnit? = if (isSpecified) this else null

internal fun Color.blendWith(other: Color, foregroundWeight: Float): Color {
    val weight = foregroundWeight.coerceIn(0f, 1f)
    val baseWeight = 1f - weight
    return Color(
        red * baseWeight + other.red * weight,
        green * baseWeight + other.green * weight,
        blue * baseWeight + other.blue * weight,
        alpha
    )
}

internal fun TextUnit.resolveFontSizeSp(baseFontSizeSp: Float): TextUnit {
    return when {
        isEm -> (baseFontSizeSp * value).sp
        else -> value.sp
    }
}

internal fun TextUnit.resolveLineHeightSp(fontSizeSp: Float): TextUnit {
    return when {
        isEm -> (fontSizeSp * value).sp
        else -> value.sp
    }
}

internal fun Modifier.sharedNativeCssBox(blockStyle: BlockStyle): Modifier = drawBehind {
    val borderTop = blockStyle.borderTop
    val borderRight = blockStyle.borderRight
    val borderBottom = blockStyle.borderBottom
    val borderLeft = blockStyle.borderLeft
    val topWidth = borderTop?.width?.toPx() ?: 0f
    val rightWidth = borderRight?.width?.toPx() ?: 0f
    val bottomWidth = borderBottom?.width?.toPx() ?: 0f
    val leftWidth = borderLeft?.width?.toPx() ?: 0f

    val topLeftRadius = blockStyle.borderTopLeftRadius.toPx()
    val topRightRadius = blockStyle.borderTopRightRadius.toPx()
    val bottomRightRadius = blockStyle.borderBottomRightRadius.toPx()
    val bottomLeftRadius = blockStyle.borderBottomLeftRadius.toPx()

    if (blockStyle.backgroundColor.isSpecified && blockStyle.backgroundColor != Color.Transparent) {
        val backgroundPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(topLeftRadius, topLeftRadius),
                    topRight = CornerRadius(topRightRadius, topRightRadius),
                    bottomRight = CornerRadius(bottomRightRadius, bottomRightRadius),
                    bottomLeft = CornerRadius(bottomLeftRadius, bottomLeftRadius)
                )
            )
        }
        drawPath(backgroundPath, color = blockStyle.backgroundColor, style = Fill)
    }

    fun pathEffect(style: String?, width: Float): PathEffect? {
        return when (style) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 2f), 0f)
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(width, width), 0f)
            else -> null
        }
    }

    if (topWidth > 0f && borderTop != null) {
        drawLine(
            color = borderTop.color,
            start = Offset(if (topLeftRadius > 0f) topLeftRadius else 0f, topWidth / 2f),
            end = Offset(if (topRightRadius > 0f) size.width - topRightRadius else size.width, topWidth / 2f),
            strokeWidth = topWidth,
            pathEffect = pathEffect(borderTop.style, topWidth)
        )
    }

    if (bottomWidth > 0f && borderBottom != null) {
        val y = size.height - (bottomWidth / 2f)
        drawLine(
            color = borderBottom.color,
            start = Offset(if (bottomLeftRadius > 0f) bottomLeftRadius else 0f, y),
            end = Offset(if (bottomRightRadius > 0f) size.width - bottomRightRadius else size.width, y),
            strokeWidth = bottomWidth,
            pathEffect = pathEffect(borderBottom.style, bottomWidth)
        )
    }

    if (leftWidth > 0f && borderLeft != null) {
        drawLine(
            color = borderLeft.color,
            start = Offset(leftWidth / 2f, if (topLeftRadius > 0f) topLeftRadius else 0f),
            end = Offset(leftWidth / 2f, if (bottomLeftRadius > 0f) size.height - bottomLeftRadius else size.height),
            strokeWidth = leftWidth,
            pathEffect = pathEffect(borderLeft.style, leftWidth)
        )
    }

    if (rightWidth > 0f && borderRight != null) {
        val x = size.width - (rightWidth / 2f)
        drawLine(
            color = borderRight.color,
            start = Offset(x, if (topRightRadius > 0f) topRightRadius else 0f),
            end = Offset(x, if (bottomRightRadius > 0f) size.height - bottomRightRadius else size.height),
            strokeWidth = rightWidth,
            pathEffect = pathEffect(borderRight.style, rightWidth)
        )
    }

    if (topLeftRadius > 0f && topWidth > 0f && leftWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth / 2f, topWidth / 2f),
            size = Size(topLeftRadius * 2 - leftWidth, topLeftRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (topRightRadius > 0f && topWidth > 0f && rightWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (topRightRadius * 2) + (rightWidth / 2f), topWidth / 2f),
            size = Size(topRightRadius * 2 - rightWidth, topRightRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (bottomRightRadius > 0f && bottomWidth > 0f && rightWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(
                size.width - (bottomRightRadius * 2) + (rightWidth / 2f),
                size.height - (bottomRightRadius * 2) + (bottomWidth / 2f)
            ),
            size = Size(bottomRightRadius * 2 - rightWidth, bottomRightRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }

    if (bottomLeftRadius > 0f && bottomWidth > 0f && leftWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth / 2f, size.height - (bottomLeftRadius * 2) + (bottomWidth / 2f)),
            size = Size(bottomLeftRadius * 2 - leftWidth, bottomLeftRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }
}

internal fun BlockStyle.sharedNativeThemeBlockStyle(
    isDarkTheme: Boolean,
    background: Color,
    foreground: Color
): BlockStyle {
    return copy(
        backgroundColor = backgroundColor.sharedNativeThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = true,
            background = background,
            foreground = foreground
        ),
        borderTop = borderTop?.sharedNativeThemeBorderStyle(isDarkTheme, background, foreground),
        borderRight = borderRight?.sharedNativeThemeBorderStyle(isDarkTheme, background, foreground),
        borderBottom = borderBottom?.sharedNativeThemeBorderStyle(isDarkTheme, background, foreground),
        borderLeft = borderLeft?.sharedNativeThemeBorderStyle(isDarkTheme, background, foreground)
    )
}

internal fun BorderStyle.sharedNativeThemeBorderStyle(
    isDarkTheme: Boolean,
    background: Color,
    foreground: Color
): BorderStyle {
    return copy(
        color = color.sharedNativeThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = false,
            background = background,
            foreground = foreground
        )
    )
}

internal fun SpanStyle.sharedNativeThemeSpanStyle(
    isDarkTheme: Boolean,
    background: Color,
    foreground: Color
): SpanStyle {
    return copy(
        color = color.sharedNativeThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = false,
            background = background,
            foreground = foreground
        ),
        background = this.background.sharedNativeThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = true,
            background = background,
            foreground = foreground
        )
    )
}

internal fun sharedNativeReaderLinkSpanStyle(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    existingDecoration: TextDecoration? = null
): SpanStyle {
    val background = themeBackgroundColor.takeIf { it.isSpecified }
        ?: if (isDarkTheme) Color.Black else Color.White
    val text = themeTextColor.takeIf { it.isSpecified }
        ?: if (isDarkTheme) Color.White else Color.Black
    val linkColor = sharedNativeReaderLinkColorForTheme(isDarkTheme, background, text)
    val backgroundAlpha = if (background.sharedNativeSafeLuminance() < 0.45f) 0.24f else 0.16f
    return SpanStyle(
        color = linkColor,
        background = linkColor.copy(alpha = backgroundAlpha),
        textDecoration = existingDecoration.withSharedNativeUnderline()
    )
}

internal fun SpanStyle.withSharedNativeReaderLinkStyle(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): SpanStyle {
    val linkStyle = sharedNativeReaderLinkSpanStyle(
        isDarkTheme = isDarkTheme,
        themeBackgroundColor = themeBackgroundColor,
        themeTextColor = themeTextColor,
        existingDecoration = textDecoration
    )
    return copy(
        color = linkStyle.color,
        background = linkStyle.background,
        textDecoration = linkStyle.textDecoration
    )
}

internal fun sharedNativeReaderLinkColorForTheme(
    isDarkTheme: Boolean,
    background: Color,
    text: Color
): Color {
    val backgroundLuminance = background.sharedNativeSafeLuminance()
    val textLuminance = text.sharedNativeSafeLuminance()
    val candidates = if (isDarkTheme || backgroundLuminance < 0.45f) {
        listOf(
            Color(0xFF7DD3FC),
            Color(0xFF5EEAD4),
            Color(0xFFA5B4FC),
            Color(0xFFFDE68A),
            Color.White
        )
    } else {
        listOf(
            Color(0xFF005FCC),
            Color(0xFF006D75),
            Color(0xFF7A1E52),
            Color(0xFF4A148C),
            Color(0xFF111827)
        )
    }
    return candidates.firstOrNull { candidate ->
        candidate.sharedNativeContrastRatio(background) >= 4.5f &&
            abs(candidate.sharedNativeSafeLuminance() - textLuminance) >= 0.08f
    } ?: candidates.maxByOrNull { it.sharedNativeContrastRatio(background) }
        ?: if (isDarkTheme) Color(0xFF7DD3FC) else Color(0xFF005FCC)
}

internal fun TextDecoration?.withSharedNativeUnderline(): TextDecoration {
    val current = this ?: TextDecoration.None
    val decorations = mutableListOf<TextDecoration>()
    if (current.contains(TextDecoration.LineThrough)) decorations += TextDecoration.LineThrough
    decorations += TextDecoration.Underline
    return TextDecoration.combine(decorations)
}

internal fun Color.sharedNativeContrastRatio(other: Color): Float {
    val lighter = maxOf(sharedNativeSafeLuminance(), other.sharedNativeSafeLuminance())
    val darker = minOf(sharedNativeSafeLuminance(), other.sharedNativeSafeLuminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun Color.sharedNativeSafeLuminance(): Float {
    return if (isSpecified) luminance() else 0f
}

internal fun BlockStyle.sharedNativeFlexHorizontalArrangement(): Arrangement.Horizontal {
    return when (justifyContent) {
        "center" -> Arrangement.Center
        "flex-end" -> Arrangement.End
        "space-between" -> Arrangement.SpaceBetween
        "space-around" -> Arrangement.SpaceAround
        else -> Arrangement.Start
    }
}

internal fun BlockStyle.sharedNativeFlexVerticalArrangement(): Arrangement.Vertical {
    return when (justifyContent) {
        "center" -> Arrangement.Center
        "flex-end" -> Arrangement.Bottom
        "space-between" -> Arrangement.SpaceBetween
        "space-around" -> Arrangement.SpaceAround
        else -> Arrangement.Top
    }
}

internal fun BlockStyle.sharedNativeFlexVerticalAlignment(): Alignment.Vertical {
    return when (alignItems) {
        "center" -> Alignment.CenterVertically
        "flex-end" -> Alignment.Bottom
        else -> Alignment.Top
    }
}

internal fun BlockStyle.sharedNativeFlexHorizontalAlignment(): Alignment.Horizontal {
    return when (alignItems) {
        "center" -> Alignment.CenterHorizontally
        "flex-end" -> Alignment.End
        else -> Alignment.Start
    }
}

internal fun Color.sharedNativeThemeColor(
    isDarkTheme: Boolean,
    isBackground: Boolean,
    background: Color,
    foreground: Color
): Color {
    return CssParser.adaptColorForTheme(
        color = this,
        isDarkTheme = isDarkTheme,
        isBackground = isBackground,
        themeBackground = background,
        themeText = foreground
    )
}

internal fun CssStyle.toRenderedSpanStyle(parentFontSizeSp: Float, tag: String? = null): SpanStyle {
    val resolvedFontSize = (spanStyle.fontSize.takeIfSpecified() ?: fontSize.takeIfSpecified())
        ?.resolveFontSizeSp(parentFontSizeSp)
    return spanStyle.copy(
        fontSize = resolvedFontSize ?: spanStyle.fontSize,
        fontFeatureSettings = resolveSharedReaderFontFeatureSettings(
            existingSettings = spanStyle.fontFeatureSettings,
            fontVariantNumeric = fontVariantNumeric
        ),
        baselineShift = spanStyle.baselineShift ?: sharedNativeBaselineShift(tag, verticalAlign)
    )
}

internal fun sharedNativeBaselineShift(tag: String?, verticalAlign: String?): BaselineShift? {
    return when (tag?.lowercase() ?: verticalAlign?.lowercase()) {
        "sub", "subscript" -> BaselineShift.Subscript
        "sup", "super", "superscript" -> BaselineShift.Superscript
        else -> null
    }
}
