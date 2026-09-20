package com.aryan.reader.paginatedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * One measured furigana reading ready to draw over its base range.
 *
 * Readings are painted as decorations (like emphasis marks): the base text
 * stays the real selectable/searchable content, and the small reading is
 * drawn hugging its base (horizontal), exactly like browsers place `<rt>`
 * (annotation bottom == base top, no extra gap). Pagination measurement never
 * sees the readings, so measure and render agree by construction.
 */
data class RubyDraw(
    val layout: TextLayoutResult,
    val topLeft: Offset
)

/**
 * Measures [rubies] against a laid-out base paragraph. Returns draws for the
 * first line fragment of each base range; multi-line base splits (rare for
 * 1-3 character bases) keep their reading over the first fragment.
 */
@Composable
fun rememberHorizontalRubyDraws(
    layoutResult: TextLayoutResult?,
    rubies: List<RubyAnnotation>,
    baseColor: Color,
    baseFontFamily: FontFamily?,
    baseFontSize: TextUnit,
    density: Density = LocalDensity.current
): List<RubyDraw> {
    val measurer = rememberTextMeasurer()
    return remember(layoutResult, rubies, baseColor, baseFontFamily, baseFontSize, density) {
        val layout = layoutResult ?: return@remember emptyList()
        if (rubies.isEmpty() || !baseFontSize.isSpecified || baseFontSize.value <= 0f) {
            return@remember emptyList()
        }
        val textLength = layout.layoutInput.text.length
        if (textLength <= 0) return@remember emptyList()
        val basePx = with(density) { baseFontSize.toPx() }
        if (basePx <= 0f) return@remember emptyList()
        val draws = mutableListOf<RubyDraw>()
        for (ruby in rubies) {
            val start = ruby.baseStart.coerceIn(0, textLength)
            val end = ruby.baseEnd.coerceIn(start, textLength)
            if (start >= end || ruby.reading.isBlank()) continue
            // EPUB-specified `<rt>` size wins; otherwise the browser default.
            val readingFontSize =
                with(density) { (basePx * ruby.effectiveReadingScale()).toSp() }
            try {
                val firstLine = layout.getLineForOffset(start.coerceAtMost(textLength - 1))
                val lineStart = layout.getLineStart(firstLine)
                val lineEnd = layout.getLineEnd(firstLine, visibleEnd = true)
                val fragmentStart = maxOf(start, lineStart)
                val fragmentEnd = minOf(end, lineEnd)
                if (fragmentStart >= fragmentEnd) continue
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                var top = Float.POSITIVE_INFINITY
                for (offset in fragmentStart until fragmentEnd) {
                    val box = layout.getBoundingBox(offset)
                    left = minOf(left, box.left, box.right)
                    right = maxOf(right, box.left, box.right)
                    top = minOf(top, box.top, box.bottom)
                }
                if (!left.isFinite() || !right.isFinite() || !top.isFinite() || right <= left) continue
                val readingLayout = measurer.measure(
                    text = ruby.reading,
                    style = TextStyle(
                        fontSize = readingFontSize,
                        fontFamily = baseFontFamily,
                        color = baseColor
                    )
                )
                val x = (left + right) / 2f - readingLayout.size.width / 2f
                // Browsers place `<rt>` flush against the base (annotation
                // bottom == base top); any extra gap reads as detached.
                val y = top - readingLayout.size.height
                draws.add(RubyDraw(readingLayout, Offset(x, y)))
            } catch (_: Exception) {
                continue
            }
        }
        draws
    }
}

fun DrawScope.drawRubyDraws(draws: List<RubyDraw>) {
    for (draw in draws) {
        drawText(draw.layout, topLeft = draw.topLeft)
    }
}
