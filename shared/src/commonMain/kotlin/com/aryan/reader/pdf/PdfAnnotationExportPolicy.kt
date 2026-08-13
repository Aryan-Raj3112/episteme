package com.aryan.reader.pdf

fun hasExportablePdfAnnotationContent(
    inkAnnotationCounts: Iterable<Int>,
    textBoxCount: Int,
    highlightCount: Int,
): Boolean {
    return inkAnnotationCounts.any { it > 0 } || textBoxCount > 0 || highlightCount > 0
}

fun shouldShowPdfAnnotationExportChoice(
    sidecarsReady: Boolean,
    inkAnnotationCounts: Iterable<Int>,
    textBoxCount: Int,
    highlightCount: Int,
): Boolean {
    return !sidecarsReady || hasExportablePdfAnnotationContent(
        inkAnnotationCounts = inkAnnotationCounts,
        textBoxCount = textBoxCount,
        highlightCount = highlightCount,
    )
}

fun supportsOriginalPdfPageOrder(layout: List<PdfPageIdentity>?): Boolean {
    return layout == null || layout.withIndex().all { (index, page) ->
        page is PdfPageIdentity.Pdf && page.pdfIndex == index
    }
}
