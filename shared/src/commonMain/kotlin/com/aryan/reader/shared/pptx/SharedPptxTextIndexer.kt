package com.aryan.reader.shared.pptx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Single-source slide text index shared by the JVM (Android/desktop) and iOS
 * PPTX parsers. Produces searchable text plus per-character boxes that are
 * anchor-, rotation- and autofit-aware. Android is the benchmark.
 */
data class SharedPptxTextIndex(
    val text: String,
    val charBoxes: List<SharedPptxCharBox>
)

object SharedPptxTextIndexer {
    fun index(elements: List<SharedPptxElement>): SharedPptxTextIndex {
        val text = StringBuilder()
        val charBoxes = mutableListOf<SharedPptxCharBox>()
        elements.forEach { element ->
            when (element) {
                is SharedPptxShapeElement -> appendShapeText(element, text, charBoxes)
                is SharedPptxTableElement -> layoutTableCells(element).forEach { cell ->
                    appendShapeText(cell.asShape(), text, charBoxes)
                }
                is SharedPptxImageElement -> Unit
            }
        }
        val indexedText = text.toString().trimEnd()
        return SharedPptxTextIndex(indexedText, charBoxes.take(indexedText.length))
    }

    private fun appendShapeText(
        shape: SharedPptxShapeElement,
        text: StringBuilder,
        charBoxes: MutableList<SharedPptxCharBox>
    ) {
        if (!shape.renderText) return
        val textBounds = shape.textBounds()
        if (textBounds.width() <= 0f || textBounds.height() <= 0f) return
        val paragraphs = shape.paragraphs
            .mapNotNull { paragraph -> paragraph.displayText().takeIf { it.isNotBlank() }?.let { paragraph to it } }
        if (paragraphs.isEmpty()) return

        val paragraphHeights = paragraphs.map { (paragraph, displayText) ->
            paragraph.spaceBeforePt + displayText.lineCount() * paragraph.approximateLineHeight(shape) + paragraph.spaceAfterPt
        }
        val totalHeight = paragraphHeights.sumOf { it.toDouble() }.toFloat()
        val effectiveBounds = if (shape.autoFitMode == SharedPptxAutoFitMode.SHAPE && totalHeight > textBounds.height()) {
            textBounds.copy(bottom = textBounds.top + totalHeight)
        } else {
            textBounds
        }
        var y = when (shape.verticalAnchor) {
            SharedPptxVerticalAnchor.TOP -> effectiveBounds.top
            SharedPptxVerticalAnchor.MIDDLE -> effectiveBounds.top + ((effectiveBounds.height() - totalHeight) / 2f).coerceAtLeast(0f)
            SharedPptxVerticalAnchor.BOTTOM -> effectiveBounds.bottom - totalHeight.coerceAtMost(effectiveBounds.height())
        }

        paragraphs.forEachIndexed { index, (paragraph, displayText) ->
            y += paragraph.spaceBeforePt
            displayText.lines().forEach { line ->
                if (shape.autoFitMode != SharedPptxAutoFitMode.SHAPE && y > effectiveBounds.bottom) return@forEach
                appendLine(line, paragraph, shape, effectiveBounds, y, text, charBoxes)
                y += paragraph.approximateLineHeight(shape)
            }
            y += paragraph.spaceAfterPt
            if (index < paragraphs.lastIndex && text.lastOrNull() != '\n') {
                text.append('\n')
                charBoxes += SharedPptxCharBox('\n', SharedPptxRect(shape.bounds.left, shape.bounds.bottom, shape.bounds.left, shape.bounds.bottom))
            }
        }
    }

    private fun appendLine(
        line: String,
        paragraph: SharedPptxParagraph,
        shape: SharedPptxShapeElement,
        textBounds: SharedPptxRect,
        y: Float,
        text: StringBuilder,
        charBoxes: MutableList<SharedPptxCharBox>
    ) {
        if (line.isEmpty()) {
            text.append('\n')
            charBoxes += SharedPptxCharBox('\n', SharedPptxRect(textBounds.left, y, textBounds.left, y))
            return
        }
        val fontSize = scaledTextSize(paragraph.runs.firstOrNull()?.sizePt, shape.fontScale).toFloat()
        val estimatedWidth = line.sumOf { char ->
            when {
                char.isWhitespace() -> 0.33
                char in "ilI.,;:!|" -> 0.3
                char in "MW@#%&" -> 0.85
                else -> 0.55
            }
        }.toFloat() * fontSize
        val maxLineWidth = textBounds.width().coerceAtLeast(0.5f)
        val minLineWidth = min(line.length * 0.5f, maxLineWidth)
        val lineWidth = estimatedWidth.coerceIn(minLineWidth, maxLineWidth)
        val charAdvance = (lineWidth / line.length.coerceAtLeast(1)).coerceAtLeast(0.5f)
        val startX = when (paragraph.alignment) {
            SharedPptxTextAlign.START -> textBounds.left
            SharedPptxTextAlign.CENTER -> textBounds.left + ((textBounds.width() - lineWidth) / 2f).coerceAtLeast(0f)
            SharedPptxTextAlign.END -> textBounds.right - lineWidth
        }
        val bottom = y + paragraph.approximateLineHeight(shape)

        text.append(line)
        line.forEachIndexed { index, char ->
            val left = startX + index * charAdvance
            val right = if (index == line.lastIndex) startX + lineWidth else left + charAdvance
            charBoxes += SharedPptxCharBox(
                char = char,
                bounds = SharedPptxRect(left, y, right, bottom).rotatedBounds(shape.bounds, shape.rotationDegrees)
            )
        }
    }

    private fun String.lineCount(): Int = lines().size.coerceAtLeast(1)

    private fun SharedPptxParagraph.approximateLineHeight(shape: SharedPptxShapeElement): Float {
        val fontSize = scaledTextSize(runs.firstOrNull()?.sizePt, shape.fontScale).toFloat()
        return (fontSize * 1.2f * effectiveLineSpacing(shape)).coerceAtLeast(1f)
    }
}

private data class LaidOutTableCell(
    val rect: SharedPptxRect,
    val cell: SharedPptxTableCell
)

private fun LaidOutTableCell.asShape(): SharedPptxShapeElement {
    return SharedPptxShapeElement(
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

private fun scaledTextSize(sizePt: Float?, fontScale: Float): Int {
    return ((sizePt ?: SharedPptxOoXml.DEFAULT_TEXT_SIZE_PT) * fontScale.coerceIn(0.4f, 2f))
        .roundToInt()
        .coerceAtLeast(1)
}

/** Line-spacing multiplier after autofit reduction, clamped to the PowerPoint range. */
fun SharedPptxParagraph.effectiveLineSpacing(shape: SharedPptxShapeElement): Float {
    val reduced = lineSpacingMultiple * (1f - shape.lineSpacingReduction)
    return reduced.coerceIn(0.9f, 2.5f)
}

private fun SharedPptxShapeElement.textBounds(sourceBounds: SharedPptxRect = bounds): SharedPptxRect {
    return SharedPptxRect(
        sourceBounds.left + textInsets.left,
        sourceBounds.top + textInsets.top,
        sourceBounds.right - textInsets.right,
        sourceBounds.bottom - textInsets.bottom
    )
}

/** Plain-text form of a paragraph, including its bullet prefix when present. */
fun SharedPptxParagraph.displayText(): String {
    val text = runs.joinToString("") { it.text }
    val prefix = bullet?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return prefix + text
}

private fun SharedPptxRect.rotatedBounds(bounds: SharedPptxRect, rotationDegrees: Float): SharedPptxRect {
    if (rotationDegrees == 0f) return this
    val radians = rotationDegrees * PI / 180f
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
        SharedPptxPoint(cx + dx * cosValue - dy * sinValue, cy + dx * sinValue + dy * cosValue)
    }
    return SharedPptxRect(
        points.minOf { it.x },
        points.minOf { it.y },
        points.maxOf { it.x },
        points.maxOf { it.y }
    )
}

private fun layoutTableCells(table: SharedPptxTableElement): List<LaidOutTableCell> {
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
                rect = SharedPptxRect(x, y, x + cellWidth, y + rowHeight),
                cell = cell
            )
            x += cellWidth
        }
        y += rowHeight
    }
    return cells
}
