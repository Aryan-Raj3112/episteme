package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle as ComposeFontStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import kotlin.math.ceil
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWeight
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.FontMgrWithFallback
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.DecorationLineStyle
import org.jetbrains.skia.paragraph.DecorationStyle
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProviderWithFallback

private const val IOS_PDF_RICH_FONT_PATH_TAG = "pdf-rich-font-path"

/**
 * Keeps imported PDF fonts registered for the lifetime of one export pass.
 *
 * The Compose surface loads the same files through its platform font loader. PDF
 * export uses Skia paragraphs directly, so it needs its own provider; otherwise
 * an imported family would be visible while editing but silently fall back in a
 * saved copy.
 */
internal class IosPdfTextFontRegistry(
    val fontCollection: FontCollection,
    private val aliasesByPath: Map<String, String>,
    private val aliasesByName: Map<String, String>,
) {
    fun familyName(path: String?, name: String?): String? {
        path?.let { aliasesByPath[it] }?.let { return it }
        name?.let { aliasesByName[it] }?.let { return it }
        return name?.takeIf(String::isNotBlank)
    }
}

internal data class IosPdfRasterOverlay(
    val pageIndex: Int,
    val bounds: PdfPageBounds,
    val width: Int,
    val height: Int,
    val bgraPixels: ByteArray,
)

internal data class IosPdfRasterizationResult(
    val overlays: List<IosPdfRasterOverlay>,
    val complete: Boolean,
)

internal fun buildIosPdfTextRasterOverlays(
    pageIndex: Int,
    pageWidth: Float,
    pageHeight: Float,
    annotations: List<SharedPdfAnnotation>,
    richTextLayouts: List<SharedPdfRichPageLayout>,
    fontRegistry: IosPdfTextFontRegistry = buildIosPdfTextFontRegistry(annotations, richTextLayouts),
): IosPdfRasterizationResult {
    val overlays = mutableListOf<IosPdfRasterOverlay>()
    var complete = true
    annotations.filter { it.pageIndex == pageIndex && it.kind == PdfAnnotationKind.TEXT && it.text.isNotBlank() }
        .forEach { annotation ->
            val overlay = annotation.toIosPdfTextBoxOverlay(pageWidth, pageHeight, fontRegistry)
            if (overlay == null) complete = false else overlays += overlay
        }
    richTextLayouts.filter { it.pageIndex == pageIndex && it.visibleText.any { char -> !char.isWhitespace() } }
        .forEach { layout ->
            val overlay = layout.toIosPdfRichTextOverlay(pageWidth, pageHeight, fontRegistry)
            if (overlay == null) complete = false else overlays += overlay
        }
    return IosPdfRasterizationResult(overlays, complete)
}

private fun SharedPdfAnnotation.toIosPdfTextBoxOverlay(
    pageWidth: Float,
    pageHeight: Float,
    fontRegistry: IosPdfTextFontRegistry,
): IosPdfRasterOverlay? {
    val safeBounds = bounds?.normalizedIosPdfBounds() ?: return null
    val exportHeight = iosPdfExportHeight(pageHeight)
    val exportWidth = exportHeight * (pageWidth / pageHeight.coerceAtLeast(1f))
    val width = ceil((safeBounds.right - safeBounds.left) * exportWidth).toInt().coerceAtLeast(1)
    val height = ceil((safeBounds.bottom - safeBounds.top) * exportHeight).toInt().coerceAtLeast(1)
    val padding = (8f * exportHeight / 1414f).coerceAtMost(minOf(width, height) / 2f)
    val style = SpanStyle(
        color = Color(colorArgb),
        background = Color(backgroundArgb),
        fontSize = (sharedPdfTextPageRelativeFontSize() * exportHeight).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
        fontWeight = if (isBold) ComposeFontWeight.Bold else ComposeFontWeight.Normal,
        fontStyle = if (isItalic) ComposeFontStyle.Italic else ComposeFontStyle.Normal,
        textDecoration = when {
            isUnderline && isStrikeThrough -> TextDecoration.Underline + TextDecoration.LineThrough
            isUnderline -> TextDecoration.Underline
            isStrikeThrough -> TextDecoration.LineThrough
            else -> TextDecoration.None
        },
        fontFamily = null,
    )
    val pixels = renderIosPdfParagraph(
        text = AnnotatedString(text, listOf(AnnotatedString.Range(style, 0, text.length))),
        width = width,
        height = height,
        padding = padding,
        fallbackFontName = fontRegistry.familyName(fontPath, fontName),
        fontRegistry = fontRegistry,
    ) ?: return null
    return IosPdfRasterOverlay(pageIndex, safeBounds, width, height, pixels)
}

private fun SharedPdfRichPageLayout.toIosPdfRichTextOverlay(
    pageWidth: Float,
    pageHeight: Float,
    fontRegistry: IosPdfTextFontRegistry,
): IosPdfRasterOverlay? {
    val text = visibleText.withoutTrailingIosPdfPageBreak()
    if (text.text.isBlank()) return null
    val bounds = PdfPageBounds(0.1f, 0.08f, 0.9f, 0.92f)
    val exportHeight = iosPdfExportHeight(pageHeight)
    val exportWidth = exportHeight * (pageWidth / pageHeight.coerceAtLeast(1f))
    val width = ceil((bounds.right - bounds.left) * exportWidth).toInt().coerceAtLeast(1)
    val height = ceil((bounds.bottom - bounds.top) * exportHeight).toInt().coerceAtLeast(1)
    val pixels = renderIosPdfParagraph(
        text,
        width,
        height,
        padding = 0f,
        fontRegistry = fontRegistry,
    ) ?: return null
    return IosPdfRasterOverlay(pageIndex, bounds, width, height, pixels)
}

private fun renderIosPdfParagraph(
    text: AnnotatedString,
    width: Int,
    height: Int,
    padding: Float,
    fallbackFontName: String? = null,
    fontRegistry: IosPdfTextFontRegistry,
): ByteArray? = runCatching {
    val paragraphStyle = ParagraphStyle().apply {
        this.textStyle = TextStyle().apply {
            color = 0xFF000000.toInt()
            fontSize = 16f
            fontFamilies = arrayOf(fallbackFontName?.takeIf(String::isNotBlank) ?: "Arial")
        }
    }
    val builder = ParagraphBuilder(paragraphStyle, fontRegistry.fontCollection)
    val boundaries = buildSet {
        add(0)
        add(text.length)
        text.spanStyles.forEach { range ->
            add(range.start.coerceIn(0, text.length))
            add(range.end.coerceIn(0, text.length))
        }
    }.sorted()
    boundaries.zipWithNext().forEach { (start, end) ->
        if (start >= end) return@forEach
        val merged = text.spanStyles
            .filter { it.start < end && it.end > start }
            .fold(SpanStyle()) { style, range -> style.merge(range.item) }
        val spanFontPath = text.getStringAnnotations(
            tag = IOS_PDF_RICH_FONT_PATH_TAG,
            start = start,
            end = end,
        ).firstOrNull()?.item
        builder.pushStyle(
            merged.toIosSkiaTextStyle(
                fallbackFontName = fontRegistry.familyName(spanFontPath, null) ?: fallbackFontName,
            )
        )
        builder.addText(text.text.substring(start, end))
        builder.popStyle()
    }
    val paragraph = builder.build().layout((width - padding * 2f).coerceAtLeast(1f))
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.clear(0x00000000)
    paragraph.paint(surface.canvas, padding, padding)
    val image = surface.makeImageSnapshot()
    val bitmap = Bitmap.makeFromImage(image)
    bitmap.readPixels(
        ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
        width * 4,
    )
}.getOrNull()

internal fun buildIosPdfTextFontRegistry(
    annotations: List<SharedPdfAnnotation>,
    richTextLayouts: List<SharedPdfRichPageLayout> = emptyList(),
): IosPdfTextFontRegistry {
    val provider = TypefaceFontProviderWithFallback()
    val aliasesByPath = mutableMapOf<String, String>()
    val aliasesByName = mutableMapOf<String, String>()
    val richFontPaths = richTextLayouts.asSequence()
        .map { layout ->
            layout.visibleText.getStringAnnotations(
                tag = IOS_PDF_RICH_FONT_PATH_TAG,
                start = 0,
                end = layout.visibleText.length,
            ).map { it.item }
        }
        .flatten()
    annotations.asSequence()
        .mapNotNull { it.fontPath?.takeIf(String::isNotBlank) }
        .plus(richFontPaths)
        .distinct()
        .forEachIndexed { index, path ->
            val typeface = runCatching { FontMgr.default.makeFromFile(path) }.getOrNull()
                ?: return@forEachIndexed
            val alias = "ReaderPdfImportedFont$index"
            provider.registerTypeface(typeface, alias)
            aliasesByPath[path] = alias
            annotations.asSequence()
                .filter { it.fontPath == path }
                .mapNotNull { it.fontName?.takeIf(String::isNotBlank) }
                .forEach { aliasesByName[it] = alias }
        }
    val fontManager = FontMgrWithFallback(provider)
    val fontCollection = FontCollection()
        .setDefaultFontManager(fontManager)
        .setAssetFontManager(provider)
    return IosPdfTextFontRegistry(fontCollection, aliasesByPath, aliasesByName)
}

private fun SpanStyle.toIosSkiaTextStyle(fallbackFontName: String?): TextStyle {
    val foreground = color.takeIf { it.isSpecified }?.toArgb() ?: 0xFF000000.toInt()
    val backgroundArgb = background.takeIf { it.isSpecified && it.alpha > 0f }?.toArgb()
    val decoration = textDecoration ?: TextDecoration.None
    return TextStyle().apply {
        color = foreground
        backgroundArgb?.let { background = Paint().apply { color = it } }
        fontSize = this@toIosSkiaTextStyle.fontSize.takeIf { it.isSpecified }?.value?.coerceAtLeast(1f) ?: 16f
        fontFamilies = arrayOf(fallbackFontName?.takeIf(String::isNotBlank) ?: "Arial")
        fontStyle = FontStyle(
            if ((fontWeight ?: ComposeFontWeight.Normal).weight >= ComposeFontWeight.Bold.weight) FontWeight.BOLD else FontWeight.NORMAL,
            FontWidth.NORMAL,
            if (this@toIosSkiaTextStyle.fontStyle == ComposeFontStyle.Italic) FontSlant.ITALIC else FontSlant.UPRIGHT,
        )
        decorationStyle = DecorationStyle(
            decoration.contains(TextDecoration.Underline),
            false,
            decoration.contains(TextDecoration.LineThrough),
            false,
            foreground,
            DecorationLineStyle.SOLID,
            1f,
        )
    }
}

private fun PdfPageBounds.normalizedIosPdfBounds(): PdfPageBounds? {
    val left = minOf(left, right).coerceIn(0f, 1f)
    val right = maxOf(this.left, right).coerceIn(0f, 1f)
    val top = minOf(top, bottom).coerceIn(0f, 1f)
    val bottom = maxOf(this.top, bottom).coerceIn(0f, 1f)
    return PdfPageBounds(left, top, right, bottom).takeIf { right > left && bottom > top }
}

private fun AnnotatedString.withoutTrailingIosPdfPageBreak(): AnnotatedString {
    if (!text.endsWith(SHARED_PDF_PAGE_BREAK_CHAR)) return this
    return subSequence(0, text.length - 1)
}

private fun iosPdfExportHeight(pageHeight: Float): Float = (pageHeight * 3f).coerceIn(1200f, 3600f)
