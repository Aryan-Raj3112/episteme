package com.aryan.reader.shared.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Geometry for the decoration strokes drawn on top of PDF text highlights (underline, wavy
 * underline, strikethrough).
 *
 * A single highlight is made of several rects: pdfium emits one rect per font run per line, and a
 * multi-line selection emits one rect per line. Every rect of one highlight therefore shares a
 * single baseline, amplitude and wavelength. Deriving them per rect made the stroke jump height and
 * thickness at whichever glyph happened to be tallest, and it restarted the wave phase at every
 * rect boundary.
 *
 * This file is deliberately free of any drawing or platform type so the geometry can be unit-tested
 * directly. Adapters live at the bottom for both call sites: Android passes bitmap-space
 * `androidx.compose.ui.geometry.Rect`s, shared/iOS passes normalized [PdfPageBounds]s.
 */

/** A rect in whatever space the stroke is drawn in. */
data class PdfHighlightRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val hasArea: Boolean get() = width > 0f && height > 0f && left.isFinite() && top.isFinite() &&
            right.isFinite() && bottom.isFinite()

    companion object {
        fun from(left: Float, top: Float, right: Float, bottom: Float): PdfHighlightRect {
            val minX = minOf(left, right)
            val maxX = maxOf(left, right)
            val minY = minOf(top, bottom)
            val maxY = maxOf(top, bottom)
            return PdfHighlightRect(minX, minY, maxX, maxY)
        }
    }
}

data class PdfHighlightStrokeStyle(
    val height: Float,
    val amplitude: Float,
    val wavelength: Float,
    val lineStrokeWidth: Float,
    val waveStrokeWidth: Float,
) {
    val hasArea: Boolean get() = height > 0f
}

/** Baseline offset for underline and wavy underline, as a fraction of the highlight height. */
const val PDF_HIGHLIGHT_UNDERLINE_BASELINE_FRACTION: Float = 0.86f

/** Baseline offset for strikethrough, as a fraction of the highlight height. */
const val PDF_HIGHLIGHT_STRIKETHROUGH_BASELINE_FRACTION: Float = 0.52f

/** Horizontal samples used per wavelength when approximating the sine wave with a polyline. */
const val PDF_HIGHLIGHT_WAVE_SAMPLES_PER_PERIOD: Int = 8

/** Vertical-overlap fraction required for two rects to count as the same text line. */
const val PDF_HIGHLIGHT_LINE_OVERLAP_FRACTION: Float = 0.6f

/**
 * Vertical-overlap fraction required by the text-run merges in `PdfTextProcessing`.
 *
 * Two adjacent text lines share a small vertical band when leading is tight (a font box is roughly
 * ascender + descender, so 12pt leading over a 13.8pt box already overlaps ~1.8pt). A low threshold
 * therefore collapses two lines into one rect, which deletes one line's decoration stroke. A
 * majority overlap is the right test for "same line"; runs on one line share nearly their whole box.
 */
const val PDF_SAME_LINE_MIN_OVERLAP: Float = 0.6f

/**
 * Resolves the stroke metrics for a highlight of [height].
 *
 * The absolute pixel floors keep a low-zoom wave from degenerating into a scribble, exactly as the
 * previous per-rect implementation did, so this alone is not a visual change.
 */
fun pdfHighlightStrokeStyle(height: Float): PdfHighlightStrokeStyle {
    val safeHeight = height.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    return PdfHighlightStrokeStyle(
        height = safeHeight,
        amplitude = (safeHeight * 0.08f).coerceIn(1.2f, 3.5f),
        wavelength = (safeHeight * 0.62f).coerceIn(6f, 14f),
        lineStrokeWidth = (safeHeight * 0.08f).coerceIn(1.5f, 4f),
        waveStrokeWidth = (safeHeight * 0.06f).coerceIn(1.2f, 3f),
    )
}

/**
 * One visual line of a highlight: the union bounds of the rects that share a text line.
 *
 * A highlight is drawn one [PdfHighlightLine] at a time rather than one rect at a time so the wave
 * phase and the baseline stay continuous along a line that pdfium split into several font runs,
 * while still giving each line of a multi-line highlight its own baseline.
 */
data class PdfHighlightLine(
    val left: Float,
    val right: Float,
    val top: Float,
    val height: Float,
) {
    val baselineForUnderline: Float
        get() = top + height * PDF_HIGHLIGHT_UNDERLINE_BASELINE_FRACTION

    val baselineForStrikethrough: Float
        get() = top + height * PDF_HIGHLIGHT_STRIKETHROUGH_BASELINE_FRACTION
}

/**
 * Groups a highlight's rects into text lines, ordered top-to-bottom.
 *
 * Rects join a line when their vertical bands overlap by at least [overlapFraction] of the shorter
 * band. That is deliberately stricter than the text-run merge in `PdfTextProcessing`: this grouping
 * only has to recognise "same line", and a loose test here would merge two adjacent text lines and
 * silently drop one line's stroke. It also compares against the **running** band rather than the
 * previous rect, so a run cannot chain its way up through several lines.
 *
 * Returns an empty list when no rect has area.
 */
fun pdfHighlightLines(
    rects: List<PdfHighlightRect>,
    overlapFraction: Float = PDF_HIGHLIGHT_LINE_OVERLAP_FRACTION,
): List<PdfHighlightLine> {
    val drawable = rects.filter { it.hasArea }
    if (drawable.isEmpty()) return emptyList()
    val safeFraction = overlapFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        ?: PDF_HIGHLIGHT_LINE_OVERLAP_FRACTION

    val groupIndices = mutableListOf<MutableList<Int>>()
    val groupTops = mutableListOf<Float>()
    val groupBottoms = mutableListOf<Float>()

    // Reading order first, so grouping never depends on input order.
    drawable.sortedWith(compareBy({ it.top }, { it.left })).forEachIndexed { order, rect ->
        var mergedInto = -1
        for (group in groupIndices.indices) {
            val overlap = minOf(groupBottoms[group], rect.bottom) - maxOf(groupTops[group], rect.top)
            val shortest = minOf(groupBottoms[group] - groupTops[group], rect.height)
            if (overlap > 0f && overlap >= shortest * safeFraction) {
                mergedInto = group
                break
            }
        }
        if (mergedInto >= 0) {
            groupIndices[mergedInto] += order
            if (rect.top < groupTops[mergedInto]) groupTops[mergedInto] = rect.top
            if (rect.bottom > groupBottoms[mergedInto]) groupBottoms[mergedInto] = rect.bottom
        } else {
            groupIndices += mutableListOf(order)
            groupTops += rect.top
            groupBottoms += rect.bottom
        }
    }

    return groupIndices.indices
        .sortedBy { groupTops[it] }
        .map { group ->
            val top = groupTops[group]
            val bottom = groupBottoms[group]
            PdfHighlightLine(
                left = groupIndices[group].minOf { drawable[it].left },
                right = groupIndices[group].maxOf { drawable[it].right },
                top = top,
                height = bottom - top,
            )
        }
}

/**
 * Sine-sampled wavy underline for one line, as a polyline.
 *
 * The previous implementation advanced in whole wavelengths and clamped only the curve *endpoints*
 * to the line end while leaving the control points unclamped. In the final partial period that
 * produced a degenerate loop whose start and end were both at the line end and whose body bulged
 * outside the highlight, plus a tangent cusp at the join - which read as a gap at the end of the
 * stroke. Sampling a real sine gives an exact amplitude and period at any width and can never leave
 * the line horizontally.
 *
 * [startPhaseCycles] carries the wave phase across the lines of one highlight so crests and troughs
 * stay continuous along a run that wraps onto a following line. Only meaningful for lines on the
 * same text line being drawn as one path; a multi-line highlight starts a fresh phase per line
 * because the baseline itself jumps.
 */
fun pdfHighlightWavePoints(
    style: PdfHighlightStrokeStyle,
    line: PdfHighlightLine,
    startPhaseCycles: Float = 0f,
    samplesPerPeriod: Int = PDF_HIGHLIGHT_WAVE_SAMPLES_PER_PERIOD,
): List<Offset> {
    if (!style.hasArea || style.wavelength <= 0f || line.right <= line.left) return emptyList()
    val safeSamples = samplesPerPeriod.coerceAtLeast(2)
    val width = line.right - line.left
    // Sample on a fixed x-step of wavelength/samplesPerPeriod rather than dividing the line into
    // equal fractions. Fractional division changes the effective sampling density with the line's
    // length, which is how the old implementation ended up with a final period squeezed into a few
    // pixels; a fixed step keeps every period identically shaped and the last sample lands exactly
    // on the line end.
    val step = style.wavelength / safeSamples
    val intervalCount = ceil(width / step).toInt().coerceAtLeast(1)
    return (0..intervalCount).map { index ->
        val x = if (index == intervalCount) line.right else line.left + step * index
        val cycles = startPhaseCycles + (x - line.left) / style.wavelength
        Offset(x, line.baselineForUnderline - style.amplitude * sin(2f * PI.toFloat() * cycles))
    }
}

/** Wavy underline for one text line, as a [Path]. */
fun pdfHighlightWavePath(
    style: PdfHighlightStrokeStyle,
    line: PdfHighlightLine,
    startPhaseCycles: Float = 0f,
    samplesPerPeriod: Int = PDF_HIGHLIGHT_WAVE_SAMPLES_PER_PERIOD,
): Path {
    val points = pdfHighlightWavePoints(style, line, startPhaseCycles, samplesPerPeriod)
    if (points.isEmpty()) return Path()
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { path.lineTo(it.x, it.y) }
    return path
}

/**
 * Flat decoration stroke (underline, strikethrough) for one text line, as a [Path].
 *
 * Kept as a path rather than a `drawLine` so the whole line shares one stroke width derived from
 * one height, instead of one per rect.
 */
fun pdfHighlightLinePath(line: PdfHighlightLine, strikethrough: Boolean = false): Path {
    if (line.right <= line.left) return Path()
    val baseline = if (strikethrough) line.baselineForStrikethrough else line.baselineForUnderline
    return Path().apply {
        moveTo(line.left, baseline)
        lineTo(line.right, baseline)
    }
}

// --- adapters -------------------------------------------------------------------------------

/** Android bitmap-space adapter. */
fun androidx.compose.ui.geometry.Rect.toPdfHighlightRect(): PdfHighlightRect =
    PdfHighlightRect.from(left, top, right, bottom)

/** Shared/iOS normalized-bounds adapter. */
fun PdfPageBounds.toPdfHighlightRect(
    canvasWidth: Float,
    canvasHeight: Float,
): PdfHighlightRect = PdfHighlightRect.from(
    left * canvasWidth,
    top * canvasHeight,
    right * canvasWidth,
    bottom * canvasHeight,
)