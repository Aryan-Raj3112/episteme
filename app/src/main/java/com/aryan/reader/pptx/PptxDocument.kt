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
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation

private const val EMU_PER_POINT = 12_700f
private const val DEFAULT_TEXT_SIZE_PT = 18f
private const val DEFAULT_TEXT_MARGIN_PT = 91_440f / EMU_PER_POINT
private const val DEFAULT_LINE_SPACING_MULTIPLE = 1.0f

internal data class PptxDeck(
    val widthPoint: Int,
    val heightPoint: Int,
    val slides: List<PptxSlide>
)

internal data class PptxSlide(
    val widthPoint: Int,
    val heightPoint: Int,
    val backgroundColor: Int?,
    val elements: List<PptxElement>,
    val text: String,
    val charBoxes: List<PptxCharBox>
)

internal data class PptxCharBox(
    val char: Char,
    val bounds: RectF
)

internal sealed interface PptxElement {
    val bounds: RectF
}

internal data class PptxShapeElement(
    override val bounds: RectF,
    val preset: String,
    val fillColor: Int?,
    val gradientFill: PptxGradientFill? = null,
    val lineColor: Int?,
    val lineWidthPoint: Float,
    val paragraphs: List<PptxParagraph>,
    val hyperlink: String?,
    val textInsets: PptxTextInsets = PptxTextInsets(),
    val verticalAnchor: PptxVerticalAnchor = PptxVerticalAnchor.TOP,
    val rotationDegrees: Float = 0f,
    val renderText: Boolean = true,
    val fontScale: Float = 1f,
    val lineSpacingReduction: Float = 0f,
    val autoFitMode: PptxAutoFitMode = PptxAutoFitMode.NONE,
    val customGeometry: PptxCustomGeometry? = null
) : PptxElement

internal data class PptxImageElement(
    override val bounds: RectF,
    val bytes: ByteArray,
    val contentType: String?,
    val crop: PptxImageCrop = PptxImageCrop(),
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
) : PptxElement

internal data class PptxImageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

internal data class PptxTableElement(
    override val bounds: RectF,
    val rows: List<PptxTableRow>,
    val rotationDegrees: Float = 0f
) : PptxElement

internal data class PptxTableRow(
    val heightPoint: Float?,
    val cells: List<PptxTableCell>
)

internal data class PptxTableCell(
    val widthPoint: Float?,
    val fillColor: Int?,
    val lineColor: Int?,
    val paragraphs: List<PptxParagraph>,
    val textInsets: PptxTextInsets = PptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f),
    val verticalAnchor: PptxVerticalAnchor = PptxVerticalAnchor.TOP
)

internal data class PptxParagraph(
    val runs: List<PptxTextRun>,
    val alignment: PptxTextAlign = PptxTextAlign.START,
    val bullet: String? = null,
    val level: Int = 0,
    val marginLeftPt: Float? = null,
    val indentPt: Float? = null,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    val lineSpacingMultiple: Float = DEFAULT_LINE_SPACING_MULTIPLE,
    val alignmentExplicit: Boolean = false,
    val bulletExplicit: Boolean = false,
    val spaceBeforeExplicit: Boolean = false,
    val spaceAfterExplicit: Boolean = false,
    val lineSpacingExplicit: Boolean = false
)

internal data class PptxTextRun(
    val text: String,
    val sizePt: Float? = null,
    val color: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val typeface: String? = null,
    val baseline: Float = 0f,
    val sizeExplicit: Boolean = false,
    val colorExplicit: Boolean = false,
    val boldExplicit: Boolean = false,
    val italicExplicit: Boolean = false,
    val typefaceExplicit: Boolean = false,
    val baselineExplicit: Boolean = false
)

internal enum class PptxTextAlign {
    START,
    CENTER,
    END
}

internal enum class PptxVerticalAnchor {
    TOP,
    MIDDLE,
    BOTTOM
}

internal enum class PptxAutoFitMode {
    NONE,
    NORMAL,
    SHAPE
}

internal data class PptxTextInsets(
    val left: Float = DEFAULT_TEXT_MARGIN_PT,
    val top: Float = DEFAULT_TEXT_MARGIN_PT,
    val right: Float = DEFAULT_TEXT_MARGIN_PT,
    val bottom: Float = DEFAULT_TEXT_MARGIN_PT
)

internal data class PptxGradientFill(
    val startColor: Int,
    val endColor: Int,
    val angleDegrees: Float = 0f
)

internal data class PptxCustomGeometry(
    val width: Float,
    val height: Float,
    val commands: List<PptxPathCommand>
) {
    fun toPath(bounds: RectF): Path {
        val scaleX = bounds.width() / width.coerceAtLeast(1f)
        val scaleY = bounds.height() / height.coerceAtLeast(1f)
        fun x(value: Float) = bounds.left + value * scaleX
        fun y(value: Float) = bounds.top + value * scaleY
        return Path().apply {
            commands.forEach { command ->
                when (command) {
                    is PptxPathCommand.MoveTo -> moveTo(x(command.x), y(command.y))
                    is PptxPathCommand.LineTo -> lineTo(x(command.x), y(command.y))
                    is PptxPathCommand.QuadTo -> quadTo(
                        x(command.x1),
                        y(command.y1),
                        x(command.x2),
                        y(command.y2)
                    )
                    is PptxPathCommand.CubicTo -> cubicTo(
                        x(command.x1),
                        y(command.y1),
                        x(command.x2),
                        y(command.y2),
                        x(command.x3),
                        y(command.y3)
                    )
                    PptxPathCommand.Close -> close()
                }
            }
        }
    }
}

internal sealed interface PptxPathCommand {
    data class MoveTo(val x: Float, val y: Float) : PptxPathCommand
    data class LineTo(val x: Float, val y: Float) : PptxPathCommand
    data class QuadTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : PptxPathCommand
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x3: Float,
        val y3: Float
    ) : PptxPathCommand
    object Close : PptxPathCommand
}

internal object PptxDeckCache {
    fun load(file: File): PptxDeck = PptxDocumentParser.parse(file)
}

internal object PptxDocumentParser {
    fun parse(file: File): PptxDeck = SharedPptxDeckCache.load(file).toAndroidPptxDeck()
}

private fun SharedPptxDeck.toAndroidPptxDeck(): PptxDeck {
    return PptxDeck(
        widthPoint = widthPoint,
        heightPoint = heightPoint,
        slides = slides.map { it.toAndroidPptxSlide() }
    )
}

private fun SharedPptxSlide.toAndroidPptxSlide(): PptxSlide {
    return PptxSlide(
        widthPoint = widthPoint,
        heightPoint = heightPoint,
        backgroundColor = backgroundColor,
        elements = elements.map { it.toAndroidPptxElement() },
        text = text,
        charBoxes = charBoxes.map { it.toAndroidPptxCharBox() }
    )
}

private fun SharedPptxCharBox.toAndroidPptxCharBox(): PptxCharBox {
    return PptxCharBox(char = char, bounds = bounds.toRectF())
}

private fun SharedPptxElement.toAndroidPptxElement(): PptxElement {
    return when (this) {
        is SharedPptxShapeElement -> PptxShapeElement(
            bounds = bounds.toRectF(),
            preset = preset,
            fillColor = fillColor,
            gradientFill = gradientFill?.toAndroidPptxGradientFill(),
            lineColor = lineColor,
            lineWidthPoint = lineWidthPoint,
            paragraphs = paragraphs.map { it.toAndroidPptxParagraph() },
            hyperlink = hyperlink,
            textInsets = textInsets.toAndroidPptxTextInsets(),
            verticalAnchor = verticalAnchor.toAndroidPptxVerticalAnchor(),
            rotationDegrees = rotationDegrees,
            renderText = renderText,
            fontScale = fontScale,
            lineSpacingReduction = lineSpacingReduction,
            autoFitMode = autoFitMode.toAndroidPptxAutoFitMode(),
            customGeometry = customGeometry?.toAndroidPptxCustomGeometry()
        )
        is SharedPptxImageElement -> PptxImageElement(
            bounds = bounds.toRectF(),
            bytes = bytes,
            contentType = contentType,
            crop = crop.toAndroidPptxImageCrop(),
            rotationDegrees = rotationDegrees,
            opacity = opacity
        )
        is SharedPptxTableElement -> PptxTableElement(
            bounds = bounds.toRectF(),
            rows = rows.map { it.toAndroidPptxTableRow() },
            rotationDegrees = rotationDegrees
        )
    }
}

private fun SharedPptxRect.toRectF(): RectF = RectF(left, top, right, bottom)

private fun SharedPptxImageCrop.toAndroidPptxImageCrop(): PptxImageCrop {
    return PptxImageCrop(left = left, top = top, right = right, bottom = bottom)
}

private fun SharedPptxTableRow.toAndroidPptxTableRow(): PptxTableRow {
    return PptxTableRow(
        heightPoint = heightPoint,
        cells = cells.map { it.toAndroidPptxTableCell() }
    )
}

private fun SharedPptxTableCell.toAndroidPptxTableCell(): PptxTableCell {
    return PptxTableCell(
        widthPoint = widthPoint,
        fillColor = fillColor,
        lineColor = lineColor,
        paragraphs = paragraphs.map { it.toAndroidPptxParagraph() },
        textInsets = textInsets.toAndroidPptxTextInsets(),
        verticalAnchor = verticalAnchor.toAndroidPptxVerticalAnchor()
    )
}

private fun SharedPptxParagraph.toAndroidPptxParagraph(): PptxParagraph {
    return PptxParagraph(
        runs = runs.map { it.toAndroidPptxTextRun() },
        alignment = alignment.toAndroidPptxTextAlign(),
        bullet = bullet,
        level = level,
        marginLeftPt = marginLeftPt,
        indentPt = indentPt,
        spaceBeforePt = spaceBeforePt,
        spaceAfterPt = spaceAfterPt,
        lineSpacingMultiple = lineSpacingMultiple,
        alignmentExplicit = alignmentExplicit,
        bulletExplicit = bulletExplicit,
        spaceBeforeExplicit = spaceBeforeExplicit,
        spaceAfterExplicit = spaceAfterExplicit,
        lineSpacingExplicit = lineSpacingExplicit
    )
}

private fun SharedPptxTextRun.toAndroidPptxTextRun(): PptxTextRun {
    return PptxTextRun(
        text = text,
        sizePt = sizePt,
        color = color,
        bold = bold,
        italic = italic,
        typeface = typeface,
        baseline = baseline,
        sizeExplicit = sizeExplicit,
        colorExplicit = colorExplicit,
        boldExplicit = boldExplicit,
        italicExplicit = italicExplicit,
        typefaceExplicit = typefaceExplicit,
        baselineExplicit = baselineExplicit
    )
}

private fun SharedPptxTextAlign.toAndroidPptxTextAlign(): PptxTextAlign {
    return when (this) {
        SharedPptxTextAlign.START -> PptxTextAlign.START
        SharedPptxTextAlign.CENTER -> PptxTextAlign.CENTER
        SharedPptxTextAlign.END -> PptxTextAlign.END
    }
}

private fun SharedPptxVerticalAnchor.toAndroidPptxVerticalAnchor(): PptxVerticalAnchor {
    return when (this) {
        SharedPptxVerticalAnchor.TOP -> PptxVerticalAnchor.TOP
        SharedPptxVerticalAnchor.MIDDLE -> PptxVerticalAnchor.MIDDLE
        SharedPptxVerticalAnchor.BOTTOM -> PptxVerticalAnchor.BOTTOM
    }
}

private fun SharedPptxAutoFitMode.toAndroidPptxAutoFitMode(): PptxAutoFitMode {
    return when (this) {
        SharedPptxAutoFitMode.NONE -> PptxAutoFitMode.NONE
        SharedPptxAutoFitMode.NORMAL -> PptxAutoFitMode.NORMAL
        SharedPptxAutoFitMode.SHAPE -> PptxAutoFitMode.SHAPE
    }
}

private fun SharedPptxTextInsets.toAndroidPptxTextInsets(): PptxTextInsets {
    return PptxTextInsets(left = left, top = top, right = right, bottom = bottom)
}

private fun SharedPptxGradientFill.toAndroidPptxGradientFill(): PptxGradientFill {
    return PptxGradientFill(startColor = startColor, endColor = endColor, angleDegrees = angleDegrees)
}

private fun SharedPptxCustomGeometry.toAndroidPptxCustomGeometry(): PptxCustomGeometry {
    return PptxCustomGeometry(
        width = width,
        height = height,
        commands = commands.map { it.toAndroidPptxPathCommand() }
    )
}

private fun SharedPptxPathCommand.toAndroidPptxPathCommand(): PptxPathCommand {
    return when (this) {
        is SharedPptxPathCommand.MoveTo -> PptxPathCommand.MoveTo(x, y)
        is SharedPptxPathCommand.LineTo -> PptxPathCommand.LineTo(x, y)
        is SharedPptxPathCommand.QuadTo -> PptxPathCommand.QuadTo(x1, y1, x2, y2)
        is SharedPptxPathCommand.CubicTo -> PptxPathCommand.CubicTo(x1, y1, x2, y2, x3, y3)
        SharedPptxPathCommand.Close -> PptxPathCommand.Close
    }
}

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
            ReaderLink(uri = link, destPageIdx = null, bounds = RectF(shape.bounds))
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
                        acc?.apply { union(box.bounds) } ?: RectF(box.bounds)
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
            expanded.set(box.bounds)
            expanded.inset(-xTolerance.toFloat(), -yTolerance.toFloat())
            if (expanded.contains(pointX, pointY)) return index
        }
        return -1
    }

    override suspend fun textPageGetCharBox(index: Int): RectF? {
        return slide.charBoxes.getOrNull(index)?.bounds?.let(::RectF)
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
            appContext.contentResolver.openInputStream(uri)?.use { input ->
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
    val rect: RectF,
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
            if (shape.gradientFill != null || (shape.fillColor != null && Color.alpha(shape.fillColor) > 0)) {
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
                clip?.let { clipRect(it) }
                translate(paragraph.x, paragraph.y)
                paragraph.layout.draw(this)
            }
        }
    }

    private fun drawTable(canvas: Canvas, table: PptxTableElement) {
        canvas.withRotation(table.bounds, table.rotationDegrees) {
            layoutTableCells(table).forEach { laidOutCell ->
                val rect = laidOutCell.rect
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
                    image.bounds,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = (255f * image.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
                    }
                )
                bitmap.recycle()
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.LTGRAY
                }
                canvas.drawRect(image.bounds, paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.GRAY
                paint.strokeWidth = 0.75f
                canvas.drawRect(image.bounds, paint)
            }
        }
    }
}

private fun layoutParagraphs(shape: PptxShapeElement, bounds: RectF): List<LaidOutParagraph> {
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
        RectF(baseTextBounds).apply { bottom = top + totalHeight }
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
    textBounds: RectF,
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

private fun PptxParagraph.effectiveLineSpacing(shape: PptxShapeElement): Float {
    val reduced = lineSpacingMultiple * (1f - shape.lineSpacingReduction)
    return reduced.coerceIn(0.9f, 2.5f)
}

private fun PptxShapeElement.textBounds(sourceBounds: RectF = bounds): RectF {
    return RectF(
        sourceBounds.left + textInsets.left,
        sourceBounds.top + textInsets.top,
        sourceBounds.right - textInsets.right,
        sourceBounds.bottom - textInsets.bottom
    )
}

private fun PptxParagraph.displayText(): String {
    val text = runs.joinToString("") { it.text }
    val prefix = bullet?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return prefix + text
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

private fun RectF.rotatedBounds(bounds: RectF, rotationDegrees: Float): RectF {
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
        .mapNotNull { it.heightPoint?.takeIf { h -> h > 0f } }
        .sumOf { it.toDouble() }
        .toFloat()
    val missingRows = table.rows.count { it.heightPoint == null || it.heightPoint <= 0f }
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
            .mapNotNull { it.widthPoint?.takeIf { w -> w > 0f } }
            .sumOf { it.toDouble() }
            .toFloat()
        val missingCells = row.cells.count { it.widthPoint == null || it.widthPoint <= 0f }
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
                rect = RectF(x, y, x + cellWidth, y + rowHeight),
                cell = cell
            )
            x += cellWidth
        }
        y += rowHeight
    }
    return cells
}

private inline fun Canvas.withRotation(bounds: RectF, rotationDegrees: Float, block: () -> Unit) {
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
    bounds: RectF,
    preset: String,
    customGeometry: PptxCustomGeometry?,
    paint: Paint
) {
    if (customGeometry != null && preset != "line") {
        canvas.drawPath(customGeometry.toPath(bounds), paint)
        return
    }
    when (preset) {
        "line" -> canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
        "ellipse" -> canvas.drawOval(bounds, paint)
        "roundrect", "roundRect" -> canvas.drawRoundRect(bounds, bounds.width() * 0.08f, bounds.height() * 0.08f, paint)
        "triangle" -> canvas.drawPath(Path().apply {
            moveTo(bounds.centerX(), bounds.top)
            lineTo(bounds.right, bounds.bottom)
            lineTo(bounds.left, bounds.bottom)
            close()
        }, paint)
        "diamond" -> canvas.drawPath(Path().apply {
            moveTo(bounds.centerX(), bounds.top)
            lineTo(bounds.right, bounds.centerY())
            lineTo(bounds.centerX(), bounds.bottom)
            lineTo(bounds.left, bounds.centerY())
            close()
        }, paint)
        else -> canvas.drawRect(bounds, paint)
    }
}

private fun PptxGradientFill.toShader(bounds: RectF): Shader {
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

private fun PptxImageCrop.sourceRect(width: Int, height: Int): Rect {
    val leftPx = (width * left).roundToInt().coerceIn(0, width - 1)
    val topPx = (height * top).roundToInt().coerceIn(0, height - 1)
    val rightPx = (width * (1f - right)).roundToInt().coerceIn(leftPx + 1, width)
    val bottomPx = (height * (1f - bottom)).roundToInt().coerceIn(topPx + 1, height)
    return Rect(leftPx, topPx, rightPx, bottomPx)
}
