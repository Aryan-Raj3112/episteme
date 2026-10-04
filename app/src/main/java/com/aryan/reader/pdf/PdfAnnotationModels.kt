package com.aryan.reader.pdf

enum class AnnotationType {
    INK, TEXT
}

enum class InkType {
    PEN, HIGHLIGHTER, HIGHLIGHTER_ROUND, ERASER, FOUNTAIN_PEN, PENCIL, TEXT, SELECT
}

data class PdfPoint(val x: Float, val y: Float, val timestamp: Long = 0L)

/**
 * Stamps an ink point with the time the pointer event reported for it.
 *
 * The gesture layer already carries a per-sample timestamp, because one
 * coalesced change can hold dozens of samples that are all handled inside the
 * same millisecond — stamping them on arrival collapsed them onto one instant
 * and left the fountain pen and pencil renderers with a meaningless velocity.
 * Callers that have no event time of their own (taps, converted geometry) fall
 * back to the wall clock.
 */
internal fun resolveInkPointTimestamp(point: PdfPoint): PdfPoint =
    if (point.timestamp > 0L) point else point.copy(timestamp = System.currentTimeMillis())
