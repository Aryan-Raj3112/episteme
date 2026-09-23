package com.aryan.reader.shared.pdf

/**
 * PDF content-stream body for an ink annotation's normal appearance (`/AP /N`).
 *
 * Preview.app draws the annotation `/Border` rectangle when `/AP` is missing and
 * will not rebuild the path from `/InkList`. PDFium's `FPDFAnnot_SetBorder`
 * also clears any existing appearance, so callers must set the border first and
 * then install this stream.
 *
 * Coordinates are PDF page space (y-up), matching [FPDFAnnot_AddInkStroke]
 * inputs after the normalized→page transform.
 */
fun sharedPdfInkAppearanceContent(
    pagePoints: List<PdfPagePoint>,
    strokeWidthPdfUnits: Float,
    colorArgb: Int,
): String {
    if (pagePoints.size < 2 || strokeWidthPdfUnits <= 0f) return ""

    val red = ((colorArgb ushr 16) and 0xFF) / 255f
    val green = ((colorArgb ushr 8) and 0xFF) / 255f
    val blue = (colorArgb and 0xFF) / 255f

    return buildString {
        append("q\n")
        append(pdfFixed(red))
        append(' ')
        append(pdfFixed(green))
        append(' ')
        append(pdfFixed(blue))
        append(" RG\n")
        append(pdfFixed(strokeWidthPdfUnits))
        append(" w\n")
        // Round caps/joins match on-screen pen rendering; PDFium default is butt/miter.
        append("1 J\n1 j\n")
        pagePoints.forEachIndexed { index, point ->
            append(pdfFixed(point.x))
            append(' ')
            append(pdfFixed(point.y))
            append(if (index == 0) " m\n" else " l\n")
        }
        append("S\n")
        append("Q\n")
    }
}

private fun pdfFixed(value: Float): String {
    if (!value.isFinite()) return "0"
    val rounded = (value * 1000f).toLong() / 1000.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
