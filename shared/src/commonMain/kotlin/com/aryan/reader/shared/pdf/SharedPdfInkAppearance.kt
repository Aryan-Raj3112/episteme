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
 *
 * ## Opacity
 *
 * A content stream has no opacity of its own, and this API cannot hand PDFium a
 * `/Resources` dictionary. `FPDFAnnot_SetAP` builds the appearance's resources from
 * the annotation dictionary: when the annotation has `/CA < 1` it installs
 * `/Resources /ExtGState /GS` whose `CA`/`ca` both come from `/CA`, and otherwise it
 * installs no resources at all — and Apple's PDFKit drops the colour operators of an
 * appearance stream without `/Resources` and paints a black `/InkList` fallback. The
 * exporters therefore write `/C` + `/CA` from [sharedPdfInkAnnotationDictColorArgb]
 * (which keeps `/CA` strictly below 1) before installing this stream; the annotation
 * dictionary is frozen afterwards, because `FPDFAnnot_SetColor` refuses to update an
 * annotation that already has an appearance.
 *
 * So a translucent stroke is expressed by referencing that graphics state with
 * `/GS gs` — and only when [colorArgb]'s alpha is below 255, because the name must
 * resolve. Callers install the appearance for the exact colour
 * [sharedPdfInkExportColorArgb] produced; the dictionary colour differs from it by at
 * most one alpha step.
 *
 * Blend mode is out of reach: `FPDFAnnot_SetAP` always writes `/BM /Normal`. Multiply
 * over white paper is numerically identical to source-over at the same alpha, which is
 * what the on-screen highlighter renders on a page background.
 */
fun sharedPdfInkAppearanceContent(
    pagePoints: List<PdfPagePoint>,
    strokeWidthPdfUnits: Float,
    colorArgb: Int,
): String {
    if (pagePoints.isEmpty() || strokeWidthPdfUnits <= 0f) return ""

    val alpha = (colorArgb ushr 24) and 0xFF
    val red = ((colorArgb ushr 16) and 0xFF) / 255f
    val green = ((colorArgb ushr 8) and 0xFF) / 255f
    val blue = (colorArgb and 0xFF) / 255f

    return buildString {
        append("q\n")
        // Translucent strokes reference the ExtGState PDFium attaches for /CA < 1.
        if (alpha < 255) {
            append("/GS gs\n")
        }
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
        // Single-point taps are zero-length segments: with round caps (1 J),
        // PDF viewers paint a filled circle of diameter = line width (the dot).
        pagePoints.forEachIndexed { index, point ->
            append(pdfFixed(point.x))
            append(' ')
            append(pdfFixed(point.y))
            if (index == 0) {
                append(" m\n")
                if (pagePoints.size == 1) {
                    append(pdfFixed(point.x))
                    append(' ')
                    append(pdfFixed(point.y))
                    append(" l\n")
                }
            } else {
                append(" l\n")
            }
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
