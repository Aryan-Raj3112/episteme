package com.aryan.reader.shared.pdf

import androidx.compose.ui.graphics.Color

/**
 * Port of Android's highlight-all ("Highlight Selectable Text") logic in
 * PdfPageComposable.kt: rects come from the PDFium text page
 * (`textPageGetRectsForRanges` over the whole page), falling back to OCR when
 * the PDF has no selectable text, and are rendered as merged line rects with a
 * page scrim that is punched out over the text.
 */

data class SharedPdfHighlightAllColors(
    /** Fill color drawn over each text line rect. */
    val rectColor: Color,
    /** Full-page scrim the text rects are punched out of. */
    val scrimColor: Color,
)

/**
 * Mirrors Android's `allTextPageHighlightColor` / `scrimColorForTextHighlight`:
 * dark themes tint the text yellow with no scrim; light themes dim everything
 * except the text (primary alpha 0 means only the punch-out is visible).
 */
fun sharedPdfHighlightAllColors(isDarkMode: Boolean, primary: Color): SharedPdfHighlightAllColors =
    if (isDarkMode) {
        SharedPdfHighlightAllColors(
            rectColor = Color(0xFFFFEB3B).copy(alpha = 0.4f),
            scrimColor = Color.Transparent,
        )
    } else {
        SharedPdfHighlightAllColors(
            rectColor = primary.copy(alpha = 0f),
            scrimColor = Color.Black.copy(alpha = 0.4f),
        )
    }

/**
 * Merges normalized rects into line rects, mirroring Android's
 * `mergeRectsIntoLines` (PdfHelper.kt): rects are sorted top-to-bottom then
 * left-to-right; rects whose vertical extents overlap belong to the same line
 * and are unioned.
 */
fun sharedPdfMergeRectsIntoLines(bounds: List<PdfPageBounds>): List<PdfPageBounds> {
    if (bounds.isEmpty()) return emptyList()
    val sorted = bounds.sortedWith(compareBy({ it.top }, { it.left }))
    val mergedLines = mutableListOf<PdfPageBounds>()
    var currentLine: PdfPageBounds? = null
    for (bounds in sorted) {
        val line = currentLine
        if (line == null) {
            currentLine = bounds
        } else {
            val sameLine = maxOf(line.top, bounds.top) < minOf(line.bottom, bounds.bottom)
            if (sameLine) {
                currentLine = PdfPageBounds(
                    left = minOf(line.left, bounds.left),
                    top = minOf(line.top, bounds.top),
                    right = maxOf(line.right, bounds.right),
                    bottom = maxOf(line.bottom, bounds.bottom),
                )
            } else {
                mergedLines.add(line)
                currentLine = bounds
            }
        }
    }
    currentLine?.let { mergedLines.add(it) }
    return mergedLines
}
