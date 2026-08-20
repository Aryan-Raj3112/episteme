package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.text.CharCategory

/** A foreground paint range that does not participate in text shaping. */
data class ReaderPaintOnlyColorRange(
    val start: Int,
    val end: Int,
    val color: Color
)

/**
 * Removes foreground colors from an [AnnotatedString] while retaining every
 * typography and decoration attribute. Android's text shaper must see one
 * continuous run across paint-only spans for contextual OpenType features such
 * as mark/mkmk positioning to work correctly.
 */
fun AnnotatedString.withoutForegroundColorSpans(): AnnotatedString {
    if (spanStyles.isEmpty()) return this

    // A SpanStyle range is still a shaping boundary even when its only
    // specified field has just been cleared. Resolve all effective styles over
    // the text first, then emit only the runs whose non-colour properties
    // actually change. This is what lets a colour-only HTML span disappear
    // from the shaper while preserving boundaries for font/feature changes.
    val normalizedStyles = normalizedShapingSpanStyles()
    var stylesInserted = false
    return flatMapAnnotations { annotation ->
        if (annotation.item is SpanStyle) {
            if (stylesInserted) {
                emptyList()
            } else {
                stylesInserted = true
                normalizedStyles
            }
        } else {
            listOf(annotation)
        }
    }
}

private fun AnnotatedString.normalizedShapingSpanStyles(): List<AnnotatedString.Range<SpanStyle>> {
    val sourceStyles = spanStyles
        .map { range ->
            range.copy(
                start = range.start.coerceIn(0, text.length),
                end = range.end.coerceIn(0, text.length)
            )
        }
        .filter { it.start < it.end }
    if (sourceStyles.isEmpty() || text.isEmpty()) return emptyList()

    val boundaries = buildSet {
        add(0)
        add(text.length)
        sourceStyles.forEach {
            add(it.start)
            add(it.end)
        }
    }.sorted()

    val shapingRuns = mutableListOf<AnnotatedString.Range<SpanStyle>>()
    for (index in 0 until boundaries.lastIndex) {
        val start = boundaries[index]
        val end = boundaries[index + 1]
        if (start >= end) continue

        var effectiveStyle = SpanStyle()
        sourceStyles.forEach { range ->
            if (range.start <= start && range.end >= end) {
                effectiveStyle = effectiveStyle.merge(range.item)
            }
        }
        val shapingStyle = effectiveStyle.copy(color = Color.Unspecified)
        if (shapingStyle == SpanStyle()) continue

        val previous = shapingRuns.lastOrNull()
        if (previous != null && previous.end == start && previous.item == shapingStyle) {
            shapingRuns[shapingRuns.lastIndex] = previous.copy(end = end)
        } else {
            shapingRuns += AnnotatedString.Range(shapingStyle, start, end)
        }
    }
    return shapingRuns
}

/**
 * Computes the effective explicit foreground color for each text interval in
 * annotation order. Later annotations override earlier ones, matching
 * [AnnotatedString]'s documented style precedence. The ranges are disjoint and
 * adjacent equal colors are coalesced so callers can paint them efficiently.
 */
fun AnnotatedString.paintOnlyColorRanges(): List<ReaderPaintOnlyColorRange> {
    if (text.isEmpty()) return emptyList()

    val colorRanges = spanStyles.mapNotNull { range ->
        range.item.color.takeIf { it.isSpecified }?.let { color ->
            ReaderPaintOnlyColorRange(
                start = range.start.coerceIn(0, text.length),
                end = range.end.coerceIn(0, text.length),
                color = color
            )
        }
    }.filter { it.start < it.end }
    if (colorRanges.isEmpty()) return emptyList()

    val boundaries = buildSet {
        add(0)
        add(text.length)
        colorRanges.forEach {
            add(it.start)
            add(it.end)
        }
    }.sorted()

    val result = mutableListOf<ReaderPaintOnlyColorRange>()
    for (index in 0 until boundaries.lastIndex) {
        val start = boundaries[index]
        val end = boundaries[index + 1]
        if (start >= end) continue

        // The original annotation order is the precedence order. Unspecified
        // colors do not clear a previous color and therefore need no entry.
        val color = colorRanges.lastOrNull { it.start <= start && it.end >= end }?.color ?: continue
        val previous = result.lastOrNull()
        if (previous != null && previous.end == start && previous.color == color) {
            result[result.lastIndex] = previous.copy(end = end)
        } else {
            result += ReaderPaintOnlyColorRange(start, end, color)
        }
    }
    return result
}

/**
 * Builds a second text input that paints only the explicit foreground colours.
 *
 * The base text is rendered normally from [withoutForegroundColorSpans]. This
 * companion input has a transparent foreground over the whole string and
 * puts the explicit colours back as ordinary character-level paint spans.
 * Compose/Android therefore shapes both inputs with the same surrounding
 * context, while the native text renderer paints a colour only for the
 * requested character range. In particular, this avoids selection paths,
 * which are rectangles and cannot distinguish a combining mark from the base
 * glyph to which it is attached.
 *
 * [baseColor] is the colour already supplied to the base text. Ranges that
 * resolve to that colour are omitted so the same glyph is not painted twice.
 */
fun AnnotatedString.paintOnlyColorOverlayText(baseColor: Color = Color.Unspecified): AnnotatedString {
    val colorRanges = paintOnlyColorRanges().filter { range ->
        !baseColor.isSpecified || range.color != baseColor
    }
    if (colorRanges.isEmpty() || text.isEmpty()) return AnnotatedString("")

    // The normalized text has no colour boundaries, so its typography remains
    // identical to the text used by the base layout. Clear other paint-only
    // fields in this copy: backgrounds, decorations, shadows, and strokes are
    // already drawn by the base text and must not be doubled by the overlay.
    val shapingText = withoutForegroundColorSpans().flatMapAnnotations { annotation ->
        val style = annotation.item as? SpanStyle ?: return@flatMapAnnotations listOf(annotation)
        val overlayStyle = SpanStyle(
            brush = null,
            alpha = Float.NaN,
            fontSize = style.fontSize,
            fontWeight = style.fontWeight,
            fontStyle = style.fontStyle,
            fontSynthesis = style.fontSynthesis,
            fontFamily = style.fontFamily,
            fontFeatureSettings = style.fontFeatureSettings,
            letterSpacing = style.letterSpacing,
            baselineShift = style.baselineShift,
            textGeometricTransform = style.textGeometricTransform,
            localeList = style.localeList,
            background = Color.Unspecified,
            textDecoration = null,
            shadow = null,
            platformStyle = style.platformStyle,
            drawStyle = null
        )
        listOf(AnnotatedString.Range(overlayStyle, annotation.start, annotation.end, annotation.tag))
    }

    return buildAnnotatedString {
        append(shapingText)
        addStyle(SpanStyle(color = Color.Transparent), 0, length)
        colorRanges.forEach { range ->
            addStyle(SpanStyle(color = range.color), range.start, range.end)
        }
    }
}
