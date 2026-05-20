package com.aryan.reader.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.shared.SaveMode
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.SHARED_PDF_PAGE_BREAK_CHAR
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfAnnotationExportMapper
import com.aryan.reader.shared.pdf.SharedPdfRichPageLayout
import com.aryan.reader.shared.pdf.sharedPdfTextPageRelativeFontSize
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.awt.print.Book
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import java.text.AttributedString
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

private const val DesktopPdfTextBoxPaddingPx = 8f
private const val DesktopPdfTextRasterPointScale = 3f
private const val DesktopPdfTextRasterMinPageHeightPx = 1200f
private const val DesktopPdfTextRasterMaxPageHeightPx = 3600f
private const val DesktopPdfRichTextMarginX = 0.1f
private const val DesktopPdfRichTextMarginY = 0.08f

internal data class DesktopPdfFileActionNotice(
    val title: String,
    val message: String,
    val isError: Boolean = false
)

internal data class DesktopPdfRasterOverlay(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

internal fun desktopSuggestedPdfFilename(
    originalName: String?,
    isAnnotated: Boolean,
    shortId: String = Random.nextInt(1000, 9999).toString()
): String {
    val base = originalName
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?: "Document"
    val safeBase = base.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        .take(50)
        .ifBlank { "Document" }
    val suffix = if (isAnnotated) "_annotated" else ""
    return "${safeBase}${suffix}_$shortId.pdf"
}

internal fun hasExportableDesktopPdfAnnotations(
    annotations: List<SharedPdfAnnotation>,
    richTextPageLayouts: List<SharedPdfRichPageLayout>
): Boolean {
    return SharedPdfAnnotationExportMapper.build(annotations).hasPdfAnnotations ||
        annotations.any { annotation ->
            if (annotation.kind != PdfAnnotationKind.HIGHLIGHT) return@any false
            val startIndex = annotation.rangeStartIndex ?: return@any false
            val endIndex = annotation.rangeEndIndex ?: return@any false
            endIndex >= startIndex
        } ||
        annotations.any { annotation ->
            annotation.kind == PdfAnnotationKind.TEXT &&
                annotation.bounds != null &&
                annotation.text.isNotBlank()
        } ||
        richTextPageLayouts.any { layout ->
            layout.visibleText.text
                .replace(SHARED_PDF_PAGE_BREAK_CHAR.toString(), "")
                .isNotBlank()
        }
}

internal fun shouldShowDesktopPdfAnnotationExportChoice(
    sidecarsReady: Boolean,
    annotations: List<SharedPdfAnnotation>,
    richTextPageLayouts: List<SharedPdfRichPageLayout>
): Boolean {
    return !sidecarsReady || hasExportableDesktopPdfAnnotations(annotations, richTextPageLayouts)
}

internal fun chooseSavePdfFile(defaultFileName: String): File? {
    val dialog = FileDialog(null as Frame?, "Save PDF", FileDialog.SAVE).apply {
        file = defaultFileName
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val fileName = dialog.file ?: return null
    val selected = File(directory, fileName)
    return if (selected.extension.equals("pdf", ignoreCase = true)) {
        selected
    } else {
        File(selected.parentFile, "${selected.name}.pdf")
    }
}

internal fun saveDesktopPdfCopy(
    document: DesktopPdfDocument,
    target: File,
    mode: SaveMode,
    annotations: List<SharedPdfAnnotation> = emptyList(),
    richTextPageLayouts: List<SharedPdfRichPageLayout> = emptyList()
) {
    val source = File(document.path)
    require(source.isFile) { "The original PDF is not available as a local file." }
    require(document.formatLabel == "PDF") { "Only PDF files can be saved as PDF copies." }
    target.parentFile?.mkdirs()

    when (mode) {
        SaveMode.ORIGINAL -> {
            if (source.canonicalFile == target.canonicalFile) return
            source.copyTo(target, overwrite = true)
        }
        SaveMode.ANNOTATED -> {
            require(source.canonicalFile != target.canonicalFile) {
                "Choose a different file name for an annotated copy."
            }
            DesktopPdfium.exportAnnotatedPdf(
                document = document,
                destination = target,
                annotations = annotations,
                richTextPageLayouts = richTextPageLayouts
            )
        }
    }
}

internal fun printDesktopPdfDocument(document: DesktopPdfDocument) {
    require(document.formatLabel == "PDF") { "Only PDF files can be printed from the PDF reader." }
    val job = PrinterJob.getPrinterJob()
    job.jobName = "Episteme - ${document.title}"
    val printableBook = Book()
    val pageFormat = job.defaultPage()
    for (pageIndex in 0 until document.pageCount) {
        printableBook.append(
            Printable { graphics, format, _ ->
                drawPdfPageForPrint(document, pageIndex, graphics as Graphics2D, format)
                Printable.PAGE_EXISTS
            },
            pageFormat
        )
    }
    job.setPageable(printableBook)
    if (job.printDialog()) {
        job.print()
    }
}

internal fun buildDesktopPdfRasterOverlays(
    annotations: List<SharedPdfAnnotation>,
    richTextPageLayouts: List<SharedPdfRichPageLayout>,
    pageSizes: List<DesktopPdfPageSize>
): List<DesktopPdfRasterOverlay> {
    val overlays = mutableListOf<DesktopPdfRasterOverlay>()
    annotations.mapNotNullTo(overlays) { annotation ->
        if (annotation.kind != PdfAnnotationKind.TEXT) return@mapNotNullTo null
        renderDesktopTextBoxOverlay(annotation, pageSizes.getOrNull(annotation.pageIndex) ?: return@mapNotNullTo null)
    }
    richTextPageLayouts.mapNotNullTo(overlays) { layout ->
        renderDesktopRichTextOverlay(layout, pageSizes.getOrNull(layout.pageIndex) ?: return@mapNotNullTo null)
    }
    return overlays
}

private fun drawPdfPageForPrint(
    document: DesktopPdfDocument,
    pageIndex: Int,
    graphics: Graphics2D,
    pageFormat: PageFormat
) {
    val pageSize = document.pageSizes.getOrNull(pageIndex) ?: return
    val availableWidth = pageFormat.imageableWidth
    val availableHeight = pageFormat.imageableHeight
    val fit = minOf(
        availableWidth / pageSize.width.toDouble(),
        availableHeight / pageSize.height.toDouble()
    ).coerceAtLeast(0.01)
    val drawWidth = pageSize.width * fit
    val drawHeight = pageSize.height * fit
    val drawX = pageFormat.imageableX + (availableWidth - drawWidth) / 2.0
    val drawY = pageFormat.imageableY + (availableHeight - drawHeight) / 2.0
    val image = DesktopPdfium.renderPageBufferedImage(document, pageIndex, scale = 2f, renderAnnotations = true)
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    graphics.drawImage(
        image,
        drawX.roundToInt(),
        drawY.roundToInt(),
        drawWidth.roundToInt().coerceAtLeast(1),
        drawHeight.roundToInt().coerceAtLeast(1),
        null
    )
}

private fun renderDesktopTextBoxOverlay(
    annotation: SharedPdfAnnotation,
    pageSize: DesktopPdfPageSize
): DesktopPdfRasterOverlay? {
    val text = annotation.text.sanitizeDesktopRasterText()
    val bounds = annotation.bounds ?: return null
    if (annotation.pageIndex < 0 || text.isBlank()) return null

    val left = bounds.left.coerceIn(0f, 1f)
    val top = bounds.top.coerceIn(0f, 1f)
    val right = bounds.right.coerceIn(left, 1f)
    val bottom = bounds.bottom.coerceIn(top, 1f)
    if (right - left <= 0f || bottom - top <= 0f) return null

    val pageHeightPx = pageSize.exportHeightPx()
    val pageWidthPx = pageHeightPx * pageSize.aspect
    val bitmapWidth = ceil((right - left) * pageWidthPx).toInt().coerceAtLeast(1)
    val bitmapHeight = ceil((bottom - top) * pageHeightPx).toInt().coerceAtLeast(1)
    val paddingPx = DesktopPdfTextBoxPaddingPx
        .coerceAtMost((minOf(bitmapWidth, bitmapHeight) / 2f).coerceAtLeast(0f))
    val contentWidth = (bitmapWidth - paddingPx * 2f).roundToInt().coerceAtLeast(1)
    val fontSizePx = (annotation.sharedPdfTextPageRelativeFontSize() * pageHeightPx).coerceAtLeast(1f)
    val bitmap = BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB)

    val plainText = AnnotatedString(text)
    val baseStyle = DesktopTextRasterStyle(
        color = annotation.colorArgb.toAwtColor(),
        background = annotation.backgroundArgb.toAwtColor().takeIf { it.alpha > 0 },
        fontSize = fontSizePx,
        isBold = annotation.isBold,
        isItalic = annotation.isItalic,
        isUnderline = annotation.isUnderline,
        isStrikeThrough = annotation.isStrikeThrough,
        fontName = annotation.fontName
    )
    drawDesktopAttributedText(
        bitmap = bitmap,
        text = plainText,
        baseStyle = baseStyle,
        width = contentWidth,
        translateX = paddingPx,
        translateY = paddingPx
    )
    return bitmap.toDesktopRasterOverlay(annotation.pageIndex, left, top, right, bottom)
}

private fun renderDesktopRichTextOverlay(
    layout: SharedPdfRichPageLayout,
    pageSize: DesktopPdfPageSize
): DesktopPdfRasterOverlay? {
    val visibleText = layout.visibleText.withoutTrailingDesktopPageBreak()
    if (layout.pageIndex < 0 || visibleText.text.isBlank()) return null

    val pageHeightPx = layout.pageHeightPx.takeIf { it > 0f } ?: pageSize.exportHeightPx()
    val pageWidthPx = pageHeightPx * pageSize.aspect
    val left = DesktopPdfRichTextMarginX
    val top = DesktopPdfRichTextMarginY
    val right = 1f - DesktopPdfRichTextMarginX
    val bottom = 1f - DesktopPdfRichTextMarginY
    val bitmapWidth = ceil((right - left) * pageWidthPx).toInt().coerceAtLeast(1)
    val bitmapHeight = ceil((bottom - top) * pageHeightPx).toInt().coerceAtLeast(1)
    val bitmap = BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB)

    drawDesktopAttributedText(
        bitmap = bitmap,
        text = visibleText,
        baseStyle = DesktopTextRasterStyle(
            color = java.awt.Color.BLACK,
            background = null,
            fontSize = 16f,
            isBold = false,
            isItalic = false,
            isUnderline = false,
            isStrikeThrough = false,
            fontName = null
        ),
        width = bitmapWidth,
        translateX = 0f,
        translateY = 0f
    )
    return bitmap.toDesktopRasterOverlay(layout.pageIndex, left, top, right, bottom)
}

private fun drawDesktopAttributedText(
    bitmap: BufferedImage,
    text: AnnotatedString,
    baseStyle: DesktopTextRasterStyle,
    width: Int,
    translateX: Float,
    translateY: Float
) {
    val graphics = bitmap.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.clipRect(0, 0, bitmap.width, bitmap.height)
        var paragraphStart = 0
        var drawY = translateY
        val raw = text.text.sanitizeDesktopRasterTextPreservingLength()
        raw.split('\n').forEach { paragraph ->
            val paragraphEnd = paragraphStart + paragraph.length
            val attributed = attributedParagraph(
                paragraph = paragraph,
                paragraphStart = paragraphStart,
                text = text,
                baseStyle = baseStyle
            )
            val iterator = attributed.iterator
            val measurer = LineBreakMeasurer(iterator, graphics.fontRenderContext)
            while (measurer.position < iterator.endIndex && drawY < bitmap.height) {
                val layout = measurer.nextLayout(width.toFloat())
                drawY += layout.ascent
                layout.draw(graphics, translateX, drawY)
                drawY += layout.descent + layout.leading
            }
            if (paragraph.isEmpty()) {
                drawY += baseStyle.fontSize * 1.2f
            }
            paragraphStart = paragraphEnd + 1
        }
    } finally {
        graphics.dispose()
    }
}

private fun attributedParagraph(
    paragraph: String,
    paragraphStart: Int,
    text: AnnotatedString,
    baseStyle: DesktopTextRasterStyle
): AttributedString {
    val safeParagraph = paragraph.ifEmpty { " " }
    val attributed = AttributedString(safeParagraph)
    attributed.applyRasterStyle(baseStyle, 0, safeParagraph.length)
    text.spanStyles.forEach { range ->
        val start = maxOf(range.start, paragraphStart) - paragraphStart
        val end = minOf(range.end, paragraphStart + paragraph.length) - paragraphStart
        if (start < end) {
            attributed.applyRasterStyle(range.item.toDesktopTextRasterStyle(baseStyle), start, end)
        }
    }
    return attributed
}

private fun AttributedString.applyRasterStyle(style: DesktopTextRasterStyle, start: Int, end: Int) {
    val safeStart = start.coerceAtLeast(0)
    val safeEnd = end.coerceAtLeast(safeStart)
    if (safeStart >= safeEnd) return
    addAttribute(TextAttribute.FONT, style.awtFont(), safeStart, safeEnd)
    addAttribute(TextAttribute.FOREGROUND, style.color, safeStart, safeEnd)
    style.background?.let { addAttribute(TextAttribute.BACKGROUND, it, safeStart, safeEnd) }
    if (style.isUnderline) {
        addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, safeStart, safeEnd)
    }
    if (style.isStrikeThrough) {
        addAttribute(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, safeStart, safeEnd)
    }
}

private data class DesktopTextRasterStyle(
    val color: java.awt.Color,
    val background: java.awt.Color?,
    val fontSize: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isUnderline: Boolean,
    val isStrikeThrough: Boolean,
    val fontName: String?
) {
    fun awtFont(): Font {
        val style = (if (isBold) Font.BOLD else Font.PLAIN) or (if (isItalic) Font.ITALIC else Font.PLAIN)
        return Font(awtFontFamily(fontName), style, fontSize.roundToInt().coerceAtLeast(1))
    }
}

private fun SpanStyle.toDesktopTextRasterStyle(base: DesktopTextRasterStyle): DesktopTextRasterStyle {
    val color = this.color.takeUnless { it == Color.Unspecified }?.toAwtColor() ?: base.color
    val background = this.background.takeUnless { it == Color.Unspecified || it.alpha <= 0f }?.toAwtColor()
        ?: base.background
    val fontSize = if (this.fontSize.isSpecified && this.fontSize.isSp) {
        this.fontSize.value
    } else {
        base.fontSize
    }
    val fontWeight = this.fontWeight?.weight ?: if (base.isBold) 700 else 400
    return base.copy(
        color = color,
        background = background,
        fontSize = fontSize,
        isBold = fontWeight >= 600,
        isItalic = this.fontStyle == FontStyle.Italic || base.isItalic,
        isUnderline = this.textDecoration?.contains(TextDecoration.Underline) == true ||
            base.isUnderline,
        isStrikeThrough = this.textDecoration?.contains(TextDecoration.LineThrough) == true ||
            base.isStrikeThrough
    )
}

private fun awtFontFamily(fontName: String?): String {
    return when (fontName?.lowercase()) {
        "serif" -> Font.SERIF
        "monospace" -> Font.MONOSPACED
        else -> Font.SANS_SERIF
    }
}

private fun BufferedImage.toDesktopRasterOverlay(
    pageIndex: Int,
    boundsLeft: Float,
    boundsTop: Float,
    boundsRight: Float,
    boundsBottom: Float
): DesktopPdfRasterOverlay? {
    val allPixels = IntArray(width * height)
    getRGB(0, 0, width, height, allPixels, 0, width)

    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        val rowOffset = y * width
        for (x in 0 until width) {
            if ((allPixels[rowOffset + x] ushr 24) != 0) {
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
    val cropped = IntArray(cropWidth * cropHeight)
    for (row in 0 until cropHeight) {
        System.arraycopy(
            allPixels,
            (minY + row) * width + minX,
            cropped,
            row * cropWidth,
            cropWidth
        )
    }

    val boundsWidth = boundsRight - boundsLeft
    val boundsHeight = boundsBottom - boundsTop
    return DesktopPdfRasterOverlay(
        pageIndex = pageIndex,
        left = boundsLeft + boundsWidth * (minX.toFloat() / width),
        top = boundsTop + boundsHeight * (minY.toFloat() / height),
        right = boundsLeft + boundsWidth * ((maxX + 1).toFloat() / width),
        bottom = boundsTop + boundsHeight * ((maxY + 1).toFloat() / height),
        width = cropWidth,
        height = cropHeight,
        pixels = cropped
    )
}

private val DesktopPdfPageSize.aspect: Float
    get() = if (width > 0f && height > 0f) width / height else 612f / 792f

private fun DesktopPdfPageSize.exportHeightPx(): Float {
    return (height * DesktopPdfTextRasterPointScale)
        .coerceIn(DesktopPdfTextRasterMinPageHeightPx, DesktopPdfTextRasterMaxPageHeightPx)
}

private fun AnnotatedString.withoutTrailingDesktopPageBreak(): AnnotatedString =
    if (text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR) subSequence(0, length - 1) else this

private fun String.sanitizeDesktopRasterText(): String =
    replace(SHARED_PDF_PAGE_BREAK_CHAR, '\n')
        .replace("\u200B", "")
        .replace('\r', ' ')

private fun String.sanitizeDesktopRasterTextPreservingLength(): String =
    replace(SHARED_PDF_PAGE_BREAK_CHAR, '\n')
        .replace('\r', ' ')

private fun Int.toAwtColor(): java.awt.Color = java.awt.Color(this, true)

private fun Color.toAwtColor(): java.awt.Color = java.awt.Color(toArgb(), true)
