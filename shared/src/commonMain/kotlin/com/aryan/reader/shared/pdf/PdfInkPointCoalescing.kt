package com.aryan.reader.shared.pdf

import kotlin.math.sqrt

private const val PdfHighlighterSampleDistanceMultiplier = 0.12f
private const val PdfHighlighterMinimumSampleDistance = 0.0005f

/**
 * Adds a live ink sample without accumulating high-frequency endpoint tremor.
 *
 * Highlighters are wide enough that multiple samples inside a tiny fraction of the nib width do
 * not add visible curve detail. Replacing the current endpoint instead of dropping the new sample
 * keeps the stroke attached to the finger while preventing those samples from becoming a loop in
 * the smoothed path. No points are removed after the gesture, so intentional curves are retained.
 */
fun shouldReplaceLastPdfInkPoint(
    points: List<PdfPagePoint>,
    next: PdfPagePoint,
    inkTool: PdfInkTool,
    strokeWidth: Float,
): Boolean {
    if (points.size < 2) return false
    if (inkTool != PdfInkTool.HIGHLIGHTER && inkTool != PdfInkTool.HIGHLIGHTER_ROUND) {
        return false
    }

    val minimumDistance = (strokeWidth * PdfHighlighterSampleDistanceMultiplier)
        .coerceAtLeast(PdfHighlighterMinimumSampleDistance)
    return distance(points.last(), next) < minimumDistance
}

private fun distance(first: PdfPagePoint, second: PdfPagePoint): Float {
    val dx = second.x - first.x
    val dy = second.y - first.y
    return sqrt(dx * dx + dy * dy)
}
