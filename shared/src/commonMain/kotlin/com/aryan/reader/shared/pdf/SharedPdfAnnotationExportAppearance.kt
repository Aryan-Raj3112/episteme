package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.HighlightStyle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Colour/opacity policy for exported PDF annotations.
 *
 * The in-app renderer is the benchmark: exported annotations must look like what the
 * reader draws. Every platform derives its annotation colour (and its `/AP` stroke
 * colour) from these helpers so the three exporters cannot drift apart.
 */

/** On-screen underline/strike/wavy markup stroke opacity (Android/desktop render). */
internal const val SHARED_PDF_MARKUP_RENDER_ALPHA = 0.92f

/** Minimum PDF stroke width in page points, matching every platform's export floor. */
private const val MIN_PDF_STROKE_WIDTH_UNITS = 0.25f

/**
 * Highest annotation `/CA` (alpha byte) pdfium still attaches an appearance `/Resources`
 * entry for: `FPDFAnnot_SetAP` checks `CA < 1` before writing one.
 */
private const val MAX_PDF_ANNOTATION_DICT_ALPHA = 254

/**
 * ARGB used for an ink annotation in the exported PDF and in its `/AP` stream.
 *
 * On-screen highlighter strokes paint at their stored alpha with a Multiply blend
 * ([SharedPdfInkRendering]'s `blendMode`), so the export must carry that same alpha
 * instead of a solid stroke. Multiplying over white paper is numerically identical to
 * source-over at the same alpha, and [SharedPdfHighlighterPalette.DefaultAlpha] is the
 * alpha the palette always stores — highlighters are therefore capped at it so legacy
 * opaque strokes cannot export as a solid smear.
 *
 * Any other tool keeps its stored colour untouched.
 */
fun sharedPdfInkExportColorArgb(tool: PdfInkTool, colorArgb: Int): Int {
    if (tool != PdfInkTool.HIGHLIGHTER && tool != PdfInkTool.HIGHLIGHTER_ROUND) return colorArgb
    val storedAlpha = (colorArgb ushr 24) and 0xFF
    val alpha = when {
        storedAlpha == 0 -> SharedPdfHighlighterPalette.DefaultAlpha
        else -> minOf(storedAlpha, SharedPdfHighlighterPalette.DefaultAlpha)
    }
    return (alpha shl 24) or (colorArgb and 0x00FFFFFF)
}

/**
 * ARGB written to an ink annotation's dictionary (`/C` + `/CA`) before its `/AP` stream
 * is installed; the same RGB as [sharedPdfInkExportColorArgb] with the alpha clamped.
 *
 * Keep the RGB from [sharedPdfInkExportColorArgb] so the `/InkList` fallback colour and
 * the appearance stroke agree, and keep the alpha below 255: `FPDFAnnot_SetAP` only
 * attaches `/Resources` to the appearance while `/CA < 1`, and Apple's PDFKit (Preview,
 * Quick Look, Books) drops the colour operators of an appearance stream without
 * `/Resources` and paints a black `/InkList` fallback instead. `FPDFAnnot_SetColor`
 * refuses to update `/CA` once an appearance exists, so the value supplied here *is*
 * the exported opacity: 254/255 is imperceptible on screen and in print, while 255/255
 * exports a red pen stroke as black in every Apple renderer.
 */
fun sharedPdfInkAnnotationDictColorArgb(colorArgb: Int): Int {
    val alpha = (colorArgb ushr 24) and 0xFF
    return (minOf(alpha, MAX_PDF_ANNOTATION_DICT_ALPHA) shl 24) or (colorArgb and 0x00FFFFFF)
}

/**
 * Stroke width in PDF page units for an ink annotation.
 *
 * [strokeWidth] is stored as a fraction of the page width (the unit the on-screen
 * renderer scales by), and [displayWidthPdfUnits] is the rotated page width in points.
 */
fun sharedPdfInkStrokeWidthPdfUnits(strokeWidth: Float, displayWidthPdfUnits: Float): Float =
    max(MIN_PDF_STROKE_WIDTH_UNITS, strokeWidth * displayWidthPdfUnits)

/**
 * ARGB used for a text-markup (highlight) annotation in the exported PDF.
 *
 * The reader draws the `BACKGROUND` overlay via
 * `sharedPdfHighlightAnnotationOverlayStyle`, which caps the stored alpha at
 * `RenderAlpha`; underline/strike/wavy draw at their stored alpha with a 0.92 default.
 * Export must land on the same value, so an opaque stored colour is burned down to the
 * style's render alpha instead of exporting as a solid box over the text.
 */
fun sharedPdfHighlightExportColorArgb(style: HighlightStyle, colorArgb: Int): Int {
    val targetAlpha = when (style) {
        HighlightStyle.BACKGROUND ->
            (SharedPdfAndroidHighlightColors.RenderAlpha * 255f).roundToInt().coerceIn(0, 255)
        HighlightStyle.UNDERLINE,
        HighlightStyle.WAVY_UNDERLINE,
        HighlightStyle.STRIKETHROUGH ->
            (SHARED_PDF_MARKUP_RENDER_ALPHA * 255f).roundToInt().coerceIn(0, 255)
    }
    val storedAlpha = (colorArgb ushr 24) and 0xFF
    val alpha = if (storedAlpha == 0) targetAlpha else minOf(storedAlpha, targetAlpha)
    return (alpha shl 24) or (colorArgb and 0x00FFFFFF)
}
