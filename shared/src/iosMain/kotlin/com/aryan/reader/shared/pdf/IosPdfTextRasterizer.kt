package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle as ComposeFontStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.shared.generated.resources.Res
import kotlin.math.ceil
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWeight
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.FontMgrWithFallback
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.Alignment as SkiaAlignment
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

internal suspend fun buildIosPdfTextRasterOverlays(
    pageIndex: Int,
    pageWidth: Float,
    pageHeight: Float,
    annotations: List<SharedPdfAnnotation>,
    richTextLayouts: List<SharedPdfRichPageLayout>,
    fontRegistry: IosPdfTextFontRegistry,
    richTextExportScale: Float = 1f,
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
            val overlay = layout.toIosPdfRichTextOverlay(pageWidth, pageHeight, fontRegistry, richTextExportScale)
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
    // Android benchmark (sanitizeRasterText): page breaks become newlines, zero-width chars are
    // dropped, carriage returns become spaces so the rasterized copy matches the editor.
    val safeText = text.sanitizeIosPdfRasterText()
    if (pageIndex < 0 || safeText.isBlank()) return null
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
        text = AnnotatedString(safeText, listOf(AnnotatedString.Range(style, 0, safeText.length))),
        width = width,
        height = height,
        padding = padding,
        fallbackFontName = fontRegistry.familyName(fontPath, fontName),
        fontRegistry = fontRegistry,
    ) ?: return null
    // Android benchmark (Bitmap.toRasterOverlay): crop transparent margins so the embedded image
    // is tight to the glyphs instead of the full text-box bitmap.
    return cropIosPdfRasterOverlay(pageIndex, safeBounds, width, height, pixels)
}

private fun SharedPdfRichPageLayout.toIosPdfRichTextOverlay(
    pageWidth: Float,
    pageHeight: Float,
    fontRegistry: IosPdfTextFontRegistry,
    richTextExportScale: Float,
): IosPdfRasterOverlay? {
    val text = visibleText.withoutTrailingIosPdfPageBreak().sanitizeIosPdfRasterTextPreservingLength()
    if (text.text.isBlank()) return null
    val bounds = PdfPageBounds(0.1f, 0.08f, 0.9f, 0.92f)
    // Android/Desktop size the rich-text bitmap from the same pageHeightPx that baked the
    // span font sizes into the AnnotatedString. Mixing exportHeight here with layout-based
    // font sizes scales glyphs against the wrong canvas (tiny/huge text vs Android).
    val layoutHeight = pageHeightPx.takeIf { it > 0f } ?: iosPdfExportHeight(pageHeight)
    val layoutWidth = layoutHeight * (pageWidth / pageHeight.coerceAtLeast(1f))
    val width = ceil((bounds.right - bounds.left) * layoutWidth).toInt().coerceAtLeast(1)
    val height = ceil((bounds.bottom - bounds.top) * layoutHeight).toInt().coerceAtLeast(1)
    val pixels = renderIosPdfParagraph(
        text,
        width,
        height,
        padding = 0f,
        fontRegistry = fontRegistry,
        // Android benchmark (applySpanStyle): baked .sp sizes go through sp→px
        // (density × fontScale). Text boxes skip this and stay page-relative.
        fontScale = richTextExportScale,
    ) ?: return null
    return cropIosPdfRasterOverlay(pageIndex, bounds, width, height, pixels)
}

internal fun cropIosPdfRasterOverlay(
    pageIndex: Int,
    bounds: PdfPageBounds,
    width: Int,
    height: Int,
    bgraPixels: ByteArray,
): IosPdfRasterOverlay? {
    if (width <= 0 || height <= 0 || bgraPixels.size < width * height * 4) return null
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        val rowOffset = y * width * 4
        for (x in 0 until width) {
            // BGRA_8888 premul: alpha is the 4th byte; transparent texels are skipped like Android.
            if (bgraPixels[rowOffset + x * 4 + 3] != 0.toByte()) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < minX || maxY < minY) return null
    val cropWidth = maxX - minX + 1
    val cropHeight = maxY - minY + 1
    val cropped = ByteArray(cropWidth * cropHeight * 4)
    for (row in 0 until cropHeight) {
        val srcOffset = ((minY + row) * width + minX) * 4
        val dstOffset = row * cropWidth * 4
        bgraPixels.copyInto(cropped, dstOffset, srcOffset, srcOffset + cropWidth * 4)
    }
    val boundsWidth = bounds.right - bounds.left
    val boundsHeight = bounds.bottom - bounds.top
    return IosPdfRasterOverlay(
        pageIndex = pageIndex,
        bounds = PdfPageBounds(
            left = bounds.left + boundsWidth * (minX.toFloat() / width),
            top = bounds.top + boundsHeight * (minY.toFloat() / height),
            right = bounds.left + boundsWidth * ((maxX + 1).toFloat() / width),
            bottom = bounds.top + boundsHeight * ((maxY + 1).toFloat() / height),
        ),
        width = cropWidth,
        height = cropHeight,
        bgraPixels = cropped,
    )
}

private fun String.sanitizeIosPdfRasterText(): String =
    replace(SHARED_PDF_PAGE_BREAK_CHAR, '\n')
        .replace("\u200B", "")
        .replace('\r', ' ')

private fun AnnotatedString.sanitizeIosPdfRasterTextPreservingLength(): AnnotatedString {
    if (text.indexOf(SHARED_PDF_PAGE_BREAK_CHAR) < 0 && text.indexOf('\r') < 0) return this
    val sanitized = text.replace(SHARED_PDF_PAGE_BREAK_CHAR, '\n').replace('\r', ' ')
    if (sanitized.length != text.length) return this
    // Length-preserving replacements keep every span offset valid; rebuild via Builder so the
    // rich-font path annotations (IOS_PDF_RICH_FONT_PATH_TAG) survive for the font registry.
    return AnnotatedString.Builder(sanitized).apply {
        spanStyles.forEach { range -> addStyle(range.item, range.start, range.end) }
        paragraphStyles.forEach { range -> addStyle(range.item, range.start, range.end) }
        getStringAnnotations(
            tag = IOS_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = length,
        ).forEach { annotation ->
            addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
        }
    }.toAnnotatedString()
}

private fun renderIosPdfParagraph(
    text: AnnotatedString,
    width: Int,
    height: Int,
    padding: Float,
    fallbackFontName: String? = null,
    fontRegistry: IosPdfTextFontRegistry,
    fontScale: Float = 1f,
): ByteArray? = runCatching {
    // One Skia paragraph per '\n'-paragraph: alignment is builder-wide in
    // Skia, so segments stack vertically with their own alignment (list
    // markers are plain text and flow through). A trailing newline adds no
    // line, so an empty trailing segment is dropped.
    val segments = mutableListOf<AnnotatedString>()
    var segmentStart = 0
    for (i in text.text.indices) {
        if (text.text[i] == '\n') {
            segments += text.subSequence(segmentStart, i)
            segmentStart = i + 1
        }
    }
    val trailing = text.subSequence(segmentStart, text.length)
    if (trailing.text.isNotEmpty()) segments += trailing
    if (segments.isEmpty()) return@runCatching null
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.clear(0x00000000)
    var y = padding
    for (segment in segments) {
        if (y >= height) break
        val alignment = when (
            segment.paragraphStyles.firstOrNull()?.item?.textAlign
        ) {
            ComposeTextAlign.Center -> SkiaAlignment.CENTER
            ComposeTextAlign.Right, ComposeTextAlign.End -> SkiaAlignment.RIGHT
            else -> SkiaAlignment.LEFT
        }
        val paragraphStyle = ParagraphStyle().apply {
            this.alignment = alignment
            this.textStyle = TextStyle().apply {
                color = 0xFF000000.toInt()
                fontSize = 16f * fontScale
                fontFamilies = arrayOf(fallbackFontName?.takeIf(String::isNotBlank) ?: "Arial")
            }
        }
        val builder = ParagraphBuilder(paragraphStyle, fontRegistry.fontCollection)
        val boundaries = buildSet {
            add(0)
            add(segment.length)
            segment.spanStyles.forEach { range ->
                add(range.start.coerceIn(0, segment.length))
                add(range.end.coerceIn(0, segment.length))
            }
        }.sorted()
        boundaries.zipWithNext().forEach { (start, end) ->
            if (start >= end) return@forEach
            val merged = segment.spanStyles
                .filter { it.start < end && it.end > start }
                .fold(SpanStyle()) { style, range -> style.merge(range.item) }
            val spanFontPath = segment.getStringAnnotations(
                tag = IOS_PDF_RICH_FONT_PATH_TAG,
                start = start,
                end = end,
            ).firstOrNull()?.item
            builder.pushStyle(
                merged.toIosSkiaTextStyle(
                    fallbackFontName = fontRegistry.familyName(spanFontPath, null) ?: fallbackFontName,
                    fontScale = fontScale,
                )
            )
            builder.addText(segment.text.substring(start, end))
            builder.popStyle()
        }
        if (segment.text.isEmpty()) builder.addText(" ")
        val paragraph = builder.build().layout((width - padding * 2f).coerceAtLeast(1f))
        paragraph.paint(surface.canvas, padding, y)
        y += paragraph.height
    }
    val image = surface.makeImageSnapshot()
    val bitmap = Bitmap.makeFromImage(image)
    // FPDFBitmap_BGRA expects unpremultiplied components (fpdfview.h: "Pixel components
    // are independent of alpha"). Reading PREMUL and feeding BGRA corrupts antialiased
    // glyph edges into dark/garbled pixels on export.
    bitmap.readPixels(
        ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL),
        width * 4,
    )
}.getOrNull()

internal suspend fun buildIosPdfTextFontRegistry(
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
            val typeface = loadIosPdfExportTypeface(path) ?: run {
                // Preset family still resolves to the same serif/sans/mono class as
                // on-screen rendering when the TTF itself cannot be decoded.
                sharedPdfFallbackExportFontFamily(path)?.let { family ->
                    aliasesByPath[path] = family
                    annotations.asSequence()
                        .filter { it.fontPath == path }
                        .mapNotNull { it.fontName?.takeIf(String::isNotBlank) }
                        .forEach { aliasesByName[it] = family }
                }
                return@forEachIndexed
            }
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

/**
 * Loads a font path for export. Preset `asset:fonts/` paths live in shared compose
 * resources (Android still uses TypeFace.createFromAsset for the same basenames).
 * Absolute filesystem paths (imported user fonts) go through Skia's file loader.
 */
private suspend fun loadIosPdfExportTypeface(path: String): Typeface? {
    val bundledPath = sharedPdfBundledFontResourcePath(path)
    if (bundledPath != null) {
        val bytes = runCatching { Res.readBytes(bundledPath) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val data = Data.makeFromBytes(bytes)
        return try {
            FontMgr.default.makeFromData(data)
        } finally {
            data.close()
        }
    }
    if (path.startsWith("asset:")) return null
    return runCatching { FontMgr.default.makeFromFile(path) }.getOrNull()
}

private fun SpanStyle.toIosSkiaTextStyle(fallbackFontName: String?, fontScale: Float = 1f): TextStyle {
    val foreground = color.takeIf { it.isSpecified }?.toArgb() ?: 0xFF000000.toInt()
    val backgroundArgb = background.takeIf { it.isSpecified && it.alpha > 0f }?.toArgb()
    val decoration = textDecoration ?: TextDecoration.None
    val baseFontSize = this@toIosSkiaTextStyle.fontSize.takeIf { it.isSpecified }?.value ?: 16f
    return TextStyle().apply {
        color = foreground
        backgroundArgb?.let { background = Paint().apply { color = it } }
        fontSize = (baseFontSize * fontScale).coerceAtLeast(1f)
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
