package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.roundToInt

internal object PdfiumAnnotationExporter {
    internal const val TEXT_FLAG_BOLD = 1
    internal const val TEXT_FLAG_ITALIC = 1 shl 1
    internal const val TEXT_FLAG_UNDERLINE = 1 shl 2
    internal const val TEXT_FLAG_STRIKE_THROUGH = 1 shl 3
    internal const val TEXT_FLAG_ABSOLUTE_LINE = 1 shl 4

    private const val TEXT_BOX_PADDING_DP = 8f
    private const val TEXT_RASTER_PDF_POINT_SCALE = 3f
    private const val TEXT_RASTER_MIN_PAGE_HEIGHT_PX = 1200f
    private const val TEXT_RASTER_MAX_PAGE_HEIGHT_PX = 3600f
    private const val RICH_TEXT_MARGIN_X = 0.1f
    private const val RICH_TEXT_MARGIN_Y = 0.08f

    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceUri: Uri,
        destStream: OutputStream,
        virtualPages: List<VirtualPage>?,
        inkAnnotations: Map<Int, List<PdfAnnotation>>,
        richTextPageLayouts: List<PageTextLayout>? = null,
        textBoxes: List<PdfTextBox>? = null,
        highlights: List<PdfUserHighlight>? = null
    ) {
        withContext(Dispatchers.IO) {
            if (!supportsOriginalPageOrder(virtualPages)) {
                destStream.close()
                throw UnsupportedOperationException(
                    "PDFium annotation export currently supports only the original PDF page order."
                )
            }

            val exportDir = File(context.cacheDir, "pdfium_annotation_export")
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                destStream.close()
                throw IOException("Unable to create PDFium export cache directory.")
            }
            val sourceFile: File
            val destFile: File
            try {
                sourceFile = File.createTempFile("source_", ".pdf", exportDir)
                destFile = File.createTempFile("annotated_", ".pdf", exportDir)
            } catch (e: IOException) {
                destStream.close()
                throw e
            }

            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(sourceFile).use { output -> input.copyTo(output) }
                } ?: throw IOException("Unable to open source PDF for PDFium export.")

                val pageSizes = runCatching { readPdfPageSizes(sourceFile) }
                    .onFailure { Timber.tag("PdfExportDebug").w(it, "Unable to read page sizes for text raster export.") }
                    .getOrDefault(emptyList())
                val rasterOverlays = buildTextRasterOverlays(
                    context = context,
                    textBoxes = textBoxes.orEmpty(),
                    richTextPageLayouts = richTextPageLayouts.orEmpty(),
                    pageSizes = pageSizes
                )
                val payload = buildPayload(
                    inkAnnotations = inkAnnotations,
                    textBoxes = emptyList(),
                    highlights = highlights.orEmpty(),
                    richTextPageLayouts = emptyList(),
                    rasterOverlays = rasterOverlays
                )

                if (!payload.hasAnnotations()) {
                    FileInputStream(sourceFile).use { input -> input.copyTo(destStream) }
                    return@withContext
                }

                val exported = NativePdfiumBridge.exportAnnotatedPdf(
                    sourcePath = sourceFile.absolutePath,
                    destPath = destFile.absolutePath,
                    inkPageIndices = payload.inkPageIndices,
                    inkTypes = payload.inkTypes,
                    inkColors = payload.inkColors,
                    inkStrokeWidths = payload.inkStrokeWidths,
                    inkPointOffsets = payload.inkPointOffsets,
                    inkPointCounts = payload.inkPointCounts,
                    inkPoints = payload.inkPoints,
                    textPageIndices = payload.textPageIndices,
                    textBounds = payload.textBounds,
                    textColors = payload.textColors,
                    textBackgroundColors = payload.textBackgroundColors,
                    textFontSizes = payload.textFontSizes,
                    textFlags = payload.textFlags,
                    textValues = payload.textValues,
                    textFontPaths = payload.textFontPaths,
                    textFontNames = payload.textFontNames,
                    rasterPageIndices = payload.rasterPageIndices,
                    rasterBounds = payload.rasterBounds,
                    rasterWidths = payload.rasterWidths,
                    rasterHeights = payload.rasterHeights,
                    rasterPixelOffsets = payload.rasterPixelOffsets,
                    rasterPixels = payload.rasterPixels,
                    highlightPageIndices = payload.highlightPageIndices,
                    highlightColors = payload.highlightColors,
                    highlightRectOffsets = payload.highlightRectOffsets,
                    highlightRectCounts = payload.highlightRectCounts,
                    highlightRects = payload.highlightRects,
                    highlightContents = payload.highlightContents
                )

                if (!exported) {
                    throw IOException("PDFium failed to write annotated PDF.")
                }

                FileInputStream(destFile).use { input -> input.copyTo(destStream) }
                Timber.tag("PdfExportDebug").i(
                    "PDFium export saved ${payload.inkPageIndices.size} ink, " +
                        "${payload.highlightPageIndices.size} highlight, " +
                        "${payload.rasterPageIndices.size} raster text overlays."
                )
            } finally {
                destStream.close()
                sourceFile.delete()
                destFile.delete()
            }
        }
    }

    internal fun supportsOriginalPageOrder(virtualPages: List<VirtualPage>?): Boolean {
        return virtualPages == null || virtualPages.withIndex().all { (index, page) ->
            page is VirtualPage.PdfPage && page.pdfIndex == index
        }
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun buildPayload(
        inkAnnotations: Map<Int, List<PdfAnnotation>>,
        textBoxes: List<PdfTextBox>,
        highlights: List<PdfUserHighlight>,
        richTextPageLayouts: List<PageTextLayout> = emptyList(),
        fontPathResolver: (String?) -> String? = { it },
        rasterOverlays: List<PdfiumRasterOverlay> = emptyList()
    ): PdfiumAnnotationExportPayload {
        val inkItems = inkAnnotations.entries
            .flatMap { (pageIndex, annotations) -> annotations.map { pageIndex to it } }
            .filter { (_, annotation) ->
                annotation.points.size >= 2 &&
                    annotation.inkType != InkType.ERASER &&
                    annotation.inkType != InkType.TEXT
            }

        val inkPageIndices = IntArray(inkItems.size)
        val inkTypes = IntArray(inkItems.size)
        val inkColors = IntArray(inkItems.size)
        val inkStrokeWidths = FloatArray(inkItems.size)
        val inkPointOffsets = IntArray(inkItems.size)
        val inkPointCounts = IntArray(inkItems.size)
        val inkPoints = FloatArray(inkItems.sumOf { it.second.points.size } * 2)

        var inkPointCursor = 0
        inkItems.forEachIndexed { index, (pageIndex, annotation) ->
            inkPageIndices[index] = pageIndex
            inkTypes[index] = annotation.inkType.ordinal
            inkColors[index] = annotation.color.toArgb()
            inkStrokeWidths[index] = annotation.strokeWidth
            inkPointOffsets[index] = inkPointCursor / 2
            inkPointCounts[index] = annotation.points.size
            annotation.points.forEach { point ->
                inkPoints[inkPointCursor++] = point.x
                inkPoints[inkPointCursor++] = point.y
            }
        }

        val textPageIndices = IntArray(0)
        val textBounds = FloatArray(0)
        val textColors = IntArray(0)
        val textBackgroundColors = IntArray(0)
        val textFontSizes = FloatArray(0)
        val textFlags = IntArray(0)
        val textValues = emptyArray<String>()
        val textFontPaths = emptyArray<String>()
        val textFontNames = emptyArray<String>()

        val rasterPageIndices = IntArray(rasterOverlays.size)
        val rasterBounds = FloatArray(rasterOverlays.size * 4)
        val rasterWidths = IntArray(rasterOverlays.size)
        val rasterHeights = IntArray(rasterOverlays.size)
        val rasterPixelOffsets = IntArray(rasterOverlays.size)
        val rasterPixels = IntArray(rasterOverlays.sumOf { it.pixels.size })

        var rasterPixelCursor = 0
        rasterOverlays.forEachIndexed { index, overlay ->
            rasterPageIndices[index] = overlay.pageIndex
            rasterBounds[index * 4] = overlay.left
            rasterBounds[index * 4 + 1] = overlay.top
            rasterBounds[index * 4 + 2] = overlay.right
            rasterBounds[index * 4 + 3] = overlay.bottom
            rasterWidths[index] = overlay.width
            rasterHeights[index] = overlay.height
            rasterPixelOffsets[index] = rasterPixelCursor
            overlay.pixels.copyInto(rasterPixels, rasterPixelCursor)
            rasterPixelCursor += overlay.pixels.size
        }

        val boundedHighlights = highlights.filter { it.bounds.isNotEmpty() }
        val highlightPageIndices = IntArray(boundedHighlights.size)
        val highlightColors = IntArray(boundedHighlights.size)
        val highlightRectOffsets = IntArray(boundedHighlights.size)
        val highlightRectCounts = IntArray(boundedHighlights.size)
        val highlightRects = FloatArray(boundedHighlights.sumOf { it.bounds.size } * 4)
        val highlightContents = Array(boundedHighlights.size) { "" }

        var highlightRectCursor = 0
        boundedHighlights.forEachIndexed { index, highlight ->
            highlightPageIndices[index] = highlight.pageIndex
            highlightColors[index] = highlight.color.color.toArgb()
            highlightRectOffsets[index] = highlightRectCursor / 4
            highlightRectCounts[index] = highlight.bounds.size
            highlightContents[index] = highlight.note?.takeIf { it.isNotBlank() } ?: highlight.text
            highlight.bounds.forEach { rect ->
                highlightRects[highlightRectCursor++] = rect.left
                highlightRects[highlightRectCursor++] = rect.top
                highlightRects[highlightRectCursor++] = rect.right
                highlightRects[highlightRectCursor++] = rect.bottom
            }
        }

        return PdfiumAnnotationExportPayload(
            inkPageIndices = inkPageIndices,
            inkTypes = inkTypes,
            inkColors = inkColors,
            inkStrokeWidths = inkStrokeWidths,
            inkPointOffsets = inkPointOffsets,
            inkPointCounts = inkPointCounts,
            inkPoints = inkPoints,
            textPageIndices = textPageIndices,
            textBounds = textBounds,
            textColors = textColors,
            textBackgroundColors = textBackgroundColors,
            textFontSizes = textFontSizes,
            textFlags = textFlags,
            textValues = textValues,
            textFontPaths = textFontPaths,
            textFontNames = textFontNames,
            rasterPageIndices = rasterPageIndices,
            rasterBounds = rasterBounds,
            rasterWidths = rasterWidths,
            rasterHeights = rasterHeights,
            rasterPixelOffsets = rasterPixelOffsets,
            rasterPixels = rasterPixels,
            highlightPageIndices = highlightPageIndices,
            highlightColors = highlightColors,
            highlightRectOffsets = highlightRectOffsets,
            highlightRectCounts = highlightRectCounts,
            highlightRects = highlightRects,
            highlightContents = highlightContents
        )
    }

    private fun buildTextRasterOverlays(
        context: Context,
        textBoxes: List<PdfTextBox>,
        richTextPageLayouts: List<PageTextLayout>,
        pageSizes: List<PdfiumPageSize>
    ): List<PdfiumRasterOverlay> {
        val overlays = mutableListOf<PdfiumRasterOverlay>()
        textBoxes.mapNotNullTo(overlays) { box ->
            renderTextBoxOverlay(context, box, pageSizeFor(pageSizes, box.pageIndex))
        }
        richTextPageLayouts.mapNotNullTo(overlays) { layout ->
            renderRichTextOverlay(context, layout, pageSizeFor(pageSizes, layout.pageIndex))
        }
        return overlays
    }

    private fun renderTextBoxOverlay(
        context: Context,
        box: PdfTextBox,
        pageSize: PdfiumPageSize
    ): PdfiumRasterOverlay? {
        val text = box.text.sanitizeRasterText()
        if (box.pageIndex < 0 || text.isBlank()) return null

        val bounds = box.relativeBounds
        val left = bounds.left.coerceIn(0f, 1f)
        val top = bounds.top.coerceIn(0f, 1f)
        val right = bounds.right.coerceIn(left, 1f)
        val bottom = bounds.bottom.coerceIn(top, 1f)
        if (right - left <= 0f || bottom - top <= 0f) return null

        val pageHeightPx = pageSize.exportHeightPx()
        val pageWidthPx = pageHeightPx * pageSize.aspect
        val bitmapWidth = ceil((right - left) * pageWidthPx).toInt().coerceAtLeast(1)
        val bitmapHeight = ceil((bottom - top) * pageHeightPx).toInt().coerceAtLeast(1)
        val paddingPx = dpToPx(context, TEXT_BOX_PADDING_DP)
            .coerceAtMost((minOf(bitmapWidth, bitmapHeight) / 2f).coerceAtLeast(0f))
        val contentWidth = (bitmapWidth - paddingPx * 2f).roundToInt().coerceAtLeast(1)
        val fontSizePx = (box.fontSize * pageHeightPx).coerceAtLeast(1f)
        val typeface = resolveTypeface(context, box.fontPath, box.fontName, box.isBold, box.isItalic)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        return try {
            val paint = textPaint(
                colorArgb = box.color.toArgb(),
                textSizePx = fontSizePx,
                typeface = typeface
            )
            val spannable = SpannableString(text)
            applyTextBoxSpans(
                text = spannable,
                colorArgb = box.color.toArgb(),
                backgroundArgb = box.backgroundColor.toArgb(),
                fontSizePx = fontSizePx,
                isBold = box.isBold,
                isItalic = box.isItalic,
                isUnderline = box.isUnderline,
                isStrikeThrough = box.isStrikeThrough,
                typeface = typeface
            )
            drawStaticLayout(
                bitmap = bitmap,
                text = spannable,
                paint = paint,
                width = contentWidth,
                translateX = paddingPx,
                translateY = paddingPx
            )
            bitmap.toRasterOverlay(box.pageIndex, left, top, right, bottom)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderRichTextOverlay(
        context: Context,
        layout: PageTextLayout,
        pageSize: PdfiumPageSize
    ): PdfiumRasterOverlay? {
        val visibleText = layout.visibleText.withoutTrailingPdfiumPageBreak()
        if (layout.pageIndex < 0 || visibleText.text.isBlank()) return null

        val pageHeightPx = layout.pageHeightPx.takeIf { it > 0f } ?: pageSize.exportHeightPx()
        val pageWidthPx = pageHeightPx * pageSize.aspect
        val left = RICH_TEXT_MARGIN_X
        val top = RICH_TEXT_MARGIN_Y
        val right = 1f - RICH_TEXT_MARGIN_X
        val bottom = 1f - RICH_TEXT_MARGIN_Y
        val bitmapWidth = ceil((right - left) * pageWidthPx).toInt().coerceAtLeast(1)
        val bitmapHeight = ceil((bottom - top) * pageHeightPx).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        return try {
            val paint = textPaint(
                colorArgb = Color.Black.toArgb(),
                textSizePx = spToPx(context, 16f),
                typeface = Typeface.DEFAULT
            )
            val spannable = visibleText.toAndroidSpannable(context)
            drawStaticLayout(
                bitmap = bitmap,
                text = spannable,
                paint = paint,
                width = bitmapWidth,
                translateX = 0f,
                translateY = 0f
            )
            bitmap.toRasterOverlay(layout.pageIndex, left, top, right, bottom)
        } finally {
            bitmap.recycle()
        }
    }

    private fun applyTextBoxSpans(
        text: SpannableString,
        colorArgb: Int,
        backgroundArgb: Int,
        fontSizePx: Float,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean,
        isStrikeThrough: Boolean,
        typeface: Typeface
    ) {
        if (text.isEmpty()) return
        val end = text.length
        text.setSpan(ForegroundColorSpan(colorArgb), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if ((backgroundArgb ushr 24) != 0) {
            text.setSpan(BackgroundColorSpan(backgroundArgb), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text.setSpan(AbsoluteSizeSpan(fontSizePx.roundToInt().coerceAtLeast(1), false), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(TypefaceSpanCompat(typeface), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (!hasStyle(typeface, isBold, isItalic)) {
            text.setSpan(StyleSpan(typefaceStyle(isBold, isItalic)), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isUnderline) {
            text.setSpan(UnderlineSpan(), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isStrikeThrough) {
            text.setSpan(StrikethroughSpan(), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun AnnotatedString.toAndroidSpannable(context: Context): SpannableString {
        val spannable = SpannableString(text.sanitizeRasterTextPreservingLength())
        spanStyles.forEach { range ->
            applySpanStyle(context, spannable, range.item, range.start, range.end)
        }
        return spannable
    }

    private fun applySpanStyle(
        context: Context,
        spannable: SpannableString,
        style: SpanStyle,
        rawStart: Int,
        rawEnd: Int
    ) {
        val start = rawStart.coerceIn(0, spannable.length)
        val end = rawEnd.coerceIn(start, spannable.length)
        if (start >= end) return

        val color = style.color
        if (color != Color.Unspecified) {
            spannable.setSpan(ForegroundColorSpan(color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val background = style.background
        if (background != Color.Unspecified && background.alpha > 0f) {
            spannable.setSpan(BackgroundColorSpan(background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.fontSize.isSpecified) {
            val textSizePx = spToPx(context, style.fontSize.value)
            spannable.setSpan(
                AbsoluteSizeSpan(textSizePx.roundToInt().coerceAtLeast(1), false),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val isBold = isBold(style.fontWeight)
        val isItalic = style.fontStyle == FontStyle.Italic
        val fontPath = PdfFontCache.getPath(style.fontFamily)
        val fontName = standardFontName(style.fontFamily)
        val typeface = resolveTypeface(context, fontPath, fontName, isBold, isItalic)
        if (fontPath != null || fontName != null) {
            spannable.setSpan(TypefaceSpanCompat(typeface), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (isBold || isItalic) {
            spannable.setSpan(StyleSpan(typefaceStyle(isBold, isItalic)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val decoration = style.textDecoration ?: TextDecoration.None
        if (decoration.contains(TextDecoration.Underline)) {
            spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (decoration.contains(TextDecoration.LineThrough)) {
            spannable.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun drawStaticLayout(
        bitmap: Bitmap,
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        translateX: Float,
        translateY: Float
    ) {
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.clipRect(0, 0, bitmap.width, bitmap.height)
        canvas.translate(translateX, translateY)
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
            .draw(canvas)
        canvas.restore()
    }

    private fun textPaint(
        colorArgb: Int,
        textSizePx: Float,
        typeface: Typeface
    ): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = colorArgb
            textSize = textSizePx
            this.typeface = typeface
        }

    private fun Bitmap.toRasterOverlay(
        pageIndex: Int,
        boundsLeft: Float,
        boundsTop: Float,
        boundsRight: Float,
        boundsBottom: Float
    ): PdfiumRasterOverlay? {
        val allPixels = IntArray(width * height)
        getPixels(allPixels, 0, width, 0, 0, width, height)

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
        return PdfiumRasterOverlay(
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

    private fun readPdfPageSizes(sourceFile: File): List<PdfiumPageSize> {
        return ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                List(renderer.pageCount) { index ->
                    val page = renderer.openPage(index)
                    try {
                        PdfiumPageSize(page.width, page.height)
                    } finally {
                        page.close()
                    }
                }
            }
        }
    }

    private fun pageSizeFor(pageSizes: List<PdfiumPageSize>, pageIndex: Int): PdfiumPageSize =
        pageSizes.getOrNull(pageIndex) ?: PdfiumPageSize.Default

    private fun PdfiumPageSize.exportHeightPx(): Float =
        (height * TEXT_RASTER_PDF_POINT_SCALE)
            .coerceIn(TEXT_RASTER_MIN_PAGE_HEIGHT_PX, TEXT_RASTER_MAX_PAGE_HEIGHT_PX)

    private fun resolveTypeface(
        context: Context,
        fontPath: String?,
        fontName: String?,
        isBold: Boolean,
        isItalic: Boolean
    ): Typeface {
        val base = try {
            when {
                !fontPath.isNullOrBlank() && fontPath.startsWith("asset:") ->
                    Typeface.createFromAsset(context.assets, fontPath.removePrefix("asset:"))
                !fontPath.isNullOrBlank() ->
                    Typeface.createFromFile(fontPath)
                else -> when (fontName?.lowercase(Locale.US)) {
                    "serif" -> Typeface.SERIF
                    "monospace" -> Typeface.MONOSPACE
                    "cursive" -> Typeface.create("casual", Typeface.NORMAL)
                    "sans", "sansserif", "sans-serif" -> Typeface.SANS_SERIF
                    else -> Typeface.DEFAULT
                }
            }
        } catch (e: Exception) {
            Timber.tag("PdfFontDebug").w(e, "Falling back while rasterizing fontPath=$fontPath fontName=$fontName")
            Typeface.DEFAULT
        }
        return Typeface.create(base, typefaceStyle(isBold, isItalic))
    }

    private fun typefaceStyle(isBold: Boolean, isItalic: Boolean): Int =
        when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

    private fun hasStyle(typeface: Typeface, isBold: Boolean, isItalic: Boolean): Boolean {
        val style = typeface.style
        return (!isBold || style and Typeface.BOLD != 0) &&
            (!isItalic || style and Typeface.ITALIC != 0)
    }

    private fun isBold(weight: FontWeight?): Boolean =
        (weight?.weight ?: FontWeight.Normal.weight) >= FontWeight.SemiBold.weight

    private fun standardFontName(fontFamily: FontFamily?): String? =
        when (fontFamily) {
            FontFamily.Serif -> "Serif"
            FontFamily.Monospace -> "Monospace"
            FontFamily.SansSerif -> "Sans"
            FontFamily.Cursive -> "Cursive"
            else -> null
        }

    private fun dpToPx(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    private fun spToPx(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)

    private fun AnnotatedString.withoutTrailingPdfiumPageBreak(): AnnotatedString =
        if (text.lastOrNull() == PAGE_BREAK_CHAR) subSequence(0, length - 1) else this

    private fun String.sanitizeRasterText(): String =
        replace(PAGE_BREAK_CHAR, '\n')
            .replace("\u200B", "")
            .replace('\r', ' ')

    private fun String.sanitizeRasterTextPreservingLength(): String =
        replace(PAGE_BREAK_CHAR, '\n')
            .replace('\r', ' ')
}

internal data class PdfiumRasterOverlay(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

private data class PdfiumPageSize(
    val width: Int,
    val height: Int
) {
    val aspect: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else Default.aspect

    companion object {
        val Default = PdfiumPageSize(612, 792)
    }
}

private class TypefaceSpanCompat(
    private val typeface: Typeface
) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        apply(tp)
    }

    override fun updateMeasureState(tp: TextPaint) {
        apply(tp)
    }

    private fun apply(paint: Paint) {
        val oldStyle = paint.typeface?.style ?: Typeface.NORMAL
        val missingStyles = oldStyle and typeface.style.inv()
        if (missingStyles and Typeface.BOLD != 0) {
            paint.isFakeBoldText = true
        }
        if (missingStyles and Typeface.ITALIC != 0) {
            paint.textSkewX = -0.25f
        }
        paint.typeface = typeface
    }
}

internal data class PdfiumAnnotationExportPayload(
    val inkPageIndices: IntArray,
    val inkTypes: IntArray,
    val inkColors: IntArray,
    val inkStrokeWidths: FloatArray,
    val inkPointOffsets: IntArray,
    val inkPointCounts: IntArray,
    val inkPoints: FloatArray,
    val textPageIndices: IntArray,
    val textBounds: FloatArray,
    val textColors: IntArray,
    val textBackgroundColors: IntArray,
    val textFontSizes: FloatArray,
    val textFlags: IntArray,
    val textValues: Array<String>,
    val textFontPaths: Array<String>,
    val textFontNames: Array<String>,
    val rasterPageIndices: IntArray,
    val rasterBounds: FloatArray,
    val rasterWidths: IntArray,
    val rasterHeights: IntArray,
    val rasterPixelOffsets: IntArray,
    val rasterPixels: IntArray,
    val highlightPageIndices: IntArray,
    val highlightColors: IntArray,
    val highlightRectOffsets: IntArray,
    val highlightRectCounts: IntArray,
    val highlightRects: FloatArray,
    val highlightContents: Array<String>
) {
    fun hasAnnotations(): Boolean =
        inkPageIndices.isNotEmpty() ||
            textPageIndices.isNotEmpty() ||
            rasterPageIndices.isNotEmpty() ||
            highlightPageIndices.isNotEmpty()
}
