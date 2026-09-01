package com.aryan.reader.paginatedreader

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.net.URLDecoder

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ContentStyler(
    baseTextStyle: TextStyle,
    fontFamilyMap: Map<String, FontFamily>,
    density: Density,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    chapterAbsPath: String,
    extractionBasePath: String,
    userTextAlign: TextAlign?,
    paragraphGapMultiplier: Float,
    userLineHeightMultiplier: Float = 1f,
    adaptThemeColors: Boolean = true
) {
    private val delegate = SharedContentStyler(
        baseTextStyle = baseTextStyle,
        fontFamilyMap = fontFamilyMap,
        density = density,
        isDarkTheme = isDarkTheme,
        themeBackgroundColor = themeBackgroundColor,
        themeTextColor = themeTextColor,
        userTextAlign = userTextAlign,
        paragraphGapMultiplier = paragraphGapMultiplier,
        userLineHeightMultiplier = userLineHeightMultiplier,
        adaptThemeColors = adaptThemeColors,
        applyThemeToSvg = { svg -> applyThemeToSvg(svg, baseTextStyle.color) },
        embedImagesInSvg = { svg -> embedImagesInSvg(svg, chapterAbsPath, extractionBasePath) },
        onUnsupportedBlock = { block ->
            Timber.w("Unsupported or misplaced SemanticBlock type encountered: ${block::class.java.simpleName}")
        },
        onStyledText = ::logStyledText
    )

    fun style(semanticBlocks: List<SemanticBlock>): List<ContentBlock> = delegate.style(semanticBlocks)
}

private fun embedImagesInSvg(svgContent: String, chapterAbsPath: String, extractionBasePath: String): String {
    return try {
        val svgDocument = Jsoup.parseBodyFragment(svgContent)
        val svgElement = svgDocument.body().children().firstOrNull() ?: return svgContent
        svgElement.select("image").forEach { imageElement ->
            val href = imageElement.attr("href").ifBlank { imageElement.attr("xlink:href") }
            if (href.isNotBlank() && !href.startsWith("data:")) {
                resolveImagePath(href, chapterAbsPath, extractionBasePath)?.let { imageFile ->
                    try {
                        val mimeType = when (imageFile.extension.lowercase()) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            "webp" -> "image/webp"
                            else -> "application/octet-stream"
                        }
                        val base64 = android.util.Base64.encodeToString(imageFile.readBytes(), android.util.Base64.NO_WRAP)
                        imageElement.attr("xlink:href", "data:$mimeType;base64,$base64")
                        imageElement.removeAttr("href")
                    } catch (error: Exception) {
                        Timber.e(error, "Failed to read and encode image file '$href' to Base64.")
                    }
                }
            }
        }
        svgElement.outerHtml()
    } catch (error: Exception) {
        Timber.e(error, "Error while embedding images in SVG content.")
        svgContent
    }
}

private fun resolveImagePath(src: String, chapterAbsPath: String, extractionBasePath: String): File? {
    if (src.isBlank()) return null
    val decodedSrc = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }
    val parentPath = File(chapterAbsPath).parent ?: ""
    val fromRelativeFile = File(extractionBasePath, File(parentPath, decodedSrc).path)
    if (fromRelativeFile.exists()) return fromRelativeFile
    val fromRootFile = File(extractionBasePath, decodedSrc)
    if (fromRootFile.exists()) return fromRootFile
    Timber.w("Image not found for SVG embedding. Tried: ${fromRelativeFile.absolutePath} and ${fromRootFile.absolutePath}")
    return null
}

private fun applyThemeToSvg(svgContent: String, textColor: Color): String {
    if (svgContent.isBlank()) return svgContent
    return try {
        val svgDocument = Jsoup.parseBodyFragment(svgContent)
        val svgElement = svgDocument.body().children().firstOrNull() ?: return svgContent
        val textColorHex = textColor.toCssHexString()
        svgElement.select("text").forEach { textElement ->
            val existingStyle = textElement.attr("style")
            val styleWithoutFill = existingStyle.replace(Regex("""\bfill\s*:\s*[^;]+;?"""), "")
            textElement.attr("style", "fill:$textColorHex; $styleWithoutFill".trim())
            textElement.removeAttr("fill")
        }
        svgElement.outerHtml()
    } catch (error: Exception) {
        Timber.e(error, "Failed to apply dark theme to SVG content.")
        svgContent
    }
}

private fun Color.toCssHexString(): String {
    val red = (red * 255).toInt()
    val green = (green * 255).toInt()
    val blue = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", red, green, blue)
}

private fun logStyledText(block: SemanticTextBlock, styled: AnnotatedString) {
    if (READER_LINK_DIAGNOSTICS_ENABLED && block.spans.any { !it.linkHref.isNullOrBlank() }) {
        Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
            "style_text_block type=${block::class.simpleName ?: "Text"} " +
                "block=${block.blockIndex} cfi=${block.cfi} " +
                "rawLinkSpans=${block.spans.count { !it.linkHref.isNullOrBlank() }} " +
                styled.readerAnnotatedLinkDiagSummary()
        )
    }
}
