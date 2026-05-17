package com.aryan.reader.desktop

import com.aryan.reader.shared.pptx.SharedPptxCharBox as DesktopPptxCharBox
import com.aryan.reader.shared.pptx.SharedPptxDeck as DesktopPptxDeck
import com.aryan.reader.shared.pptx.SharedPptxDeckCache
import com.aryan.reader.shared.pptx.SharedPptxImageCrop as DesktopPptxImageCrop
import com.aryan.reader.shared.pptx.SharedPptxImageElement as DesktopPptxImageElement
import com.aryan.reader.shared.pptx.SharedPptxParagraph as DesktopPptxParagraph
import com.aryan.reader.shared.pptx.SharedPptxRect as DesktopPptxRect
import com.aryan.reader.shared.pptx.SharedPptxShapeElement as DesktopPptxShapeElement
import com.aryan.reader.shared.pptx.SharedPptxSlide as DesktopPptxSlide
import com.aryan.reader.shared.pptx.SharedPptxTableCell as DesktopPptxTableCell
import com.aryan.reader.shared.pptx.SharedPptxTableElement as DesktopPptxTableElement
import com.aryan.reader.shared.pptx.SharedPptxTextAlign as DesktopPptxTextAlign
import com.aryan.reader.shared.pptx.SharedPptxTextInsets as DesktopPptxTextInsets
import com.aryan.reader.shared.pptx.SharedPptxVerticalAnchor as DesktopPptxVerticalAnchor
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val EmuPerPoint = 12_700f
private const val DefaultTextSizePoint = 18f
private const val DefaultTextMarginPoint = 91_440f / EmuPerPoint

private val PptxWhite = pptxRgb(255, 255, 255)
private val PptxBlack = pptxRgb(0, 0, 0)
private val PptxLightGray = pptxRgb(245, 246, 248)
private val PptxGray = pptxRgb(128, 128, 128)

internal class DesktopPptxDocument private constructor(
    val path: String,
    val title: String,
    private val deck: DesktopPptxDeck
) {
    val pageCount: Int = deck.slides.size
    val pageSizes: List<DesktopPdfPageSize> = deck.slides.map { slide ->
        DesktopPdfPageSize(slide.widthPoint.toFloat(), slide.heightPoint.toFloat())
    }

    fun renderPageBufferedImage(pageIndex: Int, scale: Float): BufferedImage {
        val slide = slideAt(pageIndex)
        return DesktopPptxRenderer.render(slide, scale)
    }

    fun textOnlyPage(pageIndex: Int): String {
        return deck.slides.getOrNull(pageIndex)?.text.orEmpty()
    }

    fun textPageData(pageIndex: Int): DesktopPdfTextPageData {
        val slide = deck.slides.getOrNull(pageIndex) ?: return DesktopPdfTextPageData()
        return DesktopPdfTextPageData(
            text = slide.text,
            chars = slide.charBoxes.mapIndexed { index, box ->
                DesktopPdfTextChar(
                    index = index,
                    char = box.char,
                    left = (box.bounds.left / slide.widthPoint).coerceIn(0f, 1f),
                    top = (box.bounds.top / slide.heightPoint).coerceIn(0f, 1f),
                    right = (box.bounds.right / slide.widthPoint).coerceIn(0f, 1f),
                    bottom = (box.bounds.bottom / slide.heightPoint).coerceIn(0f, 1f)
                )
            }
        )
    }

    fun linkAt(pageIndex: Int, normalizedX: Float, normalizedY: Float): DesktopPdfLinkTarget? {
        val slide = deck.slides.getOrNull(pageIndex) ?: return null
        val pointX = normalizedX.coerceIn(0f, 1f) * slide.widthPoint
        val pointY = normalizedY.coerceIn(0f, 1f) * slide.heightPoint
        return slide.elements
            .asReversed()
            .filterIsInstance<DesktopPptxShapeElement>()
            .firstNotNullOfOrNull { shape ->
                val link = shape.hyperlink?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
                link.takeIf { shape.bounds.rotatedBounds(shape.bounds, shape.rotationDegrees).contains(pointX, pointY) }
            }
            ?.let { DesktopPdfLinkTarget(uri = it) }
    }

    fun charIndexAt(pageIndex: Int, normalizedX: Float, normalizedY: Float, tolerance: Float): Int? {
        val slide = deck.slides.getOrNull(pageIndex) ?: return null
        val pointX = normalizedX.coerceIn(0f, 1f) * slide.widthPoint
        val pointY = normalizedY.coerceIn(0f, 1f) * slide.heightPoint
        val toleranceX = slide.widthPoint * tolerance
        val toleranceY = slide.heightPoint * tolerance
        slide.charBoxes.forEachIndexed { index, box ->
            if (box.bounds.expanded(toleranceX, toleranceY).contains(pointX, pointY)) {
                return index
            }
        }
        return slide.charBoxes
            .mapIndexedNotNull { index, box ->
                if (pointY < box.bounds.top - toleranceY || pointY > box.bounds.bottom + toleranceY) {
                    null
                } else {
                    index to abs(pointX - box.bounds.centerX())
                }
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= toleranceX * 3f }
            ?.first
    }

    fun textRectsForRange(pageIndex: Int, startIndex: Int, endIndex: Int): List<DesktopPdfTextRect> {
        val slide = deck.slides.getOrNull(pageIndex) ?: return emptyList()
        if (slide.charBoxes.isEmpty()) return emptyList()
        val first = min(startIndex, endIndex).coerceIn(0, slide.charBoxes.size)
        val lastExclusive = (max(startIndex, endIndex) + 1).coerceIn(first, slide.charBoxes.size)
        return slide.charBoxes
            .subList(first, lastExclusive)
            .filterNot { it.char.isWhitespace() }
            .groupBy { it.bounds.top.roundToInt() }
            .values
            .mapNotNull { boxes ->
                boxes.fold<DesktopPptxCharBox, DesktopPptxRect?>(null) { acc, box ->
                    acc?.union(box.bounds) ?: box.bounds
                }
            }
            .map { rect ->
                DesktopPdfTextRect(
                    left = (rect.left / slide.widthPoint).coerceIn(0f, 1f),
                    top = (rect.top / slide.heightPoint).coerceIn(0f, 1f),
                    right = (rect.right / slide.widthPoint).coerceIn(0f, 1f),
                    bottom = (rect.bottom / slide.heightPoint).coerceIn(0f, 1f)
                )
            }
    }

    fun close() = Unit

    private fun slideAt(pageIndex: Int): DesktopPptxSlide {
        return deck.slides.getOrNull(pageIndex) ?: error("Invalid PPTX slide index $pageIndex.")
    }

    companion object {
        fun load(file: File): DesktopPptxDocument {
            require(file.isFile) { "Missing PPTX file: ${file.absolutePath}" }
            val deck = DesktopPptxDeckCache.load(file)
            return DesktopPptxDocument(
                path = file.absolutePath,
                title = file.nameWithoutExtension,
                deck = deck
            )
        }
    }
}

internal object DesktopPptxDocuments {
    fun load(file: File): DesktopPptxDocument = DesktopPptxDocument.load(file)
}

private fun DesktopPptxRect.inset(insets: DesktopPptxTextInsets): DesktopPptxRect {
    return DesktopPptxRect(
        left = left + insets.left,
        top = top + insets.top,
        right = right - insets.right,
        bottom = bottom - insets.bottom
    )
}

private fun DesktopPptxRect.toAwtRect(): Rectangle2D.Float {
    return Rectangle2D.Float(left, top, width(), height())
}

private fun DesktopPptxRect.rotatedBounds(rotationBounds: DesktopPptxRect, rotationDegrees: Float): DesktopPptxRect {
    if (rotationDegrees == 0f) return this
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    val cx = rotationBounds.centerX()
    val cy = rotationBounds.centerY()
    val points = arrayOf(
        left to top,
        right to top,
        right to bottom,
        left to bottom
    ).map { (x, y) ->
        val dx = x - cx
        val dy = y - cy
        (cx + dx * cosValue - dy * sinValue) to (cy + dx * sinValue + dy * cosValue)
    }
    return DesktopPptxRect(
        left = points.minOf { it.first },
        top = points.minOf { it.second },
        right = points.maxOf { it.first },
        bottom = points.maxOf { it.second }
    )
}

private object DesktopPptxDeckCache {
    fun load(file: File): DesktopPptxDeck = SharedPptxDeckCache.load(file)
}

private data class DesktopPptxLaidOutLine(
    val text: String,
    val x: Float,
    val baselineY: Float,
    val font: Font,
    val color: Int,
    val charBoxes: List<DesktopPptxCharBox>
) {
    fun newlineBounds(): DesktopPptxRect {
        val last = charBoxes.lastOrNull()?.bounds
        return if (last == null) {
            DesktopPptxRect(x, baselineY, x, baselineY)
        } else {
            DesktopPptxRect(last.right, last.top, last.right, last.bottom)
        }
    }
}

private object DesktopPptxTextLayout {
    fun layout(shape: DesktopPptxShapeElement): List<DesktopPptxLaidOutLine> {
        if (!shape.renderText || shape.paragraphs.isEmpty()) return emptyList()
        val textBounds = shape.textBounds()
        if (textBounds.width() <= 0f || textBounds.height() <= 0f) return emptyList()
        val measured = withMeasureGraphics { graphics ->
            val paragraphs = shape.paragraphs.mapNotNull { paragraph ->
                val text = paragraph.displayText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val font = paragraph.font(shape)
                graphics.font = font
                val metrics = graphics.fontMetrics
                val lines = text.split('\n').flatMap { rawLine ->
                    wrapLine(rawLine, textBounds.width()) { value -> metrics.stringWidth(value) }
                }.ifEmpty { listOf("") }
                PreparedPptxParagraph(
                    paragraph = paragraph,
                    lines = lines,
                    font = font,
                    color = paragraph.runs.firstOrNull()?.color ?: PptxBlack,
                    ascent = metrics.ascent.toFloat(),
                    lineHeight = metrics.height.toFloat().coerceAtLeast(1f)
                )
            }
            val totalHeight = paragraphs.sumOf { item ->
                (item.paragraph.spaceBeforePt + item.lines.size * item.lineHeight + item.paragraph.spaceAfterPt).toDouble()
            }.toFloat()
            paragraphs to totalHeight
        }
        val paragraphs = measured.first
        if (paragraphs.isEmpty()) return emptyList()
        val totalHeight = measured.second
        var top = when (shape.verticalAnchor) {
            DesktopPptxVerticalAnchor.TOP -> textBounds.top
            DesktopPptxVerticalAnchor.MIDDLE -> textBounds.top + ((textBounds.height() - totalHeight) / 2f).coerceAtLeast(0f)
            DesktopPptxVerticalAnchor.BOTTOM -> textBounds.bottom - totalHeight.coerceAtMost(textBounds.height())
        }
        val lines = mutableListOf<DesktopPptxLaidOutLine>()
        withMeasureGraphics { graphics ->
            paragraphs.forEach { paragraph ->
                graphics.font = paragraph.font
                val metrics = graphics.fontMetrics
                top += paragraph.paragraph.spaceBeforePt
                paragraph.lines.forEach { text ->
                    val textWidth = metrics.stringWidth(text).toFloat()
                    val x = when (paragraph.paragraph.alignment) {
                        DesktopPptxTextAlign.START -> textBounds.left
                        DesktopPptxTextAlign.CENTER -> textBounds.left + ((textBounds.width() - textWidth) / 2f).coerceAtLeast(0f)
                        DesktopPptxTextAlign.END -> textBounds.right - textWidth
                    }
                    val baseline = top + paragraph.ascent
                    val boxes = text.charBoxes(
                        x = x,
                        top = top,
                        bottom = top + paragraph.lineHeight,
                        metrics = { char -> metrics.charWidth(char) },
                        rotationBounds = shape.bounds,
                        rotationDegrees = shape.rotationDegrees
                    )
                    lines += DesktopPptxLaidOutLine(
                        text = text,
                        x = x,
                        baselineY = baseline,
                        font = paragraph.font,
                        color = paragraph.color,
                        charBoxes = boxes
                    )
                    top += paragraph.lineHeight
                }
                top += paragraph.paragraph.spaceAfterPt
            }
        }
        return lines
    }

    private fun wrapLine(rawLine: String, maxWidth: Float, measure: (String) -> Int): List<String> {
        if (rawLine.isBlank()) return listOf(rawLine)
        if (measure(rawLine) <= maxWidth) return listOf(rawLine)
        val lines = mutableListOf<String>()
        var current = ""
        rawLine.split(Regex("(?<=\\s)|(?=\\s)")).forEach { token ->
            val candidate = current + token
            when {
                candidate.isBlank() -> current = candidate
                measure(candidate) <= maxWidth || current.isBlank() -> current = candidate
                else -> {
                    lines += current.trimEnd()
                    current = token.trimStart()
                }
            }
        }
        if (current.isNotBlank()) lines += current.trimEnd()
        return lines.ifEmpty { listOf(rawLine.take(1)) }
    }
}

private data class PreparedPptxParagraph(
    val paragraph: DesktopPptxParagraph,
    val lines: List<String>,
    val font: Font,
    val color: Int,
    val ascent: Float,
    val lineHeight: Float
)

private object DesktopPptxRenderer {
    fun render(slide: DesktopPptxSlide, scale: Float): BufferedImage {
        val safeScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val width = (slide.widthPoint * safeScale).roundToInt().coerceAtLeast(1)
        val height = (slide.heightPoint * safeScale).roundToInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.enablePptxRenderingHints()
            graphics.color = (slide.backgroundColor ?: PptxWhite).toAwtColor()
            graphics.fillRect(0, 0, width, height)
            graphics.scale(
                width.toDouble() / slide.widthPoint.toDouble().coerceAtLeast(1.0),
                height.toDouble() / slide.heightPoint.toDouble().coerceAtLeast(1.0)
            )
            slide.elements.forEach { element ->
                when (element) {
                    is DesktopPptxShapeElement -> graphics.drawShape(element)
                    is DesktopPptxImageElement -> graphics.drawImageElement(element)
                    is DesktopPptxTableElement -> graphics.drawTable(element)
                }
            }
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun Graphics2D.drawShape(shape: DesktopPptxShapeElement) {
        withRotation(shape.bounds, shape.rotationDegrees) {
            val geometry = shape.geometry()
            val fillColor = shape.fillColor
            if (shape.preset != "line" && fillColor != null && fillColor.pptxAlpha() > 0) {
                paint = fillColor.toAwtColor()
                fill(geometry)
            }
            val lineColor = shape.lineColor
            if (lineColor != null && lineColor.pptxAlpha() > 0) {
                color = lineColor.toAwtColor()
                stroke = BasicStroke(shape.lineWidthPoint.coerceAtLeast(0.25f))
                draw(geometry)
            }
            drawShapeText(shape)
        }
    }

    private fun Graphics2D.drawShapeText(shape: DesktopPptxShapeElement) {
        if (!shape.renderText) return
        val oldClip = clip
        clip = shape.textBounds().toAwtRect()
        try {
            DesktopPptxTextLayout.layout(shape).forEach { line ->
                font = line.font
                color = line.color.toAwtColor()
                drawString(line.text, line.x, line.baselineY)
            }
        } finally {
            clip = oldClip
        }
    }

    private fun Graphics2D.drawImageElement(image: DesktopPptxImageElement) {
        withRotation(image.bounds, image.rotationDegrees) {
            val source = ByteArrayInputStream(image.bytes).use { input -> ImageIO.read(input) }
            if (source == null) {
                drawImagePlaceholder(image.bounds)
                return@withRotation
            }
            val oldComposite = composite
            composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, image.opacity.coerceIn(0f, 1f))
            try {
                val sourceRect = image.crop.sourceRect(source.width, source.height)
                drawImage(
                    source,
                    image.bounds.left.roundToInt(),
                    image.bounds.top.roundToInt(),
                    image.bounds.right.roundToInt(),
                    image.bounds.bottom.roundToInt(),
                    sourceRect.left.roundToInt(),
                    sourceRect.top.roundToInt(),
                    sourceRect.right.roundToInt(),
                    sourceRect.bottom.roundToInt(),
                    null
                )
            } finally {
                composite = oldComposite
                source.flush()
            }
        }
    }

    private fun Graphics2D.drawTable(table: DesktopPptxTableElement) {
        withRotation(table.bounds, table.rotationDegrees) {
            layoutTableCells(table).forEach { laidOutCell ->
                val rect = laidOutCell.rect
                val cell = laidOutCell.cell
                cell.fillColor?.takeIf { it.pptxAlpha() > 0 }?.let {
                    paint = it.toAwtColor()
                    fill(rect.toAwtRect())
                }
                cell.lineColor?.takeIf { it.pptxAlpha() > 0 }?.let {
                    color = it.toAwtColor()
                    stroke = BasicStroke(0.5f)
                    draw(rect.toAwtRect())
                }
                drawShapeText(laidOutCell.asShape())
            }
        }
    }

    private fun Graphics2D.drawImagePlaceholder(bounds: DesktopPptxRect) {
        color = PptxLightGray.toAwtColor()
        fill(bounds.toAwtRect())
        color = PptxGray.toAwtColor()
        stroke = BasicStroke(0.75f)
        draw(bounds.toAwtRect())
    }

    private fun Graphics2D.withRotation(bounds: DesktopPptxRect, rotationDegrees: Float, block: Graphics2D.() -> Unit) {
        val oldTransform = transform
        try {
            if (rotationDegrees != 0f) {
                rotate(Math.toRadians(rotationDegrees.toDouble()), bounds.centerX().toDouble(), bounds.centerY().toDouble())
            }
            block()
        } finally {
            transform = oldTransform
        }
    }
}

private data class LaidOutDesktopPptxTableCell(
    val rect: DesktopPptxRect,
    val cell: DesktopPptxTableCell
)

private fun layoutTableCells(table: DesktopPptxTableElement): List<LaidOutDesktopPptxTableCell> {
    if (table.rows.isEmpty() || table.bounds.width() <= 0f || table.bounds.height() <= 0f) return emptyList()
    val explicitHeight = table.rows
        .mapNotNull { it.heightPoint?.takeIf { height -> height > 0f } }
        .sumOf { it.toDouble() }
        .toFloat()
    val missingRows = table.rows.count { row ->
        val heightPoint = row.heightPoint
        heightPoint == null || heightPoint <= 0f
    }
    val fallbackHeight = if (missingRows > 0) {
        ((table.bounds.height() - explicitHeight).coerceAtLeast(1f)) / missingRows
    } else {
        table.bounds.height() / table.rows.size
    }
    val cells = mutableListOf<LaidOutDesktopPptxTableCell>()
    var y = table.bounds.top
    table.rows.forEach { row ->
        val rowHeight = row.heightPoint?.takeIf { it > 0f } ?: fallbackHeight
        val explicitWidth = row.cells
            .mapNotNull { it.widthPoint?.takeIf { width -> width > 0f } }
            .sumOf { it.toDouble() }
            .toFloat()
        val missingCells = row.cells.count { cell ->
            val widthPoint = cell.widthPoint
            widthPoint == null || widthPoint <= 0f
        }
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
            cells += LaidOutDesktopPptxTableCell(
                rect = DesktopPptxRect(x, y, x + cellWidth, y + rowHeight),
                cell = cell
            )
            x += cellWidth
        }
        y += rowHeight
    }
    return cells
}

private fun LaidOutDesktopPptxTableCell.asShape(): DesktopPptxShapeElement {
    return DesktopPptxShapeElement(
        bounds = rect,
        preset = "rect",
        fillColor = cell.fillColor,
        lineColor = cell.lineColor,
        lineWidthPoint = 0.75f,
        paragraphs = cell.paragraphs,
        hyperlink = null,
        placeholderKey = null,
        textInsets = cell.textInsets,
        verticalAnchor = cell.verticalAnchor
    )
}

private fun DesktopPptxShapeElement.textBounds(): DesktopPptxRect {
    return bounds.inset(textInsets)
}

private fun DesktopPptxShapeElement.geometry(): Shape {
    val shapeBounds = bounds
    val rect = shapeBounds.toAwtRect()
    return when (preset) {
        "line" -> Line2D.Float(shapeBounds.left, shapeBounds.top, shapeBounds.right, shapeBounds.bottom)
        "ellipse" -> Ellipse2D.Float(shapeBounds.left, shapeBounds.top, shapeBounds.width(), shapeBounds.height())
        "roundrect", "roundRect" -> RoundRectangle2D.Float(
            shapeBounds.left,
            shapeBounds.top,
            shapeBounds.width(),
            shapeBounds.height(),
            shapeBounds.width() * 0.16f,
            shapeBounds.height() * 0.16f
        )
        "triangle" -> Path2D.Float().apply {
            moveTo(shapeBounds.centerX(), shapeBounds.top)
            lineTo(shapeBounds.right, shapeBounds.bottom)
            lineTo(shapeBounds.left, shapeBounds.bottom)
            closePath()
        }
        "diamond" -> Path2D.Float().apply {
            moveTo(shapeBounds.centerX(), shapeBounds.top)
            lineTo(shapeBounds.right, shapeBounds.centerY())
            lineTo(shapeBounds.centerX(), shapeBounds.bottom)
            lineTo(shapeBounds.left, shapeBounds.centerY())
            closePath()
        }
        else -> rect
    }
}

private fun DesktopPptxParagraph.displayText(): String {
    val text = runs.joinToString("") { it.text }
    val prefix = bullet?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return prefix + text
}

private fun DesktopPptxParagraph.font(shape: DesktopPptxShapeElement): Font {
    val firstRun = runs.firstOrNull()
    val style = (if (firstRun?.bold == true) Font.BOLD else Font.PLAIN) or
        (if (firstRun?.italic == true) Font.ITALIC else Font.PLAIN)
    val family = firstRun?.typeface
        ?.takeIf { it.isNotBlank() && !it.startsWith("+") }
        ?: Font.SANS_SERIF
    val size = ((firstRun?.sizePt ?: DefaultTextSizePoint) * shape.fontScale.coerceIn(0.4f, 2f))
        .roundToInt()
        .coerceAtLeast(1)
    return Font(family, style, size)
}

private fun String.charBoxes(
    x: Float,
    top: Float,
    bottom: Float,
    metrics: (Char) -> Int,
    rotationBounds: DesktopPptxRect,
    rotationDegrees: Float
): List<DesktopPptxCharBox> {
    val boxes = mutableListOf<DesktopPptxCharBox>()
    var left = x
    forEach { char ->
        val advance = metrics(char).toFloat().coerceAtLeast(0.5f)
        val rect = DesktopPptxRect(left, top, left + advance, bottom)
            .rotatedBounds(rotationBounds, rotationDegrees)
        boxes += DesktopPptxCharBox(char, rect)
        left += advance
    }
    return boxes
}

private inline fun <T> withMeasureGraphics(block: (Graphics2D) -> T): T {
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    return try {
        graphics.enablePptxRenderingHints()
        block(graphics)
    } finally {
        graphics.dispose()
    }
}

private fun Graphics2D.enablePptxRenderingHints() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
}

private fun DesktopPptxImageCrop.sourceRect(width: Int, height: Int): DesktopPptxRect {
    val leftPx = (width * left).coerceIn(0f, (width - 1).toFloat())
    val topPx = (height * top).coerceIn(0f, (height - 1).toFloat())
    val rightPx = (width * (1f - right)).coerceIn(leftPx + 1f, width.toFloat())
    val bottomPx = (height * (1f - bottom)).coerceIn(topPx + 1f, height.toFloat())
    return DesktopPptxRect(leftPx, topPx, rightPx, bottomPx)
}

private fun pptxRgb(red: Int, green: Int, blue: Int): Int = pptxArgb(255, red, green, blue)

private fun pptxArgb(alpha: Int, red: Int, green: Int, blue: Int): Int {
    return ((alpha and 0xFF) shl 24) or
        ((red and 0xFF) shl 16) or
        ((green and 0xFF) shl 8) or
        (blue and 0xFF)
}

private fun Int.pptxAlpha(): Int = (this ushr 24) and 0xFF
private fun Int.pptxRed(): Int = (this ushr 16) and 0xFF
private fun Int.pptxGreen(): Int = (this ushr 8) and 0xFF
private fun Int.pptxBlue(): Int = this and 0xFF
private fun Int.toAwtColor(): Color = Color(this, true)

