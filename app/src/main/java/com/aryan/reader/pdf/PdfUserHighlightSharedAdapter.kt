package com.aryan.reader.pdf

import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfAndroidHighlightColors
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.sharedPdfHighlightAnnotation

internal fun PdfUserHighlight.toSharedPdfHighlightAnnotation(
    normalizedBounds: List<PdfPageBounds> = emptyList(),
    resolvedColorArgb: Int = colorArgb ?: SharedPdfAndroidHighlightColors.argbForName(color.name),
): SharedPdfAnnotation = sharedPdfHighlightAnnotation(
    id = id,
    pageIndex = pageIndex,
    bounds = normalizedBounds,
    text = text,
    note = note,
    comments = comments,
    colorArgb = resolvedColorArgb,
    highlightStyle = style,
    rangeStart = range.first,
    rangeEndExclusive = range.second,
)
