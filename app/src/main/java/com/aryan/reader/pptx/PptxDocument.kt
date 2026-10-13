package com.aryan.reader.pptx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import androidx.core.graphics.createBitmap
import com.aryan.reader.pdf.DummyTextPage
import com.aryan.reader.pdf.ReaderDocument
import com.aryan.reader.pdf.ReaderLink
import com.aryan.reader.pdf.ReaderPage
import com.aryan.reader.pdf.ReaderTextPage
import com.aryan.reader.pdf.ReaderTextRect
import com.aryan.reader.shared.pptx.SharedPptxAutoFitMode
import com.aryan.reader.shared.pptx.SharedPptxCharBox
import com.aryan.reader.shared.pptx.SharedPptxCustomGeometry
import com.aryan.reader.shared.pptx.SharedPptxDeck
import com.aryan.reader.shared.pptx.SharedPptxDeckCache
import com.aryan.reader.shared.pptx.SharedPptxElement
import com.aryan.reader.shared.pptx.SharedPptxGradientFill
import com.aryan.reader.shared.pptx.SharedPptxImageCrop
import com.aryan.reader.shared.pptx.SharedPptxImageElement
import com.aryan.reader.shared.pptx.SharedPptxParagraph
import com.aryan.reader.shared.pptx.SharedPptxPathCommand
import com.aryan.reader.shared.pptx.SharedPptxRect
import com.aryan.reader.shared.pptx.SharedPptxShapeElement
import com.aryan.reader.shared.pptx.SharedPptxSlide
import com.aryan.reader.shared.pptx.SharedPptxTableCell
import com.aryan.reader.shared.pptx.SharedPptxTableElement
import com.aryan.reader.shared.pptx.SharedPptxTableRow
import com.aryan.reader.shared.pptx.SharedPptxTextAlign
import com.aryan.reader.shared.pptx.SharedPptxTextInsets
import com.aryan.reader.shared.pptx.SharedPptxTextRun
import com.aryan.reader.shared.pptx.SharedPptxVerticalAnchor
import com.aryan.reader.shared.pptx.displayText
import com.aryan.reader.shared.pptx.effectiveLineSpacing
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation

private const val DEFAULT_TEXT_SIZE_PT = 18f

// Single-source slide model: Android renders SharedPptx* directly (Desktop pattern).
// The only Android-specific shape is RectF, converted at Canvas boundaries.
internal typealias PptxDeck = SharedPptxDeck
internal typealias PptxSlide = SharedPptxSlide
internal typealias PptxCharBox = SharedPptxCharBox
internal typealias PptxElement = SharedPptxElement
internal typealias PptxShapeElement = SharedPptxShapeElement
internal typealias PptxImageElement = SharedPptxImageElement
internal typealias PptxImageCrop = SharedPptxImageCrop
internal typealias PptxTableElement = SharedPptxTableElement
internal typealias PptxTableRow = SharedPptxTableRow
internal typealias PptxTableCell = SharedPptxTableCell
internal typealias PptxParagraph = SharedPptxParagraph
internal typealias PptxTextRun = SharedPptxTextRun
internal typealias PptxTextAlign = SharedPptxTextAlign
internal typealias PptxVerticalAnchor = SharedPptxVerticalAnchor
internal typealias PptxAutoFitMode = SharedPptxAutoFitMode
internal typealias PptxTextInsets = SharedPptxTextInsets
internal typealias PptxGradientFill = SharedPptxGradientFill
internal typealias PptxCustomGeometry = SharedPptxCustomGeometry
internal typealias PptxPathCommand = SharedPptxPathCommand

internal fun SharedPptxCustomGeometry.toPath(bounds: SharedPptxRect): Path {
    val scaleX = bounds.width() / width.coerceAtLeast(1f)
    val scaleY = bounds.height() / height.coerceAtLeast(1f)
    fun x(value: Float) = bounds.left + value * scaleX
    fun y(value: Float) = bounds.top + value * scaleY
    return Path().apply {
        commands.forEach { command ->
            when (command) {
                is SharedPptxPathCommand.MoveTo -> moveTo(x(command.x), y(command.y))
                is SharedPptxPathCommand.LineTo -> lineTo(x(command.x), y(command.y))
                is SharedPptxPathCommand.QuadTo -> quadTo(
                    x(command.x1),
                    y(command.y1),
                    x(command.x2),
                    y(command.y2)
                )
                is SharedPptxPathCommand.CubicTo -> cubicTo(
                    x(command.x1),
                    y(command.y1),
                    x(command.x2),
                    y(command.y2),
                    x(command.x3),
                    y(command.y3)
                )
                SharedPptxPathCommand.Close -> close()
            }
        }
    }
}

internal object PptxDeckCache {
    fun load(file: File): PptxDeck = PptxDocumentParser.parse(file)
}

internal object PptxDocumentParser {
    fun parse(file: File): PptxDeck = SharedPptxDeckCache.load(file)
}

internal fun SharedPptxRect.toRectF(): RectF = RectF(left, top, right, bottom)

internal class PptxDocumentWrapper(
    private val file: File,
    private val deleteOnClose: Boolean = false
) : ReaderDocument {
    private val deck: PptxDeck by lazy { PptxDeckCache.load(file) }

    override suspend fun getPageCount(): Int = deck.slides.size

    override suspend fun openPage(pageIndex: Int): ReaderPage? {
        return deck.slides.getOrNull(pageIndex)?.let(::PptxPageWrapper)
    }

    override suspend fun getTableOfContents(): List<Bookmark> = emptyList()

    override fun close() {
        if (deleteOnClose) {
            runCatching { file.delete() }
        }
    }
}

internal class PptxPageWrapper(
    private val slide: PptxSlide
) : ReaderPage {
    override suspend fun getPageWidthPoint(): Int = slide.widthPoint
    override suspend fun getPageHeightPoint(): Int = slide.heightPoint
    override suspend fun getPageRotation(): Int = 0

    override suspend fun renderPageBitmap(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        renderAnnot: Boolean
    ) {
        withContext(Dispatchers.Default) {
            PptxSlideRenderer.render(slide, bitmap, startX, startY, drawSizeX, drawSizeY)
        }
    }

    override suspend fun mapRectToDevice(
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: RectF
    ): Rect {
        val scaleX = sizeX.toFloat() / slide.widthPoint.toFloat().coerceAtLeast(1f)
        val scaleY = sizeY.toFloat() / slide.heightPoint.toFloat().coerceAtLeast(1f)
        return Rect(
            (startX + coords.left * scaleX).roundToInt(),
            (startY + coords.top * scaleY).roundToInt(),
            (startX + coords.right * scaleX).roundToInt(),
            (startY + coords.bottom * scaleY).roundToInt()
        )
    }

    override suspend fun mapDeviceCoordsToPage(
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        deviceX: Int,
        deviceY: Int
    ): PointF {
        val scaleX = sizeX.toFloat() / slide.widthPoint.toFloat().coerceAtLeast(1f)
        val scaleY = sizeY.toFloat() / slide.heightPoint.toFloat().coerceAtLeast(1f)
        return PointF(
            (deviceX - startX) / scaleX,
            (deviceY - startY) / scaleY
        )
    }

    override suspend fun openTextPage(): ReaderTextPage {
        return if (slide.text.isBlank()) DummyTextPage() else PptxTextPage(slide)
    }

    override suspend fun getLinks(): List<ReaderLink> {
        return slide.elements.mapNotNull { element ->
            val shape = element as? PptxShapeElement ?: return@mapNotNull null
            val link = shape.hyperlink?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ReaderLink(uri = link, destPageIdx = null, bounds = shape.bounds.toRectF())
        }
    }

    override fun getNativePointer(): Long = 0L
    override fun close() = Unit
}

internal class PptxTextPage(
    private val slide: PptxSlide
) : ReaderTextPage {
    override suspend fun textPageCountChars(): Int = slide.text.length

    override suspend fun textPageGetText(startIndex: Int, count: Int): String? {
        if (count <= 0 || startIndex !in 0..slide.text.length) return ""
        val end = (startIndex + count).coerceAtMost(slide.text.length)
        return slide.text.substring(startIndex, end)
    }

    override suspend fun textPageGetRectsForRanges(ranges: IntArray): List<ReaderTextRect>? {
        if (ranges.size < 2) return emptyList()
        val rects = mutableListOf<ReaderTextRect>()
        var index = 0
        while (index + 1 < ranges.size) {
            val start = ranges[index].coerceIn(0, slide.charBoxes.size)
            val length = ranges[index + 1].coerceAtLeast(0)
            val end = (start + length).coerceAtMost(slide.charBoxes.size)
            val lineRects = slide.charBoxes.subList(start, end)
                .filter { !it.char.isWhitespace() }
                .groupBy { it.bounds.top.roundToInt() }
                .values
                .mapNotNull { boxes ->
                    boxes.fold<PptxCharBox, RectF?>(null) { acc, box ->
                        acc?.apply { union(box.bounds.toRectF()) } ?: box.bounds.toRectF()
                    }
                }
            rects += lineRects.map(::ReaderTextRect)
            index += 2
        }
        return rects
    }

    override suspend fun textPageGetCharIndexAtPos(
        x: Double,
        y: Double,
        xTolerance: Double,
        yTolerance: Double
    ): Int {
        val pointX = x.toFloat()
        val pointY = y.toFloat()
        val expanded = RectF()
        slide.charBoxes.forEachIndexed { index, box ->
            expanded.set(box.bounds.left, box.bounds.top, box.bounds.right, box.bounds.bottom)
            expanded.inset(-xTolerance.toFloat(), -yTolerance.toFloat())
            if (expanded.contains(pointX, pointY)) return index
        }
        return -1
    }

    override suspend fun textPageGetCharBox(index: Int): RectF? {
        return slide.charBoxes.getOrNull(index)?.bounds?.toRectF()
    }

    override suspend fun textPageGetUnicode(index: Int): Int {
        return slide.text.getOrNull(index)?.code ?: 0
    }

    override suspend fun loadWebLink() = null
    override fun close() = Unit
}

internal class PptxCoverGenerator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generateCover(uri: Uri, targetHeight: Int = 800): Bitmap? = withContext(Dispatchers.IO) {
        val cacheFile = File(appContext.cacheDir, "pptx_cover_${System.currentTimeMillis()}.pptx")
        try {
            openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            PptxDocumentWrapper(cacheFile, deleteOnClose = true).use { doc ->
                val page = doc.openPage(0) ?: return@withContext null
                page.use {
                    val width = it.getPageWidthPoint()
                    val height = it.getPageHeightPoint()
                    if (width <= 0 || height <= 0) return@withContext null
                    val targetWidth = (targetHeight * (width.toFloat() / height.toFloat())).roundToInt().coerceAtLeast(1)
                    val bitmap = createBitmap(targetWidth, targetHeight)
                    it.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false)
                    bitmap
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate PPTX cover")
            null
        } finally {
            runCatching { cacheFile.delete() }
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            fileFromUri(uri)?.takeIf(File::isFile)?.inputStream()
        } else {
            appContext.contentResolver.openInputStream(uri)
        }
    }

    private fun fileFromUri(uri: Uri): File? {
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        runCatching { File(URI(uri.toString())) }.getOrNull()?.let { return it }
        uri.path?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val rawPath = uri.toString().removePrefix("file:").takeIf { it.isNotBlank() } ?: return null
        val normalizedPath = when {
            rawPath.startsWith("///") -> rawPath.drop(3)
            rawPath.length > 2 && rawPath[0] == '/' && rawPath[2] == ':' -> rawPath.drop(1)
            else -> rawPath
        }
        return runCatching {
            File(URLDecoder.decode(normalizedPath, Charsets.UTF_8.name()))
        }.getOrNull()
    }
}

private data class LaidOutParagraph(
    val text: String,
    val layout: StaticLayout,
    val x: Float,
    val y: Float,
    val charBoxes: List<RectF>
)

private data class PreparedParagraphLayout(
    val paragraph: PptxParagraph,
    val text: String,
    val layout: StaticLayout
)

private data class LaidOutTableCell(
    val rect: SharedPptxRect,
    val cell: PptxTableCell
)

private fun LaidOutTableCell.asShape(): PptxShapeElement {
    return PptxShapeElement(
        bounds = rect,
        preset = "rect",
        fillColor = cell.fillColor,
        gradientFill = null,
        lineColor = cell.lineColor,
        lineWidthPoint = 0.75f,
        paragraphs = cell.paragraphs,
        hyperlink = null,
        placeholderKey = null,
        textInsets = cell.textInsets,
        verticalAnchor = cell.verticalAnchor
    )
}

private val TextLayoutPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = DEFAULT_TEXT_SIZE_PT
}

private object PptxSlideRenderer {
    fun render(slide: PptxSlide, bitmap: Bitmap, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(slide.backgroundColor ?: Color.WHITE)
        if (slide.widthPoint <= 0 || slide.heightPoint <= 0) return
        canvas.withTranslation(startX.toFloat(), startY.toFloat()) {
            scale(drawSizeX.toFloat() / slide.widthPoint, drawSizeY.toFloat() / slide.heightPoint)

            slide.elements.forEach { element ->
                when (element) {
                    is PptxShapeElement -> drawShape(this, element)
                    is PptxImageElement -> drawImage(this, element)
                    is PptxTableElement -> drawTable(this, element)
                }
            }

        }
    }

    private fun drawShape(canvas: Canvas, shape: PptxShapeElement) {
        canvas.withRotation(shape.bounds, shape.rotationDegrees) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shape.gradientFill?.let { shader = it.toShader(shape.bounds) }
                if (shape.gradientFill == null) {
                    color = shape.fillColor ?: Color.TRANSPARENT
                }
            }
            val fillColor = shape.fillColor
            if (shape.gradientFill != null || (fillColor != null && Color.alpha(fillColor) > 0)) {
                drawPresetShape(canvas, shape.bounds, shape.preset, shape.customGeometry, fillPaint)
            }

            val strokeColor = shape.lineColor
            if (strokeColor != null && Color.alpha(strokeColor) > 0) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = strokeColor
                    strokeWidth = shape.lineWidthPoint.coerceAtLeast(0.25f)
                }
                drawPresetShape(canvas, shape.bounds, shape.preset, shape.customGeometry, strokePaint)
            }

            drawText(canvas, shape)
        }
    }

    private fun drawText(canvas: Canvas, shape: PptxShapeElement) {
        if (!shape.renderText) return
        val layout = layoutParagraphs(shape, shape.bounds)
        val clip = shape.textBounds().takeUnless { shape.autoFitMode == PptxAutoFitMode.SHAPE }
        layout.forEach { paragraph ->
            canvas.withSave {
                clip?.let { clipRect(it.toRectF()) }
                translate(paragraph.x, paragraph.y)
                paragraph.layout.draw(this)
            }
        }
    }

    private fun drawTable(canvas: Canvas, table: PptxTableElement) {
        canvas.withRotation(table.bounds, table.rotationDegrees) {
            layoutTableCells(table).forEach { laidOutCell ->
                val rect = laidOutCell.rect.toRectF()
                val cell = laidOutCell.cell
                val fill = cell.fillColor
                if (fill != null && Color.alpha(fill) > 0) {
                    canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = fill
                    })
                }
                cell.lineColor?.let { lineColor ->
                    canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = lineColor
                        strokeWidth = 0.5f
                    })
                }
                drawText(canvas, laidOutCell.asShape())
            }
        }
    }

    private fun drawImage(canvas: Canvas, image: PptxImageElement) {
        canvas.withRotation(image.bounds, image.rotationDegrees) {
            val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            if (bitmap != null) {
                canvas.drawBitmap(
                    bitmap,
                    image.crop.sourceRect(bitmap.width, bitmap.height),
                    image.bounds.toRectF(),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = (255f * image.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
                    }
                )
                bitmap.recycle()
            } else {
                val bounds = image.bounds.toRectF()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.LTGRAY
                }
                canvas.drawRect(bounds, paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.GRAY
                paint.strokeWidth = 0.75f
                canvas.drawRect(bounds, paint)
            }
        }
    }
}

private fun layoutParagraphs(shape: PptxShapeElement, bounds: SharedPptxRect): List<LaidOutParagraph> {
    if (shape.paragraphs.isEmpty() || bounds.width() <= 0f || bounds.height() <= 0f) return emptyList()
    val baseTextBounds = shape.textBounds(bounds)
    if (baseTextBounds.width() <= 0f || baseTextBounds.height() <= 0f) return emptyList()

    var fontScale = shape.fontScale
    var prepared = prepareParagraphLayouts(shape, baseTextBounds, fontScale)
    if (shape.autoFitMode == PptxAutoFitMode.NORMAL && prepared.isNotEmpty()) {
        repeat(8) {
            val totalHeight = prepared.totalHeight()
            if (totalHeight <= baseTextBounds.height() || fontScale <= 0.35f) return@repeat
            val fitRatio = (baseTextBounds.height() / totalHeight).coerceIn(0.35f, 0.96f)
            fontScale = (fontScale * fitRatio).coerceAtLeast(0.35f)
            prepared = prepareParagraphLayouts(shape, baseTextBounds, fontScale)
        }
    }
    if (prepared.isEmpty()) return emptyList()

    val totalHeight = prepared.totalHeight()
    val textBounds = if (shape.autoFitMode == PptxAutoFitMode.SHAPE && totalHeight > baseTextBounds.height()) {
        baseTextBounds.copy(bottom = baseTextBounds.top + totalHeight)
    } else {
        baseTextBounds
    }
    var y = when (shape.verticalAnchor) {
        PptxVerticalAnchor.TOP -> textBounds.top
        PptxVerticalAnchor.MIDDLE -> textBounds.top + ((textBounds.height() - totalHeight) / 2f).coerceAtLeast(0f)
        PptxVerticalAnchor.BOTTOM -> textBounds.bottom - totalHeight.coerceAtMost(textBounds.height())
    }

    return prepared.mapNotNull { item ->
        y += item.paragraph.spaceBeforePt
        if (shape.autoFitMode != PptxAutoFitMode.SHAPE && y > textBounds.bottom) return@mapNotNull null
        val paragraphY = y
        val charBoxes = item.layout.charBoxesFor(item.text, textBounds.left, paragraphY)
            .map { rect -> rect.rotatedBounds(shape.bounds, shape.rotationDegrees) }
        y += item.layout.height + item.paragraph.spaceAfterPt
        LaidOutParagraph(
            text = item.text,
            layout = item.layout,
            x = textBounds.left,
            y = paragraphY,
            charBoxes = charBoxes
        )
    }
}

private fun prepareParagraphLayouts(
    shape: PptxShapeElement,
    textBounds: SharedPptxRect,
    fontScale: Float
): List<PreparedParagraphLayout> {
    val layoutWidth = textBounds.width().roundToInt().coerceAtLeast(1)
    return shape.paragraphs.mapNotNull { paragraph ->
        val text = paragraph.displayText()
        if (text.isBlank()) return@mapNotNull null
        val spannable = paragraph.toSpannable(text, fontScale)
        val layout = StaticLayout.Builder
            .obtain(spannable, 0, spannable.length, TextLayoutPaint, layoutWidth)
            .setAlignment(paragraph.alignment.toLayoutAlignment())
            .setIncludePad(true)
            .setLineSpacing(0f, paragraph.effectiveLineSpacing(shape))
            .build()
        PreparedParagraphLayout(paragraph, text, layout)
    }
}

private fun List<PreparedParagraphLayout>.totalHeight(): Float {
    return sumOf { item ->
        (item.paragraph.spaceBeforePt + item.layout.height + item.paragraph.spaceAfterPt).toDouble()
    }.toFloat()
}

private fun PptxShapeElement.textBounds(sourceBounds: SharedPptxRect = bounds): SharedPptxRect {
    return SharedPptxRect(
        sourceBounds.left + textInsets.left,
        sourceBounds.top + textInsets.top,
        sourceBounds.right - textInsets.right,
        sourceBounds.bottom - textInsets.bottom
    )
}

private fun PptxParagraph.toSpannable(displayText: String, fontScale: Float): SpannableStringBuilder {
    val builder = SpannableStringBuilder(displayText)
    val bulletPrefixLength = bullet?.takeIf { it.isNotBlank() }?.let { it.length + 1 } ?: 0
    val baseMargin = marginLeftPt ?: ((level * 18f) + if (bulletPrefixLength > 0) 18f else 0f)
    val firstMargin = (baseMargin + (indentPt ?: if (bulletPrefixLength > 0) -12f else 0f))
        .roundToInt()
        .coerceAtLeast(0)
    val restMargin = baseMargin.roundToInt().coerceAtLeast(0)
    if (firstMargin > 0 || restMargin > 0) {
        builder.setSpan(
            LeadingMarginSpan.Standard(firstMargin, restMargin),
            0,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    var offset = bulletPrefixLength
    runs.forEach { run ->
        val start = offset
        val end = (start + run.text.length).coerceAtMost(builder.length)
        if (start >= end) return@forEach
        builder.setSpan(
            AbsoluteSizeSpan(scaledTextSize(run.sizePt, fontScale), false),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        run.color?.let { color ->
            builder.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (run.bold || run.italic) {
            builder.setSpan(
                StyleSpan(
                    when {
                        run.bold && run.italic -> Typeface.BOLD_ITALIC
                        run.bold -> Typeface.BOLD
                        else -> Typeface.ITALIC
                    }
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        run.typeface?.takeIf { it.isNotBlank() && !it.startsWith("+") }?.let { family ->
            builder.setSpan(TypefaceSpan(family), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        when {
            run.baseline > 0.05f -> {
                builder.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            run.baseline < -0.05f -> {
                builder.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        offset = end
    }

    if (bulletPrefixLength > 0) {
        val firstRun = runs.firstOrNull()
        builder.setSpan(
            AbsoluteSizeSpan(scaledTextSize(firstRun?.sizePt, fontScale), false),
            0,
            bulletPrefixLength,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        firstRun?.color?.let { color ->
            builder.setSpan(ForegroundColorSpan(color), 0, bulletPrefixLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return builder
}

private fun PptxTextAlign.toLayoutAlignment(): Layout.Alignment {
    return when (this) {
        PptxTextAlign.START -> Layout.Alignment.ALIGN_NORMAL
        PptxTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        PptxTextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

private fun StaticLayout.charBoxesFor(text: String, originX: Float, originY: Float): List<RectF> {
    if (text.isEmpty()) return emptyList()
    return text.indices.map { index ->
        val line = getLineForOffset(index.coerceIn(0, text.length))
        val nextOffset = (index + 1).coerceAtMost(text.length)
        val left = runCatching { getPrimaryHorizontal(index) }.getOrDefault(getLineLeft(line))
        val right = runCatching { getPrimaryHorizontal(nextOffset) }.getOrDefault(left)
        val minX = min(left, right)
        val maxX = max(left, right).let { if (it == minX) it + 0.5f else it }
        RectF(
            originX + minX,
            originY + getLineTop(line),
            originX + maxX,
            originY + getLineBottom(line)
        )
    }
}

private fun RectF.rotatedBounds(bounds: SharedPptxRect, rotationDegrees: Float): RectF {
    if (rotationDegrees == 0f) return this
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    val cx = bounds.centerX()
    val cy = bounds.centerY()
    val points = arrayOf(
        left to top,
        right to top,
        right to bottom,
        left to bottom
    ).map { (x, y) ->
        val dx = x - cx
        val dy = y - cy
        PointF(cx + dx * cosValue - dy * sinValue, cy + dx * sinValue + dy * cosValue)
    }
    return RectF(
        points.minOf { it.x },
        points.minOf { it.y },
        points.maxOf { it.x },
        points.maxOf { it.y }
    )
}

private fun layoutTableCells(table: PptxTableElement): List<LaidOutTableCell> {
    if (table.rows.isEmpty() || table.bounds.width() <= 0f || table.bounds.height() <= 0f) return emptyList()
    val explicitHeight = table.rows
        .mapNotNull { row -> row.heightPoint?.takeIf { h -> h > 0f } }
        .sumOf { it.toDouble() }
        .toFloat()
    val missingRows = table.rows.count { row -> val h = row.heightPoint; h == null || h <= 0f }
    val fallbackHeight = if (missingRows > 0) {
        ((table.bounds.height() - explicitHeight).coerceAtLeast(1f)) / missingRows
    } else {
        table.bounds.height() / table.rows.size
    }

    val cells = mutableListOf<LaidOutTableCell>()
    var y = table.bounds.top
    table.rows.forEach { row ->
        val rowHeight = row.heightPoint?.takeIf { it > 0f } ?: fallbackHeight
        val explicitWidth = row.cells
            .mapNotNull { cell -> cell.widthPoint?.takeIf { w -> w > 0f } }
            .sumOf { it.toDouble() }
            .toFloat()
        val missingCells = row.cells.count { cell -> val w = cell.widthPoint; w == null || w <= 0f }
        val fallbackWidth = if (missingCells > 0) {
            ((table.bounds.width() - explicitWidth).coerceAtLeast(1f)) / missingCells
        } else if (row.cells.isNotEmpty()) {
            table.bounds.width() / row.cells.size
        } else {
            table.bounds.width()
        }
        var x = table.bounds.left
        row.cells.forEach { cell ->
            val cellWidth = cell.widthPoint?.takeIf { it > 0f } ?: fallbackWidth
            cells += LaidOutTableCell(
                rect = SharedPptxRect(x, y, x + cellWidth, y + rowHeight),
                cell = cell
            )
            x += cellWidth
        }
        y += rowHeight
    }
    return cells
}

private inline fun Canvas.withRotation(bounds: SharedPptxRect, rotationDegrees: Float, block: () -> Unit) {
    withSave {
        try {
            if (rotationDegrees != 0f) {
                rotate(rotationDegrees, bounds.centerX(), bounds.centerY())
            }
            block()
        } finally {
        }
    }
}

private fun drawPresetShape(
    canvas: Canvas,
    bounds: SharedPptxRect,
    preset: String,
    customGeometry: PptxCustomGeometry?,
    paint: Paint
) {
    if (customGeometry != null && preset != "line") {
        canvas.drawPath(customGeometry.toPath(bounds), paint)
        return
    }
    val rect = bounds.toRectF()
    when (preset) {
        "line" -> canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
        "ellipse" -> canvas.drawOval(rect, paint)
        "roundrect", "roundRect" -> canvas.drawRoundRect(rect, rect.width() * 0.08f, rect.height() * 0.08f, paint)
        "triangle" -> canvas.drawPath(Path().apply {
            moveTo(rect.centerX(), rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.left, rect.bottom)
            close()
        }, paint)
        "diamond" -> canvas.drawPath(Path().apply {
            moveTo(rect.centerX(), rect.top)
            lineTo(rect.right, rect.centerY())
            lineTo(rect.centerX(), rect.bottom)
            lineTo(rect.left, rect.centerY())
            close()
        }, paint)
        else -> canvas.drawRect(rect, paint)
    }
}

private fun PptxGradientFill.toShader(bounds: SharedPptxRect): Shader {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = cos(radians).toFloat() * bounds.width()
    val dy = sin(radians).toFloat() * bounds.height()
    return LinearGradient(
        bounds.centerX() - dx / 2f,
        bounds.centerY() - dy / 2f,
        bounds.centerX() + dx / 2f,
        bounds.centerY() + dy / 2f,
        startColor,
        endColor,
        Shader.TileMode.CLAMP
    )
}

private fun scaledTextSize(sizePt: Float?, fontScale: Float): Int {
    return ((sizePt ?: DEFAULT_TEXT_SIZE_PT) * fontScale.coerceIn(0.4f, 2f))
        .roundToInt()
        .coerceAtLeast(1)
}

internal fun PptxImageCrop.sourceRect(width: Int, height: Int): Rect {
    val leftPx = (width * left).roundToInt().coerceIn(0, width - 1)
    val topPx = (height * top).roundToInt().coerceIn(0, height - 1)
    val rightPx = (width * (1f - right)).roundToInt().coerceIn(leftPx + 1, width)
    val bottomPx = (height * (1f - bottom)).roundToInt().coerceIn(topPx + 1, height)
    return Rect(leftPx, topPx, rightPx, bottomPx)
}
