// PdfTextBoxCreate.kt
package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.TextStyleConfig
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextBoxSpec
import com.aryan.reader.shared.pdf.trimmedRichParagraphs

/**
 * Text-box construction (page rich text is retired: boxes are the only text
 * annotation, created by tapping the page). Pure builders so tap placement,
 * the legacy fixed-position path and legacy conversion share one
 * implementation and stay unit-tested.
 */

const val PDF_TEXT_BOX_DEFAULT_WIDTH_REL = 0.4f
const val PDF_TEXT_BOX_DEFAULT_HEIGHT_REL = 0.1f

/** Display-sp font size to page-relative norm, same formula as the dock path. */
fun pdfTextBoxFontSizeNorm(
    displayFontSizeSp: Float,
    spToPx: (Float) -> Float,
    pageRatio: Float,
    containerWidthPx: Float,
): Float {
    val estimatedPageHeightPx = if (pageRatio > 0) containerWidthPx / pageRatio else containerWidthPx
    val fontSizePx = spToPx(displayFontSizeSp)
    return if (estimatedPageHeightPx > 0) fontSizePx / estimatedPageHeightPx else 0.02f
}

fun buildTextBoxAtTap(
    id: String,
    pageIndex: Int,
    xRel: Float,
    yRel: Float,
    style: TextStyleConfig,
    fontSizeNorm: Float,
    widthRel: Float = PDF_TEXT_BOX_DEFAULT_WIDTH_REL,
    heightRel: Float = PDF_TEXT_BOX_DEFAULT_HEIGHT_REL,
): PdfTextBox {
    val safeWidth = widthRel.coerceIn(0.05f, 1f)
    val safeHeight = heightRel.coerceIn(0.02f, 1f)
    val left = (xRel - safeWidth / 2f).coerceIn(0f, (1f - safeWidth).coerceAtLeast(0f))
    val top = (yRel - safeHeight / 2f).coerceIn(0f, (1f - safeHeight).coerceAtLeast(0f))
    return PdfTextBox(
        id = id,
        pageIndex = pageIndex,
        relativeBounds = Rect(left, top, left + safeWidth, top + safeHeight),
        text = "",
        color = Color(style.colorArgb),
        backgroundColor = Color(style.backgroundColorArgb),
        fontSize = fontSizeNorm,
        isBold = style.isBold,
        isItalic = style.isItalic,
        isUnderline = style.isUnderline,
        isStrikeThrough = style.isStrikeThrough,
        fontPath = style.fontPath,
        fontName = style.fontName,
    )
}

/**
 * Maps a legacy-converter spec to a box. Bounds come from the converter's
 * page-wise stacking (coerced onto the page); the estimator is not rerun.
 */
fun buildTextBoxFromSpec(
    spec: SharedPdfTextBoxSpec,
    id: String,
): PdfTextBox {
    val norm = SharedPdfTextAnnotationDefaults.sanitizePageRelativeFontSize(spec.fontSizeNorm)
    val bounds = spec.bounds
    return PdfTextBox(
        id = id,
        pageIndex = spec.pageIndex,
        relativeBounds = Rect(
            bounds.left.coerceIn(0f, 1f),
            bounds.top.coerceIn(0f, 1f),
            bounds.right.coerceIn(bounds.left.coerceIn(0f, 1f), 1f),
            bounds.bottom.coerceIn(bounds.top.coerceIn(0f, 1f), 1f),
        ),
        text = spec.text,
        color = Color(spec.colorArgb),
        backgroundColor = Color(spec.backgroundArgb),
        fontSize = norm,
        isBold = spec.isBold,
        isItalic = spec.isItalic,
        isUnderline = spec.isUnderline,
        isStrikeThrough = spec.isStrikeThrough,
        fontPath = spec.fontPath,
        fontName = null,
        paragraphs = spec.paragraphs.trimmedRichParagraphs(),
    )
}
