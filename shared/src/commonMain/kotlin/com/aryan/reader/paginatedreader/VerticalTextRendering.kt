package com.aryan.reader.paginatedreader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Native vertical-rl paragraph. Lays [text] out with [layoutVerticalParagraph]
 * (the same engine pagination measures with) and draws every cell: upright
 * kana/kanji, 90-degree rotated Latin runs, squeezed tate-chu-yoko runs and
 * furigana mini-columns right of their bases.
 *
 * @param columnHeightPx usable column height; MUST match the paginator's
 * column height for this block or breaks will disagree with measurement.
 */
@Composable
fun VerticalParagraphText(
    text: String,
    rubies: List<RubyAnnotation>,
    tcyRanges: List<IntRange>,
    fontSize: TextUnit,
    lineHeightEm: Float,
    textAlign: TextAlign,
    color: Color,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
    fontFeatureSettings: String?,
    columnHeightPx: Float,
    modifier: Modifier = Modifier,
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    density: Density = LocalDensity.current,
    onLayoutResolved: ((VerticalParagraphLayout) -> Unit)? = null
) {
    val measurer = remember(textMeasurer, fontFamily, fontWeight, fontFeatureSettings, density) {
        verticalDrawMeasurer(textMeasurer, fontFamily, fontWeight, fontFeatureSettings, density)
    }
    val layout = remember(text, rubies, tcyRanges, fontSize, lineHeightEm, textAlign, columnHeightPx, measurer) {
        layoutVerticalParagraph(
            text = text,
            rubies = rubies,
            tcyRanges = tcyRanges,
            fontSizePx = with(density) { fontSize.toPx() }.coerceAtLeast(1f),
            lineHeightEm = lineHeightEm,
            columnHeightPx = columnHeightPx,
            textAlign = textAlign,
            measurer = measurer
        )
    }
    androidx.compose.runtime.LaunchedEffect(layout) {
        onLayoutResolved?.invoke(layout)
    }
    val baseFontSizePx = with(density) { fontSize.toPx() }.coerceAtLeast(1f)
    val draws = remember(layout, color, fontFamily, fontWeight, density, baseFontSizePx) {
        VerticalCellDraws.prepare(layout, textMeasurer, color, fontFamily, fontWeight, density, baseFontSizePx)
    }
    // Fixed size: width from the laid-out columns, full column height. Both
    // match pagination inputs exactly, so pages never disagree with measure.
    val widthDp = with(density) { layout.widthPx.toDp() }
    val heightDp = with(density) { columnHeightPx.toDp() }
    Box(
        modifier = modifier
            .requiredSize(widthDp, heightDp)
            .drawWithContent {
                drawContent()
                draws.draw(this)
            }
    )
}

/** Width/height in [Dp] for a laid-out vertical paragraph. */
fun verticalParagraphSizeDp(
    layout: VerticalParagraphLayout,
    columnHeightPx: Float,
    density: Density
): Pair<Dp, Dp> = with(density) {
    layout.widthPx.toDp() to columnHeightPx.toDp()
}

internal fun verticalDrawMeasurer(
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
    fontFeatureSettings: String?,
    density: Density
): VerticalGlyphMeasurer = object : VerticalGlyphMeasurer {
    private val uprightFeatures = listOfNotNull(
        fontFeatureSettings?.takeIf { it.isNotBlank() },
        "\"vert\""
    ).joinToString(", ").takeIf { it.isNotBlank() }

    private fun styleFor(sizePx: Float, upright: Boolean): TextStyle = TextStyle(
        fontSize = with(density) { sizePx.toSp() },
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontFeatureSettings = if (upright) uprightFeatures else fontFeatureSettings
    )

    override fun measureUpright(text: String, fontSizePx: Float): Size =
        textMeasurer.measure(text, styleFor(fontSizePx, upright = true)).size.let { size ->
            Size(size.width.toFloat(), size.height.toFloat())
        }

    override fun measureHorizontal(text: String, fontSizePx: Float): Size =
        textMeasurer.measure(text, styleFor(fontSizePx, upright = false)).size.let { size ->
            Size(size.width.toFloat(), size.height.toFloat())
        }
}

/** Pre-measured draw payloads for one vertical paragraph layout. */
internal class VerticalCellDraws private constructor(
    private val upright: List<UprightDraw>,
    private val rotated: List<RotatedDraw>,
    private val combined: List<CombinedDraw>,
    private val readings: List<ReadingDraw>
) {
    private data class UprightDraw(val layout: androidx.compose.ui.text.TextLayoutResult, val topLeft: Offset)
    private data class RotatedDraw(val layout: androidx.compose.ui.text.TextLayoutResult, val center: Offset)
    private data class CombinedDraw(val layout: androidx.compose.ui.text.TextLayoutResult, val box: Rect, val emPx: Float)
    private data class ReadingDraw(val chars: List<Pair<androidx.compose.ui.text.TextLayoutResult, Offset>>)

    fun draw(scope: DrawScope) = with(scope) {
        for (item in upright) drawText(item.layout, topLeft = item.topLeft)
        for (item in rotated) {
            rotate(degrees = 90f, pivot = item.center) {
                val size = item.layout.size
                drawText(
                    item.layout,
                    topLeft = Offset(
                        item.center.x - size.width / 2f,
                        item.center.y - size.height / 2f
                    )
                )
            }
        }
        for (item in combined) {
            val size = item.layout.size
            if (size.width <= 0f) continue
            val squeeze = (item.emPx / size.width).coerceAtMost(1f)
            val center = item.box.center
            scale(scaleX = squeeze, scaleY = 1f, pivot = center) {
                drawText(
                    item.layout,
                    topLeft = Offset(
                        center.x - size.width / 2f,
                        center.y - size.height / 2f
                    )
                )
            }
        }
        for (reading in readings) {
            for ((glyph, topLeft) in reading.chars) drawText(glyph, topLeft = topLeft)
        }
    }

    companion object {
        fun prepare(
            layout: VerticalParagraphLayout,
            textMeasurer: TextMeasurer,
            color: Color,
            fontFamily: FontFamily?,
            fontWeight: FontWeight?,
            density: Density,
            baseFontSizePx: Float
        ): VerticalCellDraws {
            val upright = mutableListOf<UprightDraw>()
            val rotated = mutableListOf<RotatedDraw>()
            val combined = mutableListOf<CombinedDraw>()
            val readings = mutableListOf<ReadingDraw>()
            // Resolve the base em from the first upright cell for tcy squeeze.
            val emPx = layout.columns.firstOrNull()?.cells?.firstOrNull { cell ->
                cell.orientation == VerticalCellOrientation.UPRIGHT
            }?.heightPx ?: with(density) { 16.dp.toPx() }
            for (column in layout.columns) {
                for (cell in column.cells) {
                    when (cell.orientation) {
                        VerticalCellOrientation.GAP -> Unit
                        VerticalCellOrientation.UPRIGHT -> {
                            val glyph = textMeasurer.measure(
                                text = cell.text,
                                style = TextStyle(
                                    fontSize = with(density) { baseFontSizePx.toSp() },
                                    color = color,
                                    fontFamily = fontFamily,
                                    fontWeight = fontWeight,
                                    fontFeatureSettings = "\"vert\""
                                )
                            )
                            upright.add(
                                UprightDraw(
                                    glyph,
                                    Offset(
                                        cell.xPx + (cell.widthPx - glyph.size.width) / 2f,
                                        cell.yPx + (cell.heightPx - glyph.size.height) / 2f
                                    )
                                )
                            )
                            cell.reading?.let { reading ->
                                readings.add(measureReading(reading, textMeasurer, color, fontFamily, fontWeight, density))
                            }
                        }
                        VerticalCellOrientation.ROTATED -> {
                            val run = textMeasurer.measure(
                                text = cell.text,
                                style = TextStyle(
                                    fontSize = with(density) { baseFontSizePx.toSp() },
                                    color = color,
                                    fontFamily = fontFamily,
                                    fontWeight = fontWeight
                                )
                            )
                            rotated.add(
                                RotatedDraw(
                                    run,
                                    Offset(cell.xPx + cell.widthPx / 2f, cell.yPx + cell.heightPx / 2f)
                                )
                            )
                            cell.reading?.let { reading ->
                                readings.add(measureReading(reading, textMeasurer, color, fontFamily, fontWeight, density))
                            }
                        }
                        VerticalCellOrientation.COMBINED -> {
                            val run = textMeasurer.measure(
                                text = cell.text,
                                style = TextStyle(
                                    fontSize = with(density) { baseFontSizePx.toSp() },
                                    color = color,
                                    fontFamily = fontFamily,
                                    fontWeight = fontWeight
                                )
                            )
                            combined.add(
                                CombinedDraw(
                                    run,
                                    Rect(cell.xPx, cell.yPx, cell.xPx + cell.widthPx, cell.yPx + cell.heightPx),
                                    emPx
                                )
                            )
                        }
                    }
                }
            }
            return VerticalCellDraws(upright, rotated, combined, readings)
        }

        private fun measureReading(
            reading: VerticalReading,
            textMeasurer: TextMeasurer,
            color: Color,
            fontFamily: FontFamily?,
            fontWeight: FontWeight?,
            density: Density
        ): ReadingDraw {
            val chars = mutableListOf<Pair<androidx.compose.ui.text.TextLayoutResult, Offset>>()
            var y = reading.yPx
            for (char in reading.text) {
                val glyph = textMeasurer.measure(
                    text = char.toString(),
                    style = TextStyle(
                        fontSize = with(density) { reading.fontSizePx.toSp() },
                        color = color,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        fontFeatureSettings = "\"vert\""
                    )
                )
                chars.add(
                    glyph to Offset(
                        reading.xPx + (reading.widthPx - glyph.size.width) / 2f,
                        y
                    )
                )
                y += reading.fontSizePx
            }
            return ReadingDraw(chars)
        }
    }
}

/** Character boxes for [start, end) as draw-space rects (highlights, TTS, taps). */
fun VerticalParagraphLayout.rectsForRange(start: Int, end: Int): List<Rect> {
    if (start >= end) return emptyList()
    val rects = mutableListOf<Rect>()
    for (column in columns) {
        for (cell in column.cells) {
            val cellEnd = cell.charOffset + cell.charLength
            if (cellEnd <= start || cell.charOffset >= end) continue
            rects.add(Rect(cell.xPx, cell.yPx, cell.xPx + cell.widthPx, cell.yPx + cell.heightPx))
        }
    }
    return rects
}

/** Base-text offset at draw-space [position], or null when no cell covers it. */
fun VerticalParagraphLayout.offsetAt(position: Offset): Int? {
    for (column in columns) {
        for (cell in column.cells) {
            if (position.x >= cell.xPx && position.x <= cell.xPx + cell.widthPx &&
                position.y >= cell.yPx && position.y <= cell.yPx + cell.heightPx
            ) {
                return cell.charOffset
            }
        }
    }
    return null
}
