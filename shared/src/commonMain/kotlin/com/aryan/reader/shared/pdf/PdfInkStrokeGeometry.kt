package com.aryan.reader.shared.pdf

import kotlin.math.sqrt

data class PdfInkCurvePoint(val x: Float, val y: Float)

data class PdfInkCubicSegment(
    val control1: PdfInkCurvePoint,
    val control2: PdfInkCurvePoint,
    val end: PdfInkCurvePoint,
)

/**
 * Builds a C1-continuous cubic path through every real input sample.
 *
 * Samples remain append-only. At a reversal the shared tangent is reduced to zero, preventing
 * spline overshoot and loops without moving or deleting an already committed endpoint.
 */
fun buildPdfInkCubicSegments(
    points: List<PdfPagePoint>,
    scaleX: Float,
    scaleY: Float,
): List<PdfInkCubicSegment> {
    if (points.size < 2) return emptyList()
    val scaled = points.map { PdfInkCurvePoint(it.x * scaleX, it.y * scaleY) }
    val tangents = scaled.indices.map { index ->
        when (index) {
            0 -> scaled[1] - scaled[0]
            scaled.lastIndex -> scaled.last() - scaled[scaled.lastIndex - 1]
            else -> stableTangent(
                incoming = scaled[index] - scaled[index - 1],
                outgoing = scaled[index + 1] - scaled[index],
            )
        }
    }
    return List(scaled.lastIndex) { index ->
        val start = scaled[index]
        val end = scaled[index + 1]
        PdfInkCubicSegment(
            control1 = start + tangents[index] / 3f,
            control2 = end - tangents[index + 1] / 3f,
            end = end,
        )
    }
}

private fun stableTangent(incoming: PdfInkCurvePoint, outgoing: PdfInkCurvePoint): PdfInkCurvePoint {
    val incomingLength = incoming.length()
    val outgoingLength = outgoing.length()
    if (incomingLength <= 0.0001f || outgoingLength <= 0.0001f) return PdfInkCurvePoint(0f, 0f)
    val incomingUnit = incoming / incomingLength
    val outgoingUnit = outgoing / outgoingLength
    if (incomingUnit.dot(outgoingUnit) <= 0f) return PdfInkCurvePoint(0f, 0f)
    val direction = incomingUnit + outgoingUnit
    val directionLength = direction.length()
    if (directionLength <= 0.0001f) return PdfInkCurvePoint(0f, 0f)
    return direction / directionLength * minOf(incomingLength, outgoingLength)
}

private operator fun PdfInkCurvePoint.plus(other: PdfInkCurvePoint) = PdfInkCurvePoint(x + other.x, y + other.y)
private operator fun PdfInkCurvePoint.minus(other: PdfInkCurvePoint) = PdfInkCurvePoint(x - other.x, y - other.y)
private operator fun PdfInkCurvePoint.times(value: Float) = PdfInkCurvePoint(x * value, y * value)
private operator fun PdfInkCurvePoint.div(value: Float) = PdfInkCurvePoint(x / value, y / value)
private fun PdfInkCurvePoint.dot(other: PdfInkCurvePoint): Float = x * other.x + y * other.y
private fun PdfInkCurvePoint.length(): Float = sqrt(x * x + y * y)
