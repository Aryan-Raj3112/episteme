package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import org.jsoup.Jsoup

internal fun Page.applyReaderThemeForDisplay(
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color
): Page = applyReaderThemeForDisplayPolicy(
    isDarkTheme = isDarkTheme,
    themeBackgroundColor = themeBackgroundColor,
    themeTextColor = themeTextColor,
    transformNonMathSvgText = String::applyReaderThemeToSvgText
)

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
    val red = (red * 255).toInt()
    val green = (green * 255).toInt()
    val blue = (blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}
