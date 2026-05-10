package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.jsoup.Jsoup

internal fun Page.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): Page {
    return copy(
        content = content.map {
            it.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor)
        }
    )
}

private fun ContentBlock.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): ContentBlock {
    val themedStyle = style.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor)
    return when (this) {
        is ParagraphBlock -> copy(
            content = content.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
            style = themedStyle
        )
        is HeaderBlock -> copy(
            content = content.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
            style = themedStyle
        )
        is QuoteBlock -> copy(
            content = content.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
            style = themedStyle
        )
        is ListItemBlock -> copy(
            content = content.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
            style = themedStyle
        )
        is ImageBlock -> copy(style = themedStyle)
        is SpacerBlock -> copy(style = themedStyle)
        is MathBlock -> copy(
            svgContent = if (isFromMathJax) {
                svgContent
            } else {
                svgContent?.applyReaderThemeToSvgText(themeTextColor)
            },
            style = themedStyle
        )
        is WrappingContentBlock -> copy(
            floatedImage = floatedImage.applyReaderThemeForDisplay(
                isDarkTheme,
                themeBackgroundColor,
                themeTextColor
            ) as ImageBlock,
            paragraphsToWrap = paragraphsToWrap.map {
                it.applyReaderThemeForDisplay(
                    isDarkTheme,
                    themeBackgroundColor,
                    themeTextColor
                ) as ParagraphBlock
            },
            style = themedStyle
        )
        is TableBlock -> copy(
            rows = rows.map { row ->
                row.map { cell ->
                    cell.copy(
                        content = cell.content.map {
                            it.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor)
                        },
                        style = cell.style.applyReaderThemeForDisplay(
                            isDarkTheme,
                            themeBackgroundColor,
                            themeTextColor
                        )
                    )
                }
            },
            style = themedStyle
        )
        is FlexContainerBlock -> copy(
            children = children.map {
                it.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor)
            },
            style = themedStyle
        )
    }
}

private fun AnnotatedString.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(this@applyReaderThemeForDisplay.text)
        this@applyReaderThemeForDisplay.spanStyles.forEach { range ->
            addStyle(
                range.item.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
                range.start,
                range.end
            )
        }
        this@applyReaderThemeForDisplay.paragraphStyles.forEach { range ->
            addStyle(range.item, range.start, range.end)
        }
        this@applyReaderThemeForDisplay.getStringAnnotations(0, this@applyReaderThemeForDisplay.length).forEach { range ->
            val item = if (range.tag == "CustomUnderline") {
                range.item.applyReaderThemeToUnderlineAnnotation(isDarkTheme, themeBackgroundColor, themeTextColor)
            } else {
                range.item
            }
            addStringAnnotation(range.tag, item, range.start, range.end)
        }
        this@applyReaderThemeForDisplay.getStringAnnotations("URL", 0, this@applyReaderThemeForDisplay.length).forEach { range ->
            addStyle(
                readerLinkSpanStyle(
                    isDarkTheme = isDarkTheme,
                    themeBackgroundColor = themeBackgroundColor,
                    themeTextColor = themeTextColor
                ),
                range.start,
                range.end
            )
        }
    }
}

private fun CssStyle.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): CssStyle {
    val emphasis = textEmphasis
    return copy(
        spanStyle = spanStyle.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
        blockStyle = blockStyle.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
        textDecorationColor = textDecorationColor.applyReaderThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = false,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor
        ),
        textEmphasis = emphasis?.copy(
            color = emphasis.color.applyReaderThemeColor(
                isDarkTheme = isDarkTheme,
                isBackground = false,
                themeBackgroundColor = themeBackgroundColor,
                themeTextColor = themeTextColor
            )
        )
    )
}

private fun SpanStyle.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): SpanStyle {
    return copy(
        color = color.applyReaderThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = false,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor
        ),
        background = background.applyReaderThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = true,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor
        )
    )
}

private fun BlockStyle.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): BlockStyle {
    return copy(
        backgroundColor = backgroundColor.applyReaderThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = true,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor
        ),
        borderTop = borderTop?.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
        borderRight = borderRight?.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
        borderBottom = borderBottom?.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor),
        borderLeft = borderLeft?.applyReaderThemeForDisplay(isDarkTheme, themeBackgroundColor, themeTextColor)
    )
}

private fun BorderStyle.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): BorderStyle {
    return copy(
        color = color.applyReaderThemeColor(
            isDarkTheme = isDarkTheme,
            isBackground = false,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor
        )
    )
}

private fun Color.applyReaderThemeColor(
    isDarkTheme: Boolean,
    isBackground: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): Color {
    if (!isSpecified) return this
    return CssParser.adaptColorForTheme(
        color = this,
        isDarkTheme = isDarkTheme,
        isBackground = isBackground,
        themeBackground = themeBackgroundColor,
        themeText = themeTextColor
    )
}

private fun String.applyReaderThemeToUnderlineAnnotation(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): String {
    val parts = split('|').toMutableList()
    val colorPart = parts.getOrNull(1) ?: return this
    if (colorPart == "Unspecified") return this

    val color = colorPart.toULongOrNull()?.let { Color(it) } ?: return this
    parts[1] = color.applyReaderThemeColor(
        isDarkTheme = isDarkTheme,
        isBackground = false,
        themeBackgroundColor = themeBackgroundColor,
        themeTextColor = themeTextColor
    ).value.toString()
    return parts.joinToString("|")
}

private fun String.applyReaderThemeToSvgText(themeTextColor: Color): String {
    if (!themeTextColor.isSpecified || isBlank()) return this
    return try {
        val textColorHex = themeTextColor.toCssHexString()
        val svgDocument = Jsoup.parseBodyFragment(this)
        val svgElement = svgDocument.body().children().firstOrNull() ?: return this

        svgElement.select("text").forEach { textElement ->
            val existingStyle = textElement.attr("style")
            val styleWithoutFill = existingStyle.replace(Regex("""\bfill\s*:\s*[^;]+;?"""), "")
            val newStyle = "fill:$textColorHex; $styleWithoutFill".trim()
            textElement.attr("style", newStyle)
            textElement.removeAttr("fill")
        }
        svgElement.outerHtml()
    } catch (_: Exception) {
        this
    }
}

private fun Color.toCssHexString(): String {
    val red = (this.red * 255).toInt()
    val green = (this.green * 255).toInt()
    val blue = (this.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}
